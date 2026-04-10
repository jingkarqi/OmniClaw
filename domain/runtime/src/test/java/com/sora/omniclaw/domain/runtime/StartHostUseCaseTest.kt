package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BridgeLifecycleState
import com.sora.omniclaw.core.model.BridgeStatus
import com.sora.omniclaw.core.model.HostLifecycleState
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartHostUseCaseTest {
    @Test
    fun `returns permission failure and does not start host when required permission is missing`() = runBlocking {
        val runtimeManager = FakeRuntimeManager(
            initialStatus = RuntimeStatus(lifecycleState = HostLifecycleState.Stopped)
        )
        val bridgeServer = FakeBridgeServer(
            initialStatus = BridgeStatus(lifecycleState = BridgeLifecycleState.Stopped)
        )
        val capabilityGateway = FakeDeviceCapabilityGateway(
            initialPermissionSummary = PermissionSummary(
                permissions = listOf(
                    PermissionStatus(
                        id = "accessibility",
                        label = "Accessibility",
                        state = PermissionGrantState.Required,
                        required = true,
                    )
                )
            )
        )
        val providerConfigStore = FakeRuntimeProviderConfigStore(
            ProviderConfigDraft(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
                hasStoredApiKey = true,
            )
        )
        val secretStore = FakeRuntimeSecretStore(hasApiKey = true)
        val useCase = StartHostUseCase(
            bridgeServer = bridgeServer,
            runtimeManager = runtimeManager,
            deviceCapabilityGateway = capabilityGateway,
            providerConfigStore = providerConfigStore,
            secretStore = secretStore,
        )

        val result = useCase()

        assertTrue(result is HostResult.Failure)
        assertEquals(
            HostErrorCategory.Permission,
            (result as HostResult.Failure).error.category,
        )
        assertEquals(BridgeLifecycleState.Stopped, bridgeServer.status.value.lifecycleState)
        assertEquals(HostLifecycleState.Stopped, runtimeManager.status.value.lifecycleState)
    }

    @Test
    fun `returns validation failure and does not start host when provider secret is unavailable`() = runBlocking {
        val runtimeManager = FakeRuntimeManager(
            initialStatus = RuntimeStatus(lifecycleState = HostLifecycleState.Stopped)
        )
        val bridgeServer = FakeBridgeServer(
            initialStatus = BridgeStatus(lifecycleState = BridgeLifecycleState.Stopped)
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
        val providerConfigStore = FakeRuntimeProviderConfigStore(
            ProviderConfigDraft(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
                hasStoredApiKey = true,
            )
        )
        val secretStore = FakeRuntimeSecretStore(hasApiKey = false)
        val useCase = StartHostUseCase(
            bridgeServer = bridgeServer,
            runtimeManager = runtimeManager,
            deviceCapabilityGateway = capabilityGateway,
            providerConfigStore = providerConfigStore,
            secretStore = secretStore,
        )

        val result = useCase()

        assertTrue(result is HostResult.Failure)
        assertEquals(
            HostErrorCategory.Validation,
            (result as HostResult.Failure).error.category,
        )
        assertEquals(BridgeLifecycleState.Stopped, bridgeServer.status.value.lifecycleState)
        assertEquals(HostLifecycleState.Stopped, runtimeManager.status.value.lifecycleState)
    }

    @Test
    fun `starts bridge and runtime when host prerequisites are satisfied`() = runBlocking {
        val runtimeManager = FakeRuntimeManager(
            initialStatus = RuntimeStatus(lifecycleState = HostLifecycleState.Stopped)
        )
        val bridgeServer = FakeBridgeServer(
            initialStatus = BridgeStatus(lifecycleState = BridgeLifecycleState.Stopped)
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
        val providerConfigStore = FakeRuntimeProviderConfigStore(
            ProviderConfigDraft(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
                hasStoredApiKey = true,
            )
        )
        val secretStore = FakeRuntimeSecretStore(hasApiKey = true)
        val useCase = StartHostUseCase(
            bridgeServer = bridgeServer,
            runtimeManager = runtimeManager,
            deviceCapabilityGateway = capabilityGateway,
            providerConfigStore = providerConfigStore,
            secretStore = secretStore,
        )

        val result = useCase()

        assertTrue(result is HostResult.Success<*>)
        assertEquals(BridgeLifecycleState.Running, bridgeServer.status.value.lifecycleState)
        assertEquals(HostLifecycleState.Running, runtimeManager.status.value.lifecycleState)
    }
}

private class FakeRuntimeProviderConfigStore(
    initialDraft: ProviderConfigDraft,
) : ProviderConfigStore {
    private val drafts = MutableStateFlow(initialDraft)

    override fun observeDraft(): Flow<ProviderConfigDraft> = drafts

    override suspend fun saveDraft(draft: ProviderConfigDraft): HostResult<Unit> {
        drafts.value = draft
        return HostResult.Success(Unit)
    }
}

private class FakeRuntimeSecretStore(
    private var hasApiKey: Boolean,
) : SecretStore {
    private val apiKeyAvailability = MutableStateFlow(hasApiKey)

    override suspend fun saveApiKey(apiKey: String): HostResult<Unit> {
        hasApiKey = apiKey.isNotBlank()
        apiKeyAvailability.value = hasApiKey
        return HostResult.Success(Unit)
    }

    override suspend fun readApiKey(): String? = if (hasApiKey) "stored-secret" else null

    override suspend fun hasApiKey(): Boolean = hasApiKey

    override fun observeApiKeyAvailability(): Flow<Boolean> = apiKeyAvailability

    override suspend fun clearApiKey(): HostResult<Unit> {
        hasApiKey = false
        apiKeyAvailability.value = false
        return HostResult.Success(Unit)
    }
}
