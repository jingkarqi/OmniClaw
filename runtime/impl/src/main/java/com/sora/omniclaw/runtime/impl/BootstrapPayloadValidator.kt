package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadManifest

class BootstrapPayloadValidator(
    private val requiredFileNames: Set<String> = REQUIRED_FILE_NAMES,
) {
    fun validate(manifest: BundledPayloadManifest?): HostResult<BundledPayloadManifest> {
        if (manifest == null) {
            return failure("Bundled payload manifest is missing at assets/bootstrap/manifest.json.")
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

        val missingRequiredFileNames = requiredFileNames
            .filterNot { requiredFileName ->
                manifest.payloads.any { payload -> payload.fileName == requiredFileName }
            }
            .sorted()
        if (missingRequiredFileNames.isNotEmpty()) {
            return failure(
                "Bundled payload manifest is missing required payload entries: ${missingRequiredFileNames.joinToString(", ")}."
            )
        }

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

        return HostResult.Success(manifest)
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
        val REQUIRED_FILE_NAMES = setOf(
            "debian-rootfs.tar.xz",
            "openclaw-2026.3.13.tgz",
        )
        val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
    }
}
