package com.harnessclaude.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.harnessclaude.llm.model.LlamaCppModelLoader
import com.harnessclaude.llm.nativebridge.LlamaJni
import com.harnessclaude.llm.nativebridge.LlamaJniBridge
import com.harnessclaude.llm.storage.PathValidator
import com.harnessclaude.llm.threading.ThreadChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the full JNI path: real `libllama_jni.so` + real GGUF file.
 *
 * Marked [LargeTest] so the gatekeeper skips it by default — only the
 * CI's connectedAndroidTest job runs it.
 *
 * Fixtures in androidTest/assets/:
 *  - ggml-vocab-llama-spm.gguf  — vocab-only GGUF from llama.cpp submodule (724 KB),
 *                                  used to exercise the real loadModel JNI path with
 *                                  vocabOnly=true (no weight tensors needed).
 *  - tinyllama-test.gguf         — 640-byte minimal fixture, used only for magic/isValidGguf
 *                                  checks; NOT suitable for real loadModel (n_tensors=0).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LlamaCppLoaderInstrumentedTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val instrCtx = InstrumentationRegistry.getInstrumentation().context

    private fun stagedFixture(assetName: String): File? {
        val dest = File(ctx.filesDir, "models/$assetName")
        dest.parentFile?.mkdirs()
        return runCatching {
            instrCtx.assets.open(assetName).use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            dest
        }.getOrNull()
    }

    /** Real JNI path: vocab-only GGUF loads successfully, returns non-zero non-stub ptr. */
    @Test
    fun loadModel_vocabOnly_returnsValidNativeHandle() =
        runBlocking {
            val fixture =
                stagedFixture("ggml-vocab-llama-spm.gguf")
                    ?: error("missing androidTest/assets/ggml-vocab-llama-spm.gguf")

            val loader = vocabOnlyLoader()
            val result = loader.load(fixture.absolutePath)

            assertTrue("vocab load should succeed: ${result.exceptionOrNull()}", result.isSuccess)
            val handle = result.getOrThrow()

            // Real mode: must not be 0 or the STUB sentinel 0xC0FFEE
            assertNotEquals("nativePtr must not be 0", 0L, handle.nativePointer())
            assertNotEquals("nativePtr must not be STUB sentinel 0xC0FFEE", 0xC0FFEEL, handle.nativePointer())

            assertFalse(handle.isReleased)
            handle.close()
            assertTrue(handle.isReleased)
        }

    /** After close(), the handle must report isReleased=true. */
    @Test
    fun loadModel_afterClose_isReleasedTrue() =
        runBlocking {
            val fixture =
                stagedFixture("ggml-vocab-llama-spm.gguf")
                    ?: error("missing androidTest/assets/ggml-vocab-llama-spm.gguf")

            val handle = vocabOnlyLoader().load(fixture.absolutePath).getOrThrow()
            handle.close()
            assertTrue("handle must be released after close()", handle.isReleased)
        }

    /** PathValidator must reject external storage paths before any JNI call. */
    @Test
    fun rejects_unsafe_external_storage_path() =
        runBlocking {
            val loader = LlamaCppModelLoader(filesDir = ctx.filesDir)
            val result = loader.load("/sdcard/Download/anything.gguf")
            assertTrue(result.isFailure)
        }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a loader that wraps LlamaJni with vocabOnly=true so the vocab fixture
     * is loaded without weight tensors (which it doesn't have).
     */
    private fun vocabOnlyLoader(): LlamaCppModelLoader {
        val vocabJni: LlamaJniBridge =
            object : LlamaJniBridge by LlamaJni {
                override fun loadModel(
                    path: String,
                    useMmap: Boolean,
                    vocabOnly: Boolean,
                ): Long = LlamaJni.loadModel(path, useMmap = useMmap, vocabOnly = true)
            }
        return LlamaCppModelLoader(
            ioDispatcher = Dispatchers.IO,
            pathValidator = PathValidator(ctx.filesDir),
            jni = vocabJni,
            threadChecker = ThreadChecker.Real,
            stubMode = false,
        )
    }
}
