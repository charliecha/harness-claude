package com.harnessclaude.llm.inference

data class InferenceParams(
    val maxNewTokens: Int = 128,
    val temperature: Float = 0.8f,
    val topP: Float = 0.9f,
    val seed: Int = -1,
    val nCtx: Int = 512,
    val nBatch: Int = 512,
)
