package com.sora.omniclaw.feature.runtime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sora.omniclaw.core.model.DiagnosticsSummary
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.RuntimeStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeScreen(
    status: RuntimeStatus,
    diagnostics: DiagnosticsSummary,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onViewDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Runtime") },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = runtimeHeadline(status.lifecycleState),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = runtimeBody(status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    RuntimeStateRow(
                        label = "Lifecycle",
                        value = status.lifecycleState.displayName(),
                    )
                    RuntimeStateRow(
                        label = "Payload",
                        value = if (status.payloadAvailable) "Available" else "Missing",
                    )
                    status.lastErrorMessage?.let { errorMessage ->
                        RuntimeStateRow(
                            label = "Last error",
                            value = errorMessage,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = diagnostics.headline.ifBlank { "Diagnostics" },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (diagnostics.details.isEmpty()) {
                        Text(
                            text = "No diagnostic details are currently available.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        diagnostics.details.forEach { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStart,
                    enabled = status.lifecycleState != HostLifecycleState.Starting &&
                        status.lifecycleState != HostLifecycleState.Running,
                ) {
                    Text(text = "Start")
                }
                OutlinedButton(
                    onClick = onStop,
                    enabled = status.lifecycleState == HostLifecycleState.Starting ||
                        status.lifecycleState == HostLifecycleState.Running ||
                        status.lifecycleState == HostLifecycleState.Recovering,
                ) {
                    Text(text = "Stop")
                }
            }

            OutlinedButton(onClick = onViewDiagnostics) {
                Text(text = "View diagnostics")
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun RuntimeStateRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun runtimeHeadline(state: HostLifecycleState): String = when (state) {
    HostLifecycleState.Running -> "Runtime is running"
    HostLifecycleState.Starting -> "Runtime is starting"
    HostLifecycleState.Recovering -> "Runtime is recovering"
    HostLifecycleState.PermissionRequired -> "Permissions are required"
    HostLifecycleState.ConfigurationInvalid -> "Configuration needs attention"
    HostLifecycleState.InstallRequired -> "Install required"
    HostLifecycleState.Degraded -> "Runtime is degraded"
    HostLifecycleState.Error -> "Runtime encountered an error"
    HostLifecycleState.Stopped -> "Runtime is stopped"
}

private fun runtimeBody(status: RuntimeStatus): String = when (status.lifecycleState) {
    HostLifecycleState.Running ->
        "The bundled payload is available and the host is ready for runtime traffic."
    HostLifecycleState.Starting ->
        "The host is starting up and preparing its runtime environment."
    HostLifecycleState.Recovering ->
        "The host is recovering from an interruption and will retry its setup."
    HostLifecycleState.PermissionRequired ->
        "At least one required Android capability is still missing."
    HostLifecycleState.ConfigurationInvalid ->
        "The saved provider configuration is incomplete or invalid."
    HostLifecycleState.InstallRequired ->
        "A bundled payload has not been detected yet."
    HostLifecycleState.Degraded ->
        "The runtime is partially functional but needs attention."
    HostLifecycleState.Error ->
        status.lastErrorMessage?.let { "The last startup attempt failed: $it" }
            ?: "The runtime reported an unrecoverable error."
    HostLifecycleState.Stopped ->
        "The host is idle and can be started when you are ready."
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

@Preview(showBackground = true)
@Composable
private fun RuntimeScreenPreview() {
    MaterialTheme {
        Surface {
            RuntimeScreen(
                status = RuntimeStatus(
                    lifecycleState = HostLifecycleState.Running,
                    payloadAvailable = true,
                ),
                diagnostics = DiagnosticsSummary(
                    headline = "Runtime running",
                    details = listOf(
                        "Bundled payload manifest was loaded successfully.",
                        "The bridge is waiting for the OpenClaw client to connect.",
                    ),
                ),
                onStart = {},
                onStop = {},
                onViewDiagnostics = {},
            )
        }
    }
}
