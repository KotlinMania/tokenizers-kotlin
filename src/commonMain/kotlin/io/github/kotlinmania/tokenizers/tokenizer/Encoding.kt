// port-lint: source tokenizer/encoding.rs
package io.github.kotlinmania.tokenizers.tokenizer

import io.github.kotlinmania.tokenizers.utils.PaddingDirection
import io.github.kotlinmania.tokenizers.utils.TruncationDirection
import kotlin.math.min

/**
 * Represents the output of a Tokenizer.
 */
public class Encoding(
    ids: List<UInt> = emptyList(),
    typeIds: List<UInt> = emptyList(),
    tokens: List<String> = emptyList(),
    words: List<UInt?> = emptyList(),
    offsets: List<Offsets> = emptyList(),
    specialTokensMask: List<UInt> = emptyList(),
    attentionMask: List<UInt> = emptyList(),
    overflowing: List<Encoding> = emptyList(),
    sequenceRanges: Map<Int, IntRange> = emptyMap(),
) {
    private var ids: MutableList<UInt> = ids.toMutableList()
    private var typeIds: MutableList<UInt> = typeIds.toMutableList()
    private var tokens: MutableList<String> = tokens.toMutableList()
    private var words: MutableList<UInt?> = words.toMutableList()
    private var offsets: MutableList<Offsets> = offsets.toMutableList()
    private var specialTokensMask: MutableList<UInt> = specialTokensMask.toMutableList()
    private var attentionMask: MutableList<UInt> = attentionMask.toMutableList()
    private var overflowing: MutableList<Encoding> = overflowing.toMutableList()
    private var sequenceRanges: MutableMap<Int, IntRange> = sequenceRanges.toMutableMap()

    public fun isEmpty(): Boolean = ids.isEmpty()

    public fun len(): Int = ids.size

    public fun nSequences(): Int = if (sequenceRanges.isEmpty()) 1 else sequenceRanges.size

    public fun setSequenceId(sequenceId: Int) {
        sequenceRanges[sequenceId] = 0 until len()
    }

    public fun getTokens(): List<String> = tokens

    public fun getWordIds(): List<UInt?> = words

    internal fun getWordIdsMut(): MutableList<UInt?> = words

    public fun getSequenceIds(): List<Int?> {
        val sequences = MutableList<Int?>(len()) { null }
        for (seqId in 0 until nSequences()) {
            val range = sequenceRange(seqId)
            for (idx in range) {
                if (idx < sequences.size) {
                    sequences[idx] = seqId
                }
            }
        }
        return sequences
    }

    public fun getIds(): List<UInt> = ids

    public fun getTypeIds(): List<UInt> = typeIds

    public fun setTypeIds(typeIds: List<UInt>) {
        this.typeIds = typeIds.toMutableList()
    }

    public fun getOffsets(): List<Offsets> = offsets

    internal fun getOffsetsMut(): MutableList<Offsets> = offsets

    public fun getSpecialTokensMask(): List<UInt> = specialTokensMask

    public fun getAttentionMask(): List<UInt> = attentionMask

    public fun getOverflowing(): List<Encoding> = overflowing

    public fun setOverflowing(overflowing: List<Encoding>) {
        this.overflowing = overflowing.toMutableList()
    }

    internal fun getOverflowingMut(): MutableList<Encoding> = overflowing

    public fun takeOverflowing(): List<Encoding> {
        val old = overflowing.toList()
        overflowing.clear()
        return old
    }

    public fun getSequenceRanges(): Map<Int, IntRange> = sequenceRanges

    private fun sequenceRange(sequenceId: Int): IntRange =
        sequenceRanges[sequenceId] ?: (0 until len())

    public fun tokenToSequence(token: Int): Int? {
        return if (token >= len()) {
            null
        } else if (sequenceRanges.isEmpty()) {
            0
        } else {
            sequenceRanges.entries.find { it.value.contains(token) }?.key
        }
    }

    public fun wordToTokens(word: UInt, sequenceId: Int): TokenIndices? {
        val seqRange = sequenceRange(sequenceId)
        if (seqRange.first >= words.size) return null
        val subWords = words.subList(seqRange.first, min(seqRange.last + 1, words.size))
        var start: Int? = null
        var end: Int? = null

        for (i in subWords.indices) {
            val w = subWords[i]
            if (w != null && w > word) break
            if (w == word) {
                if (start == null) start = i
                end = i + 1
            }
        }

        return if (start != null && end != null) {
            TokenIndices(seqRange.first + start, seqRange.first + end)
        } else {
            null
        }
    }

    public fun wordToChars(word: UInt, sequenceId: Int): Offsets? {
        val tokensRange = wordToTokens(word, sequenceId) ?: return null
        val (start, end) = tokensRange
        return if (end == 0 || start >= offsets.size || end - 1 >= offsets.size) {
            null
        } else {
            Offsets(offsets[start].start, offsets[end - 1].end)
        }
    }

    public fun tokenToChars(token: Int): SequenceOffsets? {
        val seqId = tokenToSequence(token) ?: return null
        val off = offsets.getOrNull(token) ?: return null
        return SequenceOffsets(seqId, off)
    }

    public fun tokenToWord(token: Int): SequenceWord? {
        val seqId = tokenToSequence(token) ?: return null
        val w = words.getOrNull(token) ?: return null
        return SequenceWord(seqId, w)
    }

    public fun charToToken(pos: Int, sequenceId: Int): Int? {
        val seqRange = sequenceRange(sequenceId)
        val end = min(seqRange.last + 1, offsets.size)
        for (i in seqRange.first until end) {
            val off = offsets[i]
            if (pos >= off.start && pos < off.end) {
                return i
            }
        }
        return null
    }

    public fun charToWord(pos: Int, sequenceId: Int): UInt? {
        val token = charToToken(pos, sequenceId) ?: return null
        return tokenToWord(token)?.wordId
    }

    public fun truncate(maxLen: Int, stride: Int, direction: TruncationDirection) {
        val encodingLen = ids.size
        if (maxLen >= encodingLen) {
            return
        }

        if (maxLen == 0) {
            val old = this.clone()
            ids.clear()
            typeIds.clear()
            tokens.clear()
            words.clear()
            offsets.clear()
            specialTokensMask.clear()
            attentionMask.clear()
            sequenceRanges.clear()
            overflowing.add(old)
            return
        }

        require(stride < maxLen) {
            "stride must be strictly less than maxLen"
        }

        sequenceRanges.clear()

        val offset = maxLen - stride
        val partsRanges = mutableListOf<Pair<Int, Int>>()

        when (direction) {
            TruncationDirection.Right -> {
                var start = 0
                while (start < encodingLen) {
                    val stop = min(start + maxLen, encodingLen)
                    partsRanges.add(Pair(start, stop))
                    if (stop == encodingLen) break
                    start += offset
                }
            }
            TruncationDirection.Left -> {
                var stop = encodingLen
                while (stop > 0) {
                    val start = if (stop > maxLen) stop - maxLen else 0
                    partsRanges.add(Pair(start, stop))
                    if (start == 0) break
                    stop -= offset
                }
            }
        }

        if (partsRanges.isEmpty()) return

        val firstRange = partsRanges[0]
        val (firstStart, firstStop) = firstRange
        val newEncoding = Encoding(
            ids = ids.subList(firstStart, firstStop).toMutableList(),
            typeIds = typeIds.subList(firstStart, firstStop).toMutableList(),
            tokens = tokens.subList(firstStart, firstStop).toMutableList(),
            words = words.subList(firstStart, firstStop).toMutableList(),
            offsets = offsets.subList(firstStart, firstStop).toMutableList(),
            specialTokensMask = specialTokensMask.subList(firstStart, firstStop).toMutableList(),
            attentionMask = attentionMask.subList(firstStart, firstStop).toMutableList(),
            overflowing = mutableListOf(),
            sequenceRanges = mutableMapOf(),
        )

        for (i in 1 until partsRanges.size) {
            val (s, e) = partsRanges[i]
            newEncoding.overflowing.add(
                Encoding(
                    ids = ids.subList(s, e).toMutableList(),
                    typeIds = typeIds.subList(s, e).toMutableList(),
                    tokens = tokens.subList(s, e).toMutableList(),
                    words = words.subList(s, e).toMutableList(),
                    offsets = offsets.subList(s, e).toMutableList(),
                    specialTokensMask = specialTokensMask.subList(s, e).toMutableList(),
                    attentionMask = attentionMask.subList(s, e).toMutableList(),
                    overflowing = mutableListOf(),
                    sequenceRanges = mutableMapOf(),
                ),
            )
        }

        this.ids = newEncoding.ids
        this.typeIds = newEncoding.typeIds
        this.tokens = newEncoding.tokens
        this.words = newEncoding.words
        this.offsets = newEncoding.offsets
        this.specialTokensMask = newEncoding.specialTokensMask
        this.attentionMask = newEncoding.attentionMask
        this.overflowing = newEncoding.overflowing
        this.sequenceRanges = newEncoding.sequenceRanges
    }

    public fun mergeWith(pair: Encoding, growingOffsets: Boolean = false) {
        val overflowings = mutableListOf<Encoding>()

        for (selfO in overflowing) {
            val nEncoding = selfO.clone()
            nEncoding.mergeWith(pair.clone(), growingOffsets)
            overflowings.add(nEncoding)

            for (otherO in pair.overflowing) {
                val nEncoding2 = selfO.clone()
                nEncoding2.mergeWith(otherO.clone(), growingOffsets)
                overflowings.add(nEncoding2)
            }
        }

        for (otherO in pair.overflowing) {
            val nEncoding = this.clone()
            nEncoding.mergeWith(otherO.clone(), growingOffsets)
            overflowings.add(nEncoding)
        }

        val originalSelfLen = len()

        for ((seqId, range) in pair.sequenceRanges) {
            sequenceRanges[seqId] = (originalSelfLen + range.first)..(originalSelfLen + range.last)
        }

        ids.addAll(pair.ids)
        typeIds.addAll(pair.typeIds)
        tokens.addAll(pair.tokens)
        words.addAll(pair.words)

        val startingOffset = if (growingOffsets) {
            offsets.lastOrNull()?.end ?: 0
        } else {
            0
        }

        for (off in pair.offsets) {
            offsets.add(Offsets(off.start + startingOffset, off.end + startingOffset))
        }

        specialTokensMask.addAll(pair.specialTokensMask)
        attentionMask.addAll(pair.attentionMask)
        this.overflowing = overflowings
    }

    public fun pad(
        targetLength: Int,
        padId: UInt,
        padTypeId: UInt,
        padToken: String,
        direction: PaddingDirection,
    ) {
        for (enc in overflowing) {
            enc.pad(targetLength, padId, padTypeId, padToken, direction)
        }

        if (ids.size >= targetLength) return

        val padLength = targetLength - ids.size

        when (direction) {
            PaddingDirection.Left -> {
                val newIds = MutableList(padLength) { padId }.apply { addAll(ids) }
                val newTypeIds = MutableList(padLength) { padTypeId }.apply { addAll(typeIds) }
                val newTokens = MutableList(padLength) { padToken }.apply { addAll(tokens) }
                val newWords = MutableList<UInt?>(padLength) { null }.apply { addAll(words) }
                val newAttention = MutableList(padLength) { 0u }.apply { addAll(attentionMask) }
                val newSpecial = MutableList(padLength) { 1u }.apply { addAll(specialTokensMask) }
                val newOffsets = MutableList(padLength) { Offsets(0, 0) }.apply { addAll(offsets) }

                ids = newIds
                typeIds = newTypeIds
                tokens = newTokens
                words = newWords
                attentionMask = newAttention
                specialTokensMask = newSpecial
                offsets = newOffsets

                val updatedRanges = mutableMapOf<Int, IntRange>()
                for ((seqId, range) in sequenceRanges) {
                    updatedRanges[seqId] = (range.first + padLength)..(range.last + padLength)
                }
                sequenceRanges = updatedRanges
            }
            PaddingDirection.Right -> {
                repeat(padLength) {
                    ids.add(padId)
                    typeIds.add(padTypeId)
                    tokens.add(padToken)
                    words.add(null)
                    attentionMask.add(0u)
                    specialTokensMask.add(1u)
                    offsets.add(Offsets(0, 0))
                }
            }
        }
    }

    public fun clone(): Encoding =
        Encoding(
            ids = ids,
            typeIds = typeIds,
            tokens = tokens,
            words = words,
            offsets = offsets,
            specialTokensMask = specialTokensMask,
            attentionMask = attentionMask,
            overflowing = overflowing.map { it.clone() },
            sequenceRanges = sequenceRanges,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Encoding) return false
        return ids == other.ids &&
            typeIds == other.typeIds &&
            tokens == other.tokens &&
            words == other.words &&
            offsets == other.offsets &&
            specialTokensMask == other.specialTokensMask &&
            attentionMask == other.attentionMask &&
            overflowing == other.overflowing &&
            sequenceRanges == other.sequenceRanges
    }

    override fun hashCode(): Int {
        var result = ids.hashCode()
        result = 31 * result + typeIds.hashCode()
        result = 31 * result + tokens.hashCode()
        result = 31 * result + words.hashCode()
        result = 31 * result + offsets.hashCode()
        result = 31 * result + specialTokensMask.hashCode()
        result = 31 * result + attentionMask.hashCode()
        result = 31 * result + overflowing.hashCode()
        result = 31 * result + sequenceRanges.hashCode()
        return result
    }

    public companion object {
        public fun new(
            ids: List<UInt> = emptyList(),
            typeIds: List<UInt> = emptyList(),
            tokens: List<String> = emptyList(),
            words: List<UInt?> = emptyList(),
            offsets: List<Offsets> = emptyList(),
            specialTokensMask: List<UInt> = emptyList(),
            attentionMask: List<UInt> = emptyList(),
            overflowing: List<Encoding> = emptyList(),
            sequenceRanges: Map<Int, IntRange> = emptyMap(),
        ): Encoding =
            Encoding(
                ids = ids,
                typeIds = typeIds,
                tokens = tokens,
                words = words,
                offsets = offsets,
                specialTokensMask = specialTokensMask,
                attentionMask = attentionMask,
                overflowing = overflowing,
                sequenceRanges = sequenceRanges,
            )

        public fun withCapacity(len: Int): Encoding = Encoding()

        public fun fromTokens(tokens: List<Token>, typeId: UInt): Encoding {
            val length = tokens.size
            val ids = mutableListOf<UInt>()
            val tokenStrings = mutableListOf<String>()
            val offsets = mutableListOf<Offsets>()

            for (t in tokens) {
                ids.add(t.id)
                tokenStrings.add(t.value)
                offsets.add(t.offsets)
            }

            return Encoding(
                ids = ids,
                tokens = tokenStrings,
                offsets = offsets,
                words = MutableList(length) { null },
                typeIds = MutableList(length) { typeId },
                attentionMask = MutableList(length) { 1u },
                specialTokensMask = MutableList(length) { 0u },
                overflowing = mutableListOf(),
                sequenceRanges = mutableMapOf(),
            )
        }

        public fun merge(encodings: Iterable<Encoding>, growingOffsets: Boolean = false): Encoding {
            val encList = encodings.toList()
            if (encList.isEmpty()) return Encoding()
            val result = encList[0].clone()
            for (i in 1 until encList.size) {
                result.mergeWith(encList[i], growingOffsets)
            }
            return result
        }

        public fun default(): Encoding = Encoding()
    }
}
