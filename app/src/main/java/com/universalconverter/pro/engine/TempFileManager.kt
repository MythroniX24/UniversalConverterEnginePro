package com.universalconverter.pro.engine

import android.content.Context
import android.os.Environment
import java.io.File

object TempFileManager {

    fun cleanupOldFiles(context: Context, maxAgeHours: Long = 24) {
        val dirs = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "UCEngine"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "UCEngine"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),    "UCEngine"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),   "UCEngine"),
            context.cacheDir
        )
        val cutoffMs = System.currentTimeMillis() - maxAgeHours * 3600 * 1000
        dirs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoffMs) {
                    file.delete()
                }
            }
        }
    }

    fun getTotalCacheSize(context: Context): Long {
        val dirs = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "UCEngine"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "UCEngine"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),    "UCEngine"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),   "UCEngine"),
            context.cacheDir
        )
        return dirs.sumOf { dir ->
            dir.listFiles()?.sumOf { it.length() } ?: 0L
        }
    }

    fun clearAll(context: Context) {
        cleanupOldFiles(context, maxAgeHours = 0)
    }
}
