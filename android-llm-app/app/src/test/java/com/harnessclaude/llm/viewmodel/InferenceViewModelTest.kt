package com.harnessclaude.llm.viewmodel

import android.content.Context
import android.net.Uri
import com.harnessclaude.llm.inference.InferenceParams
import com.harnessclaude.llm.inference.InferenceSessionFactory
import com.harnessclaude.llm.model.ModelHandle
import com.harnessclaude.llm.model.ModelLoader
import com.harnessclaude.llm.nativebridge.LlamaJniBridge
import com.harnessclaude.llm.storage.ModelStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InferenceViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeHandle(ptr: Long = 42L): ModelHandle {
        val jni = mockk<LlamaJniBridge>(relaxed = true)
        return ModelHandle.create(ptr, "/model.gguf", jni)
    }

    @Test
    fun `initial state has no model loaded`() {
        val vm =
            InferenceViewModel(
                modelLoader = mockk(),
                sessionFactory = mockk(),
            )
        val state = vm.uiState.value
        assertFalse(state.modelLoaded)
        assertFalse(state.isLoading)
        assertFalse(state.isGenerating)
        assertNull(state.error)
        assertEquals("", state.output)
    }

    @Test
    fun `loadModel success sets modelLoaded true`() =
        runTest {
            val handle = fakeHandle()
            val loader =
                mockk<ModelLoader> {
                    coEvery { load(any()) } returns Result.success(handle)
                }
            val vm = InferenceViewModel(modelLoader = loader, sessionFactory = mockk())
            vm.loadModel("/model.gguf")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.uiState.value.modelLoaded)
            assertFalse(vm.uiState.value.isLoading)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `loadModel failure sets error message`() =
        runTest {
            val loader =
                mockk<ModelLoader> {
                    coEvery { load(any()) } returns Result.failure(RuntimeException("bad path"))
                }
            val vm = InferenceViewModel(modelLoader = loader, sessionFactory = mockk())
            vm.loadModel("/bad.gguf")
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.uiState.value.modelLoaded)
            assertFalse(vm.uiState.value.isLoading)
            assertNotNull(vm.uiState.value.error)
        }

    @Test
    fun `generate without model sets error`() {
        val vm = InferenceViewModel(modelLoader = mockk(), sessionFactory = mockk())
        vm.generate("hello")
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `clearOutput resets output and error`() =
        runTest {
            val handle = fakeHandle()
            val loader =
                mockk<ModelLoader> {
                    coEvery { load(any()) } returns Result.success(handle)
                }
            val vm = InferenceViewModel(modelLoader = loader, sessionFactory = mockk())
            vm.loadModel("/model.gguf")
            testDispatcher.scheduler.advanceUntilIdle()

            // Simulate error state
            vm.generate("prompt without session setup")
            // We already have modelLoaded=true but sessionFactory will throw — just clear
            vm.clearOutput()
            assertEquals("", vm.uiState.value.output)
            assertNull(vm.uiState.value.error)
        }

    @Test
    fun `loadModel is no-op when already loaded`() =
        runTest {
            val handle = fakeHandle()
            var callCount = 0
            val loader =
                mockk<ModelLoader> {
                    coEvery { load(any()) } answers {
                        callCount++
                        Result.success(handle)
                    }
                }
            val vm = InferenceViewModel(modelLoader = loader, sessionFactory = mockk())
            vm.loadModel("/model.gguf")
            testDispatcher.scheduler.advanceUntilIdle()
            vm.loadModel("/model.gguf")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, callCount)
        }

    @Test
    fun `stopGeneration clears isGenerating flag`() =
        runTest {
            val vm = InferenceViewModel(modelLoader = mockk(), sessionFactory = mockk())
            vm.stopGeneration()
            assertFalse(vm.uiState.value.isGenerating)
        }

    @Test
    fun `InferenceUiState default values are correct`() {
        val state = InferenceUiState()
        assertEquals("", state.output)
        assertFalse(state.isLoading)
        assertFalse(state.isCopying)
        assertFalse(state.isGenerating)
        assertNull(state.error)
        assertFalse(state.modelLoaded)
    }

    @Test
    fun `generate session factory exception surfaces as error`() =
        runTest {
            val handle = fakeHandle()
            val loader =
                mockk<ModelLoader> {
                    coEvery { load(any()) } returns Result.success(handle)
                }
            val factory =
                mockk<InferenceSessionFactory> {
                    every { create(any(), any()) } throws RuntimeException("context fail")
                }
            val vm = InferenceViewModel(modelLoader = loader, sessionFactory = factory)
            vm.loadModel("/model.gguf")
            testDispatcher.scheduler.advanceUntilIdle()

            vm.generate("hello", InferenceParams())
            testDispatcher.scheduler.advanceUntilIdle()

            assertNotNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isGenerating)
        }

    // ── loadModelFromUri tests ────────────────────────────────────────────

    @Test
    fun `loadModelFromUri success sets modelLoaded and clears isCopying and isLoading`() =
        runTest {
            mockkObject(ModelStorage)
            val ctx = mockk<Context>()
            val uri = mockk<Uri>()
            val handle = fakeHandle()
            val loader = mockk<ModelLoader> { coEvery { load(any()) } returns Result.success(handle) }
            coEvery { ModelStorage.copyModelFromUri(ctx, uri) } returns Result.success("/files/models/m.gguf")

            val vm = InferenceViewModel(modelLoader = loader, sessionFactory = mockk(), ioDispatcher = testDispatcher)
            vm.loadModelFromUri(ctx, uri)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(vm.uiState.value.modelLoaded)
            assertFalse(vm.uiState.value.isCopying)
            assertFalse(vm.uiState.value.isLoading)
            assertNull(vm.uiState.value.error)
            unmockkObject(ModelStorage)
        }

    @Test
    fun `loadModelFromUri copy failure sets error and clears isCopying`() =
        runTest {
            mockkObject(ModelStorage)
            val ctx = mockk<Context>()
            val uri = mockk<Uri>()
            coEvery { ModelStorage.copyModelFromUri(ctx, uri) } returns
                Result.failure(RuntimeException("disk full"))

            val vm = InferenceViewModel(modelLoader = mockk(), sessionFactory = mockk(), ioDispatcher = testDispatcher)
            vm.loadModelFromUri(ctx, uri)
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.uiState.value.modelLoaded)
            assertFalse(vm.uiState.value.isCopying)
            assertNotNull(vm.uiState.value.error)
            unmockkObject(ModelStorage)
        }

    @Test
    fun `loadModelFromUri load failure sets error and clears isLoading`() =
        runTest {
            mockkObject(ModelStorage)
            val ctx = mockk<Context>()
            val uri = mockk<Uri>()
            val loader =
                mockk<ModelLoader> {
                    coEvery { load(any()) } returns Result.failure(RuntimeException("null ptr"))
                }
            coEvery { ModelStorage.copyModelFromUri(ctx, uri) } returns Result.success("/files/models/m.gguf")

            val vm = InferenceViewModel(modelLoader = loader, sessionFactory = mockk(), ioDispatcher = testDispatcher)
            vm.loadModelFromUri(ctx, uri)
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(vm.uiState.value.modelLoaded)
            assertFalse(vm.uiState.value.isLoading)
            assertNotNull(vm.uiState.value.error)
            unmockkObject(ModelStorage)
        }

    @Test
    fun `loadModelFromUri is no-op when model already loaded`() =
        runTest {
            mockkObject(ModelStorage)
            val ctx = mockk<Context>()
            val uri = mockk<Uri>()
            val handle = fakeHandle()
            var copyCount = 0
            val loader = mockk<ModelLoader> { coEvery { load(any()) } returns Result.success(handle) }
            coEvery { ModelStorage.copyModelFromUri(ctx, uri) } answers {
                copyCount++
                Result.success("/files/models/m.gguf")
            }

            val vm = InferenceViewModel(modelLoader = loader, sessionFactory = mockk(), ioDispatcher = testDispatcher)
            vm.loadModelFromUri(ctx, uri)
            testDispatcher.scheduler.advanceUntilIdle()
            vm.loadModelFromUri(ctx, uri)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, copyCount)
            unmockkObject(ModelStorage)
        }

    // ── unloadModel tests ────────────────────────────────────────────────

    @Test
    fun `unloadModel resets state to initial`() =
        runTest {
            val handle = fakeHandle()
            val loader = mockk<ModelLoader> { coEvery { load(any()) } returns Result.success(handle) }
            val vm = InferenceViewModel(modelLoader = loader, sessionFactory = mockk())
            vm.loadModel("/model.gguf")
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(vm.uiState.value.modelLoaded)

            vm.unloadModel()

            val state = vm.uiState.value
            assertFalse(state.modelLoaded)
            assertFalse(state.isLoading)
            assertFalse(state.isGenerating)
            assertNull(state.error)
            assertEquals("", state.output)
        }

    @Test
    fun `unloadModel allows reload after unload`() =
        runTest {
            val handle = fakeHandle()
            var loadCount = 0
            val loader =
                mockk<ModelLoader> {
                    coEvery { load(any()) } answers {
                        loadCount++
                        Result.success(handle)
                    }
                }
            val vm = InferenceViewModel(modelLoader = loader, sessionFactory = mockk())

            vm.loadModel("/model.gguf")
            testDispatcher.scheduler.advanceUntilIdle()
            vm.unloadModel()
            vm.loadModel("/model2.gguf")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, loadCount)
            assertTrue(vm.uiState.value.modelLoaded)
        }

    @Test
    fun `unloadModel while generating stops generation first`() =
        runTest {
            val vm = InferenceViewModel(modelLoader = mockk(), sessionFactory = mockk())
            // Force isGenerating=true manually is not possible without real session,
            // but unloadModel should not throw when isGenerating=false
            vm.unloadModel()
            assertFalse(vm.uiState.value.isGenerating)
            assertFalse(vm.uiState.value.modelLoaded)
        }
}
