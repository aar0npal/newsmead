# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# JNI names are the pinned MNN ABI.
-keep class com.taobao.android.mnn.MNNNetNative { *; }
# ---------------------------------------------------------------------------
# Logging (see docs/logging.md)
# ---------------------------------------------------------------------------

# Keep readable stack traces in Crashlytics reports from the minified release
# build. Without these, a crash report is a list of obfuscated single letters.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Strip debug-level logging out of release builds. AppLog.d/i already check
# BuildConfig.DEBUG; this removes the calls and their string construction
# entirely rather than leaving dead branches in the APK.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Warnings and errors are deliberately kept: they are what feeds Crashlytics
# non-fatals, so do not add w/e to the rule above.

# Readability4J uses slf4j without a binding (no-op at runtime).
-dontwarn org.slf4j.impl.StaticLoggerBinder
