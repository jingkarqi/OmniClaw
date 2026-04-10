package com.sora.omniclaw.domain.bridge

import com.sora.omniclaw.bridge.api.DeviceCapabilityGateway
import com.sora.omniclaw.core.model.PermissionSummary
import kotlinx.coroutines.flow.Flow

class ObservePermissionSummaryUseCase(
    private val deviceCapabilityGateway: DeviceCapabilityGateway,
) {
    operator fun invoke(): Flow<PermissionSummary> = deviceCapabilityGateway.observePermissionSummary()
}
