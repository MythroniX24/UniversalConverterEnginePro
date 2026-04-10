package com.universalconverter.pro.engine

import android.graphics.Bitmap
import android.util.Log

/**
 * JNI bridge to the native C++ converter engine.
 * Safe wrappers — never crashes even if .so fails to load.
 */
object NativeEngine {

    private const val TAG = "NativeEngine"
    var isLoaded: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("universalconverter")
            isLoaded = true
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            isLoaded = false
            Log.e(TAG, "Failed to load native library: ${e.message}")
        } catch (e: Exception) {
            isLoaded = false
            Log.e(TAG, "Unexpected error loading native library: ${e.message}")
        }
    }

    // ─── Safe wrappers — fallback if .so not loaded ───────────────────────────

    fun getEngineVersion(): String =
        if (isLoaded) try { getEngineVersionNative() } catch (e: Exception) { "1.0.0-fallback" }
        else "1.0.0-fallback"

    fun detectMimeType(filePath: String): String =
        if (isLoaded) try { detectMimeTypeNative(filePath) } catch (e: Exception) { "application/octet-stream" }
        else "application/octet-stream"

    fun detectCategory(filePath: String): Int =
        if (isLoaded) try { detectCategoryNative(filePath) } catch (e: Exception) { CATEGORY_UNKNOWN }
        else CATEGORY_UNKNOWN

    fun detectByMagicBytes(filePath: String): String =
        if (isLoaded) try { detectByMagicBytesNative(filePath) } catch (e: Exception) { "unknown" }
        else "unknown"

    fun isValidConversion(fromExt: String, toExt: String): Boolean =
        if (isLoaded) try { isValidConversionNative(fromExt, toExt) } catch (e: Exception) { false }
        else false

    fun getValidOutputFormats(inputPath: String): Array<String> =
        if (isLoaded) try { getValidOutputFormatsNative(inputPath) } catch (e: Exception) { emptyArray() }
        else emptyArray()

    fun suggestBestFormat(inputPath: String): String =
        if (isLoaded) try { suggestBestFormatNative(inputPath) } catch (e: Exception) { "jpg" }
        else "jpg"

    fun predictOutputSize(inputPath: String, targetFormat: String, quality: Int): Long =
        if (isLoaded) try { predictOutputSizeNative(inputPath, targetFormat, quality) } catch (e: Exception) { -1L }
        else -1L

    fun getFileSize(filePath: String): Long =
        if (isLoaded) try { getFileSizeNative(filePath) } catch (e: Exception) { -1L }
        else -1L

    fun fileExists(filePath: String): Boolean =
        if (isLoaded) try { fileExistsNative(filePath) } catch (e: Exception) { false }
        else false

    fun resizeBitmapNative(srcBitmap: Bitmap, dstBitmap: Bitmap): Boolean =
        if (isLoaded) try { resizeBitmapNativeImpl(srcBitmap, dstBitmap) } catch (e: Exception) { false }
        else false

    fun computeBrightness(bitmap: Bitmap): Float =
        if (isLoaded) try { computeBrightnessNative(bitmap) } catch (e: Exception) { 0.5f }
        else 0.5f

    fun computeImageComplexity(bitmap: Bitmap): Float =
        if (isLoaded) try { computeImageComplexityNative(bitmap) } catch (e: Exception) { 0.5f }
        else 0.5f

    fun applyGrayscale(bitmap: Bitmap) {
        if (isLoaded) try { applyGrayscaleNative(bitmap) } catch (e: Exception) { /* ignore */ }
    }

    fun applyLightBlur(bitmap: Bitmap, radius: Int) {
        if (isLoaded) try { applyLightBlurNative(bitmap, radius) } catch (e: Exception) { /* ignore */ }
    }

    fun adjustBrightnessContrast(bitmap: Bitmap, brightness: Float, contrast: Float) {
        if (isLoaded) try { adjustBrightnessContrastNative(bitmap, brightness, contrast) } catch (e: Exception) { /* ignore */ }
    }

    fun estimateOptimalQuality(inputSizeBytes: Long, targetSizeBytes: Long, format: String): Int =
        if (isLoaded) try { estimateOptimalQualityNative(inputSizeBytes, targetSizeBytes, format) } catch (e: Exception) { 85 }
        else 85

    fun computeCompressionRatio(originalSize: Long, compressedSize: Long): Float =
        if (isLoaded) try { computeCompressionRatioNative(originalSize, compressedSize) } catch (e: Exception) { 0f }
        else 0f

    fun compressDataNative(inputData: ByteArray): ByteArray =
        if (isLoaded) try { compressDataNativeImpl(inputData) } catch (e: Exception) { inputData }
        else inputData

    fun decompressDataNative(inputData: ByteArray): ByteArray =
        if (isLoaded) try { decompressDataNativeImpl(inputData) } catch (e: Exception) { inputData }
        else inputData

    fun getAvailableThreads(): Int =
        if (isLoaded) try { getAvailableThreadsNative() } catch (e: Exception) { 2 }
        else 2

    fun cancelOperation() {
        if (isLoaded) try { cancelOperationNative() } catch (e: Exception) { /* ignore */ }
    }

    // ─── Category Constants ───────────────────────────────────────────────────
    const val CATEGORY_IMAGE    = 0
    const val CATEGORY_DOCUMENT = 1
    const val CATEGORY_VIDEO    = 2
    const val CATEGORY_AUDIO    = 3
    const val CATEGORY_ARCHIVE  = 4
    const val CATEGORY_3D       = 5
    const val CATEGORY_UNKNOWN  = 6

    // ─── Native declarations (private) ────────────────────────────────────────
    private external fun getEngineVersionNative(): String
    private external fun detectMimeTypeNative(filePath: String): String
    private external fun detectCategoryNative(filePath: String): Int
    private external fun detectByMagicBytesNative(filePath: String): String
    private external fun isValidConversionNative(fromExt: String, toExt: String): Boolean
    private external fun getValidOutputFormatsNative(inputPath: String): Array<String>
    private external fun suggestBestFormatNative(inputPath: String): String
    private external fun predictOutputSizeNative(inputPath: String, targetFormat: String, quality: Int): Long
    private external fun getFileSizeNative(filePath: String): Long
    private external fun fileExistsNative(filePath: String): Boolean
    private external fun resizeBitmapNativeImpl(srcBitmap: Bitmap, dstBitmap: Bitmap): Boolean
    private external fun computeBrightnessNative(bitmap: Bitmap): Float
    private external fun computeImageComplexityNative(bitmap: Bitmap): Float
    private external fun applyGrayscaleNative(bitmap: Bitmap)
    private external fun applyLightBlurNative(bitmap: Bitmap, radius: Int)
    private external fun adjustBrightnessContrastNative(bitmap: Bitmap, brightness: Float, contrast: Float)
    private external fun estimateOptimalQualityNative(inputSizeBytes: Long, targetSizeBytes: Long, format: String): Int
    private external fun computeCompressionRatioNative(originalSize: Long, compressedSize: Long): Float
    private external fun compressDataNativeImpl(inputData: ByteArray): ByteArray
    private external fun decompressDataNativeImpl(inputData: ByteArray): ByteArray
    private external fun getAvailableThreadsNative(): Int
    private external fun cancelOperationNative()
}
