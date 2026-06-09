package com.harnessclaude.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.harnessclaude.llm.model.LlamaCppModelLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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
 * Expects a tiny GGUF fixture at `androidTest/assets/tiny.gguf`. The
 * fixture is NOT checked into git (see `.gitignore`); CI seeds it before
 * running this suite.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LlamaCppLoaderInstrumentedTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun stagedFixture(): File? {
        val dest = File(ctx.filesDir, "models/tiny.gguf")
        dest.parentFile?.mkdirs()
        return runCatching {
            ctx.resources.assets.open("tiny.gguf").use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            dest
        }.getOrNull()
    }

    @Test
    fun loads_real_model_then_releases() =
        runBlocking {
            val fixture =
                stagedFixture()
                    ?: error("missing androidTest/assets/tiny.gguf — CI must seed this fixture")

            val loader = LlamaCppModelLoader(filesDir = ctx.filesDir)
            val result = loader.load(fixture.absolutePath)

            assertTrue("load should succeed: ${result.exceptionOrNull()}", result.isSuccess)
            val handle = result.getOrThrow()
            assertFalse(handle.isReleased)
            handle.close()
            assertTrue(handle.isReleased)
        }

    @Test
    fun rejects_unsafe_external_storage_path() =
        runBlocking {
            val loader = LlamaCppModelLoader(filesDir = ctx.filesDir)
            val result = loader.load("/sdcard/Download/anything.gguf")
            assertTrue(result.isFailure)
        }
}
