package io.github.kotlinmania.tokenizers.tokenizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatternTest {
    private fun assertPatternMatches(
        inside: String,
        pattern: Pattern,
        expected: List<Pair<Pair<Int, Int>, Boolean>>,
    ) {
        val expectedMatches = expected.map { PatternMatch(Offsets(it.first.first, it.first.second), it.second) }
        val result = pattern.findMatches(inside)
        assertTrue(result.isSuccess)
        assertEquals(expectedMatches, result.getOrThrow())

        val inverted = Invert(pattern).findMatches(inside)
        assertTrue(inverted.isSuccess)
        val invertedExpected = expectedMatches.map { PatternMatch(it.offsets, !it.isMatch) }
        assertEquals(invertedExpected, inverted.getOrThrow())
    }

    @Test
    fun testChar() {
        val pattern = CharPattern('a')
        assertPatternMatches("aba", pattern, listOf(Pair(Pair(0, 1), true), Pair(Pair(1, 2), false), Pair(Pair(2, 3), true)))
        assertPatternMatches("bbbba", pattern, listOf(Pair(Pair(0, 4), false), Pair(Pair(4, 5), true)))
        assertPatternMatches("aabbb", pattern, listOf(Pair(Pair(0, 1), true), Pair(Pair(1, 2), true), Pair(Pair(2, 5), false)))
        assertPatternMatches("", pattern, listOf(Pair(Pair(0, 0), false)))
        assertPatternMatches("aaa", CharPattern('b'), listOf(Pair(Pair(0, 3), false)))
    }

    @Test
    fun testStr() {
        val patternA = StringPattern("a")
        assertPatternMatches("aba", patternA, listOf(Pair(Pair(0, 1), true), Pair(Pair(1, 2), false), Pair(Pair(2, 3), true)))
        assertPatternMatches("bbbba", patternA, listOf(Pair(Pair(0, 4), false), Pair(Pair(4, 5), true)))
        assertPatternMatches("aabbb", patternA, listOf(Pair(Pair(0, 1), true), Pair(Pair(1, 2), true), Pair(Pair(2, 5), false)))
        assertPatternMatches("aabbb", StringPattern("ab"), listOf(Pair(Pair(0, 1), false), Pair(Pair(1, 3), true), Pair(Pair(3, 5), false)))
        assertPatternMatches("aabbab", StringPattern("ab"), listOf(Pair(Pair(0, 1), false), Pair(Pair(1, 3), true), Pair(Pair(3, 4), false), Pair(Pair(4, 6), true)))
        assertPatternMatches("", StringPattern(""), listOf(Pair(Pair(0, 0), false)))
        assertPatternMatches("aaa", StringPattern(""), listOf(Pair(Pair(0, 3), false)))
        assertPatternMatches("aaa", StringPattern("b"), listOf(Pair(Pair(0, 3), false)))
    }

    @Test
    fun testFunctions() {
        val isB = PredicatePattern { it == 'b' }
        assertPatternMatches("aba", isB, listOf(Pair(Pair(0, 1), false), Pair(Pair(1, 2), true), Pair(Pair(2, 3), false)))
        assertPatternMatches("aaaab", isB, listOf(Pair(Pair(0, 4), false), Pair(Pair(4, 5), true)))
        assertPatternMatches("bbaaa", isB, listOf(Pair(Pair(0, 1), true), Pair(Pair(1, 2), true), Pair(Pair(2, 5), false)))
        assertPatternMatches("", isB, listOf(Pair(Pair(0, 0), false)))
        assertPatternMatches("aaa", isB, listOf(Pair(Pair(0, 3), false)))
    }

    @Test
    fun testRegex() {
        val isWhitespace = RegexPattern(Regex("\\s+"))
        assertPatternMatches("a   b", isWhitespace, listOf(Pair(Pair(0, 1), false), Pair(Pair(1, 4), true), Pair(Pair(4, 5), false)))
        assertPatternMatches(
            "   a   b   ",
            isWhitespace,
            listOf(
                Pair(Pair(0, 3), true),
                Pair(Pair(3, 4), false),
                Pair(Pair(4, 7), true),
                Pair(Pair(7, 8), false),
                Pair(Pair(8, 11), true),
            ),
        )
        assertPatternMatches("", isWhitespace, listOf(Pair(Pair(0, 0), false)))
        assertPatternMatches(
            "𝔾𝕠𝕠𝕕 𝕞𝕠𝕣𝕟𝕚𝕟𝕘",
            isWhitespace,
            listOf(
                Pair(Pair(0, 16), false),
                Pair(Pair(16, 17), true),
                Pair(Pair(17, 45), false),
            ),
        )
        assertPatternMatches("aaa", isWhitespace, listOf(Pair(Pair(0, 3), false)))
    }
}
