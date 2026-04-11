package com.sora.omniclaw.runtime.impl

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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
            entry.isDirectory -> createDirectory(root, outputPath, entry.name)
            entry.isSymbolicLink -> createSymbolicLink(root, outputPath, entry)
            entry.isLink || entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO ->
                throw IOException("Archive entry '${entry.name}' uses unsupported type.")
            else -> writeFile(root, outputPath, tar, entry)
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
        root: Path,
        outputPath: Path,
        entry: TarArchiveEntry,
    ) {
        val linkTarget = entry.linkName ?: throw IOException("Archive symbolic link '${entry.name}' is missing a target.")
        val parentPath = outputPath.parent ?: root
        ensurePathDoesNotTraverseSymlink(root, parentPath, entry.name)
        Files.createDirectories(parentPath)
        Files.deleteIfExists(outputPath)
        Files.createSymbolicLink(outputPath, Path.of(linkTarget))
    }

    private fun createDirectory(
        root: Path,
        outputPath: Path,
        entryName: String,
    ) {
        val parentPath = outputPath.parent ?: root
        ensurePathDoesNotTraverseSymlink(root, parentPath, entryName)
        ensurePathIsNotSymbolicLink(outputPath, entryName)
        Files.createDirectories(outputPath)
    }

    private fun writeFile(
        root: Path,
        outputPath: Path,
        input: InputStream,
        entry: TarArchiveEntry,
    ) {
        val parentPath = outputPath.parent ?: root
        ensurePathDoesNotTraverseSymlink(root, parentPath, entry.name)
        ensurePathIsNotSymbolicLink(outputPath, entry.name)
        Files.createDirectories(parentPath)
        Files.newOutputStream(
            outputPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
            LinkOption.NOFOLLOW_LINKS,
        ).use { output ->
            input.copyTo(output)
        }
        applyPosixPermissions(outputPath, entry.mode)
    }

    private fun ensurePathDoesNotTraverseSymlink(
        root: Path,
        outputPath: Path,
        entryName: String,
    ) {
        var currentPath = root
        val relativePath = root.relativize(outputPath)
        for (index in 0 until relativePath.nameCount) {
            currentPath = currentPath.resolve(relativePath.getName(index))
            ensurePathIsNotSymbolicLink(currentPath, entryName)
        }
    }

    private fun ensurePathIsNotSymbolicLink(
        path: Path,
        entryName: String,
    ) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw IOException("Archive entry '$entryName' traverses symbolic link '$path'.")
        }
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
