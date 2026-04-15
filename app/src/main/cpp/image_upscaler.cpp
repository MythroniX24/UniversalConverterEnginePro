// image_upscaler.cpp - Bicubic upscaling with sharpening
#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <algorithm>
#include <vector>

static float bicubicWeight(float x) {
    x = std::abs(x);
    if (x < 1.0f) return 1.5f*x*x*x - 2.5f*x*x + 1.0f;
    if (x < 2.0f) return -0.5f*x*x*x + 2.5f*x*x - 4.0f*x + 2.0f;
    return 0.0f;
}

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_universalconverter_pro_engine_NativeEngine_upscaleBitmapNative(
    JNIEnv* env, jobject, jobject src, jobject dst, jfloat sharpness)
{
    AndroidBitmapInfo si, di; void *sp=nullptr, *dp=nullptr;
    if (AndroidBitmap_getInfo(env,src,&si)<0 || AndroidBitmap_getInfo(env,dst,&di)<0) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env,src,&sp)<0) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env,dst,&dp)<0) { AndroidBitmap_unlockPixels(env,src); return JNI_FALSE; }

    uint32_t* S=(uint32_t*)sp; uint32_t* D=(uint32_t*)dp;
    float sx=(float)si.width/di.width, sy=(float)si.height/di.height;

    auto getPixel=[&](int x, int y, int ch)->float {
        x=std::max(0,std::min((int)si.width-1,x));
        y=std::max(0,std::min((int)si.height-1,y));
        uint32_t p=S[y*si.width+x];
        switch(ch){case 0:return (p>>16)&0xFF;case 1:return (p>>8)&0xFF;case 2:return p&0xFF;default:return (p>>24)&0xFF;}
    };

    for (uint32_t dy=0; dy<di.height; ++dy) {
        for (uint32_t dx=0; dx<di.width; ++dx) {
            float fx=dx*sx, fy=dy*sy;
            int ix=(int)fx, iy=(int)fy;
            float fracX=fx-ix, fracY=fy-iy;
            float r=0,g=0,b=0,a=0,tw=0;

            for (int jy=-1; jy<=2; ++jy) {
                float wy=bicubicWeight(jy-fracY);
                for (int jx=-1; jx<=2; ++jx) {
                    float wx=bicubicWeight(jx-fracX);
                    float w=wx*wy;
                    r+=w*getPixel(ix+jx,iy+jy,0);
                    g+=w*getPixel(ix+jx,iy+jy,1);
                    b+=w*getPixel(ix+jx,iy+jy,2);
                    a+=w*getPixel(ix+jx,iy+jy,3);
                    tw+=w;
                }
            }
            if(tw>0){r/=tw;g/=tw;b/=tw;a/=tw;}

            // Unsharp mask sharpening
            float center=getPixel(ix,iy,0);
            r=r+(r-center)*sharpness;
            center=getPixel(ix,iy,1);
            g=g+(g-center)*sharpness;
            center=getPixel(ix,iy,2);
            b=b+(b-center)*sharpness;

            auto clamp=[](float v)->uint8_t{return (uint8_t)std::max(0.0f,std::min(255.0f,v));};
            D[dy*di.width+dx]=((uint32_t)clamp(a)<<24)|((uint32_t)clamp(r)<<16)|((uint32_t)clamp(g)<<8)|clamp(b);
        }
    }
    AndroidBitmap_unlockPixels(env,src); AndroidBitmap_unlockPixels(env,dst);
    return JNI_TRUE;
}

} // extern "C"
