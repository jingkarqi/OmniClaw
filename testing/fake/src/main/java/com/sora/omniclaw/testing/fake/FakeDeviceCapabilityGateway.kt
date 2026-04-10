package com.sora.omniclaw.testing.fake

import com.sora.omniclaw.bridge.api.BridgeCommand
import com.sora.omniclaw.bridge.api.BridgeResponse
import com.sora.omniclaw.bridge.api.BridgeResponseStatus
import com.sora.omniclaw.bridge.api.DeviceCapabilityGateway
import com.sora.omniclaw.core.model.PermissionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FakeDeviceCapabilityGateway(
    initialPermissionSummary: PermissionSummary = PermissionSummary(),
) : DeviceCapabilityGateway {
    private val permissionSummary = MutableStateFlow(initialPermissionSummary)

    var nextResponse: BridgeResponse = BridgeResponse(
        requestId = "fake-response",
        status = BridgeResponseStatus.Success,
        payload = buildJsonObject {
            put("handledBy", "FakeDeviceCapabilityGateway")
        },
    )

    override fun observePermissionSummary(): Flow<PermissionSummary> = permissionSummary.asStateFlow()

    override suspend fun execute(command: BridgeCommand): BridgeResponse {
        return nextResponse.copy(requestId = command.requestId)
    }

    fun updatePermissionSummary(summary: PermissionSummary) {
        permissionSummary.value = summary
    }
}
