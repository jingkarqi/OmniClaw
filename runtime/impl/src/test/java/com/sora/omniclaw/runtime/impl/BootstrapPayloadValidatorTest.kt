package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadEntry
import com.sora.omniclaw.core.model.BundledPayloadManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapPayloadValidatorTest {
    private val validator = BootstrapPayloadValidator()

    @Test
    fun `validate returns failure when manifest is missing`() {
        val result = validator.validate(null)

        assertPayloadFailure(
            result = result,
            message = "Bundled payload manifest is missing at assets/bootstrap/manifest.json.",
        )
    }

    @Test
    fun `validate returns failure when required payload entry is missing`() {
        val result = validator.validate(
            BundledPayloadManifest(
                payloads = listOf(rootFsEntry())
            )
        )

        assertPayloadFailure(
            result = result,
            message = "Bundled payload manifest is missing required payload entries: openclaw-2026.3.13.tgz.",
        )
    }

    @Test
    fun `validate returns failure when required payload entry is duplicated`() {
        val result = validator.validate(
            BundledPayloadManifest(
                payloads = listOf(
                    rootFsEntry(),
                    rootFsEntry(),
                    runtimeArchiveEntry(),
                )
            )
        )

        assertPayloadFailure(
            result = result,
            message = "Bundled payload manifest contains duplicate payload entries: debian-rootfs.tar.xz.",
        )
    }

    @Test
    fun `validate returns failure when payload hash is not sha256`() {
        val result = validator.validate(
            validManifest().copy(
                payloads = listOf(
                    rootFsEntry(sha256 = "abc123"),
                    runtimeArchiveEntry(),
                )
            )
        )

        assertPayloadFailure(
            result = result,
            message = "Bundled payload 'debian-rootfs.tar.xz' has an invalid SHA-256 digest.",
        )
    }

    @Test
    fun `validate returns failure when payload size is not positive`() {
        val result = validator.validate(
            validManifest().copy(
                payloads = listOf(
                    rootFsEntry(sizeBytes = 0L),
                    runtimeArchiveEntry(),
                )
            )
        )

        assertPayloadFailure(
            result = result,
            message = "Bundled payload 'debian-rootfs.tar.xz' must declare a positive size.",
        )
    }

    private fun assertPayloadFailure(
        result: HostResult<BundledPayloadManifest>,
        message: String,
    ) {
        assertTrue(result is HostResult.Failure)
        assertEquals(
            HostErrorCategory.Runtime,
            (result as HostResult.Failure).error.category,
        )
        assertTrue(result.error.recoverable)
        assertEquals(message, result.error.message)
    }

    private fun validManifest(): BundledPayloadManifest {
        return BundledPayloadManifest(
            payloads = listOf(
                rootFsEntry(),
                runtimeArchiveEntry(),
            )
        )
    }

    private fun rootFsEntry(
        sha256: String = VALID_SHA256,
        sizeBytes: Long = 42L,
    ): BundledPayloadEntry {
        return BundledPayloadEntry(
            fileName = ROOT_FS_FILE_NAME,
            sha256 = sha256,
            sizeBytes = sizeBytes,
        )
    }

    private fun runtimeArchiveEntry(
        sha256: String = VALID_SHA256.reversed(),
        sizeBytes: Long = 84L,
    ): BundledPayloadEntry {
        return BundledPayloadEntry(
            fileName = RUNTIME_ARCHIVE_FILE_NAME,
            sha256 = sha256,
            sizeBytes = sizeBytes,
        )
    }

    private companion object {
        const val ROOT_FS_FILE_NAME = "debian-rootfs.tar.xz"
        const val RUNTIME_ARCHIVE_FILE_NAME = "openclaw-2026.3.13.tgz"
        const val VALID_SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
