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
#include <fstream>

#define LOG_TAG "UCEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── ConversionMatrix: define vectors here (NOT in header) ───────────────────
namespace ConversionMatrix {
    const std::vector<std::string> IMAGE_FORMATS   = { "jpg","jpeg","png","webp","bmp","tiff","gif" };
    const std::vector<std::string> DOC_FORMATS     = { "pdf" };
    const std::vector<std::string> AUDIO_FORMATS   = { "mp3","aac","wav","ogg","flac","m4a" };
    const std::vector<std::string> VIDEO_FORMATS   = { "mp4","mkv","avi","mov","webm","3gp" };
    const std::vector<std::string> THREE_D_FORMATS = { "obj","fbx","stl" };

    static bool inList(const std::vector<std::string>& list, const std::string& ext) {
        std::string lower = ext;
        std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
        for (const auto& f : list) if (f == lower) return true;
        return false;
    }

    bool isImageFormat(const std::string& ext)  { return inList(IMAGE_FORMATS,   ext); }
    bool isDocFormat(const std::string& ext)     { return inList(DOC_FORMATS,     ext); }
    bool isAudioFormat(const std::string& ext)   { return inList(AUDIO_FORMATS,   ext); }
    bool isVideoFormat(const std::string& ext)   { return inList(VIDEO_FORMATS,   ext); }
    bool isThreeDFormat(const std::string& ext)  { return inList(THREE_D_FORMATS, ext); }
}

// ─── ConverterEngine ─────────────────────────────────────────────────────────
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
    if (ext=="jpg"||ext=="jpeg") return "image/jpeg";
    if (ext=="png")  return "image/png";
    if (ext=="webp") return "image/webp";
    if (ext=="bmp")  return "image/bmp";
    if (ext=="gif")  return "image/gif";
    if (ext=="tiff") return "image/tiff";
    if (ext=="pdf")  return "application/pdf";
    if (ext=="mp4")  return "video/mp4";
    if (ext=="mkv")  return "video/x-matroska";
    if (ext=="avi")  return "video/x-msvideo";
    if (ext=="mov")  return "video/quicktime";
    if (ext=="mp3")  return "audio/mpeg";
    if (ext=="aac")  return "audio/aac";
    if (ext=="wav")  return "audio/wav";
    if (ext=="ogg")  return "audio/ogg";
    if (ext=="flac") return "audio/flac";
    if (ext=="obj")  return "model/obj";
    if (ext=="fbx")  return "model/fbx";
    if (ext=="stl")  return "model/stl";
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
    if (fromExt == toExt) return false;
    std::string from = fromExt, to = toExt;
    std::transform(from.begin(), from.end(), from.begin(), ::tolower);
    std::transform(to.begin(),   to.end(),   to.begin(),   ::tolower);

    bool fromImage = ConversionMatrix::isImageFormat(from);
    bool fromDoc   = ConversionMatrix::isDocFormat(from);
    bool fromVideo = ConversionMatrix::isVideoFormat(from);
    bool fromAudio = ConversionMatrix::isAudioFormat(from);
    bool from3D    = ConversionMatrix::isThreeDFormat(from);

    bool toImage   = ConversionMatrix::isImageFormat(to);
    bool toDoc     = ConversionMatrix::isDocFormat(to);
    bool toVideo   = ConversionMatrix::isVideoFormat(to);
    bool toAudio   = ConversionMatrix::isAudioFormat(to);
    bool to3D      = ConversionMatrix::isThreeDFormat(to);

    if (fromImage  && toImage)  return true;
    if (fromImage  && toDoc)    return true;
    if (fromDoc    && toImage)  return true;
    if (fromVideo  && toVideo)  return true;
    if (fromVideo  && toAudio)  return true;
    if (fromAudio  && toAudio)  return true;
    if (from3D     && to3D)     return true;
    return false;
}

std::vector<std::string> ConverterEngine::getValidOutputFormats(const std::string& inputPath) {
    std::string ext = detectExtension(inputPath);
    std::vector<std::string> valid;

    if (ConversionMatrix::isImageFormat(ext)) {
        for (const auto& f : ConversionMatrix::IMAGE_FORMATS)
            if (f != ext && f != "jpeg") valid.push_back(f);
        valid.push_back("pdf");
    } else if (ConversionMatrix::isDocFormat(ext)) {
        valid = {"jpg","png","webp","bmp"};
    } else if (ConversionMatrix::isVideoFormat(ext)) {
        for (const auto& f : ConversionMatrix::VIDEO_FORMATS)  if (f != ext) valid.push_back(f);
        for (const auto& f : ConversionMatrix::AUDIO_FORMATS)  valid.push_back(f);
    } else if (ConversionMatrix::isAudioFormat(ext)) {
        for (const auto& f : ConversionMatrix::AUDIO_FORMATS)  if (f != ext) valid.push_back(f);
    } else if (ConversionMatrix::isThreeDFormat(ext)) {
        for (const auto& f : ConversionMatrix::THREE_D_FORMATS) if (f != ext) valid.push_back(f);
    }
    return valid;
}

std::string ConverterEngine::suggestBestFormat(const std::string& inputPath) {
    std::string ext = detectExtension(inputPath);
    if (ConversionMatrix::isImageFormat(ext))   return "webp";
    if (ConversionMatrix::isVideoFormat(ext))   return "mp4";
    if (ConversionMatrix::isAudioFormat(ext))   return "aac";
    if (ConversionMatrix::isThreeDFormat(ext))  return "obj";
    return ext;
}

long ConverterEngine::predictOutputSize(const std::string& inputPath,
                                         const std::string& targetFormat,
                                         int quality) {
    std::ifstream file(inputPath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) return -1;
    long inputSize = static_cast<long>(file.tellg());
    file.close();

    double ratio = 1.0;
    if (targetFormat == "webp")                   ratio = 0.65;
    else if (targetFormat=="jpg"||targetFormat=="jpeg")
        ratio = 0.3 + (0.7 * quality / 100.0);
    else if (targetFormat == "png")               ratio = 0.9;
    else if (targetFormat == "bmp")               ratio = 3.0;
    else                                          ratio = 0.8;

    double qualityFactor = 0.5 + (0.5 * quality / 100.0);
    return static_cast<long>(inputSize * ratio * qualityFactor);
}

void ConverterEngine::cancelOperation() {
    cancelled_.store(true);
}

std::vector<uint8_t> ConverterEngine::resizeBitmap(
    const std::vector<uint8_t>& src, int srcW, int srcH, int channels,
    int dstW, int dstH)
{
    std::vector<uint8_t> dst(dstW * dstH * channels);
    float xScale = static_cast<float>(srcW) / dstW;
    float yScale = static_cast<float>(srcH) / dstH;
    for (int y = 0; y < dstH; ++y) {
        for (int x = 0; x < dstW; ++x) {
            int x0 = static_cast<int>(x * xScale);
            int y0 = static_cast<int>(y * yScale);
            x0 = std::min(x0, srcW - 1);
            y0 = std::min(y0, srcH - 1);
            for (int c = 0; c < channels; ++c)
                dst[(y*dstW+x)*channels+c] = src[(y0*srcW+x0)*channels+c];
        }
    }
    return dst;
}

std::vector<uint8_t> ConverterEngine::rotateBitmap(
    const std::vector<uint8_t>& pixels, int w, int h, int channels, int degrees)
{
    if (degrees == 0 || degrees == 360) return pixels;
    std::vector<uint8_t> rotated(w * h * channels);
    if (degrees == 180) {
        for (int y = 0; y < h; ++y)
            for (int x = 0; x < w; ++x)
                for (int c = 0; c < channels; ++c)
                    rotated[((h-1-y)*w+(w-1-x))*channels+c] =
                        pixels[(y*w+x)*channels+c];
    }
    return rotated;
}

void ConverterEngine::cleanupTempFiles(const std::string& tempDir) {
    LOGI("Cleanup: %s", tempDir.c_str());
}

// ─── JNI Bridge ──────────────────────────────────────────────────────────────
extern "C" {

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_getEngineVersion(
    JNIEnv* env, jobject)
{ return env->NewStringUTF("1.0.0-NDK"); }

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_detectMimeType(
    JNIEnv* env, jobject, jstring filePath)
{
    const char* p = env->GetStringUTFChars(filePath, nullptr);
    std::string r = ConverterEngine::getInstance().detectMimeType(p);
    env->ReleaseStringUTFChars(filePath, p);
    return env->NewStringUTF(r.c_str());
}

JNIEXPORT jint JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_detectCategory(
    JNIEnv* env, jobject, jstring filePath)
{
    const char* p = env->GetStringUTFChars(filePath, nullptr);
    FormatCategory c = ConverterEngine::getInstance().detectCategory(p);
    env->ReleaseStringUTFChars(filePath, p);
    return static_cast<jint>(c);
}

JNIEXPORT jboolean JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_isValidConversion(
    JNIEnv* env, jobject, jstring fromExt, jstring toExt)
{
    const char* from = env->GetStringUTFChars(fromExt, nullptr);
    const char* to   = env->GetStringUTFChars(toExt,   nullptr);
    bool v = ConverterEngine::getInstance().isValidConversion(from, to);
    env->ReleaseStringUTFChars(fromExt, from);
    env->ReleaseStringUTFChars(toExt,   to);
    return static_cast<jboolean>(v);
}

JNIEXPORT jobjectArray JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_getValidOutputFormats(
    JNIEnv* env, jobject, jstring inputPath)
{
    const char* p = env->GetStringUTFChars(inputPath, nullptr);
    auto fmts = ConverterEngine::getInstance().getValidOutputFormats(p);
    env->ReleaseStringUTFChars(inputPath, p);
    jclass sc = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(fmts.size()), sc, nullptr);
    for (int i = 0; i < (int)fmts.size(); ++i)
        env->SetObjectArrayElement(arr, i, env->NewStringUTF(fmts[i].c_str()));
    return arr;
}

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_suggestBestFormat(
    JNIEnv* env, jobject, jstring inputPath)
{
    const char* p = env->GetStringUTFChars(inputPath, nullptr);
    std::string r = ConverterEngine::getInstance().suggestBestFormat(p);
    env->ReleaseStringUTFChars(inputPath, p);
    return env->NewStringUTF(r.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_predictOutputSize(
    JNIEnv* env, jobject, jstring inputPath, jstring targetFmt, jint quality)
{
    const char* p = env->GetStringUTFChars(inputPath,  nullptr);
    const char* f = env->GetStringUTFChars(targetFmt, nullptr);
    long sz = ConverterEngine::getInstance().predictOutputSize(p, f, (int)quality);
    env->ReleaseStringUTFChars(inputPath,  p);
    env->ReleaseStringUTFChars(targetFmt, f);
    return static_cast<jlong>(sz);
}

JNIEXPORT void JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_cancelOperation(
    JNIEnv*, jobject)
{ ConverterEngine::getInstance().cancelOperation(); }

JNIEXPORT jboolean JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_resizeBitmapNative(
    JNIEnv* env, jobject, jobject srcBmp, jobject dstBmp)
{
    AndroidBitmapInfo si, di;
    void *sp = nullptr, *dp = nullptr;
    if (AndroidBitmap_getInfo(env, srcBmp, &si) < 0) return JNI_FALSE;
    if (AndroidBitmap_getInfo(env, dstBmp, &di) < 0) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, srcBmp, &sp) < 0) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, dstBmp, &dp) < 0) {
        AndroidBitmap_unlockPixels(env, srcBmp); return JNI_FALSE;
    }
    uint32_t* src = static_cast<uint32_t*>(sp);
    uint32_t* dst = static_cast<uint32_t*>(dp);
    float xs = (float)si.width / di.width, ys = (float)si.height / di.height;
    for (uint32_t y = 0; y < di.height; ++y)
        for (uint32_t x = 0; x < di.width; ++x) {
            uint32_t sx = std::min((uint32_t)(x*xs), si.width-1);
            uint32_t sy = std::min((uint32_t)(y*ys), si.height-1);
            dst[y*di.width+x] = src[sy*si.width+sx];
        }
    AndroidBitmap_unlockPixels(env, srcBmp);
    AndroidBitmap_unlockPixels(env, dstBmp);
    return JNI_TRUE;
}

JNIEXPORT jfloat JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_computeBrightness(
    JNIEnv* env, jobject, jobject bitmap)
{
    AndroidBitmapInfo info; void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return -1.0f;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return -1.0f;
    uint32_t* data = static_cast<uint32_t*>(pixels);
    double total = 0.0;
    uint64_t count = (uint64_t)info.width * info.height;
    for (uint64_t i = 0; i < count; ++i) {
        uint32_t p = data[i];
        total += 0.299*((p>>16)&0xFF) + 0.587*((p>>8)&0xFF) + 0.114*(p&0xFF);
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return (float)(total / count / 255.0);
}

} // extern "C"
