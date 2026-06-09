package com.harnessclaude.llm.model

import com.harnessclaude.llm.nativebridge.LlamaJni
import com.harnessclaude.llm.nativebridge.LlamaJniBridge
import java.io.Closeable

/**
 * Owns a native llama.cpp `llama_model*` pointer.
 *
 * Lifecycle rules (ADR-004 §6.6):
 *  - [release] is idempotent and thread-safe; only the first call frees
 *    the native resource.
 *  - After release, any access through [nativePointer] throws
 *    [IllegalStateException] — callers must check [isReleased] first.
 *  - Implements [Closeable] so it can be used with Kotlin `.use { }` and
 *    auto-released when a `viewModelScope` cancels.
 *
 * The [jni] indirection exists purely so unit tests can inject a fake
 * bridge; production callers should use the no-arg factory.
 */
class ModelHandle internal constructor(
    nativePtr: Long,
    private val sourcePath: String,
    private val jni: LlamaJniBridge,
) : Closeable {
    private val lock = Any()

    @Volatile
    private var nativePtr: Long = nativePtr

    val isReleased: Boolean
        get() = nativePtr == 0L

    /** Source path used at load time, kept for diagnostics. */
    fun sourcePath(): String = sourcePath

    /**
     * @throws IllegalStateException if [release] has already been called.
     */
    fun nativePointer(): Long {
        val current = nativePtr
        check(current != 0L) { "ModelHandle already released (source=$sourcePath)" }
        return current
    }

    fun release() {
        synchronized(lock) {
            val ptr = nativePtr
            if (ptr != 0L) {
                jni.freeModel(ptr)
                nativePtr = 0L
            }
        }
    }

    override fun close() = release()

    companion object {
        internal fun create(
            nativePtr: Long,
            sourcePath: String,
            jni: LlamaJniBridge = LlamaJni,
        ): ModelHandle = ModelHandle(nativePtr, sourcePath, jni)
    }
}
