package com.sora.omniclaw.runtime.impl

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
                            sha256 = "abc123",
                            sizeBytes = 42L,
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
}

private class FakePayloadLocator(
    private val manifest: BundledPayloadManifest?,
) : PayloadLocator {
    override suspend fun loadBundledPayloadManifest(): HostResult<BundledPayloadManifest?> {
        return HostResult.Success(manifest)
    }
}
