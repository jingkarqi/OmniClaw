package com.sora.omniclaw.runtime.impl

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.common.getOrNull
import com.sora.omniclaw.core.common.isSuccess
import com.sora.omniclaw.core.model.ProviderRuntimeExport
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeProviderConfigWriterTest {
    @Test
    fun `read returns null when the runtime config file is missing`() {
        withTempDir { tempRoot ->
            val writer = newWriter(tempRoot)

            assertRead(writer, expectedConfig = null)
        }
    }

    @Test
    fun `write persists runtime provider config for later reads`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val writer = newWriter(tempRoot)
            val export = ProviderRuntimeExport(
                providerId = "OpenAI Compatible",
                baseUrl = "https://api.openai.example/v1",
                modelName = "gpt-4.1-mini",
            )
            val apiKey = "secret-openai-key"

            val result = writer.write(export, apiKey)

            assertTrue(result.isSuccess)
            assertEquals(
                RuntimeProviderConfig.fromExport(export, apiKey),
                result.getOrNull(),
            )
            assertRead(
                writer,
                expectedConfig = RuntimeProviderConfig.fromExport(export, apiKey),
            )
            assertTrue(File(directories.generatedConfigDir, "openclaw.json5").isFile)
        }
    }

    @Test
    fun `observe emits missing written and cleared configs`() {
        withTempDir { tempRoot ->
            val writer = newWriter(tempRoot)
            val export = ProviderRuntimeExport(
                providerId = "anthropic",
                baseUrl = "https://api.anthropic.example",
                modelName = "claude-sonnet-4",
            )
            val apiKey = "secret-anthropic-key"
            val emissions = mutableListOf<RuntimeProviderConfig?>()

            runBlocking {
                val collection = launch {
                    writer.observe()
                        .take(3)
                        .toList(emissions)
                }

                yield()
                writer.write(export, apiKey)
                yield()
                assertUnitResult(writer.clear())
                collection.join()
            }

            assertEquals(
                listOf(
                    null,
                    RuntimeProviderConfig.fromExport(export, apiKey),
                    null,
                ),
                emissions,
            )
            assertRead(writer, expectedConfig = null)
            assertFalse(writer.configFile.exists())
        }
    }

    @Test
    fun `read treats malformed runtime config as missing`() {
        withTempDir { tempRoot ->
            val writer = newWriter(tempRoot)
            writer.configFile.parentFile?.mkdirs()
            writer.configFile.writeText("{not-valid-json")

            assertRead(writer, expectedConfig = null)
        }
    }

    @Test
    fun `write overwrites an existing runtime config`() {
        withTempDir { tempRoot ->
            val writer = newWriter(tempRoot)

            writer.write(
                ProviderRuntimeExport(
                    providerId = "openai",
                    baseUrl = "https://api.openai.example/v1",
                    modelName = "gpt-4.1-mini",
                ),
                "secret-openai-key",
            )
            writer.write(
                ProviderRuntimeExport(
                    providerId = "openrouter",
                    baseUrl = "https://openrouter.example/api/v1",
                    modelName = "openrouter/quasar-alpha",
                ),
                "secret-openrouter-key",
            )

            assertRead(
                writer,
                expectedConfig = RuntimeProviderConfig.fromExport(
                    ProviderRuntimeExport(
                        providerId = "openrouter",
                        baseUrl = "https://openrouter.example/api/v1",
                        modelName = "openrouter/quasar-alpha",
                    ),
                    "secret-openrouter-key",
                ),
            )
        }
    }

    @Test
    fun `writer uses the generated config runtime path`() {
        withTempDir { tempRoot ->
            val directories = RuntimeDirectories.fromRoot(tempRoot)
            val writer = newWriter(tempRoot)

            assertEquals(
                File(directories.generatedConfigDir, "openclaw.json5").absoluteFile,
                writer.configFile,
            )
        }
    }

    @Test
    fun `write normalizes provider keys and infers api family`() {
        withTempDir { tempRoot ->
            val writer = newWriter(tempRoot)
            val export = ProviderRuntimeExport(
                providerId = " Anthropic / Primary ",
                baseUrl = "https://api.anthropic.example",
                modelName = "claude-sonnet-4",
            )

            val config = writer.write(export, "secret-anthropic-key").getOrNull()

            assertEquals("anthropic-primary/claude-sonnet-4", config?.agents?.defaults?.model)
            assertEquals(
                "anthropic-messages",
                config?.models?.providers?.get("anthropic-primary")?.api,
            )
        }
    }

    @Test
    fun `write rejects missing api key even when export metadata is complete`() {
        withTempDir { tempRoot ->
            val writer = newWriter(tempRoot)
            val export = ProviderRuntimeExport(
                providerId = "openai",
                baseUrl = "https://api.openai.example/v1",
                modelName = "gpt-4.1-mini",
            )

            val result = writer.write(export, "")

            assertTrue(result is HostResult.Failure)
            assertRead(writer, expectedConfig = null)
        }
    }

    private fun newWriter(workspaceRoot: File): RuntimeProviderConfigWriter {
        return RuntimeProviderConfigWriter(
            directories = RuntimeDirectories.fromRoot(workspaceRoot),
        )
    }

    private fun assertRead(
        writer: RuntimeProviderConfigWriter,
        expectedConfig: RuntimeProviderConfig?,
    ) {
        val result = writer.read()
        assertTrue(result.isSuccess)
        assertEquals(expectedConfig, result.getOrNull())
    }

    private fun assertUnitResult(result: HostResult<Unit>) {
        assertTrue(result.isSuccess)
        assertEquals(Unit, result.getOrNull())
    }

    private fun withTempDir(block: (File) -> Unit) {
        val tempDir = createTempDirectory(prefix = "runtime-provider-config-writer-").toFile()
        try {
            block(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
