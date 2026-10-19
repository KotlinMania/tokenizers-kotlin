// port-lint: source tokenizer/pattern.rs
package io.github.kotlinmania.tokenizers.tokenizer

/**
 * A matched or non-matched slice with its byte offsets.
 */
public data class PatternMatch(
    public val offsets: Offsets,
    public val isMatch: Boolean,
)

internal fun interface CharPredicate {
    fun test(char: Char): Boolean
}

/**
 * Pattern used to split a NormalizedString.
 */
public interface Pattern {
    /**
     * Slice the given string in a list of pattern match positions, with
     * a boolean indicating whether this is a match or not.
     *
     * This method must cover the whole string in its outputs, with
     * contiguous ordered slices.
     */
    public fun findMatches(inside: String): Result<List<PatternMatch>>
}

public class CharPattern(public val char: Char) : Pattern {
    override fun findMatches(inside: String): Result<List<PatternMatch>> {
        val isChar = PredicatePattern { it == char }
        return isChar.findMatches(inside)
    }
}

public class StringPattern(public val pattern: String) : Pattern {
    override fun findMatches(inside: String): Result<List<PatternMatch>> {
        if (pattern.isEmpty()) {
            val charCount = inside.encodeToByteArray().size
            return Result.success(listOf(PatternMatch(Offsets(0, charCount), false)))
        }

        val byteTable = utf8CharOffsets(inside)
        val totalBytes = byteTable[inside.length]
        val matches = mutableListOf<PatternMatch>()
        var prev = 0
        var startIndex = 0

        while (startIndex <= inside.length - pattern.length) {
            val found = inside.indexOf(pattern, startIndex)
            if (found < 0) break
            val startByte = byteTable[found]
            val endByte = byteTable[found + pattern.length]
            if (prev != startByte) {
                matches.add(PatternMatch(Offsets(prev, startByte), false))
            }
            matches.add(PatternMatch(Offsets(startByte, endByte), true))
            prev = endByte
            startIndex = found + pattern.length
        }

        if (prev != totalBytes) {
            matches.add(PatternMatch(Offsets(prev, totalBytes), false))
        }

        return Result.success(matches)
    }
}

public class RegexPattern(public val regex: Regex) : Pattern {
    override fun findMatches(inside: String): Result<List<PatternMatch>> {
        val byteTable = utf8CharOffsets(inside)
        val totalBytes = byteTable[inside.length]
        if (inside.isEmpty()) {
            return Result.success(listOf(PatternMatch(Offsets(0, 0), false)))
        }

        var prev = 0
        val splits = mutableListOf<PatternMatch>()
        val matchResults = regex.findAll(inside)

        for (m in matchResults) {
            val startByte = byteTable[m.range.first]
            val endByte = byteTable[m.range.last + 1]
            if (prev != startByte) {
                splits.add(PatternMatch(Offsets(prev, startByte), false))
            }
            splits.add(PatternMatch(Offsets(startByte, endByte), true))
            prev = endByte
        }

        if (prev != totalBytes) {
            splits.add(PatternMatch(Offsets(prev, totalBytes), false))
        }

        return Result.success(splits)
    }
}

internal class PredicatePattern(val predicate: CharPredicate) : Pattern {
    override fun findMatches(inside: String): Result<List<PatternMatch>> {
        if (inside.isEmpty()) {
            return Result.success(listOf(PatternMatch(Offsets(0, 0), false)))
        }

        val byteTable = utf8CharOffsets(inside)
        val totalBytes = byteTable[inside.length]
        var lastOffset = 0
        val matches = mutableListOf<PatternMatch>()

        var i = 0
        while (i < inside.length) {
            val c = inside[i]
            val startByte = byteTable[i]
            val nextI = if (c.isHighSurrogate() && i + 1 < inside.length && inside[i + 1].isLowSurrogate()) {
                i + 2
            } else {
                i + 1
            }
            val endByte = byteTable[nextI]

            if (predicate.test(c)) {
                if (lastOffset < startByte) {
                    matches.add(PatternMatch(Offsets(lastOffset, startByte), false))
                }
                matches.add(PatternMatch(Offsets(startByte, endByte), true))
                lastOffset = endByte
            }
            i = nextI
        }

        if (totalBytes > lastOffset) {
            matches.add(PatternMatch(Offsets(lastOffset, totalBytes), false))
        }

        return Result.success(matches)
    }
}

/**
 * Invert the isMatch flags for the wrapped Pattern. This is useful
 * for example when we use a regex that matches words instead of a delimiter,
 * and we want to match the delimiter.
 */
public class Invert<P : Pattern>(public val pattern: P) : Pattern {
    override fun findMatches(inside: String): Result<List<PatternMatch>> {
        val result = pattern.findMatches(inside)
        return result.map { list ->
            list.map { (offsets, flag) -> PatternMatch(offsets, !flag) }
        }
    }
}

/**
 * Computes an array of size `s.length + 1` where `array[i]` is the UTF-8 byte offset
 * corresponding to UTF-16 character index `i`.
 */
internal fun utf8CharOffsets(s: String): IntArray {
    val table = IntArray(s.length + 1)
    var byteOffset = 0
    var i = 0
    while (i < s.length) {
        table[i] = byteOffset
        val c = s[i]
        val codePoint = if (c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) {
            val high = c.code
            val low = s[i + 1].code
            0x10000 + ((high - 0xD800) shl 10) + (low - 0xDC00)
        } else {
            c.code
        }

        val bytes = when {
            codePoint <= 0x7F -> 1
            codePoint <= 0x7FF -> 2
            codePoint <= 0xFFFF -> 3
            else -> 4
        }

        byteOffset += bytes
        if (codePoint > 0xFFFF) {
            i += 2
            table[i - 1] = byteOffset
        } else {
            i += 1
        }
    }
    table[s.length] = byteOffset
    return table
}
