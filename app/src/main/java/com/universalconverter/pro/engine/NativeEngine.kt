package com.universalconverter.pro.engine

import android.graphics.Bitmap
import android.util.Log

object NativeEngine {
    private const val TAG = "NativeEngine"
    var isLoaded = false; private set

    init {
        try { System.loadLibrary("universalconverter"); isLoaded = true; Log.i(TAG, "NDK loaded ✓") }
        catch (e: Throwable) { Log.e(TAG, "NDK load failed: ${e.message}") }
    }

    fun getVersion()                                = safe("2.0.0") { getVersion() }
    fun getCategory(path: String)                   = safeInt(6)   { getCategoryNative(path) }
    fun isValidConversion(from: String, to: String) = safeBool     { isValidConversionNative(from, to) }
    fun getFileSize(path: String)                   = safeLong(-1) { getFileSizeNative(path) }
    fun predictSize(sz: Long, fmt: String, q: Int)  = safeLong(-1) { predictSizeNative(sz, fmt, q) }
    fun estimateQuality(inSz: Long, tgt: Long)      = safeInt(85)  { estimateQualityNative(inSz, tgt) }
    fun computeBrightness(bmp: Bitmap)              = safeFloat(.5f){ computeBrightnessNative(bmp) }
    fun computeComplexity(bmp: Bitmap)              = safeFloat(.5f){ computeComplexityNative(bmp) }
    fun computeSSIM(b1: Bitmap, b2: Bitmap)         = safeFloat(-1f){ computeSSIMNative(b1, b2) }
    fun perceptualHash(bmp: Bitmap)                 = safeLong(0)  { computePerceptualHashNative(bmp) }
    fun hammingDistance(h1: Long, h2: Long)         = safeInt(64)  { hammingDistanceNative(h1, h2) }
    fun isDuplicate(h1: Long, h2: Long, thr: Int)   = safeBool     { isDuplicateNative(h1, h2, thr) }
    fun classifyContent(bmp: Bitmap)                = safeInt(0)   { classifyImageContentNative(bmp) }
    fun recommendedQuality(ct: Int, mode: Int)      = safeInt(80)  { getRecommendedQualityNative(ct, mode) }
    fun recommendFormat(ct: Int, alpha: Boolean)    = safeInt(2)   { recommendFormatNative(ct, alpha) }
    fun applyGrayscale(bmp: Bitmap)                 { if (isLoaded) try { applyGrayscaleNative(bmp) } catch (_: Exception) {} }
    fun resizeBitmap(src: Bitmap, dst: Bitmap)      = safeBool     { resizeBitmapNative(src, dst) }
    fun upscaleBitmap(src: Bitmap, dst: Bitmap, sharpness: Float) = safeBool { upscaleBitmapNative(src, dst, sharpness) }
    fun getThreadCount()                            = safeInt(2)   { getThreadCountNative() }
    fun cancel()  { if (isLoaded) try { cancelNative() }      catch (_: Exception) {} }
    fun resetCancel() { if (isLoaded) try { resetCancelNative() } catch (_: Exception) {} }

    private fun safe(default: String, block: NativeEngine.() -> String) =
        if (isLoaded) try { block() } catch (_: Exception) { default } else default
    private fun safeInt(d: Int, block: NativeEngine.() -> Int) =
        if (isLoaded) try { block() } catch (_: Exception) { d } else d
    private fun safeLong(d: Long, block: NativeEngine.() -> Long) =
        if (isLoaded) try { block() } catch (_: Exception) { d } else d
    private fun safeFloat(d: Float, block: NativeEngine.() -> Float) =
        if (isLoaded) try { block() } catch (_: Exception) { d } else d
    private fun safeBool(block: NativeEngine.() -> Boolean) =
        if (isLoaded) try { block() } catch (_: Exception) { false } else false

    private external fun getVersion(): String
    private external fun getCategoryNative(path: String): Int
    private external fun isValidConversionNative(from: String, to: String): Boolean
    private external fun getFileSizeNative(path: String): Long
    private external fun predictSizeNative(sz: Long, fmt: String, q: Int): Long
    private external fun estimateQualityNative(inSz: Long, tgt: Long): Int
    private external fun computeBrightnessNative(bmp: Bitmap): Float
    private external fun computeComplexityNative(bmp: Bitmap): Float
    private external fun computeSSIMNative(b1: Bitmap, b2: Bitmap): Float
    private external fun computePerceptualHashNative(bmp: Bitmap): Long
    private external fun hammingDistanceNative(h1: Long, h2: Long): Int
    private external fun isDuplicateNative(h1: Long, h2: Long, thr: Int): Boolean
    private external fun classifyImageContentNative(bmp: Bitmap): Int
    private external fun getRecommendedQualityNative(ct: Int, mode: Int): Int
    private external fun recommendFormatNative(ct: Int, alpha: Boolean): Int
    private external fun applyGrayscaleNative(bmp: Bitmap)
    private external fun resizeBitmapNative(src: Bitmap, dst: Bitmap): Boolean
    private external fun upscaleBitmapNative(src: Bitmap, dst: Bitmap, sharpness: Float): Boolean
    private external fun getThreadCountNative(): Int
    private external fun cancelNative()
    private external fun resetCancelNative()

    const val CAT_IMAGE = 0; const val CAT_DOC = 1; const val CAT_VIDEO = 2
    const val CAT_AUDIO = 3; const val CAT_3D = 5; const val CAT_UNKNOWN = 6
    const val FMT_JPG = 0; const val FMT_PNG = 1; const val FMT_WEBP = 2; const val FMT_BMP = 3
    const val MODE_FAST = 0; const val MODE_BALANCED = 1; const val MODE_ULTRA = 2
}
