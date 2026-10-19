// port-lint: source utils/cache.rs
package io.github.kotlinmania.tokenizers.utils

import kotlin.concurrent.atomics.AtomicReference

public const val DEFAULT_CACHE_CAPACITY: Int = 10_000
public const val MAX_LENGTH: Int = 256

/**
 * Provides a simple multithread cache to speed up BPE tokenization that will try to read values
 * concurrently but won't block if another thread is writing.
 * Both get and set are not guaranteed to actually get or set.
 */
public class Cache<K : Any, V : Any>(
    public var capacity: Int,
) {
    private val map: AtomicReference<Map<K, V>> = AtomicReference(emptyMap())

    public constructor() : this(DEFAULT_CACHE_CAPACITY)

    public companion object {
        /**
         * Create new Cache with the given capacity.
         */
        public fun <K : Any, V : Any> new(capacity: Int = DEFAULT_CACHE_CAPACITY): Cache<K, V> = Cache(capacity)

        /**
         * Create default Cache.
         */
        public fun <K : Any, V : Any> default(): Cache<K, V> = new()
    }

    public fun fresh(): Cache<K, V> = Cache(capacity)

    public fun clear() {
        map.store(emptyMap())
    }

    public fun getValues(keys: Iterable<K>): List<V?>? {
        val current = map.load()
        return keys.map { current[it] }
    }

    public fun get(key: K): V? = map.load()[key]

    public fun setValues(entries: Iterable<Pair<K, V>>) {
        val entryList = entries.toList()
        if (entryList.isEmpty()) return
        while (true) {
            val current = map.load()
            if (current.size >= capacity) return
            val free = capacity - current.size
            val toAdd = entryList.take(free)
            val next = current.toMutableMap()
            for ((k, v) in toAdd) {
                next[k] = v
            }
            if (map.compareAndSet(current, next)) {
                return
            }
        }
    }

    public fun set(key: K, value: V) {
        setValues(listOf(Pair(key, value)))
    }

    public fun resize(capacity: Int) {
        this.capacity = capacity
        while (true) {
            val current = map.load()
            if (current.size <= capacity) return
            val next = current.entries.take(capacity).associate { it.key to it.value }
            if (map.compareAndSet(current, next)) {
                return
            }
        }
    }

    override fun equals(other: Any?): Boolean = other is Cache<*, *>

    override fun hashCode(): Int = capacity.hashCode()
}
