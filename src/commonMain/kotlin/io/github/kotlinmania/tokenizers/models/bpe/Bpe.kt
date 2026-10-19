// port-lint: source models/bpe/model.rs
package io.github.kotlinmania.tokenizers.models.bpe

import io.github.kotlinmania.tokenizers.tokenizer.Model
import io.github.kotlinmania.tokenizers.tokenizer.Offsets
import io.github.kotlinmania.tokenizers.tokenizer.Token
import io.github.kotlinmania.tokenizers.tokenizer.Trainer
import io.github.kotlinmania.tokenizers.utils.Cache
import io.github.kotlinmania.tokenizers.utils.DEFAULT_CACHE_CAPACITY
import io.github.kotlinmania.tokenizers.utils.MAX_LENGTH

public typealias Vocab = Map<String, UInt>
public typealias Merges = List<BpeMerge>
public typealias MergeMap = Map<PairIds, Pair<UInt, UInt>>

private data class Config(
    var files: Pair<String, String>? = null,
    var vocab: MutableMap<String, UInt> = mutableMapOf(),
    var merges: MutableList<BpeMerge> = mutableListOf(),
    var cacheCapacity: Int = DEFAULT_CACHE_CAPACITY,
    var dropout: Float? = null,
    var unkToken: String? = null,
    var continuingSubwordPrefix: String? = null,
    var endOfWordSuffix: String? = null,
    var fuseUnk: Boolean = false,
    var byteFallback: Boolean = false,
    var ignoreMerges: Boolean = false,
)

/**
 * A BpeBuilder can be used to create a BPE model with a custom configuration.
 */
public class BpeBuilder private constructor(
    private val config: Config = Config(),
) {
    public constructor() : this(Config())

    public fun files(vocab: String, merges: String): BpeBuilder {
        config.files = Pair(vocab, merges)
        return this
    }

    public fun vocabAndMerges(vocab: Map<String, UInt>, merges: Merges): BpeBuilder {
        config.vocab = vocab.toMutableMap()
        config.merges = merges.toMutableList()
        return this
    }

    public fun cacheCapacity(capacity: Int): BpeBuilder {
        config.cacheCapacity = capacity
        return this
    }

    public fun dropout(dropout: Float): BpeBuilder {
        config.dropout = dropout
        return this
    }

    public fun unkToken(unkToken: String): BpeBuilder {
        config.unkToken = unkToken
        return this
    }

    public fun continuingSubwordPrefix(prefix: String): BpeBuilder {
        config.continuingSubwordPrefix = prefix
        return this
    }

    public fun endOfWordSuffix(suffix: String): BpeBuilder {
        config.endOfWordSuffix = suffix
        return this
    }

    public fun fuseUnk(fuseUnk: Boolean): BpeBuilder {
        config.fuseUnk = fuseUnk
        return this
    }

    public fun byteFallback(byteFallback: Boolean): BpeBuilder {
        config.byteFallback = byteFallback
        return this
    }

    public fun ignoreMerges(ignoreMerges: Boolean): BpeBuilder {
        config.ignoreMerges = ignoreMerges
        return this
    }

    public fun build(): Result<BPE> {
        val d = config.dropout
        if (d != null && (d < 0.0f || d > 1.0f)) {
            return Result.failure(BpeError.InvalidDropout)
        }

        val vocab = config.vocab
        val vocabR = mutableMapOf<UInt, String>()
        for ((k, v) in vocab) {
            vocabR[v] = k
        }

        val cache =
            if (config.cacheCapacity > 0) {
                Cache<String, Word>(config.cacheCapacity)
            } else {
                null
            }

        val prefixLen = config.continuingSubwordPrefix?.length ?: 0
        val mergeMap = mutableMapOf<PairIds, Pair<UInt, UInt>>()

        for (i in config.merges.indices) {
            val (a, b) = config.merges[i]
            val aId = vocab[a] ?: return Result.failure(BpeError.MergeTokenOutOfVocabulary(a))
            val bId = vocab[b] ?: return Result.failure(BpeError.MergeTokenOutOfVocabulary(b))
            val bSuffix = if (b.length >= prefixLen) b.substring(prefixLen) else b
            val newToken = "$a$bSuffix"
            val newId = vocab[newToken] ?: return Result.failure(BpeError.MergeTokenOutOfVocabulary(newToken))
            mergeMap[PairIds(aId, bId)] = Pair(i.toUInt(), newId)
        }

        val unk = config.unkToken
        if (unk != null && !vocab.containsKey(unk)) {
            return Result.failure(BpeError.UnkTokenOutOfVocabulary(unk))
        }

        return Result.success(
            BPE(
                vocab = vocab,
                vocabR = vocabR,
                merges = mergeMap,
                cache = cache,
                dropout = config.dropout,
                unkToken = config.unkToken,
                continuingSubwordPrefix = config.continuingSubwordPrefix,
                endOfWordSuffix = config.endOfWordSuffix,
                fuseUnk = config.fuseUnk,
                byteFallback = config.byteFallback,
                ignoreMerges = config.ignoreMerges,
            ),
        )
    }

    public companion object {
        public fun new(): BpeBuilder = BpeBuilder()
    }
}

/**
 * A Byte Pair Encoding model.
 */
public class BPE internal constructor(
    internal val vocab: Map<String, UInt>,
    internal val vocabR: Map<UInt, String>,
    internal val merges: Map<PairIds, Pair<UInt, UInt>>,
    private val cache: Cache<String, Word>?,
    public val dropout: Float? = null,
    public val unkToken: String? = null,
    public val continuingSubwordPrefix: String? = null,
    public val endOfWordSuffix: String? = null,
    public val fuseUnk: Boolean = false,
    public val byteFallback: Boolean = false,
    public val ignoreMerges: Boolean = false,
) : Model {
    public fun clearCache() {
        cache?.clear()
    }

    public fun resizeCache(capacity: Int) {
        cache?.resize(capacity)
    }

    override fun getVocab(): Map<String, UInt> = vocab

    override fun getVocabSize(): Int = vocab.size

    internal fun mergeWord(w: String): Result<Word> {
        val word = Word.withCapacity(w.length)
        var unk: Pair<UInt, Int>? = null
        val codePoints = mutableListOf<String>()

        var i = 0
        while (i < w.length) {
            val c = w[i]
            val s =
                if (c.isHighSurrogate() && i + 1 < w.length && w[i + 1].isLowSurrogate()) {
                    val str = w.substring(i, i + 2)
                    i += 2
                    str
                } else {
                    val str = w.substring(i, i + 1)
                    i += 1
                    str
                }
            codePoints.add(s)
        }

        for (idx in codePoints.indices) {
            val isFirst = idx == 0
            val isLast = idx == codePoints.size - 1
            var s = codePoints[idx]
            val byteLen = s.encodeToByteArray().size

            if (!isFirst) {
                val prefix = continuingSubwordPrefix
                if (prefix != null) {
                    s = "$prefix$s"
                }
            }
            if (isLast) {
                val suffix = endOfWordSuffix
                if (suffix != null) {
                    s = "$s$suffix"
                }
            }

            val id = vocab[s]
            if (id != null) {
                val currentUnk = unk
                if (currentUnk != null) {
                    word.add(currentUnk.first, currentUnk.second)
                    unk = null
                }
                word.add(id, byteLen)
            } else {
                if (byteFallback) {
                    val bytes = s.encodeToByteArray()
                    var allFound = true
                    val byteTokens = mutableListOf<UInt>()
                    for (b in bytes) {
                        val hex = (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
                        val code = "<0x$hex>"
                        val byteId = vocab[code]
                        if (byteId != null) {
                            byteTokens.add(byteId)
                        } else {
                            allFound = false
                            break
                        }
                    }
                    if (allFound) {
                        for (bt in byteTokens) {
                            word.add(bt, 1)
                        }
                        continue
                    }
                }

                val currentUnkToken = unkToken
                if (currentUnkToken != null) {
                    val unkId =
                        vocab[currentUnkToken]
                            ?: return Result.failure(BpeError.UnkTokenOutOfVocabulary(currentUnkToken))

                    val currentUnk = unk
                    unk =
                        if (currentUnk != null && fuseUnk) {
                            Pair(currentUnk.first, currentUnk.second + byteLen)
                        } else if (currentUnk != null) {
                            word.add(currentUnk.first, currentUnk.second)
                            Pair(unkId, byteLen)
                        } else {
                            Pair(unkId, byteLen)
                        }
                }
            }
        }

        val finalUnk = unk
        if (finalUnk != null) {
            word.add(finalUnk.first, finalUnk.second)
        }

        word.mergeAll(merges, dropout)
        return Result.success(word)
    }

    private fun wordToTokens(word: Word): List<Token> {
        val chars = word.getCharsIter()
        val offsets = word.getOffsetsIter()
        val tokens = mutableListOf<Token>()
        for (i in chars.indices) {
            val id = chars[i]
            val value = vocabR[id].orEmpty()
            tokens.add(Token.new(id, value, offsets[i]))
        }
        return tokens
    }

    private fun tokenizeWithCache(sequence: String): Result<List<Token>> {
        if (ignoreMerges) {
            val id = vocab[sequence]
            if (id != null) {
                val byteLen = sequence.encodeToByteArray().size
                return Result.success(listOf(Token.new(id, sequence, Offsets(0, byteLen))))
            }
        }

        val cachedWord = cache?.get(sequence)
        if (cachedWord != null) {
            return Result.success(wordToTokens(cachedWord))
        }

        val wordRes = mergeWord(sequence)
        if (wordRes.isFailure) return Result.failure(wordRes.exceptionOrNull()!!)
        val word = wordRes.getOrThrow()
        val ret = wordToTokens(word)
        if (cache != null && sequence.length < MAX_LENGTH) {
            cache.set(sequence, word)
        }
        return Result.success(ret)
    }

    override fun tokenize(sequence: String): Result<List<Token>> {
        if (sequence.isEmpty()) {
            return Result.success(emptyList())
        }

        val d = dropout
        return if (d == null || d == 0.0f) {
            tokenizeWithCache(sequence)
        } else {
            val wordRes = mergeWord(sequence)
            if (wordRes.isFailure) return Result.failure(wordRes.exceptionOrNull()!!)
            Result.success(wordToTokens(wordRes.getOrThrow()))
        }
    }

    override fun tokenToId(token: String): UInt? = vocab[token]

    override fun idToToken(id: UInt): String? = vocabR[id]

    override fun save(folder: String, prefix: String?): Result<List<String>> =
        Result.success(emptyList())

    override fun getTrainer(): Trainer =
        object : Trainer {
            override fun shouldShowProgress(): Boolean = false

            override fun train(model: Model): Result<List<String>> = Result.success(emptyList())
        }

    public companion object {
        public fun builder(): BpeBuilder = BpeBuilder.new()

        public fun new(vocab: Map<String, UInt>, merges: Merges): BPE =
            builder().vocabAndMerges(vocab, merges).build().getOrThrow()

        public fun fromFile(vocab: String, merges: String): BpeBuilder =
            builder().files(vocab, merges)
    }
}
