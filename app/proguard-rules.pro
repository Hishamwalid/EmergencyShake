# Add project specific ProGuard rules here.
# 1. Keep all classes mentioned in the Manifest
# This ensures Activities and Services aren't renamed or removed.
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# 2. Specifically protect your custom services/activities if needed
-keep class .service.EmergencyService { *; }
-keep class .EmergencyHandler { *; }

# 3. Prevent obfuscation of specialized methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 4. Keep GSON/Serialization classes (if you use them for settings/JSON)
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
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