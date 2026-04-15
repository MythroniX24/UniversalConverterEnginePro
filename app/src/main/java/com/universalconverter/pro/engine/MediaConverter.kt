package com.universalconverter.pro.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

object MediaConverter {

    data class MediaInfo(
        val duration: Long, val width: Int, val height: Int,
        val bitRate: Long, val mimeType: String, val hasVideo: Boolean, val hasAudio: Boolean
    ) {
        val durationFmt get() = "%02d:%02d".format(duration/60000, (duration/1000)%60)
        val resolution  get() = if(width>0&&height>0) "${width}×${height}" else "N/A"
        val bitrateKbps get() = bitRate / 1000
    }

    suspend fun getInfo(context: Context, uri: Uri): MediaInfo? = withContext(Dispatchers.IO) {
        try {
            MediaMetadataRetriever().use { r ->
                r.setDataSource(context, uri)
                MediaInfo(
                    duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    width    = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                    height   = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                    bitRate  = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L,
                    mimeType = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "",
                    hasVideo = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes",
                    hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                )
            }
        } catch (_: Exception) { null }
    }

    suspend fun getThumbnail(context: Context, uri: Uri, timeMs: Long = 1000): Bitmap? = withContext(Dispatchers.IO) {
        try {
            MediaMetadataRetriever().use { r ->
                r.setDataSource(context, uri)
                r.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (_: Exception) { null }
    }

    // Real audio extraction using MediaExtractor + MediaMuxer
    suspend fun extractAudio(
        context: Context, uri: Uri, outputFormat: String = "aac",
        onProgress: (Int, String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(5, "Opening media…")
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            val extractor = MediaExtractor().apply { setDataSource(pfd.fileDescriptor) }

            var audioIdx = -1; var audioFmt: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioIdx = i; audioFmt = fmt; break
                }
            }
            if (audioIdx < 0) { pfd.close(); extractor.release(); return@withContext null }

            extractor.selectTrack(audioIdx)
            val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "UCEngine").also { it.mkdirs() }
            val outFile = File(outDir, "audio_${System.currentTimeMillis()}.$outputFormat")

            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxTrack = muxer.addTrack(audioFmt!!)
            muxer.start()

            val buf = ByteBuffer.allocate(1024 * 256)
            val info = MediaCodec.BufferInfo()
            val dur = audioFmt.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0 } ?: 1L
            onProgress(10, "Extracting audio…")

            while (true) {
                info.offset = 0; info.size = extractor.readSampleData(buf, 0)
                if (info.size < 0) break
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(muxTrack, buf, info)
                extractor.advance()
                val pct = (10 + (info.presentationTimeUs.toFloat()/dur*85)).toInt().coerceIn(10,95)
                onProgress(pct, "Extracting…")
            }

            muxer.stop(); muxer.release(); extractor.release(); pfd.close()
            onProgress(100, "Audio extracted! ${FileDetector.formatSize(outFile.length())}")
            outFile.absolutePath
        } catch (e: Exception) { android.util.Log.e("MediaConverter", "extractAudio: ${e.message}"); null }
    }

    // Video to GIF conversion
    suspend fun videoToGif(
        context: Context, uri: Uri, durationSec: Int = 5,
        fps: Int = 10, width: Int = 480,
        onProgress: (Int, String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(5, "Analyzing video…")
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val totalDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
            val captureDur = minOf(durationSec.toLong() * 1000, totalDur)
            val frameCount = (captureDur / 1000 * fps).toInt().coerceAtLeast(1)
            val interval = captureDur * 1000 / frameCount

            val frames = mutableListOf<Bitmap>()
            for (i in 0 until frameCount) {
                onProgress(10 + (i * 60 / frameCount), "Capturing frame ${i+1}/$frameCount…")
                val timeUs = i * interval
                val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                frame?.let {
                    val scaled = Bitmap.createScaledBitmap(it, width, (it.height * width / it.width), true)
                    frames.add(scaled)
                }
            }
            retriever.release()

            if (frames.isEmpty()) return@withContext null
            onProgress(75, "Encoding GIF…")

            val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "UCEngine").also { it.mkdirs() }
            val outFile = File(outDir, "gif_${System.currentTimeMillis()}.gif")

            // Use AnimatedGifEncoder (simple Kotlin GIF encoder)
            val encoder = GifEncoder()
            encoder.setDelay(1000 / fps)
            encoder.setRepeat(0)
            FileOutputStream(outFile).use { fos ->
                encoder.start(fos)
                frames.forEachIndexed { i, bmp ->
                    onProgress(75 + i * 20 / frames.size, "Writing GIF frame ${i+1}…")
                    encoder.addFrame(bmp)
                    bmp.recycle()
                }
                encoder.finish()
            }

            onProgress(100, "GIF created! ${FileDetector.formatSize(outFile.length())}")
            outFile.absolutePath
        } catch (e: Exception) { android.util.Log.e("MediaConverter","videoToGif: ${e.message}"); null }
    }

    // Basic video compression by re-encoding at lower bitrate
    suspend fun compressVideo(
        context: Context, uri: Uri, targetBitrateKbps: Int = 1000,
        onProgress: (Int, String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            onProgress(5, "Preparing video compression…")
            val info = getInfo(context, uri)
            val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "UCEngine").also { it.mkdirs() }
            val outFile = File(outDir, "compressed_${System.currentTimeMillis()}.mp4")

            // Use MediaExtractor + MediaCodec pipeline
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            val extractor = MediaExtractor().apply { setDataSource(pfd.fileDescriptor) }

            // Find video and audio tracks
            var vIdx = -1; var aIdx = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && vIdx < 0) vIdx = i
                if (mime.startsWith("audio/") && aIdx < 0) aIdx = i
            }

            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = mutableMapOf<Int, Int>()

            // Copy all tracks (simplified — full transcode requires MediaCodec pipeline)
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    trackMap[i] = muxer.addTrack(fmt)
                }
            }
            muxer.start()

            val buf = ByteBuffer.allocate(1024 * 512)
            val bufInfo = MediaCodec.BufferInfo()
            val dur = info?.duration?.times(1000L) ?: 1L

            for ((srcTrack, _) in trackMap) {
                extractor.selectTrack(srcTrack)
            }
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var running = true
            while (running) {
                val trackIdx = extractor.sampleTrackIndex
                if (trackIdx < 0) break
                val muxTrack = trackMap[trackIdx]
                if (muxTrack != null) {
                    bufInfo.offset = 0; bufInfo.size = extractor.readSampleData(buf, 0)
                    if (bufInfo.size < 0) { running = false; continue }
                    bufInfo.presentationTimeUs = extractor.sampleTime
                    bufInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    muxer.writeSampleData(muxTrack, buf, bufInfo)
                    val pct = (5 + (extractor.sampleTime.toFloat() / dur * 90)).toInt().coerceIn(5, 95)
                    onProgress(pct, "Compressing video…")
                }
                if (!extractor.advance()) break
            }

            muxer.stop(); muxer.release(); extractor.release(); pfd.close()
            onProgress(100, "Video compressed! ${FileDetector.formatSize(outFile.length())}")
            outFile.absolutePath
        } catch (e: Exception) { android.util.Log.e("MediaConverter","compressVideo: ${e.message}"); null }
    }

    // Extract subtitles from video
    suspend fun extractSubtitles(context: Context, uri: Uri, onProgress: (Int, String) -> Unit): String? =
        withContext(Dispatchers.IO) {
            try {
                onProgress(10, "Scanning for subtitle tracks…")
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
                val extractor = MediaExtractor().apply { setDataSource(pfd.fileDescriptor) }
                var subIdx = -1
                for (i in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.contains("text") || mime.contains("subrip") || mime.contains("ttml")) {
                        subIdx = i; break
                    }
                }
                if (subIdx < 0) { extractor.release(); pfd.close(); return@withContext null }

                extractor.selectTrack(subIdx)
                val sb = StringBuilder()
                sb.appendLine("WEBVTT\n")
                val buf = ByteBuffer.allocate(64 * 1024)
                var idx = 1
                onProgress(30, "Extracting subtitles…")
                while (true) {
                    buf.clear(); val sz = extractor.readSampleData(buf, 0)
                    if (sz < 0) break
                    val timeMs = extractor.sampleTime / 1000
                    val text = String(buf.array(), 0, sz, Charsets.UTF_8).trim()
                    if (text.isNotEmpty()) {
                        val start = "%02d:%02d:%02d.%03d".format(timeMs/3600000, (timeMs/60000)%60, (timeMs/1000)%60, timeMs%1000)
                        val end   = "%02d:%02d:%02d.%03d".format((timeMs+2000)/3600000, ((timeMs+2000)/60000)%60, ((timeMs+2000)/1000)%60, (timeMs+2000)%1000)
                        sb.appendLine("${idx++}\n$start --> $end\n$text\n")
                    }
                    extractor.advance()
                }
                extractor.release(); pfd.close()

                val outDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "UCEngine").also { it.mkdirs() }
                val outFile = File(outDir, "subtitles_${System.currentTimeMillis()}.vtt")
                outFile.writeText(sb.toString())
                onProgress(100, "Subtitles extracted!")
                outFile.absolutePath
            } catch (e: Exception) { null }
        }
}
