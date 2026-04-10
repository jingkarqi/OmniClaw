package com.sora.omniclaw.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.ProviderConfigDraft
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.providerConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "provider_config"
)

class AndroidProviderConfigStore(
    private val context: Context,
) : ProviderConfigStore {
    override fun observeDraft(): Flow<ProviderConfigDraft> {
        return context.providerConfigDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { preferences ->
                ProviderConfigDraft(
                    providerId = preferences[Keys.ProviderId].orEmpty(),
                    baseUrl = preferences[Keys.BaseUrl].orEmpty(),
                    modelName = preferences[Keys.ModelName].orEmpty(),
                    hasStoredApiKey = preferences[Keys.HasStoredApiKey] ?: false,
                )
            }
    }

    override suspend fun saveDraft(draft: ProviderConfigDraft): HostResult<Unit> {
        return runCatching {
            context.providerConfigDataStore.edit { preferences ->
                val persistedDraft = draft.withoutSecret()
                preferences[Keys.ProviderId] = persistedDraft.providerId
                preferences[Keys.BaseUrl] = persistedDraft.baseUrl
                preferences[Keys.ModelName] = persistedDraft.modelName
                preferences[Keys.HasStoredApiKey] = persistedDraft.hasStoredApiKey
            }
        }.fold(
            onSuccess = { HostResult.Success(Unit) },
            onFailure = { throwable ->
                HostResult.Failure(
                    HostError(
                        category = HostErrorCategory.Storage,
                        message = throwable.message ?: "Failed to persist provider draft.",
                        recoverable = true,
                    )
                )
            }
        )
    }

    private object Keys {
        val ProviderId = stringPreferencesKey("provider_id")
        val BaseUrl = stringPreferencesKey("base_url")
        val ModelName = stringPreferencesKey("model_name")
        val HasStoredApiKey = booleanPreferencesKey("has_stored_api_key")
    }
}
