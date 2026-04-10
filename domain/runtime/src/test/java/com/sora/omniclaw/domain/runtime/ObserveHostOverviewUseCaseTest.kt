package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.core.model.BridgeLifecycleState
import com.sora.omniclaw.core.model.BridgeStatus
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.PermissionGrantState
import com.sora.omniclaw.core.model.PermissionStatus
import com.sora.omniclaw.core.model.PermissionSummary
import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.model.RuntimeStatus
import com.sora.omniclaw.core.storage.ProviderConfigStore
import com.sora.omniclaw.testing.fake.FakeBridgeServer
import com.sora.omniclaw.testing.fake.FakeDeviceCapabilityGateway
import com.sora.omniclaw.testing.fake.FakeRuntimeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        )

        val overview = useCase().first()

        assertEquals(HostLifecycleState.Running, overview.runtimeStatus.lifecycleState)
        assertEquals(BridgeLifecycleState.Running, overview.bridgeStatus.lifecycleState)
        assertTrue(overview.permissionSummary.allRequiredGranted)
        assertTrue(overview.providerConfigReady)
    }
}

private class FakeOverviewProviderConfigStore(
    initialDraft: ProviderConfigDraft,
) : ProviderConfigStore {
    private val drafts = MutableStateFlow(initialDraft)

    override fun observeDraft(): Flow<ProviderConfigDraft> = drafts

    override suspend fun saveDraft(draft: ProviderConfigDraft) = throw UnsupportedOperationException()
}
