package com.sora.omniclaw.bridge.api

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BridgeStatus
import com.sora.omniclaw.core.model.PermissionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BridgeServer {
    val status: StateFlow<BridgeStatus>

    suspend fun start(): HostResult<Unit>

    suspend fun stop(): HostResult<Unit>
}

interface DeviceCapabilityGateway {
    fun observePermissionSummary(): Flow<PermissionSummary>

    suspend fun execute(command: BridgeCommand): BridgeResponse
}
