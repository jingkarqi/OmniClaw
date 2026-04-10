package com.sora.omniclaw.domain.provider

import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.storage.ProviderConfigStore
import kotlinx.coroutines.flow.Flow

class ObserveProviderConfigUseCase(
    private val providerConfigStore: ProviderConfigStore,
) {
    operator fun invoke(): Flow<ProviderConfigDraft> = providerConfigStore.observeDraft()
}
