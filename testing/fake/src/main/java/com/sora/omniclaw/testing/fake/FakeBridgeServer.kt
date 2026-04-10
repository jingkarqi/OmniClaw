package com.sora.omniclaw.testing.fake

import com.sora.omniclaw.bridge.api.BridgeServer
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BridgeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeBridgeServer(
    initialStatus: BridgeStatus = BridgeStatus(),
) : BridgeServer {
    private val _status = MutableStateFlow(initialStatus)
    override val status: StateFlow<BridgeStatus> = _status.asStateFlow()

    var nextStartResult: HostResult<Unit> = HostResult.Success(Unit)
    var nextStopResult: HostResult<Unit> = HostResult.Success(Unit)

    override suspend fun start(): HostResult<Unit> = nextStartResult

    override suspend fun stop(): HostResult<Unit> = nextStopResult

    fun updateStatus(status: BridgeStatus) {
        _status.value = status
    }
}
