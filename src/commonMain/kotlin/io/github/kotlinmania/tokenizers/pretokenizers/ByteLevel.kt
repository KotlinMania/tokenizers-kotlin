// port-lint: source pre_tokenizers/byte_level.rs
package io.github.kotlinmania.tokenizers.pretokenizers

import io.github.kotlinmania.tokenizers.tokenizer.Decoder
import io.github.kotlinmania.tokenizers.tokenizer.Encoding
import io.github.kotlinmania.tokenizers.tokenizer.Offsets
import io.github.kotlinmania.tokenizers.tokenizer.Pattern
import io.github.kotlinmania.tokenizers.tokenizer.PatternMatch
import io.github.kotlinmania.tokenizers.tokenizer.PostProcessor
import io.github.kotlinmania.tokenizers.tokenizer.PreTokenizedString
import io.github.kotlinmania.tokenizers.tokenizer.PreTokenizer
import io.github.kotlinmania.tokenizers.tokenizer.Split
import io.github.kotlinmania.tokenizers.tokenizer.SplitDelimiterBehavior
import io.github.kotlinmania.tokenizers.tokenizer.utf8CharOffsets

/**
 * Converts bytes to unicode characters.
 * See https://github.com/openai/gpt-2/blob/master/src/encoder.py#L9
 */
internal fun bytesChar(): Map<UByte, Char> {
    val bs = mutableListOf<UByte>()
    for (i in '!'.code..'~'.code) bs.add(i.toUByte())
    for (i in 0xA1..0xAC) bs.add(i.toUByte())
    for (i in 0xAE..0xFF) bs.add(i.toUByte())

    val cs = bs.map { it.toUInt() }.toMutableList()
    var n = 0u

    for (bInt in 0..255) {
        val b = bInt.toUByte()
        if (!bs.contains(b)) {
            bs.add(b)
            cs.add(256u + n)
            n += 1u
        }
    }

    val result = HashMap<UByte, Char>(256)
    for (i in bs.indices) {
        result[bs[i]] = cs[i].toInt().toChar()
    }
    return result
}

internal val BYTES_CHAR: Map<UByte, Char> by lazy { bytesChar() }
internal val CHAR_BYTES: Map<Char, UByte> by lazy {
    val map = HashMap<Char, UByte>(256)
    for ((b, c) in BYTES_CHAR) {
        map[c] = b
    }
    map
}

internal object Gpt2Pattern : Pattern {
    private val CONTRACTIONS = arrayOf("'s", "'t", "'re", "'ve", "'m", "'ll", "'d")

    override fun findMatches(inside: String): Result<List<PatternMatch>> {
        if (inside.isEmpty()) {
            return Result.success(listOf(PatternMatch(Offsets(0, 0), false)))
        }

        val byteTable = utf8CharOffsets(inside)
        val matches = mutableListOf<PatternMatch>()
        var i = 0
        val n = inside.length

        fun isSymbol(c: Char): Boolean = !c.isWhitespace() && !c.isLetter() && !c.isDigit()

        while (i < n) {
            val startI = i
            // 1. Contractions
            var matchedContraction = false
            for (c in CONTRACTIONS) {
                if (inside.startsWith(c, i)) {
                    val nextI = i + c.length
                    matches.add(PatternMatch(Offsets(byteTable[startI], byteTable[nextI]), true))
                    i = nextI
                    matchedContraction = true
                    break
                }
            }
            if (matchedContraction) continue

            val hasSpace = inside[i] == ' '
            val nxt = if (hasSpace) i + 1 else i

            // 2. Letters: ?\p{L}+
            if (nxt < n && inside[nxt].isLetter()) {
                var j = nxt
                while (j < n && inside[j].isLetter()) {
                    if (inside[j].isHighSurrogate() && j + 1 < n && inside[j + 1].isLowSurrogate()) {
                        j += 2
                    } else {
                        j += 1
                    }
                }
                matches.add(PatternMatch(Offsets(byteTable[startI], byteTable[j]), true))
                i = j
                continue
            }

            // 3. Digits: ?\p{N}+
            if (nxt < n && inside[nxt].isDigit()) {
                var j = nxt
                while (j < n && inside[j].isDigit()) {
                    if (inside[j].isHighSurrogate() && j + 1 < n && inside[j + 1].isLowSurrogate()) {
                        j += 2
                    } else {
                        j += 1
                    }
                }
                matches.add(PatternMatch(Offsets(byteTable[startI], byteTable[j]), true))
                i = j
                continue
            }

            // 4. Symbols: ?[^\s\p{L}\p{N}]+
            if (nxt < n && isSymbol(inside[nxt])) {
                var j = nxt
                while (j < n && isSymbol(inside[j])) {
                    if (inside[j].isHighSurrogate() && j + 1 < n && inside[j + 1].isLowSurrogate()) {
                        j += 2
                    } else {
                        j += 1
                    }
                }
                matches.add(PatternMatch(Offsets(byteTable[startI], byteTable[j]), true))
                i = j
                continue
            }

            // 5. Whitespace: \s+(?!\S)|\s+
            if (inside[i].isWhitespace()) {
                var j = i
                while (j < n && inside[j].isWhitespace()) {
                    j++
                }
                val end =
                    if (j < n && inside[j - 1] == ' ' && (inside[j].isLetter() || inside[j].isDigit() || isSymbol(inside[j]))) {
                        if (j - 1 > i) j - 1 else j
                    } else {
                        j
                    }
                matches.add(PatternMatch(Offsets(byteTable[startI], byteTable[end]), true))
                i = end
                continue
            }

            // Fallback
            val step = if (inside[i].isHighSurrogate() && i + 1 < n && inside[i + 1].isLowSurrogate()) 2 else 1
            matches.add(PatternMatch(Offsets(byteTable[startI], byteTable[i + step]), true))
            i += step
        }

        return Result.success(matches)
    }
}

internal val GPT2_REGEX: Pattern = Gpt2Pattern

/**
 * Provides all the necessary steps to handle BPE tokenization at the byte-level.
 * Implements PreTokenizer, PostProcessor, and Decoder.
 */
public class ByteLevel(
    public val addPrefixSpace: Boolean = true,
    public val trimOffsets: Boolean = true,
    public val useRegex: Boolean = true,
) : PreTokenizer,
    Decoder,
    PostProcessor {
    public companion object {
        public fun alphabet(): Set<Char> = BYTES_CHAR.values.toSet()

        public fun processOffsets(encoding: Encoding, addPrefixSpace: Boolean) {
            val spaceChar = BYTES_CHAR[' '.code.toUByte()] ?: ' '
            val tokens = encoding.getTokens()
            val offsets = encoding.getOffsets()
            val newOffsets = offsets.toMutableList()

            for (i in tokens.indices) {
                val token = tokens[i]
                val curOffsets = newOffsets[i]
                var leadingSpaces = token.takeWhile { it == spaceChar || it.isWhitespace() }.length
                val trailingSpaces = token.takeLastWhile { it == spaceChar || it.isWhitespace() }.length

                if (leadingSpaces > 0 || trailingSpaces > 0) {
                    var start = curOffsets.start
                    var end = curOffsets.end
                    if (leadingSpaces > 0) {
                        val isFirst = i == 0 || curOffsets.start == 0
                        if (isFirst && addPrefixSpace && leadingSpaces == 1) {
                            leadingSpaces = 0
                        }
                        start = minOf(start + leadingSpaces, end)
                    }
                    if (trailingSpaces > 0 && end >= trailingSpaces) {
                        end = maxOf(end - trailingSpaces, start)
                    }
                    newOffsets[i] = Offsets(start, end)
                }
            }
            encoding.setOffsets(newOffsets)
        }
    }

    public fun withAddPrefixSpace(v: Boolean): ByteLevel =
        ByteLevel(addPrefixSpace = v, trimOffsets = trimOffsets, useRegex = useRegex)

    public fun withTrimOffsets(v: Boolean): ByteLevel =
        ByteLevel(addPrefixSpace = addPrefixSpace, trimOffsets = v, useRegex = useRegex)

    public fun withUseRegex(v: Boolean): ByteLevel =
        ByteLevel(addPrefixSpace = addPrefixSpace, trimOffsets = trimOffsets, useRegex = v)

    override fun preTokenize(pretokenized: PreTokenizedString): Result<Unit> {
        val splitRes =
            pretokenized.split { _, normalized ->
                if (addPrefixSpace && !normalized.get().startsWith(' ')) {
                    normalized.prepend(" ")
                }
                val splits =
                    if (useRegex) {
                        normalized.split(GPT2_REGEX, SplitDelimiterBehavior.Isolated)
                    } else {
                        Result.success(listOf(normalized))
                    }
                splits.map { list -> list.map { Split(it) } }
            }
        if (splitRes.isFailure) return splitRes

        return pretokenized.normalize { normalized ->
            val s = normalized.get()
            val transformations = ArrayList<Pair<Char, Int>>(s.length)
            val byteTable = utf8CharOffsets(s)
            val utf8Bytes = s.encodeToByteArray()

            var charIdx = 0
            while (charIdx < s.length) {
                val startByte = byteTable[charIdx]
                val c = s[charIdx]
                val step = if (c.isHighSurrogate() && charIdx + 1 < s.length && s[charIdx + 1].isLowSurrogate()) 2 else 1
                val endByte = byteTable[charIdx + step]

                for (bIdx in startByte until endByte) {
                    val b = utf8Bytes[bIdx].toUByte()
                    val targetChar = BYTES_CHAR[b] ?: b.toInt().toChar()
                    transformations.add(Pair(targetChar, if (bIdx > startByte) 1 else 0))
                }
                charIdx += step
            }
            normalized.transform(transformations, 0)
            Result.success(Unit)
        }
    }

    override fun decodeChain(tokens: List<String>): Result<List<String>> {
        val bytes = ArrayList<Byte>()
        for (t in tokens) {
            var allInCharBytes = true
            val tokenBytes = ArrayList<Byte>(t.length)
            for (c in t) {
                val b = CHAR_BYTES[c]
                if (b != null) {
                    tokenBytes.add(b.toByte())
                } else {
                    allInCharBytes = false
                    break
                }
            }
            if (allInCharBytes) {
                bytes.addAll(tokenBytes)
            } else {
                bytes.addAll(t.encodeToByteArray().toList())
            }
        }
        return Result.success(listOf(bytes.toByteArray().decodeToString()))
    }

    override fun addedTokens(isPair: Boolean): Int = 0

    override fun processEncodings(
        encodings: List<Encoding>,
        addSpecialTokens: Boolean,
    ): Result<List<Encoding>> {
        if (trimOffsets) {
            for (encoding in encodings) {
                processOffsets(encoding, addPrefixSpace)
                for (over in encoding.getOverflowing()) {
                    processOffsets(over, addPrefixSpace)
                }
            }
        }
        for ((i, encoding) in encodings.withIndex()) {
            encoding.setSequenceId(i)
        }
        return Result.success(encodings)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteLevel) return false
        return addPrefixSpace == other.addPrefixSpace &&
            trimOffsets == other.trimOffsets &&
            useRegex == other.useRegex
    }

    override fun hashCode(): Int {
        var result = addPrefixSpace.hashCode()
        result = 31 * result + trimOffsets.hashCode()
        result = 31 * result + useRegex.hashCode()
        return result
    }

    override fun toString(): String =
        "ByteLevel(addPrefixSpace=$addPrefixSpace, trimOffsets=$trimOffsets, useRegex=$useRegex)"
}
