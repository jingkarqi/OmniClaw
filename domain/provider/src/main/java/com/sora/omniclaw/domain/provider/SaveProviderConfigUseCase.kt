package com.sora.omniclaw.domain.provider

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.model.ProviderRuntimeExport
import com.sora.omniclaw.core.storage.ProviderConfigStore
import com.sora.omniclaw.core.storage.ProviderExportStore
import com.sora.omniclaw.core.storage.SecretStore

class SaveProviderConfigUseCase(
    private val providerConfigStore: ProviderConfigStore,
    private val secretStore: SecretStore,
    private val providerExportStore: ProviderExportStore,
) {
    suspend operator fun invoke(draft: ProviderConfigDraft): HostResult<Unit> {
        if (!draft.hasRequiredFields) {
            return invalidProviderDraft()
        }

        val clearExportResult = providerExportStore.clearExport()
        if (clearExportResult is HostResult.Failure) {
            return clearExportResult
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

        val persistedDraft = draft.withoutSecret(hasStoredApiKey = persistedHasApiKey)
        val saveDraftResult = providerConfigStore.saveDraft(persistedDraft)
        if (saveDraftResult is HostResult.Failure) {
            return saveDraftResult
        }

        if (!persistedDraft.isConfigured(hasAvailableSecret = persistedHasApiKey)) {
            return HostResult.Success(Unit)
        }

        return providerExportStore.writeExport(
            ProviderRuntimeExport(
                providerId = persistedDraft.providerId,
                baseUrl = persistedDraft.baseUrl,
                modelName = persistedDraft.modelName,
            )
        )
    }

    private fun invalidProviderDraft(): HostResult<Unit> {
        return HostResult.Failure(
            com.sora.omniclaw.core.common.HostError(
                category = com.sora.omniclaw.core.common.HostErrorCategory.Validation,
                message = "Provider draft is incomplete.",
                recoverable = true,
            )
        )
    }
}
