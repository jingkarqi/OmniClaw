package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.bridge.api.BridgeServer
import com.sora.omniclaw.bridge.api.DeviceCapabilityGateway
import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.storage.ProviderExportStore
import com.sora.omniclaw.core.storage.SecretStore
import com.sora.omniclaw.runtime.api.RuntimeManager
import kotlinx.coroutines.flow.first

class StartHostUseCase(
    private val bridgeServer: BridgeServer,
    private val runtimeManager: RuntimeManager,
    private val deviceCapabilityGateway: DeviceCapabilityGateway,
    private val providerExportStore: ProviderExportStore,
    private val secretStore: SecretStore,
) {
    suspend operator fun invoke(): HostResult<Unit> {
        val permissionSummary = deviceCapabilityGateway.observePermissionSummary().first()
        if (!permissionSummary.allRequiredGranted) {
            return HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Permission,
                    message = "Required host permissions are missing.",
                    recoverable = true,
                )
            )
        }

        if (!providerExportStore.observeExportReadiness().first() || !secretStore.hasApiKey()) {
            return HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Validation,
                    message = "Runtime provider export or secret is missing.",
                    recoverable = true,
                )
            )
        }

        val bridgeStartResult = bridgeServer.start()
        if (bridgeStartResult is HostResult.Failure) {
            return bridgeStartResult
        }

        val runtimeStartResult = runtimeManager.start()
        if (runtimeStartResult is HostResult.Failure) {
            bridgeServer.stop()
            return runtimeStartResult
        }

        return HostResult.Success(Unit)
    }
}
