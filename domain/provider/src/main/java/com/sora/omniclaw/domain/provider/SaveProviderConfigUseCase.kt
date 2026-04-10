package com.sora.omniclaw.domain.provider

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.storage.ProviderConfigStore
import com.sora.omniclaw.core.storage.SecretStore

class SaveProviderConfigUseCase(
    private val providerConfigStore: ProviderConfigStore,
    private val secretStore: SecretStore,
) {
    suspend operator fun invoke(draft: ProviderConfigDraft): HostResult<Unit> {
        if (!draft.hasRequiredFields) {
            return HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Validation,
                    message = "Provider draft is incomplete.",
                    recoverable = true,
                )
            )
        }

        val persistedHasApiKey = when {
            draft.apiKey.isNotBlank() -> {
                val secretResult = secretStore.saveApiKey(draft.apiKey)
                if (secretResult is HostResult.Failure) {
                    return secretResult
                }
                true
            }

            draft.hasStoredApiKey -> secretStore.hasApiKey()

            else -> {
                val secretResult = secretStore.clearApiKey()
                if (secretResult is HostResult.Failure) {
                    return secretResult
                }
                false
            }
        }

        return providerConfigStore.saveDraft(
            draft.withoutSecret(hasStoredApiKey = persistedHasApiKey)
        )
    }
}
