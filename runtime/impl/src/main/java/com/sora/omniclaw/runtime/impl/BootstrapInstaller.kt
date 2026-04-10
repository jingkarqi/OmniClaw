package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadManifest
import java.io.File
import java.io.IOException

internal interface BundledPayloadSource {
    fun openBundledPayload(fileName: String): HostResult<java.io.InputStream>
}

internal class BootstrapInstaller(
    private val payloadSource: BundledPayloadSource,
    private val directories: RuntimeDirectories,
    private val installStateStore: RuntimeInstallStateStore,
    private val archiveExtractor: ArchiveExtractor = ArchiveExtractor(),
    private val payloadValidator: BootstrapPayloadValidator = BootstrapPayloadValidator(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    fun install(manifest: BundledPayloadManifest?): HostResult<RuntimeInstallState> {
        val validatedManifest = when (val validationResult = payloadValidator.validate(manifest)) {
            is HostResult.Success -> validationResult.value
            is HostResult.Failure -> return validationResult
        }
        val runtimeVersion = extractRuntimeVersion(validatedManifest)
            ?: return HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Runtime,
                    message = "Bundled runtime archive name does not encode a runtime version.",
                    recoverable = true,
                )
            )

        when (val currentState = installStateStore.read()) {
            is HostResult.Success -> {
                val installedState = currentState.value as? RuntimeInstallState.Installed
                if (installedState?.runtimeVersion == runtimeVersion && hasExpectedLayout(runtimeVersion)) {
                    return currentState
                }
            }

            is HostResult.Failure -> return currentState
        }

        val startedAtEpochMs = nowEpochMs()
        return try {
            resetInstallWorkspace()
            stageAndExtractPayload(
                fileName = ROOT_FS_FILE_NAME,
                runtimeVersion = runtimeVersion,
                startedAtEpochMs = startedAtEpochMs,
                updatedAtEpochMs = startedAtEpochMs,
                progress = RuntimeInstallProgress(
                    step = "extract-rootfs",
                    completedSteps = 1,
                    totalSteps = 2,
                    detail = "Extracting bundled Debian rootfs.",
                ),
                archiveFormat = ArchiveFormat.TAR_XZ,
                extractionTarget = directories.extractedRootFsDir,
            )
            ensureExpectedRootFsLayout()

            stageAndExtractPayload(
                fileName = RUNTIME_ARCHIVE_FILE_NAME,
                runtimeVersion = runtimeVersion,
                startedAtEpochMs = startedAtEpochMs,
                updatedAtEpochMs = nowEpochMs(),
                progress = RuntimeInstallProgress(
                    step = "extract-runtime",
                    completedSteps = 2,
                    totalSteps = 2,
                    detail = "Extracting bundled OpenClaw runtime files.",
                ),
                archiveFormat = ArchiveFormat.TAR_GZ,
                extractionTarget = directories.extractedRuntimeFilesDir,
            )
            ensureExpectedRuntimeLayout(runtimeVersion)

            when (val installedResult = installStateStore.setInstalled(runtimeVersion, nowEpochMs())) {
                is HostResult.Success -> installedResult
                is HostResult.Failure -> installedResult
            }
        } catch (throwable: Throwable) {
            installFailure(
                reason = when (throwable) {
                    is MissingBundledPayloadException -> throwable.message ?: "Bundled payload is missing."
                    is PayloadExtractionException -> "Failed to extract bundled payload '${throwable.fileName}'."
                    is InvalidExtractedLayoutException -> throwable.message ?: "Extracted runtime layout is invalid."
                    is IOException -> throwable.message ?: "Failed to extract bundled payload."
                    else -> throwable.message ?: "Failed to install bundled payloads."
                },
                runtimeVersion = runtimeVersion,
            )
        }
    }

    private fun stageAndExtractPayload(
        fileName: String,
        runtimeVersion: String,
        startedAtEpochMs: Long,
        updatedAtEpochMs: Long,
        progress: RuntimeInstallProgress,
        archiveFormat: ArchiveFormat,
        extractionTarget: File,
    ) {
        when (val installingResult = installStateStore.setInstalling(
            targetRuntimeVersion = runtimeVersion,
            startedAtEpochMs = startedAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            progress = progress,
        )) {
            is HostResult.Success -> Unit
            is HostResult.Failure -> throw IOException(installingResult.error.message)
        }

        val stagedFile = stagePayload(fileName)
        stagedFile.inputStream().use { input ->
            try {
                archiveExtractor.extract(
                    input = input,
                    format = archiveFormat,
                    targetDir = extractionTarget,
                )
            } catch (throwable: Throwable) {
                throw PayloadExtractionException(fileName)
            }
        }
    }

    private fun stagePayload(fileName: String): File {
        val stagedFile = File(directories.payloadStagingDir, fileName)
        ensureDirectory(directories.payloadStagingDir)

        when (val openResult = payloadSource.openBundledPayload(fileName)) {
            is HostResult.Success -> {
                openResult.value.use { input ->
                    stagedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            is HostResult.Failure -> throw MissingBundledPayloadException(openResult.error.message)
        }

        return stagedFile
    }

    private fun hasExpectedLayout(runtimeVersion: String): Boolean {
        return rootFsMarker().isDirectory && runtimeRoot(runtimeVersion).isDirectory
    }

    private fun ensureExpectedRootFsLayout() {
        if (!rootFsMarker().isDirectory) {
            throw InvalidExtractedLayoutException("Extracted Debian rootfs layout is invalid.")
        }
    }

    private fun ensureExpectedRuntimeLayout(runtimeVersion: String) {
        if (!runtimeRoot(runtimeVersion).isDirectory) {
            throw InvalidExtractedLayoutException("Extracted OpenClaw runtime layout is invalid.")
        }
    }

    private fun resetInstallWorkspace() {
        deleteIfExists(directories.payloadStagingDir)
        deleteIfExists(directories.extractedRootFsDir)
        deleteIfExists(directories.extractedRuntimeFilesDir)
        deleteIfExists(directories.tempDir)
        ensureDirectory(directories.payloadStagingDir)
        ensureDirectory(directories.extractedRootFsDir)
        ensureDirectory(directories.extractedRuntimeFilesDir)
        ensureDirectory(directories.tempDir)
    }

    private fun deleteIfExists(file: File) {
        if (file.exists() && !file.deleteRecursively()) {
            throw IOException("Failed to reset install path '${file.absolutePath}'.")
        }
    }

    private fun ensureDirectory(file: File) {
        if (!file.exists() && !file.mkdirs() && !file.isDirectory) {
            throw IOException("Failed to create directory '${file.absolutePath}'.")
        }
        if (file.exists() && !file.isDirectory) {
            throw IOException("Expected directory at '${file.absolutePath}'.")
        }
    }

    private fun extractRuntimeVersion(manifest: BundledPayloadManifest): String? {
        val runtimeArchiveEntry = manifest.payloads.firstOrNull { it.fileName == RUNTIME_ARCHIVE_FILE_NAME }
            ?: return null
        return RUNTIME_VERSION_REGEX.matchEntire(runtimeArchiveEntry.fileName)?.groupValues?.get(1)
    }

    private fun installFailure(
        reason: String,
        runtimeVersion: String,
    ): HostResult<RuntimeInstallState> {
        return when (val corruptResult = installStateStore.setCorrupt(
            reason = reason,
            detectedAtEpochMs = nowEpochMs(),
            lastKnownRuntimeVersion = runtimeVersion,
        )) {
            is HostResult.Success -> HostResult.Failure(
                HostError(
                    category = HostErrorCategory.Runtime,
                    message = reason,
                    recoverable = true,
                )
            )

            is HostResult.Failure -> corruptResult
        }
    }

    private fun rootFsMarker(): File = File(directories.extractedRootFsDir, "installed-rootfs/debian")

    private fun runtimeRoot(runtimeVersion: String): File =
        File(directories.extractedRuntimeFilesDir, "openclaw-$runtimeVersion-1")

    private companion object {
        const val ROOT_FS_FILE_NAME = "debian-rootfs.tar.xz"
        const val RUNTIME_ARCHIVE_FILE_NAME = "openclaw-2026.3.13.tgz"
        val RUNTIME_VERSION_REGEX = Regex("""openclaw-(\d+\.\d+\.\d+)\.tgz""")
    }
}

private class MissingBundledPayloadException(
    override val message: String,
) : RuntimeException(message)

private class InvalidExtractedLayoutException(
    override val message: String,
) : RuntimeException(message)

private class PayloadExtractionException(
    val fileName: String,
) : RuntimeException("Failed to extract bundled payload '$fileName'.")
