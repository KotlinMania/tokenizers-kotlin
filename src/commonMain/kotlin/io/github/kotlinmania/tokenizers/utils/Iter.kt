// port-lint: source utils/iter.rs
package io.github.kotlinmania.tokenizers.utils

/**
 * ResultShunt adapter that processes an iterator of Results,
 * stopping at the first error encountered and capturing it.
 */
public class ResultShunt<T, E : Throwable> internal constructor(
    private val iterator: Iterator<Result<T>>,
) : Iterator<T> {
    private var error: Throwable? = null

    override fun hasNext(): Boolean {
        if (error != null) return false
        return iterator.hasNext()
    }

    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException("No more elements in ResultShunt iterator")
        val result = iterator.next()
        return if (result.isSuccess) {
            result.getOrThrow()
        } else {
            val ex = result.exceptionOrNull() ?: IllegalStateException("Unknown failure")
            error = ex
            throw ex
        }
    }

    public fun reconstruct(): Result<Unit> {
        val err = error
        return if (err != null) {
            Result.failure(err)
        } else {
            Result.success(Unit)
        }
    }

    public companion object {
        public fun <T, U> process(
            iterator: Iterator<Result<T>>,
            action: (ResultShunt<T, Throwable>) -> U,
        ): Result<U> {
            val shunt = ResultShunt<T, Throwable>(iterator)
            return try {
                val value = action(shunt)
                val status = shunt.reconstruct()
                if (status.isSuccess) {
                    Result.success(value)
                } else {
                    Result.failure(status.exceptionOrNull()!!)
                }
            } catch (ex: Throwable) {
                Result.failure(ex)
            }
        }
    }
}

/**
 * Split a string into lines while keeping trailing line endings (\n or \r\n).
 */
public fun String.linesWithEnding(): List<String> {
    if (this.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    var start = 0
    var i = 0
    val len = this.length
    while (i < len) {
        if (this[i] == '\n') {
            lines.add(this.substring(start, i + 1))
            start = i + 1
        } else if (this[i] == '\r') {
            if (i + 1 < len && this[i + 1] == '\n') {
                lines.add(this.substring(start, i + 2))
                start = i + 2
                i++
            } else {
                lines.add(this.substring(start, i + 1))
                start = i + 1
            }
        }
        i++
    }
    if (start < len) {
        lines.add(this.substring(start, len))
    }
    return lines
}
