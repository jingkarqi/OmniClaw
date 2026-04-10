package com.sora.omniclaw.runtime.impl

import java.io.File

internal data class RuntimeDirectories(
    val workspaceRoot: File,
    val payloadStagingDir: File,
    val extractedRootFsDir: File,
    val extractedRuntimeFilesDir: File,
    val generatedConfigDir: File,
    val logsDir: File,
    val tempDir: File,
    val stateDir: File,
    val installStateFile: File,
) {
    companion object {
        fun fromRoot(workspaceRoot: File): RuntimeDirectories {
            val root = workspaceRoot.absoluteFile
            val payloadsDir = File(root, "payloads")
            val runtimeDir = File(root, "runtime")
            val stateDir = File(root, "state").absoluteFile

            return RuntimeDirectories(
                workspaceRoot = root,
                payloadStagingDir = File(payloadsDir, "staging").absoluteFile,
                extractedRootFsDir = File(runtimeDir, "rootfs").absoluteFile,
                extractedRuntimeFilesDir = File(runtimeDir, "files").absoluteFile,
                generatedConfigDir = File(runtimeDir, "config").absoluteFile,
                logsDir = File(runtimeDir, "logs").absoluteFile,
                tempDir = File(runtimeDir, "tmp").absoluteFile,
                stateDir = stateDir,
                installStateFile = File(stateDir, "install-state.json").absoluteFile,
            )
        }
    }
}
