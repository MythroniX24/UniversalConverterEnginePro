package com.universalconverter.pro.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SmartCompressor {
    data class CompressionResult(
        val outputPath: String, val originalSize: Long, val compressedSize: Long,
        val quality: Int, val format: String, val ssimScore: Float,
        val reductionPercent: Int, val mode: CompressMode
    )

    suspend fun smartCompress(
        context: Context, uri: Uri, mode: CompressMode = CompressMode.BALANCED,
        targetSizeBytes: Long = 0L, onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<CompressionResult> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(5, "Analyzing image…")
            val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open")
            val bmp = BitmapFactory.decodeStream(stream) ?: error("Cannot decode")
            stream.close()

            onProgress(20, "Detecting content type…")
            val contentType = NativeEngine.classifyContent(bmp)
            val contentName = when(contentType) { 0->"Photo"; 1->"Graphic"; 2->"Screenshot"; else->"Drawing" }
            onProgress(30, "Content: $contentName — optimizing…")

            val quality = if (targetSizeBytes > 0) {
                val inSize = estimateSize(context, uri)
                NativeEngine.estimateQuality(inSize, targetSizeBytes)
            } else {
                NativeEngine.recommendedQuality(contentType, mode.ordinal)
            }

            val fmtIdx = NativeEngine.recommendFormat(contentType, false)
            val format = when(fmtIdx) { 1->"png"; 2->"webp"; 3->"bmp"; else->"jpg" }

            onProgress(50, "Compressing to $format at quality $quality…")
            val info = FileDetector.analyze(context, uri)
            val job = ConversionJob(
                inputUri = uri, inputName = info.name, inputSizeBytes = info.sizeBytes,
                outputFormat = format, quality = quality, compressMode = mode,
                useSmartCompress = true, targetSizeBytes = targetSizeBytes
            )
            val result = ImageConverter.convert(context, job) { p, m -> onProgress(50 + p/2, m) }
            if (result.status != JobStatus.SUCCESS) error(result.errorMessage)

            onProgress(100, "Smart compression complete!")
            CompressionResult(
                outputPath = result.outputPath, originalSize = info.sizeBytes,
                compressedSize = result.outputSizeBytes, quality = quality,
                format = format, ssimScore = result.ssimScore,
                reductionPercent = result.compressionPercent, mode = mode
            )
        }
    }

    // Compression Racing — run multiple algorithms, pick best
    suspend fun compressionRace(
        context: Context, uri: Uri, targetSize: Long = 0,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<CompressionResult> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(5, "Starting compression race…")
            val info = FileDetector.analyze(context, uri)
            val formats = listOf("webp", "jpg", "png")
            val qualities = listOf(75, 82, 88)
            var best: CompressionResult? = null
            var bestScore = Double.MAX_VALUE

            formats.forEachIndexed { fi, fmt ->
                qualities.forEachIndexed { qi, q ->
                    val pct = ((fi * 3 + qi) * 100 / (formats.size * qualities.size))
                    onProgress(pct, "Testing $fmt @ $q…")
                    val job = ConversionJob(inputUri = uri, inputName = info.name,
                        inputSizeBytes = info.sizeBytes, outputFormat = fmt, quality = q)
                    val result = ImageConverter.convert(context, job) { _, _ -> }
                    if (result.status == JobStatus.SUCCESS) {
                        val sz = result.outputSizeBytes
                        if (targetSize > 0 && sz > targetSize) { File(result.outputPath).delete(); return@forEachIndexed }
                        val ssim = result.ssimScore
                        val score = sz.toDouble() / (if(ssim > 0) ssim.toDouble() else 0.8)
                        if (score < bestScore) {
                            best?.let { File(it.outputPath).delete() }
                            bestScore = score
                            best = CompressionResult(result.outputPath, info.sizeBytes, sz, q, fmt, ssim,
                                result.compressionPercent, CompressMode.BALANCED)
                        } else File(result.outputPath).delete()
                    }
                }
            }
            onProgress(100, "Race complete! Winner: ${best?.format} @ ${best?.quality}")
            best ?: error("All compression attempts failed")
        }
    }
}

private fun estimateSize(context: android.content.Context, uri: android.net.Uri): Long {
    val info = FileDetector.analyze(context, uri)
    return if (info.sizeBytes > 0) info.sizeBytes else 500_000L
}
