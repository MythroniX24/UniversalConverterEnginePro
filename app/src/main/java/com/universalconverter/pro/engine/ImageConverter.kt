package com.universalconverter.pro.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageConverter {

    // ─── Convert image to target format ──────────────────────────────────────
    suspend fun convert(
        context: Context,
        job: ConversionJob,
        onProgress: (Int, String) -> Unit
    ): ConversionJob = withContext(Dispatchers.IO) {
        try {
            onProgress(5, "Reading file…")

            val inputStream = context.contentResolver.openInputStream(job.inputUri)
                ?: return@withContext job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = "Cannot open input file"
                )

            onProgress(15, "Decoding image…")
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                return@withContext job.copy(
                    status = JobStatus.FAILED,
                    errorMessage = "Failed to decode image"
                )
            }

            // Apply rotation
            if (job.rotation != 0) {
                onProgress(25, "Rotating…")
                bitmap = rotateBitmap(bitmap, job.rotation)
            }

            // Apply resize
            if (job.width > 0 || job.height > 0) {
                onProgress(35, "Resizing…")
                bitmap = resizeBitmap(bitmap, job.width, job.height, job.keepAspectRatio)
            }

            // Target size compression
            if (job.targetSizeBytes > 0) {
                onProgress(45, "Optimising for target size…")
                val estimatedQuality = NativeEngine.estimateOptimalQuality(
                    job.inputSizeBytes, job.targetSizeBytes, job.outputFormat
                )
                return@withContext encodeAndSave(
                    context, bitmap, job, estimatedQuality, onProgress
                )
            }

            onProgress(60, "Encoding…")
            return@withContext encodeAndSave(context, bitmap, job, job.quality, onProgress)

        } catch (e: Exception) {
            job.copy(status = JobStatus.FAILED, errorMessage = e.message ?: "Unknown error")
        }
    }

    // ─── Compress only (no format change) ────────────────────────────────────
    suspend fun compress(
        context: Context,
        job: ConversionJob,
        onProgress: (Int, String) -> Unit
    ): ConversionJob = withContext(Dispatchers.IO) {
        try {
            onProgress(10, "Reading…")
            val inputStream = context.contentResolver.openInputStream(job.inputUri)
                ?: return@withContext job.copy(status = JobStatus.FAILED,
                    errorMessage = "Cannot open file")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) {
                return@withContext job.copy(status = JobStatus.FAILED,
                    errorMessage = "Failed to decode image")
            }
            onProgress(50, "Compressing…")
            encodeAndSave(context, bitmap, job, job.quality, onProgress)
        } catch (e: Exception) {
            job.copy(status = JobStatus.FAILED, errorMessage = e.message ?: "Error")
        }
    }

    // ─── Encode bitmap and write to output ───────────────────────────────────
    private fun encodeAndSave(
        context: Context,
        bitmap: Bitmap,
        job: ConversionJob,
        quality: Int,
        onProgress: (Int, String) -> Unit
    ): ConversionJob {
        onProgress(70, "Saving ${job.outputFormat.uppercase()}…")

        val outputDir = getOutputDir(context)
        val timestamp = System.currentTimeMillis()
        val baseName  = job.inputName.substringBeforeLast('.')
        val outFile   = File(outputDir, "${baseName}_${timestamp}.${job.outputFormat}")

        val compressFormat = when (job.outputFormat.lowercase()) {
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            "png"         -> Bitmap.CompressFormat.PNG
            "webp"        -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                                Bitmap.CompressFormat.WEBP_LOSSY
                             else
                                @Suppress("DEPRECATION")
                                Bitmap.CompressFormat.WEBP
            else          -> Bitmap.CompressFormat.JPEG
        }

        FileOutputStream(outFile).use { fos ->
            bitmap.compress(compressFormat, quality.coerceIn(1, 100), fos)
        }
        bitmap.recycle()

        // Remove EXIF if requested
        if (job.removeExif && (job.outputFormat == "jpg" || job.outputFormat == "jpeg")) {
            stripExif(outFile.absolutePath)
        }

        val outputSize = outFile.length()
        val ratio = NativeEngine.computeCompressionRatio(job.inputSizeBytes, outputSize)

        onProgress(100, "Done! Saved ${(ratio * 100).toInt()}% space")

        return job.copy(
            status = JobStatus.SUCCESS,
            outputPath = outFile.absolutePath,
            outputSizeBytes = outputSize,
            progress = 100,
            statusMessage = "Complete",
            completedAt = System.currentTimeMillis()
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it != bitmap) bitmap.recycle() }
    }

    private fun resizeBitmap(bitmap: Bitmap, targetW: Int, targetH: Int, keepAspect: Boolean): Bitmap {
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val newW: Int
        val newH: Int
        if (keepAspect) {
            val scaleW = if (targetW > 0) targetW / srcW else Float.MAX_VALUE
            val scaleH = if (targetH > 0) targetH / srcH else Float.MAX_VALUE
            val scale  = minOf(scaleW, scaleH)
            newW = (srcW * scale).toInt().coerceAtLeast(1)
            newH = (srcH * scale).toInt().coerceAtLeast(1)
        } else {
            newW = if (targetW > 0) targetW else bitmap.width
            newH = if (targetH > 0) targetH else bitmap.height
        }
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            .also { if (it != bitmap) bitmap.recycle() }
    }

    private fun stripExif(path: String) {
        try {
            val exif = ExifInterface(path)
            val tagsToRemove = listOf(
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT
            )
            tagsToRemove.forEach { exif.setAttribute(it, null) }
            exif.saveAttributes()
        } catch (_: Exception) {}
    }

    private fun getOutputDir(context: Context): File {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "UCEngine"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
