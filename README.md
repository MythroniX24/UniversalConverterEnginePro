# Universal Converter Engine Pro 🚀

A production-ready Android application for converting, compressing, and optimizing files — built with **Kotlin**, **C++ NDK**, **JNI**, and **Material Design**.

[![Android CI](https://github.com/YOUR_USERNAME/UniversalConverterEnginePro/actions/workflows/android-build.yml/badge.svg)](https://github.com/YOUR_USERNAME/UniversalConverterEnginePro/actions/workflows/android-build.yml)

---

## 📦 Download APK

After pushing to GitHub, the APK is automatically built via GitHub Actions.
Go to → **Actions → Android CI → Latest run → Artifacts** to download.

---

## 🏗️ Architecture

```
UniversalConverterEnginePro/
├── .github/workflows/android-build.yml   ← CI/CD pipeline
├── app/
│   ├── src/main/
│   │   ├── cpp/                          ← C++ NDK engine
│   │   │   ├── CMakeLists.txt
│   │   │   ├── converter_engine.cpp      ← Core engine + JNI bridge
│   │   │   ├── converter_engine.h
│   │   │   ├── image_processor.cpp       ← Native image operations
│   │   │   ├── file_detector.cpp         ← Magic byte detection
│   │   │   ├── compression_engine.cpp    ← Compression algorithms
│   │   │   └── thread_pool.cpp           ← Native threading
│   │   ├── java/com/universalconverter/pro/
│   │   │   ├── engine/
│   │   │   │   ├── NativeEngine.kt       ← JNI bridge (Kotlin)
│   │   │   │   ├── FileDetector.kt       ← File analysis
│   │   │   │   ├── ImageConverter.kt     ← Image processing
│   │   │   │   ├── PdfConverter.kt       ← PDF operations
│   │   │   │   ├── MediaConverter.kt     ← Audio/video extraction
│   │   │   │   ├── ConversionJob.kt      ← Job data model
│   │   │   │   ├── ConversionQueue.kt    ← Queue manager
│   │   │   │   └── TempFileManager.kt
│   │   │   ├── ui/
│   │   │   │   ├── image/                ← Image tab
│   │   │   │   ├── pdf/                  ← PDF tab
│   │   │   │   ├── media/                ← Media tab
│   │   │   │   └── threed/               ← 3D viewer (OpenGL ES 2.0)
│   │   │   ├── premium/                  ← Premium/monetization
│   │   │   ├── worker/                   ← WorkManager background jobs
│   │   │   ├── MainActivity.kt
│   │   │   ├── SplashActivity.kt
│   │   │   └── ConverterApplication.kt
│   │   └── res/                          ← Layouts, drawables, strings
│   └── build.gradle
└── build.gradle
```

---

## 🚀 Features

### 🖼️ Image Engine
| Feature | Free | Pro |
|---|---|---|
| Convert PNG/JPG/WebP/BMP/GIF/TIFF | ✅ | ✅ |
| Image → PDF | ✅ | ✅ |
| Compress with quality slider | ✅ | ✅ |
| EXIF removal | ✅ | ✅ |
| Target size compression | ❌ | ✅ |
| Batch processing (multi-file) | ❌ | ✅ |
| Resize / Rotate | ✅ | ✅ |

### 📄 PDF Engine
| Feature | Free | Pro |
|---|---|---|
| Image → PDF | ✅ | ✅ |
| PDF → Image | ✅ | ✅ |
| PDF Compression | ❌ | ✅ |
| PDF Merge | ❌ | ✅ |

### 🎥 Media Engine
| Feature | Free | Pro |
|---|---|---|
| Audio extraction from video | ✅ | ✅ |
| Output to AAC/MP3/WAV | ✅ | ✅ |
| Media info display | ✅ | ✅ |

### 🧊 3D Engine
| Feature | Free | Pro |
|---|---|---|
| OBJ file viewer (OpenGL ES 2.0) | ✅ | ✅ |
| Touch-rotate model | ✅ | ✅ |
| Vertex/triangle count display | ✅ | ✅ |

---

## ⚡ NDK Engine

The C++ native library (`libuniversalconverter.so`) provides:

- **File detection** — magic byte sniffing (JPEG, PNG, WebP, PDF, MP4, MKV, MP3…)
- **Conversion validation** — blocks all invalid conversions before they start
- **Native bitmap operations** — resize, rotate, brightness, contrast, grayscale (via Android Bitmap API)
- **Compression estimation** — predicts optimal quality for target file size
- **Thread pool** — uses `std::thread` / hardware concurrency
- **RLE compression** — for raw byte buffer compression

---

## 🔧 Build Locally

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- NDK 25.2.9519653

```bash
git clone https://github.com/YOUR_USERNAME/UniversalConverterEnginePro.git
cd UniversalConverterEnginePro
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/
```

---

## 🤖 GitHub Actions CI/CD

Push to `main` / `master` / `develop` → auto-builds debug + release APKs.

Workflow path: `.github/workflows/android-build.yml`

Steps:
1. Checkout code
2. Set up JDK 17 (Temurin)
3. Install Android SDK + NDK 25
4. Cache Gradle
5. `./gradlew assembleDebug assembleRelease`
6. Upload APKs as downloadable artifacts (30-day retention)

---

## 🔐 Privacy & Security

- **100% local processing** — no files are uploaded to any server
- **Temporary file cleanup** — output files purged after 24 hours
- **EXIF stripping** — removes GPS, device, and author metadata
- **Minimal permissions** — only requests what's needed per Android version

---

## 💰 Monetization

Free tier: 10 conversions/day
Pro tier: Unlimited conversions, batch processing, PDF merge, target-size compression

In a production release, integrate Google Play Billing for the Pro upgrade.

---

## 📋 Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 1.9 + C++17 |
| UI | Material Design 3 |
| Navigation | Jetpack Navigation Component |
| Background | WorkManager + Coroutines |
| Native | Android NDK + JNI |
| 3D Rendering | OpenGL ES 2.0 |
| Build | Gradle 8.4 + CMake 3.22 |
| CI/CD | GitHub Actions |

---

## 📄 License

MIT License — free for personal and commercial use.
