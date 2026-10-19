package io.github.kotlinmania.tokenizers.pretokenizers

import io.github.kotlinmania.tokenizers.tokenizer.OffsetReferential
import io.github.kotlinmania.tokenizers.tokenizer.OffsetType
import io.github.kotlinmania.tokenizers.tokenizer.Offsets
import io.github.kotlinmania.tokenizers.tokenizer.PreTokenizedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ByteLevelTest {
    @Test
    fun testPreTokenization() {
        val bytelevel = ByteLevel(addPrefixSpace = false)
        val pretokenized = PreTokenizedString("Hello my friend, how is your day going?")
        val res = bytelevel.preTokenize(pretokenized)
        assertTrue(res.isSuccess)

        val splits =
            pretokenized
                .getSplits(OffsetReferential.Original, OffsetType.Byte)
                .map { Pair(it.text, it.offsets) }

        val expected =
            listOf(
                Pair("Hello", Offsets(0, 5)),
                Pair("Ġmy", Offsets(5, 8)),
                Pair("Ġfriend", Offsets(8, 15)),
                Pair(",", Offsets(15, 16)),
                Pair("Ġhow", Offsets(16, 20)),
                Pair("Ġis", Offsets(20, 23)),
                Pair("Ġyour", Offsets(23, 28)),
                Pair("Ġday", Offsets(28, 32)),
                Pair("Ġgoing", Offsets(32, 38)),
                Pair("?", Offsets(38, 39)),
            )
        assertEquals(expected, splits)
    }

    @Test
    fun testPreTokenizationNoRegex() {
        val bytelevel = ByteLevel(addPrefixSpace = true, useRegex = false)
        val pretokenized = PreTokenizedString("Hello my friend, how is your day going?")
        val res = bytelevel.preTokenize(pretokenized)
        assertTrue(res.isSuccess)

        val splits =
            pretokenized
                .getSplits(OffsetReferential.Original, OffsetType.Byte)
                .map { Pair(it.text, it.offsets) }

        assertEquals(
            listOf(Pair("ĠHelloĠmyĠfriend,ĠhowĠisĠyourĠdayĠgoing?", Offsets(0, 39))),
            splits,
        )
    }

    @Test
    fun testDecoding() {
        val bytelevel = ByteLevel(addPrefixSpace = false)
        val tokens = listOf("Hello", "Ġmy", "Ġfriend", ",", "Ġhow", "Ġis", "Ġyour", "Ġday", "Ġgoing", "?")
        val decoded = bytelevel.decodeChain(tokens)
        assertTrue(decoded.isSuccess)
        assertEquals(listOf("Hello my friend, how is your day going?"), decoded.getOrThrow())
    }

    @Test
    fun testHandlingOfMultipleWhitespaces() {
        val pretokenized = PreTokenizedString("Hello there       dear")
        val bytelevel = ByteLevel(addPrefixSpace = false)
        val res = bytelevel.preTokenize(pretokenized)
        assertTrue(res.isSuccess)

        val splits =
            pretokenized
                .getSplits(OffsetReferential.Original, OffsetType.Byte)
                .map { Pair(it.text, it.offsets) }

        val expected =
            listOf(
                Pair("Hello", Offsets(0, 5)),
                Pair("Ġthere", Offsets(5, 11)),
                Pair("ĠĠĠĠĠĠ", Offsets(11, 17)),
                Pair("Ġdear", Offsets(17, 22)),
            )
        assertEquals(expected, splits)
    }

    @Test
    fun testOffsetsWhenCharSplitUp() {
        val input = "i⭢j"
        val pretokenized = PreTokenizedString(input)
        val bytelevel = ByteLevel(addPrefixSpace = false)
        val res = bytelevel.preTokenize(pretokenized)
        assertTrue(res.isSuccess)

        val splitsOrig =
            pretokenized
                .getSplits(OffsetReferential.Original, OffsetType.Byte)
                .map { Pair(it.text, it.offsets) }
        assertEquals(
            listOf(Pair("i", Offsets(0, 1)), Pair("âŃ¢", Offsets(1, 4)), Pair("j", Offsets(4, 5))),
            splitsOrig,
        )

        val splitsNorm =
            pretokenized
                .getSplits(OffsetReferential.Normalized, OffsetType.Byte)
                .map { Pair(it.text, it.offsets) }
        assertEquals(
            listOf(Pair("i", Offsets(0, 1)), Pair("âŃ¢", Offsets(1, 7)), Pair("j", Offsets(7, 8))),
            splitsNorm,
        )
    }

    @Test
    fun testDecodeUnknownCharacters() {
        val bytelevel = ByteLevel()
        val tokens = listOf("Hello", "Ġthere", "Ġdear", "Ġfriend!", "Ġ", "[PA D]")
        val decoded = bytelevel.decodeChain(tokens)
        assertTrue(decoded.isSuccess)
        assertEquals(listOf("Hello there dear friend! [PA D]"), decoded.getOrThrow())
    }
}
