// hash_engine.cpp - Perceptual hash and duplicate detection
#include <jni.h>
#include <cstring>

extern "C" {

// Hamming distance between two pHashes (lower = more similar)
JNIEXPORT jint JNICALL Java_com_universalconverter_pro_engine_NativeEngine_hammingDistanceNative(
    JNIEnv*, jobject, jlong h1, jlong h2)
{
    uint64_t diff = (uint64_t)h1 ^ (uint64_t)h2;
    int count = 0;
    while(diff) { count += diff & 1; diff >>= 1; }
    return count;
}

// Check if two images are perceptual duplicates (distance <= threshold)
JNIEXPORT jboolean JNICALL Java_com_universalconverter_pro_engine_NativeEngine_isDuplicateNative(
    JNIEnv* env, jobject thiz, jlong h1, jlong h2, jint threshold)
{
    jint dist = Java_com_universalconverter_pro_engine_NativeEngine_hammingDistanceNative(env, thiz, h1, h2);
    return (jboolean)(dist <= threshold);
}

} // extern "C"
