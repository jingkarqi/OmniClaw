package com.sora.omniclaw.core.storage

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.ProviderConfigDraft
import kotlinx.coroutines.flow.Flow

interface ProviderConfigStore {
    fun observeDraft(): Flow<ProviderConfigDraft>

    suspend fun saveDraft(draft: ProviderConfigDraft): HostResult<Unit>
}
