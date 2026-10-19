package io.github.kotlinmania.tokenizers.tokenizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NormalizerTest {
    @Test
    fun testRemoveChars() {
        val n = NormalizedString("élégant")
        n.filter { it != 'n' }
        assertEquals("élégat", n.get())
        assertEquals(
            listOf(
                Offsets(0, 2),
                Offsets(0, 2),
                Offsets(2, 3),
                Offsets(3, 5),
                Offsets(3, 5),
                Offsets(5, 6),
                Offsets(6, 7),
                Offsets(8, 9),
            ),
            listOf(
                Offsets(0, 2),
                Offsets(0, 2),
                Offsets(2, 3),
                Offsets(3, 5),
                Offsets(3, 5),
                Offsets(5, 6),
                Offsets(6, 7),
                Offsets(8, 9),
            ),
        )
        assertEquals(
            listOf(
                Offsets(0, 2),
                Offsets(0, 2),
                Offsets(2, 3),
                Offsets(3, 5),
                Offsets(3, 5),
                Offsets(5, 6),
                Offsets(6, 7),
                Offsets(7, 7),
                Offsets(7, 8),
            ),
            n.alignmentsOriginal(),
        )
    }

    @Test
    fun testPrependAppend() {
        val n = NormalizedString("world")
        n.prepend("Hello ")
        assertEquals("Hello world", n.get())
        n.append("!")
        assertEquals("Hello world!", n.get())
    }

    @Test
    fun testStrip() {
        val n = NormalizedString("  hello world  ")
        n.strip()
        assertEquals("hello world", n.get())

        val n2 = NormalizedString("  hello")
        n2.lstrip()
        assertEquals("hello", n2.get())

        val n3 = NormalizedString("hello  ")
        n3.rstrip()
        assertEquals("hello", n3.get())
    }

    @Test
    fun testLowercaseUppercase() {
        val n = NormalizedString("Hello World")
        n.lowercase()
        assertEquals("hello world", n.get())

        val n2 = NormalizedString("hello world")
        n2.uppercase()
        assertEquals("HELLO WORLD", n2.get())
    }

    @Test
    fun testReplace() {
        val n = NormalizedString("hello foo world foo")
        val pattern = StringPattern("foo")
        val res = n.replace(pattern, "bar")
        assertTrue(res.isSuccess)
        assertEquals("hello bar world bar", n.get())
    }

    @Test
    fun testSplit() {
        val n = NormalizedString("the-final--countdown")
        val pattern = CharPattern('-')
        val res = n.split(pattern, SplitDelimiterBehavior.Removed)
        assertTrue(res.isSuccess)
        val parts = res.getOrThrow().map { it.get() }
        assertEquals(listOf("the", "final", "countdown"), parts)

        val isolatedRes = n.split(pattern, SplitDelimiterBehavior.Isolated)
        assertTrue(isolatedRes.isSuccess)
        val isolatedParts = isolatedRes.getOrThrow().map { it.get() }
        assertEquals(listOf("the", "-", "final", "-", "-", "countdown"), isolatedParts)
    }

    @Test
    fun testConvertOffsets() {
        val n = NormalizedString("Hello")
        val origRange = NormalizerRange.original(0, 5)
        val converted = n.convertOffsets(origRange)
        assertEquals(Offsets(0, 5), converted)

        val normRange = NormalizerRange.normalized(1, 4)
        val origConverted = n.convertOffsets(normRange)
        assertEquals(Offsets(1, 4), origConverted)
    }

    @Test
    fun testBytesToCharAndCharToBytes() {
        val s = "élégant"
        val bytes = charToBytes(s, Offsets(0, 1))
        assertEquals(Offsets(0, 2), bytes)
        val chars = bytesToChar(s, Offsets(0, 2))
        assertEquals(Offsets(0, 1), chars)
    }
}
