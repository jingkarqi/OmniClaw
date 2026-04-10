package com.sora.omniclaw.feature.provider

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.sora.omniclaw.core.model.ProviderConfigDraft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProviderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun providerScreen_editsFieldsAndSaves() {
        var saved = 0
        var lastBaseUrl = ""

        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    ProviderConfigDraft(
                        providerId = "OpenAI",
                        baseUrl = "https://api.openai.com/v1",
                        modelName = "gpt-4o-mini",
                        apiKey = "sk-test",
                    )
                )
            }

            ProviderScreen(
                draft = uiState,
                onDraftChange = {
                    lastBaseUrl = it.baseUrl
                    uiState = it
                },
                onSave = { saved++ },
                onReset = {}
            )
        }

        composeRule.onNodeWithTag(ProviderScreenTags.BaseUrlField)
            .assertIsDisplayed()
            .performTextReplacement("https://example.com/v1")

        composeRule.onNodeWithTag(ProviderScreenTags.SaveButton).performClick()

        composeRule.runOnIdle {
            assertEquals(1, saved)
            assertEquals("https://example.com/v1", lastBaseUrl)
        }
    }
}
