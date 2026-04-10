package com.sora.omniclaw.testing.fake

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.DiagnosticsSummary
import com.sora.omniclaw.core.model.RuntimeStatus
import com.sora.omniclaw.runtime.api.RuntimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeRuntimeManager(
    initialStatus: RuntimeStatus = RuntimeStatus(),
    initialDiagnostics: DiagnosticsSummary = DiagnosticsSummary(),
) : RuntimeManager {
    private val _status = MutableStateFlow(initialStatus)
    override val status: StateFlow<RuntimeStatus> = _status.asStateFlow()

    private val _diagnostics = MutableStateFlow(initialDiagnostics)
    override val diagnostics: StateFlow<DiagnosticsSummary> = _diagnostics.asStateFlow()

    var nextStartResult: HostResult<Unit> = HostResult.Success(Unit)
    var nextStopResult: HostResult<Unit> = HostResult.Success(Unit)

    override suspend fun start(): HostResult<Unit> = nextStartResult

    override suspend fun stop(): HostResult<Unit> = nextStopResult

    fun updateStatus(status: RuntimeStatus) {
        _status.value = status
    }

    fun updateDiagnostics(diagnostics: DiagnosticsSummary) {
        _diagnostics.value = diagnostics
    }
}
