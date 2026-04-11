package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.common.errorOrNull
import com.sora.omniclaw.core.common.getOrNull
import com.sora.omniclaw.core.model.BundledPayloadManifest
import com.sora.omniclaw.core.model.DiagnosticsSummary
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.RuntimeStatus
import com.sora.omniclaw.core.storage.ProviderExportStore
import com.sora.omniclaw.core.storage.SecretStore
import com.sora.omniclaw.runtime.api.PayloadLocator
import com.sora.omniclaw.runtime.api.RuntimeManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun interface RuntimeInstaller {
    fun install(manifest: BundledPayloadManifest?): HostResult<RuntimeInstallState>
}

class RealRuntimeManager private constructor(
    private val payloadLocator: PayloadLocator,
    private val providerExportStore: ProviderExportStore,
    private val secretStore: SecretStore,
    workspaceRoot: File,
    private val installStateStore: RuntimeInstallStateStore,
    private val launchStateStore: RuntimeLaunchStateStore,
    private val installer: RuntimeInstaller,
    private val providerConfigWriter: RuntimeProviderConfigWriter,
    private val processLauncher: RuntimeProcessLauncher,
    private val healthChecker: RuntimeHealthChecker,
    private val logCollector: RuntimeLogCollector,
    private val scope: CoroutineScope,
    private val nowEpochMs: () -> Long,
    private val healthCheckIntervalMs: Long,
    private val launchStabilizationDelayMs: Long,
    private val stopTimeoutMs: Long,
) : RuntimeManager {
    constructor(
        payloadLocator: PayloadLocator,
        payloadSource: BundledPayloadSource,
        workspaceRoot: File,
        providerExportStore: ProviderExportStore,
        secretStore: SecretStore,
    ) : this(
        payloadLocator = payloadLocator,
        providerExportStore = providerExportStore,
        secretStore = secretStore,
        workspaceRoot = workspaceRoot,
        installStateStore = RuntimeInstallStateStore(
            RuntimeDirectories.fromRoot(workspaceRoot).installStateFile
        ),
        launchStateStore = RuntimeLaunchStateStore(
            RuntimeLaunchStateStore.stateFileFor(workspaceRoot)
        ),
        installer = RuntimeInstaller { manifest ->
            BootstrapInstaller(
                payloadSource = payloadSource,
                directories = RuntimeDirectories.fromRoot(workspaceRoot),
                installStateStore = RuntimeInstallStateStore(
                    RuntimeDirectories.fromRoot(workspaceRoot).installStateFile
                ),
            ).install(manifest)
        },
        providerConfigWriter = RuntimeProviderConfigWriter(
            directories = RuntimeDirectories.fromRoot(workspaceRoot),
        ),
        processLauncher = ShellRuntimeProcessLauncher(),
        healthChecker = RuntimeHealthChecker(),
        logCollector = RuntimeLogCollector(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        nowEpochMs = System::currentTimeMillis,
        healthCheckIntervalMs = 2_000L,
        launchStabilizationDelayMs = 250L,
        stopTimeoutMs = 5_000L,
    )

    internal constructor(
        payloadLocator: PayloadLocator,
        payloadSource: BundledPayloadSource,
        workspaceRoot: File,
        providerExportStore: ProviderExportStore,
        secretStore: SecretStore,
        installStateStore: RuntimeInstallStateStore,
        launchStateStore: RuntimeLaunchStateStore,
        installer: RuntimeInstaller,
        providerConfigWriter: RuntimeProviderConfigWriter,
        processLauncher: RuntimeProcessLauncher,
        healthChecker: RuntimeHealthChecker,
        logCollector: RuntimeLogCollector,
        scope: CoroutineScope,
        nowEpochMs: () -> Long,
        healthCheckIntervalMs: Long,
        launchStabilizationDelayMs: Long,
        stopTimeoutMs: Long,
    ) : this(
        payloadLocator = payloadLocator,
        providerExportStore = providerExportStore,
        secretStore = secretStore,
        workspaceRoot = workspaceRoot,
        installStateStore = installStateStore,
        launchStateStore = launchStateStore,
        installer = installer,
        providerConfigWriter = providerConfigWriter,
        processLauncher = processLauncher,
        healthChecker = healthChecker,
        logCollector = logCollector,
        scope = scope,
        nowEpochMs = nowEpochMs,
        healthCheckIntervalMs = healthCheckIntervalMs,
        launchStabilizationDelayMs = launchStabilizationDelayMs,
        stopTimeoutMs = stopTimeoutMs,
    )

    private val directories = RuntimeDirectories.fromRoot(workspaceRoot)
    private val stateMutex = Mutex()

    private val _status = MutableStateFlow(
        RuntimeStatus(
            lifecycleState = HostLifecycleState.InstallRequired,
            payloadAvailable = false,
        )
    )
    override val status: StateFlow<RuntimeStatus> = _status.asStateFlow()

    private val _diagnostics = MutableStateFlow(
        DiagnosticsSummary(
            headline = "Runtime idle",
            details = listOf("Real runtime manager is waiting to be started."),
            lastUpdatedEpochMillis = nowEpochMs(),
        )
    )
    override val diagnostics: StateFlow<DiagnosticsSummary> = _diagnostics.asStateFlow()

    private var payloadAvailable: Boolean = false
    private var activeProcess: RuntimeProcessHandle? = null
    private var monitorJob: kotlinx.coroutines.Job? = null

    init {
        restorePersistedState()
    }

    override suspend fun start(): HostResult<Unit> = stateMutex.withLock {
        activeProcess?.takeIf { it.isAlive() }?.let {
            publishCurrentSnapshot(extraDetails = emptyList())
            return HostResult.Success(Unit)
        }

        publishStartingState("Preparing runtime payloads and configuration.")

        val manifest = when (val manifestResult = payloadLocator.loadBundledPayloadManifest()) {
            is HostResult.Success -> {
                manifestResult.value ?: return failWithSnapshot(
                    error = HostError(
                        category = HostErrorCategory.Runtime,
                        message = "Bundled payload manifest is missing at assets/bootstrap/manifest.json.",
                        recoverable = true,
                    ),
                    lifecycleState = HostLifecycleState.InstallRequired,
                )
            }

            is HostResult.Failure -> {
                return failWithSnapshot(
                    error = manifestResult.error,
                    lifecycleState = HostLifecycleState.Error,
                )
            }
        }
        payloadAvailable = true

        val installedState = when (val installResult = installer.install(manifest)) {
            is HostResult.Success -> {
                installResult.value as? RuntimeInstallState.Installed ?: return failWithSnapshot(
                    error = HostError(
                        category = HostErrorCategory.Runtime,
                        message = "Runtime installer did not finish with an installed state.",
                        recoverable = true,
                    ),
                    lifecycleState = HostLifecycleState.Error,
                )
            }

            is HostResult.Failure -> {
                return failWithSnapshot(
                    error = installResult.error,
                    lifecycleState = HostLifecycleState.InstallRequired,
                )
            }
        }

        val export = providerExportStore.readExport()
        if (export == null || !export.isReady) {
            return failWithSnapshot(
                error = HostError(
                    category = HostErrorCategory.Validation,
                    message = "Runtime provider export metadata is missing or incomplete.",
                    recoverable = true,
                ),
                lifecycleState = HostLifecycleState.ConfigurationInvalid,
            )
        }

        val apiKey = secretStore.readApiKey()
        if (apiKey.isNullOrBlank()) {
            return failWithSnapshot(
                error = HostError(
                    category = HostErrorCategory.Validation,
                    message = "Runtime provider secret is missing.",
                    recoverable = true,
                ),
                lifecycleState = HostLifecycleState.ConfigurationInvalid,
            )
        }

        when (val configResult = providerConfigWriter.write(export, apiKey)) {
            is HostResult.Success -> Unit
            is HostResult.Failure -> {
                return failWithSnapshot(
                    error = configResult.error,
                    lifecycleState = if (configResult.error.category == HostErrorCategory.Validation) {
                        HostLifecycleState.ConfigurationInvalid
                    } else {
                        HostLifecycleState.Error
                    },
                )
            }
        }

        val launchRequest = when (val launchRequestResult = buildLaunchRequest(installedState.runtimeVersion)) {
            is HostResult.Success -> launchRequestResult.value
            is HostResult.Failure -> {
                return failWithSnapshot(
                    error = launchRequestResult.error,
                    lifecycleState = HostLifecycleState.Error,
                )
            }
        }

        val startedAtEpochMs = nowEpochMs()
        when (val startingResult = launchStateStore.setStarting(
            runtimeVersion = installedState.runtimeVersion,
            command = listOf(launchRequest.executable.absolutePath) + launchRequest.args,
            stdoutPath = launchRequest.stdoutFile.absolutePath,
            stderrPath = launchRequest.stderrFile.absolutePath,
            startedAtEpochMs = startedAtEpochMs,
        )) {
            is HostResult.Success -> publishSnapshot(
                installState = installedState,
                launchState = startingResult.value,
            )

            is HostResult.Failure -> {
                return failWithSnapshot(
                    error = startingResult.error,
                    lifecycleState = HostLifecycleState.Error,
                )
            }
        }

        val launchedProcess = when (val launchResult = processLauncher.launch(launchRequest)) {
            is HostResult.Success -> launchResult.value
            is HostResult.Failure -> {
                return markLaunchFailed(
                    runtimeVersion = installedState.runtimeVersion,
                    command = listOf(launchRequest.executable.absolutePath) + launchRequest.args,
                    stdoutPath = launchRequest.stdoutFile.absolutePath,
                    stderrPath = launchRequest.stderrFile.absolutePath,
                    startedAtEpochMs = startedAtEpochMs,
                    message = launchResult.error.message,
                )
            }
        }

        activeProcess = launchedProcess.handle
        val runningState = when (val runningResult = launchStateStore.setRunning(
            runtimeVersion = installedState.runtimeVersion,
            command = launchedProcess.command,
            stdoutPath = launchedProcess.stdoutFile.absolutePath,
            stderrPath = launchedProcess.stderrFile.absolutePath,
            startedAtEpochMs = startedAtEpochMs,
            pid = launchedProcess.handle.pid,
        )) {
            is HostResult.Success -> runningResult.value
            is HostResult.Failure -> {
                activeProcess = null
                launchedProcess.handle.stop(stopTimeoutMs)
                return failWithSnapshot(
                    error = runningResult.error,
                    lifecycleState = HostLifecycleState.Error,
                )
            }
        }

        if (launchStabilizationDelayMs > 0) {
            delay(launchStabilizationDelayMs)
        }

        if (!launchedProcess.handle.isAlive()) {
            activeProcess = null
            return markLaunchFailed(
                runtimeVersion = installedState.runtimeVersion,
                command = launchedProcess.command,
                stdoutPath = launchedProcess.stdoutFile.absolutePath,
                stderrPath = launchedProcess.stderrFile.absolutePath,
                startedAtEpochMs = startedAtEpochMs,
                message = launchedProcess.handle.exitCodeOrNull()?.let { exitCode ->
                    "Runtime process exited before reaching healthy state (exit code $exitCode)."
                } ?: "Runtime process exited before reaching healthy state.",
            )
        }

        startProcessMonitor()
        publishSnapshot(
            installState = installedState,
            launchState = runningState,
        )
        return HostResult.Success(Unit)
    }

    override suspend fun stop(): HostResult<Unit> = stateMutex.withLock {
        monitorJob?.cancel()
        monitorJob = null

        val installState = currentInstallState().getOrNull() ?: RuntimeInstallState.NotInstalled
        val launchState = currentLaunchState().getOrNull() ?: RuntimeLaunchState.Stopped()
        val active = activeProcess

        if (active == null) {
            val stoppedState = persistStoppedState(launchState, lastError = null)
            clearRuntimeConfig()
            publishSnapshot(installState = installState, launchState = stoppedState)
            return HostResult.Success(Unit)
        }

        val stopped = active.stop(stopTimeoutMs)
        val exitCode = active.exitCodeOrNull()
        activeProcess = null

        if (!stopped) {
            return markLaunchFailed(
                runtimeVersion = launchState.runtimeVersionOrNull(),
                command = launchState.commandOrEmpty(),
                stdoutPath = launchState.stdoutPathOrNull(),
                stderrPath = launchState.stderrPathOrNull(),
                startedAtEpochMs = launchState.startedAtEpochMsOrNull(),
                message = "Runtime process did not stop within ${stopTimeoutMs}ms.",
            )
        }

        val stoppedState = persistStoppedState(
            launchState = launchState,
            lastError = null,
            exitCode = exitCode,
        )
        clearRuntimeConfig()
        publishSnapshot(installState = installState, launchState = stoppedState)
        return HostResult.Success(Unit)
    }

    private fun restorePersistedState() {
        val installState = currentInstallState()
        val launchState = currentLaunchState()
        payloadAvailable = installState.getOrNull() !is RuntimeInstallState.NotInstalled

        val installValue = installState.getOrNull()
        val launchValue = launchState.getOrNull()
        if (installValue == null || launchValue == null) {
            val message = installState.errorOrNull()?.message ?: launchState.errorOrNull()?.message
            _status.value = RuntimeStatus(
                lifecycleState = HostLifecycleState.Error,
                payloadAvailable = payloadAvailable,
                lastErrorMessage = message,
            )
            _diagnostics.value = DiagnosticsSummary(
                headline = "Runtime state unavailable",
                details = listOfNotNull(message),
                lastUpdatedEpochMillis = nowEpochMs(),
            )
            return
        }

        publishSnapshot(
            installState = installValue,
            launchState = launchValue,
        )
    }

    private fun startProcessMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(healthCheckIntervalMs)
                val active = activeProcess ?: return@launch
                if (active.isAlive()) {
                    continue
                }

                stateMutex.withLock {
                    if (activeProcess !== active) {
                        return@withLock
                    }

                    activeProcess = null
                    val launchState = currentLaunchState().getOrNull() ?: RuntimeLaunchState.Stopped()
                    markLaunchFailed(
                        runtimeVersion = launchState.runtimeVersionOrNull(),
                        command = launchState.commandOrEmpty(),
                        stdoutPath = launchState.stdoutPathOrNull(),
                        stderrPath = launchState.stderrPathOrNull(),
                        startedAtEpochMs = launchState.startedAtEpochMsOrNull(),
                        message = active.exitCodeOrNull()?.let { exitCode ->
                            "Runtime process exited unexpectedly with code $exitCode."
                        } ?: "Runtime process exited unexpectedly.",
                    )
                }
                return@launch
            }
        }
    }

    private fun publishStartingState(detail: String) {
        _status.value = RuntimeStatus(
            lifecycleState = HostLifecycleState.Starting,
            payloadAvailable = payloadAvailable,
            lastErrorMessage = null,
        )
        _diagnostics.value = DiagnosticsSummary(
            headline = "Runtime starting",
            details = listOf(detail),
            lastUpdatedEpochMillis = nowEpochMs(),
        )
    }

    private fun publishCurrentSnapshot(extraDetails: List<String>) {
        val installState = currentInstallState().getOrNull() ?: RuntimeInstallState.NotInstalled
        val launchState = currentLaunchState().getOrNull() ?: RuntimeLaunchState.Stopped()
        publishSnapshot(installState, launchState, extraDetails)
    }

    private fun publishSnapshot(
        installState: RuntimeInstallState,
        launchState: RuntimeLaunchState,
        extraDetails: List<String> = emptyList(),
    ) {
        val snapshot = healthChecker.snapshot(
            installState = installState,
            launchState = launchState,
            payloadAvailable = payloadAvailable,
            processAlive = activeProcess?.isAlive() == true,
            nowEpochMs = nowEpochMs(),
            extraDetails = extraDetails,
        )
        _status.value = snapshot.status
        _diagnostics.value = snapshot.diagnostics
    }

    private fun markLaunchFailed(
        runtimeVersion: String?,
        command: List<String>,
        stdoutPath: String?,
        stderrPath: String?,
        startedAtEpochMs: Long?,
        message: String,
    ): HostResult<Unit> {
        val failedState = when (val failedResult = launchStateStore.setFailed(
            runtimeVersion = runtimeVersion,
            command = command,
            stdoutPath = stdoutPath,
            stderrPath = stderrPath,
            startedAtEpochMs = startedAtEpochMs,
            lastError = message,
        )) {
            is HostResult.Success -> failedResult.value
            is HostResult.Failure -> {
                return failWithSnapshot(
                    error = failedResult.error,
                    lifecycleState = HostLifecycleState.Error,
                )
            }
        }

        publishCurrentSnapshot(
            extraDetails = logCollector.collect(stdoutPath, stderrPath)
        )
        clearRuntimeConfig()
        return HostResult.Failure(
            HostError(
                category = HostErrorCategory.Runtime,
                message = message,
                recoverable = true,
            )
        )
    }

    private fun persistStoppedState(
        launchState: RuntimeLaunchState,
        lastError: String?,
        exitCode: Int? = null,
    ): RuntimeLaunchState {
        return when (val stoppedResult = launchStateStore.setStopped(
            runtimeVersion = launchState.runtimeVersionOrNull(),
            command = launchState.commandOrEmpty(),
            stdoutPath = launchState.stdoutPathOrNull(),
            stderrPath = launchState.stderrPathOrNull(),
            startedAtEpochMs = launchState.startedAtEpochMsOrNull(),
            stoppedAtEpochMs = nowEpochMs(),
            exitCode = exitCode,
            lastError = lastError,
        )) {
            is HostResult.Success -> stoppedResult.value
            is HostResult.Failure -> RuntimeLaunchState.Stopped(
                runtimeVersion = launchState.runtimeVersionOrNull(),
                command = launchState.commandOrEmpty(),
                stdoutPath = launchState.stdoutPathOrNull(),
                stderrPath = launchState.stderrPathOrNull(),
                startedAtEpochMs = launchState.startedAtEpochMsOrNull(),
                stoppedAtEpochMs = nowEpochMs(),
                exitCode = exitCode,
                lastError = lastError ?: stoppedResult.error.message,
            )
        }
    }

    private fun buildLaunchRequest(runtimeVersion: String): HostResult<RuntimeLaunchRequest> {
        val runtimeRoot = runtimeRoot(runtimeVersion)
            ?: return HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Runtime,
                    message = "Installed runtime files are missing for version $runtimeVersion.",
                    recoverable = true,
                )
            )

        val nodeBinary = File(
            directories.extractedRootFsDir,
            "installed-rootfs/debian/usr/bin/node",
        ).absoluteFile
        val entryScript = File(runtimeRoot, "openclaw.mjs").absoluteFile
        if (!nodeBinary.isFile) {
            return HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Runtime,
                    message = "Bundled Node runtime is missing at ${nodeBinary.absolutePath}.",
                    recoverable = true,
                )
            )
        }
        if (!entryScript.isFile) {
            return HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Runtime,
                    message = "OpenClaw CLI entrypoint is missing at ${entryScript.absolutePath}.",
                    recoverable = true,
                )
            )
        }

        val stdoutFile = File(directories.logsDir, "gateway.stdout.log").absoluteFile
        val stderrFile = File(directories.logsDir, "gateway.stderr.log").absoluteFile
        return HostResult.Success(
            RuntimeLaunchRequest(
                workingDirectory = runtimeRoot,
                executable = nodeBinary,
                args = listOf(
                    entryScript.absolutePath,
                    "gateway",
                    "--bind",
                    "loopback",
                ),
                environment = mapOf(
                    "HOME" to directories.workspaceRoot.absolutePath,
                    "TMPDIR" to directories.tempDir.absolutePath,
                    "OPENCLAW_CONFIG_PATH" to providerConfigWriter.configFile.absolutePath,
                ),
                stdoutFile = stdoutFile,
                stderrFile = stderrFile,
            )
        )
    }

    private fun runtimeRoot(runtimeVersion: String): File? {
        return directories.extractedRuntimeFilesDir.listFiles()
            ?.filter { candidate ->
                candidate.isDirectory && candidate.name.startsWith("openclaw-$runtimeVersion-")
            }
            ?.singleOrNull()
            ?.absoluteFile
    }

    private fun failWithSnapshot(
        error: HostError,
        lifecycleState: HostLifecycleState,
    ): HostResult<Unit> {
        clearRuntimeConfig()
        _status.value = RuntimeStatus(
            lifecycleState = lifecycleState,
            payloadAvailable = payloadAvailable,
            lastErrorMessage = error.message,
        )
        _diagnostics.value = DiagnosticsSummary(
            headline = when (lifecycleState) {
                HostLifecycleState.ConfigurationInvalid -> "Runtime configuration invalid"
                HostLifecycleState.InstallRequired -> "Runtime install required"
                else -> "Runtime failed"
            },
            details = listOf(error.message),
            lastUpdatedEpochMillis = nowEpochMs(),
        )
        return HostResult.Failure(error)
    }

    private fun currentInstallState(): HostResult<RuntimeInstallState> = installStateStore.read()

    private fun currentLaunchState(): HostResult<RuntimeLaunchState> = launchStateStore.read()

    private fun clearRuntimeConfig() {
        providerConfigWriter.clear()
    }
}

private fun RuntimeLaunchState.runtimeVersionOrNull(): String? = when (this) {
    is RuntimeLaunchState.Stopped -> runtimeVersion
    is RuntimeLaunchState.Starting -> runtimeVersion
    is RuntimeLaunchState.Running -> runtimeVersion
    is RuntimeLaunchState.Failed -> runtimeVersion
}

private fun RuntimeLaunchState.commandOrEmpty(): List<String> = when (this) {
    is RuntimeLaunchState.Stopped -> command
    is RuntimeLaunchState.Starting -> command
    is RuntimeLaunchState.Running -> command
    is RuntimeLaunchState.Failed -> command
}

private fun RuntimeLaunchState.stdoutPathOrNull(): String? = when (this) {
    is RuntimeLaunchState.Stopped -> stdoutPath
    is RuntimeLaunchState.Starting -> stdoutPath
    is RuntimeLaunchState.Running -> stdoutPath
    is RuntimeLaunchState.Failed -> stdoutPath
}

private fun RuntimeLaunchState.stderrPathOrNull(): String? = when (this) {
    is RuntimeLaunchState.Stopped -> stderrPath
    is RuntimeLaunchState.Starting -> stderrPath
    is RuntimeLaunchState.Running -> stderrPath
    is RuntimeLaunchState.Failed -> stderrPath
}

private fun RuntimeLaunchState.startedAtEpochMsOrNull(): Long? = when (this) {
    is RuntimeLaunchState.Stopped -> startedAtEpochMs
    is RuntimeLaunchState.Starting -> startedAtEpochMs
    is RuntimeLaunchState.Running -> startedAtEpochMs
    is RuntimeLaunchState.Failed -> startedAtEpochMs
}
