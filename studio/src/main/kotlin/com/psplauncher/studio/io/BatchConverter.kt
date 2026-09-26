package com.psplauncher.studio.io

import com.psplauncher.themekit.PfpThemeCodec
import java.io.File

data class BatchProgress(val done: Int, val total: Int, val current: String)

data class BatchSummary(
    val converted: List<String>,
    val skippedCxmb: List<String>,
    val failed: List<Pair<String, String>>,

    val warnings: List<Pair<String, String>> = emptyList(),
)

object BatchConverter {
    private const val MAX_BATCH_FILES = 500

    fun convertFolder(
        input: File,
        output: File,
        renderPreview: (com.psplauncher.themekit.PfpThemeBundle) -> ByteArray?,
        onProgress: (BatchProgress) -> Unit = {},
    ): BatchSummary {
        val ptfs = input.listFiles { f -> f.isFile && f.extension.lowercase() == "ptf" }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
            .take(MAX_BATCH_FILES)
        output.mkdirs()

        val converted = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()
        val warnings = mutableListOf<Pair<String, String>>()

        ptfs.forEachIndexed { index, file ->
            onProgress(BatchProgress(done = index, total = ptfs.size, current = file.name))
            when (val outcome = runCatching {
                val bytes = SafeIo.readBytesCapped(file)
                if (bytes == null) ConvertOutcome.Failed("file too large")
                else PtfConversion.convert(bytes, file.name)
            }.getOrElse { ConvertOutcome.Failed(it.message ?: "read error") }
            ) {
                is ConvertOutcome.Converted -> {
                    val bundle = outcome.bundle.copy(preview = renderPreview(outcome.bundle))
                    val target = uniqueTarget(output, file.nameWithoutExtension)
                    runCatching { target.outputStream().use { PfpThemeCodec.write(bundle, it) } }
                        .onSuccess {
                            converted += target.name
                            outcome.warning?.let { w -> warnings += file.name to w }
                        }
                        .onFailure { failed += file.name to (it.message ?: "write error") }
                }
                ConvertOutcome.Cxmb -> skipped += file.name
                is ConvertOutcome.Failed -> failed += file.name to outcome.reason
            }
        }
        onProgress(BatchProgress(done = ptfs.size, total = ptfs.size, current = ""))
        return BatchSummary(converted, skipped, failed, warnings)
    }

    private fun uniqueTarget(dir: File, baseName: String): File {
        var candidate = File(dir, "$baseName.${PfpThemeCodec.FILE_EXTENSION}")
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "$baseName ($n).${PfpThemeCodec.FILE_EXTENSION}")
            n++
        }
        return candidate
    }
}
