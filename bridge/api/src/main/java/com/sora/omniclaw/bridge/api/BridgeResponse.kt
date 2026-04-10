package com.sora.omniclaw.bridge.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BridgeResponse(
    val requestId: String,
    val status: BridgeResponseStatus,
    val payload: JsonObject? = null,
    val error: BridgeResponseError? = null,
)

@Serializable
enum class BridgeResponseStatus {
    Success,
    Failure,
}

@Serializable
data class BridgeResponseError(
    val category: String,
    val message: String,
    val recoverable: Boolean = false,
)
