package com.sora.omniclaw.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

@Serializable
data class ProviderRuntimeExport(
    @EncodeDefault
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val providerId: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
) {
    val isReady: Boolean
        get() = schemaVersion > 0 &&
            providerId.isNotBlank() &&
            baseUrl.isNotBlank() &&
            modelName.isNotBlank()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
