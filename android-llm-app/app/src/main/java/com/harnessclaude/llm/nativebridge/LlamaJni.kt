package com.harnessclaude.llm.nativebridge

/**
 * Native bridge to llama.cpp.
 *
 * Kept intentionally tiny — only the three calls needed for FR-004.
 * All methods are blocking; callers must invoke from a background
 * dispatcher (ADR-004 §3.1).
 *
 * Wrapped behind [LlamaJniBridge] so tests can inject a fake.
 */
internal object LlamaJni : LlamaJniBridge {
    init {
        System.loadLibrary("llama_jni")
    }

    /**
     * @return native model pointer, or `0L` on failure.
     */
    external override fun loadModel(
        path: String,
        useMmap: Boolean,
    ): Long

    external override fun freeModel(nativePtr: Long)

    /** Lightweight magic-byte check; does not load the full model. */
    external override fun isValidGguf(path: String): Boolean
}

/** Test seam for [LlamaJni]; production code uses the object above. */
internal interface LlamaJniBridge {
    fun loadModel(
        path: String,
        useMmap: Boolean,
    ): Long

    fun freeModel(nativePtr: Long)

    fun isValidGguf(path: String): Boolean
}
