#include "converter_engine.h"
#include <jni.h>
#include <android/log.h>
#include <fstream>
#include <vector>
#include <cstdint>

#define LOG_TAG "UCEngine_Detector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

// ─── Magic byte signatures ────────────────────────────────────────────────────
static std::string detectByMagicBytes(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f.is_open()) return "unknown";

    uint8_t magic[12] = {0};
    f.read(reinterpret_cast<char*>(magic), sizeof(magic));
    f.close();

    // JPEG: FF D8 FF
    if (magic[0] == 0xFF && magic[1] == 0xD8 && magic[2] == 0xFF)
        return "jpeg";

    // PNG: 89 50 4E 47 0D 0A 1A 0A
    if (magic[0] == 0x89 && magic[1] == 0x50 &&
        magic[2] == 0x4E && magic[3] == 0x47)
        return "png";

    // WebP: RIFF....WEBP
    if (magic[0] == 0x52 && magic[1] == 0x49 &&
        magic[2] == 0x46 && magic[3] == 0x46 &&
        magic[8] == 0x57 && magic[9] == 0x45 &&
        magic[10]== 0x42 && magic[11]== 0x50)
        return "webp";

    // PDF: %PDF
    if (magic[0] == 0x25 && magic[1] == 0x50 &&
        magic[2] == 0x44 && magic[3] == 0x46)
        return "pdf";

    // BMP: BM
    if (magic[0] == 0x42 && magic[1] == 0x4D)
        return "bmp";

    // GIF: GIF87a / GIF89a
    if (magic[0] == 0x47 && magic[1] == 0x49 && magic[2] == 0x46)
        return "gif";

    // MP4 / MOV (ftyp box at offset 4)
    if (magic[4] == 0x66 && magic[5] == 0x74 &&
        magic[6] == 0x79 && magic[7] == 0x70)
        return "mp4";

    // MKV: EBML
    if (magic[0] == 0x1A && magic[1] == 0x45 &&
        magic[2] == 0xDF && magic[3] == 0xA3)
        return "mkv";

    // MP3: ID3 or FF FB
    if ((magic[0] == 0x49 && magic[1] == 0x44 && magic[2] == 0x33) ||
        (magic[0] == 0xFF && (magic[1] & 0xE0) == 0xE0))
        return "mp3";

    // OBJ: look for 'v ' or '# ' (text-based)
    if (magic[0] == 'v' && magic[1] == ' ')
        return "obj";
    if (magic[0] == '#' && magic[1] == ' ')
        return "obj";

    return "unknown";
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_detectByMagicBytes(
    JNIEnv* env, jobject /* this */, jstring filePath)
{
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    std::string result = detectByMagicBytes(std::string(path));
    env->ReleaseStringUTFChars(filePath, path);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_getFileSize(
    JNIEnv* env, jobject /* this */, jstring filePath)
{
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    long size = f.is_open() ? static_cast<long>(f.tellg()) : -1L;
    env->ReleaseStringUTFChars(filePath, path);
    return static_cast<jlong>(size);
}

JNIEXPORT jboolean JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_fileExists(
    JNIEnv* env, jobject /* this */, jstring filePath)
{
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    std::ifstream f(path);
    bool exists = f.good();
    env->ReleaseStringUTFChars(filePath, path);
    return static_cast<jboolean>(exists);
}

} // extern "C"
