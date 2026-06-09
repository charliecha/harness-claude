package com.harnessclaude.llm.model

import com.harnessclaude.llm.nativebridge.LlamaJniBridge
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelHandleTest {
    @Test
    fun `nativePointer returns underlying ptr before release`() {
        val jni = mockk<LlamaJniBridge>(relaxed = true)
        val handle = ModelHandle.create(nativePtr = 42L, sourcePath = "/x.gguf", jni = jni)
        assertEquals(42L, handle.nativePointer())
        assertFalse(handle.isReleased)
    }

    @Test
    fun `release frees native ptr exactly once`() {
        val jni = mockk<LlamaJniBridge>(relaxed = true)
        val handle = ModelHandle.create(nativePtr = 100L, sourcePath = "/x.gguf", jni = jni)
        handle.release()
        handle.release()
        handle.release()
        verify(exactly = 1) { jni.freeModel(100L) }
        assertTrue(handle.isReleased)
    }

    @Test
    fun `close delegates to release`() {
        val jni = mockk<LlamaJniBridge>(relaxed = true)
        val handle = ModelHandle.create(nativePtr = 7L, sourcePath = "/x.gguf", jni = jni)
        handle.close()
        verify(exactly = 1) { jni.freeModel(7L) }
        assertTrue(handle.isReleased)
    }

    @Test
    fun `nativePointer after release throws`() {
        val jni = mockk<LlamaJniBridge>(relaxed = true)
        val handle = ModelHandle.create(nativePtr = 11L, sourcePath = "/x.gguf", jni = jni)
        handle.release()
        assertThrows(IllegalStateException::class.java) {
            handle.nativePointer()
        }
    }

    @Test
    fun `use closes handle automatically`() {
        val jni = mockk<LlamaJniBridge>(relaxed = true)
        val handle = ModelHandle.create(nativePtr = 99L, sourcePath = "/x.gguf", jni = jni)
        handle.use {
            assertEquals(99L, it.nativePointer())
        }
        verify(exactly = 1) { jni.freeModel(99L) }
    }

    @Test
    fun `concurrent releases do not double-free`() {
        val jni = mockk<LlamaJniBridge>(relaxed = true)
        val handle = ModelHandle.create(nativePtr = 555L, sourcePath = "/x.gguf", jni = jni)
        val threads =
            List(20) {
                Thread { handle.release() }
            }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        verify(exactly = 1) { jni.freeModel(555L) }
    }
}
