package com.sora.omniclaw.runtime.impl

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

internal enum class ArchiveFormat {
    TAR_GZ,
    TAR_XZ,
}

internal class ArchiveExtractor {
    fun extract(
        input: InputStream,
        format: ArchiveFormat,
        targetDir: File,
    ) {
        ensureTargetDirectory(targetDir)
        openTarInput(input, format).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                extractEntry(
                    root = targetDir.toPath().toAbsolutePath().normalize(),
                    tar = tar,
                    entry = entry,
                )
                entry = tar.nextTarEntry
            }
        }
    }

    private fun openTarInput(
        input: InputStream,
        format: ArchiveFormat,
    ): TarArchiveInputStream {
        val archiveStream = when (format) {
            ArchiveFormat.TAR_GZ -> GzipCompressorInputStream(input.buffered())
            ArchiveFormat.TAR_XZ -> XZCompressorInputStream(input.buffered())
        }
        return TarArchiveInputStream(archiveStream)
    }

    private fun extractEntry(
        root: Path,
        tar: TarArchiveInputStream,
        entry: TarArchiveEntry,
    ) {
        val outputPath = resolveEntryPath(root, entry.name)

        when {
            entry.isDirectory -> Files.createDirectories(outputPath)
            entry.isSymbolicLink -> createSymbolicLink(outputPath, entry)
            else -> writeFile(outputPath, tar, entry.mode)
        }
    }

    private fun resolveEntryPath(
        root: Path,
        entryName: String,
    ): Path {
        val outputPath = root.resolve(entryName).normalize()
        if (!outputPath.startsWith(root)) {
            throw IOException("Archive entry '$entryName' escapes the target directory.")
        }
        return outputPath
    }

    private fun createSymbolicLink(
        outputPath: Path,
        entry: TarArchiveEntry,
    ) {
        val linkTarget = entry.linkName ?: throw IOException("Archive symbolic link '${entry.name}' is missing a target.")
        Files.createDirectories(outputPath.parent)
        Files.deleteIfExists(outputPath)
        Files.createSymbolicLink(outputPath, Path.of(linkTarget))
    }

    private fun writeFile(
        outputPath: Path,
        input: InputStream,
        mode: Int,
    ) {
        Files.createDirectories(outputPath.parent)
        Files.newOutputStream(outputPath).use { output ->
            input.copyTo(output)
        }
        applyPosixPermissions(outputPath, mode)
    }

    private fun applyPosixPermissions(
        path: Path,
        mode: Int,
    ) {
        val view = Files.getFileAttributeView(path, PosixFileAttributeView::class.java) ?: return
        val permissions = mutableSetOf<PosixFilePermission>()

        if (mode and 0b100_000_000 != 0) permissions += PosixFilePermission.OWNER_READ
        if (mode and 0b010_000_000 != 0) permissions += PosixFilePermission.OWNER_WRITE
        if (mode and 0b001_000_000 != 0) permissions += PosixFilePermission.OWNER_EXECUTE
        if (mode and 0b000_100_000 != 0) permissions += PosixFilePermission.GROUP_READ
        if (mode and 0b000_010_000 != 0) permissions += PosixFilePermission.GROUP_WRITE
        if (mode and 0b000_001_000 != 0) permissions += PosixFilePermission.GROUP_EXECUTE
        if (mode and 0b000_000_100 != 0) permissions += PosixFilePermission.OTHERS_READ
        if (mode and 0b000_000_010 != 0) permissions += PosixFilePermission.OTHERS_WRITE
        if (mode and 0b000_000_001 != 0) permissions += PosixFilePermission.OTHERS_EXECUTE

        view.setPermissions(permissions)
    }

    private fun ensureTargetDirectory(targetDir: File) {
        if (targetDir.exists() && !targetDir.isDirectory) {
            throw IOException("Extraction target '${targetDir.absolutePath}' is not a directory.")
        }
        if (!targetDir.exists() && !targetDir.mkdirs() && !targetDir.isDirectory) {
            throw IOException("Failed to create extraction target '${targetDir.absolutePath}'.")
        }
    }
}
