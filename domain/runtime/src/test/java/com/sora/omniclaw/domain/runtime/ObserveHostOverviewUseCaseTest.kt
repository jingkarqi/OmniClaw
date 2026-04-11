package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.core.model.BridgeLifecycleState
import com.sora.omniclaw.core.model.BridgeStatus
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.PermissionGrantState
import com.sora.omniclaw.core.model.PermissionStatus
import com.sora.omniclaw.core.model.PermissionSummary
import com.sora.omniclaw.core.model.ProviderRuntimeExport
import com.sora.omniclaw.core.model.RuntimeStatus
import com.sora.omniclaw.core.storage.ProviderExportStore
import com.sora.omniclaw.core.storage.SecretStore
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.testing.fake.FakeBridgeServer
import com.sora.omniclaw.testing.fake.FakeDeviceCapabilityGateway
import com.sora.omniclaw.testing.fake.FakeRuntimeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        val exportStore = FakeOverviewProviderExportStore(
            initialExport = ProviderRuntimeExport(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
            )
        )
        val secretStore = FakeOverviewSecretStore(initialAvailability = true)
        val useCase = ObserveHostOverviewUseCase(
            runtimeManager = runtimeManager,
            bridgeServer = bridgeServer,
            deviceCapabilityGateway = capabilityGateway,
            providerExportStore = exportStore,
            secretStore = secretStore,
        )

        val overview = useCase().first()

        assertEquals(HostLifecycleState.Running, overview.runtimeStatus.lifecycleState)
        assertEquals(BridgeLifecycleState.Running, overview.bridgeStatus.lifecycleState)
        assertTrue(overview.permissionSummary.allRequiredGranted)
        assertTrue(overview.providerConfigReady)
    }

    @Test
    fun `reports provider not ready when export metadata exists but secret store has no api key`() = runBlocking {
        val useCase = ObserveHostOverviewUseCase(
            runtimeManager = FakeRuntimeManager(),
            bridgeServer = FakeBridgeServer(),
            deviceCapabilityGateway = FakeDeviceCapabilityGateway(),
            providerExportStore = FakeOverviewProviderExportStore(
                initialExport = ProviderRuntimeExport(
                    providerId = "openai-compatible",
                    baseUrl = "https://api.example.com",
                    modelName = "gpt-test",
                )
            ),
            secretStore = FakeOverviewSecretStore(initialAvailability = false),
        )

        val overview = useCase().first()

        assertTrue(!overview.providerConfigReady)
    }

    @Test
    fun `emits new overview when secret availability changes without other upstream updates`() = runBlocking {
        val exportStore = FakeOverviewProviderExportStore(
            initialExport = ProviderRuntimeExport(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
            )
        )
        val secretStore = FakeOverviewSecretStore(initialAvailability = false)
        val useCase = ObserveHostOverviewUseCase(
            runtimeManager = FakeRuntimeManager(),
            bridgeServer = FakeBridgeServer(),
            deviceCapabilityGateway = FakeDeviceCapabilityGateway(),
            providerExportStore = exportStore,
            secretStore = secretStore,
        )
        val initial = useCase().first()
        val updatedDeferred = async {
            useCase().first { it.providerConfigReady }
        }

        yield()
        secretStore.updateAvailability(true)
        val updated = updatedDeferred.await()

        assertEquals(false, initial.providerConfigReady)
        assertEquals(true, updated.providerConfigReady)
    }
}

private class FakeOverviewProviderExportStore(
    initialExport: ProviderRuntimeExport?,
) : ProviderExportStore {
    private val exports = MutableStateFlow(initialExport)

    override suspend fun readExport(): ProviderRuntimeExport? = exports.value

    override fun observeExport(): Flow<ProviderRuntimeExport?> = exports

    override fun observeExportReadiness(): Flow<Boolean> = exports.map { it?.isReady == true }

    override suspend fun writeExport(export: ProviderRuntimeExport) = throw UnsupportedOperationException()

    override suspend fun clearExport() = throw UnsupportedOperationException()

    fun updateExport(export: ProviderRuntimeExport?) {
        exports.value = export
    }
}

private class FakeOverviewSecretStore(
    initialAvailability: Boolean,
) : SecretStore {
    private val availability = MutableStateFlow(initialAvailability)

    override suspend fun saveApiKey(apiKey: String): HostResult<Unit> = throw UnsupportedOperationException()

    override suspend fun readApiKey(): String? = if (availability.value) "stored-secret" else null

    override suspend fun hasApiKey(): Boolean = availability.value

    override fun observeApiKeyAvailability(): Flow<Boolean> = availability

    override suspend fun clearApiKey(): HostResult<Unit> = throw UnsupportedOperationException()

    fun updateAvailability(hasApiKey: Boolean) {
        availability.value = hasApiKey
    }
}
