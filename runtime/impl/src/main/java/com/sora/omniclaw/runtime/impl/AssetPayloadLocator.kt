package com.sora.omniclaw.runtime.impl

import android.content.Context
import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadManifest
import com.sora.omniclaw.runtime.api.PayloadLocator
import java.io.FileNotFoundException
import kotlinx.serialization.json.Json

class AssetPayloadLocator(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) : PayloadLocator {
    override suspend fun loadBundledPayloadManifest(): HostResult<BundledPayloadManifest?> {
        return runCatching {
            context.assets.open(MANIFEST_PATH).bufferedReader().use { reader ->
                json.decodeFromString<BundledPayloadManifest>(reader.readText())
            }
        }.fold(
            onSuccess = { HostResult.Success(it) },
            onFailure = { throwable ->
                when (throwable) {
                    is FileNotFoundException -> HostResult.Success(null)
                    else -> HostResult.Failure(
                        HostError(
                            category = HostErrorCategory.Runtime,
                            message = throwable.message ?: "Failed to read bundled payload manifest.",
                            recoverable = true,
                        )
                    )
                }
            }
        )
    }

    private companion object {
        const val MANIFEST_PATH = "bootstrap/manifest.json"
    }
}
