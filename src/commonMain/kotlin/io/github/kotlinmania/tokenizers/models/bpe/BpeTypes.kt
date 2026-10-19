package io.github.kotlinmania.tokenizers.models.bpe

/**
 * A pair of token IDs representing a BPE merge rule key.
 */
public data class PairIds(
    public val first: UInt,
    public val second: UInt,
) {
    internal constructor(pair: Pair<UInt, UInt>) : this(pair.first, pair.second)

    internal fun toPair(): Pair<UInt, UInt> = Pair(first, second)
}

/**
 * A pair of strings representing a BPE merge rule.
 */
public data class BpeMerge(
    public val first: String,
    public val second: String,
) {
    internal constructor(pair: Pair<String, String>) : this(pair.first, pair.second)

    internal fun toPair(): Pair<String, String> = Pair(first, second)
}

public sealed class BpeError(
    message: String,
) : Exception(message) {
    public data class BadMerges(
        val line: Int,
    ) : BpeError("Merges text file invalid at line $line")

    public data class MergeTokenOutOfVocabulary(
        val token: String,
    ) : BpeError("Token `$token` out of vocabulary")

    public data class UnkTokenOutOfVocabulary(
        val token: String,
    ) : BpeError("Unk token `$token` not found in the vocabulary")

    public data object BadVocabulary : BpeError("Bad vocabulary json file")

    public data object InvalidDropout : BpeError("Dropout should be between 0 and 1, inclusive")
}
