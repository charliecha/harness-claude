package com.harnessclaude.llm.model

/**
 * Loads a GGUF-format LLM model from a local file path into native
 * memory and returns an opaque, releasable [ModelHandle].
 *
 * Implementations must:
 *  - Refuse main-thread invocation (`load` is suspending; ANR safety).
 *  - Validate the path lives under app-private storage (path traversal).
 *  - Never throw out of [load]; surface failures as [Result.failure] with
 *    a [ModelLoadError] subtype.
 *
 * See [com.harnessclaude.llm.model.LlamaCppModelLoader] for the
 * production implementation.
 */
interface ModelLoader {
    suspend fun load(path: String): Result<ModelHandle>
}
