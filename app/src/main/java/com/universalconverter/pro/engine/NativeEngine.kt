package com.universalconverter.pro.engine

import android.graphics.Bitmap

/**
 * JNI bridge to the native C++ converter engine.
 * All methods map 1:1 to C++ implementations in converter_engine.cpp,
 * image_processor.cpp, file_detector.cpp, thread_pool.cpp.
 */
object NativeEngine {

    init {
        System.loadLibrary("universalconverter")
    }

    // ─── Engine Info ──────────────────────────────────────────────────────────
    external fun getEngineVersion(): String

    // ─── File Detection ───────────────────────────────────────────────────────
    /** Returns MIME type string based on file extension */
    external fun detectMimeType(filePath: String): String

    /** Returns FormatCategory ordinal (0=IMAGE, 1=DOCUMENT, 2=VIDEO, 3=AUDIO, 4=ARCHIVE, 5=3D, 6=UNKNOWN) */
    external fun detectCategory(filePath: String): Int

    /** Reads magic bytes from the file to detect true format */
    external fun detectByMagicBytes(filePath: String): String

    /** Returns true if the from→to conversion is valid and supported */
    external fun isValidConversion(fromExt: String, toExt: String): Boolean

    /** Returns an array of valid output format extensions for the given input file */
    external fun getValidOutputFormats(inputPath: String): Array<String>

    /** Suggests the best output format for smallest size with good quality */
    external fun suggestBestFormat(inputPath: String): String

    /** Predicts output file size in bytes for the given format and quality */
    external fun predictOutputSize(inputPath: String, targetFormat: String, quality: Int): Long

    // ─── File Info ────────────────────────────────────────────────────────────
    external fun getFileSize(filePath: String): Long
    external fun fileExists(filePath: String): Boolean

    // ─── Bitmap Operations ────────────────────────────────────────────────────
    /** High-performance native bitmap resize using bilinear interpolation */
    external fun resizeBitmapNative(srcBitmap: Bitmap, dstBitmap: Bitmap): Boolean

    /** Compute mean brightness (0.0–1.0) */
    external fun computeBrightness(bitmap: Bitmap): Float

    /** Compute image complexity score (0.0–1.0) based on edge detection */
    external fun computeImageComplexity(bitmap: Bitmap): Float

    /** Convert bitmap to greyscale in-place */
    external fun applyGrayscale(bitmap: Bitmap)

    /** Apply light blur in-place (radius 1–3) */
    external fun applyLightBlur(bitmap: Bitmap, radius: Int)

    /** Adjust brightness (–1.0 to +1.0) and contrast (0.5–2.0) */
    external fun adjustBrightnessContrast(bitmap: Bitmap, brightness: Float, contrast: Float)

    // ─── Compression ─────────────────────────────────────────────────────────
    /** Estimate quality (0–100) needed to reach target size */
    external fun estimateOptimalQuality(
        inputSizeBytes: Long,
        targetSizeBytes: Long,
        format: String
    ): Int

    /** Returns savings fraction 0.0–1.0 */
    external fun computeCompressionRatio(originalSize: Long, compressedSize: Long): Float

    /** Native RLE data compression (for raw byte buffers) */
    external fun compressDataNative(inputData: ByteArray): ByteArray

    /** Native RLE data decompression */
    external fun decompressDataNative(inputData: ByteArray): ByteArray

    // ─── Threading ────────────────────────────────────────────────────────────
    external fun getAvailableThreads(): Int

    // ─── Control ─────────────────────────────────────────────────────────────
    external fun cancelOperation()

    // ─── Category Constants ───────────────────────────────────────────────────
    const val CATEGORY_IMAGE    = 0
    const val CATEGORY_DOCUMENT = 1
    const val CATEGORY_VIDEO    = 2
    const val CATEGORY_AUDIO    = 3
    const val CATEGORY_ARCHIVE  = 4
    const val CATEGORY_3D       = 5
    const val CATEGORY_UNKNOWN  = 6
}
