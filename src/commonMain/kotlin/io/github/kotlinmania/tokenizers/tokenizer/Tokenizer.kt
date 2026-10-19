package io.github.kotlinmania.tokenizers.tokenizer

import io.github.kotlinmania.tokenizers.utils.PaddingParams
import io.github.kotlinmania.tokenizers.utils.TruncationParams
import io.github.kotlinmania.tokenizers.utils.padEncodings
import io.github.kotlinmania.tokenizers.utils.truncateEncodings

/**
 * An input sequence to be encoded.
 */
public sealed class InputSequence {
    public data class Raw(
        public val text: String,
    ) : InputSequence()

    public data class PreTokenized(
        public val tokens: List<String>,
    ) : InputSequence()

    public companion object {
        public fun from(text: String): InputSequence = Raw(text)

        public fun from(tokens: List<String>): InputSequence = PreTokenized(tokens)
    }
}

/**
 * An encode input, consisting of either a single sequence or a pair of sequences.
 */
public sealed class EncodeInput {
    public data class Single(
        public val sequence: InputSequence,
    ) : EncodeInput()

    public data class Dual(
        public val sequence1: InputSequence,
        public val sequence2: InputSequence,
    ) : EncodeInput()

    public companion object {
        public fun from(text: String): EncodeInput = Single(InputSequence.Raw(text))

        public fun from(text1: String, text2: String): EncodeInput =
            Dual(InputSequence.Raw(text1), InputSequence.Raw(text2))

        public fun from(tokens: List<String>): EncodeInput = Single(InputSequence.PreTokenized(tokens))

        public fun from(tokens1: List<String>, tokens2: List<String>): EncodeInput =
            Dual(InputSequence.PreTokenized(tokens1), InputSequence.PreTokenized(tokens2))
    }
}

/**
 * A `Tokenizer` is capable of encoding/decoding any text using a tokenization pipeline:
 * 1. Normalizer
 * 2. PreTokenizer
 * 3. Model
 * 4. PostProcessor
 * 5. Decoder
 */
public class Tokenizer(
    public var model: Model,
    public var normalizer: Normalizer? = null,
    public var preTokenizer: PreTokenizer? = null,
    public var postProcessor: PostProcessor? = null,
    public var decoder: Decoder? = null,
    public var addedVocabulary: AddedVocabulary = AddedVocabulary(),
    public var truncation: TruncationParams? = null,
    public var padding: PaddingParams? = null,
) {
    public constructor(model: Model) : this(
        model = model,
        normalizer = null,
        preTokenizer = null,
        postProcessor = null,
        decoder = null,
    )

    public companion object {
        public fun builder(): TokenizerBuilder = TokenizerBuilder()
    }

    public fun withNormalizer(normalizer: Normalizer?): Tokenizer = apply { this.normalizer = normalizer }

    public fun withPreTokenizer(preTokenizer: PreTokenizer?): Tokenizer = apply { this.preTokenizer = preTokenizer }

    public fun withPostProcessor(postProcessor: PostProcessor?): Tokenizer = apply { this.postProcessor = postProcessor }

    public fun withDecoder(decoder: Decoder?): Tokenizer = apply { this.decoder = decoder }

    public fun withModel(model: Model): Tokenizer = apply { this.model = model }

    public fun withAddedVocabulary(addedVocabulary: AddedVocabulary): Tokenizer = apply { this.addedVocabulary = addedVocabulary }

    public fun withTruncation(truncation: TruncationParams?): Tokenizer = apply { this.truncation = truncation }

    public fun withPadding(padding: PaddingParams?): Tokenizer = apply { this.padding = padding }

    public fun getVocab(withAddedTokens: Boolean = true): Map<String, UInt> {
        val finalVocab = model.getVocab().toMutableMap()
        if (withAddedTokens) {
            for ((k, v) in addedVocabulary.getVocab()) {
                finalVocab[k] = v
            }
        }
        return finalVocab
    }

    public fun getVocabSize(withAddedTokens: Boolean = true): Int = if (withAddedTokens) getVocab(true).size else model.getVocabSize()

    public fun tokenToId(token: String): UInt? = addedVocabulary.tokenToId(token, model)

    public fun idToToken(id: UInt): String? = addedVocabulary.idToToken(id, model)

    public fun addTokens(tokens: List<AddedToken>): Int =
        addedVocabulary.addTokens(tokens, model, normalizer)

    public fun addSpecialTokens(tokens: List<AddedToken>): Int =
        addedVocabulary.addSpecialTokens(tokens, model, normalizer)

    private fun doPreTokenize(pretokenized: PreTokenizedString): Result<PreTokenizedString> {
        val pt = preTokenizer
        if (pt != null) {
            val res = pt.preTokenize(pretokenized)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
        }
        return Result.success(pretokenized)
    }

    private fun doTokenize(
        pretokenized: PreTokenizedString,
        typeId: UInt,
        wordIdx: UInt?,
        offsetsType: OffsetType,
    ): Result<Encoding> {
        val tokRes = pretokenized.tokenize { norm -> model.tokenize(norm.get()) }
        if (tokRes.isFailure) return Result.failure(tokRes.exceptionOrNull()!!)
        return pretokenized.intoEncoding(wordIdx, typeId, offsetsType)
    }

    private fun encodeSingleSequence(
        sequence: InputSequence,
        typeId: UInt,
        offsetsType: OffsetType,
    ): Result<Encoding> {
        return when (sequence) {
            is InputSequence.Raw -> {
                val pretokenized = addedVocabulary.extractAndNormalize(normalizer, sequence.text)
                val ptRes = doPreTokenize(pretokenized)
                if (ptRes.isFailure) return Result.failure(ptRes.exceptionOrNull()!!)
                doTokenize(ptRes.getOrThrow(), typeId, null, offsetsType)
            }

            is InputSequence.PreTokenized -> {
                val encodings = mutableListOf<Encoding>()
                for ((i, text) in sequence.tokens.withIndex()) {
                    val pretokenized = addedVocabulary.extractAndNormalize(normalizer, text)
                    val ptRes = doPreTokenize(pretokenized)
                    if (ptRes.isFailure) return Result.failure(ptRes.exceptionOrNull()!!)
                    val encRes = doTokenize(ptRes.getOrThrow(), typeId, i.toUInt(), offsetsType)
                    if (encRes.isFailure) return Result.failure(encRes.exceptionOrNull()!!)
                    encodings.add(encRes.getOrThrow())
                }
                Result.success(Encoding.merge(encodings, false))
            }
        }
    }

    public fun postProcess(
        encoding: Encoding,
        pairEncoding: Encoding?,
        addSpecialTokens: Boolean,
    ): Result<Encoding> {
        // 1. Truncate if needed
        val trunc = truncation
        val (enc, pairEnc) =
            if (trunc != null) {
                val nAddedTokens = if (pairEncoding != null) addedTokens(true) else addedTokens(false)
                val params =
                    if (addSpecialTokens && nAddedTokens > 0) {
                        trunc.copy(maxLength = trunc.maxLength - nAddedTokens)
                    } else {
                        trunc
                    }
                val truncRes = truncateEncodings(encoding, pairEncoding, params)
                if (truncRes.isFailure) return Result.failure(truncRes.exceptionOrNull()!!)
                val pair = truncRes.getOrThrow()
                Pair(pair.first, pair.second)
            } else {
                Pair(encoding, pairEncoding)
            }

        // 2. Post-process
        val proc = postProcessor
        val finalEncodingRes =
            if (proc != null) {
                proc.process(enc, pairEnc, addSpecialTokens)
            } else {
                val encodings = if (pairEnc != null) listOf(enc, pairEnc) else listOf(enc)
                val defRes = PostProcessor.defaultProcess(encodings, addSpecialTokens)
                if (defRes.isFailure) return Result.failure(defRes.exceptionOrNull()!!)
                Result.success(defRes.getOrThrow().first())
            }
        if (finalEncodingRes.isFailure) return finalEncodingRes
        val finalEncoding = finalEncodingRes.getOrThrow()

        // 3. Pad if needed
        val pad = padding
        if (pad != null) {
            val padRes = padEncodings(mutableListOf(finalEncoding), pad)
            if (padRes.isFailure) return Result.failure(padRes.exceptionOrNull()!!)
        }

        return Result.success(finalEncoding)
    }

    public fun addedTokens(isPair: Boolean): Int = postProcessor?.addedTokens(isPair) ?: 0

    public fun encode(input: EncodeInput, addSpecialTokens: Boolean = false): Result<Encoding> {
        val (seq1, seq2) =
            when (input) {
                is EncodeInput.Single -> Pair(input.sequence, null)
                is EncodeInput.Dual -> Pair(input.sequence1, input.sequence2)
            }
        val encRes = encodeSingleSequence(seq1, 0u, OffsetType.Byte)
        if (encRes.isFailure) return encRes
        val encoding = encRes.getOrThrow()

        val pairEncoding =
            if (seq2 != null) {
                val pairRes = encodeSingleSequence(seq2, 1u, OffsetType.Byte)
                if (pairRes.isFailure) return pairRes
                pairRes.getOrThrow()
            } else {
                null
            }

        return postProcess(encoding, pairEncoding, addSpecialTokens)
    }

    public fun encode(sequence: String, addSpecialTokens: Boolean = false): Result<Encoding> =
        encode(EncodeInput.from(sequence), addSpecialTokens)

    public fun encode(sequence: String, pair: String?, addSpecialTokens: Boolean = false): Result<Encoding> =
        if (pair != null) {
            encode(EncodeInput.from(sequence, pair), addSpecialTokens)
        } else {
            encode(EncodeInput.from(sequence), addSpecialTokens)
        }

    public fun encodeFast(sequence: String, addSpecialTokens: Boolean = false): Result<Encoding> {
        val encRes = encodeSingleSequence(InputSequence.Raw(sequence), 0u, OffsetType.None)
        if (encRes.isFailure) return encRes
        return postProcess(encRes.getOrThrow(), null, addSpecialTokens)
    }

    public fun encodeCharOffsets(sequence: String, addSpecialTokens: Boolean = false): Result<Encoding> {
        val encRes = encodeSingleSequence(InputSequence.Raw(sequence), 0u, OffsetType.Char)
        if (encRes.isFailure) return encRes
        return postProcess(encRes.getOrThrow(), null, addSpecialTokens)
    }

    public fun decode(ids: List<UInt>, skipSpecialTokens: Boolean = false): Result<String> {
        val tokens = mutableListOf<String>()
        for (id in ids) {
            val token = addedVocabulary.simpleIdToToken(id) ?: model.idToToken(id)
            if (token != null) {
                if (!skipSpecialTokens || !addedVocabulary.isSpecialToken(token)) {
                    tokens.add(token)
                }
            }
        }
        val dec = decoder
        return if (dec != null) {
            dec.decode(tokens)
        } else {
            Result.success(tokens.joinToString(" "))
        }
    }
}

/**
 * Builder for Tokenizer instances.
 */
public class TokenizerBuilder(
    public var model: Model? = null,
    public var normalizer: Normalizer? = null,
    public var preTokenizer: PreTokenizer? = null,
    public var postProcessor: PostProcessor? = null,
    public var decoder: Decoder? = null,
    public var addedVocabulary: AddedVocabulary = AddedVocabulary(),
    public var truncation: TruncationParams? = null,
    public var padding: PaddingParams? = null,
) {
    public fun withModel(model: Model): TokenizerBuilder = apply { this.model = model }

    public fun withNormalizer(normalizer: Normalizer?): TokenizerBuilder = apply { this.normalizer = normalizer }

    public fun withPreTokenizer(preTokenizer: PreTokenizer?): TokenizerBuilder = apply { this.preTokenizer = preTokenizer }

    public fun withPostProcessor(postProcessor: PostProcessor?): TokenizerBuilder = apply { this.postProcessor = postProcessor }

    public fun withDecoder(decoder: Decoder?): TokenizerBuilder = apply { this.decoder = decoder }

    public fun withAddedVocabulary(addedVocabulary: AddedVocabulary): TokenizerBuilder = apply { this.addedVocabulary = addedVocabulary }

    public fun withTruncation(truncation: TruncationParams?): TokenizerBuilder = apply { this.truncation = truncation }

    public fun withPadding(padding: PaddingParams?): TokenizerBuilder = apply { this.padding = padding }

    public fun build(): Result<Tokenizer> {
        val m = model ?: return Result.failure(IllegalStateException("Model missing."))
        return Result.success(
            Tokenizer(
                model = m,
                normalizer = normalizer,
                preTokenizer = preTokenizer,
                postProcessor = postProcessor,
                decoder = decoder,
                addedVocabulary = addedVocabulary,
                truncation = truncation,
                padding = padding,
            ),
        )
    }
}
