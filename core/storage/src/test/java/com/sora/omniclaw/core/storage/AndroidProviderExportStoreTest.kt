package com.sora.omniclaw.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.common.errorOrNull
import com.sora.omniclaw.core.common.getOrNull
import com.sora.omniclaw.core.common.isFailure
import com.sora.omniclaw.core.common.isSuccess
import com.sora.omniclaw.core.model.ProviderRuntimeExport
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidProviderExportStoreTest {
    @Test
    fun `write persists a complete export for later reads`() {
        withStore { store, dataStore ->
            val export = readyExport()

            val result = runBlocking { store.writeExport(export) }
            val persistedPreferences = runBlocking { dataStore.data.first().asMap() }

            assertUnitSuccess(result)
            assertEquals(export, runBlocking { store.readExport() })
            assertEquals(export, runBlocking { store.observeExport().first() })
            assertEquals(
                setOf(
                    intPreferencesKey("schema_version"),
                    stringPreferencesKey("provider_id"),
                    stringPreferencesKey("base_url"),
                    stringPreferencesKey("model_name"),
                ),
                persistedPreferences.keys,
            )
            assertEquals(
                ProviderRuntimeExport.CURRENT_SCHEMA_VERSION,
                persistedPreferences[intPreferencesKey("schema_version")],
            )
            assertEquals("openai", persistedPreferences[stringPreferencesKey("provider_id")])
            assertEquals(
                "https://api.openai.example/v1",
                persistedPreferences[stringPreferencesKey("base_url")],
            )
            assertEquals("gpt-4.1-mini", persistedPreferences[stringPreferencesKey("model_name")])
            assertFalse(persistedPreferences.containsKey(stringPreferencesKey("api_key")))
        }
    }

    @Test
    fun `observe export readiness reflects missing written and cleared values`() {
        val store = AndroidProviderExportStore(dataStore = InMemoryDataStore())

        assertFalse(runBlocking { store.observeExportReadiness().first() })
        assertUnitSuccess(runBlocking { store.writeExport(readyExport()) })
        assertTrue(runBlocking { store.observeExportReadiness().first() })
        assertUnitSuccess(runBlocking { store.clearExport() })
        assertFalse(runBlocking { store.observeExportReadiness().first() })
    }

    @Test
    fun `incomplete exports are surfaced as missing`() {
        withStore { store ->
            val incompleteExport = readyExport(baseUrl = "")

            assertUnitSuccess(runBlocking { store.writeExport(incompleteExport) })

            assertNull(runBlocking { store.readExport() })
            assertNull(runBlocking { store.observeExport().first() })
            assertFalse(runBlocking { store.observeExportReadiness().first() })
        }
    }

    @Test
    fun `clear export removes legacy api key data`() {
        val dataStore = InMemoryDataStore()
        val store = AndroidProviderExportStore(dataStore = dataStore)

        runBlocking {
            dataStore.updateData {
                mutablePreferencesOf(
                    intPreferencesKey("schema_version") to
                        ProviderRuntimeExport.CURRENT_SCHEMA_VERSION,
                    stringPreferencesKey("provider_id") to "openai",
                    stringPreferencesKey("base_url") to "https://api.openai.example/v1",
                    stringPreferencesKey("model_name") to "gpt-4.1-mini",
                    stringPreferencesKey("api_key") to "legacy-secret",
                )
            }
        }

        assertTrue(
            runBlocking {
                dataStore.data.first().asMap().containsKey(stringPreferencesKey("api_key"))
            }
        )

        assertUnitSuccess(runBlocking { store.clearExport() })

        val persistedPreferences = runBlocking { dataStore.data.first().asMap() }
        assertFalse(persistedPreferences.containsKey(stringPreferencesKey("api_key")))
        assertTrue(persistedPreferences.isEmpty())
    }

    @Test
    fun `io failures become missing reads and storage host results`() {
        val readStore = AndroidProviderExportStore(
            dataStore = FailingDataStore(readFailure = IOException("read failed"))
        )

        assertNull(runBlocking { readStore.readExport() })
        assertNull(runBlocking { readStore.observeExport().first() })
        assertFalse(runBlocking { readStore.observeExportReadiness().first() })

        val writeStore = AndroidProviderExportStore(
            dataStore = FailingDataStore(writeFailure = IOException("write failed"))
        )

        assertStorageFailure(runBlocking { writeStore.writeExport(readyExport()) })
        assertStorageFailure(runBlocking { writeStore.clearExport() })
    }

    private fun withStore(block: (AndroidProviderExportStore) -> Unit) {
        withStore { store, _ -> block(store) }
    }

    private fun withStore(block: (AndroidProviderExportStore, DataStore<Preferences>) -> Unit) {
        val tempDir = createTempDirectory(prefix = "android-provider-export-store-")
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempDir.resolve("provider_export.preferences_pb").toFile() },
        )

        try {
            block(AndroidProviderExportStore(dataStore = dataStore), dataStore)
        } finally {
            scope.cancel()
            dispatcher.close()
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun readyExport(
        providerId: String = "openai",
        baseUrl: String = "https://api.openai.example/v1",
        modelName: String = "gpt-4.1-mini",
    ): ProviderRuntimeExport {
        return ProviderRuntimeExport(
            providerId = providerId,
            baseUrl = baseUrl,
            modelName = modelName,
        )
    }

    private fun assertUnitSuccess(result: HostResult<Unit>) {
        assertTrue("Expected success but was $result", result.isSuccess)
        assertEquals(Unit, result.getOrNull())
    }

    private fun assertStorageFailure(result: HostResult<Unit>) {
        assertTrue(result.isFailure)
        val error = result.errorOrNull()
        assertEquals(HostErrorCategory.Storage, error?.category)
        assertTrue(error?.recoverable == true)
    }

    private class FailingDataStore(
        private val readFailure: Throwable? = null,
        private val writeFailure: Throwable? = null,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            readFailure?.let { throw it }
            emit(emptyPreferences())
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writeFailure?.let { throw it }
            return transform(emptyPreferences())
        }
    }

    private class InMemoryDataStore(
        initialPreferences: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updatedPreferences = transform(state.value)
            state.value = updatedPreferences
            return updatedPreferences
        }
    }
}
