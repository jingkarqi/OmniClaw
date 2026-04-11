package com.sora.omniclaw.bridge.impl

import com.sora.omniclaw.bridge.api.BridgeCommand
import com.sora.omniclaw.bridge.api.BridgeResponse
import com.sora.omniclaw.bridge.api.BridgeResponseStatus
import com.sora.omniclaw.bridge.api.DeviceCapabilityGateway
import com.sora.omniclaw.core.model.PermissionGrantState
import com.sora.omniclaw.core.model.PermissionStatus
import com.sora.omniclaw.core.model.PermissionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class StubDeviceCapabilityGateway : DeviceCapabilityGateway {
    private val supportedCapabilities = setOf(
        "device.info",
        "device.permissions",
        "device.status",
        "device.health",
    )

    private val permissionSummary = MutableStateFlow(
        PermissionSummary(
            permissions = listOf(
                PermissionStatus(
                    id = "foreground-service",
                    label = "Foreground service",
                    state = PermissionGrantState.Granted,
                    required = true,
                ),
                PermissionStatus(
                    id = "notifications",
                    label = "Notifications",
                    state = PermissionGrantState.Granted,
                    required = false,
                ),
                PermissionStatus(
                    id = "accessibility",
                    label = "Accessibility",
                    state = PermissionGrantState.Unavailable,
                    required = false,
                ),
            )
        )
    )

    override fun observePermissionSummary(): Flow<PermissionSummary> = permissionSummary.asStateFlow()

    override suspend fun execute(command: BridgeCommand): BridgeResponse {
        if (command.capability !in supportedCapabilities) {
            return BridgeResponse(
                requestId = command.requestId,
                status = BridgeResponseStatus.Failure,
                error = com.sora.omniclaw.bridge.api.BridgeResponseError(
                    category = "unsupported",
                    message = "Capability '${command.capability}' is not supported by StubDeviceCapabilityGateway.",
                    recoverable = true,
                ),
            )
        }

        return BridgeResponse(
            requestId = command.requestId,
            status = BridgeResponseStatus.Success,
            payload = buildJsonObject {
                put("handledBy", "StubDeviceCapabilityGateway")
                put("capability", command.capability)
                put("action", command.action)
            },
        )
    }
}
