package com.universalconverter.pro.worker

import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
        const val KEY_INPUT_URI    = "input_uri"
        const val KEY_OUTPUT_FMT   = "output_format"
        const val KEY_QUALITY      = "quality"
        const val KEY_REMOVE_EXIF  = "remove_exif"
        const val KEY_ROTATION     = "rotation"
        const val KEY_TARGET_SIZE  = "target_size_bytes"
        const val KEY_WIDTH        = "width"
        const val KEY_HEIGHT       = "height"
        const val KEY_KEEP_ASPECT  = "keep_aspect"
        const val NOTIFICATION_ID  = 1001
    }

    override suspend fun doWork(): Result {
        val uriStr     = inputData.getString(KEY_INPUT_URI)    ?: return Result.failure()
        val format     = inputData.getString(KEY_OUTPUT_FMT)   ?: return Result.failure()
        val quality    = inputData.getInt(KEY_QUALITY, 85)
        val removeExif = inputData.getBoolean(KEY_REMOVE_EXIF, false)
        val rotation   = inputData.getInt(KEY_ROTATION, 0)
        val targetSize = inputData.getLong(KEY_TARGET_SIZE, 0L)
        val width      = inputData.getInt(KEY_WIDTH, 0)
        val height     = inputData.getInt(KEY_HEIGHT, 0)
        val keepAspect = inputData.getBoolean(KEY_KEEP_ASPECT, true)

        val uri = android.net.Uri.parse(uriStr)
        setForeground(createForegroundInfo("Starting conversion…"))

        val fileInfo = com.universalconverter.pro.engine.FileDetector.analyze(context, uri)
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
            updateNotification(progress, message)
        }

        return if (result.status == JobStatus.SUCCESS) {
            showCompleteNotification(result)
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

    private fun createForegroundInfo(message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, ConverterApplication.CHANNEL_CONVERSION)
            .setContentTitle("Converting file…")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()
        // Use 3-arg constructor only on API 29+ via reflection-safe approach
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                ForegroundInfo::class.java
                    .getConstructor(Int::class.java, android.app.Notification::class.java, Int::class.java)
                    .newInstance(NOTIFICATION_ID, notification, serviceType) as ForegroundInfo
            } catch (e: Exception) {
                ForegroundInfo(NOTIFICATION_ID, notification)
            }
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(progress: Int, message: String) {
        val notification = NotificationCompat.Builder(context, ConverterApplication.CHANNEL_CONVERSION)
            .setContentTitle("Converting…")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification(job: ConversionJob) {
        val savedText = if (job.compressionPercent > 0)
            "Saved ${job.compressionPercent}% · ${FileDetector.formatSize(job.outputSizeBytes)}"
        else FileDetector.formatSize(job.outputSizeBytes)

        val notification = NotificationCompat.Builder(context, ConverterApplication.CHANNEL_COMPLETE)
            .setContentTitle("✓ Conversion complete")
            .setContentText(savedText)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
        manager.notify(NOTIFICATION_ID + 1, notification)
    }
}
