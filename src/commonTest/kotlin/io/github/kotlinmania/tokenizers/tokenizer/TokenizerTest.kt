package io.github.kotlinmania.tokenizers.tokenizer

import io.github.kotlinmania.tokenizers.models.bpe.BpeBuilder
import io.github.kotlinmania.tokenizers.models.bpe.BpeMerge
import io.github.kotlinmania.tokenizers.pretokenizers.ByteLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenizerTest {
    @Test
    fun testTokenizerEndToEnd() {
        // Construct a simple vocabulary and merge table
        val vocab =
            mapOf(
                "H" to 0u,
                "e" to 1u,
                "l" to 2u,
                "o" to 3u,
                " " to 4u,
                "w" to 5u,
                "r" to 6u,
                "d" to 7u,
                "Ġ" to 8u,
                "He" to 9u,
                "ll" to 10u,
                "Hell" to 11u,
                "Hello" to 12u,
                "Ġw" to 13u,
                "or" to 14u,
                "ld" to 15u,
                "Ġworld" to 16u,
            )
        val merges =
            listOf(
                BpeMerge("H", "e"),
                BpeMerge("l", "l"),
                BpeMerge("He", "ll"),
                BpeMerge("Hell", "o"),
                BpeMerge("Ġ", "w"),
                BpeMerge("o", "r"),
                BpeMerge("l", "d"),
            )

        val bpeRes =
            BpeBuilder()
                .vocabAndMerges(vocab, merges)
                .build()
        assertTrue(bpeRes.isSuccess)
        val bpe = bpeRes.getOrThrow()

        val byteLevel = ByteLevel(addPrefixSpace = false)
        val tokenizer =
            Tokenizer(bpe)
                .withPreTokenizer(byteLevel)
                .withDecoder(byteLevel)
                .withPostProcessor(byteLevel)

        val encRes = tokenizer.encode("Hello world")
        assertTrue(encRes.isSuccess)
        val encoding = encRes.getOrThrow()

        assertEquals(listOf(12u, 13u, 14u, 15u), encoding.getIds())
        assertEquals(listOf("Hello", "Ġw", "or", "ld"), encoding.getTokens())

        val decRes = tokenizer.decode(encoding.getIds())
        assertTrue(decRes.isSuccess)
        assertEquals("Hello world", decRes.getOrThrow())
    }

    @Test
    fun testAddedTokens() {
        val vocab = mapOf("a" to 0u, "b" to 1u)
        val bpe = BpeBuilder().vocabAndMerges(vocab, emptyList()).build().getOrThrow()
        val tokenizer = Tokenizer(bpe)

        val addedCount = tokenizer.addSpecialTokens(listOf(AddedToken.from("[CLS]", true), AddedToken.from("[SEP]", true)))
        assertEquals(2, addedCount)
        assertEquals(2u, tokenizer.tokenToId("[CLS]"))
        assertEquals(3u, tokenizer.tokenToId("[SEP]"))
    }
}
