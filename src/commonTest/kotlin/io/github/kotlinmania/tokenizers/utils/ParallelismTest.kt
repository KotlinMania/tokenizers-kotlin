package io.github.kotlinmania.tokenizers.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ParallelismTest {
    @Test
    fun testMaybeParallelIterator() {
        val v = mutableListOf(1u, 2u, 3u, 4u, 5u, 6u)

        assertEquals(21u, v.maybeParIter().sum())
        val mappedSum = v.maybeParIterMut().map { it * 2u }.sum()
        assertEquals(42u, mappedSum)
        for (i in 0 until v.size) {
            v[i] = v[i] * 2u
        }
        assertEquals(42u, v.maybeParIter().sum())
        assertEquals(42u, v.intoMaybeParIter().sum())
    }

    @Test
    fun testMaybeParallelSlice() {
        val v = listOf(1, 2, 3, 4, 5)
        val chunks = v.maybeParChunks(2)
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), chunks)
    }
}
