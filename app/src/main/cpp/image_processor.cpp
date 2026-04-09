#include "converter_engine.h"
#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "UCEngine_Image"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── Native image quality scoring ────────────────────────────────────────────
// Returns a quality score 0.0-1.0 for compression decisions
extern "C" {

JNIEXPORT jfloat JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_computeImageComplexity(
    JNIEnv* env, jobject /* this */, jobject bitmap)
{
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return 0.5f;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return 0.5f;

    uint32_t* data = static_cast<uint32_t*>(pixels);
    uint32_t w = info.width;
    uint32_t h = info.height;

    // Measure complexity via edge detection (Sobel-like)
    double edgeSum = 0.0;
    uint64_t samples = 0;

    uint32_t stepX = std::max(1u, w / 64);
    uint32_t stepY = std::max(1u, h / 64);

    for (uint32_t y = 1; y < h - 1; y += stepY) {
        for (uint32_t x = 1; x < w - 1; x += stepX) {
            auto luminance = [&](int px, int py) -> double {
                uint32_t p = data[py * w + px];
                uint8_t r = (p >> 16) & 0xFF;
                uint8_t g = (p >> 8)  & 0xFF;
                uint8_t b =  p        & 0xFF;
                return 0.299 * r + 0.587 * g + 0.114 * b;
            };

            double gx = -luminance(x-1, y-1) - 2*luminance(x-1, y) - luminance(x-1, y+1)
                       + luminance(x+1, y-1) + 2*luminance(x+1, y) + luminance(x+1, y+1);
            double gy = -luminance(x-1, y-1) - 2*luminance(x, y-1) - luminance(x+1, y-1)
                       + luminance(x-1, y+1) + 2*luminance(x, y+1) + luminance(x+1, y+1);

            edgeSum += std::sqrt(gx*gx + gy*gy);
            ++samples;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    if (samples == 0) return 0.5f;
    // Normalize: max edge magnitude ≈ 255*4*sqrt(2) ≈ 1442
    double normalized = (edgeSum / samples) / 400.0;
    return static_cast<jfloat>(std::min(1.0, normalized));
}

// Apply simple greyscale conversion to bitmap in-place
JNIEXPORT void JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_applyGrayscale(
    JNIEnv* env, jobject /* this */, jobject bitmap)
{
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    uint32_t* data = static_cast<uint32_t*>(pixels);
    uint64_t count = static_cast<uint64_t>(info.width) * info.height;

    for (uint64_t i = 0; i < count; ++i) {
        uint32_t pixel = data[i];
        uint8_t a = (pixel >> 24) & 0xFF;
        uint8_t r = (pixel >> 16) & 0xFF;
        uint8_t g = (pixel >> 8)  & 0xFF;
        uint8_t b =  pixel        & 0xFF;
        uint8_t gray = static_cast<uint8_t>(0.299 * r + 0.587 * g + 0.114 * b);
        data[i] = (a << 24) | (gray << 16) | (gray << 8) | gray;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

// Strip pixel-level noise (very simple 3x3 box blur for EXIF removal awareness)
JNIEXPORT void JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_applyLightBlur(
    JNIEnv* env, jobject /* this */, jobject bitmap, jint radius)
{
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    uint32_t* data = static_cast<uint32_t*>(pixels);
    uint32_t w = info.width;
    uint32_t h = info.height;
    int r = std::min(static_cast<int>(radius), 3);

    std::vector<uint32_t> temp(data, data + w * h);

    for (uint32_t y = r; y < h - r; ++y) {
        for (uint32_t x = r; x < w - r; ++x) {
            int sumR = 0, sumG = 0, sumB = 0, sumA = 0;
            int cnt = 0;
            for (int dy = -r; dy <= r; ++dy) {
                for (int dx = -r; dx <= r; ++dx) {
                    uint32_t p = temp[(y + dy) * w + (x + dx)];
                    sumA += (p >> 24) & 0xFF;
                    sumR += (p >> 16) & 0xFF;
                    sumG += (p >> 8)  & 0xFF;
                    sumB +=  p        & 0xFF;
                    ++cnt;
                }
            }
            data[y * w + x] = ((sumA/cnt) << 24) | ((sumR/cnt) << 16)
                             | ((sumG/cnt) << 8)  |  (sumB/cnt);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

// Adjust brightness/contrast
JNIEXPORT void JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_adjustBrightnessContrast(
    JNIEnv* env, jobject /* this */, jobject bitmap,
    jfloat brightness, jfloat contrast)
{
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    uint32_t* data = static_cast<uint32_t*>(pixels);
    uint64_t count = static_cast<uint64_t>(info.width) * info.height;

    // Build LUT for performance
    uint8_t lut[256];
    for (int i = 0; i < 256; ++i) {
        float v = i / 255.0f;
        v = (v - 0.5f) * contrast + 0.5f + brightness;
        v = std::max(0.0f, std::min(1.0f, v));
        lut[i] = static_cast<uint8_t>(v * 255);
    }

    for (uint64_t i = 0; i < count; ++i) {
        uint32_t p = data[i];
        uint8_t a = (p >> 24) & 0xFF;
        uint8_t r = lut[(p >> 16) & 0xFF];
        uint8_t g = lut[(p >> 8)  & 0xFF];
        uint8_t b = lut[ p        & 0xFF];
        data[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

} // extern "C"
