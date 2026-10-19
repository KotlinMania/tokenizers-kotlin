// port-lint: source utils/truncation.rs
package io.github.kotlinmania.tokenizers.utils

import io.github.kotlinmania.tokenizers.tokenizer.Encoding
import kotlin.math.max

public enum class TruncationDirection {
    Left,
    Right,
    ;

    public fun asStr(): String =
        when (this) {
            Left -> "left"
            Right -> "right"
        }

    public companion object {
        public fun default(): TruncationDirection = Right
    }
}

public enum class TruncationStrategy {
    LongestFirst,
    OnlyFirst,
    OnlySecond,
    ;

    public fun asStr(): String =
        when (this) {
            LongestFirst -> "longest_first"
            OnlyFirst -> "only_first"
            OnlySecond -> "only_second"
        }

    public companion object {
        public fun default(): TruncationStrategy = LongestFirst
    }
}

public sealed class TruncationError(
    message: String,
) : Exception(message) {
    public data object SecondSequenceNotProvided :
        TruncationError("Truncation error: Second sequence not provided")

    public data object SequenceTooShort :
        TruncationError("Truncation error: Sequence to truncate too short to respect the provided max_length")
}

public data class TruncationParams(
    public val direction: TruncationDirection = TruncationDirection.Right,
    public val maxLength: Int = 512,
    public val strategy: TruncationStrategy = TruncationStrategy.LongestFirst,
    public val stride: Int = 0,
) {
    public companion object {
        public fun default(): TruncationParams = TruncationParams()
    }
}

public data class TruncatedEncodings(
    public val first: Encoding,
    public val second: Encoding?,
)

public fun truncateEncodings(
    encoding: Encoding,
    pairEncoding: Encoding?,
    params: TruncationParams,
): Result<TruncatedEncodings> {
    val enc = encoding
    val pairEnc = pairEncoding

    if (params.maxLength == 0) {
        enc.truncate(0, params.stride, params.direction)
        pairEnc?.truncate(0, params.stride, params.direction)
        return Result.success(TruncatedEncodings(enc, pairEnc))
    }

    val totalLength = enc.getIds().size + (pairEnc?.getIds()?.size ?: 0)
    val toRemove =
        if (totalLength > params.maxLength) {
            totalLength - params.maxLength
        } else {
            return Result.success(TruncatedEncodings(enc, pairEnc))
        }

    when (params.strategy) {
        TruncationStrategy.LongestFirst -> {
            if (pairEnc != null) {
                var n1 = enc.getIds().size
                var n2 = pairEnc.getIds().size
                var swap = false

                if (n1 > n2) {
                    swap = true
                    val tmp = n1
                    n1 = n2
                    n2 = tmp
                }

                if (n1 > params.maxLength) {
                    n2 = n1
                } else {
                    n2 = max(n1, params.maxLength - n1)
                }

                if (n1 + n2 > params.maxLength) {
                    n1 = params.maxLength / 2
                    n2 = n1 + params.maxLength % 2
                }

                if (swap) {
                    val tmp = n1
                    n1 = n2
                    n2 = tmp
                }

                enc.truncate(n1, params.stride, params.direction)
                pairEnc.truncate(n2, params.stride, params.direction)
            } else {
                enc.truncate(totalLength - toRemove, params.stride, params.direction)
            }
        }

        TruncationStrategy.OnlyFirst -> {
            val targetLen = enc.getIds().size
            if (targetLen > toRemove) {
                enc.truncate(targetLen - toRemove, params.stride, params.direction)
            } else {
                return Result.failure(TruncationError.SequenceTooShort)
            }
        }

        TruncationStrategy.OnlySecond -> {
            val target = pairEnc ?: return Result.failure(TruncationError.SecondSequenceNotProvided)
            val targetLen = target.getIds().size
            if (targetLen > toRemove) {
                target.truncate(targetLen - toRemove, params.stride, params.direction)
            } else {
                return Result.failure(TruncationError.SequenceTooShort)
            }
        }
    }

    return Result.success(TruncatedEncodings(enc, pairEnc))
}
