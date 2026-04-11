package com.sora.omniclaw.domain.provider

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.model.ProviderRuntimeExport
import com.sora.omniclaw.core.storage.ProviderConfigStore
import com.sora.omniclaw.core.storage.ProviderExportStore
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
        val exportStore = FakeProviderExportStore()
        val useCase = SaveProviderConfigUseCase(
            providerConfigStore = configStore,
            secretStore = secretStore,
            providerExportStore = exportStore,
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
        assertEquals(
            ProviderRuntimeExport(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
            ),
            exportStore.savedExport,
        )
    }

    @Test
    fun `persists missing stored api key when metadata says key exists but secret store is empty`() = runBlocking {
        val configStore = FakeProviderConfigStore()
        val secretStore = FakeSecretStore(savedApiKey = null)
        val exportStore = FakeProviderExportStore()
        val useCase = SaveProviderConfigUseCase(
            providerConfigStore = configStore,
            secretStore = secretStore,
            providerExportStore = exportStore,
        )
        val draft = ProviderConfigDraft(
            providerId = "openai-compatible",
            baseUrl = "https://api.example.com",
            modelName = "gpt-test",
            apiKey = "",
            hasStoredApiKey = true,
        )

        val result = useCase(draft)

        assertTrue(result is HostResult.Success<*>)
        assertEquals(false, configStore.savedDraft?.hasStoredApiKey)
        assertEquals(null, exportStore.savedExport)
    }

    @Test
    fun `returns export failure after persisting the draft when export writing fails`() = runBlocking {
        val configStore = FakeProviderConfigStore()
        val secretStore = FakeSecretStore()
        val exportStore = FakeProviderExportStore(
            initialExport = ProviderRuntimeExport(
                providerId = "stale-provider",
                baseUrl = "https://stale.example.com",
                modelName = "stale-model",
            ),
            writeResult = HostResult.Failure(
                com.sora.omniclaw.core.common.HostError(
                    category = com.sora.omniclaw.core.common.HostErrorCategory.Storage,
                    message = "boom",
                    recoverable = true,
                )
            )
        )
        val useCase = SaveProviderConfigUseCase(
            providerConfigStore = configStore,
            secretStore = secretStore,
            providerExportStore = exportStore,
        )

        val result = useCase(
            ProviderConfigDraft(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
                apiKey = "secret-value",
            )
        )

        assertTrue(result is HostResult.Failure)
        assertEquals("openai-compatible", configStore.savedDraft?.providerId)
        assertEquals("secret-value", secretStore.savedApiKey)
        assertEquals(null, exportStore.savedExport)
    }

    @Test
    fun `returns clear failure before mutating draft or secret state`() = runBlocking {
        val configStore = FakeProviderConfigStore()
        val secretStore = FakeSecretStore(savedApiKey = "old-secret")
        val exportStore = FakeProviderExportStore(
            initialExport = ProviderRuntimeExport(
                providerId = "stale-provider",
                baseUrl = "https://stale.example.com",
                modelName = "stale-model",
            ),
            clearResult = HostResult.Failure(
                com.sora.omniclaw.core.common.HostError(
                    category = com.sora.omniclaw.core.common.HostErrorCategory.Storage,
                    message = "clear failed",
                    recoverable = true,
                )
            )
        )
        val useCase = SaveProviderConfigUseCase(
            providerConfigStore = configStore,
            secretStore = secretStore,
            providerExportStore = exportStore,
        )

        val result = useCase(
            ProviderConfigDraft(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
                apiKey = "new-secret",
            )
        )

        assertTrue(result is HostResult.Failure)
        assertEquals(null, configStore.savedDraft)
        assertEquals("old-secret", secretStore.savedApiKey)
        assertEquals("stale-provider", exportStore.savedExport?.providerId)
    }

    @Test
    fun `clears stale export before saving secret so secret save failures cannot leave it authoritative`() = runBlocking {
        val configStore = FakeProviderConfigStore()
        val secretStore = FakeSecretStore(
            savedApiKey = "old-secret",
            saveResult = HostResult.Failure(
                com.sora.omniclaw.core.common.HostError(
                    category = com.sora.omniclaw.core.common.HostErrorCategory.Storage,
                    message = "secret save failed",
                    recoverable = true,
                )
            )
        )
        val exportStore = FakeProviderExportStore(
            initialExport = ProviderRuntimeExport(
                providerId = "stale-provider",
                baseUrl = "https://stale.example.com",
                modelName = "stale-model",
            ),
        )
        val useCase = SaveProviderConfigUseCase(
            providerConfigStore = configStore,
            secretStore = secretStore,
            providerExportStore = exportStore,
        )

        val result = useCase(
            ProviderConfigDraft(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
                apiKey = "new-secret",
            )
        )

        assertTrue(result is HostResult.Failure)
        assertEquals(null, configStore.savedDraft)
        assertEquals("old-secret", secretStore.savedApiKey)
        assertEquals(null, exportStore.savedExport)
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

private class FakeSecretStore(
    savedApiKey: String? = null,
    private val saveResult: HostResult<Unit> = HostResult.Success(Unit),
    private val clearResult: HostResult<Unit> = HostResult.Success(Unit),
) : SecretStore {
    var savedApiKey: String? = savedApiKey
    private val apiKeyAvailability = MutableStateFlow(false)

    init {
        apiKeyAvailability.value = !savedApiKey.isNullOrBlank()
    }

    override suspend fun saveApiKey(apiKey: String): HostResult<Unit> {
        if (saveResult is HostResult.Success) {
            savedApiKey = apiKey
            apiKeyAvailability.value = apiKey.isNotBlank()
        }
        return saveResult
    }

    override suspend fun readApiKey(): String? = savedApiKey

    override suspend fun hasApiKey(): Boolean = !savedApiKey.isNullOrBlank()

    override fun observeApiKeyAvailability(): Flow<Boolean> = apiKeyAvailability

    override suspend fun clearApiKey(): HostResult<Unit> {
        if (clearResult is HostResult.Success) {
            savedApiKey = null
            apiKeyAvailability.value = false
        }
        return clearResult
    }
}

private class FakeProviderExportStore(
    initialExport: ProviderRuntimeExport? = null,
    private val writeResult: HostResult<Unit> = HostResult.Success(Unit),
    private val clearResult: HostResult<Unit> = HostResult.Success(Unit),
) : ProviderExportStore {
    private val exports = MutableStateFlow(initialExport)
    var savedExport: ProviderRuntimeExport? = initialExport

    override suspend fun readExport(): ProviderRuntimeExport? = exports.value

    override fun observeExport(): Flow<ProviderRuntimeExport?> = exports

    override fun observeExportReadiness(): Flow<Boolean> = MutableStateFlow(exports.value?.isReady == true)

    override suspend fun writeExport(export: ProviderRuntimeExport): HostResult<Unit> {
        if (writeResult is HostResult.Success) {
            savedExport = export
            exports.value = export
        }
        return writeResult
    }

    override suspend fun clearExport(): HostResult<Unit> {
        if (clearResult is HostResult.Success) {
            savedExport = null
            exports.value = null
        }
        return clearResult
    }
}
