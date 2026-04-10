package com.sora.omniclaw.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sora.omniclaw.core.model.BridgeLifecycleState
import com.sora.omniclaw.core.model.BridgeStatus
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.HostOverview
import com.sora.omniclaw.core.model.PermissionGrantState
import com.sora.omniclaw.core.model.PermissionStatus
import com.sora.omniclaw.core.model.PermissionSummary
import com.sora.omniclaw.core.model.RuntimeStatus

object HomeScreenTags {
    const val StartHostButton = "home:start_host"
    const val StopHostButton = "home:stop_host"
    const val ProviderButton = "home:open_provider"
    const val RuntimeButton = "home:open_runtime"
    const val PermissionsButton = "home:open_permissions"
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun HomeScreen(
    overview: HostOverview,
    onStartHost: () -> Unit,
    onStopHost: () -> Unit,
    onOpenProvider: () -> Unit,
    onOpenRuntime: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = "OmniClaw host") })
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = homeSummary(overview),
                style = MaterialTheme.typography.bodyLarge
            )

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Host status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = overview.runtimeStatus.lifecycleState.displayName(),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            StatusGrid(
                runtimeStatus = overview.runtimeStatus.lifecycleState.displayName(),
                bridgeStatus = overview.bridgeStatus.lifecycleState.displayName(),
                providerStatus = if (overview.providerConfigReady) "Configured" else "Needs setup",
                permissionsStatus = if (overview.permissionSummary.allRequiredGranted) "Granted" else "Attention needed",
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Quick actions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onStartHost,
                            modifier = Modifier.testTag(HomeScreenTags.StartHostButton)
                        ) {
                            Text(text = "Start host")
                        }
                        OutlinedButton(
                            onClick = onStopHost,
                            modifier = Modifier.testTag(HomeScreenTags.StopHostButton)
                        ) {
                            Text(text = "Stop host")
                        }
                        OutlinedButton(
                            onClick = onOpenProvider,
                            modifier = Modifier.testTag(HomeScreenTags.ProviderButton)
                        ) {
                            Text(text = "Open provider")
                        }
                        OutlinedButton(
                            onClick = onOpenRuntime,
                            modifier = Modifier.testTag(HomeScreenTags.RuntimeButton)
                        ) {
                            Text(text = "Open runtime")
                        }
                        OutlinedButton(
                            onClick = onOpenPermissions,
                            modifier = Modifier.testTag(HomeScreenTags.PermissionsButton)
                        ) {
                            Text(text = "Open permissions")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StatusGrid(
    runtimeStatus: String,
    bridgeStatus: String,
    providerStatus: String,
    permissionsStatus: String
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusCard(title = "Runtime", value = runtimeStatus)
        StatusCard(title = "Bridge", value = bridgeStatus)
        StatusCard(title = "Provider", value = providerStatus)
        StatusCard(title = "Permissions", value = permissionsStatus)
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String
) {
    ElevatedCard(
        modifier = Modifier.width(160.dp),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(text = value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun homeSummary(overview: HostOverview): String {
    val runtimeLine = when (overview.runtimeStatus.lifecycleState) {
        HostLifecycleState.Running -> "Runtime is active."
        HostLifecycleState.InstallRequired -> "Runtime payload is still missing."
        HostLifecycleState.Error -> overview.runtimeStatus.lastErrorMessage
            ?: "Runtime reported an error."
        else -> "Runtime is ${overview.runtimeStatus.lifecycleState.displayName().lowercase()}."
    }
    val providerLine = if (overview.providerConfigReady) {
        "Provider configuration is ready."
    } else {
        "Provider configuration still needs attention."
    }
    val permissionsLine = if (overview.permissionSummary.allRequiredGranted) {
        "Required permissions are granted."
    } else {
        "At least one required permission is missing."
    }
    return "$runtimeLine $providerLine $permissionsLine"
}

private fun HostLifecycleState.displayName(): String = when (this) {
    HostLifecycleState.Stopped -> "Stopped"
    HostLifecycleState.Starting -> "Starting"
    HostLifecycleState.Running -> "Running"
    HostLifecycleState.Recovering -> "Recovering"
    HostLifecycleState.PermissionRequired -> "Permission required"
    HostLifecycleState.ConfigurationInvalid -> "Configuration invalid"
    HostLifecycleState.InstallRequired -> "Install required"
    HostLifecycleState.Degraded -> "Degraded"
    HostLifecycleState.Error -> "Error"
}

private fun BridgeLifecycleState.displayName(): String = when (this) {
    BridgeLifecycleState.Stopped -> "Stopped"
    BridgeLifecycleState.Starting -> "Starting"
    BridgeLifecycleState.Running -> "Running"
    BridgeLifecycleState.Degraded -> "Degraded"
    BridgeLifecycleState.Error -> "Error"
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    Surface {
        HomeScreen(
            overview = HostOverview(
                runtimeStatus = RuntimeStatus(
                    lifecycleState = HostLifecycleState.Running,
                    payloadAvailable = true,
                ),
                bridgeStatus = BridgeStatus(
                    lifecycleState = BridgeLifecycleState.Running,
                    endpoint = "local://bridge/bootstrap",
                ),
                permissionSummary = PermissionSummary(
                    permissions = listOf(
                        PermissionStatus(
                            id = "notifications",
                            label = "Notifications",
                            state = PermissionGrantState.Granted,
                            required = true,
                        )
                    )
                ),
                providerConfigReady = true,
            ),
            onStartHost = {},
            onStopHost = {},
            onOpenProvider = {},
            onOpenRuntime = {},
            onOpenPermissions = {}
        )
    }
}
