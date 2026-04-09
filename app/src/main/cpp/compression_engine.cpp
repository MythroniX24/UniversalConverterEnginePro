#include "converter_engine.h"
#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <algorithm>

#define LOG_TAG "UCEngine_Compress"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

// Estimate optimal quality setting to reach target size
JNIEXPORT jint JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_estimateOptimalQuality(
    JNIEnv* /* env */, jobject /* this */,
    jlong inputSizeBytes, jlong targetSizeBytes, jstring format)
{
    if (targetSizeBytes <= 0 || inputSizeBytes <= 0) return 85;
    double ratio = static_cast<double>(targetSizeBytes) / inputSizeBytes;
    // Non-linear mapping from size ratio to quality
    // quality = 100 * ratio^0.5 (approximate)
    int quality = static_cast<int>(100.0 * std::sqrt(ratio));
    return std::max(5, std::min(95, quality));
}

// Compute compression savings percentage
JNIEXPORT jfloat JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_computeCompressionRatio(
    JNIEnv* /* env */, jobject /* this */,
    jlong originalSize, jlong compressedSize)
{
    if (originalSize <= 0) return 0.0f;
    float ratio = 1.0f - (static_cast<float>(compressedSize) / originalSize);
    return std::max(0.0f, std::min(1.0f, ratio));
}

} // extern "C"
