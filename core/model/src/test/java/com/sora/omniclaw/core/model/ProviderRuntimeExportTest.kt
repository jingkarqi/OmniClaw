package com.sora.omniclaw.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRuntimeExportTest {
    private val json = Json

    @Test
    fun `is ready when schema version and all exported fields are present`() {
        val export = ProviderRuntimeExport(
            schemaVersion = ProviderRuntimeExport.CURRENT_SCHEMA_VERSION,
            providerId = "openai-compatible",
            baseUrl = "https://api.example.com",
            modelName = "gpt-test",
        )

        assertTrue(export.isReady)
    }

    @Test
    fun `is not ready when schema version or any required field is missing`() {
        val missingSchemaVersion = ProviderRuntimeExport(
            schemaVersion = 0,
            providerId = "openai-compatible",
            baseUrl = "https://api.example.com",
            modelName = "gpt-test",
        )
        val missingProvider = ProviderRuntimeExport(
            schemaVersion = ProviderRuntimeExport.CURRENT_SCHEMA_VERSION,
            providerId = "",
            baseUrl = "https://api.example.com",
            modelName = "gpt-test",
        )
        val missingBaseUrl = ProviderRuntimeExport(
            schemaVersion = ProviderRuntimeExport.CURRENT_SCHEMA_VERSION,
            providerId = "openai-compatible",
            baseUrl = "",
            modelName = "gpt-test",
        )
        val missingModel = ProviderRuntimeExport(
            schemaVersion = ProviderRuntimeExport.CURRENT_SCHEMA_VERSION,
            providerId = "openai-compatible",
            baseUrl = "https://api.example.com",
            modelName = "",
        )

        assertFalse(missingSchemaVersion.isReady)
        assertFalse(missingProvider.isReady)
        assertFalse(missingBaseUrl.isReady)
        assertFalse(missingModel.isReady)
    }

    @Test
    fun `serializes supported export fields and recomputes readiness on decode`() {
        val export = ProviderRuntimeExport(
            schemaVersion = ProviderRuntimeExport.CURRENT_SCHEMA_VERSION,
            providerId = "openai-compatible",
            baseUrl = "https://api.example.com",
            modelName = "gpt-test",
        )

        val encoded = json.encodeToString(ProviderRuntimeExport.serializer(), export)
        val payload = json.decodeFromString(JsonObject.serializer(), encoded).jsonObject
        val decoded = json.decodeFromString(ProviderRuntimeExport.serializer(), encoded)

        assertEquals(
            setOf("schemaVersion", "providerId", "baseUrl", "modelName"),
            payload.keys,
        )
        assertEquals(
            ProviderRuntimeExport.CURRENT_SCHEMA_VERSION,
            payload["schemaVersion"]?.jsonPrimitive?.int,
        )
        assertEquals("openai-compatible", payload["providerId"]?.jsonPrimitive?.content)
        assertEquals("https://api.example.com", payload["baseUrl"]?.jsonPrimitive?.content)
        assertEquals("gpt-test", payload["modelName"]?.jsonPrimitive?.content)
        assertNull(payload["apiKey"])
        assertNull(payload["isReady"])
        assertEquals(export, decoded)
        assertTrue(decoded.isReady)
    }
}
