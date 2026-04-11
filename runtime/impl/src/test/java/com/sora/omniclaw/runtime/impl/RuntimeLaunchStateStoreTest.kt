package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLaunchStateStoreTest {
    @Test
    fun `read returns stopped when the state file is missing`() {
        withTempDir { tempRoot ->
            val store = RuntimeLaunchStateStore(
                stateFile = launchStateFile(tempRoot),
                nowEpochMs = { 500L },
            )

            val result = store.read()

            assertLaunchState(result, RuntimeLaunchState.Stopped())
        }
    }

    @Test
    fun `launch-state round trips recovery details across lifecycle transitions`() {
        withTempDir { tempRoot ->
            val store = RuntimeLaunchStateStore(
                stateFile = launchStateFile(tempRoot),
                nowEpochMs = { 500L },
            )
            val command = listOf("/system/bin/sh", "-lc", "/opt/omniclaw/bin/runtime --serve")
            val stdoutPath = File(tempRoot, "runtime/logs/runtime.stdout.log").absolutePath
            val stderrPath = File(tempRoot, "runtime/logs/runtime.stderr.log").absolutePath

            val starting = store.setStarting(
                runtimeVersion = "2026.4.11",
                command = command,
                stdoutPath = stdoutPath,
                stderrPath = stderrPath,
                startedAtEpochMs = 100L,
            )
            assertLaunchState(
                result = starting,
                expectedState = RuntimeLaunchState.Starting(
                    runtimeVersion = "2026.4.11",
                    command = command,
                    stdoutPath = stdoutPath,
                    stderrPath = stderrPath,
                    startedAtEpochMs = 100L,
                ),
            )
            assertLaunchState(
                result = store.read(),
                expectedState = RuntimeLaunchState.Starting(
                    runtimeVersion = "2026.4.11",
                    command = command,
                    stdoutPath = stdoutPath,
                    stderrPath = stderrPath,
                    startedAtEpochMs = 100L,
                ),
            )

            val running = store.setRunning(
                runtimeVersion = "2026.4.11",
                command = command,
                stdoutPath = stdoutPath,
                stderrPath = stderrPath,
                startedAtEpochMs = 100L,
                pid = 4242L,
            )
            assertLaunchState(
                result = running,
                expectedState = RuntimeLaunchState.Running(
                    runtimeVersion = "2026.4.11",
                    command = command,
                    stdoutPath = stdoutPath,
                    stderrPath = stderrPath,
                    startedAtEpochMs = 100L,
                    pid = 4242L,
                ),
            )
            assertLaunchState(
                result = store.read(),
                expectedState = RuntimeLaunchState.Running(
                    runtimeVersion = "2026.4.11",
                    command = command,
                    stdoutPath = stdoutPath,
                    stderrPath = stderrPath,
                    startedAtEpochMs = 100L,
                    pid = 4242L,
                ),
            )

            val stopped = store.setStopped(
                runtimeVersion = "2026.4.11",
                command = command,
                stdoutPath = stdoutPath,
                stderrPath = stderrPath,
                startedAtEpochMs = 100L,
                stoppedAtEpochMs = 200L,
                exitCode = 0,
                lastError = null,
            )
            assertLaunchState(
                result = stopped,
                expectedState = RuntimeLaunchState.Stopped(
                    runtimeVersion = "2026.4.11",
                    command = command,
                    stdoutPath = stdoutPath,
                    stderrPath = stderrPath,
                    startedAtEpochMs = 100L,
                    stoppedAtEpochMs = 200L,
                    exitCode = 0,
                    lastError = null,
                ),
            )
            assertLaunchState(
                result = store.read(),
                expectedState = RuntimeLaunchState.Stopped(
                    runtimeVersion = "2026.4.11",
                    command = command,
                    stdoutPath = stdoutPath,
                    stderrPath = stderrPath,
                    startedAtEpochMs = 100L,
                    stoppedAtEpochMs = 200L,
                    exitCode = 0,
                    lastError = null,
                ),
            )

            val failed = store.setFailed(
                runtimeVersion = "2026.4.11",
                command = command,
                stdoutPath = stdoutPath,
                stderrPath = stderrPath,
                startedAtEpochMs = 300L,
                failedAtEpochMs = 350L,
                lastError = "Process exited before bridge handshake.",
            )
            assertLaunchState(
                result = failed,
                expectedState = RuntimeLaunchState.Failed(
                    runtimeVersion = "2026.4.11",
                    command = command,
                    stdoutPath = stdoutPath,
                    stderrPath = stderrPath,
                    startedAtEpochMs = 300L,
                    failedAtEpochMs = 350L,
                    lastError = "Process exited before bridge handshake.",
                ),
            )
            assertLaunchState(
                result = store.read(),
                expectedState = RuntimeLaunchState.Failed(
                    runtimeVersion = "2026.4.11",
                    command = command,
                    stdoutPath = stdoutPath,
                    stderrPath = stderrPath,
                    startedAtEpochMs = 300L,
                    failedAtEpochMs = 350L,
                    lastError = "Process exited before bridge handshake.",
                ),
            )
        }
    }

    @Test
    fun `read treats malformed persisted state as failed`() {
        withTempDir { tempRoot ->
            val stateFile = launchStateFile(tempRoot)
            stateFile.parentFile!!.mkdirs()
            stateFile.writeText("{not-valid-json")

            val store = RuntimeLaunchStateStore(
                stateFile = stateFile,
                nowEpochMs = { 700L },
            )

            val result = store.read()

            assertLaunchState(
                result = result,
                expectedState = RuntimeLaunchState.Failed(
                    runtimeVersion = null,
                    command = emptyList(),
                    stdoutPath = null,
                    stderrPath = null,
                    startedAtEpochMs = null,
                    failedAtEpochMs = 700L,
                    lastError = "Runtime launch state file is malformed.",
                ),
            )
        }
    }

    @Test
    fun `write returns a storage failure when the state parent path cannot be created`() {
        withTempDir { tempRoot ->
            val blockedParent = File(tempRoot, "blocked")
            blockedParent.writeText("not-a-directory")

            val store = RuntimeLaunchStateStore(
                stateFile = File(blockedParent, "launch-state.json"),
                nowEpochMs = { 500L },
            )

            val result = store.setRunning(
                runtimeVersion = "2026.4.11",
                command = listOf("/opt/omniclaw/bin/runtime"),
                stdoutPath = "runtime/logs/runtime.stdout.log",
                stderrPath = "runtime/logs/runtime.stderr.log",
                startedAtEpochMs = 100L,
                pid = 42L,
            )

            assertTrue(result is HostResult.Failure)
            assertEquals(HostErrorCategory.Storage, (result as HostResult.Failure).error.category)
            assertEquals("Failed to persist runtime launch state.", result.error.message)
            assertTrue(result.error.recoverable)
        }
    }

    private fun assertLaunchState(
        result: HostResult<RuntimeLaunchState>,
        expectedState: RuntimeLaunchState,
    ) {
        assertTrue(result is HostResult.Success)
        assertEquals(expectedState, (result as HostResult.Success).value)
    }

    private fun launchStateFile(workspaceRoot: File): File {
        return File(workspaceRoot, "state/launch-state.json").absoluteFile
    }

    private fun withTempDir(block: (File) -> Unit) {
        val tempDir = createTempDirectory(prefix = "runtime-launch-state-store-").toFile()
        try {
            block(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
