#include "converter_engine.h"
#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <string>
#include <vector>
#include <algorithm>
#include <cmath>
#include <fstream>
#include <thread>

#define LOG_TAG "UCEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── ConversionMatrix definitions ────────────────────────────────────────────
namespace ConversionMatrix {
    const std::vector<std::string> IMAGE_FORMATS   = {"jpg","jpeg","png","webp","bmp","tiff","gif"};
    const std::vector<std::string> DOC_FORMATS     = {"pdf"};
    const std::vector<std::string> AUDIO_FORMATS   = {"mp3","aac","wav","ogg","flac","m4a"};
    const std::vector<std::string> VIDEO_FORMATS   = {"mp4","mkv","avi","mov","webm","3gp"};
    const std::vector<std::string> THREE_D_FORMATS = {"obj","fbx","stl"};

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
    if (ConversionMatrix::isImageFormat(from) && ConversionMatrix::isImageFormat(to)) return true;
    if (ConversionMatrix::isImageFormat(from) && ConversionMatrix::isDocFormat(to))   return true;
    if (ConversionMatrix::isDocFormat(from)   && ConversionMatrix::isImageFormat(to)) return true;
    if (ConversionMatrix::isVideoFormat(from) && ConversionMatrix::isVideoFormat(to)) return true;
    if (ConversionMatrix::isVideoFormat(from) && ConversionMatrix::isAudioFormat(to)) return true;
    if (ConversionMatrix::isAudioFormat(from) && ConversionMatrix::isAudioFormat(to)) return true;
    if (ConversionMatrix::isThreeDFormat(from)&& ConversionMatrix::isThreeDFormat(to))return true;
    return false;
}

std::vector<std::string> ConverterEngine::getValidOutputFormats(const std::string& inputPath) {
    std::string ext = detectExtension(inputPath);
    std::vector<std::string> valid;
    if (ConversionMatrix::isImageFormat(ext)) {
        for (const auto& f : ConversionMatrix::IMAGE_FORMATS) if (f!=ext && f!="jpeg") valid.push_back(f);
        valid.push_back("pdf");
    } else if (ConversionMatrix::isDocFormat(ext)) {
        valid = {"jpg","png","webp","bmp"};
    } else if (ConversionMatrix::isVideoFormat(ext)) {
        for (const auto& f : ConversionMatrix::VIDEO_FORMATS) if (f!=ext) valid.push_back(f);
        for (const auto& f : ConversionMatrix::AUDIO_FORMATS) valid.push_back(f);
    } else if (ConversionMatrix::isAudioFormat(ext)) {
        for (const auto& f : ConversionMatrix::AUDIO_FORMATS) if (f!=ext) valid.push_back(f);
    } else if (ConversionMatrix::isThreeDFormat(ext)) {
        for (const auto& f : ConversionMatrix::THREE_D_FORMATS) if (f!=ext) valid.push_back(f);
    }
    return valid;
}

std::string ConverterEngine::suggestBestFormat(const std::string& inputPath) {
    std::string ext = detectExtension(inputPath);
    if (ConversionMatrix::isImageFormat(ext))  return "webp";
    if (ConversionMatrix::isVideoFormat(ext))  return "mp4";
    if (ConversionMatrix::isAudioFormat(ext))  return "aac";
    return ext;
}

long ConverterEngine::predictOutputSize(const std::string& inputPath,
                                         const std::string& targetFormat, int quality) {
    std::ifstream file(inputPath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) return -1;
    long inputSize = static_cast<long>(file.tellg());
    double ratio = (targetFormat=="webp") ? 0.65 :
                   (targetFormat=="jpg"||targetFormat=="jpeg") ? 0.3+(0.7*quality/100.0) :
                   (targetFormat=="png") ? 0.9 :
                   (targetFormat=="bmp") ? 3.0 : 0.8;
    return static_cast<long>(inputSize * ratio * (0.5 + 0.5*quality/100.0));
}

void ConverterEngine::cancelOperation() { cancelled_.store(true); }
void ConverterEngine::cleanupTempFiles(const std::string&) {}

std::vector<uint8_t> ConverterEngine::resizeBitmap(
    const std::vector<uint8_t>& src, int srcW, int srcH, int channels, int dstW, int dstH) {
    std::vector<uint8_t> dst(dstW*dstH*channels);
    float xs=(float)srcW/dstW, ys=(float)srcH/dstH;
    for(int y=0;y<dstH;++y) for(int x=0;x<dstW;++x) {
        int sx=std::min((int)(x*xs),srcW-1), sy=std::min((int)(y*ys),srcH-1);
        for(int c=0;c<channels;++c) dst[(y*dstW+x)*channels+c]=src[(sy*srcW+sx)*channels+c];
    }
    return dst;
}

std::vector<uint8_t> ConverterEngine::rotateBitmap(
    const std::vector<uint8_t>& pixels, int w, int h, int channels, int degrees) {
    if(degrees==0||degrees==360) return pixels;
    std::vector<uint8_t> rotated(w*h*channels);
    if(degrees==180) for(int y=0;y<h;++y) for(int x=0;x<w;++x) for(int c=0;c<channels;++c)
        rotated[((h-1-y)*w+(w-1-x))*channels+c]=pixels[(y*w+x)*channels+c];
    return rotated;
}

// ─── JNI — names match private external functions in NativeEngine.kt ─────────
extern "C" {

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_getEngineVersionNative(JNIEnv* env, jobject)
{ return env->NewStringUTF("1.0.0-NDK"); }

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_detectMimeTypeNative(JNIEnv* env, jobject, jstring fp)
{
    const char* p=env->GetStringUTFChars(fp,nullptr);
    std::string r=ConverterEngine::getInstance().detectMimeType(p);
    env->ReleaseStringUTFChars(fp,p);
    return env->NewStringUTF(r.c_str());
}

JNIEXPORT jint JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_detectCategoryNative(JNIEnv* env, jobject, jstring fp)
{
    const char* p=env->GetStringUTFChars(fp,nullptr);
    int c=static_cast<int>(ConverterEngine::getInstance().detectCategory(p));
    env->ReleaseStringUTFChars(fp,p);
    return c;
}

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_detectByMagicBytesNative(JNIEnv* env, jobject, jstring fp)
{
    const char* p=env->GetStringUTFChars(fp,nullptr);
    std::string ext=ConverterEngine::getInstance().detectExtension(p);
    env->ReleaseStringUTFChars(fp,p);
    return env->NewStringUTF(ext.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_isValidConversionNative(JNIEnv* env, jobject, jstring from, jstring to)
{
    const char* f=env->GetStringUTFChars(from,nullptr);
    const char* t=env->GetStringUTFChars(to,nullptr);
    bool v=ConverterEngine::getInstance().isValidConversion(f,t);
    env->ReleaseStringUTFChars(from,f); env->ReleaseStringUTFChars(to,t);
    return (jboolean)v;
}

JNIEXPORT jobjectArray JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_getValidOutputFormatsNative(JNIEnv* env, jobject, jstring ip)
{
    const char* p=env->GetStringUTFChars(ip,nullptr);
    auto fmts=ConverterEngine::getInstance().getValidOutputFormats(p);
    env->ReleaseStringUTFChars(ip,p);
    jclass sc=env->FindClass("java/lang/String");
    jobjectArray arr=env->NewObjectArray((jsize)fmts.size(),sc,nullptr);
    for(int i=0;i<(int)fmts.size();++i)
        env->SetObjectArrayElement(arr,i,env->NewStringUTF(fmts[i].c_str()));
    return arr;
}

JNIEXPORT jstring JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_suggestBestFormatNative(JNIEnv* env, jobject, jstring ip)
{
    const char* p=env->GetStringUTFChars(ip,nullptr);
    std::string r=ConverterEngine::getInstance().suggestBestFormat(p);
    env->ReleaseStringUTFChars(ip,p);
    return env->NewStringUTF(r.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_predictOutputSizeNative(JNIEnv* env, jobject, jstring ip, jstring fmt, jint q)
{
    const char* p=env->GetStringUTFChars(ip,nullptr);
    const char* f=env->GetStringUTFChars(fmt,nullptr);
    long sz=ConverterEngine::getInstance().predictOutputSize(p,f,(int)q);
    env->ReleaseStringUTFChars(ip,p); env->ReleaseStringUTFChars(fmt,f);
    return (jlong)sz;
}

JNIEXPORT jlong JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_getFileSizeNative(JNIEnv* env, jobject, jstring fp)
{
    const char* p=env->GetStringUTFChars(fp,nullptr);
    std::ifstream f(p,std::ios::binary|std::ios::ate);
    long sz=f.is_open()?(long)f.tellg():-1L;
    env->ReleaseStringUTFChars(fp,p);
    return (jlong)sz;
}

JNIEXPORT jboolean JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_fileExistsNative(JNIEnv* env, jobject, jstring fp)
{
    const char* p=env->GetStringUTFChars(fp,nullptr);
    std::ifstream f(p);
    bool ok=f.good();
    env->ReleaseStringUTFChars(fp,p);
    return (jboolean)ok;
}

JNIEXPORT void JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_cancelOperationNative(JNIEnv*, jobject)
{ ConverterEngine::getInstance().cancelOperation(); }

JNIEXPORT jint JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_getAvailableThreadsNative(JNIEnv*, jobject)
{ return (jint)std::thread::hardware_concurrency(); }

JNIEXPORT jboolean JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_resizeBitmapNativeImpl(JNIEnv* env, jobject, jobject src, jobject dst)
{
    AndroidBitmapInfo si,di; void *sp=nullptr,*dp=nullptr;
    if(AndroidBitmap_getInfo(env,src,&si)<0) return JNI_FALSE;
    if(AndroidBitmap_getInfo(env,dst,&di)<0) return JNI_FALSE;
    if(AndroidBitmap_lockPixels(env,src,&sp)<0) return JNI_FALSE;
    if(AndroidBitmap_lockPixels(env,dst,&dp)<0){ AndroidBitmap_unlockPixels(env,src); return JNI_FALSE; }
    uint32_t* s=(uint32_t*)sp; uint32_t* d=(uint32_t*)dp;
    float xs=(float)si.width/di.width, ys=(float)si.height/di.height;
    for(uint32_t y=0;y<di.height;++y) for(uint32_t x=0;x<di.width;++x){
        uint32_t sx=std::min((uint32_t)(x*xs),si.width-1);
        uint32_t sy=std::min((uint32_t)(y*ys),si.height-1);
        d[y*di.width+x]=s[sy*si.width+sx];
    }
    AndroidBitmap_unlockPixels(env,src); AndroidBitmap_unlockPixels(env,dst);
    return JNI_TRUE;
}

JNIEXPORT jfloat JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_computeBrightnessNative(JNIEnv* env, jobject, jobject bmp)
{
    AndroidBitmapInfo info; void* px=nullptr;
    if(AndroidBitmap_getInfo(env,bmp,&info)<0) return 0.5f;
    if(AndroidBitmap_lockPixels(env,bmp,&px)<0) return 0.5f;
    uint32_t* d=(uint32_t*)px;
    double tot=0.0; uint64_t cnt=(uint64_t)info.width*info.height;
    for(uint64_t i=0;i<cnt;++i){
        uint32_t p=d[i];
        tot+=0.299*((p>>16)&0xFF)+0.587*((p>>8)&0xFF)+0.114*(p&0xFF);
    }
    AndroidBitmap_unlockPixels(env,bmp);
    return (float)(tot/cnt/255.0);
}

JNIEXPORT jfloat JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_computeImageComplexityNative(JNIEnv* env, jobject, jobject bmp)
{
    AndroidBitmapInfo info; void* px=nullptr;
    if(AndroidBitmap_getInfo(env,bmp,&info)<0) return 0.5f;
    if(AndroidBitmap_lockPixels(env,bmp,&px)<0) return 0.5f;
    AndroidBitmap_unlockPixels(env,bmp);
    return 0.5f; // simplified
}

JNIEXPORT void JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_applyGrayscaleNative(JNIEnv* env, jobject, jobject bmp)
{
    AndroidBitmapInfo info; void* px=nullptr;
    if(AndroidBitmap_getInfo(env,bmp,&info)<0) return;
    if(AndroidBitmap_lockPixels(env,bmp,&px)<0) return;
    uint32_t* d=(uint32_t*)px;
    uint64_t cnt=(uint64_t)info.width*info.height;
    for(uint64_t i=0;i<cnt;++i){
        uint32_t p=d[i];
        uint8_t a=(p>>24)&0xFF;
        uint8_t g=(uint8_t)(0.299*((p>>16)&0xFF)+0.587*((p>>8)&0xFF)+0.114*(p&0xFF));
        d[i]=(a<<24)|(g<<16)|(g<<8)|g;
    }
    AndroidBitmap_unlockPixels(env,bmp);
}

JNIEXPORT void JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_applyLightBlurNative(JNIEnv* env, jobject, jobject bmp, jint)
{
    // no-op safe stub
    (void)env; (void)bmp;
}

JNIEXPORT void JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_adjustBrightnessContrastNative(JNIEnv* env, jobject, jobject bmp, jfloat br, jfloat co)
{
    AndroidBitmapInfo info; void* px=nullptr;
    if(AndroidBitmap_getInfo(env,bmp,&info)<0) return;
    if(AndroidBitmap_lockPixels(env,bmp,&px)<0) return;
    uint32_t* d=(uint32_t*)px;
    uint8_t lut[256];
    for(int i=0;i<256;++i){
        float v=i/255.0f;
        v=(v-0.5f)*co+0.5f+br;
        v=std::max(0.0f,std::min(1.0f,v));
        lut[i]=(uint8_t)(v*255);
    }
    uint64_t cnt=(uint64_t)info.width*info.height;
    for(uint64_t i=0;i<cnt;++i){
        uint32_t p=d[i];
        d[i]=((p>>24)&0xFF)<<24|lut[(p>>16)&0xFF]<<16|lut[(p>>8)&0xFF]<<8|lut[p&0xFF];
    }
    AndroidBitmap_unlockPixels(env,bmp);
}

JNIEXPORT jint JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_estimateOptimalQualityNative(JNIEnv*, jobject, jlong inSz, jlong tgtSz, jstring)
{
    if(tgtSz<=0||inSz<=0) return 85;
    double ratio=(double)tgtSz/inSz;
    int q=(int)(100.0*std::sqrt(ratio));
    return std::max(5,std::min(95,q));
}

JNIEXPORT jfloat JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_computeCompressionRatioNative(JNIEnv*, jobject, jlong orig, jlong comp)
{
    if(orig<=0) return 0.0f;
    float r=1.0f-(float)comp/orig;
    return std::max(0.0f,std::min(1.0f,r));
}

JNIEXPORT jbyteArray JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_compressDataNativeImpl(JNIEnv* env, jobject, jbyteArray input)
{
    // Return input as-is (safe stub)
    return input;
}

JNIEXPORT jbyteArray JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_decompressDataNativeImpl(JNIEnv* env, jobject, jbyteArray input)
{
    return input;
}

} // extern "C"
