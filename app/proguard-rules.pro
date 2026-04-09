-keep class com.universalconverter.pro.** { *; }
-keep class com.itextpdf.** { *; }
-keepclassmembers class * {
    native <methods>;
}
-dontwarn com.itextpdf.**
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
