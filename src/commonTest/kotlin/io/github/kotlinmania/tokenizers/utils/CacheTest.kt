package io.github.kotlinmania.tokenizers.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CacheTest {
    @Test
    fun testCacheBasicOperations() {
        val cache = Cache.new<String, Int>(3)
        assertEquals(3, cache.capacity)

        cache.set("a", 1)
        cache.set("b", 2)
        assertEquals(1, cache.get("a"))
        assertEquals(2, cache.get("b"))
        assertNull(cache.get("c"))

        val values = cache.getValues(listOf("a", "b", "c"))
        assertEquals(listOf(1, 2, null), values)

        cache.set("c", 3)
        cache.set("d", 4)
        assertNull(cache.get("d"))

        cache.clear()
        assertNull(cache.get("a"))

        val freshCache = cache.fresh()
        assertEquals(3, freshCache.capacity)
    }
}
