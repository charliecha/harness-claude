package com.harnessclaude.llm.nativebridge

/** Test seam for [LlamaJni]; production code uses the object. */
internal interface LlamaJniBridge {
    // ── Model lifecycle (FR-004) ──────────────────────────────────────
    fun loadModel(
        path: String,
        useMmap: Boolean,
    ): Long

    fun freeModel(nativePtr: Long)

    fun isValidGguf(path: String): Boolean

    // ── Context lifecycle (FR-006) ────────────────────────────────────

    /** Creates a llama_context from an already-loaded model pointer.
     *  @return context pointer, or 0L on failure. */
    fun createContext(
        modelPtr: Long,
        nCtx: Int,
        nBatch: Int,
    ): Long

    /** Frees the llama_context. Safe to call with 0L. */
    fun freeContext(ctxPtr: Long)

    /** Clears the KV self-cache (llama_kv_self_clear). */
    fun clearKvCache(ctxPtr: Long)

    // ── Token operations (FR-006) ─────────────────────────────────────

    /** Tokenizes text; returns token-id array. Includes BOS when addBos=true. */
    fun tokenize(
        ctxPtr: Long,
        text: String,
        addBos: Boolean,
    ): IntArray

    /** Returns the text piece for a single token id (may be empty). */
    fun tokenToText(
        ctxPtr: Long,
        tokenId: Int,
    ): String

    /** Returns true if the token is an end-of-generation token. */
    fun isEog(
        ctxPtr: Long,
        tokenId: Int,
    ): Boolean

    // ── Inference (FR-006) ────────────────────────────────────────────

    /** Runs llama_decode on the provided token ids.
     *  @return llama_decode exit code (0 = success). */
    fun decode(
        ctxPtr: Long,
        tokenIds: IntArray,
    ): Int

    /** Samples the next token using a greedy+top-p+temperature chain.
     *  @return sampled token id. */
    fun sampleNext(
        ctxPtr: Long,
        temperature: Float,
        topP: Float,
        seed: Int,
    ): Int
}
