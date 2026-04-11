package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.model.ProviderRuntimeExport
import kotlinx.serialization.Serializable

@Serializable
internal data class RuntimeProviderConfig(
    val gateway: RuntimeGatewayConfig = RuntimeGatewayConfig(),
    val agents: RuntimeAgentsConfig,
    val models: RuntimeModelsConfig,
) {
    val isReady: Boolean
        get() = agents.defaults.model.isNotBlank() && models.providers.isNotEmpty()

    companion object {
        fun fromExport(export: ProviderRuntimeExport, apiKey: String): RuntimeProviderConfig {
            val providerKey = normalizeProviderKey(export.providerId)
            val modelName = export.modelName.trim()
            return RuntimeProviderConfig(
                agents = RuntimeAgentsConfig(
                    defaults = RuntimeAgentDefaultsConfig(
                        model = "$providerKey/$modelName",
                    ),
                ),
                models = RuntimeModelsConfig(
                    providers = mapOf(
                        providerKey to RuntimeModelProviderConfig(
                            baseUrl = export.baseUrl.trim(),
                            apiKey = apiKey,
                            api = inferApi(export),
                            models = listOf(
                                RuntimeModelDefinition(
                                    id = modelName,
                                    name = modelName,
                                )
                            ),
                        )
                    )
                ),
            )
        }

        internal fun normalizeProviderKey(providerId: String): String {
            val normalized = providerId.trim()
                .lowercase()
                .replace(Regex("[^a-z0-9-]+"), "-")
                .replace(Regex("-{2,}"), "-")
                .trim('-')
            return normalized.ifBlank { DEFAULT_PROVIDER_KEY }
        }

        private fun inferApi(export: ProviderRuntimeExport): String {
            val providerId = export.providerId.trim().lowercase()
            val baseUrl = export.baseUrl.trim().lowercase()
            return when {
                "anthropic" in providerId || "anthropic" in baseUrl -> "anthropic-messages"
                "google" in providerId ||
                    "gemini" in providerId ||
                    "generativelanguage" in baseUrl -> "google-generative-ai"
                else -> "openai-completions"
            }
        }

        private const val DEFAULT_PROVIDER_KEY = "provider"
    }
}

@Serializable
internal data class RuntimeGatewayConfig(
    val mode: String = "local",
)

@Serializable
internal data class RuntimeAgentsConfig(
    val defaults: RuntimeAgentDefaultsConfig,
)

@Serializable
internal data class RuntimeAgentDefaultsConfig(
    val model: String,
)

@Serializable
internal data class RuntimeModelsConfig(
    val mode: String = "replace",
    val providers: Map<String, RuntimeModelProviderConfig>,
)

@Serializable
internal data class RuntimeModelProviderConfig(
    val baseUrl: String,
    val apiKey: String,
    val api: String,
    val models: List<RuntimeModelDefinition>,
)

@Serializable
internal data class RuntimeModelDefinition(
    val id: String,
    val name: String,
)
