package com.harnessclaude.llm.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.harnessclaude.llm.viewmodel.InferenceUiState

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
fun InferenceScreen(
    uiState: InferenceUiState,
    actions: InferenceScreenActions,
    modifier: Modifier = Modifier,
) {
    var promptText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            text = "LLM Inference",
            style = MaterialTheme.typography.headlineSmall,
        )

        if (!uiState.modelLoaded && !uiState.isCopying && !uiState.isLoading) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = actions.onLoadModelClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Load Model")
            }
        }

        if (uiState.isCopying || uiState.isLoading) {
            Spacer(modifier = Modifier.height(8.dp))
            val statusText = if (uiState.isCopying) "Copying model…" else "Loading model…"
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                Text(text = statusText, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        uiState.error?.let { errMsg ->
            Text(
                text = errMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        OutputBox(uiState = uiState, scrollState = scrollState, modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.modelLoaded && !uiState.isLoading && !uiState.isCopying,
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions =
                KeyboardActions(
                    onSend = {
                        if (promptText.isNotBlank() && !uiState.isGenerating) {
                            actions.onGenerate(promptText.trim())
                        }
                    },
                ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        ActionRow(
            uiState = uiState,
            promptText = promptText,
            actions = actions,
        )
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun OutputBox(
    uiState: InferenceUiState,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
    ) {
        if (uiState.output.isEmpty() && !uiState.isGenerating) {
            Text(
                text = "Output will appear here…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        } else {
            Text(
                text = uiState.output,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(scrollState),
            )
        }
        if (uiState.isGenerating) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Suppress("FunctionNaming", "ktlint:standard:function-naming")
@Composable
private fun ActionRow(
    uiState: InferenceUiState,
    promptText: String,
    actions: InferenceScreenActions,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                if (uiState.isGenerating) {
                    actions.onStop()
                } else if (promptText.isNotBlank()) {
                    actions.onGenerate(promptText.trim())
                }
            },
            enabled = uiState.modelLoaded && !uiState.isLoading && !uiState.isCopying,
            modifier = Modifier.weight(1f),
        ) {
            Text(if (uiState.isGenerating) "Stop" else "Generate")
        }

        TextButton(
            onClick = actions.onClear,
            enabled = uiState.output.isNotEmpty() || uiState.error != null,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text("Clear")
        }

        if (uiState.modelLoaded) {
            TextButton(
                onClick = actions.onUnloadModel,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Unload")
            }
        }
    }
}
