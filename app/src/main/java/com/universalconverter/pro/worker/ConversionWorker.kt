package com.universalconverter.pro.worker

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.universalconverter.pro.ConverterApplication
import com.universalconverter.pro.engine.ConversionJob
import com.universalconverter.pro.engine.FileDetector
import com.universalconverter.pro.engine.ImageConverter
import com.universalconverter.pro.engine.JobStatus

class ConversionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_INPUT_URI   = "input_uri"
        const val KEY_OUTPUT_FMT  = "output_format"
        const val KEY_QUALITY     = "quality"
        const val KEY_REMOVE_EXIF = "remove_exif"
        const val KEY_ROTATION    = "rotation"
        const val KEY_TARGET_SIZE = "target_size_bytes"
        const val KEY_WIDTH       = "width"
        const val KEY_HEIGHT      = "height"
        const val KEY_KEEP_ASPECT = "keep_aspect"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val uriStr     = inputData.getString(KEY_INPUT_URI)   ?: return Result.failure()
        val format     = inputData.getString(KEY_OUTPUT_FMT)  ?: return Result.failure()
        val quality    = inputData.getInt(KEY_QUALITY, 85)
        val removeExif = inputData.getBoolean(KEY_REMOVE_EXIF, false)
        val rotation   = inputData.getInt(KEY_ROTATION, 0)
        val targetSize = inputData.getLong(KEY_TARGET_SIZE, 0L)
        val width      = inputData.getInt(KEY_WIDTH, 0)
        val height     = inputData.getInt(KEY_HEIGHT, 0)
        val keepAspect = inputData.getBoolean(KEY_KEEP_ASPECT, true)

        val uri = android.net.Uri.parse(uriStr)

        // Simple foreground info - no ServiceInfo type needed for basic notification
        val notification = NotificationCompat.Builder(context, ConverterApplication.CHANNEL_CONVERSION)
            .setContentTitle("Converting…")
            .setContentText("Starting…")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
        setForeground(ForegroundInfo(NOTIFICATION_ID, notification))

        val fileInfo = FileDetector.analyze(context, uri)
        val job = ConversionJob(
            inputUri        = uri,
            inputName       = fileInfo.name,
            inputSizeBytes  = fileInfo.sizeBytes,
            outputFormat    = format,
            quality         = quality,
            removeExif      = removeExif,
            rotation        = rotation,
            targetSizeBytes = targetSize,
            width           = width,
            height          = height,
            keepAspectRatio = keepAspect
        )

        val result = ImageConverter.convert(context, job) { progress, message ->
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(NOTIFICATION_ID,
                NotificationCompat.Builder(context, ConverterApplication.CHANNEL_CONVERSION)
                    .setContentTitle("Converting…")
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setProgress(100, progress, false)
                    .setOngoing(true)
                    .build()
            )
        }

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.cancel(NOTIFICATION_ID)

        return if (result.status == JobStatus.SUCCESS) {
            Result.success(
                androidx.work.workDataOf(
                    "output_path"     to result.outputPath,
                    "output_size"     to result.outputSizeBytes,
                    "savings_percent" to result.compressionPercent
                )
            )
        } else {
            Result.failure(androidx.work.workDataOf("error" to result.errorMessage))
        }
    }
}
