package com.harnessclaude.llm.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ModelStorageTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun mockContext(filesDir: File): Context {
        val resolver = mockk<ContentResolver>()
        return mockk<Context> {
            every { this@mockk.filesDir } returns filesDir
            every { contentResolver } returns resolver
        }
    }

    @Test
    fun `copy from uri succeeds and returns absolute path in models dir`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val ctx = mockContext(tempFolder.root)
            val uri =
                mockk<Uri> {
                    every { lastPathSegment } returns "tiny.gguf"
                }
            val payload = ByteArray(1024) { it.toByte() }
            every { ctx.contentResolver.openInputStream(uri) } returns ByteArrayInputStream(payload)

            val result = ModelStorage.copyModelFromUri(ctx, uri, dispatcher)

            assertTrue(result.isSuccess)
            val targetPath = result.getOrNull()!!
            assertTrue(targetPath.endsWith("/models/tiny.gguf"))
            val target = File(targetPath)
            assertTrue(target.exists())
            assertEquals(payload.size.toLong(), target.length())
        }

    @Test
    fun `copy failure removes partial file`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val ctx = mockContext(tempFolder.root)
            val uri =
                mockk<Uri> {
                    every { lastPathSegment } returns "broken.gguf"
                }
            val brokenStream =
                object : java.io.InputStream() {
                    private var calls = 0

                    override fun read(): Int = throw IOException("boom")

                    override fun read(
                        b: ByteArray,
                        off: Int,
                        len: Int,
                    ): Int {
                        calls++
                        if (calls > 1) throw IOException("boom")
                        System.arraycopy(ByteArray(len), 0, b, off, len)
                        return len
                    }
                }
            every { ctx.contentResolver.openInputStream(uri) } returns brokenStream

            val result = ModelStorage.copyModelFromUri(ctx, uri, dispatcher)

            assertTrue(result.isFailure)
            val expected = File(tempFolder.root, "models/broken.gguf")
            assertFalse("partial file should be deleted", expected.exists())
        }

    @Test
    fun `null openInputStream surfaces failure without crashing`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val ctx = mockContext(tempFolder.root)
            val uri =
                mockk<Uri> {
                    every { lastPathSegment } returns "missing.gguf"
                }
            every { ctx.contentResolver.openInputStream(uri) } returns null

            val result = ModelStorage.copyModelFromUri(ctx, uri, dispatcher)

            assertTrue(result.isFailure)
        }

    @Test
    fun `uri without filename uses fallback name`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val ctx = mockContext(tempFolder.root)
            val uri =
                mockk<Uri> {
                    every { lastPathSegment } returns null
                }
            every { ctx.contentResolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(16))

            val result = ModelStorage.copyModelFromUri(ctx, uri, dispatcher)

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!.endsWith("/models/model.gguf"))
        }
}
