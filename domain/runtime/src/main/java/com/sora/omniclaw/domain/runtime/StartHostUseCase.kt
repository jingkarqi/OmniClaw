package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.bridge.api.BridgeServer
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.runtime.api.RuntimeManager

class StartHostUseCase(
    private val bridgeServer: BridgeServer,
    private val runtimeManager: RuntimeManager,
) {
    suspend operator fun invoke(): HostResult<Unit> {
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
