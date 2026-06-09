package com.harnessclaude.llm.ui

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
    onGenerate: (String) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
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

        Spacer(modifier = Modifier.height(8.dp))

        uiState.error?.let { errMsg ->
            Text(
                text = errMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
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

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = { Text("Prompt") },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.modelLoaded && !uiState.isLoading,
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions =
                KeyboardActions(
                    onSend = {
                        if (promptText.isNotBlank() && !uiState.isGenerating) {
                            onGenerate(promptText.trim())
                        }
                    },
                ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (uiState.isGenerating) {
                        onStop()
                    } else {
                        if (promptText.isNotBlank()) {
                            onGenerate(promptText.trim())
                        }
                    }
                },
                enabled = uiState.modelLoaded && !uiState.isLoading,
                modifier = Modifier.weight(1f),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                } else {
                    Text(if (uiState.isGenerating) "Stop" else "Generate")
                }
            }

            TextButton(
                onClick = onClear,
                enabled = uiState.output.isNotEmpty() || uiState.error != null,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Clear")
            }
        }
    }
}
