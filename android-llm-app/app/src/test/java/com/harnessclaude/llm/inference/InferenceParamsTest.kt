package com.harnessclaude.llm.inference

import org.junit.Assert.assertEquals
import org.junit.Test

class InferenceParamsTest {
    @Test
    fun `default params have expected values`() {
        val params = InferenceParams()
        assertEquals(128, params.maxNewTokens)
        assertEquals(0.8f, params.temperature, 1e-6f)
        assertEquals(0.9f, params.topP, 1e-6f)
        assertEquals(-1, params.seed)
        assertEquals(512, params.nCtx)
        assertEquals(512, params.nBatch)
    }

    @Test
    fun `copy changes only the specified field`() {
        val base = InferenceParams()
        val modified = base.copy(maxNewTokens = 64, temperature = 0.5f)
        assertEquals(64, modified.maxNewTokens)
        assertEquals(0.5f, modified.temperature, 1e-6f)
        assertEquals(base.topP, modified.topP, 1e-6f)
        assertEquals(base.seed, modified.seed)
        assertEquals(base.nCtx, modified.nCtx)
    }

    @Test
    fun `two params with same values are equal`() {
        val a = InferenceParams(maxNewTokens = 32, seed = 42)
        val b = InferenceParams(maxNewTokens = 32, seed = 42)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
