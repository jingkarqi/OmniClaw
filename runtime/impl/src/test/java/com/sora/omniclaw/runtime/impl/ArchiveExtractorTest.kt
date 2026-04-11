package com.sora.omniclaw.runtime.impl

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ArchiveExtractorTest {
    private val extractor = ArchiveExtractor()

    @Test
    fun `extract preserves standalone absolute symbolic links`() {
        assumeSymbolicLinksSupported()

        withTempDir { tempRoot ->
            val targetDir = File(tempRoot, "extract-root")
            val archive = createTarGzArchive(
                ArchiveEntrySpec.SymbolicLink(
                    path = "usr/bin/awk",
                    target = "/usr/bin/mawk",
                ),
            )

            extractor.extract(
                input = archive.inputStream(),
                format = ArchiveFormat.TAR_GZ,
                targetDir = targetDir,
            )

            val linkPath = targetDir.toPath().resolve("usr/bin/awk")
            assertTrue(Files.isSymbolicLink(linkPath))
            assertEquals(Path.of("/usr/bin/mawk"), Files.readSymbolicLink(linkPath))
        }
    }

    @Test
    fun `extract rejects entries that write through previously extracted symbolic links`() {
        assumeSymbolicLinksSupported()

        withTempDir { tempRoot ->
            val targetDir = File(tempRoot, "extract-root")
            val archive = createTarGzArchive(
                ArchiveEntrySpec.SymbolicLink(
                    path = "linked-dir",
                    target = "../outside-root",
                ),
                ArchiveEntrySpec.RegularFile(
                    path = "linked-dir/payload.txt",
                    contents = "payload",
                ),
            )

            val error = assertThrows(IOException::class.java) {
                extractor.extract(
                    input = archive.inputStream(),
                    format = ArchiveFormat.TAR_GZ,
                    targetDir = targetDir,
                )
            }

            val linkPath = targetDir.toPath().resolve("linked-dir")
            assertTrue(Files.isSymbolicLink(linkPath))
            assertEquals(Path.of("../outside-root"), Files.readSymbolicLink(linkPath))
            assertTrue(error.message.orEmpty().contains("traverses symbolic link"))
            assertFalse(File(tempRoot, "outside-root/payload.txt").exists())
        }
    }

    @Test
    fun `extract rejects unsupported hard link entries`() {
        withTempDir { tempRoot ->
            val targetDir = File(tempRoot, "extract-root")
            val archive = createTarGzArchive(
                ArchiveEntrySpec.HardLink(
                    path = "hard-link",
                    target = "source.txt",
                ),
            )

            val error = assertThrows(IOException::class.java) {
                extractor.extract(
                    input = archive.inputStream(),
                    format = ArchiveFormat.TAR_GZ,
                    targetDir = targetDir,
                )
            }

            assertTrue(error.message.orEmpty().contains("unsupported type"))
            assertFalse(File(targetDir, "hard-link").exists())
        }
    }

    private fun assumeSymbolicLinksSupported() {
        val probeDir = createTempDirectory(prefix = "archive-extractor-symlink-probe-")
        try {
            Files.createDirectories(probeDir.resolve("target"))
            try {
                Files.createSymbolicLink(
                    probeDir.resolve("link"),
                    Path.of("target"),
                )
            } catch (_: UnsupportedOperationException) {
                assumeTrue("Symbolic links are not supported in this environment.", false)
            } catch (_: SecurityException) {
                assumeTrue("Symbolic links require elevated privileges in this environment.", false)
            } catch (_: IOException) {
                assumeTrue("Symbolic links require elevated privileges in this environment.", false)
            }
        } finally {
            probeDir.toFile().deleteRecursively()
        }
    }

    private fun createTarGzArchive(vararg entries: ArchiveEntrySpec): ByteArray {
        val output = ByteArrayOutputStream()
        GzipCompressorOutputStream(output).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                for (entrySpec in entries) {
                    when (entrySpec) {
                        is ArchiveEntrySpec.RegularFile -> {
                            val bytes = entrySpec.contents.toByteArray(Charsets.UTF_8)
                            val entry = TarArchiveEntry(entrySpec.path).apply {
                                size = bytes.size.toLong()
                            }
                            tar.putArchiveEntry(entry)
                            tar.write(bytes)
                            tar.closeArchiveEntry()
                        }

                        is ArchiveEntrySpec.SymbolicLink -> {
                            val entry = TarArchiveEntry(entrySpec.path, TarConstants.LF_SYMLINK).apply {
                                linkName = entrySpec.target
                            }
                            tar.putArchiveEntry(entry)
                            tar.closeArchiveEntry()
                        }

                        is ArchiveEntrySpec.HardLink -> {
                            val entry = TarArchiveEntry(entrySpec.path, TarConstants.LF_LINK).apply {
                                linkName = entrySpec.target
                            }
                            tar.putArchiveEntry(entry)
                            tar.closeArchiveEntry()
                        }
                    }
                }
                tar.finish()
            }
        }
        return output.toByteArray()
    }

    private fun withTempDir(block: (File) -> Unit) {
        val tempDir = createTempDirectory(prefix = "archive-extractor-").toFile()
        try {
            block(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private sealed interface ArchiveEntrySpec {
        data class RegularFile(
            val path: String,
            val contents: String,
        ) : ArchiveEntrySpec

        data class SymbolicLink(
            val path: String,
            val target: String,
        ) : ArchiveEntrySpec

        data class HardLink(
            val path: String,
            val target: String,
        ) : ArchiveEntrySpec
    }
}
