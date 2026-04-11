package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostResult
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetPayloadLocatorTest {
    @Test
    fun `openBundledPayload returns success when asset exists`() {
        val locator = AssetPayloadLocator.forTesting(
            manifestStreamOpener = {
                ByteArrayInputStream("{}".toByteArray(Charsets.UTF_8))
            },
            assetStreamOpener = { fileName ->
                assertEquals("openclaw-2026.3.13.tgz", fileName)
                ByteArrayInputStream("payload-bytes".toByteArray(Charsets.UTF_8))
            }
        )

        val result = locator.openBundledPayload("openclaw-2026.3.13.tgz")

        assertTrue(result is HostResult.Success)
        assertEquals(
            "payload-bytes",
            (result as HostResult.Success).value.bufferedReader().use { it.readText() },
        )
    }

    @Test
    fun `openBundledPayload returns failure when asset is missing`() {
        val locator = AssetPayloadLocator.forTesting(
            manifestStreamOpener = {
                ByteArrayInputStream("{}".toByteArray(Charsets.UTF_8))
            },
            assetStreamOpener = {
                throw FileNotFoundException("missing bundled payload")
            }
        )

        val result = locator.openBundledPayload("missing.tgz")

        assertTrue(result is HostResult.Failure)
        assertEquals(
            "Bundled payload 'missing.tgz' is missing at assets/bootstrap/missing.tgz.",
            (result as HostResult.Failure).error.message,
        )
    }

    @Test
    fun `openBundledPayload returns failure when asset cannot be opened`() {
        val locator = AssetPayloadLocator.forTesting(
            manifestStreamOpener = {
                ByteArrayInputStream("{}".toByteArray(Charsets.UTF_8))
            },
            assetStreamOpener = {
                throw IllegalStateException("boom")
            }
        )

        val result = locator.openBundledPayload("broken.tgz")

        assertTrue(result is HostResult.Failure)
        assertEquals(
            "Failed to open bundled payload 'broken.tgz' at assets/bootstrap/broken.tgz.",
            (result as HostResult.Failure).error.message,
        )
    }

    @Test
    fun `loadBundledPayloadManifest returns failure when manifest is malformed`() = runBlocking {
        val locator = AssetPayloadLocator.forTesting(
            manifestStreamOpener = {
                ByteArrayInputStream("{ malformed".toByteArray(Charsets.UTF_8))
            }
        )

        val result = locator.loadBundledPayloadManifest()

        assertTrue(result is HostResult.Failure)
        assertEquals(
            "Bundled payload manifest is malformed at assets/bootstrap/manifest.json.",
            (result as HostResult.Failure).error.message,
        )
    }

    @Test
    fun `loadBundledPayloadManifest returns success null when manifest file is missing`() = runBlocking {
        val locator = AssetPayloadLocator.forTesting(
            manifestStreamOpener = {
                throw FileNotFoundException("missing manifest")
            }
        )

        val result = locator.loadBundledPayloadManifest()

        assertTrue(result is HostResult.Success)
        assertEquals(null, (result as HostResult.Success).value)
    }
}
