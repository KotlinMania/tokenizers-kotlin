package io.github.kotlinmania.tokenizers.tokenizer

public sealed class ProcessorError(
    message: String,
) : Exception(message) {
    public data object InvalidEncodingsVecLength : ProcessorError("encodings vector length must be either 1 or 2")
}

/**
 * A `PostProcessor` has the responsibility to post process an encoded output of the `Tokenizer`.
 * It adds any special tokens that a language model would require.
 */
public interface PostProcessor {
    /**
     * Returns the number of tokens that will be added during the processing step.
     */
    public fun addedTokens(isPair: Boolean): Int

    /**
     * Process both encodings and returns a new merged one.
     */
    public fun process(
        encoding: Encoding,
        pairEncoding: Encoding?,
        addSpecialTokens: Boolean,
    ): Result<Encoding> {
        val encodings =
            if (pairEncoding != null) {
                listOf(encoding, pairEncoding)
            } else {
                listOf(encoding)
            }
        for ((i, enc) in encodings.withIndex()) {
            enc.setSequenceId(i)
            for (over in enc.getOverflowing()) {
                over.setSequenceId(i)
            }
            enc.setTypeIds(List(enc.len()) { i.toUInt() })
        }
        val processed = processEncodings(encodings, addSpecialTokens)
        return processed.map { Encoding.merge(it, false) }
    }

    /**
     * Process any amount of encodings and returns a series of encodings (might merge them).
     */
    public fun processEncodings(
        encodings: List<Encoding>,
        addSpecialTokens: Boolean,
    ): Result<List<Encoding>>

    public companion object {
        public fun defaultProcess(
            encodings: List<Encoding>,
            addSpecialTokens: Boolean = false,
        ): Result<List<Encoding>> =
            when (encodings.size) {
                1 -> {
                    Result.success(encodings)
                }

                else -> {
                    val finalEncoding = Encoding.default()
                    for ((i, encoding) in encodings.withIndex()) {
                        encoding.setSequenceId(i)
                        finalEncoding.mergeWith(encoding, false)
                    }
                    Result.success(listOf(finalEncoding))
                }
            }
    }
}
