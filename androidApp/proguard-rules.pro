# ============================================================================
# Music Assistant Client — R8 / ProGuard rules
# ============================================================================
# Defaults from proguard-android-optimize.txt are applied first (see build.gradle.kts).
# Keep this file focused on the reflective / ServiceLoader / JNI surfaces that R8
# cannot see through.

# Strip Kermit/log call sites that aren't useful in release crashes.
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**

# Source file / line numbers — keep stack traces useful after obfuscation.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Kotlinx serialization
# ---------------------------------------------------------------------------
# kotlinx.serialization generates $$serializer companions discovered by name.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# All @Serializable data classes in the app — keep names + members so polymorphic
# discriminators and field names survive.
-keep @kotlinx.serialization.Serializable class ** { *; }

# ---------------------------------------------------------------------------
# Ktor client
# ---------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# OkHttp engine (Android) — reflective platform detection.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Ktor WebRTC binding — JNI + reflective bridge to native WebRTC.
-keep class io.ktor.client.webrtc.** { *; }
-keep class org.webrtc.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---------------------------------------------------------------------------
# Coil 3 — ServiceLoader-discovered decoders / fetchers.
# ---------------------------------------------------------------------------
-keep class coil3.** { *; }
-keepclassmembers class coil3.** { *; }
-dontwarn coil3.**

# ---------------------------------------------------------------------------
# Concentus (Opus codec) — pure-Java but reflective entry points.
# ---------------------------------------------------------------------------
-keep class org.concentus.** { *; }
-dontwarn org.concentus.**

# ---------------------------------------------------------------------------
# kmpalette — operates on android.graphics.Bitmap via platform glue.
# ---------------------------------------------------------------------------
-keep class com.kmpalette.** { *; }
-dontwarn com.kmpalette.**

# ---------------------------------------------------------------------------
# AndroidX Media (MediaSessionCompat, MediaBrowserServiceCompat).
# ---------------------------------------------------------------------------
-keep class androidx.media.** { *; }
-keep class android.support.v4.media.** { *; }
-dontwarn androidx.media.**

# ---------------------------------------------------------------------------
# App entry points referenced by AndroidManifest.xml.
# ---------------------------------------------------------------------------
-keep class io.music_assistant.client.MyApplication { *; }
-keep class io.music_assistant.client.MainActivity { *; }
-keep class io.music_assistant.client.VoicePlayDispatchActivity { *; }
-keep class io.music_assistant.client.services.AndroidAutoPlaybackService { *; }
-keep class io.music_assistant.client.services.MainMediaPlaybackService { *; }

# Koin — modules are referenced by lambda, but ViewModels are created
# reflectively via constructor injection. Keep public constructors of
# everything Koin builds.
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep class io.music_assistant.client.**.*ViewModel { *; }

# ---------------------------------------------------------------------------
# easyqrscan / CameraX / ML Kit barcode scanning.
# CameraX discovers configs (Camera2Config, etc.) via metadata + reflection,
# and ML Kit barcode-scanning loads detectors reflectively. Without keeping
# these, the QR Composable NPEs during AndroidView attach.
# ---------------------------------------------------------------------------
-keep class io.github.kalinjul.easyqrscan.** { *; }
-dontwarn io.github.kalinjul.easyqrscan.**

-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }
-keepclassmembers class androidx.camera.** { *; }
-dontwarn androidx.camera.**

-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keepclassmembers class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

-keep class com.google.android.odml.** { *; }
-dontwarn com.google.android.odml.**

# Guava ListenableFuture stub — used across CameraX/ML Kit async APIs.
-dontwarn com.google.common.util.concurrent.**
-keep class com.google.common.util.concurrent.ListenableFuture { *; }

# ---------------------------------------------------------------------------
# Compose — defaults handle most of it. Keep @Composable signatures intact
# so tooling/preview-only paths don't get stripped when reachable.
# ---------------------------------------------------------------------------
-keep class androidx.compose.runtime.Composer { *; }
-dontwarn androidx.compose.**

# ---------------------------------------------------------------------------
# Multiplatform settings (settings-multiplatform).
# ---------------------------------------------------------------------------
-keep class com.russhwolf.settings.** { *; }
-dontwarn com.russhwolf.settings.**
