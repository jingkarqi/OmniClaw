package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal class RuntimeInstallStateStore(
    stateFile: File,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) {
    private val stateFile = stateFile.absoluteFile

    fun read(): HostResult<RuntimeInstallState> {
        if (!stateFile.exists()) {
            return HostResult.Success(RuntimeInstallState.NotInstalled)
        }

        return runCatching {
            decode(stateFile.readText())
        }.getOrElse {
            HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Storage,
                    message = "Failed to read runtime install state.",
                    recoverable = true,
                )
            )
        }
    }

    fun setInstalling(
        targetRuntimeVersion: String,
        startedAtEpochMs: Long,
        updatedAtEpochMs: Long,
        progress: RuntimeInstallProgress? = null,
    ): HostResult<RuntimeInstallState> {
        return write(
            RuntimeInstallState.Installing(
                targetRuntimeVersion = targetRuntimeVersion,
                startedAtEpochMs = startedAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
                progress = progress,
            )
        )
    }

    fun setInstalled(
        runtimeVersion: String,
        installedAtEpochMs: Long,
    ): HostResult<RuntimeInstallState> {
        return write(
            RuntimeInstallState.Installed(
                runtimeVersion = runtimeVersion,
                installedAtEpochMs = installedAtEpochMs,
            )
        )
    }

    fun setCorrupt(
        reason: String,
        detectedAtEpochMs: Long = nowEpochMs(),
        lastKnownRuntimeVersion: String? = null,
    ): HostResult<RuntimeInstallState> {
        return write(
            RuntimeInstallState.Corrupt(
                reason = reason,
                detectedAtEpochMs = detectedAtEpochMs,
                lastKnownRuntimeVersion = lastKnownRuntimeVersion,
            )
        )
    }

    fun setUpgradeRequired(
        installedRuntimeVersion: String,
        requiredRuntimeVersion: String,
        detectedAtEpochMs: Long,
    ): HostResult<RuntimeInstallState> {
        return write(
            RuntimeInstallState.UpgradeRequired(
                installedRuntimeVersion = installedRuntimeVersion,
                requiredRuntimeVersion = requiredRuntimeVersion,
                detectedAtEpochMs = detectedAtEpochMs,
            )
        )
    }

    private fun write(state: RuntimeInstallState): HostResult<RuntimeInstallState> {
        return runCatching {
            ensureParentDirectory()
            stateFile.writeText(json.encodeToString(JsonObject.serializer(), encode(state)))
            HostResult.Success(state)
        }.getOrElse {
            HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Storage,
                    message = "Failed to persist runtime install state.",
                    recoverable = true,
                )
            )
        }
    }

    private fun ensureParentDirectory() {
        val parent = stateFile.parentFile ?: return
        if (parent.exists()) {
            if (!parent.isDirectory) {
                throw IOException("State parent is not a directory.")
            }
            return
        }

        if (!parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Failed to create runtime state directory.")
        }
    }

    private fun decode(serializedState: String): HostResult<RuntimeInstallState> {
        val root = runCatching {
            json.parseToJsonElement(serializedState).jsonObject
        }.getOrElse {
            return HostResult.Success(malformedState())
        }

        return HostResult.Success(
            when (val kind = root.string("status")) {
                STATUS_NOT_INSTALLED -> RuntimeInstallState.NotInstalled
                STATUS_INSTALLING -> decodeInstalling(root) ?: malformedState()
                STATUS_INSTALLED -> decodeInstalled(root) ?: malformedState()
                STATUS_CORRUPT -> decodeCorrupt(root) ?: malformedState()
                STATUS_UPGRADE_REQUIRED -> decodeUpgradeRequired(root) ?: malformedState()
                else -> malformedState(kind)
            }
        )
    }

    private fun decodeInstalling(root: JsonObject): RuntimeInstallState.Installing? {
        val progress = root.jsonObjectOrNull("progress")?.let { decodeProgress(it) ?: return null }
        return RuntimeInstallState.Installing(
            targetRuntimeVersion = root.string("targetRuntimeVersion") ?: return null,
            startedAtEpochMs = root.long("startedAtEpochMs") ?: return null,
            updatedAtEpochMs = root.long("updatedAtEpochMs") ?: return null,
            progress = progress,
        )
    }

    private fun decodeInstalled(root: JsonObject): RuntimeInstallState.Installed? {
        return RuntimeInstallState.Installed(
            runtimeVersion = root.string("runtimeVersion") ?: return null,
            installedAtEpochMs = root.long("installedAtEpochMs") ?: return null,
        )
    }

    private fun decodeCorrupt(root: JsonObject): RuntimeInstallState.Corrupt? {
        return RuntimeInstallState.Corrupt(
            reason = root.string("reason") ?: return null,
            detectedAtEpochMs = root.long("detectedAtEpochMs") ?: return null,
            lastKnownRuntimeVersion = root.string("lastKnownRuntimeVersion"),
        )
    }

    private fun decodeUpgradeRequired(root: JsonObject): RuntimeInstallState.UpgradeRequired? {
        return RuntimeInstallState.UpgradeRequired(
            installedRuntimeVersion = root.string("installedRuntimeVersion") ?: return null,
            requiredRuntimeVersion = root.string("requiredRuntimeVersion") ?: return null,
            detectedAtEpochMs = root.long("detectedAtEpochMs") ?: return null,
        )
    }

    private fun decodeProgress(root: JsonObject): RuntimeInstallProgress? {
        return RuntimeInstallProgress(
            step = root.string("step") ?: return null,
            completedSteps = root.int("completedSteps") ?: return null,
            totalSteps = root.int("totalSteps") ?: return null,
            detail = root.string("detail"),
        )
    }

    private fun encode(state: RuntimeInstallState): JsonObject {
        return when (state) {
            RuntimeInstallState.NotInstalled -> buildJsonObject {
                put("status", STATUS_NOT_INSTALLED)
            }

            is RuntimeInstallState.Installing -> buildJsonObject {
                put("status", STATUS_INSTALLING)
                put("targetRuntimeVersion", state.targetRuntimeVersion)
                put("startedAtEpochMs", state.startedAtEpochMs)
                put("updatedAtEpochMs", state.updatedAtEpochMs)
                state.progress?.let { progress ->
                    put(
                        "progress",
                        buildJsonObject {
                            put("step", progress.step)
                            put("completedSteps", progress.completedSteps)
                            put("totalSteps", progress.totalSteps)
                            progress.detail?.let { put("detail", it) }
                        }
                    )
                }
            }

            is RuntimeInstallState.Installed -> buildJsonObject {
                put("status", STATUS_INSTALLED)
                put("runtimeVersion", state.runtimeVersion)
                put("installedAtEpochMs", state.installedAtEpochMs)
            }

            is RuntimeInstallState.Corrupt -> buildJsonObject {
                put("status", STATUS_CORRUPT)
                put("reason", state.reason)
                put("detectedAtEpochMs", state.detectedAtEpochMs)
                state.lastKnownRuntimeVersion?.let { put("lastKnownRuntimeVersion", it) }
            }

            is RuntimeInstallState.UpgradeRequired -> buildJsonObject {
                put("status", STATUS_UPGRADE_REQUIRED)
                put("installedRuntimeVersion", state.installedRuntimeVersion)
                put("requiredRuntimeVersion", state.requiredRuntimeVersion)
                put("detectedAtEpochMs", state.detectedAtEpochMs)
            }
        }
    }

    private fun malformedState(status: String? = null): RuntimeInstallState.Corrupt {
        val detail = if (status == null) {
            "Runtime install state file is malformed."
        } else {
            "Runtime install state file is malformed."
        }

        return RuntimeInstallState.Corrupt(
            reason = detail,
            detectedAtEpochMs = nowEpochMs(),
            lastKnownRuntimeVersion = null,
        )
    }

    private fun JsonObject.jsonObjectOrNull(key: String): JsonObject? {
        return this[key]?.let { element ->
            runCatching { element.jsonObject }.getOrNull()
        }
    }

    private fun JsonObject.string(key: String): String? {
        return this[key]?.let { element ->
            runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
        }
    }

    private fun JsonObject.long(key: String): Long? {
        return this[key]?.let { element ->
            runCatching { element.jsonPrimitive.longOrNull }.getOrNull()
        }
    }

    private fun JsonObject.int(key: String): Int? {
        return this[key]?.let { element ->
            runCatching { element.jsonPrimitive.intOrNull }.getOrNull()
        }
    }

    private companion object {
        const val STATUS_NOT_INSTALLED = "not-installed"
        const val STATUS_INSTALLING = "installing"
        const val STATUS_INSTALLED = "installed"
        const val STATUS_CORRUPT = "corrupt"
        const val STATUS_UPGRADE_REQUIRED = "upgrade-required"
    }
}
