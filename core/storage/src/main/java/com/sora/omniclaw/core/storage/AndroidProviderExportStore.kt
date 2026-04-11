package com.sora.omniclaw.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.ProviderRuntimeExport
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.providerExportDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "provider_export"
)

class AndroidProviderExportStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) : ProviderExportStore {
    constructor(context: Context) : this(dataStore = context.providerExportDataStore)

    override suspend fun readExport(): ProviderRuntimeExport? = observeExport().first()

    override fun observeExport(): Flow<ProviderRuntimeExport?> {
        return dataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map { preferences -> preferences.toProviderRuntimeExportOrNull() }
    }

    override fun observeExportReadiness(): Flow<Boolean> {
        return observeExport()
            .map { export -> export?.isReady == true }
            .distinctUntilChanged()
    }

    override suspend fun writeExport(export: ProviderRuntimeExport): HostResult<Unit> {
        return runCatching {
            dataStore.edit { preferences ->
                preferences[Keys.SchemaVersion] = export.schemaVersion
                preferences[Keys.ProviderId] = export.providerId
                preferences[Keys.BaseUrl] = export.baseUrl
                preferences[Keys.ModelName] = export.modelName
            }
        }.fold(
            onSuccess = { HostResult.Success(Unit) },
            onFailure = { throwable ->
                HostResult.Failure(
                    HostError(
                        category = HostErrorCategory.Storage,
                        message = throwable.message ?: "Failed to persist provider export.",
                        recoverable = true,
                    )
                )
            }
        )
    }

    override suspend fun clearExport(): HostResult<Unit> {
        return runCatching {
            dataStore.edit { preferences ->
                preferences.remove(Keys.SchemaVersion)
                preferences.remove(Keys.ProviderId)
                preferences.remove(Keys.BaseUrl)
                preferences.remove(Keys.ModelName)
                preferences.remove(Keys.LegacyApiKey)
            }
        }.fold(
            onSuccess = { HostResult.Success(Unit) },
            onFailure = { throwable ->
                HostResult.Failure(
                    HostError(
                        category = HostErrorCategory.Storage,
                        message = throwable.message ?: "Failed to clear provider export.",
                        recoverable = true,
                    )
                )
            }
        )
    }

    private fun Preferences.toProviderRuntimeExportOrNull(): ProviderRuntimeExport? {
        val export = ProviderRuntimeExport(
            schemaVersion = this[Keys.SchemaVersion] ?: 0,
            providerId = this[Keys.ProviderId].orEmpty(),
            baseUrl = this[Keys.BaseUrl].orEmpty(),
            modelName = this[Keys.ModelName].orEmpty(),
        )

        return export.takeIf { it.isReady }
    }

    private object Keys {
        val SchemaVersion = intPreferencesKey("schema_version")
        val ProviderId = stringPreferencesKey("provider_id")
        val BaseUrl = stringPreferencesKey("base_url")
        val ModelName = stringPreferencesKey("model_name")
        val LegacyApiKey = stringPreferencesKey("api_key")
    }
}
