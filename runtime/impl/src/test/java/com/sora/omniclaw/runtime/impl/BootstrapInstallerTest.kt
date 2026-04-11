package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadEntry
import com.sora.omniclaw.core.model.BundledPayloadManifest
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class BootstrapInstallerTest {
    @Test
    fun `install stages and extracts payloads on a clean workspace`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createValidRootFsArchive()
            val runtimeArchive = createValidRuntimeArchive()
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
            assertTrue(File(directories.extractedRootFsDir, "installed-rootfs/debian/etc/debian_version").isFile)
            assertTrue(File(directories.extractedRootFsDir, "installed-rootfs/debian/usr/lib/os-release").isFile)
            assertTrue(File(directories.extractedRootFsDir, "installed-rootfs/debian/usr/bin/bash").isFile)
            assertTrue(File(directories.extractedRuntimeFilesDir, "openclaw-2026.3.13-1/package.json").isFile)
            assertTrue(File(directories.extractedRuntimeFilesDir, "openclaw-2026.3.13-1/pnpm-lock.yaml").isFile)
            assertTrue(
                File(
                    directories.extractedRuntimeFilesDir,
                    "openclaw-2026.3.13-1/apps/android/app/src/main/AndroidManifest.xml",
                ).isFile
            )
            assertEquals(RuntimeInstallState.Installed("2026.3.13", 1_000L), store.read().getOrThrow())
            assertEquals(1, source.openCount(ROOT_FS_FILE_NAME))
            assertEquals(1, source.openCount(RUNTIME_ARCHIVE_FILE_NAME))
        }
    }

    @Test
    fun `install is idempotent when a valid install already exists`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createValidRootFsArchive()
            val runtimeArchive = createValidRuntimeArchive()
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
            val rootFsArchive = createValidRootFsArchive()
            val runtimeArchive = createValidRuntimeArchive()
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
            assertTrue(File(directories.extractedRootFsDir, "installed-rootfs/debian/etc/debian_version").isFile)
            assertTrue(File(directories.extractedRuntimeFilesDir, "openclaw-2026.3.13-1/package.json").isFile)
        }
    }

    @Test
    fun `install fails fast when a required payload asset is missing`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createValidRootFsArchive()
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
                    RUNTIME_ARCHIVE_FILE_NAME to createValidRuntimeArchive(),
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
                runtimeArchive = createValidRuntimeArchive(),
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
    fun `install fails when staged payload hash does not match manifest`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createValidRootFsArchive()
            val runtimeArchive = createValidRuntimeArchive()
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to rootFsArchive,
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

            val result = installer.install(
                manifestFor(
                    rootFsArchive = rootFsArchive,
                    runtimeArchive = runtimeArchive,
                    rootFsSha256 = sha256("different-rootfs".toByteArray()),
                )
            )

            assertTrue(result is HostResult.Failure)
            assertEquals(
                RuntimeInstallState.Corrupt(
                    reason = "Bundled payload 'debian-rootfs.tar.xz' SHA-256 digest does not match the manifest.",
                    detectedAtEpochMs = 6_000L,
                    lastKnownRuntimeVersion = "2026.3.13",
                ),
                store.read().getOrThrow(),
            )
        }
    }

    @Test
    fun `install fails when staged payload size does not match manifest`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createValidRootFsArchive()
            val runtimeArchive = createValidRuntimeArchive()
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to rootFsArchive,
                    RUNTIME_ARCHIVE_FILE_NAME to runtimeArchive,
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 7_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 7_000L },
            )

            val result = installer.install(
                manifestFor(
                    rootFsArchive = rootFsArchive,
                    runtimeArchive = runtimeArchive,
                    runtimeSizeBytes = runtimeArchive.size.toLong() + 1L,
                )
            )

            assertTrue(result is HostResult.Failure)
            assertEquals(
                RuntimeInstallState.Corrupt(
                    reason = "Bundled payload 'openclaw-2026.3.13.tgz' size does not match the manifest.",
                    detectedAtEpochMs = 7_000L,
                    lastKnownRuntimeVersion = "2026.3.13",
                ),
                store.read().getOrThrow(),
            )
        }
    }

    @Test
    fun `install fails when extracted rootfs layout is missing required marker files`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val incompleteRootFsArchive = createTarXzArchive(
                "installed-rootfs/debian/etc/debian_version" to "12.10\n",
            )
            val runtimeArchive = createValidRuntimeArchive()
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to incompleteRootFsArchive,
                    RUNTIME_ARCHIVE_FILE_NAME to runtimeArchive,
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 8_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 8_000L },
            )

            val result = installer.install(manifestFor(incompleteRootFsArchive, runtimeArchive))

            assertTrue(result is HostResult.Failure)
            assertEquals(
                RuntimeInstallState.Corrupt(
                    reason = "Extracted Debian rootfs layout is invalid.",
                    detectedAtEpochMs = 8_000L,
                    lastKnownRuntimeVersion = "2026.3.13",
                ),
                store.read().getOrThrow(),
            )
        }
    }

    @Test
    fun `install fails when extracted runtime layout is missing required marker files`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createValidRootFsArchive()
            val incompleteRuntimeArchive = createTarGzArchive(
                "openclaw-2026.3.13-1/package.json" to packageJson(DEFAULT_RUNTIME_VERSION),
            )
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to rootFsArchive,
                    RUNTIME_ARCHIVE_FILE_NAME to incompleteRuntimeArchive,
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 9_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 9_000L },
            )

            val result = installer.install(manifestFor(rootFsArchive, incompleteRuntimeArchive))

            assertTrue(result is HostResult.Failure)
            assertEquals(
                RuntimeInstallState.Corrupt(
                    reason = "Extracted OpenClaw runtime layout is invalid.",
                    detectedAtEpochMs = 9_000L,
                    lastKnownRuntimeVersion = "2026.3.13",
                ),
                store.read().getOrThrow(),
            )
        }
    }

    @Test
    fun `install derives runtime archive filename and version from the manifest entry`() {
        withTempDir { tempRoot ->
            val runtimeVersion = "2030.1.2"
            val runtimeArchiveFileName = runtimeArchiveFileName(runtimeVersion)
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createValidRootFsArchive()
            val runtimeArchive = createValidRuntimeArchive(runtimeVersion = runtimeVersion)
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to rootFsArchive,
                    runtimeArchiveFileName to runtimeArchive,
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 10_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 10_000L },
            )

            val result = installer.install(
                manifestFor(
                    rootFsArchive = rootFsArchive,
                    runtimeArchive = runtimeArchive,
                    runtimeArchiveFileName = runtimeArchiveFileName,
                )
            )

            assertTrue(result is HostResult.Success<*>)
            assertEquals(
                RuntimeInstallState.Installed(
                    runtimeVersion = runtimeVersion,
                    installedAtEpochMs = 10_000L,
                ),
                (result as HostResult.Success<*>).value,
            )
            assertTrue(File(directories.payloadStagingDir, runtimeArchiveFileName).isFile)
            assertTrue(File(directories.extractedRuntimeFilesDir, "openclaw-2030.1.2-1/package.json").isFile)
        }
    }

    @Test
    fun `install rejects a symlinked runtime root during idempotence checks and preserves its target`() {
        assumeSymbolicLinksSupported()

        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createValidRootFsArchive()
            val runtimeArchive = createValidRuntimeArchive()
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to rootFsArchive,
                    RUNTIME_ARCHIVE_FILE_NAME to runtimeArchive,
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 11_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 11_000L },
            )
            val manifest = manifestFor(rootFsArchive, runtimeArchive)

            assertTrue(installer.install(manifest) is HostResult.Success<*>)

            val realRuntimeRoot = File(directories.extractedRuntimeFilesDir, "openclaw-2026.3.13-1")
            assertTrue(realRuntimeRoot.deleteRecursively())
            val externalRuntimeRoot = File(tempRoot, "external-runtime/openclaw-2026.3.13-1")
            createRuntimeLayout(externalRuntimeRoot, DEFAULT_RUNTIME_VERSION)
            Files.createSymbolicLink(realRuntimeRoot.toPath(), externalRuntimeRoot.toPath())

            val result = installer.install(manifest)

            assertTrue(result is HostResult.Success<*>)
            assertEquals(2, source.openCount(ROOT_FS_FILE_NAME))
            assertEquals(2, source.openCount(RUNTIME_ARCHIVE_FILE_NAME))
            assertTrue(File(externalRuntimeRoot, "package.json").isFile)
            assertFalse(Files.isSymbolicLink(realRuntimeRoot.toPath()))
            assertTrue(File(realRuntimeRoot, "package.json").isFile)
        }
    }

    @Test
    fun `install rejects rootfs markers that are only reachable through symlink ancestors`() {
        assumeSymbolicLinksSupported()

        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val rootFsArchive = createValidRootFsArchive()
            val runtimeArchive = createValidRuntimeArchive()
            val source = TestBundledPayloadSource(
                payloads = mapOf(
                    ROOT_FS_FILE_NAME to rootFsArchive,
                    RUNTIME_ARCHIVE_FILE_NAME to runtimeArchive,
                )
            )
            val store = RuntimeInstallStateStore(directories.installStateFile, nowEpochMs = { 12_000L })
            val installer = BootstrapInstaller(
                payloadSource = source,
                directories = directories,
                installStateStore = store,
                archiveExtractor = ArchiveExtractor(),
                nowEpochMs = { 12_000L },
            )
            val manifest = manifestFor(rootFsArchive, runtimeArchive)

            assertTrue(installer.install(manifest) is HostResult.Success<*>)

            val rootFsPath = File(directories.extractedRootFsDir, "installed-rootfs/debian")
            assertTrue(rootFsPath.deleteRecursively())
            val externalRootFsPath = File(tempRoot, "external-rootfs/debian")
            createRootFsLayout(externalRootFsPath)
            requireNotNull(rootFsPath.parentFile).mkdirs()
            Files.createSymbolicLink(rootFsPath.toPath(), externalRootFsPath.toPath())

            val result = installer.install(manifest)

            assertTrue(result is HostResult.Success<*>)
            assertEquals(2, source.openCount(ROOT_FS_FILE_NAME))
            assertEquals(2, source.openCount(RUNTIME_ARCHIVE_FILE_NAME))
            assertTrue(File(externalRootFsPath, "etc/debian_version").isFile)
            assertFalse(Files.isSymbolicLink(rootFsPath.toPath()))
            assertTrue(File(rootFsPath, "etc/debian_version").isFile)
        }
    }

    private fun manifestFor(
        rootFsArchive: ByteArray,
        runtimeArchive: ByteArray,
        runtimeArchiveFileName: String = RUNTIME_ARCHIVE_FILE_NAME,
        rootFsSha256: String = sha256(rootFsArchive),
        rootFsSizeBytes: Long = rootFsArchive.size.toLong(),
        runtimeSha256: String = sha256(runtimeArchive),
        runtimeSizeBytes: Long = runtimeArchive.size.toLong(),
    ): BundledPayloadManifest {
        return BundledPayloadManifest(
            payloads = listOf(
                BundledPayloadEntry(
                    fileName = ROOT_FS_FILE_NAME,
                    sha256 = rootFsSha256,
                    sizeBytes = rootFsSizeBytes,
                ),
                BundledPayloadEntry(
                    fileName = runtimeArchiveFileName,
                    sha256 = runtimeSha256,
                    sizeBytes = runtimeSizeBytes,
                ),
            )
        )
    }

    private fun createValidRootFsArchive(): ByteArray {
        return createTarXzArchive(
            "installed-rootfs/debian/etc/debian_version" to "12.10\n",
            "installed-rootfs/debian/usr/lib/os-release" to "ID=debian\n",
            "installed-rootfs/debian/usr/bin/bash" to "#!/bin/sh\n",
            "proot-distro/debian.sh" to "#!/bin/sh\n",
        )
    }

    private fun createValidRuntimeArchive(runtimeVersion: String = DEFAULT_RUNTIME_VERSION): ByteArray {
        return createTarGzArchive(
            "openclaw-$runtimeVersion-1/package.json" to packageJson(runtimeVersion),
            "openclaw-$runtimeVersion-1/pnpm-lock.yaml" to "lockfileVersion: '9.0'\n",
            "openclaw-$runtimeVersion-1/pnpm-workspace.yaml" to "packages:\n  - apps/*\n",
            "openclaw-$runtimeVersion-1/apps/android/app/src/main/AndroidManifest.xml" to
                """<manifest package="com.sora.omniclaw" />""",
        )
    }

    private fun packageJson(runtimeVersion: String): String {
        return """
            {
              "name": "openclaw",
              "version": "$runtimeVersion"
            }
        """.trimIndent()
    }

    private fun createRuntimeLayout(
        runtimeRoot: File,
        runtimeVersion: String,
    ) {
        writeFile(runtimeRoot, "package.json", packageJson(runtimeVersion))
        writeFile(runtimeRoot, "pnpm-lock.yaml", "lockfileVersion: '9.0'\n")
        writeFile(runtimeRoot, "pnpm-workspace.yaml", "packages:\n  - apps/*\n")
        writeFile(runtimeRoot, "apps/android/app/src/main/AndroidManifest.xml", """<manifest package="com.sora.omniclaw" />""")
    }

    private fun createRootFsLayout(rootFsPath: File) {
        writeFile(rootFsPath, "etc/debian_version", "12.10\n")
        writeFile(rootFsPath, "usr/lib/os-release", "ID=debian\n")
        writeFile(rootFsPath, "usr/bin/bash", "#!/bin/sh\n")
    }

    private fun writeFile(
        root: File,
        relativePath: String,
        contents: String,
    ) {
        val file = File(root, relativePath)
        requireNotNull(file.parentFile).mkdirs()
        file.writeText(contents)
    }

    private fun assumeSymbolicLinksSupported() {
        val probeDir = createTempDirectory(prefix = "bootstrap-installer-symlink-probe-")
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
            } catch (_: java.io.IOException) {
                assumeTrue("Symbolic links require elevated privileges in this environment.", false)
            }
        } finally {
            probeDir.toFile().deleteRecursively()
        }
    }

    private fun runtimeArchiveFileName(runtimeVersion: String): String {
        return "openclaw-$runtimeVersion.tgz"
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
        val tempDir = createTempDirectory(prefix = "bootstrap-installer-").toFile()
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
        const val DEFAULT_RUNTIME_VERSION = "2026.3.13"
        const val ROOT_FS_FILE_NAME = "debian-rootfs.tar.xz"
        const val RUNTIME_ARCHIVE_FILE_NAME = "openclaw-$DEFAULT_RUNTIME_VERSION.tgz"
    }
}
