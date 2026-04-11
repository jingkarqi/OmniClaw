package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.ProviderRuntimeExport
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

internal class RuntimeProviderConfigWriter(
    directories: RuntimeDirectories,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    },
) {
    internal val configFile = File(directories.generatedConfigDir, CONFIG_FILE_NAME).absoluteFile

    private val configState = MutableStateFlow(loadConfigFromDisk())

    fun read(): HostResult<RuntimeProviderConfig?> {
        return HostResult.Success(
            loadConfigFromDisk().also { config ->
                configState.value = config
            }
        )
    }

    fun observe(): Flow<RuntimeProviderConfig?> = configState.asStateFlow()

    fun observeReadiness(): Flow<Boolean> {
        return observe()
            .map { config -> config?.isReady == true }
            .distinctUntilChanged()
    }

    fun write(export: ProviderRuntimeExport, apiKey: String): HostResult<RuntimeProviderConfig> {
        if (!export.isReady) {
            return HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Validation,
                    message = "Runtime provider export is incomplete.",
                    recoverable = true,
                )
            )
        }
        if (apiKey.isBlank()) {
            return HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Validation,
                    message = "Runtime provider secret is missing.",
                    recoverable = true,
                )
            )
        }

        val config = RuntimeProviderConfig.fromExport(export, apiKey)
        return runCatching {
            ensureParentDirectory()
            configFile.writeText(json.encodeToString(RuntimeProviderConfig.serializer(), config))
            configState.value = config
            HostResult.Success(config)
        }.getOrElse {
            HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Storage,
                    message = "Failed to persist runtime provider config.",
                    recoverable = true,
                )
            )
        }
    }

    fun clear(): HostResult<Unit> {
        return runCatching {
            if (configFile.exists() && !configFile.delete()) {
                throw IOException("Failed to delete runtime provider config file.")
            }
            configState.value = null
            HostResult.Success(Unit)
        }.getOrElse {
            HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Storage,
                    message = "Failed to clear runtime provider config.",
                    recoverable = true,
                )
            )
        }
    }

    private fun loadConfigFromDisk(): RuntimeProviderConfig? {
        if (!configFile.exists()) {
            return null
        }

        return runCatching {
            json.decodeFromString(RuntimeProviderConfig.serializer(), configFile.readText())
        }.getOrNull()
    }

    private fun ensureParentDirectory() {
        val parent = configFile.parentFile ?: return
        if (parent.exists()) {
            if (!parent.isDirectory) {
                throw IOException("Provider export parent is not a directory.")
            }
            return
        }

        if (!parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Failed to create runtime provider config directory.")
        }
    }

    private companion object {
        const val CONFIG_FILE_NAME = "openclaw.json5"
    }
}
