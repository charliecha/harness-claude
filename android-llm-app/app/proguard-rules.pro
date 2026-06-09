# ProGuard rules for release builds
# Keep JNI bridge intact
-keep class com.harnessclaude.llm.nativebridge.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
