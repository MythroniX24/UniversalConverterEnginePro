package com.universalconverter.pro.engine

import android.content.Context
import android.net.Uri
import java.io.File

enum class FileCategory {
    IMAGE, DOCUMENT, MEDIA_VIDEO, MEDIA_AUDIO, ARCHIVE, THREE_D, UNKNOWN
}

data class FileInfo(
    val uri: Uri,
    val name: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: FileCategory,
    val validOutputFormats: List<String>,
    val suggestedFormat: String
)

object FileDetector {

    // ─── Valid conversion matrix (mirrors C++) ────────────────────────────────
    private val IMAGE_FORMATS   = setOf("jpg","jpeg","png","webp","bmp","tiff","gif")
    private val DOC_FORMATS     = setOf("pdf")
    private val VIDEO_FORMATS   = setOf("mp4","mkv","avi","mov","webm","3gp")
    private val AUDIO_FORMATS   = setOf("mp3","aac","wav","ogg","flac","m4a")
    private val THREE_D_FORMATS = setOf("obj","fbx","stl")

    fun analyze(context: Context, uri: Uri): FileInfo {
        val name      = getFileName(context, uri)
        val ext       = name.substringAfterLast('.', "").lowercase()
        val mimeType  = context.contentResolver.getType(uri) ?: guessMime(ext)
        val sizeBytes = getFileSize(context, uri)
        val category  = categorize(ext)
        val valids    = getValidOutputFormats(ext)
        val suggested = suggestBest(ext)

        return FileInfo(
            uri            = uri,
            name           = name,
            extension      = ext,
            mimeType       = mimeType,
            sizeBytes      = sizeBytes,
            category       = category,
            validOutputFormats = valids,
            suggestedFormat = suggested
        )
    }

    fun categorize(ext: String): FileCategory = when (ext.lowercase()) {
        in IMAGE_FORMATS   -> FileCategory.IMAGE
        in DOC_FORMATS     -> FileCategory.DOCUMENT
        in VIDEO_FORMATS   -> FileCategory.MEDIA_VIDEO
        in AUDIO_FORMATS   -> FileCategory.MEDIA_AUDIO
        in THREE_D_FORMATS -> FileCategory.THREE_D
        "zip","rar","7z","tar","gz" -> FileCategory.ARCHIVE
        else               -> FileCategory.UNKNOWN
    }

    fun isValidConversion(fromExt: String, toExt: String): Boolean {
        if (fromExt == toExt) return false
        val from = fromExt.lowercase()
        val to   = toExt.lowercase()
        return when {
            from in IMAGE_FORMATS && to in IMAGE_FORMATS   -> true
            from in IMAGE_FORMATS && to in DOC_FORMATS     -> true
            from in DOC_FORMATS   && to in IMAGE_FORMATS   -> true
            from in VIDEO_FORMATS && to in VIDEO_FORMATS   -> true
            from in VIDEO_FORMATS && to in AUDIO_FORMATS   -> true
            from in AUDIO_FORMATS && to in AUDIO_FORMATS   -> true
            from in THREE_D_FORMATS && to in THREE_D_FORMATS -> true
            else -> false
        }
    }

    fun getValidOutputFormats(ext: String): List<String> {
        val from = ext.lowercase()
        return when {
            from in IMAGE_FORMATS -> {
                (IMAGE_FORMATS - from - "jpeg").toList() + listOf("pdf")
            }
            from in DOC_FORMATS -> {
                listOf("jpg","png","webp","bmp")
            }
            from in VIDEO_FORMATS -> {
                (VIDEO_FORMATS - from).toList() + AUDIO_FORMATS.toList()
            }
            from in AUDIO_FORMATS -> {
                (AUDIO_FORMATS - from).toList()
            }
            from in THREE_D_FORMATS -> {
                (THREE_D_FORMATS - from).toList()
            }
            else -> emptyList()
        }
    }

    fun suggestBest(ext: String): String = when {
        ext in IMAGE_FORMATS   -> "webp"
        ext in VIDEO_FORMATS   -> "mp4"
        ext in AUDIO_FORMATS   -> "aac"
        ext in THREE_D_FORMATS -> "obj"
        else -> ext
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024L               -> "$bytes B"
        bytes < 1024L * 1024L       -> "%.1f KB".format(bytes / 1024f)
        bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
        else                        -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
    }

    private fun getFileName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path ?: "").name
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (idx >= 0) it.getString(idx) else "file"
        } ?: "file"
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") return File(uri.path ?: "").length()
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            it.moveToFirst()
            if (idx >= 0) it.getLong(idx) else -1L
        } ?: -1L
    }

    private fun guessMime(ext: String): String = when (ext.lowercase()) {
        "jpg","jpeg" -> "image/jpeg"
        "png"  -> "image/png"
        "webp" -> "image/webp"
        "gif"  -> "image/gif"
        "bmp"  -> "image/bmp"
        "tiff" -> "image/tiff"
        "pdf"  -> "application/pdf"
        "mp4"  -> "video/mp4"
        "mkv"  -> "video/x-matroska"
        "avi"  -> "video/x-msvideo"
        "mov"  -> "video/quicktime"
        "mp3"  -> "audio/mpeg"
        "aac"  -> "audio/aac"
        "wav"  -> "audio/wav"
        "ogg"  -> "audio/ogg"
        "flac" -> "audio/flac"
        "obj"  -> "model/obj"
        else   -> "application/octet-stream"
    }
}
