package com.harnessclaude.llm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.harnessclaude.llm.ui.InferenceScreen
import com.harnessclaude.llm.ui.InferenceScreenActions
import com.harnessclaude.llm.viewmodel.InferenceViewModel

class MainActivity : ComponentActivity() {
    private val vm: InferenceViewModel by viewModels { InferenceViewModel.Factory(filesDir) }

    private val pickModel =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.loadModelFromUri(applicationContext, it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState by vm.uiState.collectAsState()
                    val screenActions =
                        InferenceScreenActions(
                            onGenerate = { prompt -> vm.generate(prompt) },
                            onStop = vm::stopGeneration,
                            onClear = vm::clearOutput,
                            onLoadModelClick = {
                                pickModel.launch(arrayOf("*/*"))
                            },
                            onUnloadModel = vm::unloadModel,
                        )
                    InferenceScreen(
                        uiState = uiState,
                        actions = screenActions,
                    )
                }
            }
        }
    }
}
