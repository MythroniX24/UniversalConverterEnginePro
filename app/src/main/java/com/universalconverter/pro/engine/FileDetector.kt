package com.universalconverter.pro.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

enum class FileCategory { IMAGE, DOCUMENT, MEDIA_VIDEO, MEDIA_AUDIO, ARCHIVE, THREE_D, UNKNOWN }

data class FileInfo(
    val uri: Uri, val name: String, val extension: String,
    val mimeType: String, val sizeBytes: Long, val category: FileCategory,
    val validOutputFormats: List<String>, val suggestedFormat: String
)

object FileDetector {
    val IMAGE_FORMATS = setOf("jpg","jpeg","png","webp","bmp","gif","tiff","ico","avif")
    val DOC_FORMATS   = setOf("pdf")
    val VIDEO_FORMATS = setOf("mp4","mkv","avi","mov","webm","3gp")
    val AUDIO_FORMATS = setOf("mp3","aac","wav","ogg","flac","m4a")
    val TD_FORMATS    = setOf("obj","fbx","stl")

    fun analyze(context: Context, uri: Uri): FileInfo {
        val name   = getName(context, uri)
        val ext    = name.substringAfterLast('.', "").lowercase()
        val mime   = context.contentResolver.getType(uri) ?: guessMime(ext)
        val size   = getSize(context, uri)
        val cat    = categorize(ext)
        return FileInfo(uri, name, ext, mime, size, cat, validOutputs(ext), suggestBest(ext))
    }

    fun categorize(ext: String) = when (ext.lowercase()) {
        in IMAGE_FORMATS -> FileCategory.IMAGE
        in DOC_FORMATS   -> FileCategory.DOCUMENT
        in VIDEO_FORMATS -> FileCategory.MEDIA_VIDEO
        in AUDIO_FORMATS -> FileCategory.MEDIA_AUDIO
        in TD_FORMATS    -> FileCategory.THREE_D
        "zip","rar","7z","tar","gz" -> FileCategory.ARCHIVE
        else -> FileCategory.UNKNOWN
    }

    fun validOutputs(ext: String): List<String> = when (ext.lowercase()) {
        in IMAGE_FORMATS -> (IMAGE_FORMATS - ext - "jpeg").toList() + listOf("pdf")
        in DOC_FORMATS   -> listOf("jpg","png","webp")
        in VIDEO_FORMATS -> (VIDEO_FORMATS - ext).toList() + AUDIO_FORMATS.toList()
        in AUDIO_FORMATS -> (AUDIO_FORMATS - ext).toList()
        in TD_FORMATS    -> (TD_FORMATS - ext).toList()
        else             -> emptyList()
    }

    fun suggestBest(ext: String) = when {
        ext in IMAGE_FORMATS   -> "webp"
        ext in VIDEO_FORMATS   -> "mp4"
        ext in AUDIO_FORMATS   -> "aac"
        else -> ext
    }

    fun isValidConversion(from: String, to: String): Boolean {
        if (from == to) return false
        val fi=from in IMAGE_FORMATS; val fd=from in DOC_FORMATS
        val fv=from in VIDEO_FORMATS; val fa=from in AUDIO_FORMATS; val f3=from in TD_FORMATS
        val ti=to in IMAGE_FORMATS;   val td=to in DOC_FORMATS
        val tv=to in VIDEO_FORMATS;   val ta=to in AUDIO_FORMATS;   val t3=to in TD_FORMATS
        return (fi&&ti)||(fi&&td)||(fd&&ti)||(fv&&tv)||(fv&&ta)||(fa&&ta)||(f3&&t3)
    }

    fun formatSize(bytes: Long) = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L*1024 -> "%.1f KB".format(bytes/1024f)
        bytes < 1024L*1024*1024 -> "%.1f MB".format(bytes/1024f/1024f)
        else -> "%.2f GB".format(bytes/1024f/1024f/1024f)
    }

    fun formatReduction(orig: Long, now: Long): String {
        if (orig <= 0) return ""
        val pct = ((orig - now).toDouble() / orig * 100).toInt()
        return if (pct > 0) "↓$pct% (${formatSize(orig-now)} saved)" else "↑${-pct}% larger"
    }

    private fun getName(ctx: Context, uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path ?: "").name
        return ctx.contentResolver.query(uri, null, null, null, null)?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst(); if (idx >= 0) it.getString(idx) else "file"
        } ?: "file"
    }

    private fun getSize(ctx: Context, uri: Uri): Long {
        if (uri.scheme == "file") return File(uri.path ?: "").length()
        return ctx.contentResolver.query(uri, null, null, null, null)?.use {
            val idx = it.getColumnIndex(OpenableColumns.SIZE)
            it.moveToFirst(); if (idx >= 0) it.getLong(idx) else -1L
        } ?: -1L
    }

    private fun guessMime(ext: String) = when (ext) {
        "jpg","jpeg"->"image/jpeg"; "png"->"image/png"; "webp"->"image/webp"
        "gif"->"image/gif"; "bmp"->"image/bmp"; "tiff"->"image/tiff"
        "pdf"->"application/pdf"; "mp4"->"video/mp4"; "mkv"->"video/x-matroska"
        "mp3"->"audio/mpeg"; "aac"->"audio/aac"; "wav"->"audio/wav"
        "obj"->"model/obj"; "stl"->"model/stl"; else->"application/octet-stream"
    }
}
