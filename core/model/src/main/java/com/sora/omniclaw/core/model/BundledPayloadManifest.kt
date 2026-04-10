package com.sora.omniclaw.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BundledPayloadManifest(
    val schemaVersion: Int = 1,
    val payloads: List<BundledPayloadEntry> = emptyList(),
)

@Serializable
data class BundledPayloadEntry(
    val fileName: String,
    val sha256: String,
    val sizeBytes: Long,
)
