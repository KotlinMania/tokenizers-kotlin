// port-lint: source utils/parallelism.rs
package io.github.kotlinmania.tokenizers.utils

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt

public const val ENV_VARIABLE: String = "TOKENIZERS_PARALLELISM"

private val usedParallelism: AtomicBoolean = AtomicBoolean(false)
private val parallelismOverride: AtomicInt = AtomicInt(0)

/**
 * Check if the TOKENIZERS_PARALLELISM environment variable has been explicitly set.
 */
public fun isParallelismConfigured(): Boolean = getOverrideParallelism() != null

/**
 * Check if at some point we used a parallel iterator.
 */
public fun hasParallelismBeenUsed(): Boolean = usedParallelism.load()

private fun getOverrideParallelism(): Boolean? =
    when (parallelismOverride.load()) {
        1 -> false
        2 -> true
        else -> null
    }

private fun getEnvParallelism(): Boolean = true

public fun getParallelism(): Boolean = getOverrideParallelism() ?: getEnvParallelism()

/**
 * Set the value for TOKENIZERS_PARALLELISM for the current process.
 */
public fun setParallelism(value: Boolean) {
    parallelismOverride.store(if (value) 2 else 1)
}

internal fun <T> Iterable<T>.intoMaybeParIter(): List<T> {
    if (getParallelism()) {
        usedParallelism.store(true)
    }
    return this.toList()
}

internal fun <T> Iterable<T>.intoMaybeParIterCond(cond: Boolean): List<T> {
    if (cond && getParallelism()) {
        usedParallelism.store(true)
    }
    return this.toList()
}

internal fun <T> Iterable<T>.maybeParIter(): Sequence<T> {
    if (getParallelism()) {
        usedParallelism.store(true)
    }
    return this.asSequence()
}

internal fun <T> Iterable<T>.maybeParIterCond(cond: Boolean): Sequence<T> {
    if (cond && getParallelism()) {
        usedParallelism.store(true)
    }
    return this.asSequence()
}

internal fun <T> MutableList<T>.maybeParIterMut(): MutableList<T> {
    if (getParallelism()) {
        usedParallelism.store(true)
    }
    return this
}

internal fun <T> MutableList<T>.maybeParIterMutCond(cond: Boolean): MutableList<T> {
    if (cond && getParallelism()) {
        usedParallelism.store(true)
    }
    return this
}

internal fun <T> List<T>.maybeParChunks(chunkSize: Int): List<List<T>> {
    if (getParallelism()) {
        usedParallelism.store(true)
    }
    return this.chunked(chunkSize)
}

internal fun <T> List<T>.maybeParChunksCond(chunkSize: Int, cond: Boolean): List<List<T>> {
    if (cond && getParallelism()) {
        usedParallelism.store(true)
    }
    return this.chunked(chunkSize)
}
