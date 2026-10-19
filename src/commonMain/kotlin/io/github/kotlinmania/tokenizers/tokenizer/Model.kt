package io.github.kotlinmania.tokenizers.tokenizer

/**
 * Represents a model used during Tokenization (like BPE, WordPiece, WordLevel, or Unigram).
 */
public interface Model {
    /**
     * Tokenize the given sequence into multiple underlying Tokens. The offsets on the Token
     * are expected to be relative to the given sequence.
     */
    public fun tokenize(sequence: String): Result<List<Token>>

    /**
     * Find the ID associated to a string token.
     */
    public fun tokenToId(token: String): UInt?

    /**
     * Find the string token associated to an ID.
     */
    public fun idToToken(id: UInt): String?

    /**
     * Retrieve the entire vocabulary mapping (token -> ID).
     */
    public fun getVocab(): Map<String, UInt>

    /**
     * Retrieve the size of the vocabulary.
     */
    public fun getVocabSize(): Int

    /**
     * Save the current Model in the given folder, using the given prefix for the various
     * files that need to be saved.
     */
    public fun save(folder: String, prefix: String? = null): Result<List<String>>

    /**
     * Get an instance of a Trainer capable of training this Model.
     */
    public fun getTrainer(): Trainer
}

/**
 * A Trainer has the responsibility to train a model.
 */
public interface Trainer {
    public fun shouldShowProgress(): Boolean
    public fun train(model: Model): Result<List<String>>
}
