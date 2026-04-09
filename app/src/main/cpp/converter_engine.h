#pragma once

#include <jni.h>
#include <string>
#include <vector>
#include <functional>
#include <atomic>
#include <memory>

// ─── Format Categories ────────────────────────────────────────────────────────
enum class FormatCategory {
    IMAGE,
    DOCUMENT,
    MEDIA_VIDEO,
    MEDIA_AUDIO,
    ARCHIVE,
    THREE_D,
    UNKNOWN
};

// ─── Conversion Result ────────────────────────────────────────────────────────
struct ConversionResult {
    bool        success;
    std::string outputPath;
    std::string errorMessage;
    long        inputSizeBytes;
    long        outputSizeBytes;
    double      compressionRatio;
    long        processingTimeMs;
};

// ─── Conversion Options ───────────────────────────────────────────────────────
struct ConversionOptions {
    int  quality;           // 0-100
    int  width;             // 0 = keep original
    int  height;            // 0 = keep original
    bool keepAspectRatio;
    bool removeExif;
    bool stripMetadata;
    int  rotation;          // 0, 90, 180, 270
    long targetSizeBytes;   // 0 = no target
};

// ─── Progress Callback ────────────────────────────────────────────────────────
using ProgressCallback = std::function<void(int progress, const std::string& status)>;

// ─── Main Engine ─────────────────────────────────────────────────────────────
class ConverterEngine {
public:
    static ConverterEngine& getInstance();

    FormatCategory detectCategory(const std::string& filePath);
    std::string    detectMimeType(const std::string& filePath);
    std::string    detectExtension(const std::string& filePath);
    bool           isValidConversion(const std::string& fromExt, const std::string& toExt);

    ConversionResult convertImage(
        const std::string& inputPath,
        const std::string& outputPath,
        const std::string& targetFormat,
        const ConversionOptions& options,
        const ProgressCallback& progress
    );

    ConversionResult compressImage(
        const std::string& inputPath,
        const std::string& outputPath,
        int quality,
        long targetSizeBytes,
        const ProgressCallback& progress
    );

    long        predictOutputSize(const std::string& inputPath,
                                  const std::string& targetFormat, int quality);
    std::string suggestBestFormat(const std::string& inputPath);
    std::vector<std::string> getValidOutputFormats(const std::string& inputPath);

    void cancelOperation();
    bool isCancelled() const { return cancelled_.load(); }
    void cleanupTempFiles(const std::string& tempDir);

private:
    ConverterEngine()  = default;
    ~ConverterEngine() = default;
    ConverterEngine(const ConverterEngine&)            = delete;
    ConverterEngine& operator=(const ConverterEngine&) = delete;

    std::atomic<bool> cancelled_{false};

    std::vector<uint8_t> resizeBitmap(const std::vector<uint8_t>& src,
                                      int srcW, int srcH, int channels,
                                      int dstW, int dstH);
    std::vector<uint8_t> rotateBitmap(const std::vector<uint8_t>& pixels,
                                      int w, int h, int channels, int degrees);
};

// ─── Valid Conversion Matrix ──────────────────────────────────────────────────
// Declarations only — definitions are in converter_engine.cpp
namespace ConversionMatrix {
    // Format lists — defined in converter_engine.cpp (not in header to avoid ODR)
    extern const std::vector<std::string> IMAGE_FORMATS;
    extern const std::vector<std::string> DOC_FORMATS;
    extern const std::vector<std::string> AUDIO_FORMATS;
    extern const std::vector<std::string> VIDEO_FORMATS;
    extern const std::vector<std::string> THREE_D_FORMATS;

    bool isImageFormat(const std::string& ext);
    bool isDocFormat(const std::string& ext);
    bool isAudioFormat(const std::string& ext);
    bool isVideoFormat(const std::string& ext);
    bool isThreeDFormat(const std::string& ext);
}
