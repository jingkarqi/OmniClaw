package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadManifest
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.ProviderRuntimeExport
import com.sora.omniclaw.core.storage.ProviderExportStore
import com.sora.omniclaw.core.storage.SecretStore
import com.sora.omniclaw.runtime.api.PayloadLocator
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealRuntimeManagerTest {
    @Test
    fun `start installs writes config and transitions to running`() = runBlocking {
        withTempWorkspace { workspaceRoot, scope ->
            val environment = prepareRuntimeEnvironment(workspaceRoot, runtimeVersion = "2026.4.11")
            val launcher = FakeRuntimeProcessLauncher()
            val manager = newManager(
                workspaceRoot = workspaceRoot,
                providerExportStore = FakeProviderExportStore(
                    ProviderRuntimeExport(
                        providerId = "OpenAI Compatible",
                        baseUrl = "https://api.example.com/v1",
                        modelName = "gpt-4.1-mini",
                    )
                ),
                secretStore = FakeRuntimeSecretStore(apiKey = "secret-api-key"),
                installer = successfulInstaller(environment.installStateStore, "2026.4.11"),
                processLauncher = launcher,
                installStateStore = environment.installStateStore,
                launchStateStore = environment.launchStateStore,
                providerConfigWriter = environment.providerConfigWriter,
                scope = scope,
            )

            val result = manager.start()

            assertTrue(result is HostResult.Success)
            assertEquals(HostLifecycleState.Running, manager.status.value.lifecycleState)
            assertEquals("Runtime running", manager.diagnostics.value.headline)
            assertNotNull(launcher.lastRequest)
            assertTrue(environment.providerConfigWriter.configFile.isFile)
            val persistedConfig = environment.providerConfigWriter.read()
            assertTrue(persistedConfig is HostResult.Success)
            assertEquals(
                "openai-compatible/gpt-4.1-mini",
                (persistedConfig as HostResult.Success).value?.agents?.defaults?.model,
            )
            assertEquals(
                "secret-api-key",
                persistedConfig.value?.models?.providers?.get("openai-compatible")?.apiKey,
            )
        }
    }

    @Test
    fun `start refuses to continue when runtime provider export is missing`() = runBlocking {
        withTempWorkspace { workspaceRoot, scope ->
            val environment = prepareRuntimeEnvironment(workspaceRoot, runtimeVersion = "2026.4.11")
            val manager = newManager(
                workspaceRoot = workspaceRoot,
                providerExportStore = FakeProviderExportStore(null),
                secretStore = FakeRuntimeSecretStore(apiKey = null),
                installer = successfulInstaller(environment.installStateStore, "2026.4.11"),
                installStateStore = environment.installStateStore,
                launchStateStore = environment.launchStateStore,
                providerConfigWriter = environment.providerConfigWriter,
                scope = scope,
            )

            val result = manager.start()

            assertTrue(result is HostResult.Failure)
            assertEquals(HostLifecycleState.ConfigurationInvalid, manager.status.value.lifecycleState)
            assertEquals(
                "Runtime provider export metadata is missing or incomplete.",
                (result as HostResult.Failure).error.message,
            )
        }
    }

    @Test
    fun `start refuses to continue when runtime provider secret is missing`() = runBlocking {
        withTempWorkspace { workspaceRoot, scope ->
            val environment = prepareRuntimeEnvironment(workspaceRoot, runtimeVersion = "2026.4.11")
            val export = ProviderRuntimeExport(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com/v1",
                modelName = "gpt-4.1-mini",
            )
            environment.providerConfigWriter.write(export, "stale-secret")
            val manager = newManager(
                workspaceRoot = workspaceRoot,
                providerExportStore = FakeProviderExportStore(export),
                secretStore = FakeRuntimeSecretStore(apiKey = null),
                installer = successfulInstaller(environment.installStateStore, "2026.4.11"),
                installStateStore = environment.installStateStore,
                launchStateStore = environment.launchStateStore,
                providerConfigWriter = environment.providerConfigWriter,
                scope = scope,
            )

            val result = manager.start()

            assertTrue(result is HostResult.Failure)
            assertEquals(HostLifecycleState.ConfigurationInvalid, manager.status.value.lifecycleState)
            assertEquals(
                "Runtime provider secret is missing.",
                (result as HostResult.Failure).error.message,
            )
            assertFalse(environment.providerConfigWriter.configFile.exists())
        }
    }

    @Test
    fun `start marks runtime failed when process launcher returns a runtime failure`() = runBlocking {
        withTempWorkspace { workspaceRoot, scope ->
            val environment = prepareRuntimeEnvironment(workspaceRoot, runtimeVersion = "2026.4.11")
            val manager = newManager(
                workspaceRoot = workspaceRoot,
                providerExportStore = FakeProviderExportStore(
                    ProviderRuntimeExport(
                        providerId = "openai-compatible",
                        baseUrl = "https://api.example.com/v1",
                        modelName = "gpt-4.1-mini",
                    )
                ),
                secretStore = FakeRuntimeSecretStore(apiKey = "secret-api-key"),
                installer = successfulInstaller(environment.installStateStore, "2026.4.11"),
                processLauncher = FakeRuntimeProcessLauncher(
                    nextResult = HostResult.Failure(
                        HostError(
                            category = HostErrorCategory.Runtime,
                            message = "boom",
                            recoverable = true,
                        )
                    )
                ),
                installStateStore = environment.installStateStore,
                launchStateStore = environment.launchStateStore,
                providerConfigWriter = environment.providerConfigWriter,
                scope = scope,
            )
            environment.stdoutFile.writeText("hello")

            val result = manager.start()

            assertTrue(result is HostResult.Failure)
            assertEquals(HostLifecycleState.Error, manager.status.value.lifecycleState)
            assertTrue(manager.diagnostics.value.details.any { it.contains("stdout") })
            assertFalse(environment.providerConfigWriter.configFile.exists())
        }
    }

    @Test
    fun `stop transitions running runtime to stopped`() = runBlocking {
        withTempWorkspace { workspaceRoot, scope ->
            val environment = prepareRuntimeEnvironment(workspaceRoot, runtimeVersion = "2026.4.11")
            val processHandle = FakeRuntimeProcessHandle()
            val manager = newManager(
                workspaceRoot = workspaceRoot,
                providerExportStore = FakeProviderExportStore(
                    ProviderRuntimeExport(
                        providerId = "openai-compatible",
                        baseUrl = "https://api.example.com/v1",
                        modelName = "gpt-4.1-mini",
                    )
                ),
                secretStore = FakeRuntimeSecretStore(apiKey = "secret-api-key"),
                installer = successfulInstaller(environment.installStateStore, "2026.4.11"),
                processLauncher = FakeRuntimeProcessLauncher(
                    nextResult = HostResult.Success(
                        RuntimeLaunchedProcess(
                            handle = processHandle,
                            command = emptyList(),
                            stdoutFile = File(workspaceRoot, "stdout.log"),
                            stderrFile = File(workspaceRoot, "stderr.log"),
                        )
                    )
                ),
                installStateStore = environment.installStateStore,
                launchStateStore = environment.launchStateStore,
                providerConfigWriter = environment.providerConfigWriter,
                scope = scope,
            )

            manager.start()
            val stopResult = manager.stop()

            assertTrue(stopResult is HostResult.Success)
            assertEquals(HostLifecycleState.Stopped, manager.status.value.lifecycleState)
            assertFalse(processHandle.isAlive())
            assertFalse(environment.providerConfigWriter.configFile.exists())
        }
    }

    @Test
    fun `stop clears generated config when runtime is restored without an in memory process handle`() = runBlocking {
        withTempWorkspace { workspaceRoot, scope ->
            val environment = prepareRuntimeEnvironment(workspaceRoot, runtimeVersion = "2026.4.11")
            val export = ProviderRuntimeExport(
                providerId = "openai-compatible",
                baseUrl = "https://api.example.com/v1",
                modelName = "gpt-4.1-mini",
            )
            environment.providerConfigWriter.write(export, "secret-api-key")
            environment.installStateStore.setInstalled(
                runtimeVersion = "2026.4.11",
                installedAtEpochMs = 100L,
            )
            environment.launchStateStore.setRunning(
                runtimeVersion = "2026.4.11",
                command = listOf("/system/bin/node", "openclaw.mjs", "gateway"),
                stdoutPath = environment.stdoutFile.absolutePath,
                stderrPath = environment.stderrFile.absolutePath,
                startedAtEpochMs = 100L,
                pid = 42L,
            )

            val manager = newManager(
                workspaceRoot = workspaceRoot,
                providerExportStore = FakeProviderExportStore(export),
                secretStore = FakeRuntimeSecretStore(apiKey = "secret-api-key"),
                installer = RuntimeInstaller { error("not used") },
                installStateStore = environment.installStateStore,
                launchStateStore = environment.launchStateStore,
                providerConfigWriter = environment.providerConfigWriter,
                scope = scope,
            )

            val result = manager.stop()

            assertTrue(result is HostResult.Success)
            assertEquals(HostLifecycleState.Stopped, manager.status.value.lifecycleState)
            assertFalse(environment.providerConfigWriter.configFile.exists())
        }
    }

    @Test
    fun `restore surfaces persisted running state as recovering when process handle is absent`() = runBlocking {
        withTempWorkspace { workspaceRoot, scope ->
            val environment = prepareRuntimeEnvironment(workspaceRoot, runtimeVersion = "2026.4.11")
            environment.installStateStore.setInstalled(
                runtimeVersion = "2026.4.11",
                installedAtEpochMs = 100L,
            )
            environment.launchStateStore.setRunning(
                runtimeVersion = "2026.4.11",
                command = listOf("/system/bin/node", "openclaw.mjs", "gateway"),
                stdoutPath = environment.stdoutFile.absolutePath,
                stderrPath = environment.stderrFile.absolutePath,
                startedAtEpochMs = 100L,
                pid = 42L,
            )

            val manager = newManager(
                workspaceRoot = workspaceRoot,
                providerExportStore = FakeProviderExportStore(null),
                secretStore = FakeRuntimeSecretStore(apiKey = null),
                installer = RuntimeInstaller { error("not used") },
                installStateStore = environment.installStateStore,
                launchStateStore = environment.launchStateStore,
                providerConfigWriter = environment.providerConfigWriter,
                scope = scope,
            )

            assertEquals(HostLifecycleState.Recovering, manager.status.value.lifecycleState)
        }
    }

    private fun newManager(
        workspaceRoot: File,
        providerExportStore: ProviderExportStore,
        secretStore: SecretStore,
        installer: RuntimeInstaller,
        processLauncher: RuntimeProcessLauncher = FakeRuntimeProcessLauncher(),
        installStateStore: RuntimeInstallStateStore = RuntimeInstallStateStore(
            RuntimeDirectories.fromRoot(workspaceRoot).installStateFile
        ),
        launchStateStore: RuntimeLaunchStateStore = RuntimeLaunchStateStore(
            RuntimeLaunchStateStore.stateFileFor(workspaceRoot)
        ),
        providerConfigWriter: RuntimeProviderConfigWriter = RuntimeProviderConfigWriter(
            RuntimeDirectories.fromRoot(workspaceRoot)
        ),
        scope: CoroutineScope,
    ): RealRuntimeManager {
        return RealRuntimeManager(
            payloadLocator = TestPayloadLocator(),
            payloadSource = TestBundledPayloadSource(),
            workspaceRoot = workspaceRoot,
            providerExportStore = providerExportStore,
            secretStore = secretStore,
            installStateStore = installStateStore,
            launchStateStore = launchStateStore,
            installer = installer,
            providerConfigWriter = providerConfigWriter,
            processLauncher = processLauncher,
            healthChecker = RuntimeHealthChecker(),
            logCollector = RuntimeLogCollector(),
            scope = scope,
            nowEpochMs = { 100L },
            healthCheckIntervalMs = 10_000L,
            launchStabilizationDelayMs = 0L,
            stopTimeoutMs = 100L,
        )
    }

    private fun successfulInstaller(
        installStateStore: RuntimeInstallStateStore,
        runtimeVersion: String,
    ): RuntimeInstaller {
        return RuntimeInstaller {
            installStateStore.setInstalled(
                runtimeVersion = runtimeVersion,
                installedAtEpochMs = 100L,
            )
        }
    }

    private fun prepareRuntimeEnvironment(
        workspaceRoot: File,
        runtimeVersion: String,
    ): TestEnvironment {
        val directories = RuntimeDirectories.fromRoot(workspaceRoot)
        val runtimeRoot = File(
            directories.extractedRuntimeFilesDir,
            "openclaw-$runtimeVersion-1",
        )
        runtimeRoot.mkdirs()
        File(runtimeRoot, "openclaw.mjs").writeText("#!/usr/bin/env node\n")
        val nodeBinary = File(
            directories.extractedRootFsDir,
            "installed-rootfs/debian/usr/bin/node",
        )
        nodeBinary.parentFile!!.mkdirs()
        nodeBinary.writeText("node-binary")

        val stdoutFile = File(directories.logsDir, "gateway.stdout.log").absoluteFile
        val stderrFile = File(directories.logsDir, "gateway.stderr.log").absoluteFile
        stdoutFile.parentFile!!.mkdirs()
        stderrFile.parentFile!!.mkdirs()
        return TestEnvironment(
            installStateStore = RuntimeInstallStateStore(directories.installStateFile),
            launchStateStore = RuntimeLaunchStateStore(
                RuntimeLaunchStateStore.stateFileFor(workspaceRoot)
            ),
            providerConfigWriter = RuntimeProviderConfigWriter(directories),
            stdoutFile = stdoutFile,
            stderrFile = stderrFile,
        )
    }

    private suspend fun withTempWorkspace(block: suspend (File, CoroutineScope) -> Unit) {
        val tempDir = createTempDirectory(prefix = "real-runtime-manager-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            block(tempDir, scope)
        } finally {
            scope.cancel()
            tempDir.deleteRecursively()
        }
    }
}

private data class TestEnvironment(
    val installStateStore: RuntimeInstallStateStore,
    val launchStateStore: RuntimeLaunchStateStore,
    val providerConfigWriter: RuntimeProviderConfigWriter,
    val stdoutFile: File,
    val stderrFile: File,
)

private class TestPayloadLocator : PayloadLocator {
    override suspend fun loadBundledPayloadManifest(): HostResult<BundledPayloadManifest?> {
        return HostResult.Success(BundledPayloadManifest(payloads = emptyList()))
    }
}

private class TestBundledPayloadSource : BundledPayloadSource {
    override fun openBundledPayload(fileName: String): HostResult<java.io.InputStream> {
        return HostResult.Success(ByteArrayInputStream(ByteArray(0)))
    }
}

private class FakeProviderExportStore(
    initialExport: ProviderRuntimeExport?,
) : ProviderExportStore {
    private val exports = MutableStateFlow(initialExport)

    override suspend fun readExport(): ProviderRuntimeExport? = exports.value

    override fun observeExport(): Flow<ProviderRuntimeExport?> = exports

    override fun observeExportReadiness(): Flow<Boolean> = exports.map { it?.isReady == true }

    override suspend fun writeExport(export: ProviderRuntimeExport): HostResult<Unit> {
        exports.value = export
        return HostResult.Success(Unit)
    }

    override suspend fun clearExport(): HostResult<Unit> {
        exports.value = null
        return HostResult.Success(Unit)
    }
}

private class FakeRuntimeSecretStore(
    private val apiKey: String?,
) : SecretStore {
    override suspend fun saveApiKey(apiKey: String): HostResult<Unit> = throw UnsupportedOperationException()

    override suspend fun readApiKey(): String? = apiKey

    override suspend fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()

    override fun observeApiKeyAvailability(): Flow<Boolean> = MutableStateFlow(!apiKey.isNullOrBlank())

    override suspend fun clearApiKey(): HostResult<Unit> = throw UnsupportedOperationException()
}

private class FakeRuntimeProcessHandle(
    private val stopResult: Boolean = true,
    private val exitCode: Int? = 0,
) : RuntimeProcessHandle {
    private var alive: Boolean = true

    override val pid: Long? = 42L

    override fun isAlive(): Boolean = alive

    override fun exitCodeOrNull(): Int? = if (alive) null else exitCode

    override fun stop(timeoutMs: Long): Boolean {
        alive = false
        return stopResult
    }
}

private class FakeRuntimeProcessLauncher(
    private val nextResult: HostResult<RuntimeLaunchedProcess> = HostResult.Success(
        RuntimeLaunchedProcess(
            handle = FakeRuntimeProcessHandle(),
            command = emptyList(),
            stdoutFile = File("stdout.log"),
            stderrFile = File("stderr.log"),
        )
    ),
) : RuntimeProcessLauncher {
    var lastRequest: RuntimeLaunchRequest? = null

    override fun launch(request: RuntimeLaunchRequest): HostResult<RuntimeLaunchedProcess> {
        lastRequest = request
        return when (nextResult) {
            is HostResult.Success -> HostResult.Success(
                nextResult.value.copy(
                    command = listOf(request.executable.absolutePath) + request.args,
                    stdoutFile = request.stdoutFile,
                    stderrFile = request.stderrFile,
                )
            )

            is HostResult.Failure -> nextResult
        }
    }
}
