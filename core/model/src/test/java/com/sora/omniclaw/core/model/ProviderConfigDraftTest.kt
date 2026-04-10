package com.sora.omniclaw.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigDraftTest {
    @Test
    fun `is ready when required fields and inline api key are present`() {
        val draft = ProviderConfigDraft(
            providerId = "openai-compatible",
            baseUrl = "https://api.example.com",
            modelName = "gpt-test",
            apiKey = "secret",
            hasStoredApiKey = false,
        )

        assertTrue(draft.isReady)
    }

    @Test
    fun `is ready when required fields exist and api key was already stored`() {
        val draft = ProviderConfigDraft(
            providerId = "openai-compatible",
            baseUrl = "https://api.example.com",
            modelName = "gpt-test",
            apiKey = "",
            hasStoredApiKey = true,
        )

        assertTrue(draft.isReady)
    }

    @Test
    fun `is not ready when any required field is missing`() {
        val missingProvider = ProviderConfigDraft(
            providerId = "",
            baseUrl = "https://api.example.com",
            modelName = "gpt-test",
            apiKey = "secret",
            hasStoredApiKey = false,
        )
        val missingBaseUrl = ProviderConfigDraft(
            providerId = "openai-compatible",
            baseUrl = "",
            modelName = "gpt-test",
            apiKey = "secret",
            hasStoredApiKey = false,
        )
        val missingModel = ProviderConfigDraft(
            providerId = "openai-compatible",
            baseUrl = "https://api.example.com",
            modelName = "",
            apiKey = "secret",
            hasStoredApiKey = false,
        )
        val missingApiKey = ProviderConfigDraft(
            providerId = "openai-compatible",
            baseUrl = "https://api.example.com",
            modelName = "gpt-test",
            apiKey = "",
            hasStoredApiKey = false,
        )

        assertFalse(missingProvider.isReady)
        assertFalse(missingBaseUrl.isReady)
        assertFalse(missingModel.isReady)
        assertFalse(missingApiKey.isReady)
    }
}
