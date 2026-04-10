package com.sora.omniclaw.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ProviderConfigDraft(
    val providerId: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    @Transient val apiKey: String = "",
    val hasStoredApiKey: Boolean = false,
) {
    val isReady: Boolean
        get() = providerId.isNotBlank() &&
            baseUrl.isNotBlank() &&
            modelName.isNotBlank() &&
            (apiKey.isNotBlank() || hasStoredApiKey)

    fun withoutSecret(): ProviderConfigDraft = copy(
        apiKey = "",
        hasStoredApiKey = hasStoredApiKey || apiKey.isNotBlank(),
    )
}
