package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.bridge.api.BridgeServer
import com.sora.omniclaw.core.storage.SecretStore
import com.sora.omniclaw.bridge.api.DeviceCapabilityGateway
import com.sora.omniclaw.core.model.HostOverview
import com.sora.omniclaw.core.storage.ProviderExportStore
import com.sora.omniclaw.runtime.api.RuntimeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveHostOverviewUseCase(
    private val runtimeManager: RuntimeManager,
    private val bridgeServer: BridgeServer,
    private val deviceCapabilityGateway: DeviceCapabilityGateway,
    private val providerExportStore: ProviderExportStore,
    private val secretStore: SecretStore,
) {
    operator fun invoke(): Flow<HostOverview> {
        return combine(
            runtimeManager.status,
            bridgeServer.status,
            deviceCapabilityGateway.observePermissionSummary(),
            providerExportStore.observeExportReadiness(),
            secretStore.observeApiKeyAvailability(),
        ) { runtimeStatus, bridgeStatus, permissionSummary, exportReady, hasApiKey ->
            HostOverview(
                runtimeStatus = runtimeStatus,
                bridgeStatus = bridgeStatus,
                permissionSummary = permissionSummary,
                providerConfigReady = exportReady && hasApiKey,
            )
        }
    }
}
