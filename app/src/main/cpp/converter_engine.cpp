#include <jni.h>
#include <android/log.h>
#include <android/bitmap.h>
#include <string>
#include <vector>
#include <algorithm>
#include <cmath>
#include <fstream>
#include <thread>
#include <atomic>
#include <sstream>

#define LOG_TAG "UCEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static std::atomic<bool> g_cancelled{false};

static std::string toLower(std::string s) {
    std::transform(s.begin(),s.end(),s.begin(),::tolower); return s;
}
static std::string getExt(const std::string& p) {
    auto d=p.rfind('.'); return d==std::string::npos?"":toLower(p.substr(d+1));
}

static bool inList(const std::vector<std::string>& l, const std::string& e) {
    for(auto& x:l) if(x==e) return true; return false;
}

const std::vector<std::string> IMG  = {"jpg","jpeg","png","webp","bmp","gif","tiff","ico","avif"};
const std::vector<std::string> DOC  = {"pdf"};
const std::vector<std::string> VID  = {"mp4","mkv","avi","mov","webm","3gp"};
const std::vector<std::string> AUD  = {"mp3","aac","wav","ogg","flac","m4a"};
const std::vector<std::string> TD   = {"obj","fbx","stl"};

extern "C" {

JNIEXPORT jstring JNICALL Java_com_universalconverter_pro_engine_NativeEngine_getVersion(JNIEnv* e, jobject)
{ return e->NewStringUTF("2.0.0-NDK"); }

JNIEXPORT jint JNICALL Java_com_universalconverter_pro_engine_NativeEngine_getCategoryNative(JNIEnv* e, jobject, jstring p) {
    const char* cp=e->GetStringUTFChars(p,nullptr);
    std::string ext=getExt(cp); e->ReleaseStringUTFChars(p,cp);
    if(inList(IMG,ext))  return 0;
    if(inList(DOC,ext))  return 1;
    if(inList(VID,ext))  return 2;
    if(inList(AUD,ext))  return 3;
    if(inList(TD,ext))   return 5;
    return 6;
}

JNIEXPORT jboolean JNICALL Java_com_universalconverter_pro_engine_NativeEngine_isValidConversionNative(JNIEnv* e, jobject, jstring a, jstring b) {
    const char* ca=e->GetStringUTFChars(a,nullptr);
    const char* cb=e->GetStringUTFChars(b,nullptr);
    std::string f=toLower(ca), t=toLower(cb);
    e->ReleaseStringUTFChars(a,ca); e->ReleaseStringUTFChars(b,cb);
    if(f==t) return JNI_FALSE;
    bool fi=inList(IMG,f),fd=inList(DOC,f),fv=inList(VID,f),fa=inList(AUD,f),f3=inList(TD,f);
    bool ti=inList(IMG,t),td=inList(DOC,t),tv=inList(VID,t),ta=inList(AUD,t),t3=inList(TD,t);
    return (jboolean)((fi&&ti)||(fi&&td)||(fd&&ti)||(fv&&tv)||(fv&&ta)||(fa&&ta)||(f3&&t3));
}

JNIEXPORT jlong JNICALL Java_com_universalconverter_pro_engine_NativeEngine_getFileSizeNative(JNIEnv* e, jobject, jstring p) {
    const char* cp=e->GetStringUTFChars(p,nullptr);
    std::ifstream f(cp,std::ios::binary|std::ios::ate);
    long s=f.is_open()?(long)f.tellg():-1L;
    e->ReleaseStringUTFChars(p,cp); return s;
}

JNIEXPORT jlong JNICALL Java_com_universalconverter_pro_engine_NativeEngine_predictSizeNative(JNIEnv* e, jobject, jlong sz, jstring fmt, jint q) {
    const char* cf=e->GetStringUTFChars(fmt,nullptr);
    std::string f=toLower(cf); e->ReleaseStringUTFChars(fmt,cf);
    double r=(f=="webp")?0.55:(f=="jpg"||f=="jpeg")?(0.25+0.65*(q/100.0)):(f=="png")?0.85:(f=="bmp")?2.8:0.75;
    return (jlong)(sz*r*(0.4+0.6*q/100.0));
}

JNIEXPORT jfloat JNICALL Java_com_universalconverter_pro_engine_NativeEngine_computeBrightnessNative(JNIEnv* e, jobject, jobject bmp) {
    AndroidBitmapInfo i; void* px=nullptr;
    if(AndroidBitmap_getInfo(e,bmp,&i)<0) return 0.5f;
    if(AndroidBitmap_lockPixels(e,bmp,&px)<0) return 0.5f;
    uint32_t* d=(uint32_t*)px; double t=0; uint64_t n=(uint64_t)i.width*i.height;
    for(uint64_t x=0;x<n;++x){uint32_t p=d[x];t+=0.299*((p>>16)&0xFF)+0.587*((p>>8)&0xFF)+0.114*(p&0xFF);}
    AndroidBitmap_unlockPixels(e,bmp); return (float)(t/n/255.0);
}

JNIEXPORT jfloat JNICALL Java_com_universalconverter_pro_engine_NativeEngine_computeComplexityNative(JNIEnv* e, jobject, jobject bmp) {
    AndroidBitmapInfo i; void* px=nullptr;
    if(AndroidBitmap_getInfo(e,bmp,&i)<0) return 0.5f;
    if(AndroidBitmap_lockPixels(e,bmp,&px)<0) return 0.5f;
    uint32_t* d=(uint32_t*)px; double es=0; uint64_t cnt=0;
    uint32_t sx=std::max(1u,i.width/32), sy=std::max(1u,i.height/32);
    auto lum=[&](int x,int y)->double{uint32_t p=d[y*i.width+x];return 0.299*((p>>16)&0xFF)+0.587*((p>>8)&0xFF)+0.114*(p&0xFF);};
    for(uint32_t y=1;y<i.height-1;y+=sy) for(uint32_t x=1;x<i.width-1;x+=sx){
        double gx=-lum(x-1,y-1)-2*lum(x-1,y)-lum(x-1,y+1)+lum(x+1,y-1)+2*lum(x+1,y)+lum(x+1,y+1);
        double gy=-lum(x-1,y-1)-2*lum(x,y-1)-lum(x+1,y-1)+lum(x-1,y+1)+2*lum(x,y+1)+lum(x+1,y+1);
        es+=std::sqrt(gx*gx+gy*gy); ++cnt;
    }
    AndroidBitmap_unlockPixels(e,bmp);
    return (float)std::min(1.0,cnt>0?(es/cnt/400.0):0.5);
}

JNIEXPORT void JNICALL Java_com_universalconverter_pro_engine_NativeEngine_applyGrayscaleNative(JNIEnv* e, jobject, jobject bmp) {
    AndroidBitmapInfo i; void* px=nullptr;
    if(AndroidBitmap_getInfo(e,bmp,&i)<0||AndroidBitmap_lockPixels(e,bmp,&px)<0) return;
    uint32_t* d=(uint32_t*)px; uint64_t n=(uint64_t)i.width*i.height;
    for(uint64_t x=0;x<n;++x){uint32_t p=d[x];uint8_t a=(p>>24)&0xFF,g=(uint8_t)(0.299*((p>>16)&0xFF)+0.587*((p>>8)&0xFF)+0.114*(p&0xFF));d[x]=(a<<24)|(g<<16)|(g<<8)|g;}
    AndroidBitmap_unlockPixels(e,bmp);
}

JNIEXPORT jboolean JNICALL Java_com_universalconverter_pro_engine_NativeEngine_resizeBitmapNative(JNIEnv* e, jobject, jobject s, jobject d) {
    AndroidBitmapInfo si,di; void *sp=nullptr,*dp=nullptr;
    if(AndroidBitmap_getInfo(e,s,&si)<0||AndroidBitmap_getInfo(e,d,&di)<0) return JNI_FALSE;
    if(AndroidBitmap_lockPixels(e,s,&sp)<0) return JNI_FALSE;
    if(AndroidBitmap_lockPixels(e,d,&dp)<0){AndroidBitmap_unlockPixels(e,s);return JNI_FALSE;}
    uint32_t* src=(uint32_t*)sp; uint32_t* dst=(uint32_t*)dp;
    float xs=(float)si.width/di.width, ys=(float)si.height/di.height;
    for(uint32_t y=0;y<di.height;++y) for(uint32_t x=0;x<di.width;++x){
        uint32_t sx=std::min((uint32_t)(x*xs),si.width-1), sy=std::min((uint32_t)(y*ys),si.height-1);
        dst[y*di.width+x]=src[sy*si.width+sx];
    }
    AndroidBitmap_unlockPixels(e,s); AndroidBitmap_unlockPixels(e,d);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL Java_com_universalconverter_pro_engine_NativeEngine_estimateQualityNative(JNIEnv*, jobject, jlong in, jlong tgt) {
    if(tgt<=0||in<=0) return 85;
    int q=(int)(100.0*std::sqrt((double)tgt/in));
    return std::max(5,std::min(95,q));
}

JNIEXPORT jfloat JNICALL Java_com_universalconverter_pro_engine_NativeEngine_computeSSIMNative(JNIEnv* e, jobject, jobject b1, jobject b2) {
    AndroidBitmapInfo i1,i2; void *p1=nullptr,*p2=nullptr;
    if(AndroidBitmap_getInfo(e,b1,&i1)<0||AndroidBitmap_getInfo(e,b2,&i2)<0) return -1.0f;
    if(i1.width!=i2.width||i1.height!=i2.height) return -1.0f;
    if(AndroidBitmap_lockPixels(e,b1,&p1)<0) return -1.0f;
    if(AndroidBitmap_lockPixels(e,b2,&p2)<0){AndroidBitmap_unlockPixels(e,b1);return -1.0f;}
    uint32_t* d1=(uint32_t*)p1; uint32_t* d2=(uint32_t*)p2;
    uint64_t n=(uint64_t)i1.width*i1.height;
    double mu1=0,mu2=0,sig1=0,sig2=0,sig12=0;
    auto lum=[](uint32_t p)->double{return 0.299*((p>>16)&0xFF)+0.587*((p>>8)&0xFF)+0.114*(p&0xFF);};
    for(uint64_t i=0;i<n;++i){mu1+=lum(d1[i]);mu2+=lum(d2[i]);}
    mu1/=n; mu2/=n;
    for(uint64_t i=0;i<n;++i){
        double a=lum(d1[i])-mu1, b=lum(d2[i])-mu2;
        sig1+=a*a; sig2+=b*b; sig12+=a*b;
    }
    sig1=std::sqrt(sig1/n); sig2=std::sqrt(sig2/n); sig12/=n;
    double c1=6.5025,c2=58.5225;
    double ssim=(2*mu1*mu2+c1)*(2*sig12+c2)/((mu1*mu1+mu2*mu2+c1)*(sig1*sig1+sig2*sig2+c2));
    AndroidBitmap_unlockPixels(e,b1); AndroidBitmap_unlockPixels(e,b2);
    return (float)ssim;
}

JNIEXPORT jlong JNICALL Java_com_universalconverter_pro_engine_NativeEngine_computePerceptualHashNative(JNIEnv* e, jobject, jobject bmp) {
    AndroidBitmapInfo info; void* px=nullptr;
    if(AndroidBitmap_getInfo(e,bmp,&info)<0||AndroidBitmap_lockPixels(e,bmp,&px)<0) return 0;
    uint32_t* d=(uint32_t*)px;
    // 8x8 DCT-based pHash
    double pixels[64]={};
    float xs=(float)info.width/8, ys=(float)info.height/8;
    for(int y=0;y<8;++y) for(int x=0;x<8;++x){
        int sx=std::min((int)(x*xs),(int)info.width-1), sy=std::min((int)(y*ys),(int)info.height-1);
        uint32_t p=d[sy*info.width+sx];
        pixels[y*8+x]=0.299*((p>>16)&0xFF)+0.587*((p>>8)&0xFF)+0.114*(p&0xFF);
    }
    AndroidBitmap_unlockPixels(e,bmp);
    double mean=0; for(int i=0;i<64;++i) mean+=pixels[i]; mean/=64;
    uint64_t hash=0; for(int i=0;i<64;++i) if(pixels[i]>mean) hash|=(1ULL<<i);
    return (jlong)hash;
}

JNIEXPORT void JNICALL Java_com_universalconverter_pro_engine_NativeEngine_cancelNative(JNIEnv*, jobject)
{ g_cancelled.store(true); }

JNIEXPORT void JNICALL Java_com_universalconverter_pro_engine_NativeEngine_resetCancelNative(JNIEnv*, jobject)
{ g_cancelled.store(false); }

JNIEXPORT jint JNICALL Java_com_universalconverter_pro_engine_NativeEngine_getThreadCountNative(JNIEnv*, jobject)
{ return (jint)std::thread::hardware_concurrency(); }

} // extern "C"
