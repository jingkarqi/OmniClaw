package com.sora.omniclaw.domain.runtime

import com.sora.omniclaw.bridge.api.BridgeServer
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.runtime.api.RuntimeManager

class StopHostUseCase(
    private val runtimeManager: RuntimeManager,
    private val bridgeServer: BridgeServer,
) {
    suspend operator fun invoke(): HostResult<Unit> {
        val runtimeStopResult = runtimeManager.stop()
        val bridgeStopResult = bridgeServer.stop()

        return when {
            runtimeStopResult is HostResult.Failure -> runtimeStopResult
            bridgeStopResult is HostResult.Failure -> bridgeStopResult
            else -> HostResult.Success(Unit)
        }
    }
}
