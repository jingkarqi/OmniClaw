package com.sora.omniclaw.feature.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sora.omniclaw.core.model.ProviderConfigDraft

object ProviderScreenTags {
    const val ProviderTypeField = "provider:type"
    const val BaseUrlField = "provider:base_url"
    const val ModelNameField = "provider:model_name"
    const val ApiKeyField = "provider:api_key"
    const val SaveButton = "provider:save"
    const val ResetButton = "provider:reset"
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProviderScreen(
    draft: ProviderConfigDraft,
    onDraftChange: (ProviderConfigDraft) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = "Provider") })
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
                text = if (draft.hasStoredApiKey) {
                    "A provider key is already stored. Enter a new key to replace it."
                } else {
                    "Edit the provider draft used by the runtime."
                },
                style = MaterialTheme.typography.bodyLarge
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProviderField(
                        value = draft.providerId,
                        label = "Provider type",
                        tag = ProviderScreenTags.ProviderTypeField,
                        onValueChange = { onDraftChange(draft.copy(providerId = it)) }
                    )
                    ProviderField(
                        value = draft.baseUrl,
                        label = "Base URL",
                        tag = ProviderScreenTags.BaseUrlField,
                        onValueChange = { onDraftChange(draft.copy(baseUrl = it)) }
                    )
                    ProviderField(
                        value = draft.modelName,
                        label = "Model name",
                        tag = ProviderScreenTags.ModelNameField,
                        onValueChange = { onDraftChange(draft.copy(modelName = it)) }
                    )
                    ProviderField(
                        value = draft.apiKey,
                        label = "API key",
                        tag = ProviderScreenTags.ApiKeyField,
                        onValueChange = { onDraftChange(draft.copy(apiKey = it)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSave,
                    enabled = draft.isReady,
                    modifier = Modifier.testTag(ProviderScreenTags.SaveButton)
                ) {
                    Text(text = "Save provider")
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.testTag(ProviderScreenTags.ResetButton)
                ) {
                    Text(text = "Reset draft")
                }
            }
        }
    }
}

@Composable
private fun ProviderField(
    value: String,
    label: String,
    tag: String,
    onValueChange: (String) -> Unit,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions
    )
}

@Preview(showBackground = true)
@Composable
private fun ProviderScreenPreview() {
    Surface {
        ProviderScreen(
            draft = ProviderConfigDraft(
                providerId = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                modelName = "gpt-4o-mini",
                apiKey = "sk-***",
            ),
            onDraftChange = {},
            onSave = {},
            onReset = {}
        )
    }
}
