package com.harnessclaude.llm.model

import com.harnessclaude.llm.nativebridge.LlamaJniBridge
import com.harnessclaude.llm.storage.PathValidator
import com.harnessclaude.llm.threading.ThreadChecker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LlamaCppModelLoaderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun writeGgufFile(name: String = "tiny.gguf"): File {
        val root = tempFolder.root
        val file =
            File(root, "models/$name").apply {
                parentFile?.mkdirs()
                // GGUF magic: little-endian 'GGUF'
                writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x00, 0x00, 0x00, 0x03))
            }
        return file
    }

    @Test
    fun `successful load returns handle with native pointer`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val file = writeGgufFile()
            val jni =
                mockk<LlamaJniBridge>(relaxed = true) {
                    every { isValidGguf(file.absolutePath) } returns true
                    every { loadModel(file.absolutePath, useMmap = true) } returns 12345L
                }
            val loader =
                LlamaCppModelLoader(
                    ioDispatcher = dispatcher,
                    pathValidator = PathValidator(tempFolder.root),
                    jni = jni,
                    threadChecker = ThreadChecker.NotOnMainAlways,
                )

            val result = loader.load(file.absolutePath)

            assertTrue(result.isSuccess)
            assertEquals(12345L, result.getOrNull()!!.nativePointer())
            verify { jni.loadModel(file.absolutePath, useMmap = true) }
        }

    @Test
    fun `load on main thread fails fast without touching native`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val file = writeGgufFile()
            val jni = mockk<LlamaJniBridge>(relaxed = true)
            val loader =
                LlamaCppModelLoader(
                    ioDispatcher = dispatcher,
                    pathValidator = PathValidator(tempFolder.root),
                    jni = jni,
                    threadChecker = ThreadChecker.AlwaysOnMain,
                )

            val result = loader.load(file.absolutePath)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ModelLoadError.CalledOnMainThread)
            verify(exactly = 0) { jni.loadModel(any(), any()) }
        }

    @Test
    fun `missing file returns FileNotFound`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val missing = File(tempFolder.root, "models/missing.gguf").absolutePath
            val loader =
                LlamaCppModelLoader(
                    ioDispatcher = dispatcher,
                    pathValidator = PathValidator(tempFolder.root),
                    jni = mockk(relaxed = true),
                    threadChecker = ThreadChecker.NotOnMainAlways,
                )

            val result = loader.load(missing)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ModelLoadError.FileNotFound)
        }

    @Test
    fun `non-GGUF file returns InvalidFormat`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val file =
                File(tempFolder.root, "models/fake.bin").apply {
                    parentFile?.mkdirs()
                    writeBytes("not a gguf".toByteArray())
                }
            val jni =
                mockk<LlamaJniBridge>(relaxed = true) {
                    every { isValidGguf(file.absolutePath) } returns false
                }
            val loader =
                LlamaCppModelLoader(
                    ioDispatcher = dispatcher,
                    pathValidator = PathValidator(tempFolder.root),
                    jni = jni,
                    threadChecker = ThreadChecker.NotOnMainAlways,
                )

            val result = loader.load(file.absolutePath)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ModelLoadError.InvalidFormat)
            verify(exactly = 0) { jni.loadModel(any(), any()) }
        }

    @Test
    fun `unsafe path is rejected before any IO`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val jni = mockk<LlamaJniBridge>(relaxed = true)
            val loader =
                LlamaCppModelLoader(
                    ioDispatcher = dispatcher,
                    pathValidator = PathValidator(tempFolder.root),
                    jni = jni,
                    threadChecker = ThreadChecker.NotOnMainAlways,
                )

            val result = loader.load("/sdcard/Download/evil.gguf")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ModelLoadError.UnsafePath)
            verify(exactly = 0) { jni.loadModel(any(), any()) }
        }

    @Test
    fun `native returning 0 surfaces as NativeFailure and does not leak handle`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val file = writeGgufFile()
            val jni =
                mockk<LlamaJniBridge>(relaxed = true) {
                    every { isValidGguf(file.absolutePath) } returns true
                    every { loadModel(file.absolutePath, useMmap = true) } returns 0L
                }
            val loader =
                LlamaCppModelLoader(
                    ioDispatcher = dispatcher,
                    pathValidator = PathValidator(tempFolder.root),
                    jni = jni,
                    threadChecker = ThreadChecker.NotOnMainAlways,
                )

            val result = loader.load(file.absolutePath)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is ModelLoadError.NativeFailure)
            // 0 ptr means nothing to free
            verify(exactly = 0) { jni.freeModel(any()) }
        }
}
