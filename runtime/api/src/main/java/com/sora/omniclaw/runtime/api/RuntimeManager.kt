package com.sora.omniclaw.runtime.api

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadManifest
import com.sora.omniclaw.core.model.DiagnosticsSummary
import com.sora.omniclaw.core.model.RuntimeStatus
import kotlinx.coroutines.flow.StateFlow

interface PayloadLocator {
    suspend fun loadBundledPayloadManifest(): HostResult<BundledPayloadManifest?>
}

interface RuntimeManager {
    val status: StateFlow<RuntimeStatus>
    val diagnostics: StateFlow<DiagnosticsSummary>

    suspend fun start(): HostResult<Unit>

    suspend fun stop(): HostResult<Unit>
}
