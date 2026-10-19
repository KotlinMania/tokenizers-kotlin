// port-lint: source tokenizer/normalizer.rs
package io.github.kotlinmania.tokenizers.tokenizer

/**
 * The possible offsets referential.
 */
public enum class OffsetReferential {
    Original,
    Normalized,
}

/**
 * Represents a range usable by the NormalizedString to index its content.
 * A range can use indices relative to either the Original or Normalized string.
 */
public sealed class NormalizerRange {
    public data class Original(
        public val range: IntRange,
    ) : NormalizerRange()

    public data class Normalized(
        public val range: IntRange,
    ) : NormalizerRange()

    public companion object {
        public fun original(start: Int, end: Int): NormalizerRange = Original(start until end)

        public fun normalized(start: Int, end: Int): NormalizerRange = Normalized(start until end)

        public fun original(offsets: Offsets): NormalizerRange = Original(offsets.start until offsets.end)

        public fun normalized(offsets: Offsets): NormalizerRange = Normalized(offsets.start until offsets.end)
    }
}

/**
 * Defines the expected behavior for the delimiter of a Split Pattern.
 */
public enum class SplitDelimiterBehavior {
    Removed,
    Isolated,
    MergedWithPrevious,
    MergedWithNext,
    Contiguous,
}

/**
 * Takes care of pre-processing strings.
 */
public interface Normalizer {
    public fun normalize(normalized: NormalizedString): Result<Unit>
}

/**
 * A `NormalizedString` takes care of processing an "original" string to modify
 * it and obtain a "normalized" string. It keeps both versions of the string,
 * alignment information between both, and provides an interface to retrieve
 * ranges of each string using offsets from any of them.
 */
public class NormalizedString internal constructor(
    private val original: String,
    private var normalized: String,
    private var alignments: MutableList<Offsets>,
    private var originalShift: Int = 0,
) {
    public constructor(s: String) : this(
        original = s,
        normalized = s,
        alignments = buildInitialAlignments(s),
        originalShift = 0,
    )

    public companion object {
        private fun buildInitialAlignments(s: String): MutableList<Offsets> {
            val byteTable = utf8CharOffsets(s)
            val alignments = ArrayList<Offsets>(byteTable[s.length])
            var i = 0
            while (i < s.length) {
                val startByte = byteTable[i]
                val c = s[i]
                val step = if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) 2 else 1
                val endByte = byteTable[i + step]
                val offsets = Offsets(startByte, endByte)
                repeat(endByte - startByte) {
                    alignments.add(offsets)
                }
                i += step
            }
            return alignments
        }

        private fun utf8Len(c: Char): Int =
            when {
                c.code <= 0x7F -> 1
                c.code <= 0x7FF -> 2
                else -> 3
            }

        private fun byteToCharIndex(byteTable: IntArray, byteOffset: Int): Int {
            var low = 0
            var high = byteTable.size - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val midVal = byteTable[mid]
                if (midVal < byteOffset) {
                    low = mid + 1
                } else if (midVal > byteOffset) {
                    high = mid - 1
                } else {
                    return mid
                }
            }
            return low
        }
    }

    public fun get(): String = normalized

    public fun getOriginal(): String = original

    public fun offsetsOriginal(): Offsets =
        Offsets(originalShift, originalShift + lenOriginal())

    public fun len(): Int = alignments.size

    public fun lenOriginal(): Int {
        val table = utf8CharOffsets(original)
        return table[original.length]
    }

    public fun isEmpty(): Boolean = normalized.isEmpty()

    private fun validateRange(range: NormalizerRange): NormalizerRange? {
        val normByteTable = utf8CharOffsets(normalized)
        val origByteTable = utf8CharOffsets(original)
        return when (range) {
            is NormalizerRange.Original -> {
                val r = range.range
                val start = r.first
                val end = r.last + 1
                if (start < 0 || end > origByteTable[original.length] || start > end) {
                    null
                } else {
                    range
                }
            }

            is NormalizerRange.Normalized -> {
                val r = range.range
                val start = r.first
                val end = r.last + 1
                if (start < 0 || end > normByteTable[normalized.length] || start > end) {
                    null
                } else {
                    range
                }
            }
        }
    }

    public fun convertOffsets(range: NormalizerRange): Offsets? {
        val lenOrig = lenOriginal()
        val lenNorm = len()
        val (target, isOriginal) =
            when (range) {
                is NormalizerRange.Original -> Pair(Offsets(range.range.first, range.range.last + 1), true)
                is NormalizerRange.Normalized -> Pair(Offsets(range.range.first, range.range.last + 1), false)
            }

        if (target.start == target.end) return target
        if (target.start > target.end) return null
        if (isOriginal && original.isEmpty() && target.start == 0 && target.end == 0) return Offsets(0, lenNorm)
        if (!isOriginal && normalized.isEmpty() && target.start == 0 && target.end == 0) return Offsets(0, lenOrig)

        return if (isOriginal) {
            var start: Int? = null
            var end: Int? = null
            for (i in alignments.indices) {
                val alignment = alignments[i]
                if (target.end < alignment.end) break
                if (start == null && target.start <= alignment.start) {
                    if (alignment.start != alignment.end) {
                        start = i
                    }
                }
                if (target.end >= alignment.end) {
                    end = i + 1
                }
            }
            when {
                start != null && end == null -> Offsets(start, start)
                start == null && end != null -> Offsets(end, end)
                start != null && end != null -> Offsets(start, end)
                else -> null
            }
        } else {
            if (target.start < 0 || target.end > alignments.size || target.start > target.end) {
                null
            } else {
                val start = alignments[target.start].start
                val end = alignments[target.end - 1].end
                Offsets(start, end)
            }
        }
    }

    public fun getRange(range: NormalizerRange): String? {
        val normByteTable = utf8CharOffsets(normalized)
        val byteRange =
            when (range) {
                is NormalizerRange.Original -> convertOffsets(range) ?: return null
                is NormalizerRange.Normalized -> Offsets(range.range.first, range.range.last + 1)
            }
        val startChar = byteToCharIndex(normByteTable, byteRange.start)
        val endChar = byteToCharIndex(normByteTable, byteRange.end)
        return if (startChar in 0..normalized.length && endChar in 0..normalized.length && startChar <= endChar) {
            normalized.substring(startChar, endChar)
        } else {
            null
        }
    }

    public fun getRangeOriginal(range: NormalizerRange): String? {
        val origByteTable = utf8CharOffsets(original)
        val byteRange =
            when (range) {
                is NormalizerRange.Original -> Offsets(range.range.first, range.range.last + 1)
                is NormalizerRange.Normalized -> convertOffsets(range) ?: return null
            }
        val startChar = byteToCharIndex(origByteTable, byteRange.start)
        val endChar = byteToCharIndex(origByteTable, byteRange.end)
        return if (startChar in 0..original.length && endChar in 0..original.length && startChar <= endChar) {
            original.substring(startChar, endChar)
        } else {
            null
        }
    }

    public fun slice(range: NormalizerRange): NormalizedString? {
        val fullRange = validateRange(range) ?: return null
        val (normalizedRange, originalRange) =
            when (fullRange) {
                is NormalizerRange.Original -> {
                    val conv = convertOffsets(fullRange) ?: return null
                    Pair(conv, Offsets(fullRange.range.first, fullRange.range.last + 1))
                }

                is NormalizerRange.Normalized -> {
                    val conv = convertOffsets(fullRange) ?: return null
                    Pair(Offsets(fullRange.range.first, fullRange.range.last + 1), conv)
                }
            }
        val nShift = originalRange.start
        val origSlice = getRangeOriginal(NormalizerRange.Original(originalRange.start until originalRange.end)).orEmpty()
        val normSlice = getRange(NormalizerRange.Normalized(normalizedRange.start until normalizedRange.end)).orEmpty()
        if (normalizedRange.start < 0 || normalizedRange.end > alignments.size) return null
        val subAlignments = ArrayList<Offsets>(normalizedRange.end - normalizedRange.start)
        for (i in normalizedRange.start until normalizedRange.end) {
            val a = alignments[i]
            subAlignments.add(Offsets(a.start - nShift, a.end - nShift))
        }
        return NormalizedString(
            original = origSlice,
            normalized = normSlice,
            alignments = subAlignments,
            originalShift = originalShift + originalRange.start,
        )
    }

    public fun transformRange(range: NormalizerRange, dest: Iterable<Pair<Char, Int>>, initialOffset: Int = 0) {
        val nRange =
            when (range) {
                is NormalizerRange.Normalized -> Offsets(range.range.first, range.range.last + 1)
                is NormalizerRange.Original -> convertOffsets(range) ?: return
            }
        val normByteTable = utf8CharOffsets(normalized)
        val startChar = byteToCharIndex(normByteTable, nRange.start)
        val endChar = byteToCharIndex(normByteTable, nRange.end)
        val sliceChars = normalized.substring(startChar, endChar).toList()
        var replacedNormalizedIdx = 0

        var initialRemoved = 0
        repeat(initialOffset) {
            if (replacedNormalizedIdx < sliceChars.size) {
                val c = sliceChars[replacedNormalizedIdx++]
                initialRemoved += utf8Len(c)
            }
        }

        var offset = initialRemoved + nRange.start
        val newAlignments = ArrayList<Offsets>(nRange.end - nRange.start)
        val newNormBuilder = StringBuilder()

        for ((c, changes) in dest) {
            val idx = offset
            val align =
                if (changes > 0) {
                    if (idx < 1) Offsets(0, 0) else alignments[idx - 1]
                } else {
                    alignments[idx]
                }

            val replacedChar =
                if (changes <= 0 && replacedNormalizedIdx < sliceChars.size) {
                    sliceChars[replacedNormalizedIdx++]
                } else {
                    null
                }
            val replacedCharSize = replacedChar?.let { utf8Len(it) } ?: 0

            var totalBytesToRemove = 0
            if (changes < 0) {
                val toRemove = -changes
                repeat(toRemove) {
                    if (replacedNormalizedIdx < sliceChars.size) {
                        totalBytesToRemove += utf8Len(sliceChars[replacedNormalizedIdx++])
                    }
                }
            }

            offset += replacedCharSize
            offset += totalBytesToRemove

            val cLen = utf8Len(c)
            repeat(cLen) {
                newAlignments.add(align)
            }
            newNormBuilder.append(c)
        }

        val beforeNorm = normalized.substring(0, startChar)
        val afterNorm = normalized.substring(endChar)
        normalized = beforeNorm + newNormBuilder.toString() + afterNorm

        val beforeAlign = alignments.subList(0, nRange.start)
        val afterAlign = alignments.subList(nRange.end, alignments.size)
        val combinedAlign = ArrayList<Offsets>(beforeAlign.size + newAlignments.size + afterAlign.size)
        combinedAlign.addAll(beforeAlign)
        combinedAlign.addAll(newAlignments)
        combinedAlign.addAll(afterAlign)
        alignments = combinedAlign
    }

    public fun transform(dest: Iterable<Pair<Char, Int>>, initialOffset: Int = 0) {
        transformRange(NormalizerRange.Normalized(0 until len()), dest, initialOffset)
    }

    internal fun filter(keep: (Char) -> Boolean): NormalizedString {
        var removed = 0
        var removedStart = 0
        val transforms = ArrayList<Pair<Char, Int>>(normalized.length)
        var lastC: Char? = null
        for (c in normalized) {
            if (keep(c)) {
                if (lastC != null) {
                    transforms.add(Pair(lastC, -removed))
                } else {
                    removedStart = removed
                }
                lastC = c
                removed = 0
            } else {
                removed += 1
            }
        }
        if (lastC != null) {
            transforms.add(Pair(lastC, -removed))
        }
        transform(transforms, removedStart)
        return this
    }

    public fun prepend(s: String): NormalizedString {
        if (normalized.isNotEmpty()) {
            val next = normalized[0]
            val nextLen = utf8Len(next)
            val transformations = ArrayList<Pair<Char, Int>>()
            s.forEachIndexed { i, c ->
                transformations.add(Pair(c, if (i != 0) 1 else 0))
            }
            transformations.add(Pair(next, 1))
            transformRange(NormalizerRange.Normalized(0 until nextLen), transformations, 0)
        }
        return this
    }

    public fun append(s: String): NormalizedString {
        if (normalized.isNotEmpty()) {
            val lastChar = normalized.last()
            val lastByte = alignments.size - utf8Len(lastChar)
            val transformations = ArrayList<Pair<Char, Int>>()
            transformations.add(Pair(lastChar, 0))
            for (c in s) {
                transformations.add(Pair(c, 1))
            }
            transformRange(NormalizerRange.Normalized(lastByte until alignments.size), transformations, 0)
        } else {
            val transformations = s.map { Pair(it, 1) }
            transformRange(NormalizerRange.Normalized(IntRange(0, -1)), transformations, 0)
        }
        return this
    }

    internal fun map(transform: (Char) -> Char): NormalizedString {
        val transformations = normalized.map { Pair(transform(it), 0) }
        transform(transformations, 0)
        return this
    }

    internal fun forEach(action: (Char) -> Unit): NormalizedString {
        normalized.forEach(action)
        return this
    }

    public fun lowercase(): NormalizedString {
        val newChars = ArrayList<Pair<Char, Int>>()
        for (c in normalized) {
            val lower = c.lowercase()
            lower.forEachIndexed { index, lc ->
                newChars.add(Pair(lc, if (index > 0) 1 else 0))
            }
        }
        transform(newChars, 0)
        return this
    }

    public fun uppercase(): NormalizedString {
        val newChars = ArrayList<Pair<Char, Int>>()
        for (c in normalized) {
            val upper = c.uppercase()
            upper.forEachIndexed { index, uc ->
                newChars.add(Pair(uc, if (index > 0) 1 else 0))
            }
        }
        transform(newChars, 0)
        return this
    }

    public fun replace(pattern: Pattern, content: String): Result<Unit> {
        val matchesRes = pattern.findMatches(normalized)
        if (matchesRes.isFailure) return Result.failure(matchesRes.exceptionOrNull()!!)
        val matches = matchesRes.getOrThrow()

        val normByteTable = utf8CharOffsets(normalized)
        val newNormalized = StringBuilder()
        val newAlignments = mutableListOf<Offsets>()
        var lastEnd = 0

        for (match in matches) {
            if (match.isMatch) {
                val start = match.offsets.start
                val end = match.offsets.end

                if (lastEnd < start) {
                    val startChar = byteToCharIndex(normByteTable, lastEnd)
                    val endChar = byteToCharIndex(normByteTable, start)
                    newNormalized.append(normalized.substring(startChar, endChar))
                    newAlignments.addAll(alignments.subList(lastEnd, start))
                }

                for (c in content) {
                    val idx = start
                    val align = if (idx < 1) Offsets(0, 0) else alignments[idx - 1]
                    val cLen = utf8Len(c)
                    repeat(cLen) {
                        newAlignments.add(align)
                    }
                    newNormalized.append(c)
                }
                lastEnd = end
            }
        }

        if (lastEnd < alignments.size) {
            val startChar = byteToCharIndex(normByteTable, lastEnd)
            newNormalized.append(normalized.substring(startChar))
            newAlignments.addAll(alignments.subList(lastEnd, alignments.size))
        }

        normalized = newNormalized.toString()
        alignments = newAlignments
        return Result.success(Unit)
    }

    public fun clear(): Int {
        val l = len()
        transform(emptyList(), l)
        return l
    }

    public fun split(pattern: Pattern, behavior: SplitDelimiterBehavior): Result<List<NormalizedString>> {
        val matchesRes = pattern.findMatches(normalized)
        if (matchesRes.isFailure) return Result.failure(matchesRes.exceptionOrNull()!!)
        val matches = matchesRes.getOrThrow()

        val splits =
            when (behavior) {
                SplitDelimiterBehavior.Isolated -> {
                    matches.map { Pair(it.offsets, false) }
                }

                SplitDelimiterBehavior.Removed -> {
                    matches.map { Pair(it.offsets, it.isMatch) }
                }

                SplitDelimiterBehavior.Contiguous -> {
                    var previousMatch = false
                    val acc = mutableListOf<Pair<Offsets, Boolean>>()
                    for (m in matches) {
                        if (m.isMatch == previousMatch) {
                            if (acc.isNotEmpty()) {
                                val last = acc.removeAt(acc.size - 1)
                                acc.add(Pair(Offsets(last.first.start, m.offsets.end), false))
                            } else {
                                acc.add(Pair(m.offsets, false))
                            }
                        } else {
                            acc.add(Pair(m.offsets, false))
                        }
                        previousMatch = m.isMatch
                    }
                    acc
                }

                SplitDelimiterBehavior.MergedWithPrevious -> {
                    var previousMatch = false
                    val acc = mutableListOf<Pair<Offsets, Boolean>>()
                    for (m in matches) {
                        if (m.isMatch && !previousMatch) {
                            if (acc.isNotEmpty()) {
                                val last = acc.removeAt(acc.size - 1)
                                acc.add(Pair(Offsets(last.first.start, m.offsets.end), false))
                            } else {
                                acc.add(Pair(m.offsets, false))
                            }
                        } else {
                            acc.add(Pair(m.offsets, false))
                        }
                        previousMatch = m.isMatch
                    }
                    acc
                }

                SplitDelimiterBehavior.MergedWithNext -> {
                    var previousMatch = false
                    val acc = mutableListOf<Pair<Offsets, Boolean>>()
                    for (m in matches.reversed()) {
                        if (m.isMatch && !previousMatch) {
                            if (acc.isNotEmpty()) {
                                val last = acc.removeAt(acc.size - 1)
                                acc.add(Pair(Offsets(m.offsets.start, last.first.end), false))
                            } else {
                                acc.add(Pair(m.offsets, false))
                            }
                        } else {
                            acc.add(Pair(m.offsets, false))
                        }
                        previousMatch = m.isMatch
                    }
                    acc.reversed()
                }
            }

        val result = mutableListOf<NormalizedString>()
        for ((offsets, remove) in splits) {
            if (!remove) {
                val sliced =
                    slice(NormalizerRange.Normalized(offsets.start until offsets.end))
                        ?: return Result.failure(IllegalStateException("NormalizedString bad split"))
                result.add(sliced)
            }
        }
        return Result.success(result)
    }

    public fun lstrip(): NormalizedString = lrstrip(left = true, right = false)

    public fun rstrip(): NormalizedString = lrstrip(left = false, right = true)

    public fun strip(): NormalizedString = lrstrip(left = true, right = true)

    private fun lrstrip(left: Boolean, right: Boolean): NormalizedString {
        val leadingSpaces = if (left) normalized.takeWhile { it.isWhitespace() }.length else 0
        val trailingSpaces = if (right) normalized.takeLastWhile { it.isWhitespace() }.length else 0

        if (leadingSpaces > 0 || trailingSpaces > 0) {
            val count = normalized.length
            val transformations = mutableListOf<Pair<Char, Int>>()
            for (i in 0 until count) {
                val c = normalized[i]
                if (i < leadingSpaces || i >= count - trailingSpaces) {
                    // dropped
                } else if (i == count - trailingSpaces - 1) {
                    transformations.add(Pair(c, -trailingSpaces))
                } else {
                    transformations.add(Pair(c, 0))
                }
            }
            transform(transformations, leadingSpaces)
        }
        return this
    }

    public fun alignmentsOriginal(): List<Offsets> {
        if (alignments.isEmpty()) return emptyList()
        val alignmentsOriginal = mutableListOf<Offsets>()
        val start = alignments[0].start
        if (start != 0) {
            repeat(start) {
                alignmentsOriginal.add(Offsets(0, 0))
            }
        }
        var last = Pair(alignments[0].start, alignments[0].end)
        var offset = 0
        var length = 0
        for (a in alignments) {
            val cur = Pair(a.start, a.end)
            if (last == cur) {
                length += 1
            } else {
                if (a.start < last.second) {
                    throw IllegalStateException("We can't have overlapping ranges.")
                }
                val gap = last.second - last.first
                repeat(gap) {
                    alignmentsOriginal.add(Offsets(offset, offset + length))
                }
                offset += length
                length = 1
                val between = a.start - last.second
                repeat(between) {
                    alignmentsOriginal.add(Offsets(offset, offset))
                }
            }
            last = cur
        }
        val lastGap = last.second - last.first
        repeat(lastGap) {
            alignmentsOriginal.add(Offsets(offset, offset + length))
        }
        offset += length
        val origLen = lenOriginal()
        val remaining = origLen - alignmentsOriginal.size
        repeat(remaining) {
            alignmentsOriginal.add(Offsets(offset, offset))
        }
        return alignmentsOriginal
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NormalizedString) return false
        return original == other.original &&
            normalized == other.normalized &&
            alignments == other.alignments &&
            originalShift == other.originalShift
    }

    override fun hashCode(): Int {
        var result = original.hashCode()
        result = 31 * result + normalized.hashCode()
        result = 31 * result + alignments.hashCode()
        result = 31 * result + originalShift
        return result
    }

    override fun toString(): String =
        "NormalizedString(original=\"$original\", normalized=\"$normalized\", alignments=$alignments, originalShift=$originalShift)"
}

public fun getRangeOf(s: String, start: Int, end: Int): String? {
    val len = s.length
    if (start == 0 && end == 0) return ""
    if (start >= len || end > len || start >= end) return null
    return s.substring(start, end)
}

public fun bytesToChar(s: String, range: Offsets): Offsets? {
    if (range.start == 0 && range.end == 0) return Offsets(0, 0)
    val table = utf8CharOffsets(s)
    var start: Int? = null
    var end: Int? = null
    var i = 0
    while (i < s.length) {
        val b = table[i]
        val c = s[i]
        val step = if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) 2 else 1
        val cLen = table[i + step] - b
        if (b <= range.end && b >= range.start) {
            if (b == range.start) start = i
            if (b == range.end) end = i
            if (b + cLen == range.end) end = i + step
        }
        i += step
    }
    if (range.end == table[s.length]) end = s.length
    return if (start != null && end != null) Offsets(start, end) else null
}

public fun charToBytes(s: String, range: Offsets): Offsets? {
    if (range.start == 0 && range.end == 0) return Offsets(0, 0)
    if (range.start < 0 || range.end > s.length || range.start > range.end) return null
    val table = utf8CharOffsets(s)
    return Offsets(table[range.start], table[range.end])
}
