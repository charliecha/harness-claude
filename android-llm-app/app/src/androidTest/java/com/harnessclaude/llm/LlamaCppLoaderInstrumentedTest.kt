package com.harnessclaude.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.harnessclaude.llm.model.LlamaCppModelLoader
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
 * Requires `androidTest/assets/tinyllama-test.gguf` — a minimal valid
 * GGUF fixture checked into the repo (640 bytes, no tensors, header only).
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LlamaCppLoaderInstrumentedTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun stagedFixture(assetName: String): File? {
        val dest = File(ctx.filesDir, "models/$assetName")
        dest.parentFile?.mkdirs()
        return runCatching {
            ctx.assets.open(assetName).use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            dest
        }.getOrNull()
    }

    @Test
    fun loadModel_returnsValidNativeHandle() =
        runBlocking {
            val fixture =
                stagedFixture("tinyllama-test.gguf")
                    ?: error("missing androidTest/assets/tinyllama-test.gguf")

            val loader = LlamaCppModelLoader(filesDir = ctx.filesDir)
            val result = loader.load(fixture.absolutePath)

            assertTrue("load should succeed: ${result.exceptionOrNull()}", result.isSuccess)
            val handle = result.getOrThrow()

            // Real mode: pointer must be non-zero and not the STUB sentinel
            assertNotEquals("nativePtr must not be 0", 0L, handle.nativePointer())
            assertNotEquals("nativePtr must not be STUB sentinel 0xC0FFEE", 0xC0FFEEL, handle.nativePointer())

            assertFalse(handle.isReleased)
            handle.close()
            assertTrue(handle.isReleased)
        }

    @Test
    fun loadModel_afterClose_isReleasedTrue() =
        runBlocking {
            val fixture =
                stagedFixture("tinyllama-test.gguf")
                    ?: error("missing androidTest/assets/tinyllama-test.gguf")

            val loader = LlamaCppModelLoader(filesDir = ctx.filesDir)
            val handle = loader.load(fixture.absolutePath).getOrThrow()
            handle.close()
            assertTrue("handle must be released after close()", handle.isReleased)
        }

    @Test
    fun rejects_unsafe_external_storage_path() =
        runBlocking {
            val loader = LlamaCppModelLoader(filesDir = ctx.filesDir)
            val result = loader.load("/sdcard/Download/anything.gguf")
            assertTrue(result.isFailure)
        }
}
