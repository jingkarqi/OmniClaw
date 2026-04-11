package com.sora.omniclaw.runtime.impl

import java.io.File

internal class RuntimeLogCollector(
    private val maxLinesPerStream: Int = 10,
) {
    fun collect(
        stdoutPath: String?,
        stderrPath: String?,
    ): List<String> {
        val details = mutableListOf<String>()
        appendTail(stdoutPath, "stdout", details)
        appendTail(stderrPath, "stderr", details)
        return details
    }

    private fun appendTail(
        path: String?,
        label: String,
        details: MutableList<String>,
    ) {
        if (path.isNullOrBlank()) {
            return
        }

        val file = File(path)
        if (!file.isFile) {
            details += "$label log unavailable at $path"
            return
        }

        val lines = runCatching { file.readLines() }.getOrDefault(emptyList()).takeLast(maxLinesPerStream)
        if (lines.isEmpty()) {
            details += "$label log is empty at $path"
            return
        }

        details += "$label log tail:"
        details += lines.map { line -> "[$label] $line" }
    }
}
