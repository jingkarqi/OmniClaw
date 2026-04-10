package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeInstallStateStoreTest {
    @Test
    fun `read returns not installed when the state file is missing`() {
        withTempDir { tempRoot ->
            val store = RuntimeInstallStateStore(
                stateFile = RuntimeDirectories.fromRoot(tempRoot).installStateFile,
                nowEpochMs = { 500L },
            )

            val result = store.read()

            assertInstallState(result, RuntimeInstallState.NotInstalled)
        }
    }

    @Test
    fun `setInstalling persists in-progress install details for later recovery`() {
        withTempDir { tempRoot ->
            val store = RuntimeInstallStateStore(
                stateFile = RuntimeDirectories.fromRoot(tempRoot).installStateFile,
                nowEpochMs = { 500L },
            )

            val result = store.setInstalling(
                targetRuntimeVersion = "2026.3.13",
                startedAtEpochMs = 100L,
                updatedAtEpochMs = 140L,
                progress = RuntimeInstallProgress(
                    step = "extract-rootfs",
                    completedSteps = 1,
                    totalSteps = 4,
                    detail = "Extracting bundled Debian rootfs.",
                ),
            )

            assertInstallState(
                result = result,
                expectedState = RuntimeInstallState.Installing(
                    targetRuntimeVersion = "2026.3.13",
                    startedAtEpochMs = 100L,
                    updatedAtEpochMs = 140L,
                    progress = RuntimeInstallProgress(
                        step = "extract-rootfs",
                        completedSteps = 1,
                        totalSteps = 4,
                        detail = "Extracting bundled Debian rootfs.",
                    ),
                ),
            )
            assertInstallState(
                result = store.read(),
                expectedState = RuntimeInstallState.Installing(
                    targetRuntimeVersion = "2026.3.13",
                    startedAtEpochMs = 100L,
                    updatedAtEpochMs = 140L,
                    progress = RuntimeInstallProgress(
                        step = "extract-rootfs",
                        completedSteps = 1,
                        totalSteps = 4,
                        detail = "Extracting bundled Debian rootfs.",
                    ),
                ),
            )
        }
    }

    @Test
    fun `setInstalled persists the installed runtime version after installation`() {
        withTempDir { tempRoot ->
            val store = RuntimeInstallStateStore(
                stateFile = RuntimeDirectories.fromRoot(tempRoot).installStateFile,
                nowEpochMs = { 500L },
            )

            store.setInstalling(
                targetRuntimeVersion = "2026.3.13",
                startedAtEpochMs = 100L,
                updatedAtEpochMs = 140L,
            )
            val result = store.setInstalled(
                runtimeVersion = "2026.3.13",
                installedAtEpochMs = 200L,
            )

            assertInstallState(
                result = result,
                expectedState = RuntimeInstallState.Installed(
                    runtimeVersion = "2026.3.13",
                    installedAtEpochMs = 200L,
                ),
            )
            assertInstallState(
                result = store.read(),
                expectedState = RuntimeInstallState.Installed(
                    runtimeVersion = "2026.3.13",
                    installedAtEpochMs = 200L,
                ),
            )
        }
    }

    @Test
    fun `setUpgradeRequired persists the required target version`() {
        withTempDir { tempRoot ->
            val store = RuntimeInstallStateStore(
                stateFile = RuntimeDirectories.fromRoot(tempRoot).installStateFile,
                nowEpochMs = { 500L },
            )

            store.setInstalled(
                runtimeVersion = "2026.3.13",
                installedAtEpochMs = 200L,
            )
            val result = store.setUpgradeRequired(
                installedRuntimeVersion = "2026.3.13",
                requiredRuntimeVersion = "2026.4.0",
                detectedAtEpochMs = 300L,
            )

            assertInstallState(
                result = result,
                expectedState = RuntimeInstallState.UpgradeRequired(
                    installedRuntimeVersion = "2026.3.13",
                    requiredRuntimeVersion = "2026.4.0",
                    detectedAtEpochMs = 300L,
                ),
            )
            assertInstallState(
                result = store.read(),
                expectedState = RuntimeInstallState.UpgradeRequired(
                    installedRuntimeVersion = "2026.3.13",
                    requiredRuntimeVersion = "2026.4.0",
                    detectedAtEpochMs = 300L,
                ),
            )
        }
    }

    @Test
    fun `setCorrupt persists corruption details`() {
        withTempDir { tempRoot ->
            val store = RuntimeInstallStateStore(
                stateFile = RuntimeDirectories.fromRoot(tempRoot).installStateFile,
                nowEpochMs = { 500L },
            )

            val result = store.setCorrupt(
                reason = "Runtime executable is missing from the extracted files.",
                detectedAtEpochMs = 400L,
                lastKnownRuntimeVersion = "2026.3.13",
            )

            assertInstallState(
                result = result,
                expectedState = RuntimeInstallState.Corrupt(
                    reason = "Runtime executable is missing from the extracted files.",
                    detectedAtEpochMs = 400L,
                    lastKnownRuntimeVersion = "2026.3.13",
                ),
            )
            assertInstallState(
                result = store.read(),
                expectedState = RuntimeInstallState.Corrupt(
                    reason = "Runtime executable is missing from the extracted files.",
                    detectedAtEpochMs = 400L,
                    lastKnownRuntimeVersion = "2026.3.13",
                ),
            )
        }
    }

    @Test
    fun `read treats malformed persisted state as corrupt`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            directories.stateDir.mkdirs()
            directories.installStateFile.writeText("{not-valid-json")

            val store = RuntimeInstallStateStore(
                stateFile = directories.installStateFile,
                nowEpochMs = { 700L },
            )

            val result = store.read()

            assertInstallState(
                result = result,
                expectedState = RuntimeInstallState.Corrupt(
                    reason = "Runtime install state file is malformed.",
                    detectedAtEpochMs = 700L,
                    lastKnownRuntimeVersion = null,
                ),
            )
        }
    }

    @Test
    fun `write returns a storage failure when the state parent path cannot be created`() {
        withTempDir { tempRoot ->
            val blockedParent = File(tempRoot, "blocked")
            blockedParent.writeText("not-a-directory")

            val store = RuntimeInstallStateStore(
                stateFile = File(blockedParent, "install-state.json"),
                nowEpochMs = { 500L },
            )

            val result = store.setInstalled(
                runtimeVersion = "2026.3.13",
                installedAtEpochMs = 200L,
            )

            assertTrue(result is HostResult.Failure)
            assertEquals(HostErrorCategory.Storage, (result as HostResult.Failure).error.category)
            assertEquals("Failed to persist runtime install state.", result.error.message)
            assertTrue(result.error.recoverable)
        }
    }

    private fun assertInstallState(
        result: HostResult<RuntimeInstallState>,
        expectedState: RuntimeInstallState,
    ) {
        assertTrue(result is HostResult.Success)
        assertEquals(expectedState, (result as HostResult.Success).value)
    }

    private fun withTempDir(block: (File) -> Unit) {
        val tempDir = createTempDirectory(prefix = "runtime-install-state-store-").toFile()
        try {
            block(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
