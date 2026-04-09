#include "converter_engine.h"
#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <string>
#include <vector>
#include <algorithm>
#include <cstring>
#include <cmath>
#include <chrono>
#include <sstream>
#include <fstream>

#define LOG_TAG "UCEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ─── Conversion Matrix Implementation ────────────────────────────────────────
namespace ConversionMatrix {
    bool isImageFormat(const std::string& ext) {
        std::string lower = ext;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        for (const auto& f : IMAGE_FORMATS) {
            if (f == lower) return true;
        }
        return false;
    }
    bool isDocFormat(const std::string& ext) {
        std::string lower = ext;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        for (const auto& f : DOC_FORMATS) {
            if (f == lower) return true;
        }
        return false;
    }
    bool isAudioFormat(const std::string& ext) {
        std::string lower = ext;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        for (const auto& f : AUDIO_FORMATS) {
            if (f == lower) return true;
        }
        return false;
    }
    bool isVideoFormat(const std::string& ext) {
        std::string lower = ext;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        for (const auto& f : VIDEO_FORMATS) {
            if (f == lower) return true;
        }
        return false;
    }
    bool isThreeDFormat(const std::string& ext) {
        std::string lower = ext;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        for (const auto& f : THREE_D_FORMATS) {
            if (f == lower) return true;
        }
        return false;
    }
}

// ─── ConverterEngine Implementation ──────────────────────────────────────────
ConverterEngine& ConverterEngine::getInstance() {
    static ConverterEngine instance;
    return instance;
}

std::string ConverterEngine::detectExtension(const std::string& filePath) {
    size_t dotPos = filePath.rfind('.');
    if (dotPos == std::string::npos) return "";
    std::string ext = filePath.substr(dotPos + 1);
    std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
    return ext;
}

std::string ConverterEngine::detectMimeType(const std::string& filePath) {
    std::string ext = detectExtension(filePath);
    if (ext == "jpg" || ext == "jpeg") return "image/jpeg";
    if (ext == "png")  return "image/png";
    if (ext == "webp") return "image/webp";
    if (ext == "bmp")  return "image/bmp";
    if (ext == "gif")  return "image/gif";
    if (ext == "tiff") return "image/tiff";
    if (ext == "pdf")  return "application/pdf";
    if (ext == "mp4")  return "video/mp4";
    if (ext == "mkv")  return "video/x-matroska";
    if (ext == "avi")  return "video/x-msvideo";
    if (ext == "mov")  return "video/quicktime";
    if (ext == "mp3")  return "audio/mpeg";
    if (ext == "aac")  return "audio/aac";
    if (ext == "wav")  return "audio/wav";
    if (ext == "ogg")  return "audio/ogg";
    if (ext == "flac") return "audio/flac";
    if (ext == "obj")  return "model/obj";
    if (ext == "fbx")  return "model/fbx";
    if (ext == "stl")  return "model/stl";
    return "application/octet-stream";
}

FormatCategory ConverterEngine::detectCategory(const std::string& filePath) {
    std::string ext = detectExtension(filePath);
    if (ConversionMatrix::isImageFormat(ext))  return FormatCategory::IMAGE;
    if (ConversionMatrix::isDocFormat(ext))    return FormatCategory::DOCUMENT;
    if (ConversionMatrix::isVideoFormat(ext))  return FormatCategory::MEDIA_VIDEO;
    if (ConversionMatrix::isAudioFormat(ext))  return FormatCategory::MEDIA_AUDIO;
    if (ConversionMatrix::isThreeDFormat(ext)) return FormatCategory::THREE_D;
    return FormatCategory::UNKNOWN;
}

bool ConverterEngine::isValidConversion(const std::string& fromExt, const std::string& toExt) {
    if (fromExt == toExt) return false; // Same format — no conversion needed

    bool fromImage  = ConversionMatrix::isImageFormat(fromExt);
    bool fromDoc    = ConversionMatrix::isDocFormat(fromExt);
    bool fromVideo  = ConversionMatrix::isVideoFormat(fromExt);
    bool fromAudio  = ConversionMatrix::isAudioFormat(fromExt);
    bool from3D     = ConversionMatrix::isThreeDFormat(fromExt);

    bool toImage    = ConversionMatrix::isImageFormat(toExt);
    bool toDoc      = ConversionMatrix::isDocFormat(toExt);
    bool toVideo    = ConversionMatrix::isVideoFormat(toExt);
    bool toAudio    = ConversionMatrix::isAudioFormat(toExt);
    bool to3D       = ConversionMatrix::isThreeDFormat(toExt);

    // Image → Image: always valid
    if (fromImage && toImage) return true;

    // Image → PDF: valid
    if (fromImage && toDoc) return true;

    // PDF → Image: valid
    if (fromDoc && toImage) return true;

    // Video → Video: valid
    if (fromVideo && toVideo) return true;

    // Video → Audio: extract audio
    if (fromVideo && toAudio) return true;

    // Audio → Audio: valid
    if (fromAudio && toAudio) return true;

    // 3D → 3D: valid (basic)
    if (from3D && to3D) return true;

    // All other combinations: INVALID
    return false;
}

std::vector<std::string> ConverterEngine::getValidOutputFormats(const std::string& inputPath) {
    std::string ext = detectExtension(inputPath);
    std::vector<std::string> validFormats;

    bool isImage  = ConversionMatrix::isImageFormat(ext);
    bool isDoc    = ConversionMatrix::isDocFormat(ext);
    bool isVideo  = ConversionMatrix::isVideoFormat(ext);
    bool isAudio  = ConversionMatrix::isAudioFormat(ext);
    bool is3D     = ConversionMatrix::isThreeDFormat(ext);

    if (isImage) {
        for (const auto& f : ConversionMatrix::IMAGE_FORMATS) {
            if (f != ext && f != "jpeg") validFormats.push_back(f);
        }
        validFormats.push_back("pdf");
    }
    if (isDoc) {
        for (const auto& f : ConversionMatrix::IMAGE_FORMATS) {
            if (f != "gif" && f != "tiff") validFormats.push_back(f);
        }
    }
    if (isVideo) {
        for (const auto& f : ConversionMatrix::VIDEO_FORMATS) {
            if (f != ext) validFormats.push_back(f);
        }
        for (const auto& f : ConversionMatrix::AUDIO_FORMATS) {
            validFormats.push_back(f);
        }
    }
    if (isAudio) {
        for (const auto& f : ConversionMatrix::AUDIO_FORMATS) {
            if (f != ext) validFormats.push_back(f);
        }
    }
    if (is3D) {
        for (const auto& f : ConversionMatrix::THREE_D_FORMATS) {
            if (f != ext) validFormats.push_back(f);
        }
    }
    return validFormats;
}

std::string ConverterEngine::suggestBestFormat(const std::string& inputPath) {
    std::string ext = detectExtension(inputPath);
    if (ConversionMatrix::isImageFormat(ext)) return "webp"; // Best compression/quality ratio
    if (ConversionMatrix::isVideoFormat(ext)) return "mp4";  // Widest compatibility
    if (ConversionMatrix::isAudioFormat(ext)) return "aac";  // Good compression
    return ext;
}

long ConverterEngine::predictOutputSize(const std::string& inputPath,
                                        const std::string& targetFormat,
                                        int quality) {
    std::ifstream file(inputPath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) return -1;
    long inputSize = file.tellg();
    file.close();

    double ratio = 1.0;
    std::string ext = detectExtension(inputPath);

    // Estimate based on format characteristics
    if (targetFormat == "webp") ratio = 0.65;
    else if (targetFormat == "jpg" || targetFormat == "jpeg") {
        ratio = 0.3 + (0.7 * quality / 100.0);
    }
    else if (targetFormat == "png") ratio = 0.9;
    else if (targetFormat == "bmp") ratio = 3.0;
    else ratio = 0.8;

    // Apply quality factor
    double qualityFactor = 0.5 + (0.5 * quality / 100.0);
    return static_cast<long>(inputSize * ratio * qualityFactor);
}

void ConverterEngine::cancelOperation() {
    cancelled_.store(true);
}

// ─── Simple pixel resize (bilinear) ─────────────────────────────────────────
std::vector<uint8_t> ConverterEngine::resizeBitmap(
    const std::vector<uint8_t>& src,
    int srcW, int srcH, int channels,
    int dstW, int dstH)
{
    std::vector<uint8_t> dst(dstW * dstH * channels);
    float xScale = static_cast<float>(srcW) / dstW;
    float yScale = static_cast<float>(srcH) / dstH;

    for (int y = 0; y < dstH; ++y) {
        for (int x = 0; x < dstW; ++x) {
            float srcX = x * xScale;
            float srcY = y * yScale;
            int x0 = static_cast<int>(srcX);
            int y0 = static_cast<int>(srcY);
            int x1 = std::min(x0 + 1, srcW - 1);
            int y1 = std::min(y0 + 1, srcH - 1);
            float dx = srcX - x0;
            float dy = srcY - y0;

            for (int c = 0; c < channels; ++c) {
                float p00 = src[(y0 * srcW + x0) * channels + c];
                float p01 = src[(y0 * srcW + x1) * channels + c];
                float p10 = src[(y1 * srcW + x0) * channels + c];
                float p11 = src[(y1 * srcW + x1) * channels + c];
                float val = p00*(1-dx)*(1-dy) + p01*dx*(1-dy)
                          + p10*(1-dx)*dy     + p11*dx*dy;
                dst[(y * dstW + x) * channels + c] =
                    static_cast<uint8_t>(std::min(255.0f, std::max(0.0f, val)));
            }
        }
    }
    return dst;
}

// ─── Rotate bitmap ───────────────────────────────────────────────────────────
std::vector<uint8_t> ConverterEngine::rotateBitmap(
    const std::vector<uint8_t>& pixels,
    int w, int h, int channels, int degrees)
{
    if (degrees == 0 || degrees == 360) return pixels;
    std::vector<uint8_t> rotated;

    if (degrees == 90) {
        rotated.resize(w * h * channels);
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                int newX = h - 1 - y;
                int newY = x;
                for (int c = 0; c < channels; ++c) {
                    rotated[(newY * h + newX) * channels + c] =
                        pixels[(y * w + x) * channels + c];
                }
            }
        }
    } else if (degrees == 180) {
        rotated.resize(w * h * channels);
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                for (int c = 0; c < channels; ++c) {
                    rotated[((h-1-y) * w + (w-1-x)) * channels + c] =
                        pixels[(y * w + x) * channels + c];
                }
            }
        }
    } else if (degrees == 270) {
        rotated.resize(w * h * channels);
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                int newX = y;
                int newY = w - 1 - x;
                for (int c = 0; c < channels; ++c) {
                    rotated[(newY * h + newX) * channels + c] =
                        pixels[(y * w + x) * channels + c];
                }
            }
        }
    }
    return rotated;
}

void ConverterEngine::cleanupTempFiles(const std::string& tempDir) {
    LOGI("Cleanup temp files in: %s", tempDir.c_str());
    // Native temp file cleanup — actual file deletion done in Java/Kotlin layer
}

// ─── JNI Bridge ──────────────────────────────────────────────────────────────
extern "C" {

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_detectMimeType(
    JNIEnv* env, jobject /* this */, jstring filePath)
{
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    std::string mime = ConverterEngine::getInstance().detectMimeType(std::string(path));
    env->ReleaseStringUTFChars(filePath, path);
    return env->NewStringUTF(mime.c_str());
}

JNIEXPORT jint JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_detectCategory(
    JNIEnv* env, jobject /* this */, jstring filePath)
{
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    FormatCategory cat = ConverterEngine::getInstance().detectCategory(std::string(path));
    env->ReleaseStringUTFChars(filePath, path);
    return static_cast<jint>(cat);
}

JNIEXPORT jboolean JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_isValidConversion(
    JNIEnv* env, jobject /* this */, jstring fromExt, jstring toExt)
{
    const char* from = env->GetStringUTFChars(fromExt, nullptr);
    const char* to   = env->GetStringUTFChars(toExt, nullptr);
    bool valid = ConverterEngine::getInstance().isValidConversion(
        std::string(from), std::string(to));
    env->ReleaseStringUTFChars(fromExt, from);
    env->ReleaseStringUTFChars(toExt, to);
    return static_cast<jboolean>(valid);
}

JNIEXPORT jobjectArray JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_getValidOutputFormats(
    JNIEnv* env, jobject /* this */, jstring inputPath)
{
    const char* path = env->GetStringUTFChars(inputPath, nullptr);
    std::vector<std::string> formats =
        ConverterEngine::getInstance().getValidOutputFormats(std::string(path));
    env->ReleaseStringUTFChars(inputPath, path);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(
        static_cast<jsize>(formats.size()), stringClass, nullptr);
    for (int i = 0; i < static_cast<int>(formats.size()); ++i) {
        env->SetObjectArrayElement(result, i,
            env->NewStringUTF(formats[i].c_str()));
    }
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_suggestBestFormat(
    JNIEnv* env, jobject /* this */, jstring inputPath)
{
    const char* path = env->GetStringUTFChars(inputPath, nullptr);
    std::string best = ConverterEngine::getInstance().suggestBestFormat(std::string(path));
    env->ReleaseStringUTFChars(inputPath, path);
    return env->NewStringUTF(best.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_predictOutputSize(
    JNIEnv* env, jobject /* this */,
    jstring inputPath, jstring targetFormat, jint quality)
{
    const char* path   = env->GetStringUTFChars(inputPath, nullptr);
    const char* format = env->GetStringUTFChars(targetFormat, nullptr);
    long size = ConverterEngine::getInstance().predictOutputSize(
        std::string(path), std::string(format), static_cast<int>(quality));
    env->ReleaseStringUTFChars(inputPath, path);
    env->ReleaseStringUTFChars(targetFormat, format);
    return static_cast<jlong>(size);
}

JNIEXPORT void JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_cancelOperation(
    JNIEnv* /* env */, jobject /* this */)
{
    ConverterEngine::getInstance().cancelOperation();
}

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_getEngineVersion(
    JNIEnv* env, jobject /* this */)
{
    return env->NewStringUTF("1.0.0-NDK");
}

// Bitmap resizing via JNI (Android Bitmap API)
JNIEXPORT jboolean JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_resizeBitmapNative(
    JNIEnv* env, jobject /* this */,
    jobject srcBitmap, jobject dstBitmap)
{
    AndroidBitmapInfo srcInfo, dstInfo;
    void* srcPixels = nullptr;
    void* dstPixels = nullptr;

    if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) < 0) return JNI_FALSE;
    if (AndroidBitmap_getInfo(env, dstBitmap, &dstInfo) < 0) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) < 0) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, dstBitmap, &dstPixels) < 0) {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        return JNI_FALSE;
    }

    uint32_t* src = static_cast<uint32_t*>(srcPixels);
    uint32_t* dst = static_cast<uint32_t*>(dstPixels);
    float xScale = static_cast<float>(srcInfo.width)  / dstInfo.width;
    float yScale = static_cast<float>(srcInfo.height) / dstInfo.height;

    for (uint32_t y = 0; y < dstInfo.height; ++y) {
        for (uint32_t x = 0; x < dstInfo.width; ++x) {
            uint32_t srcX = static_cast<uint32_t>(x * xScale);
            uint32_t srcY = static_cast<uint32_t>(y * yScale);
            srcX = std::min(srcX, srcInfo.width  - 1);
            srcY = std::min(srcY, srcInfo.height - 1);
            dst[y * dstInfo.width + x] = src[srcY * srcInfo.width + srcX];
        }
    }

    AndroidBitmap_unlockPixels(env, srcBitmap);
    AndroidBitmap_unlockPixels(env, dstBitmap);
    return JNI_TRUE;
}

// Compute image statistics (mean brightness, etc.)
JNIEXPORT jfloat JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_computeBrightness(
    JNIEnv* env, jobject /* this */, jobject bitmap)
{
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return -1.0f;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return -1.0f;

    uint32_t* data = static_cast<uint32_t*>(pixels);
    double total = 0.0;
    uint64_t count = static_cast<uint64_t>(info.width) * info.height;

    for (uint64_t i = 0; i < count; ++i) {
        uint32_t pixel = data[i];
        uint8_t r = (pixel >> 16) & 0xFF;
        uint8_t g = (pixel >> 8)  & 0xFF;
        uint8_t b =  pixel        & 0xFF;
        total += (0.299 * r + 0.587 * g + 0.114 * b);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return static_cast<jfloat>(total / count / 255.0);
}

} // extern "C"
