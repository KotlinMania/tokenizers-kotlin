package io.github.kotlinmania.tokenizers.tokenizer

/**
 * UTF-8 byte offsets (start inclusive, end exclusive).
 */
public data class Offsets(
    public val start: Int,
    public val end: Int,
) {
    internal constructor(pair: Pair<Int, Int>) : this(pair.first, pair.second)

    internal fun toPair(): Pair<Int, Int> = Pair(start, end)
}

/**
 * Represents a token produced by a Tokenizer or Model.
 */
public data class Token(
    public val id: UInt,
    public val value: String,
    public val offsets: Offsets,
) {
    public companion object {
        public fun new(id: UInt, value: String, offsets: Offsets): Token = Token(id, value, offsets)
    }
}

/**
 * Offsets within a sequence.
 */
public data class SequenceOffsets(
    public val sequenceId: Int,
    public val offsets: Offsets,
)

/**
 * Word index within a sequence.
 */
public data class SequenceWord(
    public val sequenceId: Int,
    public val wordId: UInt,
)

/**
 * Token indices range (start inclusive, end exclusive).
 */
public data class TokenIndices(
    public val start: Int,
    public val end: Int,
)
