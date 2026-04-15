// smart_compress.cpp - AI-based compression quality optimizer
#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <cmath>
#include <algorithm>
#define LOG_TAG "SmartCompress"

extern "C" {

// Analyze image content to determine optimal compression strategy
// Returns: 0=photo, 1=graphic/text, 2=screenshot, 3=drawing
JNIEXPORT jint JNICALL Java_com_universalconverter_pro_engine_NativeEngine_classifyImageContentNative(
    JNIEnv* env, jobject, jobject bitmap)
{
    AndroidBitmapInfo info; void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return 0;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return 0;
    uint32_t* data = (uint32_t*)pixels;
    uint32_t w = info.width, h = info.height;

    // Sample pixels to analyze color diversity and edge density
    double uniqueColorRatio = 0;
    double edgeDensity = 0;
    int samples = 0;
    uint32_t stepX = std::max(1u, w/50), stepY = std::max(1u, h/50);

    std::vector<uint32_t> colors;
    colors.reserve(2500);

    for (uint32_t y = 1; y < h-1; y += stepY) {
        for (uint32_t x = 1; x < w-1; x += stepX) {
            uint32_t p = data[y*w+x];
            colors.push_back((p>>16)&0xF0 | (((p>>8)&0xF0)<<8) | ((p&0xF0)<<16)); // quantize
            // edge detection
            uint32_t right = data[y*w+x+1], down = data[(y+1)*w+x];
            int dr = ((int)((p>>16)&0xFF)) - ((int)((right>>16)&0xFF));
            int dg = ((int)((p>>8)&0xFF))  - ((int)((right>>8)&0xFF));
            int db = ((int)(p&0xFF))        - ((int)(right&0xFF));
            int dr2= ((int)((p>>16)&0xFF)) - ((int)((down>>16)&0xFF));
            int dg2= ((int)((p>>8)&0xFF))  - ((int)((down>>8)&0xFF));
            int db2= ((int)(p&0xFF))        - ((int)(down&0xFF));
            double mag = std::sqrt(dr*dr+dg*dg+db*db+dr2*dr2+dg2*dg2+db2*db2);
            if (mag > 30) edgeDensity++;
            samples++;
        }
    }

    if (samples > 0) {
        std::sort(colors.begin(), colors.end());
        colors.erase(std::unique(colors.begin(), colors.end()), colors.end());
        uniqueColorRatio = (double)colors.size() / samples;
        edgeDensity /= samples;
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    // Classify
    if (uniqueColorRatio > 0.6) return 0;       // photo - many colors
    if (edgeDensity > 0.4) return 1;             // graphic/text - sharp edges
    if (uniqueColorRatio < 0.1) return 3;        // drawing - few colors
    return 2;                                     // screenshot
}

// Get recommended quality for each content type
JNIEXPORT jint JNICALL Java_com_universalconverter_pro_engine_NativeEngine_getRecommendedQualityNative(
    JNIEnv*, jobject, jint contentType, jint mode)
{
    // mode: 0=fast, 1=balanced, 2=ultra
    int base;
    switch(contentType) {
        case 0: base = 78; break; // photo
        case 1: base = 88; break; // graphic - preserve sharpness
        case 2: base = 82; break; // screenshot
        case 3: base = 85; break; // drawing
        default: base = 80;
    }
    switch(mode) {
        case 0: return std::max(55, base - 15); // fast = smaller
        case 2: return std::min(95, base + 8);  // ultra = best quality
        default: return base;                    // balanced
    }
}

// Compute optimal format recommendation
// Returns: 0=jpg, 1=png, 2=webp, 3=bmp
JNIEXPORT jint JNICALL Java_com_universalconverter_pro_engine_NativeEngine_recommendFormatNative(
    JNIEnv*, jobject, jint contentType, jboolean needTransparency)
{
    if (needTransparency) return 2; // webp with alpha
    switch(contentType) {
        case 0: return 2;  // photo -> webp (best compression)
        case 1: return 1;  // graphic/text -> png (lossless)
        case 2: return 0;  // screenshot -> jpg
        case 3: return 1;  // drawing -> png
        default: return 2;
    }
}

} // extern "C"
