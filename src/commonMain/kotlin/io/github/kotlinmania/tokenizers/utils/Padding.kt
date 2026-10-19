// port-lint: source utils/padding.rs
package io.github.kotlinmania.tokenizers.utils

import io.github.kotlinmania.tokenizers.tokenizer.Encoding

/**
 * The various possible padding directions.
 */
public enum class PaddingDirection {
    Left,
    Right;

    public fun asStr(): String =
        when (this) {
            Left -> "left"
            Right -> "right"
        }
}

public sealed class PaddingStrategy {
    public data object BatchLongest : PaddingStrategy()
    public data class Fixed(public val size: Int) : PaddingStrategy()
}

public data class PaddingParams(
    public val strategy: PaddingStrategy = PaddingStrategy.BatchLongest,
    public val direction: PaddingDirection = PaddingDirection.Right,
    public val padToMultipleOf: Int? = null,
    public val padId: UInt = 0u,
    public val padTypeId: UInt = 0u,
    public val padToken: String = "[PAD]",
) {
    public companion object {
        public fun default(): PaddingParams = PaddingParams()
    }
}

public fun padEncodings(encodings: List<Encoding>, params: PaddingParams): Result<Unit> {
    if (encodings.isEmpty()) {
        return Result.success(Unit)
    }

    var padLength = when (val strategy = params.strategy) {
        is PaddingStrategy.Fixed -> strategy.size
        is PaddingStrategy.BatchLongest -> encodings.maxOf { it.getIds().size }
    }

    val multiple = params.padToMultipleOf
    if (multiple != null && multiple > 0 && padLength % multiple > 0) {
        padLength += multiple - padLength % multiple
    }

    for (encoding in encodings) {
        encoding.pad(
            padLength,
            params.padId,
            params.padTypeId,
            params.padToken,
            params.direction,
        )
    }

    return Result.success(Unit)
}
