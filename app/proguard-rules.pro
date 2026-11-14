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

# ── Shizuku (reflection-based, compileOnly) ─────────────────────────────────
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class com.x8rgb.data.db.entity.** { *; }
-keepclassmembers class com.x8rgb.data.db.entity.** { *; }

# ── Hilt generated components ────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**

# ── JTransforms ──────────────────────────────────────────────────────────────
-keep class edu.emory.mathcs.jtransforms.** { *; }

# ── Kotlin value classes (RgbColor) ─────────────────────────────────────────
-keep @kotlin.jvm.JvmInline class com.x8rgb.domain.model.RgbColor { *; }
