package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadEntry
import com.sora.omniclaw.core.model.BundledPayloadManifest
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

interface BundledPayloadSource {
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
        val validatedManifest = when (val validationResult = payloadValidator.validateInstallable(manifest)) {
            is HostResult.Success -> validationResult.value
            is HostResult.Failure -> return validationResult
        }
        val runtimeVersion = validatedManifest.runtimeVersion

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
                payloadEntry = validatedManifest.rootFsEntry,
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
                payloadEntry = validatedManifest.runtimeArchiveEntry,
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
                    is InvalidBundledPayloadException -> throwable.message ?: "Bundled payload does not match the manifest."
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
        payloadEntry: BundledPayloadEntry,
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

        val stagedFile = stagePayload(payloadEntry)
        stagedFile.inputStream().use { input ->
            try {
                archiveExtractor.extract(
                    input = input,
                    format = archiveFormat,
                    targetDir = extractionTarget,
                )
            } catch (throwable: Throwable) {
                throw PayloadExtractionException(payloadEntry.fileName)
            }
        }
    }

    private fun stagePayload(payloadEntry: BundledPayloadEntry): File {
        val stagedFile = File(directories.payloadStagingDir, payloadEntry.fileName)
        ensureDirectory(directories.payloadStagingDir)

        when (val openResult = payloadSource.openBundledPayload(payloadEntry.fileName)) {
            is HostResult.Success -> {
                openResult.value.use { input ->
                    stagedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            is HostResult.Failure -> throw MissingBundledPayloadException(openResult.error.message)
        }

        verifyStagedPayload(stagedFile, payloadEntry)
        return stagedFile
    }

    private fun hasExpectedLayout(runtimeVersion: String): Boolean {
        return hasExpectedRootFsLayout() && hasExpectedRuntimeLayout(runtimeVersion)
    }

    private fun ensureExpectedRootFsLayout() {
        if (!hasExpectedRootFsLayout()) {
            throw InvalidExtractedLayoutException("Extracted Debian rootfs layout is invalid.")
        }
    }

    private fun ensureExpectedRuntimeLayout(runtimeVersion: String) {
        if (!hasExpectedRuntimeLayout(runtimeVersion)) {
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
        val path = file.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return
        }

        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    dir: Path,
                    exc: IOException?,
                ): FileVisitResult {
                    if (exc != null) {
                        throw exc
                    }
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun ensureDirectory(file: File) {
        val path = file.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(path)
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Expected directory at '${file.absolutePath}'.")
        }
    }

    private fun verifyStagedPayload(
        stagedFile: File,
        payloadEntry: BundledPayloadEntry,
    ) {
        if (stagedFile.length() != payloadEntry.sizeBytes) {
            throw InvalidBundledPayloadException(
                "Bundled payload '${payloadEntry.fileName}' size does not match the manifest."
            )
        }

        val stagedSha256 = stagedFile.inputStream().use(::sha256)
        if (stagedSha256 != payloadEntry.sha256) {
            throw InvalidBundledPayloadException(
                "Bundled payload '${payloadEntry.fileName}' SHA-256 digest does not match the manifest."
            )
        }
    }

    private fun hasExpectedRootFsLayout(): Boolean {
        return hasRequiredRegularFiles(directories.extractedRootFsDir, ROOT_FS_MARKER_PATHS)
    }

    private fun hasExpectedRuntimeLayout(runtimeVersion: String): Boolean {
        val runtimeRoot = runtimeRoot(runtimeVersion) ?: return false
        return hasRequiredRegularFiles(runtimeRoot, RUNTIME_MARKER_PATHS)
    }

    private fun runtimeRoot(runtimeVersion: String): File? {
        return directories.extractedRuntimeFilesDir
            .listFiles()
            ?.filter { candidate ->
                candidate.name.startsWith("openclaw-$runtimeVersion-") &&
                    Files.isDirectory(candidate.toPath(), LinkOption.NOFOLLOW_LINKS)
            }
            ?.singleOrNull()
    }

    private fun hasRequiredRegularFiles(
        rootDir: File,
        markerPaths: List<String>,
    ): Boolean {
        val rootPath = rootDir.toPath().toAbsolutePath().normalize()
        return markerPaths.all { markerPath ->
            val candidatePath = rootPath.resolve(markerPath).normalize()
            candidatePath.startsWith(rootPath) &&
                hasNoSymbolicLinkAncestors(rootPath, candidatePath.parent) &&
                isRegularFile(candidatePath)
        }
    }

    private fun isRegularFile(path: Path): Boolean {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
    }

    private fun hasNoSymbolicLinkAncestors(
        rootPath: Path,
        path: Path?,
    ): Boolean {
        if (path == null) {
            return true
        }

        var currentPath = rootPath
        val relativePath = rootPath.relativize(path)
        for (index in 0 until relativePath.nameCount) {
            currentPath = currentPath.resolve(relativePath.getName(index))
            if (Files.isSymbolicLink(currentPath)) {
                return false
            }
        }
        return true
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            if (read > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
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

    private companion object {
        val ROOT_FS_MARKER_PATHS = listOf(
            "proot-distro/debian.sh",
            "installed-rootfs/debian/etc/debian_version",
            "installed-rootfs/debian/usr/lib/os-release",
            "installed-rootfs/debian/usr/bin/bash",
        )
        val RUNTIME_MARKER_PATHS = listOf(
            "package.json",
            "pnpm-lock.yaml",
            "pnpm-workspace.yaml",
            "apps/android/app/src/main/AndroidManifest.xml",
        )
    }
}

private class MissingBundledPayloadException(
    override val message: String,
) : RuntimeException(message)

private class InvalidExtractedLayoutException(
    override val message: String,
) : RuntimeException(message)

private class InvalidBundledPayloadException(
    override val message: String,
) : RuntimeException(message)

private class PayloadExtractionException(
    val fileName: String,
) : RuntimeException("Failed to extract bundled payload '$fileName'.")
