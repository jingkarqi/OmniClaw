package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
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
import com.sora.omniclaw.testing.fake.FakeBridgeServer
import com.sora.omniclaw.testing.fake.FakeDeviceCapabilityGateway
import com.sora.omniclaw.testing.fake.FakeRuntimeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
        val providerExportStore = FakeRuntimeProviderExportStore(
            ProviderRuntimeExport(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
            )
        )
        val useCase = StartHostUseCase(
            bridgeServer = bridgeServer,
            runtimeManager = runtimeManager,
            deviceCapabilityGateway = capabilityGateway,
            providerExportStore = providerExportStore,
            secretStore = FakeRuntimeSecretStore(hasApiKey = true),
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
    fun `returns validation failure and does not start host when runtime provider export is unavailable`() = runBlocking {
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
        val providerExportStore = FakeRuntimeProviderExportStore(null)
        val useCase = StartHostUseCase(
            bridgeServer = bridgeServer,
            runtimeManager = runtimeManager,
            deviceCapabilityGateway = capabilityGateway,
            providerExportStore = providerExportStore,
            secretStore = FakeRuntimeSecretStore(hasApiKey = false),
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
        val providerExportStore = FakeRuntimeProviderExportStore(
            ProviderRuntimeExport(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
            )
        )
        val useCase = StartHostUseCase(
            bridgeServer = bridgeServer,
            runtimeManager = runtimeManager,
            deviceCapabilityGateway = capabilityGateway,
            providerExportStore = providerExportStore,
            secretStore = FakeRuntimeSecretStore(hasApiKey = true),
        )

        val result = useCase()

        assertTrue(result is HostResult.Success<*>)
        assertEquals(BridgeLifecycleState.Running, bridgeServer.status.value.lifecycleState)
        assertEquals(HostLifecycleState.Running, runtimeManager.status.value.lifecycleState)
    }

    @Test
    fun `returns validation failure when export metadata exists but secret is unavailable`() = runBlocking {
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
        val providerExportStore = FakeRuntimeProviderExportStore(
            ProviderRuntimeExport(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com",
                modelName = "gpt-test",
            )
        )
        val useCase = StartHostUseCase(
            bridgeServer = bridgeServer,
            runtimeManager = runtimeManager,
            deviceCapabilityGateway = capabilityGateway,
            providerExportStore = providerExportStore,
            secretStore = FakeRuntimeSecretStore(hasApiKey = false),
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
}

private class FakeRuntimeProviderExportStore(
    initialExport: ProviderRuntimeExport?,
) : ProviderExportStore {
    private val exports = MutableStateFlow(initialExport)

    override suspend fun readExport(): ProviderRuntimeExport? = exports.value

    override fun observeExport(): Flow<ProviderRuntimeExport?> = exports

    override fun observeExportReadiness(): Flow<Boolean> = exports.map { it?.isReady == true }

    override suspend fun writeExport(export: ProviderRuntimeExport): HostResult<Unit> {
        exports.value = export
        return HostResult.Success(Unit)
    }

    override suspend fun clearExport(): HostResult<Unit> {
        exports.value = null
        return HostResult.Success(Unit)
    }
}

private class FakeRuntimeSecretStore(
    private val hasApiKey: Boolean,
) : SecretStore {
    private val availability = MutableStateFlow(hasApiKey)

    override suspend fun saveApiKey(apiKey: String): HostResult<Unit> = throw UnsupportedOperationException()

    override suspend fun readApiKey(): String? = if (hasApiKey) "stored-secret" else null

    override suspend fun hasApiKey(): Boolean = hasApiKey

    override fun observeApiKeyAvailability(): Flow<Boolean> = availability

    override suspend fun clearApiKey(): HostResult<Unit> = throw UnsupportedOperationException()
}
