package com.sora.omniclaw.bridge.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeCommandJsonTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `encodes bridge command using a flat json friendly shape`() {
        val command = BridgeCommand(
            requestId = "req-1",
            capability = "clipboard",
            action = "read",
            payload = buildJsonObject {
                put("scope", "primary")
            },
        )

        val encoded = json.encodeToString(BridgeCommand.serializer(), command)
        val parsed = json.parseToJsonElement(encoded).jsonObject

        assertEquals("req-1", parsed.getValue("requestId").jsonPrimitive.content)
        assertEquals("clipboard", parsed.getValue("capability").jsonPrimitive.content)
        assertEquals("read", parsed.getValue("action").jsonPrimitive.content)
        assertEquals("primary", parsed.getValue("payload").jsonObject.getValue("scope").jsonPrimitive.content)
    }
}
