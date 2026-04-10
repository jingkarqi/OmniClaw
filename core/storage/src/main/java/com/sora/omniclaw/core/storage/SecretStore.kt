package com.sora.omniclaw.core.storage

import com.sora.omniclaw.core.common.HostResult

interface SecretStore {
    suspend fun saveApiKey(apiKey: String): HostResult<Unit>

    suspend fun readApiKey(): String?

    suspend fun hasApiKey(): Boolean

    suspend fun clearApiKey(): HostResult<Unit>
}
