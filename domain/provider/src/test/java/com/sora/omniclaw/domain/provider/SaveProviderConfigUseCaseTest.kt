package com.sora.omniclaw.domain.provider

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.storage.ProviderConfigStore
import com.sora.omniclaw.core.storage.SecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveProviderConfigUseCaseTest {
    @Test
    fun `stores api key in secret store and persists draft without secret`() = runBlocking {
        val configStore = FakeProviderConfigStore()
        val secretStore = FakeSecretStore()
        val useCase = SaveProviderConfigUseCase(
            providerConfigStore = configStore,
            secretStore = secretStore,
        )
        val draft = ProviderConfigDraft(
            providerId = "openai-compatible",
            baseUrl = "https://api.example.com",
            modelName = "gpt-test",
            apiKey = "secret-value",
        )

        val result = useCase(draft)

        assertTrue(result is HostResult.Success<*>)
        assertEquals("secret-value", secretStore.savedApiKey)
        assertEquals("", configStore.savedDraft?.apiKey)
        assertEquals(true, configStore.savedDraft?.hasStoredApiKey)
    }
}

private class FakeProviderConfigStore : ProviderConfigStore {
    private val drafts = MutableStateFlow(ProviderConfigDraft())
    var savedDraft: ProviderConfigDraft? = null

    override fun observeDraft(): Flow<ProviderConfigDraft> = drafts

    override suspend fun saveDraft(draft: ProviderConfigDraft): HostResult<Unit> {
        savedDraft = draft
        drafts.value = draft
        return HostResult.Success(Unit)
    }
}

private class FakeSecretStore : SecretStore {
    var savedApiKey: String? = null

    override suspend fun saveApiKey(apiKey: String): HostResult<Unit> {
        savedApiKey = apiKey
        return HostResult.Success(Unit)
    }

    override suspend fun readApiKey(): String? = savedApiKey

    override suspend fun hasApiKey(): Boolean = !savedApiKey.isNullOrBlank()

    override suspend fun clearApiKey(): HostResult<Unit> {
        savedApiKey = null
        return HostResult.Success(Unit)
    }
}
