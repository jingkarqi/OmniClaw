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
    val hasRequiredFields: Boolean
        get() = providerId.isNotBlank() &&
            baseUrl.isNotBlank() &&
            modelName.isNotBlank()

    val isReady: Boolean
        get() = isConfigured(hasStoredApiKey)

    fun isConfigured(hasAvailableSecret: Boolean): Boolean = hasRequiredFields &&
        (apiKey.isNotBlank() || hasAvailableSecret)

    fun withoutSecret(hasStoredApiKey: Boolean = this.hasStoredApiKey || apiKey.isNotBlank()): ProviderConfigDraft = copy(
        apiKey = "",
        hasStoredApiKey = hasStoredApiKey,
    )
}
