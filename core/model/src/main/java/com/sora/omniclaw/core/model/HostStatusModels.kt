package com.sora.omniclaw.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class HostLifecycleState {
    Stopped,
    Starting,
    Running,
    Recovering,
    PermissionRequired,
    ConfigurationInvalid,
    InstallRequired,
    Degraded,
    Error,
}

@Serializable
data class RuntimeStatus(
    val lifecycleState: HostLifecycleState = HostLifecycleState.InstallRequired,
    val payloadAvailable: Boolean = false,
    val lastErrorMessage: String? = null,
)

@Serializable
enum class BridgeLifecycleState {
    Stopped,
    Starting,
    Running,
    Degraded,
    Error,
}

@Serializable
data class BridgeStatus(
    val lifecycleState: BridgeLifecycleState = BridgeLifecycleState.Stopped,
    val endpoint: String? = null,
    val lastErrorMessage: String? = null,
)

@Serializable
enum class PermissionGrantState {
    Granted,
    Required,
    Optional,
    Unavailable,
}

@Serializable
data class PermissionStatus(
    val id: String,
    val label: String,
    val state: PermissionGrantState,
    val required: Boolean,
)

@Serializable
data class PermissionSummary(
    val permissions: List<PermissionStatus> = emptyList(),
) {
    val allRequiredGranted: Boolean
        get() = permissions
            .filter(PermissionStatus::required)
            .all { it.state == PermissionGrantState.Granted }
}

@Serializable
data class DiagnosticsSummary(
    val headline: String = "",
    val details: List<String> = emptyList(),
    val lastUpdatedEpochMillis: Long = 0L,
)

@Serializable
data class HostOverview(
    val runtimeStatus: RuntimeStatus = RuntimeStatus(),
    val bridgeStatus: BridgeStatus = BridgeStatus(),
    val permissionSummary: PermissionSummary = PermissionSummary(),
    val providerConfigReady: Boolean = false,
)
