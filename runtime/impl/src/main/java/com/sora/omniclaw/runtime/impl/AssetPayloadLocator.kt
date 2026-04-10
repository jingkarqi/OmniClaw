package com.sora.omniclaw.runtime.impl

import android.content.Context
import com.sora.omniclaw.core.common.HostError
import com.sora.omniclaw.core.common.HostErrorCategory
import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.BundledPayloadManifest
import com.sora.omniclaw.runtime.api.PayloadLocator
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException

class AssetPayloadLocator(
    private val manifestStreamOpener: () -> InputStream,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) : PayloadLocator {
    constructor(
        context: Context,
        json: Json = Json {
            ignoreUnknownKeys = true
        },
    ) : this(
        manifestStreamOpener = { context.assets.open(MANIFEST_PATH) },
        json = json,
    )

    override suspend fun loadBundledPayloadManifest(): HostResult<BundledPayloadManifest?> {
        return runCatching {
            manifestStreamOpener().bufferedReader().use { reader ->
                json.decodeFromString<BundledPayloadManifest>(reader.readText())
            }
        }.fold(
            onSuccess = { HostResult.Success(it) },
            onFailure = { throwable ->
                when (throwable) {
                    is FileNotFoundException -> HostResult.Success(null)
                    is SerializationException -> HostResult.Failure(
                        HostError(
                            category = HostErrorCategory.Runtime,
                            message = "Bundled payload manifest is malformed at assets/bootstrap/manifest.json.",
                            recoverable = true,
                        )
                    )
                    else -> HostResult.Failure(
                        HostError(
                            category = HostErrorCategory.Runtime,
                            message = "Failed to read bundled payload manifest at assets/bootstrap/manifest.json.",
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
