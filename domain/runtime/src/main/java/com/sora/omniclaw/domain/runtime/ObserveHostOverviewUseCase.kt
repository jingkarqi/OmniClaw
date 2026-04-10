package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.bridge.api.BridgeServer
import com.sora.omniclaw.bridge.api.DeviceCapabilityGateway
import com.sora.omniclaw.core.model.HostOverview
import com.sora.omniclaw.core.storage.ProviderConfigStore
import com.sora.omniclaw.runtime.api.RuntimeManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveHostOverviewUseCase(
    private val runtimeManager: RuntimeManager,
    private val bridgeServer: BridgeServer,
    private val deviceCapabilityGateway: DeviceCapabilityGateway,
    private val providerConfigStore: ProviderConfigStore,
) {
    operator fun invoke(): Flow<HostOverview> {
        return combine(
            runtimeManager.status,
            bridgeServer.status,
            deviceCapabilityGateway.observePermissionSummary(),
            providerConfigStore.observeDraft(),
        ) { runtimeStatus, bridgeStatus, permissionSummary, providerDraft ->
            HostOverview(
                runtimeStatus = runtimeStatus,
                bridgeStatus = bridgeStatus,
                permissionSummary = permissionSummary,
                providerConfigReady = providerDraft.isReady,
            )
        }
    }
}
