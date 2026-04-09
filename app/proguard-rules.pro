-keep class com.universalconverter.pro.** { *; }

# Keep all native JNI method declarations
-keepclassmembers class * {
    native <methods>;
}

# ── SLF4J / Logging (iTextPDF transitive dep — suppress R8 missing-class errors) ──
-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.**
-dontwarn org.slf4j.helpers.**
-keep class org.slf4j.** { *; }
-keep class org.slf4j.impl.** { *; }

# ── Bouncycastle ─────────────────────────────────────────────────────────────
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# ── Commons / Apache ─────────────────────────────────────────────────────────
-dontwarn org.apache.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── AndroidX WorkManager ──────────────────────────────────────────────────────
-keep class androidx.work.** { *; }

# ── Glide ─────────────────────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ── General Android ───────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
