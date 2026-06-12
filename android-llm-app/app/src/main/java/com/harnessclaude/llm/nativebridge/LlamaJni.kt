package com.harnessclaude.llm.nativebridge

/**
 * Native bridge to llama.cpp.
 *
 * All methods are blocking; callers must invoke from a background
 * dispatcher (ADR-004 §3.1, ADR-006 §3.4).
 *
 * Wrapped behind [LlamaJniBridge] so tests can inject a fake.
 */
internal object LlamaJni : LlamaJniBridge {
    init {
        System.loadLibrary("llama_jni")
    }

    // ── Model lifecycle (FR-004) ──────────────────────────────────────
    external override fun loadModel(
        path: String,
        useMmap: Boolean,
        vocabOnly: Boolean,
    ): Long

    external override fun freeModel(nativePtr: Long)

    external override fun isValidGguf(path: String): Boolean

    // ── Context lifecycle (FR-006) ────────────────────────────────────
    external override fun createContext(
        modelPtr: Long,
        nCtx: Int,
        nBatch: Int,
    ): Long

    external override fun freeContext(ctxPtr: Long)

    external override fun clearKvCache(ctxPtr: Long)

    // ── Token operations (FR-006) ─────────────────────────────────────
    external override fun tokenize(
        ctxPtr: Long,
        text: String,
        addBos: Boolean,
    ): IntArray

    external override fun tokenToText(
        ctxPtr: Long,
        tokenId: Int,
    ): String

    external override fun isEog(
        ctxPtr: Long,
        tokenId: Int,
    ): Boolean

    // ── Inference (FR-006) ────────────────────────────────────────────
    external override fun decode(
        ctxPtr: Long,
        tokenIds: IntArray,
    ): Int

    external override fun sampleNext(
        ctxPtr: Long,
        temperature: Float,
        topP: Float,
        seed: Int,
    ): Int
}
