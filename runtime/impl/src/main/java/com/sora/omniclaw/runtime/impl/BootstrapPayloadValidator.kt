package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadEntry
import com.sora.omniclaw.core.model.BundledPayloadManifest

class BootstrapPayloadValidator(
    private val rootFsFileName: String = ROOT_FS_FILE_NAME,
    private val runtimeArchiveFileNameRegex: Regex = RUNTIME_ARCHIVE_FILE_NAME_REGEX,
) {
    fun validate(manifest: BundledPayloadManifest?): HostResult<BundledPayloadManifest> {
        return when (val validatedManifest = validateInstallable(manifest)) {
            is HostResult.Success -> HostResult.Success(validatedManifest.value.manifest)
            is HostResult.Failure -> validatedManifest
        }
    }

    internal fun validateInstallable(manifest: BundledPayloadManifest?): HostResult<ValidatedBootstrapPayloadManifest> {
        if (manifest == null) {
            return failure("Bundled payload manifest is missing at assets/bootstrap/manifest.json.")
        }

        if (manifest.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return failure("Bundled payload manifest schema version ${manifest.schemaVersion} is not supported.")
        }

        if (manifest.payloads.isEmpty()) {
            return failure("Bundled payload manifest must declare bundled payload entries.")
        }

        val duplicateFileNames = manifest.payloads
            .groupBy { it.fileName }
            .filterValues { entries -> entries.size > 1 }
            .keys
            .sorted()
        if (duplicateFileNames.isNotEmpty()) {
            return failure(
                "Bundled payload manifest contains duplicate payload entries: ${duplicateFileNames.joinToString(", ")}."
            )
        }

        val rootFsEntry = manifest.payloads.firstOrNull { payload -> payload.fileName == rootFsFileName }
            ?: return failure(
                "Bundled payload manifest is missing required payload entries: $rootFsFileName."
            )

        val runtimeArchiveEntries = manifest.payloads.filter { payload ->
            runtimeArchiveFileNameRegex.matches(payload.fileName)
        }
        if (runtimeArchiveEntries.isEmpty()) {
            return failure(
                "Bundled payload manifest is missing required payload entries: ${RUNTIME_ARCHIVE_FILE_NAME_EXAMPLE}."
            )
        }
        if (runtimeArchiveEntries.size > 1) {
            return failure(
                "Bundled payload manifest must declare exactly one runtime archive entry matching ${RUNTIME_ARCHIVE_FILE_NAME_EXAMPLE}."
            )
        }
        val runtimeArchiveEntry = runtimeArchiveEntries.single()

        val invalidDigestEntry = manifest.payloads.firstOrNull { payload ->
            !SHA256_REGEX.matches(payload.sha256)
        }
        if (invalidDigestEntry != null) {
            return failure("Bundled payload '${invalidDigestEntry.fileName}' has an invalid SHA-256 digest.")
        }

        val invalidSizeEntry = manifest.payloads.firstOrNull { payload ->
            payload.sizeBytes <= 0L
        }
        if (invalidSizeEntry != null) {
            return failure("Bundled payload '${invalidSizeEntry.fileName}' must declare a positive size.")
        }

        val runtimeVersion = runtimeArchiveFileNameRegex.matchEntire(runtimeArchiveEntry.fileName)
            ?.groupValues
            ?.get(1)
            ?: return failure(
                "Bundled runtime archive '${runtimeArchiveEntry.fileName}' does not encode a runtime version."
            )

        return HostResult.Success(
            ValidatedBootstrapPayloadManifest(
                manifest = manifest,
                rootFsEntry = rootFsEntry,
                runtimeArchiveEntry = runtimeArchiveEntry,
                runtimeVersion = runtimeVersion,
            )
        )
    }

    private fun failure(message: String): HostResult.Failure {
        return HostResult.Failure(
            HostError(
                category = HostErrorCategory.Runtime,
                message = message,
                recoverable = true,
            )
        )
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val ROOT_FS_FILE_NAME = "debian-rootfs.tar.xz"
        const val RUNTIME_ARCHIVE_FILE_NAME_EXAMPLE = "openclaw-<version>.tgz"
        val RUNTIME_ARCHIVE_FILE_NAME_REGEX = Regex("""openclaw-(\d+\.\d+\.\d+)\.tgz""")
        val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
    }
}

internal data class ValidatedBootstrapPayloadManifest(
    val manifest: BundledPayloadManifest,
    val rootFsEntry: BundledPayloadEntry,
    val runtimeArchiveEntry: BundledPayloadEntry,
    val runtimeVersion: String,
)
