package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadEntry
import com.sora.omniclaw.core.model.BundledPayloadManifest
import com.sora.omniclaw.core.model.HostLifecycleState
import com.sora.omniclaw.runtime.api.PayloadLocator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StubRuntimeManagerTest {
    @Test
    fun `start moves runtime to running when payload manifest exists`() = runBlocking {
        val manager = StubRuntimeManager(
            payloadLocator = FakePayloadLocator(
                manifest = BundledPayloadManifest(
                    payloads = listOf(
                        BundledPayloadEntry(
                            fileName = "debian-rootfs.tar.xz",
                            sha256 = VALID_ROOT_FS_SHA256,
                            sizeBytes = 42L,
                        ),
                        BundledPayloadEntry(
                            fileName = "openclaw-2026.3.13.tgz",
                            sha256 = VALID_RUNTIME_ARCHIVE_SHA256,
                            sizeBytes = 84L,
                        )
                    )
                )
            )
        )

        assertEquals(HostLifecycleState.InstallRequired, manager.status.value.lifecycleState)
        assertTrue(manager.start() is HostResult.Success<*>)
        assertEquals(HostLifecycleState.Running, manager.status.value.lifecycleState)

        assertTrue(manager.stop() is HostResult.Success<*>)
        assertEquals(HostLifecycleState.Stopped, manager.status.value.lifecycleState)
    }

    @Test
    fun `start reports install required when payload manifest is missing`() = runBlocking {
        val manager = StubRuntimeManager(
            payloadLocator = FakePayloadLocator(manifest = null)
        )

        val result = manager.start()

        assertTrue(result is HostResult.Failure)
        assertEquals(HostLifecycleState.InstallRequired, manager.status.value.lifecycleState)
    }

    @Test
    fun `start reports runtime error when payload manifest is malformed`() = runBlocking {
        val manager = StubRuntimeManager(
            payloadLocator = FakePayloadLocator(
                result = HostResult.Failure(
                    HostError(
                        category = HostErrorCategory.Runtime,
                        message = "Bundled payload manifest is malformed at assets/bootstrap/manifest.json.",
                        recoverable = true,
                    )
                )
            )
        )

        val result = manager.start()

        assertTrue(result is HostResult.Failure)
        assertEquals(HostLifecycleState.Error, manager.status.value.lifecycleState)
        assertEquals(
            "Bundled payload manifest is malformed at assets/bootstrap/manifest.json.",
            manager.status.value.lastErrorMessage,
        )
    }

    private companion object {
        const val VALID_ROOT_FS_SHA256 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val VALID_RUNTIME_ARCHIVE_SHA256 =
            "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"
    }
}

private class FakePayloadLocator(
    manifest: BundledPayloadManifest? = null,
    private val result: HostResult<BundledPayloadManifest?> = HostResult.Success(manifest),
) : PayloadLocator {
    override suspend fun loadBundledPayloadManifest(): HostResult<BundledPayloadManifest?> {
        return result
    }
}
