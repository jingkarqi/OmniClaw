package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal class RuntimeLaunchStateStore(
    stateFile: File,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) {
    private val stateFile = stateFile.absoluteFile

    fun read(): HostResult<RuntimeLaunchState> {
        if (!stateFile.exists()) {
            return HostResult.Success(RuntimeLaunchState.Stopped())
        }

        return runCatching {
            decode(stateFile.readText())
        }.getOrElse {
            HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Storage,
                    message = "Failed to read runtime launch state.",
                    recoverable = true,
                )
            )
        }
    }

    fun setStopped(
        runtimeVersion: String? = null,
        command: List<String> = emptyList(),
        stdoutPath: String? = null,
        stderrPath: String? = null,
        startedAtEpochMs: Long? = null,
        stoppedAtEpochMs: Long? = null,
        exitCode: Int? = null,
        lastError: String? = null,
    ): HostResult<RuntimeLaunchState> {
        return write(
            RuntimeLaunchState.Stopped(
                runtimeVersion = runtimeVersion,
                command = command,
                stdoutPath = stdoutPath,
                stderrPath = stderrPath,
                startedAtEpochMs = startedAtEpochMs,
                stoppedAtEpochMs = stoppedAtEpochMs,
                exitCode = exitCode,
                lastError = lastError,
            )
        )
    }

    fun setStarting(
        runtimeVersion: String,
        command: List<String>,
        stdoutPath: String,
        stderrPath: String,
        startedAtEpochMs: Long,
    ): HostResult<RuntimeLaunchState> {
        return write(
            RuntimeLaunchState.Starting(
                runtimeVersion = runtimeVersion,
                command = command,
                stdoutPath = stdoutPath,
                stderrPath = stderrPath,
                startedAtEpochMs = startedAtEpochMs,
            )
        )
    }

    fun setRunning(
        runtimeVersion: String,
        command: List<String>,
        stdoutPath: String,
        stderrPath: String,
        startedAtEpochMs: Long,
        pid: Long? = null,
    ): HostResult<RuntimeLaunchState> {
        return write(
            RuntimeLaunchState.Running(
                runtimeVersion = runtimeVersion,
                command = command,
                stdoutPath = stdoutPath,
                stderrPath = stderrPath,
                startedAtEpochMs = startedAtEpochMs,
                pid = pid,
            )
        )
    }

    fun setFailed(
        runtimeVersion: String? = null,
        command: List<String> = emptyList(),
        stdoutPath: String? = null,
        stderrPath: String? = null,
        startedAtEpochMs: Long? = null,
        failedAtEpochMs: Long = nowEpochMs(),
        lastError: String,
    ): HostResult<RuntimeLaunchState> {
        return write(
            RuntimeLaunchState.Failed(
                runtimeVersion = runtimeVersion,
                command = command,
                stdoutPath = stdoutPath,
                stderrPath = stderrPath,
                startedAtEpochMs = startedAtEpochMs,
                failedAtEpochMs = failedAtEpochMs,
                lastError = lastError,
            )
        )
    }

    private fun write(state: RuntimeLaunchState): HostResult<RuntimeLaunchState> {
        return runCatching {
            ensureParentDirectory()
            stateFile.writeText(json.encodeToString(JsonObject.serializer(), encode(state)))
            HostResult.Success(state)
        }.getOrElse {
            HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Storage,
                    message = "Failed to persist runtime launch state.",
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

    private fun decode(serializedState: String): HostResult<RuntimeLaunchState> {
        val root = runCatching {
            json.parseToJsonElement(serializedState).jsonObject
        }.getOrElse {
            return HostResult.Success(malformedState())
        }

        return HostResult.Success(
            when (root.string("status")) {
                STATUS_STOPPED -> decodeStopped(root) ?: malformedState()
                STATUS_STARTING -> decodeStarting(root) ?: malformedState()
                STATUS_RUNNING -> decodeRunning(root) ?: malformedState()
                STATUS_FAILED -> decodeFailed(root) ?: malformedState()
                else -> malformedState()
            }
        )
    }

    private fun decodeStopped(root: JsonObject): RuntimeLaunchState.Stopped? {
        val command = root.stringListOrDefault("command", emptyList()) ?: return null
        return RuntimeLaunchState.Stopped(
            runtimeVersion = root.string("runtimeVersion"),
            command = command,
            stdoutPath = root.string("stdoutPath"),
            stderrPath = root.string("stderrPath"),
            startedAtEpochMs = root.long("startedAtEpochMs"),
            stoppedAtEpochMs = root.long("stoppedAtEpochMs"),
            exitCode = root.int("exitCode"),
            lastError = root.string("lastError"),
        )
    }

    private fun decodeStarting(root: JsonObject): RuntimeLaunchState.Starting? {
        return RuntimeLaunchState.Starting(
            runtimeVersion = root.string("runtimeVersion") ?: return null,
            command = root.stringList("command") ?: return null,
            stdoutPath = root.string("stdoutPath") ?: return null,
            stderrPath = root.string("stderrPath") ?: return null,
            startedAtEpochMs = root.long("startedAtEpochMs") ?: return null,
        )
    }

    private fun decodeRunning(root: JsonObject): RuntimeLaunchState.Running? {
        return RuntimeLaunchState.Running(
            runtimeVersion = root.string("runtimeVersion") ?: return null,
            command = root.stringList("command") ?: return null,
            stdoutPath = root.string("stdoutPath") ?: return null,
            stderrPath = root.string("stderrPath") ?: return null,
            startedAtEpochMs = root.long("startedAtEpochMs") ?: return null,
            pid = root.long("pid"),
        )
    }

    private fun decodeFailed(root: JsonObject): RuntimeLaunchState.Failed? {
        val command = root.stringListOrDefault("command", emptyList()) ?: return null
        return RuntimeLaunchState.Failed(
            runtimeVersion = root.string("runtimeVersion"),
            command = command,
            stdoutPath = root.string("stdoutPath"),
            stderrPath = root.string("stderrPath"),
            startedAtEpochMs = root.long("startedAtEpochMs"),
            failedAtEpochMs = root.long("failedAtEpochMs") ?: return null,
            lastError = root.string("lastError") ?: return null,
        )
    }

    private fun encode(state: RuntimeLaunchState): JsonObject {
        return when (state) {
            is RuntimeLaunchState.Stopped -> buildJsonObject {
                put("status", STATUS_STOPPED)
                state.runtimeVersion?.let { put("runtimeVersion", it) }
                put("command", state.command.toJsonArray())
                state.stdoutPath?.let { put("stdoutPath", it) }
                state.stderrPath?.let { put("stderrPath", it) }
                state.startedAtEpochMs?.let { put("startedAtEpochMs", it) }
                state.stoppedAtEpochMs?.let { put("stoppedAtEpochMs", it) }
                state.exitCode?.let { put("exitCode", it) }
                state.lastError?.let { put("lastError", it) }
            }

            is RuntimeLaunchState.Starting -> buildJsonObject {
                put("status", STATUS_STARTING)
                put("runtimeVersion", state.runtimeVersion)
                put("command", state.command.toJsonArray())
                put("stdoutPath", state.stdoutPath)
                put("stderrPath", state.stderrPath)
                put("startedAtEpochMs", state.startedAtEpochMs)
            }

            is RuntimeLaunchState.Running -> buildJsonObject {
                put("status", STATUS_RUNNING)
                put("runtimeVersion", state.runtimeVersion)
                put("command", state.command.toJsonArray())
                put("stdoutPath", state.stdoutPath)
                put("stderrPath", state.stderrPath)
                put("startedAtEpochMs", state.startedAtEpochMs)
                state.pid?.let { put("pid", it) }
            }

            is RuntimeLaunchState.Failed -> buildJsonObject {
                put("status", STATUS_FAILED)
                state.runtimeVersion?.let { put("runtimeVersion", it) }
                put("command", state.command.toJsonArray())
                state.stdoutPath?.let { put("stdoutPath", it) }
                state.stderrPath?.let { put("stderrPath", it) }
                state.startedAtEpochMs?.let { put("startedAtEpochMs", it) }
                put("failedAtEpochMs", state.failedAtEpochMs)
                put("lastError", state.lastError)
            }
        }
    }

    private fun malformedState(): RuntimeLaunchState.Failed {
        return RuntimeLaunchState.Failed(
            failedAtEpochMs = nowEpochMs(),
            lastError = "Runtime launch state file is malformed.",
        )
    }

    private fun JsonObject.stringListOrDefault(
        key: String,
        defaultValue: List<String>,
    ): List<String>? {
        return if (containsKey(key)) {
            stringList(key)
        } else {
            defaultValue
        }
    }

    private fun JsonObject.stringList(key: String): List<String>? {
        val array = this[key]?.let { element ->
            runCatching { element.jsonArray }.getOrNull()
        } ?: return null

        return array.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull
        }.takeIf { it.size == array.size }
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

    private fun List<String>.toJsonArray(): JsonArray {
        return JsonArray(map(::JsonPrimitive))
    }

    companion object {
        const val STATE_FILE_RELATIVE_PATH: String = "state/launch-state.json"

        fun stateFileFor(workspaceRoot: File): File {
            return File(workspaceRoot.absoluteFile, STATE_FILE_RELATIVE_PATH).absoluteFile
        }

        private const val STATUS_STOPPED = "stopped"
        private const val STATUS_STARTING = "starting"
        private const val STATUS_RUNNING = "running"
        private const val STATUS_FAILED = "failed"
    }
}
