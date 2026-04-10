package com.sora.omniclaw.feature.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.sora.omniclaw.core.model.PermissionGrantState
import com.sora.omniclaw.core.model.PermissionStatus
import com.sora.omniclaw.core.model.PermissionSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    summary: PermissionSummary,
    onPermissionClick: (PermissionStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requiredPermissions = summary.permissions.filter { it.required }
    val optionalPermissions = summary.permissions.filterNot { it.required }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Permissions") },
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (summary.allRequiredGranted) {
                            "All required permissions granted"
                        } else {
                            "Some required permissions are missing"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Permissions are grouped by requirement so you can review each capability one at a time.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            PermissionSection(
                title = "Required",
                emptyText = "No required permissions are currently listed.",
                permissions = requiredPermissions,
                onPermissionClick = onPermissionClick,
            )

            PermissionSection(
                title = "Optional",
                emptyText = "No optional permissions are currently listed.",
                permissions = optionalPermissions,
                onPermissionClick = onPermissionClick,
            )
        }
    }
}

@Composable
private fun PermissionSection(
    title: String,
    emptyText: String,
    permissions: List<PermissionStatus>,
    onPermissionClick: (PermissionStatus) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        if (permissions.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            permissions.forEach { permission ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = permission.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            PermissionStateLabel(permission.state)
                        }
                        Text(
                            text = when (permission.state) {
                                PermissionGrantState.Granted -> "Granted and ready."
                                PermissionGrantState.Required -> "Required before the host can fully run."
                                PermissionGrantState.Optional -> "Helpful, but not required for launch."
                                PermissionGrantState.Unavailable -> "Not available on this device."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { onPermissionClick(permission) }) {
                            Text(text = "Review")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStateLabel(state: PermissionGrantState) {
    Surface(
        color = when (state) {
            PermissionGrantState.Granted -> MaterialTheme.colorScheme.secondaryContainer
            PermissionGrantState.Required -> MaterialTheme.colorScheme.errorContainer
            PermissionGrantState.Optional -> MaterialTheme.colorScheme.primaryContainer
            PermissionGrantState.Unavailable -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when (state) {
            PermissionGrantState.Granted -> MaterialTheme.colorScheme.onSecondaryContainer
            PermissionGrantState.Required -> MaterialTheme.colorScheme.onErrorContainer
            PermissionGrantState.Optional -> MaterialTheme.colorScheme.onPrimaryContainer
            PermissionGrantState.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = state.displayName(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun PermissionGrantState.displayName(): String = when (this) {
    PermissionGrantState.Granted -> "Granted"
    PermissionGrantState.Required -> "Required"
    PermissionGrantState.Optional -> "Optional"
    PermissionGrantState.Unavailable -> "Unavailable"
}

@Preview(showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
    MaterialTheme {
        Surface {
            PermissionsScreen(
                summary = PermissionSummary(
                    permissions = listOf(
                        PermissionStatus(
                            id = "notifications",
                            label = "Notifications",
                            state = PermissionGrantState.Required,
                            required = true,
                        ),
                        PermissionStatus(
                            id = "battery",
                            label = "Ignore battery optimization",
                            state = PermissionGrantState.Optional,
                            required = false,
                        ),
                        PermissionStatus(
                            id = "clipboard",
                            label = "Clipboard access",
                            state = PermissionGrantState.Granted,
                            required = false,
                        ),
                    ),
                ),
                onPermissionClick = {},
            )
        }
    }
}
