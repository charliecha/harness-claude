package com.harnessclaude.llm.inference

import com.harnessclaude.llm.model.ModelHandle
import com.harnessclaude.llm.nativebridge.LlamaJni
import com.harnessclaude.llm.nativebridge.LlamaJniBridge
import com.harnessclaude.llm.threading.ThreadChecker

class InferenceSessionFactory internal constructor(
    private val jni: LlamaJniBridge = LlamaJni,
    private val threadChecker: ThreadChecker = ThreadChecker.Real,
) {
    constructor() : this(jni = LlamaJni, threadChecker = ThreadChecker.Real)

    fun create(
        handle: ModelHandle,
        params: InferenceParams,
    ): InferenceSession {
        check(!handle.isReleased) { throw InferenceError.HandleReleased() }
        val ctxPtr = jni.createContext(handle.nativePointer(), params.nCtx, params.nBatch)
        if (ctxPtr == 0L) throw InferenceError.ContextCreationFailed()
        return InferenceSession.create(ctxPtr, params.nCtx, jni, threadChecker)
    }
}
