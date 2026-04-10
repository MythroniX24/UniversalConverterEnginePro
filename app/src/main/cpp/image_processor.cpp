// image_processor.cpp
// All JNI bridges are in converter_engine.cpp
// This file contains only native image processing helpers (no JNI exports)
#include "converter_engine.h"
#include <android/log.h>

#define LOG_TAG "UCEngine_Image"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
// No JNI exports here — all moved to converter_engine.cpp
