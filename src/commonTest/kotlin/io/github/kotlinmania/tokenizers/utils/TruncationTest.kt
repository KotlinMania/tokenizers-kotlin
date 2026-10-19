package io.github.kotlinmania.tokenizers.utils

import io.github.kotlinmania.tokenizers.tokenizer.Encoding
import io.github.kotlinmania.tokenizers.tokenizer.Offsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TruncationTest {
    private fun getEmpty(): Encoding = Encoding.new()

    private fun getShort(): Encoding =
        Encoding.new(
            ids = listOf(1u, 2u),
            typeIds = listOf(0u, 0u),
            tokens = listOf("a", "b"),
            words = listOf(0u, 1u),
            offsets = listOf(Offsets(0, 1), Offsets(1, 2)),
            specialTokensMask = listOf(0u, 0u),
            attentionMask = listOf(1u, 1u),
        )

    private fun getMedium(): Encoding =
        Encoding.new(
            ids = listOf(3u, 4u, 5u, 6u),
            typeIds = listOf(0u, 0u, 0u, 0u),
            tokens = listOf("d", "e", "f", "g"),
            words = listOf(0u, 1u, 2u, 3u),
            offsets = listOf(Offsets(0, 1), Offsets(1, 2), Offsets(2, 3), Offsets(3, 4)),
            specialTokensMask = listOf(0u, 0u, 0u, 0u),
            attentionMask = listOf(1u, 1u, 1u, 1u),
        )

    private fun getLong(): Encoding =
        Encoding.new(
            ids = listOf(7u, 8u, 9u, 10u, 11u, 12u, 13u, 14u),
            typeIds = listOf(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u),
            tokens = listOf("h", "i", "j", "k", "l", "m", "n", "o"),
            words = listOf(0u, 1u, 2u, 3u, 4u, 5u, 6u, 7u),
            offsets =
                listOf(
                    Offsets(0, 1),
                    Offsets(1, 2),
                    Offsets(2, 3),
                    Offsets(3, 4),
                    Offsets(4, 5),
                    Offsets(5, 6),
                    Offsets(6, 7),
                    Offsets(7, 8),
                ),
            specialTokensMask = listOf(0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u),
            attentionMask = listOf(1u, 1u, 1u, 1u, 1u, 1u, 1u, 1u),
        )

    private fun truncateAndAssert(
        encoding1: Encoding,
        encoding2: Encoding,
        params: TruncationParams,
        n1: Int,
        n2: Int,
    ) {
        val res = truncateEncodings(encoding1, encoding2, params)
        assertTrue(res.isSuccess)
        val (e1, e2) = res.getOrThrow()
        assertEquals(n1, e1.getIds().size)
        assertEquals(n2, e2?.getIds()?.size)
    }

    @Test
    fun testTruncateEncodingsLongestFirst() {
        val params =
            TruncationParams(
                maxLength = 7,
                strategy = TruncationStrategy.LongestFirst,
                stride = 0,
                direction = TruncationDirection.Right,
            )

        truncateAndAssert(getEmpty(), getEmpty(), params, 0, 0)
        truncateAndAssert(getEmpty(), getShort(), params, 0, 2)
        truncateAndAssert(getEmpty(), getMedium(), params, 0, 4)
        truncateAndAssert(getEmpty(), getLong(), params, 0, 7)

        truncateAndAssert(getShort(), getEmpty(), params, 2, 0)
        truncateAndAssert(getShort(), getShort(), params, 2, 2)
        truncateAndAssert(getShort(), getMedium(), params, 2, 4)
        truncateAndAssert(getShort(), getLong(), params, 2, 5)

        truncateAndAssert(getMedium(), getEmpty(), params, 4, 0)
        truncateAndAssert(getMedium(), getShort(), params, 4, 2)
        truncateAndAssert(getMedium(), getMedium(), params, 3, 4)
        truncateAndAssert(getMedium(), getLong(), params, 3, 4)

        truncateAndAssert(getLong(), getEmpty(), params, 7, 0)
        truncateAndAssert(getLong(), getShort(), params, 5, 2)
        truncateAndAssert(getLong(), getMedium(), params, 4, 3)
        truncateAndAssert(getLong(), getLong(), params, 3, 4)
    }

    @Test
    fun testTruncateEncodingsEmpty() {
        val params =
            TruncationParams(
                maxLength = 0,
                strategy = TruncationStrategy.LongestFirst,
                stride = 0,
                direction = TruncationDirection.Right,
            )

        truncateAndAssert(getEmpty(), getShort(), params, 0, 0)
        truncateAndAssert(getMedium(), getMedium(), params, 0, 0)
        truncateAndAssert(getLong(), getLong(), params, 0, 0)
    }
}
