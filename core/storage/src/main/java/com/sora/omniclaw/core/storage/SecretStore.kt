package com.sora.omniclaw.core.storage

import com.sora.omniclaw.core.common.HostResult
import kotlinx.coroutines.flow.Flow

interface SecretStore {
    suspend fun saveApiKey(apiKey: String): HostResult<Unit>

    suspend fun readApiKey(): String?

    suspend fun hasApiKey(): Boolean

    fun observeApiKeyAvailability(): Flow<Boolean>

    suspend fun clearApiKey(): HostResult<Unit>
}
