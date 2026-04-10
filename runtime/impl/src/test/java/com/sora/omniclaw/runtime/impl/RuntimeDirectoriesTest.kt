package com.sora.omniclaw.runtime.impl

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeDirectoriesTest {
    @Test
    fun `fromRoot derives the authoritative runtime workspace layout`() {
        val workspaceRoot = File("build/test-runtime-workspace")

        val directories = RuntimeDirectories.fromRoot(workspaceRoot)

        assertEquals(workspaceRoot.absoluteFile, directories.workspaceRoot)
        assertEquals(File(workspaceRoot, "payloads/staging").absoluteFile, directories.payloadStagingDir)
        assertEquals(File(workspaceRoot, "runtime/rootfs").absoluteFile, directories.extractedRootFsDir)
        assertEquals(File(workspaceRoot, "runtime/files").absoluteFile, directories.extractedRuntimeFilesDir)
        assertEquals(File(workspaceRoot, "runtime/config").absoluteFile, directories.generatedConfigDir)
        assertEquals(File(workspaceRoot, "runtime/logs").absoluteFile, directories.logsDir)
        assertEquals(File(workspaceRoot, "runtime/tmp").absoluteFile, directories.tempDir)
        assertEquals(File(workspaceRoot, "state").absoluteFile, directories.stateDir)
        assertEquals(File(workspaceRoot, "state/install-state.json").absoluteFile, directories.installStateFile)
    }
}
