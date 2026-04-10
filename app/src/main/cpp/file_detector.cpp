// file_detector.cpp
// All JNI bridges are in converter_engine.cpp
// This file contains only magic-byte detection helpers (no JNI exports)
#include "converter_engine.h"
#include <jni.h>
#include <android/log.h>
#include <fstream>
#include <vector>
#include <cstdint>

#define LOG_TAG "UCEngine_Detector"

// Magic-byte detection — internal helper, called from converter_engine.cpp
static std::string detectByMagicBytesInternal(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f.is_open()) return "unknown";
    uint8_t magic[12] = {0};
    f.read(reinterpret_cast<char*>(magic), sizeof(magic));
    f.close();

    if (magic[0]==0xFF && magic[1]==0xD8 && magic[2]==0xFF) return "jpeg";
    if (magic[0]==0x89 && magic[1]==0x50 && magic[2]==0x4E && magic[3]==0x47) return "png";
    if (magic[0]==0x52 && magic[1]==0x49 && magic[2]==0x46 && magic[3]==0x46 &&
        magic[8]==0x57 && magic[9]==0x45 && magic[10]==0x42 && magic[11]==0x50) return "webp";
    if (magic[0]==0x25 && magic[1]==0x50 && magic[2]==0x44 && magic[3]==0x46) return "pdf";
    if (magic[0]==0x42 && magic[1]==0x4D) return "bmp";
    if (magic[0]==0x47 && magic[1]==0x49 && magic[2]==0x46) return "gif";
    if (magic[4]==0x66 && magic[5]==0x74 && magic[6]==0x79 && magic[7]==0x70) return "mp4";
    if (magic[0]==0x1A && magic[1]==0x45 && magic[2]==0xDF && magic[3]==0xA3) return "mkv";
    if ((magic[0]==0x49 && magic[1]==0x44 && magic[2]==0x33) ||
        (magic[0]==0xFF && (magic[1]&0xE0)==0xE0)) return "mp3";
    return "unknown";
}
// No JNI exports — all moved to converter_engine.cpp
