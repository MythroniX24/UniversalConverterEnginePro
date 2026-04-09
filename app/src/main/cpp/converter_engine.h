#pragma once

#include <jni.h>
#include <string>
#include <vector>
#include <functional>
#include <atomic>
#include <memory>

// ─── Format Categories ──────────────────────────────────────────────────────
enum class FormatCategory {
    IMAGE,
    DOCUMENT,
    MEDIA_VIDEO,
    MEDIA_AUDIO,
    ARCHIVE,
    THREE_D,
    UNKNOWN
};

// ─── Conversion Result ───────────────────────────────────────────────────────
struct ConversionResult {
    bool success;
    std::string outputPath;
    std::string errorMessage;
    long inputSizeBytes;
    long outputSizeBytes;
    double compressionRatio;
    long processingTimeMs;
};

// ─── Conversion Options ──────────────────────────────────────────────────────
struct ConversionOptions {
    int quality;          // 0-100
    int width;            // 0 = keep original
    int height;           // 0 = keep original
    bool keepAspectRatio;
    bool removeExif;
    bool stripMetadata;
    int rotation;         // 0, 90, 180, 270
    long targetSizeBytes; // 0 = no target
};

// ─── Progress Callback ───────────────────────────────────────────────────────
using ProgressCallback = std::function<void(int progress, const std::string& status)>;

// ─── Main Engine Interface ───────────────────────────────────────────────────
class ConverterEngine {
public:
    static ConverterEngine& getInstance();

    // File detection
    FormatCategory detectCategory(const std::string& filePath);
    std::string detectMimeType(const std::string& filePath);
    std::string detectExtension(const std::string& filePath);
    bool isValidConversion(const std::string& fromExt, const std::string& toExt);

    // Image operations
    ConversionResult convertImage(
        const std::string& inputPath,
        const std::string& outputPath,
        const std::string& targetFormat,
        const ConversionOptions& options,
        const ProgressCallback& progress
    );

    // Compression
    ConversionResult compressImage(
        const std::string& inputPath,
        const std::string& outputPath,
        int quality,
        long targetSizeBytes,
        const ProgressCallback& progress
    );

    // Resize
    ConversionResult resizeImage(
        const std::string& inputPath,
        const std::string& outputPath,
        int width,
        int height,
        bool keepAspect,
        const ProgressCallback& progress
    );

    // Predict output size
    long predictOutputSize(
        const std::string& inputPath,
        const std::string& targetFormat,
        int quality
    );

    // Suggest best format
    std::string suggestBestFormat(const std::string& inputPath);

    // Get valid output formats for a given input
    std::vector<std::string> getValidOutputFormats(const std::string& inputPath);

    // Cancel ongoing operation
    void cancelOperation();
    bool isCancelled() const { return cancelled_.load(); }

    // Temp file management
    void cleanupTempFiles(const std::string& tempDir);

private:
    ConverterEngine() = default;
    ~ConverterEngine() = default;
    ConverterEngine(const ConverterEngine&) = delete;
    ConverterEngine& operator=(const ConverterEngine&) = delete;

    std::atomic<bool> cancelled_{false};

    // Internal helpers
    bool readBitmapFromFile(const std::string& path, std::vector<uint8_t>& pixels,
                            int& width, int& height, int& channels);
    bool writeBitmapToFile(const std::string& path, const std::vector<uint8_t>& pixels,
                           int width, int height, int channels,
                           const std::string& format, int quality);
    std::vector<uint8_t> encodeJpeg(const std::vector<uint8_t>& pixels,
                                     int width, int height, int channels, int quality);
    std::vector<uint8_t> encodePng(const std::vector<uint8_t>& pixels,
                                   int width, int height, int channels);
    std::vector<uint8_t> encodeWebP(const std::vector<uint8_t>& pixels,
                                    int width, int height, int channels, int quality);
    std::vector<uint8_t> resizeBitmap(const std::vector<uint8_t>& srcPixels,
                                      int srcW, int srcH, int channels,
                                      int dstW, int dstH);
    std::vector<uint8_t> rotateBitmap(const std::vector<uint8_t>& pixels,
                                      int w, int h, int channels, int degrees);
};

// ─── Valid Conversion Matrix ─────────────────────────────────────────────────
// Defines which conversions are allowed
namespace ConversionMatrix {
    // Image formats
    const std::vector<std::string> IMAGE_FORMATS = {
        "jpg", "jpeg", "png", "webp", "bmp", "tiff", "gif"
    };
    // Document formats  
    const std::vector<std::string> DOC_FORMATS = {
        "pdf"
    };
    // Audio formats
    const std::vector<std::string> AUDIO_FORMATS = {
        "mp3", "aac", "wav", "ogg", "flac", "m4a"
    };
    // Video formats
    const std::vector<std::string> VIDEO_FORMATS = {
        "mp4", "mkv", "avi", "mov", "webm", "3gp"
    };
    // 3D formats
    const std::vector<std::string> THREE_D_FORMATS = {
        "obj", "fbx", "stl"
    };

    bool isImageFormat(const std::string& ext);
    bool isDocFormat(const std::string& ext);
    bool isAudioFormat(const std::string& ext);
    bool isVideoFormat(const std::string& ext);
    bool isThreeDFormat(const std::string& ext);
}
