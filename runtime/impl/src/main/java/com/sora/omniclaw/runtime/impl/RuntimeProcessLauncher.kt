package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class RuntimeLaunchRequest(
    val workingDirectory: File,
    val executable: File,
    val args: List<String>,
    val environment: Map<String, String>,
    val stdoutFile: File,
    val stderrFile: File,
)

internal data class RuntimeLaunchedProcess(
    val handle: RuntimeProcessHandle,
    val command: List<String>,
    val stdoutFile: File,
    val stderrFile: File,
)

internal interface RuntimeProcessHandle {
    val pid: Long?

    fun isAlive(): Boolean

    fun exitCodeOrNull(): Int?

    fun stop(timeoutMs: Long): Boolean
}

internal interface RuntimeProcessLauncher {
    fun launch(request: RuntimeLaunchRequest): HostResult<RuntimeLaunchedProcess>
}

internal class ShellRuntimeProcessLauncher : RuntimeProcessLauncher {
    override fun launch(request: RuntimeLaunchRequest): HostResult<RuntimeLaunchedProcess> {
        val command = listOf(request.executable.absolutePath) + request.args
        return runCatching {
            ensureRegularFile(request.executable, "runtime executable")
            ensureDirectory(request.workingDirectory, "runtime working directory")
            ensureParentDirectory(request.stdoutFile)
            ensureParentDirectory(request.stderrFile)

            request.stdoutFile.writeText("")
            request.stderrFile.writeText("")

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(request.workingDirectory)
            processBuilder.environment().putAll(request.environment)
            processBuilder.redirectOutput(request.stdoutFile)
            processBuilder.redirectError(request.stderrFile)

            val process = processBuilder.start()
            RuntimeLaunchedProcess(
                handle = JavaRuntimeProcessHandle(process),
                command = command,
                stdoutFile = request.stdoutFile,
                stderrFile = request.stderrFile,
            )
        }.fold(
            onSuccess = { HostResult.Success(it) },
            onFailure = { throwable ->
                HostResult.Failure(
                    HostError(
                        category = HostErrorCategory.Runtime,
                        message = throwable.message ?: "Failed to launch runtime process.",
                        recoverable = true,
                    )
                )
            }
        )
    }

    private fun ensureRegularFile(file: File, label: String) {
        if (!file.isFile) {
            throw IOException("Missing $label at '${file.absolutePath}'.")
        }
    }

    private fun ensureDirectory(file: File, label: String) {
        if (file.exists()) {
            if (!file.isDirectory) {
                throw IOException("Expected $label at '${file.absolutePath}' to be a directory.")
            }
            return
        }

        if (!file.mkdirs() && !file.isDirectory) {
            throw IOException("Failed to create $label at '${file.absolutePath}'.")
        }
    }

    private fun ensureParentDirectory(file: File) {
        val parent = file.parentFile ?: return
        if (parent.exists()) {
            if (!parent.isDirectory) {
                throw IOException("Expected log parent at '${parent.absolutePath}' to be a directory.")
            }
            return
        }

        if (!parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Failed to create log parent at '${parent.absolutePath}'.")
        }
    }
}

private class JavaRuntimeProcessHandle(
    private val process: Process,
) : RuntimeProcessHandle {
    override val pid: Long?
        get() = null

    override fun isAlive(): Boolean = process.isAlive

    override fun exitCodeOrNull(): Int? = runCatching { process.exitValue() }.getOrNull()

    override fun stop(timeoutMs: Long): Boolean {
        if (!process.isAlive) {
            return true
        }

        process.destroy()
        if (process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            return true
        }

        process.destroyForcibly()
        return process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    }
}
