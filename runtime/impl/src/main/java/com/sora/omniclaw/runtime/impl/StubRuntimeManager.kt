package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.DiagnosticsSummary
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.RuntimeStatus
import com.sora.omniclaw.runtime.api.PayloadLocator
import com.sora.omniclaw.runtime.api.RuntimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StubRuntimeManager(
    private val payloadLocator: PayloadLocator,
    private val payloadValidator: BootstrapPayloadValidator = BootstrapPayloadValidator(),
) : RuntimeManager {
    private val _status = MutableStateFlow(
        RuntimeStatus(
            lifecycleState = HostLifecycleState.InstallRequired,
            payloadAvailable = false,
        )
    )
    override val status: StateFlow<RuntimeStatus> = _status.asStateFlow()

    private val _diagnostics = MutableStateFlow(
        DiagnosticsSummary(
            headline = "Runtime idle",
            details = listOf("Stub runtime manager is waiting to be started."),
        )
    )
    override val diagnostics: StateFlow<DiagnosticsSummary> = _diagnostics.asStateFlow()

    override suspend fun start(): HostResult<Unit> {
        _status.value = _status.value.copy(
            lifecycleState = HostLifecycleState.Starting,
            lastErrorMessage = null,
        )

        return when (val manifestResult = payloadLocator.loadBundledPayloadManifest()) {
            is HostResult.Success -> {
                when (val validationResult = payloadValidator.validate(manifestResult.value)) {
                    is HostResult.Success -> {
                        val manifest = validationResult.value
                        _status.value = RuntimeStatus(
                            lifecycleState = HostLifecycleState.Running,
                            payloadAvailable = true,
                        )
                        _diagnostics.value = DiagnosticsSummary(
                            headline = "Runtime running",
                            details = listOf("Stub runtime started with ${manifest.payloads.size} bundled payload entries."),
                        )
                        HostResult.Success(Unit)
                    }

                    is HostResult.Failure -> {
                        _status.value = RuntimeStatus(
                            lifecycleState = HostLifecycleState.InstallRequired,
                            payloadAvailable = false,
                            lastErrorMessage = validationResult.error.message,
                        )
                        _diagnostics.value = DiagnosticsSummary(
                            headline = "Install required",
                            details = listOf(validationResult.error.message),
                        )
                        validationResult
                    }
                }
            }

            is HostResult.Failure -> {
                _status.value = RuntimeStatus(
                    lifecycleState = HostLifecycleState.Error,
                    payloadAvailable = false,
                    lastErrorMessage = manifestResult.error.message,
                )
                _diagnostics.value = DiagnosticsSummary(
                    headline = "Runtime failed",
                    details = listOf(manifestResult.error.message),
                )
                manifestResult
            }
        }
    }

    override suspend fun stop(): HostResult<Unit> {
        _status.value = _status.value.copy(
            lifecycleState = HostLifecycleState.Stopped,
            lastErrorMessage = null,
        )
        _diagnostics.value = DiagnosticsSummary(
            headline = "Runtime stopped",
            details = listOf("Stub runtime is not running."),
        )
        return HostResult.Success(Unit)
    }
}
