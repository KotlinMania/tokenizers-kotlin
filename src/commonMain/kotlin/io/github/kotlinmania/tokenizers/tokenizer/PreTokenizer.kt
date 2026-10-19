// port-lint: source tokenizer/pre_tokenizer.rs
package io.github.kotlinmania.tokenizers.tokenizer

/**
 * Various possible types of offsets.
 */
public enum class OffsetType {
    Byte,
    Char,
    None,
}

/**
 * Wrapper for a subpart of a `NormalizedString`.
 * Contains the underlying `NormalizedString` as well as optional `Token`s associated with this split.
 */
public data class Split(
    public val normalized: NormalizedString,
    public var tokens: List<Token>? = null,
) {
    public constructor(n: NormalizedString) : this(n, null)
}

/**
 * A split with its text, offsets, and optional tokens.
 */
public data class PreTokenizedSplit(
    public val text: String,
    public val offsets: Offsets,
    public val tokens: List<Token>?,
)

/**
 * The `PreTokenizer` is in charge of doing the pre-segmentation step.
 */
public interface PreTokenizer {
    public fun preTokenize(pretokenized: PreTokenizedString): Result<Unit>
}

/**
 * The `PreTokenizedString` is in charge of splitting an underlying string,
 * keeping track of offsets and providing ways to normalize and tokenize these splits.
 */
public class PreTokenizedString internal constructor(
    private val original: String,
    private var splits: MutableList<Split>,
) {
    public constructor(s: String) : this(
        original = s,
        splits = mutableListOf(Split(NormalizedString(s))),
    )

    public constructor(n: NormalizedString) : this(
        original = n.getOriginal(),
        splits = mutableListOf(Split(n)),
    )

    public fun getOriginal(): String = original

    public fun getSplitsList(): List<Split> = splits

    public fun split(splitFn: (Int, NormalizedString) -> Result<List<Split>>): Result<Unit> {
        val newSplits = mutableListOf<Split>()
        for ((i, originalSplit) in splits.withIndex()) {
            if (originalSplit.tokens != null) {
                newSplits.add(originalSplit)
                continue
            }

            val res = splitFn(i, originalSplit.normalized)
            if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
            for (split in res.getOrThrow()) {
                if (!split.normalized.isEmpty()) {
                    newSplits.add(split)
                }
            }
        }
        splits = newSplits
        return Result.success(Unit)
    }

    public fun normalize(normalizeFn: (NormalizedString) -> Result<Unit>): Result<Unit> {
        for (split in splits) {
            if (split.tokens == null) {
                val res = normalizeFn(split.normalized)
                if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
            }
        }
        return Result.success(Unit)
    }

    public fun tokenize(tokenizeFn: (NormalizedString) -> Result<List<Token>>): Result<Unit> {
        for (split in splits) {
            if (split.tokens == null) {
                val res = tokenizeFn(split.normalized)
                if (res.isFailure) return Result.failure(res.exceptionOrNull()!!)
                split.tokens = res.getOrThrow()
            }
        }
        return Result.success(Unit)
    }

    public fun intoEncoding(
        wordIdx: UInt?,
        typeId: UInt,
        offsetType: OffsetType,
    ): Result<Encoding> {
        if (splits.isEmpty()) {
            return Result.success(Encoding.default())
        }
        if (splits.any { it.tokens == null }) {
            return Result.failure(IllegalStateException("Split has not been tokenized, call `PreTokenizedString::tokenize` first"))
        }

        val offsetConverter =
            when (offsetType) {
                OffsetType.Char -> BytesToCharOffsetConverter(original)
                OffsetType.Byte, OffsetType.None -> null
            }

        if (offsetType == OffsetType.None) {
            val allIds = mutableListOf<UInt>()
            val allTokens = mutableListOf<String>()
            val allOffsets = mutableListOf<Offsets>()
            val allWords = mutableListOf<UInt?>()
            val allTypeIds = mutableListOf<UInt>()
            val allSpecial = mutableListOf<UInt>()
            val allAttention = mutableListOf<UInt>()

            for (split in splits) {
                for (token in split.tokens!!) {
                    allIds.add(token.id)
                    allTokens.add("")
                    allOffsets.add(Offsets(0, 0))
                    allWords.add(null)
                    allTypeIds.add(0u)
                    allSpecial.add(0u)
                    allAttention.add(1u)
                }
            }
            return Result.success(
                Encoding(
                    ids = allIds,
                    typeIds = allTypeIds,
                    tokens = allTokens,
                    words = allWords,
                    offsets = allOffsets,
                    specialTokensMask = allSpecial,
                    attentionMask = allAttention,
                ),
            )
        }

        val allIds = mutableListOf<UInt>()
        val allTokens = mutableListOf<String>()
        val allOffsets = mutableListOf<Offsets>()
        val allWords = mutableListOf<UInt?>()
        val allTypeIds = mutableListOf<UInt>()
        val allSpecial = mutableListOf<UInt>()
        val allAttention = mutableListOf<UInt>()

        for ((idx, split) in splits.withIndex()) {
            val normalized = split.normalized
            val baseOffsets = normalized.offsetsOriginal()

            for (token in split.tokens!!) {
                val converted =
                    normalized.convertOffsets(
                        NormalizerRange.Normalized(token.offsets.start until token.offsets.end),
                    )
                var tokenOffsets =
                    if (converted != null) {
                        Offsets(baseOffsets.start + converted.start, baseOffsets.start + converted.end)
                    } else {
                        token.offsets
                    }

                if (offsetConverter != null) {
                    tokenOffsets = offsetConverter.convert(tokenOffsets) ?: tokenOffsets
                }

                allIds.add(token.id)
                allTokens.add(token.value)
                allOffsets.add(tokenOffsets)
                allWords.add(wordIdx ?: idx.toUInt())
                allTypeIds.add(typeId)
                allSpecial.add(0u)
                allAttention.add(1u)
            }
        }

        return Result.success(
            Encoding(
                ids = allIds,
                typeIds = allTypeIds,
                tokens = allTokens,
                words = allWords,
                offsets = allOffsets,
                specialTokensMask = allSpecial,
                attentionMask = allAttention,
            ),
        )
    }

    public fun getSplits(
        offsetRef: OffsetReferential,
        offsetType: OffsetType,
    ): List<PreTokenizedSplit> {
        val offsetConverter =
            when (offsetType) {
                OffsetType.Char -> BytesToCharOffsetConverter(original)
                OffsetType.Byte, OffsetType.None -> null
            }

        var offset = 0
        val result = mutableListOf<PreTokenizedSplit>()
        for (split in splits) {
            var offsets =
                when (offsetRef) {
                    OffsetReferential.Original -> {
                        split.normalized.offsetsOriginal()
                    }

                    OffsetReferential.Normalized -> {
                        val l = split.normalized.len()
                        offset += l
                        Offsets(offset - l, offset)
                    }
                }

            if (offsetConverter != null) {
                offsets = offsetConverter.convert(offsets) ?: offsets
            }

            result.add(PreTokenizedSplit(split.normalized.get(), offsets, split.tokens))
        }
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreTokenizedString) return false
        return original == other.original && splits == other.splits
    }

    override fun hashCode(): Int = 31 * original.hashCode() + splits.hashCode()

    override fun toString(): String = "PreTokenizedString(original=\"$original\", splits=$splits)"
}

private class BytesToCharOffsetConverter(
    sequence: String,
) {
    private val map: Map<Int, Int>

    init {
        val m = HashMap<Int, Int>()
        val byteTable = utf8CharOffsets(sequence)
        var i = 0
        while (i < sequence.length) {
            val startByte = byteTable[i]
            val c = sequence[i]
            val step = if (c.isHighSurrogate() && i + 1 < sequence.length && sequence[i + 1].isLowSurrogate()) 2 else 1
            val endByte = byteTable[i + step]
            for (b in startByte until endByte) {
                m[b] = i
            }
            i += step
        }
        map = m
    }

    fun convert(offsets: Offsets): Offsets? {
        val start = map[offsets.start]
        val end = map[offsets.end]
        return when {
            start != null && end != null -> {
                Offsets(start, end)
            }

            start != null && end == null -> {
                val last = map[offsets.end - 1] ?: (start + 1)
                Offsets(start, last + 1)
            }

            else -> {
                null
            }
        }
    }
}
