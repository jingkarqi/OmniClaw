package com.sora.omniclaw.domain.provider

import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.storage.ProviderConfigStore
import com.sora.omniclaw.core.storage.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveProviderConfigUseCase(
    private val providerConfigStore: ProviderConfigStore,
    private val secretStore: SecretStore,
) {
    operator fun invoke(): Flow<ProviderConfigDraft> {
        return combine(
            providerConfigStore.observeDraft(),
            secretStore.observeApiKeyAvailability(),
        ) { draft, hasApiKey ->
            draft.copy(hasStoredApiKey = hasApiKey)
        }
    }
}
