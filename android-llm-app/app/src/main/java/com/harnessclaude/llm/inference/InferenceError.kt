package com.harnessclaude.llm.inference

sealed class InferenceError(message: String) : Exception(message) {
    class HandleReleased : InferenceError("ModelHandle already released before creating session")

    class ContextCreationFailed : InferenceError("llama_init_from_model returned NULL")

    class DecodeFailed(code: Int) : InferenceError("llama_decode failed: code=$code")

    class ConcurrentGeneration : InferenceError("generate() already in progress on this session")

    class SessionAlreadyReleased : InferenceError("InferenceSession already released")
}
