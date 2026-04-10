package com.sora.omniclaw.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sora.omniclaw.AppGraph
import com.sora.omniclaw.core.model.DiagnosticsSummary
import com.sora.omniclaw.core.model.HostOverview
import com.sora.omniclaw.core.model.PermissionSummary
import com.sora.omniclaw.core.model.ProviderConfigDraft
import com.sora.omniclaw.core.model.RuntimeStatus
import com.sora.omniclaw.feature.home.HomeScreen
import com.sora.omniclaw.feature.permissions.PermissionsScreen
import com.sora.omniclaw.feature.provider.ProviderScreen
import com.sora.omniclaw.feature.runtime.RuntimeScreen
import com.sora.omniclaw.service.host.HostServiceLauncher
import kotlinx.coroutines.launch

@Composable
fun OmniClawNavHost(
    appGraph: AppGraph,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val hostOverview by appGraph.hostOverview.collectAsState(initial = HostOverview())
    val observedProviderDraft by appGraph.providerConfig.collectAsState(initial = ProviderConfigDraft())
    val permissionSummary by appGraph.permissionSummary.collectAsState(initial = PermissionSummary())
    val runtimeStatus by appGraph.runtimeStatus.collectAsState(initial = RuntimeStatus())
    val diagnostics by appGraph.runtimeDiagnostics.collectAsState(initial = DiagnosticsSummary())

    var providerDraft by remember { mutableStateOf(observedProviderDraft) }

    LaunchedEffect(observedProviderDraft) {
        providerDraft = observedProviderDraft
    }

    NavHost(
        navController = navController,
        startDestination = Routes.startDestination,
    ) {
        composable(Routes.Home.route) {
            HomeScreen(
                overview = hostOverview,
                onStartHost = { HostServiceLauncher.start(context) },
                onStopHost = { HostServiceLauncher.stop(context) },
                onOpenProvider = { navController.navigate(Routes.Provider.route) },
                onOpenRuntime = { navController.navigate(Routes.Runtime.route) },
                onOpenPermissions = { navController.navigate(Routes.Permissions.route) },
            )
        }

        composable(Routes.Provider.route) {
            ProviderScreen(
                draft = providerDraft,
                onDraftChange = { providerDraft = it },
                onSave = {
                    coroutineScope.launch {
                        appGraph.saveProviderConfig(providerDraft)
                    }
                },
                onReset = {
                    providerDraft = observedProviderDraft
                },
            )
        }

        composable(Routes.Runtime.route) {
            RuntimeScreen(
                status = runtimeStatus,
                diagnostics = diagnostics,
                onStart = { HostServiceLauncher.start(context) },
                onStop = { HostServiceLauncher.stop(context) },
                onViewDiagnostics = {},
            )
        }

        composable(Routes.Permissions.route) {
            PermissionsScreen(
                summary = permissionSummary,
                onPermissionClick = {},
            )
        }
    }
}
