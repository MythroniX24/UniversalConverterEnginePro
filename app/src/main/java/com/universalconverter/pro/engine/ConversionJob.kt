package com.universalconverter.pro.engine

import android.net.Uri
import java.util.UUID

enum class JobStatus { QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED }
enum class CompressMode { FAST, BALANCED, ULTRA }

data class ConversionJob(
    val id: String = UUID.randomUUID().toString(),
    val inputUri: Uri,
    val inputName: String,
    val inputSizeBytes: Long = 0L,
    val outputFormat: String = "",
    val quality: Int = 85,
    val width: Int = 0,
    val height: Int = 0,
    val keepAspect: Boolean = true,
    val removeExif: Boolean = false,
    val rotation: Int = 0,
    val targetSizeBytes: Long = 0L,
    val compressMode: CompressMode = CompressMode.BALANCED,
    val useSmartCompress: Boolean = false,
    val upscaleFactor: Int = 1,
    val outputPath: String = "",
    val outputSizeBytes: Long = 0L,
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Int = 0,
    val statusMessage: String = "Queued",
    val errorMessage: String = "",
    val ssimScore: Float = -1f,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L
) {
    val compressionPercent: Int get() =
        if (inputSizeBytes > 0 && outputSizeBytes > 0)
            ((1.0 - outputSizeBytes.toDouble()/inputSizeBytes)*100).toInt().coerceIn(-200, 100)
        else 0
    val isComplete get() = status == JobStatus.SUCCESS || status == JobStatus.FAILED || status == JobStatus.CANCELLED
    val processingTimeMs get() = if (completedAt > 0) completedAt - createdAt else 0L
}
