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

/**
 * `llama.cpp`-backed implementation of [ModelLoader].
 *
 * Flow (ADR-004 §4.1):
 *   1. Fail fast if invoked on the main thread.
 *   2. Switch to [ioDispatcher].
 *   3. Path validation (deny-list + canonical containment).
 *   4. Existence + readability check.
 *   5. GGUF magic byte check (cheap, before touching native loader).
 *   6. `LlamaJni.loadModel(path, useMmap = true)`.
 *   7. Wrap native pointer in [ModelHandle].
 *
 * Any failure surfaces as [Result.failure] with a [ModelLoadError] subtype;
 * the native side never leaks a half-loaded model.
 */
class LlamaCppModelLoader internal constructor(
    private val ioDispatcher: CoroutineDispatcher,
    private val pathValidator: PathValidator,
    private val jni: LlamaJniBridge,
    private val threadChecker: ThreadChecker,
) : ModelLoader {
    /** Public constructor wired to production singletons. */
    constructor(filesDir: File) : this(
        ioDispatcher = Dispatchers.IO,
        pathValidator = PathValidator(filesDir),
        jni = LlamaJni,
        threadChecker = ThreadChecker.Real,
    )

    override suspend fun load(path: String): Result<ModelHandle> {
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
                val ptr = jni.loadModel(path, useMmap = true)
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
