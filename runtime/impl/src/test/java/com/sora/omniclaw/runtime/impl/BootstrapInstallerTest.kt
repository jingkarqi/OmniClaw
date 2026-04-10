package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadEntry
import com.sora.omniclaw.core.model.BundledPayloadManifest
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapInstallerTest {
    @Test
    fun `install stages and extracts payloads on a clean workspace`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createTarXzArchive(
                "installed-rootfs/debian/etc/os-release" to "NAME=Debian\n",
                "proot-distro/debian.sh" to "#!/bin/sh\n",
            )
            val runtimeArchive = createTarGzArchive(
                "openclaw-2026.3.13-1/package.json" to """{"name":"openclaw"}""",
                "openclaw-2026.3.13-1/AGENTS.md" to "agent guidance\n",
            )
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to rootFsArchive,
                    RUNTIME_ARCHIVE_FILE_NAME to runtimeArchive,
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 1_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 1_000L },
            )

            val result = installer.install(
                manifest = manifestFor(rootFsArchive, runtimeArchive)
            )

            assertTrue(result is HostResult.Success<*>)
            assertEquals(
                RuntimeInstallState.Installed(
                    runtimeVersion = "2026.3.13",
                    installedAtEpochMs = 1_000L,
                ),
                (result as HostResult.Success<*>).value,
            )
            assertTrue(File(directories.payloadStagingDir, ROOT_FS_FILE_NAME).isFile)
            assertTrue(File(directories.payloadStagingDir, RUNTIME_ARCHIVE_FILE_NAME).isFile)
            assertTrue(File(directories.extractedRootFsDir, "installed-rootfs/debian/etc/os-release").isFile)
            assertTrue(File(directories.extractedRuntimeFilesDir, "openclaw-2026.3.13-1/package.json").isFile)
            assertEquals(RuntimeInstallState.Installed("2026.3.13", 1_000L), store.read().getOrThrow())
            assertEquals(1, source.openCount(ROOT_FS_FILE_NAME))
            assertEquals(1, source.openCount(RUNTIME_ARCHIVE_FILE_NAME))
        }
    }

    @Test
    fun `install is idempotent when a valid install already exists`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createTarXzArchive(
                "installed-rootfs/debian/etc/os-release" to "NAME=Debian\n",
            )
            val runtimeArchive = createTarGzArchive(
                "openclaw-2026.3.13-1/package.json" to """{"name":"openclaw"}""",
            )
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to rootFsArchive,
                    RUNTIME_ARCHIVE_FILE_NAME to runtimeArchive,
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 2_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 2_000L },
            )
            val manifest = manifestFor(rootFsArchive, runtimeArchive)

            assertTrue(installer.install(manifest) is HostResult.Success<*>)
            val secondResult = installer.install(manifest)

            assertTrue(secondResult is HostResult.Success<*>)
            assertEquals(1, source.openCount(ROOT_FS_FILE_NAME))
            assertEquals(1, source.openCount(RUNTIME_ARCHIVE_FILE_NAME))
        }
    }

    @Test
    fun `install recovers from partial extracted state`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createTarXzArchive(
                "installed-rootfs/debian/etc/os-release" to "NAME=Debian\n",
            )
            val runtimeArchive = createTarGzArchive(
                "openclaw-2026.3.13-1/package.json" to """{"name":"openclaw"}""",
            )
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to rootFsArchive,
                    RUNTIME_ARCHIVE_FILE_NAME to runtimeArchive,
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 3_000L })
            store.setInstalling(
                targetRuntimeVersion = "2026.3.13",
                startedAtEpochMs = 100L,
                updatedAtEpochMs = 200L,
                progress = RuntimeInstallProgress(
                    step = "extract-rootfs",
                    completedSteps = 1,
                    totalSteps = 2,
                ),
            )
            val staleFile = File(directories.extractedRootFsDir, "partial.txt").apply {
                requireNotNull(parentFile).mkdirs()
                writeText("stale")
            }
            File(directories.extractedRuntimeFilesDir, "broken.txt").apply {
                requireNotNull(parentFile).mkdirs()
                writeText("stale")
            }
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 3_000L },
            )

            val result = installer.install(manifestFor(rootFsArchive, runtimeArchive))

            assertTrue(result is HostResult.Success<*>)
            assertFalse(staleFile.exists())
            assertTrue(File(directories.extractedRootFsDir, "installed-rootfs/debian/etc/os-release").isFile)
            assertTrue(File(directories.extractedRuntimeFilesDir, "openclaw-2026.3.13-1/package.json").isFile)
        }
    }

    @Test
    fun `install fails fast when a required payload asset is missing`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createTarXzArchive(
                "installed-rootfs/debian/etc/os-release" to "NAME=Debian\n",
            )
            val source = TestBundledPayloadSource(
                payloads = mapOf(ROOT_FS_FILE_NAME to rootFsArchive)
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 4_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 4_000L },
            )
            val manifest = manifestFor(rootFsArchive, createTarGzArchive())

            val result = installer.install(manifest)

            assertTrue(result is HostResult.Failure)
            assertEquals(
                RuntimeInstallState.Corrupt(
                    reason = "Bundled payload 'openclaw-2026.3.13.tgz' is missing at assets/bootstrap/openclaw-2026.3.13.tgz.",
                    detectedAtEpochMs = 4_000L,
                    lastKnownRuntimeVersion = "2026.3.13",
                ),
                store.read().getOrThrow(),
            )
        }
    }

    @Test
    fun `install fails when archive extraction raises an error`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to "not-an-archive".toByteArray(),
                    RUNTIME_ARCHIVE_FILE_NAME to createTarGzArchive(
                        "openclaw-2026.3.13-1/package.json" to """{"name":"openclaw"}""",
                    ),
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 5_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 5_000L },
            )
            val manifest = manifestFor(
                rootFsArchive = "not-an-archive".toByteArray(),
                runtimeArchive = createTarGzArchive(
                    "openclaw-2026.3.13-1/package.json" to """{"name":"openclaw"}""",
                ),
            )

            val result = installer.install(manifest)

            assertTrue(result is HostResult.Failure)
            assertEquals(
                RuntimeInstallState.Corrupt(
                    reason = "Failed to extract bundled payload 'debian-rootfs.tar.xz'.",
                    detectedAtEpochMs = 5_000L,
                    lastKnownRuntimeVersion = "2026.3.13",
                ),
                store.read().getOrThrow(),
            )
        }
    }

    @Test
    fun `install fails when extracted layout does not match expectations`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val badRootFsArchive = createTarXzArchive(
                "unexpected-layout/rootfs.txt" to "broken\n",
            )
            val runtimeArchive = createTarGzArchive(
                "openclaw-2026.3.13-1/package.json" to """{"name":"openclaw"}""",
            )
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to badRootFsArchive,
                    RUNTIME_ARCHIVE_FILE_NAME to runtimeArchive,
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 6_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 6_000L },
            )

            val result = installer.install(manifestFor(badRootFsArchive, runtimeArchive))

            assertTrue(result is HostResult.Failure)
            assertEquals(
                RuntimeInstallState.Corrupt(
                    reason = "Extracted Debian rootfs layout is invalid.",
                    detectedAtEpochMs = 6_000L,
                    lastKnownRuntimeVersion = "2026.3.13",
                ),
                store.read().getOrThrow(),
            )
        }
    }

    private fun manifestFor(
        rootFsArchive: ByteArray,
        runtimeArchive: ByteArray,
    ): BundledPayloadManifest {
        return BundledPayloadManifest(
            payloads = listOf(
                BundledPayloadEntry(
                    fileName = ROOT_FS_FILE_NAME,
                    sha256 = sha256(rootFsArchive),
                    sizeBytes = rootFsArchive.size.toLong(),
                ),
                BundledPayloadEntry(
                    fileName = RUNTIME_ARCHIVE_FILE_NAME,
                    sha256 = sha256(runtimeArchive),
                    sizeBytes = runtimeArchive.size.toLong(),
                ),
            )
        )
    }

    private fun createTarXzArchive(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        XZCompressorOutputStream(output).use { xz ->
            TarArchiveOutputStream(xz).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                for ((path, contents) in files) {
                    val bytes = contents.toByteArray(Charsets.UTF_8)
                    val entry = TarArchiveEntry(path).apply {
                        size = bytes.size.toLong()
                    }
                    tar.putArchiveEntry(entry)
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
                tar.finish()
            }
        }
        return output.toByteArray()
    }

    private fun createTarGzArchive(vararg files: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        GzipCompressorOutputStream(output).use { gzip ->
            TarArchiveOutputStream(gzip).use { tar ->
                tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                for ((path, contents) in files) {
                    val bytes = contents.toByteArray(Charsets.UTF_8)
                    val entry = TarArchiveEntry(path).apply {
                        size = bytes.size.toLong()
                    }
                    tar.putArchiveEntry(entry)
                    tar.write(bytes)
                    tar.closeArchiveEntry()
                }
                tar.finish()
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun HostResult<RuntimeInstallState>.getOrThrow(): RuntimeInstallState {
        assertTrue(this is HostResult.Success<*>)
        return (this as HostResult.Success<RuntimeInstallState>).value
    }

    private fun withTempDir(block: (File) -> Unit) {
        val tempDir = createTempDir(prefix = "bootstrap-installer-")
        try {
            block(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private class TestBundledPayloadSource(
        private val payloads: Map<String, ByteArray>,
    ) : BundledPayloadSource {
        private val openCounts = mutableMapOf<String, Int>()

        override fun openBundledPayload(fileName: String): HostResult<InputStream> {
            val payload = payloads[fileName]
                ?: return HostResult.Failure(
                    HostError(
                        category = HostErrorCategory.Runtime,
                        message = "Bundled payload '$fileName' is missing at assets/bootstrap/$fileName.",
                        recoverable = true,
                    )
                )

            openCounts[fileName] = (openCounts[fileName] ?: 0) + 1
            return HostResult.Success(payload.inputStream())
        }

        fun openCount(fileName: String): Int = openCounts[fileName] ?: 0
    }

    private companion object {
        const val ROOT_FS_FILE_NAME = "debian-rootfs.tar.xz"
        const val RUNTIME_ARCHIVE_FILE_NAME = "openclaw-2026.3.13.tgz"
    }
}
