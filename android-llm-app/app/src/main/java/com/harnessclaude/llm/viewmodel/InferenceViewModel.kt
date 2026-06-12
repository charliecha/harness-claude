package com.harnessclaude.llm.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harnessclaude.llm.inference.InferenceError
import com.harnessclaude.llm.inference.InferenceParams
import com.harnessclaude.llm.inference.InferenceSession
import com.harnessclaude.llm.inference.InferenceSessionFactory
import com.harnessclaude.llm.model.LlamaCppModelLoader
import com.harnessclaude.llm.model.ModelHandle
import com.harnessclaude.llm.model.ModelLoader
import com.harnessclaude.llm.nativebridge.LlamaJni
import com.harnessclaude.llm.storage.ModelStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

data class InferenceUiState(
    val output: String = "",
    val isLoading: Boolean = false,
    val isCopying: Boolean = false,
    val isGenerating: Boolean = false,
    val error: String? = null,
    val modelLoaded: Boolean = false,
)

class InferenceViewModel(
    private val modelLoader: ModelLoader,
    private val sessionFactory: InferenceSessionFactory = InferenceSessionFactory(),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InferenceUiState())
    val uiState: StateFlow<InferenceUiState> = _uiState.asStateFlow()

    private var modelHandle: ModelHandle? = null
    private var session: InferenceSession? = null
    private var generateJob: Job? = null

    fun loadModel(path: String) {
        if (_uiState.value.isLoading || _uiState.value.modelLoaded) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            modelLoader.load(path).fold(
                onSuccess = { handle ->
                    modelHandle = handle
                    _uiState.update { it.copy(isLoading = false, modelLoaded = true) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                    Timber.e(e, "loadModel failed")
                },
            )
        }
    }

    fun loadModelFromUri(
        context: Context,
        uri: Uri,
    ) {
        if (_uiState.value.isCopying || _uiState.value.isLoading || _uiState.value.modelLoaded) return
        _uiState.update { it.copy(isCopying = true, error = null) }
        viewModelScope.launch {
            ModelStorage.copyModelFromUri(context, uri).fold(
                onSuccess = { path ->
                    _uiState.update { it.copy(isCopying = false, isLoading = true) }
                    withContext(ioDispatcher) { modelLoader.load(path) }.fold(
                        onSuccess = { handle ->
                            modelHandle = handle
                            _uiState.update { it.copy(isLoading = false, modelLoaded = true) }
                        },
                        onFailure = { e ->
                            _uiState.update { it.copy(isLoading = false, error = e.message) }
                            Timber.e(e, "loadModelFromUri load failed")
                        },
                    )
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isCopying = false, error = e.message) }
                    Timber.e(e, "loadModelFromUri copy failed")
                },
            )
        }
    }

    fun generate(
        prompt: String,
        params: InferenceParams = InferenceParams(),
    ) {
        val handle =
            modelHandle ?: run {
                _uiState.update { it.copy(error = "No model loaded") }
                return
            }
        if (_uiState.value.isGenerating) return

        generateJob?.cancel()
        _uiState.update { it.copy(output = "", isGenerating = true, error = null) }

        generateJob =
            viewModelScope.launch {
                runCatching {
                    val sess = sessionFactory.create(handle, params)
                    session?.close()
                    session = sess
                    sess
                }.fold(
                    onSuccess = { sess ->
                        sess.generate(prompt, params)
                            .catch { e ->
                                when (e) {
                                    is InferenceError ->
                                        _uiState.update {
                                            it.copy(error = e.message, isGenerating = false)
                                        }
                                    else -> {
                                        Timber.e(e, "generate stream error")
                                        _uiState.update {
                                            it.copy(error = e.message, isGenerating = false)
                                        }
                                    }
                                }
                            }
                            .collect { piece ->
                                _uiState.update { it.copy(output = it.output + piece) }
                            }
                        _uiState.update { it.copy(isGenerating = false) }
                    },
                    onFailure = { e ->
                        Timber.e(e, "session creation failed")
                        _uiState.update { it.copy(error = e.message, isGenerating = false) }
                    },
                )
            }
    }

    fun stopGeneration() {
        generateJob?.cancel()
        generateJob = null
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun clearOutput() {
        _uiState.update { it.copy(output = "", error = null) }
    }

    fun unloadModel() {
        if (_uiState.value.isGenerating) stopGeneration()
        session?.close()
        session = null
        modelHandle?.release()
        modelHandle = null
        _uiState.update { InferenceUiState() }
    }

    override fun onCleared() {
        super.onCleared()
        session?.close()
        modelHandle?.release()
    }

    class Factory(private val filesDir: File) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val loader =
                LlamaCppModelLoader(
                    ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
                    pathValidator = com.harnessclaude.llm.storage.PathValidator(filesDir),
                    jni = LlamaJni,
                    threadChecker = com.harnessclaude.llm.threading.ThreadChecker.Real,
                    stubMode = false,
                )
            val sessionFactory =
                InferenceSessionFactory(
                    jni = LlamaJni,
                    threadChecker = com.harnessclaude.llm.threading.ThreadChecker.Real,
                )
            return InferenceViewModel(loader, sessionFactory) as T
        }
    }
}
