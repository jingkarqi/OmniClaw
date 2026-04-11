package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.model.DiagnosticsSummary
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.core.model.RuntimeStatus

internal data class RuntimeHealthSnapshot(
    val status: RuntimeStatus,
    val diagnostics: DiagnosticsSummary,
)

internal class RuntimeHealthChecker {
    fun snapshot(
        installState: RuntimeInstallState,
        launchState: RuntimeLaunchState,
        payloadAvailable: Boolean,
        processAlive: Boolean,
        nowEpochMs: Long,
        extraDetails: List<String> = emptyList(),
    ): RuntimeHealthSnapshot {
        val (lifecycleState, headline, detailLines) = when (installState) {
            RuntimeInstallState.NotInstalled -> Triple(
                HostLifecycleState.InstallRequired,
                "Runtime install required",
                listOf("Bundled runtime payloads have not been installed yet."),
            )

            is RuntimeInstallState.Installing -> Triple(
                HostLifecycleState.Starting,
                "Runtime installing",
                buildList {
                    add("Installing runtime ${installState.targetRuntimeVersion}.")
                    installState.progress?.detail?.let(::add)
                },
            )

            is RuntimeInstallState.Corrupt -> Triple(
                HostLifecycleState.InstallRequired,
                "Runtime repair required",
                listOf(installState.reason),
            )

            is RuntimeInstallState.UpgradeRequired -> Triple(
                HostLifecycleState.InstallRequired,
                "Runtime upgrade required",
                listOf(
                    "Installed runtime ${installState.installedRuntimeVersion} does not match bundled runtime ${installState.requiredRuntimeVersion}.",
                ),
            )

            is RuntimeInstallState.Installed -> launchSnapshot(
                launchState = launchState,
                processAlive = processAlive,
            )
        }

        val details = detailLines + launchMetadata(launchState) + extraDetails
        return RuntimeHealthSnapshot(
            status = RuntimeStatus(
                lifecycleState = lifecycleState,
                payloadAvailable = payloadAvailable,
                lastErrorMessage = when (launchState) {
                    is RuntimeLaunchState.Failed -> launchState.lastError
                    is RuntimeLaunchState.Stopped -> launchState.lastError
                    else -> null
                },
            ),
            diagnostics = DiagnosticsSummary(
                headline = headline,
                details = details,
                lastUpdatedEpochMillis = nowEpochMs,
            ),
        )
    }

    private fun launchSnapshot(
        launchState: RuntimeLaunchState,
        processAlive: Boolean,
    ): Triple<HostLifecycleState, String, List<String>> {
        return when (launchState) {
            is RuntimeLaunchState.Starting -> Triple(
                HostLifecycleState.Starting,
                "Runtime starting",
                listOf("Runtime process launch is in progress."),
            )

            is RuntimeLaunchState.Running -> {
                if (processAlive) {
                    Triple(
                        HostLifecycleState.Running,
                        "Runtime running",
                        listOf("Runtime process is active."),
                    )
                } else {
                    Triple(
                        HostLifecycleState.Recovering,
                        "Runtime recovery pending",
                        listOf("Persisted runtime state says the process was running, but no active process handle is available."),
                    )
                }
            }

            is RuntimeLaunchState.Failed -> Triple(
                HostLifecycleState.Error,
                "Runtime failed",
                listOf(launchState.lastError),
            )

            is RuntimeLaunchState.Stopped -> Triple(
                HostLifecycleState.Stopped,
                "Runtime stopped",
                buildList {
                    add("Runtime process is not running.")
                    launchState.lastError?.let(::add)
                },
            )
        }
    }

    private fun launchMetadata(launchState: RuntimeLaunchState): List<String> {
        return buildList {
            when (launchState) {
                is RuntimeLaunchState.Starting -> {
                    add("Command: ${launchState.command.joinToString(" ")}")
                    add("stdout: ${launchState.stdoutPath}")
                    add("stderr: ${launchState.stderrPath}")
                }

                is RuntimeLaunchState.Running -> {
                    add("Command: ${launchState.command.joinToString(" ")}")
                    add("stdout: ${launchState.stdoutPath}")
                    add("stderr: ${launchState.stderrPath}")
                    launchState.pid?.let { pid -> add("pid: $pid") }
                }

                is RuntimeLaunchState.Failed -> {
                    if (launchState.command.isNotEmpty()) {
                        add("Command: ${launchState.command.joinToString(" ")}")
                    }
                    launchState.stdoutPath?.let { add("stdout: $it") }
                    launchState.stderrPath?.let { add("stderr: $it") }
                }

                is RuntimeLaunchState.Stopped -> {
                    if (launchState.command.isNotEmpty()) {
                        add("Command: ${launchState.command.joinToString(" ")}")
                    }
                    launchState.stdoutPath?.let { add("stdout: $it") }
                    launchState.stderrPath?.let { add("stderr: $it") }
                    launchState.exitCode?.let { exitCode -> add("exitCode: $exitCode") }
                }
            }
        }
    }
}
