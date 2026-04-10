package com.sora.omniclaw.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sora.omniclaw.core.model.BridgeLifecycleState
import com.sora.omniclaw.core.model.BridgeStatus
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.HostOverview
import com.sora.omniclaw.core.model.PermissionGrantState
import com.sora.omniclaw.core.model.PermissionStatus
import com.sora.omniclaw.core.model.PermissionSummary
import com.sora.omniclaw.core.model.RuntimeStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_showsStatusAndPrimaryActions() {
        var startHostClicks = 0
        var providerClicks = 0

        composeRule.setContent {
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
                onStartHost = { startHostClicks++ },
                onStopHost = {},
                onOpenProvider = { providerClicks++ },
                onOpenRuntime = {},
                onOpenPermissions = {}
            )
        }

        composeRule.onNodeWithText("OmniClaw host").assertIsDisplayed()
        composeRule.onNodeWithTag(HomeScreenTags.StartHostButton).performClick()
        composeRule.onNodeWithTag(HomeScreenTags.ProviderButton).performClick()

        composeRule.runOnIdle {
            assertEquals(1, startHostClicks)
            assertEquals(1, providerClicks)
        }
    }
}
