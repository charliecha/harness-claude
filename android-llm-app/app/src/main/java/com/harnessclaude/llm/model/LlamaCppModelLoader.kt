package com.harnessclaude.llm.model

import com.harnessclaude.llm.nativebridge.LlamaJni
import com.harnessclaude.llm.nativebridge.LlamaJniBridge
import com.harnessclaude.llm.storage.PathValidator
import com.harnessclaude.llm.threading.ThreadChecker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

internal val NoOpJni: LlamaJniBridge =
    object : LlamaJniBridge {
        override fun loadModel(
            path: String,
            useMmap: Boolean,
            vocabOnly: Boolean,
        ): Long = 1L

        override fun freeModel(nativePtr: Long) = Unit

        override fun isValidGguf(path: String): Boolean = true

        override fun createContext(
            modelPtr: Long,
            nCtx: Int,
            nBatch: Int,
        ): Long = 1L

        override fun freeContext(ctxPtr: Long) = Unit

        override fun clearKvCache(ctxPtr: Long) = Unit

        override fun tokenize(
            ctxPtr: Long,
            text: String,
            addBos: Boolean,
        ): IntArray = intArrayOf(1)

        override fun tokenToText(
            ctxPtr: Long,
            tokenId: Int,
        ): String = "[STUB]"

        override fun isEog(
            ctxPtr: Long,
            tokenId: Int,
        ): Boolean = tokenId == 2

        override fun decode(
            ctxPtr: Long,
            tokenIds: IntArray,
        ): Int = 0

        override fun sampleNext(
            ctxPtr: Long,
            temperature: Float,
            topP: Float,
            seed: Int,
        ): Int = 1
    }

class LlamaCppModelLoader internal constructor(
    private val ioDispatcher: CoroutineDispatcher,
    private val pathValidator: PathValidator,
    private val jni: LlamaJniBridge,
    private val threadChecker: ThreadChecker,
    private val stubMode: Boolean = false,
) : ModelLoader {
    constructor(filesDir: File) : this(
        ioDispatcher = Dispatchers.IO,
        pathValidator = PathValidator(filesDir),
        jni = LlamaJni,
        threadChecker = ThreadChecker.Real,
        stubMode = false,
    )

    override suspend fun load(path: String): Result<ModelHandle> {
        if (stubMode) {
            return Result.success(
                ModelHandle.create(nativePtr = 1L, sourcePath = "stub", jni = NoOpJni),
            )
        }
        if (threadChecker.isOnMainThread()) {
            return Result.failure(ModelLoadError.CalledOnMainThread())
        }
        return withContext(ioDispatcher) {
            runCatching {
                pathValidator.requireSafe(path)

                val file = File(path)
                if (!file.exists() || !file.canRead()) {
                    throw ModelLoadError.FileNotFound(path)
                }
                if (!jni.isValidGguf(path)) {
                    throw ModelLoadError.InvalidFormat(path)
                }

                val started = System.nanoTime()
                val ptr = jni.loadModel(path, useMmap = true, vocabOnly = false)
                if (ptr == 0L) {
                    throw ModelLoadError.NativeFailure(
                        "llama_load_model_from_file returned NULL",
                    )
                }
                val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                Timber.tag("ModelLoader").i(
                    "loaded path=%s bytes=%d elapsedMs=%d",
                    path,
                    file.length(),
                    elapsedMs,
                )
                ModelHandle.create(nativePtr = ptr, sourcePath = path, jni = jni)
            }
        }
    }
}
