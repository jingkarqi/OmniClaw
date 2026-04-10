package com.sora.omniclaw.testing.fake

import com.sora.omniclaw.bridge.api.BridgeServer
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BridgeLifecycleState
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

    override suspend fun start(): HostResult<Unit> {
        return nextStartResult.also { result ->
            _status.value = when (result) {
                is HostResult.Success -> _status.value.copy(
                    lifecycleState = BridgeLifecycleState.Running,
                    endpoint = _status.value.endpoint ?: "fake://bridge",
                    lastErrorMessage = null,
                )

                is HostResult.Failure -> _status.value.copy(
                    lifecycleState = BridgeLifecycleState.Error,
                    lastErrorMessage = result.error.message,
                )
            }
        }
    }

    override suspend fun stop(): HostResult<Unit> {
        return nextStopResult.also { result ->
            _status.value = when (result) {
                is HostResult.Success -> _status.value.copy(
                    lifecycleState = BridgeLifecycleState.Stopped,
                    endpoint = null,
                    lastErrorMessage = null,
                )

                is HostResult.Failure -> _status.value.copy(
                    lifecycleState = BridgeLifecycleState.Error,
                    lastErrorMessage = result.error.message,
                )
            }
        }
    }

    fun updateStatus(status: BridgeStatus) {
        _status.value = status
    }
}
