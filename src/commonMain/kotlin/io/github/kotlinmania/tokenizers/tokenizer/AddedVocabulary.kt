// port-lint: source tokenizer/added_vocabulary.rs
package io.github.kotlinmania.tokenizers.tokenizer

/**
 * Represents a token added by the user on top of the existing Model vocabulary.
 */
public data class AddedToken(
    public val content: String = "",
    public val singleWord: Boolean = false,
    public val lstrip: Boolean = false,
    public val rstrip: Boolean = false,
    public val normalized: Boolean = true,
    public val special: Boolean = false,
) {
    public companion object {
        public fun from(content: String, special: Boolean): AddedToken =
            AddedToken(
                content = content,
                singleWord = false,
                lstrip = false,
                rstrip = false,
                normalized = !special,
                special = special,
            )
    }

    public fun withSingleWord(singleWord: Boolean): AddedToken = copy(singleWord = singleWord)

    public fun withLstrip(lstrip: Boolean): AddedToken = copy(lstrip = lstrip)

    public fun withRstrip(rstrip: Boolean): AddedToken = copy(rstrip = rstrip)

    public fun withNormalized(normalized: Boolean): AddedToken = copy(normalized = normalized)

    public fun withSpecial(special: Boolean): AddedToken = copy(special = special)
}

private class TrieNode(
    var id: UInt? = null,
    val children: HashMap<Char, TrieNode> = HashMap(),
)

private class TrieMatcher(
    patterns: List<Pair<String, UInt>>,
) {
    val root: TrieNode = TrieNode()

    init {
        for ((pattern, id) in patterns) {
            if (pattern.isEmpty()) continue
            var current = root
            for (c in pattern) {
                current = current.children.getOrPut(c) { TrieNode() }
            }
            current.id = id
        }
    }

    data class Match(
        val startChar: Int,
        val endChar: Int,
        val id: UInt,
    )

    fun findMatches(sentence: String): List<Match> {
        val matches = mutableListOf<Match>()
        var i = 0
        while (i < sentence.length) {
            var longestMatchLen = -1
            var longestMatchId: UInt? = null
            var node: TrieNode? = root
            var j = i
            while (j < sentence.length && node != null) {
                node = node.children[sentence[j]]
                if (node?.id != null) {
                    longestMatchLen = j - i + 1
                    longestMatchId = node.id
                }
                j++
            }
            if (longestMatchLen > 0 && longestMatchId != null) {
                matches.add(Match(i, i + longestMatchLen, longestMatchId))
                i += longestMatchLen
            } else {
                i++
            }
        }
        return matches
    }
}

private val STARTS_WITH_WORD = Regex("""^\w""")
private val ENDS_WITH_WORD = Regex("""\w$""")
private val RIGHTMOST_SPACE_AT_START = Regex("""^\s*""")
private val LEFTMOST_SPACE_AT_END = Regex("""\s*$""")

/**
 * Vocabulary built on top of the Model.
 */
public class AddedVocabulary {
    private val addedTokensMap: MutableMap<String, UInt> = mutableMapOf()
    private val addedTokensMapR: MutableMap<UInt, AddedToken> = mutableMapOf()
    private val addedTokens: MutableList<AddedToken> = mutableListOf()
    private val specialTokens: MutableList<AddedToken> = mutableListOf()
    private val specialTokensSet: MutableSet<String> = mutableSetOf()

    private var splitTrie: TrieMatcher = TrieMatcher(emptyList())
    private var splitNormalizedTrie: TrieMatcher = TrieMatcher(emptyList())
    private var encodeSpecialTokens: Boolean = false

    public fun len(): Int = addedTokensMap.size

    public fun isEmpty(): Boolean = addedTokensMap.isEmpty()

    public fun getVocab(): Map<String, UInt> = addedTokensMap

    public fun getAddedTokensDecoder(): Map<UInt, AddedToken> = addedTokensMapR

    public fun tokenToId(token: String, model: Model): UInt? =
        addedTokensMap[token] ?: model.tokenToId(token)

    public fun idToToken(id: UInt, model: Model): String? =
        addedTokensMapR[id]?.content ?: model.idToToken(id)

    public fun simpleIdToToken(id: UInt): String? =
        addedTokensMapR[id]?.content

    public fun setEncodeSpecialTokens(value: Boolean) {
        encodeSpecialTokens = value
    }

    public fun getEncodeSpecialTokens(): Boolean = encodeSpecialTokens

    public fun isSpecialToken(token: String): Boolean = specialTokensSet.contains(token)

    public fun addSpecialTokens(
        tokens: List<AddedToken>,
        model: Model,
        normalizer: Normalizer? = null,
    ): Int = addTokens(tokens, model, normalizer)

    public fun addTokens(
        tokens: List<AddedToken>,
        model: Model,
        normalizer: Normalizer? = null,
    ): Int {
        for (token in tokens) {
            if (token.special && token.content.isNotEmpty() && !specialTokensSet.contains(token.content)) {
                specialTokens.add(token)
                specialTokensSet.add(token.content)
            }
        }

        var ignored = 0
        for (token in tokens) {
            if (token.content.isEmpty() || addedTokensMapR.values.any { it == token }) {
                ignored++
                continue
            }

            val newId =
                tokenToId(token.content, model) ?: run {
                    val max = addedTokensMap.values.maxOrNull()
                    val vocabSize = model.getVocabSize().toUInt()
                    if (max != null) {
                        if (max >= vocabSize || vocabSize == 0u) max + 1u else vocabSize
                    } else {
                        vocabSize
                    }
                }

            addedTokensMap[token.content] = newId
            addedTokensMapR[newId] = token

            if (!specialTokensSet.contains(token.content)) {
                addedTokens.add(token)
            }
        }

        refreshAddedTokens(model, normalizer)
        return tokens.size - ignored
    }

    private fun refreshAddedTokens(model: Model, normalizer: Normalizer?) {
        val allTokens =
            (specialTokens + addedTokens).map { token ->
                Pair(token, tokenToId(token.content, model) ?: 0u)
            }

        val (normalizedTokens, nonNormalizedTokens) = allTokens.partition { it.first.normalized }

        splitTrie = TrieMatcher(nonNormalizedTokens.map { Pair(it.first.content, it.second) })

        val normalizedPatterns =
            normalizedTokens.map { (token, id) ->
                val norm = NormalizedString(token.content)
                normalizer?.normalize(norm)
                Pair(norm.get(), id)
            }
        splitNormalizedTrie = TrieMatcher(normalizedPatterns)
    }

    private fun findMatches(sentence: String, matcher: TrieMatcher): List<Pair<UInt?, Offsets>> {
        if (sentence.isEmpty()) {
            return listOf(Pair(null, Offsets(0, 0)))
        }

        val byteTable = utf8CharOffsets(sentence)
        val matches = matcher.findMatches(sentence)
        val splits = mutableListOf<Pair<UInt?, Offsets>>()
        var startOffset = 0

        for (mat in matches) {
            var startChar = mat.startChar
            var stopChar = mat.endChar
            val id = mat.id
            val addedToken = addedTokensMapR[id] ?: continue

            if (encodeSpecialTokens && specialTokensSet.contains(addedToken.content)) {
                continue
            }

            if (addedToken.singleWord) {
                val before = sentence.substring(0, startChar)
                val after = sentence.substring(stopChar)
                val startSpace = startChar == 0 || !ENDS_WITH_WORD.containsMatchIn(before)
                val stopSpace = stopChar == sentence.length || !STARTS_WITH_WORD.containsMatchIn(after)

                if (!stopSpace || !startSpace) {
                    continue
                }
            }

            if (addedToken.lstrip) {
                val before = sentence.substring(0, startChar)
                val spaceMatch = LEFTMOST_SPACE_AT_END.find(before)
                val newStartChar = spaceMatch?.range?.first ?: startChar
                val newStartByte = byteTable[newStartChar]
                startChar = if (newStartByte >= startOffset) newStartChar else startChar
            }

            if (addedToken.rstrip) {
                val after = sentence.substring(stopChar)
                val spaceMatch = RIGHTMOST_SPACE_AT_START.find(after)
                if (spaceMatch != null) {
                    stopChar += spaceMatch.value.length
                }
            }

            val startByte = byteTable[startChar]
            val stopByte = byteTable[stopChar]

            if (startOffset < startByte) {
                splits.add(Pair(null, Offsets(startOffset, startByte)))
            }
            splits.add(Pair(id, Offsets(startByte, stopByte)))
            startOffset = stopByte
        }

        val totalByteLen = byteTable[sentence.length]
        if (startOffset < totalByteLen) {
            splits.add(Pair(null, Offsets(startOffset, totalByteLen)))
        }

        return splits
    }

    private fun splitWithIndices(sentence: NormalizedString, matcher: TrieMatcher): List<Split> {
        val matches = findMatches(sentence.get(), matcher)
        val result = mutableListOf<Split>()
        for ((id, byteOffsets) in matches) {
            val slice =
                sentence.slice(NormalizerRange.Normalized(byteOffsets.start until byteOffsets.end))
                    ?: continue
            if (id != null) {
                val value = slice.get()
                val len = slice.len()
                result.add(Split(slice, listOf(Token(id, value, Offsets(0, len)))))
            } else {
                result.add(Split(slice, null))
            }
        }
        return result
    }

    public fun extractAndNormalize(normalizer: Normalizer?, sequence: String): PreTokenizedString {
        val pretokenized = PreTokenizedString(sequence)

        pretokenized.split { _, seq ->
            Result.success(splitWithIndices(seq, splitTrie))
        }

        pretokenized.split { _, seq ->
            normalizer?.normalize(seq)
            Result.success(splitWithIndices(seq, splitNormalizedTrie))
        }

        return pretokenized
    }
}
