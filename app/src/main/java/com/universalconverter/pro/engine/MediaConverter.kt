package com.universalconverter.pro.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object MediaConverter {

    // ─── Extract Audio from Video ─────────────────────────────────────────────
    suspend fun extractAudio(
        context: Context,
        videoUri: Uri,
        outputFormat: String = "aac",
        onProgress: (Int, String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(5, "Reading media…")
            val pfd = context.contentResolver.openFileDescriptor(videoUri, "r")
                ?: return@withContext null

            val extractor = MediaExtractor()
            extractor.setDataSource(pfd.fileDescriptor)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }

            if (audioTrackIndex == -1) {
                pfd.close()
                return@withContext null
            }

            onProgress(15, "Extracting audio track…")
            extractor.selectTrack(audioTrackIndex)

            val outputDir  = getOutputDir(context, "audio")
            val ext        = when (outputFormat.lowercase()) {
                "aac"  -> "aac"
                "mp3"  -> "mp4" // wrap in container
                else   -> "mp4"
            }
            val outFile    = File(outputDir, "audio_${System.currentTimeMillis()}.$ext")
            val muxerFormat = when (ext) {
                "aac"  -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                else   -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            val muxer = MediaMuxer(outFile.absolutePath, muxerFormat)
            val muxerTrack = muxer.addTrack(audioFormat!!)
            muxer.start()

            val buffer   = ByteBuffer.allocate(1024 * 1024)
            val bufInfo  = MediaCodec.BufferInfo()

            onProgress(20, "Muxing audio…")

            // Get duration for progress reporting
            val duration = audioFormat.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0 } ?: 1L
            var lastProgress = 20

            while (true) {
                bufInfo.offset = 0
                bufInfo.size   = extractor.readSampleData(buffer, 0)
                if (bufInfo.size < 0) break

                bufInfo.presentationTimeUs = extractor.sampleTime
                bufInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0

                muxer.writeSampleData(muxerTrack, buffer, bufInfo)
                extractor.advance()

                val progress = (20 + ((bufInfo.presentationTimeUs.toFloat() / duration) * 75)).toInt()
                    .coerceIn(20, 95)
                if (progress > lastProgress) {
                    onProgress(progress, "Extracting audio…")
                    lastProgress = progress
                }
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            pfd.close()

            onProgress(100, "Audio extracted!")
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // ─── Video Info ───────────────────────────────────────────────────────────
    suspend fun getMediaInfo(
        context: Context,
        uri: Uri
    ): MediaInfo? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val info = MediaInfo(
                duration    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width       = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height      = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                bitRate     = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L,
                mimeType    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "",
                hasVideo    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes",
                hasAudio    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
                frameRate   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
            )
            retriever.release()
            info
        } catch (e: Exception) {
            null
        }
    }

    // ─── Video thumbnail ─────────────────────────────────────────────────────
    suspend fun getVideoThumbnail(
        context: Context,
        uri: Uri,
        timeMs: Long = 1000L
    ) = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(
                timeMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            retriever.release()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun getOutputDir(context: Context, type: String): File {
        val dir = File(
            context.getExternalFilesDir(
                if (type == "audio") Environment.DIRECTORY_MUSIC
                else Environment.DIRECTORY_MOVIES
            ),
            "UCEngine"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    data class MediaInfo(
        val duration: Long,
        val width: Int,
        val height: Int,
        val bitRate: Long,
        val mimeType: String,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val frameRate: Float
    ) {
        val durationFormatted: String get() {
            val secs  = duration / 1000
            val mins  = secs / 60
            val hours = mins / 60
            return if (hours > 0) "%02d:%02d:%02d".format(hours, mins % 60, secs % 60)
                   else           "%02d:%02d".format(mins, secs % 60)
        }

        val resolutionLabel: String get() = if (width > 0 && height > 0) "${width}×${height}" else "Unknown"
    }
}
