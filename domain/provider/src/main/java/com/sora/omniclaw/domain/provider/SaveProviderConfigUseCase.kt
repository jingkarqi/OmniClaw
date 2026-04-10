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
        if (draft.providerId.isBlank() || draft.baseUrl.isBlank() || draft.modelName.isBlank()) {
            return HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Validation,
                    message = "Provider draft is incomplete.",
                    recoverable = true,
                )
            )
        }

        val secretResult = when {
            draft.apiKey.isNotBlank() -> secretStore.saveApiKey(draft.apiKey)
            draft.hasStoredApiKey -> HostResult.Success(Unit)
            else -> secretStore.clearApiKey()
        }
        if (secretResult is HostResult.Failure) {
            return secretResult
        }

        return providerConfigStore.saveDraft(draft.withoutSecret())
    }
}
