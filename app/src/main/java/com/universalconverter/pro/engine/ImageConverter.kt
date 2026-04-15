package com.universalconverter.pro.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ImageConverter {

    suspend fun convert(context: Context, job: ConversionJob, onProgress: (Int, String) -> Unit): ConversionJob =
        withContext(Dispatchers.IO) {
            try {
                NativeEngine.resetCancel()
                onProgress(5, "Reading…")
                var bmp = decode(context, job.inputUri) ?: return@withContext fail(job, "Cannot decode image")

                if (job.rotation != 0) { onProgress(15, "Rotating…"); bmp = rotate(bmp, job.rotation) }
                if (job.width > 0 || job.height > 0) { onProgress(25, "Resizing…"); bmp = resize(bmp, job.width, job.height, job.keepAspect) }
                if (job.upscaleFactor > 1) { onProgress(30, "Upscaling ${job.upscaleFactor}x…"); bmp = upscale(bmp, job.upscaleFactor) }

                val quality = when {
                    job.useSmartCompress -> {
                        onProgress(35, "Analyzing content…")
                        val ct = NativeEngine.classifyContent(bmp)
                        NativeEngine.recommendedQuality(ct, job.compressMode.ordinal)
                    }
                    job.targetSizeBytes > 0 -> {
                        onProgress(35, "Computing target quality…")
                        binarySearchQuality(context, bmp, job)
                    }
                    else -> job.quality
                }

                onProgress(60, "Encoding ${job.outputFormat.uppercase()}…")
                encode(context, bmp, job, quality, onProgress)
            } catch (e: Exception) {
                fail(job, e.message ?: "Error")
            }
        }

    private fun binarySearchQuality(context: Context, bmp: Bitmap, job: ConversionJob): Int {
        var lo = 10; var hi = 95; var best = 70
        repeat(6) {
            val mid = (lo + hi) / 2
            val tmp = File(context.cacheDir, "test_${System.currentTimeMillis()}.${job.outputFormat}")
            encode(context, bmp, job.copy(outputPath = tmp.absolutePath), mid) {}
            val sz = tmp.length(); tmp.delete()
            if (sz <= job.targetSizeBytes) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        return best
    }

    private fun encode(context: Context, bmp: Bitmap, job: ConversionJob, quality: Int, onProgress: (Int, String) -> Unit = {_, _ ->}): ConversionJob {
        val dir = getOutDir(context, job.outputFormat)
        val outFile = File(dir, "${job.inputName.substringBeforeLast('.')}_${System.currentTimeMillis()}.${job.outputFormat}")
        val fmt = when (job.outputFormat.lowercase()) {
            "png"  -> Bitmap.CompressFormat.PNG
            "webp" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                          Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            "bmp"  -> Bitmap.CompressFormat.PNG // Android has no BMP encoder, use PNG
            else   -> Bitmap.CompressFormat.JPEG
        }
        FileOutputStream(outFile).use { bmp.compress(fmt, quality.coerceIn(1, 100), it) }
        if (job.removeExif && job.outputFormat in listOf("jpg","jpeg")) stripExif(outFile.absolutePath)

        val origBmp = decode(context, job.inputUri)
        val ssim = if (origBmp != null && outFile.exists()) {
            val outBmp = BitmapFactory.decodeFile(outFile.absolutePath)
            if (outBmp != null && origBmp.width == outBmp.width) NativeEngine.computeSSIM(origBmp, outBmp) else -1f
        } else -1f

        onProgress(100, "Done!")
        return job.copy(status = JobStatus.SUCCESS, outputPath = outFile.absolutePath,
            outputSizeBytes = outFile.length(), ssimScore = ssim, completedAt = System.currentTimeMillis())
    }

    suspend fun batchConvert(context: Context, jobs: List<ConversionJob>, onProgress: (Int, String, Int) -> Unit): List<ConversionJob> =
        withContext(Dispatchers.IO) {
            jobs.mapIndexed { i, job ->
                val result = convert(context, job) { p, m -> onProgress(p, m, i) }
                result
            }
        }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (_: Exception) { null }
    }

    private fun rotate(bmp: Bitmap, deg: Int): Bitmap {
        val m = Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it != bmp) bmp.recycle() }
    }

    private fun resize(bmp: Bitmap, w: Int, h: Int, keep: Boolean): Bitmap {
        val sw = bmp.width.toFloat(); val sh = bmp.height.toFloat()
        val nw: Int; val nh: Int
        if (keep) {
            val s = minOf(if(w>0) w/sw else Float.MAX_VALUE, if(h>0) h/sh else Float.MAX_VALUE)
            nw = (sw*s).toInt().coerceAtLeast(1); nh = (sh*s).toInt().coerceAtLeast(1)
        } else {
            nw = if(w>0) w else bmp.width; nh = if(h>0) h else bmp.height
        }
        return Bitmap.createScaledBitmap(bmp, nw, nh, true).also { if (it != bmp) bmp.recycle() }
    }

    private fun upscale(bmp: Bitmap, factor: Int): Bitmap {
        val nw = bmp.width * factor; val nh = bmp.height * factor
        val dst = Bitmap.createBitmap(nw, nh, Bitmap.Config.ARGB_8888)
        NativeEngine.upscaleBitmap(bmp, dst, 0.5f)
        bmp.recycle(); return dst
    }

    private fun stripExif(path: String) = try {
        val e = ExifInterface(path)
        listOf(ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
               ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_DATETIME,
               ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
               ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT).forEach { e.setAttribute(it, null) }
        e.saveAttributes()
    } catch (_: Exception) {}

    private fun getOutDir(context: Context, fmt: String): File {
        val base = if (fmt == "pdf") Environment.DIRECTORY_DOCUMENTS else Environment.DIRECTORY_PICTURES
        return File(context.getExternalFilesDir(base), "UCEngine").also { it.mkdirs() }
    }

    private fun fail(job: ConversionJob, msg: String) =
        job.copy(status = JobStatus.FAILED, errorMessage = msg, completedAt = System.currentTimeMillis())
}
