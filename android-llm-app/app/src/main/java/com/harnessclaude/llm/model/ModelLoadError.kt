package com.harnessclaude.llm.model

/**
 * Sealed hierarchy of errors that [ModelLoader] can fail with.
 *
 * Using a sealed class lets callers exhaustively pattern-match in `when`
 * blocks and react to specific failure modes (e.g. show a "permission
 * needed" hint only when the path was rejected).
 */
sealed class ModelLoadError(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    /** Path points to a file that does not exist or is not readable. */
    class FileNotFound(path: String) :
        ModelLoadError("Model file not found or unreadable: $path")

    /** File exists but does not start with the GGUF magic bytes. */
    class InvalidFormat(path: String) :
        ModelLoadError("Not a valid GGUF file: $path")

    /**
     * Path is outside the app-private storage root or hits a deny-listed
     * prefix (`/sdcard/`, `/data/local/tmp/`, `/system/`).
     * See ADR-004 §6.1.
     */
    class UnsafePath(path: String) :
        ModelLoadError("Path not allowed (must live in app private storage): $path")

    /**
     * `ModelLoader.load()` was called on the main thread. Loading a model
     * blocks for seconds and would trigger ANR.
     */
    class CalledOnMainThread :
        ModelLoadError("ModelLoader.load() must not be called on the main thread")

    /** Native llama.cpp call returned NULL / non-zero status. */
    class NativeFailure(reason: String) :
        ModelLoadError("Native load failed: $reason")
}
