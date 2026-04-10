package com.sora.omniclaw.bridge.impl

import com.sora.omniclaw.bridge.api.BridgeServer
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BridgeLifecycleState
import com.sora.omniclaw.core.model.BridgeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StubBridgeServer : BridgeServer {
    private val _status = MutableStateFlow(BridgeStatus())
    override val status: StateFlow<BridgeStatus> = _status.asStateFlow()

    override suspend fun start(): HostResult<Unit> {
        _status.value = BridgeStatus(
            lifecycleState = BridgeLifecycleState.Running,
            endpoint = "local://bridge/bootstrap",
        )
        return HostResult.Success(Unit)
    }

    override suspend fun stop(): HostResult<Unit> {
        _status.value = BridgeStatus(
            lifecycleState = BridgeLifecycleState.Stopped,
            endpoint = null,
        )
        return HostResult.Success(Unit)
    }
}
