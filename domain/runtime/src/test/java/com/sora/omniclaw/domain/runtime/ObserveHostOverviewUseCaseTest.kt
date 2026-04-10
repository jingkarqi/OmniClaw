package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.core.model.BridgeLifecycleState
import com.sora.omniclaw.core.model.BridgeStatus
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.HostOverview
import com.sora.omniclaw.core.model.PermissionGrantState
import com.sora.omniclaw.core.model.PermissionStatus
import com.sora.omniclaw.core.model.PermissionSummary
import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.model.RuntimeStatus
import com.sora.omniclaw.core.storage.ProviderConfigStore
import com.sora.omniclaw.core.storage.SecretStore
import com.sora.omniclaw.testing.fake.FakeBridgeServer
import com.sora.omniclaw.testing.fake.FakeDeviceCapabilityGateway
import com.sora.omniclaw.testing.fake.FakeRuntimeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveHostOverviewUseCaseTest {
    @Test
    fun `combines runtime bridge permissions and provider readiness into one overview`() = runBlocking {
        val runtimeManager = FakeRuntimeManager(
            initialStatus = RuntimeStatus(
                lifecycleState = HostLifecycleState.Running,
                payloadAvailable = true,
            )
        )
        val bridgeServer = FakeBridgeServer(
            initialStatus = BridgeStatus(
                lifecycleState = BridgeLifecycleState.Running,
                endpoint = "local://bridge/bootstrap",
            )
        )
        val capabilityGateway = FakeDeviceCapabilityGateway(
            initialPermissionSummary = PermissionSummary(
                permissions = listOf(
                    PermissionStatus(
                        id = "notifications",
                        label = "Notifications",
                        state = PermissionGrantState.Granted,
                        required = true,
                    )
                )
            )
        )
        val configStore = FakeOverviewProviderConfigStore(
            initialDraft = ProviderConfigDraft(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
                hasStoredApiKey = true,
            )
        )
        val useCase = ObserveHostOverviewUseCase(
            runtimeManager = runtimeManager,
            bridgeServer = bridgeServer,
            deviceCapabilityGateway = capabilityGateway,
            providerConfigStore = configStore,
            secretStore = FakeOverviewSecretStore(hasApiKey = true),
        )

        val overview = useCase().first()

        assertEquals(HostLifecycleState.Running, overview.runtimeStatus.lifecycleState)
        assertEquals(BridgeLifecycleState.Running, overview.bridgeStatus.lifecycleState)
        assertTrue(overview.permissionSummary.allRequiredGranted)
        assertTrue(overview.providerConfigReady)
    }

    @Test
    fun `reports provider not ready when stored flag exists but secret store has no api key`() = runBlocking {
        val useCase = ObserveHostOverviewUseCase(
            runtimeManager = FakeRuntimeManager(),
            bridgeServer = FakeBridgeServer(),
            deviceCapabilityGateway = FakeDeviceCapabilityGateway(),
            providerConfigStore = FakeOverviewProviderConfigStore(
                initialDraft = ProviderConfigDraft(
                    providerId = "openai-compatible",
                    baseUrl = "https://api.example.com",
                    modelName = "gpt-test",
                    hasStoredApiKey = true,
                )
            ),
            secretStore = FakeOverviewSecretStore(hasApiKey = false),
        )

        val overview = useCase().first()

        assertTrue(!overview.providerConfigReady)
    }

    @Test
    fun `emits new overview when secret availability changes without other upstream updates`() = runBlocking {
        val secretStore = FakeOverviewSecretStore(hasApiKey = false)
        val useCase = ObserveHostOverviewUseCase(
            runtimeManager = FakeRuntimeManager(),
            bridgeServer = FakeBridgeServer(),
            deviceCapabilityGateway = FakeDeviceCapabilityGateway(),
            providerConfigStore = FakeOverviewProviderConfigStore(
                initialDraft = ProviderConfigDraft(
                    providerId = "openai-compatible",
                    baseUrl = "https://api.example.com",
                    modelName = "gpt-test",
                    hasStoredApiKey = false,
                )
            ),
            secretStore = secretStore,
        )
        val initial = useCase().first()
        val updatedDeferred = async {
            useCase().first { it.providerConfigReady }
        }

        yield()
        secretStore.updateHasApiKey(true)
        val updated = updatedDeferred.await()

        assertEquals(false, initial.providerConfigReady)
        assertEquals(true, updated.providerConfigReady)
    }
}

private class FakeOverviewProviderConfigStore(
    initialDraft: ProviderConfigDraft,
) : ProviderConfigStore {
    private val drafts = MutableStateFlow(initialDraft)

    override fun observeDraft(): Flow<ProviderConfigDraft> = drafts

    override suspend fun saveDraft(draft: ProviderConfigDraft) = throw UnsupportedOperationException()
}

private class FakeOverviewSecretStore(
    hasApiKey: Boolean,
) : SecretStore {
    private val apiKeyState = MutableStateFlow(hasApiKey)

    override suspend fun saveApiKey(apiKey: String) = throw UnsupportedOperationException()

    override suspend fun readApiKey(): String? = if (apiKeyState.value) "stored-secret" else null

    override suspend fun hasApiKey(): Boolean = apiKeyState.value

    override fun observeApiKeyAvailability(): Flow<Boolean> = apiKeyState

    override suspend fun clearApiKey() = throw UnsupportedOperationException()

    fun updateHasApiKey(value: Boolean) {
        apiKeyState.value = value
    }
}
