package com.sora.omniclaw.runtime.impl

internal sealed interface RuntimeLaunchState {
    data class Stopped(
        val runtimeVersion: String? = null,
        val command: List<String> = emptyList(),
        val stdoutPath: String? = null,
        val stderrPath: String? = null,
        val startedAtEpochMs: Long? = null,
        val stoppedAtEpochMs: Long? = null,
        val exitCode: Int? = null,
        val lastError: String? = null,
    ) : RuntimeLaunchState

    data class Starting(
        val runtimeVersion: String,
        val command: List<String>,
        val stdoutPath: String,
        val stderrPath: String,
        val startedAtEpochMs: Long,
    ) : RuntimeLaunchState

    data class Running(
        val runtimeVersion: String,
        val command: List<String>,
        val stdoutPath: String,
        val stderrPath: String,
        val startedAtEpochMs: Long,
        val pid: Long? = null,
    ) : RuntimeLaunchState

    data class Failed(
        val runtimeVersion: String? = null,
        val command: List<String> = emptyList(),
        val stdoutPath: String? = null,
        val stderrPath: String? = null,
        val startedAtEpochMs: Long? = null,
        val failedAtEpochMs: Long,
        val lastError: String,
    ) : RuntimeLaunchState
}
