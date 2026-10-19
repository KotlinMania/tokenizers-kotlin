package io.github.kotlinmania.tokenizers.tokenizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreTokenizerTest {
    @Test
    fun testPreTokenizedStringBasic() {
        val pts = PreTokenizedString("Hello world")
        assertEquals("Hello world", pts.getOriginal())
        assertEquals(1, pts.getSplitsList().size)
    }

    @Test
    fun testPreTokenizedStringSplit() {
        val pts = PreTokenizedString("Hello world")
        val res =
            pts.split { _, normalized ->
                val splitRes = normalized.split(CharPattern(' '), SplitDelimiterBehavior.Removed)
                if (splitRes.isFailure) {
                    Result.failure(splitRes.exceptionOrNull()!!)
                } else {
                    Result.success(splitRes.getOrThrow().map { Split(it) })
                }
            }
        assertTrue(res.isSuccess)
        val splits = pts.getSplits(OffsetReferential.Original, OffsetType.Byte)
        assertEquals(2, splits.size)
        assertEquals("Hello", splits[0].text)
        assertEquals(Offsets(0, 5), splits[0].offsets)
        assertEquals("world", splits[1].text)
        assertEquals(Offsets(6, 11), splits[1].offsets)
    }

    @Test
    fun testPreTokenizedStringTokenizeAndIntoEncoding() {
        val pts = PreTokenizedString("Hello world")
        pts.split { _, normalized ->
            val splitRes = normalized.split(CharPattern(' '), SplitDelimiterBehavior.Removed)
            Result.success(splitRes.getOrThrow().map { Split(it) })
        }

        var nextId = 1u
        val tokRes =
            pts.tokenize { norm ->
                val text = norm.get()
                Result.success(listOf(Token(nextId++, text, Offsets(0, norm.len()))))
            }
        assertTrue(tokRes.isSuccess)

        val encRes = pts.intoEncoding(wordIdx = null, typeId = 0u, offsetType = OffsetType.Byte)
        assertTrue(encRes.isSuccess)
        val encoding = encRes.getOrThrow()
        assertEquals(listOf(1u, 2u), encoding.getIds())
        assertEquals(listOf("Hello", "world"), encoding.getTokens())
        assertEquals(listOf(Offsets(0, 5), Offsets(6, 11)), encoding.getOffsets())
        assertEquals(listOf(0u, 1u), encoding.getWordIds())
    }
}
