package com.sora.omniclaw.domain.provider

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.storage.ProviderConfigStore
import com.sora.omniclaw.core.storage.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveProviderConfigUseCaseTest {
    @Test
    fun `emits stored secret availability from secret store instead of persisted metadata`() = runBlocking {
        val configStore = FakeObservedProviderConfigStore(
            initialDraft = ProviderConfigDraft(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
                hasStoredApiKey = false,
            )
        )
        val secretStore = FlowBackedSecretStore(initialHasApiKey = true)
        val useCase = ObserveProviderConfigUseCase(
            providerConfigStore = configStore,
            secretStore = secretStore,
        )

        val observed = useCase().first()

        assertTrue(observed.hasStoredApiKey)
        assertTrue(observed.isReady)
    }

    @Test
    fun `emits updated provider draft when secret availability changes without draft updates`() = runBlocking {
        val configStore = FakeObservedProviderConfigStore(
            initialDraft = ProviderConfigDraft(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
                hasStoredApiKey = false,
            )
        )
        val secretStore = FlowBackedSecretStore(initialHasApiKey = false)
        val useCase = ObserveProviderConfigUseCase(
            providerConfigStore = configStore,
            secretStore = secretStore,
        )
        val initial = useCase().first()
        val updatedDeferred = async {
            useCase().first { it.hasStoredApiKey }
        }

        yield()
        secretStore.updateHasApiKey(true)
        val updated = updatedDeferred.await()

        assertEquals(false, initial.hasStoredApiKey)
        assertEquals(true, updated.hasStoredApiKey)
        assertTrue(updated.isReady)
    }
}

private class FakeObservedProviderConfigStore(
    initialDraft: ProviderConfigDraft,
) : ProviderConfigStore {
    private val drafts = MutableStateFlow(initialDraft)

    override fun observeDraft(): Flow<ProviderConfigDraft> = drafts

    override suspend fun saveDraft(draft: ProviderConfigDraft): HostResult<Unit> {
        drafts.value = draft
        return HostResult.Success(Unit)
    }
}

private class FlowBackedSecretStore(
    initialHasApiKey: Boolean,
) : SecretStore {
    private val apiKeyAvailability = MutableStateFlow(initialHasApiKey)

    override suspend fun saveApiKey(apiKey: String): HostResult<Unit> {
        apiKeyAvailability.value = apiKey.isNotBlank()
        return HostResult.Success(Unit)
    }

    override suspend fun readApiKey(): String? = if (apiKeyAvailability.value) "stored-secret" else null

    override suspend fun hasApiKey(): Boolean = apiKeyAvailability.value

    override fun observeApiKeyAvailability(): Flow<Boolean> = apiKeyAvailability

    override suspend fun clearApiKey(): HostResult<Unit> {
        apiKeyAvailability.value = false
        return HostResult.Success(Unit)
    }

    fun updateHasApiKey(value: Boolean) {
        apiKeyAvailability.value = value
    }
}
