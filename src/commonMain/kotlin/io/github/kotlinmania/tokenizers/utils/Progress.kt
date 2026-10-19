// port-lint: source utils/progress.rs
package io.github.kotlinmania.tokenizers.utils

public class ProgressBar(
    private var len: ULong = 0uL,
) {
    public fun setLength(length: ULong) {
        this.len = length
    }

    public fun setMessage(message: String) {
    }

    public fun finish() {
    }

    public fun reset() {
    }

    public fun inc(delta: ULong = 1uL) {
    }

    public fun setStyle(style: ProgressStyle) {
    }

    public companion object {
        public fun new(length: ULong): ProgressBar = ProgressBar(length)
    }
}

public class ProgressStyle {
    public fun template(template: String): Result<ProgressStyle> = Result.success(this)

    public companion object {
        public fun defaultBar(): ProgressStyle = ProgressStyle()
    }
}
