package com.sora.omniclaw.runtime.impl

internal sealed interface RuntimeInstallState {
    data object NotInstalled : RuntimeInstallState

    data class Installing(
        val targetRuntimeVersion: String,
        val startedAtEpochMs: Long,
        val updatedAtEpochMs: Long,
        val progress: RuntimeInstallProgress? = null,
    ) : RuntimeInstallState

    data class Installed(
        val runtimeVersion: String,
        val installedAtEpochMs: Long,
    ) : RuntimeInstallState

    data class Corrupt(
        val reason: String,
        val detectedAtEpochMs: Long,
        val lastKnownRuntimeVersion: String?,
    ) : RuntimeInstallState

    data class UpgradeRequired(
        val installedRuntimeVersion: String,
        val requiredRuntimeVersion: String,
        val detectedAtEpochMs: Long,
    ) : RuntimeInstallState
}

internal data class RuntimeInstallProgress(
    val step: String,
    val completedSteps: Int,
    val totalSteps: Int,
    val detail: String? = null,
)
