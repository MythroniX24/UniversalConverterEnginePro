package com.universalconverter.pro.engine

import android.net.Uri
import java.util.UUID

enum class JobStatus {
    QUEUED, RUNNING, SUCCESS, FAILED, CANCELLED
}

data class ConversionJob(
    val id: String            = UUID.randomUUID().toString(),
    val inputUri: Uri,
    val inputName: String,
    val inputSizeBytes: Long,
    val outputFormat: String,
    val outputPath: String    = "",
    val quality: Int          = 85,
    val width: Int            = 0,
    val height: Int           = 0,
    val keepAspectRatio: Boolean = true,
    val removeExif: Boolean   = false,
    val rotation: Int         = 0,
    val targetSizeBytes: Long = 0L,
    val status: JobStatus     = JobStatus.QUEUED,
    val progress: Int         = 0,
    val statusMessage: String = "Waiting…",
    val outputSizeBytes: Long = 0L,
    val errorMessage: String  = "",
    val createdAt: Long       = System.currentTimeMillis(),
    val completedAt: Long     = 0L
) {
    val compressionPercent: Int
        get() = if (inputSizeBytes > 0 && outputSizeBytes > 0) {
            ((1.0 - outputSizeBytes.toDouble() / inputSizeBytes) * 100).toInt()
                .coerceIn(0, 100)
        } else 0

    val isComplete: Boolean
        get() = status == JobStatus.SUCCESS || status == JobStatus.FAILED ||
                status == JobStatus.CANCELLED
}
