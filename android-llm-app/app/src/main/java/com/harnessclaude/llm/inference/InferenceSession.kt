package com.harnessclaude.llm.inference

import com.harnessclaude.llm.nativebridge.LlamaJniBridge
import com.harnessclaude.llm.threading.ThreadChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class InferenceSession internal constructor(
    ctxPtr: Long,
    private val nCtx: Int,
    private val jni: LlamaJniBridge,
    private val threadChecker: ThreadChecker,
) : Closeable {
    @Volatile private var ctxPtr: Long = ctxPtr
    private val lock = Any()
    private val generating = AtomicBoolean(false)

    val contextSize: Int get() = nCtx
    val isReleased: Boolean get() = ctxPtr == 0L

    fun generate(
        prompt: String,
        params: InferenceParams = InferenceParams(),
    ): Flow<String> =
        channelFlow {
            check(!threadChecker.isOnMainThread()) {
                "generate() must not be called on main thread"
            }
            val currentCtx = ctxPtr
            check(currentCtx != 0L) { throw InferenceError.SessionAlreadyReleased() }
            check(generating.compareAndSet(false, true)) {
                throw InferenceError.ConcurrentGeneration()
            }

            val startMs = System.currentTimeMillis()
            var tokenCount = 0

            try {
                val promptTokens = jni.tokenize(currentCtx, prompt, addBos = true)
                if (promptTokens.isEmpty()) return@channelFlow

                val prefillCode = jni.decode(currentCtx, promptTokens)
                if (prefillCode != 0) throw InferenceError.DecodeFailed(prefillCode)

                val ttft = System.currentTimeMillis() - startMs
                Timber.d("TTFT: ${ttft}ms, prompt_tokens=${promptTokens.size}")

                generateLoop(currentCtx, params, this)
                tokenCount = params.maxNewTokens
            } finally {
                generating.set(false)
                val elapsed = System.currentTimeMillis() - startMs
                if (elapsed > 0 && tokenCount > 0) {
                    Timber.d("Inference done: $tokenCount tokens in ${elapsed}ms (${tokenCount * 1000L / elapsed} t/s)")
                }
            }
        }.flowOn(Dispatchers.Default)

    @Suppress("LoopWithTooManyJumpStatements")
    private suspend fun generateLoop(
        ctxPtr: Long,
        params: InferenceParams,
        scope: ProducerScope<String>,
    ) {
        var generated = 0
        while (generated < params.maxNewTokens && !scope.isClosedForSend) {
            if (!scope.coroutineContext.isActive) break

            val tokenId = jni.sampleNext(ctxPtr, params.temperature, params.topP, params.seed)

            if (jni.isEog(ctxPtr, tokenId)) break

            val piece = jni.tokenToText(ctxPtr, tokenId)
            scope.send(piece)
            generated++

            if (generated < params.maxNewTokens && !scope.isClosedForSend) {
                val stepCode = jni.decode(ctxPtr, intArrayOf(tokenId))
                if (stepCode != 0) throw InferenceError.DecodeFailed(stepCode)
            }
        }
        Timber.d("Generated $generated tokens")
    }

    override fun close() {
        synchronized(lock) {
            val ptr = ctxPtr
            if (ptr != 0L) {
                jni.freeContext(ptr)
                ctxPtr = 0L
            }
        }
    }

    companion object {
        internal fun create(
            ctxPtr: Long,
            nCtx: Int,
            jni: LlamaJniBridge,
            threadChecker: ThreadChecker = ThreadChecker.Real,
        ): InferenceSession = InferenceSession(ctxPtr, nCtx, jni, threadChecker)
    }
}
