package io.github.kotlinmania.tokenizers.tokenizer

/**
 * A `Decoder` changes raw tokens into their more readable form.
 */
public interface Decoder {
    public fun decode(tokens: List<String>): Result<String> {
        val results = decodeChain(tokens)
        return results.map { it.joinToString("") }
    }

    public fun decodeChain(tokens: List<String>): Result<List<String>>
}
