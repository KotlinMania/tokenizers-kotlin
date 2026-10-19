package io.github.kotlinmania.tokenizers.utils

import io.github.kotlinmania.tokenizers.tokenizer.Encoding
import kotlin.test.Test
import kotlin.test.assertTrue

class PaddingTest {
    private fun getEncodings(): MutableList<Encoding> =
        mutableListOf(
            Encoding.new(ids = listOf(0u, 1u, 2u, 3u, 4u)),
            Encoding.new(ids = listOf(0u, 1u, 2u)),
        )

    @Test
    fun testPadToMultiple() {
        val encodings = getEncodings()
        val params = PaddingParams(
            strategy = PaddingStrategy.Fixed(7),
            direction = PaddingDirection.Right,
            padToMultipleOf = 8,
            padId = 0u,
            padTypeId = 0u,
            padToken = "[PAD]",
        )
        val res = padEncodings(encodings, params)
        assertTrue(res.isSuccess)
        assertTrue(encodings.all { it.getIds().size == 8 })

        val encodingsBatch = getEncodings()
        val paramsBatch = params.copy(
            strategy = PaddingStrategy.BatchLongest,
            padToMultipleOf = 6,
        )
        val resBatch = padEncodings(encodingsBatch, paramsBatch)
        assertTrue(resBatch.isSuccess)
        assertTrue(encodingsBatch.all { it.getIds().size == 6 })

        val encodingsZero = getEncodings()
        val paramsZero = params.copy(padToMultipleOf = 0)
        val resZero = padEncodings(encodingsZero, paramsZero)
        assertTrue(resZero.isSuccess)
    }
}
