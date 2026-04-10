package com.sora.omniclaw.bridge.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class BridgeCommand(
    val requestId: String,
    val capability: String,
    val action: String,
    val payload: JsonObject = buildJsonObject { },
)
