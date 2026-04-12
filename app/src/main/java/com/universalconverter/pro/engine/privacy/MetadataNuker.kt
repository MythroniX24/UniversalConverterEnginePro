package com.universalconverter.pro.engine.privacy

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MetadataNuker {
    data class MetadataInfo(
        val hasGps: Boolean, val hasDevice: Boolean, val hasDateTime: Boolean,
        val hasCopyright: Boolean, val gpsLat: String?, val gpsLon: String?,
        val make: String?, val model: String?, val software: String?
    )

    suspend fun scan(context: Context, uri: Uri): MetadataInfo = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)!!
            val tmpFile = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
            tmpFile.outputStream().use { stream.copyTo(it) }
            val exif = ExifInterface(tmpFile.absolutePath)
            val info = MetadataInfo(
                hasGps      = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null,
                hasDevice   = exif.getAttribute(ExifInterface.TAG_MAKE) != null,
                hasDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME) != null,
                hasCopyright= exif.getAttribute(ExifInterface.TAG_COPYRIGHT) != null,
                gpsLat      = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE),
                gpsLon      = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE),
                make        = exif.getAttribute(ExifInterface.TAG_MAKE),
                model       = exif.getAttribute(ExifInterface.TAG_MODEL),
                software    = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
            )
            tmpFile.delete(); info
        } catch (_: Exception) { MetadataInfo(false,false,false,false,null,null,null,null,null) }
    }

    suspend fun nukeAll(context: Context, uri: Uri, onProgress: (Int, String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(10, "Reading file…")
            val stream = context.contentResolver.openInputStream(uri)!!
            val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "UCEngine").also { it.mkdirs() }
            val outFile = File(outDir, "clean_${System.currentTimeMillis()}.jpg")
            outFile.outputStream().use { stream.copyTo(it) }

            onProgress(50, "Removing metadata…")
            val exif = ExifInterface(outFile.absolutePath)
            listOf(
                ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_CAMERA_OWNER_NAME, ExifInterface.TAG_BODY_SERIAL_NUMBER,
                ExifInterface.TAG_IMAGE_UNIQUE_ID, ExifInterface.TAG_LENS_SERIAL_NUMBER
            ).forEach { exif.setAttribute(it, null) }
            exif.saveAttributes()

            onProgress(100, "All metadata removed!"); outFile.absolutePath
        } catch (e: Exception) { null }
    }
}
