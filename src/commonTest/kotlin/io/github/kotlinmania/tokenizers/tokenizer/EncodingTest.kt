package io.github.kotlinmania.tokenizers.tokenizer

import io.github.kotlinmania.tokenizers.utils.PaddingDirection
import io.github.kotlinmania.tokenizers.utils.TruncationDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EncodingTest {
    @Test
    fun testMergeEncodings() {
        val a = Encoding.new(
            ids = listOf(1u),
            typeIds = listOf(0u),
            tokens = listOf("Hello "),
            words = listOf(0u),
            offsets = listOf(Offsets(0, 6)),
            specialTokensMask = listOf(0u),
            attentionMask = listOf(1u),
        )
        val b = Encoding.new(
            ids = listOf(2u),
            typeIds = listOf(1u),
            tokens = listOf("World!"),
            words = listOf(0u),
            offsets = listOf(Offsets(0, 6)),
            specialTokensMask = listOf(0u),
            attentionMask = listOf(1u),
        )
        a.mergeWith(b, growingOffsets = true)

        val expected = Encoding.new(
            ids = listOf(1u, 2u),
            typeIds = listOf(0u, 1u),
            tokens = listOf("Hello ", "World!"),
            words = listOf(0u, 0u),
            offsets = listOf(Offsets(0, 6), Offsets(6, 12)),
            specialTokensMask = listOf(0u, 0u),
            attentionMask = listOf(1u, 1u),
        )
        assertEquals(expected, a)
    }

    @Test
    fun testTruncate() {
        val a = Encoding.new(
            ids = listOf(1u, 2u, 3u),
            typeIds = listOf(0u, 0u, 0u),
            tokens = listOf("Hello", "World", "!"),
            words = listOf(0u, 1u, 2u),
            offsets = listOf(Offsets(0, 5), Offsets(6, 11), Offsets(11, 12)),
            specialTokensMask = listOf(0u, 0u, 0u),
            attentionMask = listOf(1u, 1u, 1u),
        )
        a.truncate(2, 0, TruncationDirection.Right)

        val expectedOverflowing = listOf(
            Encoding.new(
                ids = listOf(3u),
                typeIds = listOf(0u),
                tokens = listOf("!"),
                words = listOf(2u),
                offsets = listOf(Offsets(11, 12)),
                specialTokensMask = listOf(0u),
                attentionMask = listOf(1u),
            ),
        )
        val expected = Encoding.new(
            ids = listOf(1u, 2u),
            typeIds = listOf(0u, 0u),
            tokens = listOf("Hello", "World"),
            words = listOf(0u, 1u),
            offsets = listOf(Offsets(0, 5), Offsets(6, 11)),
            specialTokensMask = listOf(0u, 0u),
            attentionMask = listOf(1u, 1u),
            overflowing = expectedOverflowing,
        )
        assertEquals(expected, a)
    }

    @Test
    fun testTruncateToEmpty() {
        val a = Encoding.new(
            ids = listOf(1u, 2u, 3u),
            typeIds = listOf(0u, 0u, 0u),
            tokens = listOf("Hello", "World", "!"),
            words = listOf(0u, 1u, 2u),
            offsets = listOf(Offsets(0, 5), Offsets(6, 11), Offsets(11, 12)),
            specialTokensMask = listOf(0u, 0u, 0u),
            attentionMask = listOf(1u, 1u, 1u),
        )
        a.truncate(0, 0, TruncationDirection.Right)

        val expectedOverflowing = listOf(
            Encoding.new(
                ids = listOf(1u, 2u, 3u),
                typeIds = listOf(0u, 0u, 0u),
                tokens = listOf("Hello", "World", "!"),
                words = listOf(0u, 1u, 2u),
                offsets = listOf(Offsets(0, 5), Offsets(6, 11), Offsets(11, 12)),
                specialTokensMask = listOf(0u, 0u, 0u),
                attentionMask = listOf(1u, 1u, 1u),
            ),
        )
        val expected = Encoding.new(overflowing = expectedOverflowing)
        assertEquals(expected, a)
    }

    @Test
    fun testTruncateOverflowWithStride() {
        val enc = Encoding.new(
            ids = listOf(1u, 2u, 3u, 4u, 5u),
            typeIds = listOf(0u, 0u, 0u, 0u, 0u),
            tokens = listOf("42", "is", "the", "answer", "!"),
            words = listOf(0u, 1u, 2u, 3u, 4u),
            offsets = listOf(Offsets(0, 2), Offsets(2, 4), Offsets(4, 7), Offsets(7, 13), Offsets(13, 14)),
            specialTokensMask = listOf(0u, 0u, 0u, 0u, 0u),
            attentionMask = listOf(1u, 1u, 1u, 1u, 1u),
        )
        enc.truncate(4, 2, TruncationDirection.Right)

        val expectedOverflowing = listOf(
            Encoding.new(
                ids = listOf(3u, 4u, 5u),
                typeIds = listOf(0u, 0u, 0u),
                tokens = listOf("the", "answer", "!"),
                words = listOf(2u, 3u, 4u),
                offsets = listOf(Offsets(4, 7), Offsets(7, 13), Offsets(13, 14)),
                specialTokensMask = listOf(0u, 0u, 0u),
                attentionMask = listOf(1u, 1u, 1u),
            ),
        )
        val expected = Encoding.new(
            ids = listOf(1u, 2u, 3u, 4u),
            typeIds = listOf(0u, 0u, 0u, 0u),
            tokens = listOf("42", "is", "the", "answer"),
            words = listOf(0u, 1u, 2u, 3u),
            offsets = listOf(Offsets(0, 2), Offsets(2, 4), Offsets(4, 7), Offsets(7, 13)),
            specialTokensMask = listOf(0u, 0u, 0u, 0u),
            attentionMask = listOf(1u, 1u, 1u, 1u),
            overflowing = expectedOverflowing,
        )
        assertEquals(expected, enc)
    }

    @Test
    fun testTruncateLeft() {
        val a = Encoding.new(
            ids = listOf(1u, 2u, 3u),
            typeIds = listOf(0u, 0u, 0u),
            tokens = listOf("Hello", "World", "!"),
            words = listOf(0u, 1u, 2u),
            offsets = listOf(Offsets(0, 5), Offsets(6, 11), Offsets(11, 12)),
            specialTokensMask = listOf(0u, 0u, 0u),
            attentionMask = listOf(1u, 1u, 1u),
        )
        a.truncate(2, 0, TruncationDirection.Left)

        val expectedOverflowing = listOf(
            Encoding.new(
                ids = listOf(1u),
                typeIds = listOf(0u),
                tokens = listOf("Hello"),
                words = listOf(0u),
                offsets = listOf(Offsets(0, 5)),
                specialTokensMask = listOf(0u),
                attentionMask = listOf(1u),
            ),
        )
        val expected = Encoding.new(
            ids = listOf(2u, 3u),
            typeIds = listOf(0u, 0u),
            tokens = listOf("World", "!"),
            words = listOf(1u, 2u),
            offsets = listOf(Offsets(6, 11), Offsets(11, 12)),
            specialTokensMask = listOf(0u, 0u),
            attentionMask = listOf(1u, 1u),
            overflowing = expectedOverflowing,
        )
        assertEquals(expected, a)
    }

    @Test
    fun testMappings() {
        val encoding = Encoding.new(
            ids = List(11) { 0u },
            tokens = listOf(
                "He", "llo", "won", "der", "ful", "friend", "!",
                "How", "are", "you", "?",
            ),
            offsets = listOf(
                Offsets(0, 2), Offsets(2, 5), Offsets(7, 10), Offsets(10, 13), Offsets(13, 16), Offsets(17, 23), Offsets(23, 24),
                Offsets(0, 3), Offsets(4, 7), Offsets(8, 11), Offsets(11, 12),
            ),
            words = listOf(
                0u, 0u, 1u, 1u, 1u, 2u, 3u,
                0u, 1u, 2u, 3u,
            ),
            sequenceRanges = mapOf(0 to 0..6, 1 to 7..10),
        )

        assertEquals(TokenIndices(0, 2), encoding.wordToTokens(0u, 0))
        assertEquals(TokenIndices(2, 5), encoding.wordToTokens(1u, 0))
        assertEquals(TokenIndices(5, 6), encoding.wordToTokens(2u, 0))
        assertEquals(TokenIndices(6, 7), encoding.wordToTokens(3u, 0))
        assertEquals(TokenIndices(7, 8), encoding.wordToTokens(0u, 1))
        assertEquals(TokenIndices(8, 9), encoding.wordToTokens(1u, 1))
        assertEquals(TokenIndices(9, 10), encoding.wordToTokens(2u, 1))
        assertEquals(TokenIndices(10, 11), encoding.wordToTokens(3u, 1))

        assertEquals(Offsets(0, 5), encoding.wordToChars(0u, 0))
        assertEquals(Offsets(7, 16), encoding.wordToChars(1u, 0))
        assertEquals(Offsets(0, 3), encoding.wordToChars(0u, 1))
        assertEquals(Offsets(4, 7), encoding.wordToChars(1u, 1))

        assertEquals(SequenceOffsets(0, Offsets(0, 2)), encoding.tokenToChars(0))
        assertEquals(SequenceOffsets(0, Offsets(2, 5)), encoding.tokenToChars(1))
        assertEquals(SequenceOffsets(1, Offsets(0, 3)), encoding.tokenToChars(7))
        assertEquals(SequenceOffsets(1, Offsets(8, 11)), encoding.tokenToChars(9))

        assertEquals(SequenceWord(0, 0u), encoding.tokenToWord(1))
        assertEquals(SequenceWord(0, 1u), encoding.tokenToWord(2))
        assertEquals(SequenceWord(1, 0u), encoding.tokenToWord(7))
        assertEquals(SequenceWord(1, 2u), encoding.tokenToWord(9))
        assertNull(encoding.tokenToWord(11))

        assertEquals(1, encoding.charToToken(3, 0))
        assertEquals(2, encoding.charToToken(8, 0))
        assertNull(encoding.charToToken(16, 0))
        assertEquals(6, encoding.charToToken(23, 0))
        assertEquals(7, encoding.charToToken(2, 1))
        assertEquals(9, encoding.charToToken(9, 1))

        assertEquals(0u, encoding.charToWord(3, 0))
        assertEquals(1u, encoding.charToWord(8, 0))
        assertNull(encoding.charToWord(16, 0))
        assertEquals(3u, encoding.charToWord(23, 0))
        assertEquals(0u, encoding.charToWord(2, 1))
        assertEquals(2u, encoding.charToWord(9, 1))
    }

    @Test
    fun testPadding() {
        val a = Encoding.new(
            ids = listOf(1u),
            typeIds = listOf(0u),
            tokens = listOf("Hello "),
            words = listOf(0u),
            offsets = listOf(Offsets(0, 6)),
            specialTokensMask = listOf(0u),
            attentionMask = listOf(1u),
            sequenceRanges = mapOf(0 to 0..0),
        )
        a.pad(2, 99u, 0u, "[PAD]", PaddingDirection.Left)
        assertEquals(mapOf(0 to 1..1), a.getSequenceRanges())
    }
}
