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
    fun `loadBundledPayloadManifest returns failure when manifest is malformed`() = runBlocking {
        val locator = AssetPayloadLocator(
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
        val locator = AssetPayloadLocator(
            manifestStreamOpener = {
                throw FileNotFoundException("missing manifest")
            }
        )

        val result = locator.loadBundledPayloadManifest()

        assertTrue(result is HostResult.Success)
        assertEquals(null, (result as HostResult.Success).value)
    }
}
