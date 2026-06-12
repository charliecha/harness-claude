package com.harnessclaude.llm.ui

data class InferenceScreenActions(
    val onGenerate: (String) -> Unit,
    val onStop: () -> Unit,
    val onClear: () -> Unit,
    val onLoadModel: (String) -> Unit = {},
)
