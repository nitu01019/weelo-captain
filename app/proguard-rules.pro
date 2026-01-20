# ==============================================================================
# WEELO CAPTAIN APP - PROGUARD RULES
# ==============================================================================
# 
# These rules configure code shrinking, obfuscation, and optimization
# for the release build to:
# 1. Make reverse engineering harder (security)
# 2. Reduce APK size
# 3. Improve runtime performance
#
# For more details, see:
#   https://developer.android.com/build/shrink-code
# ==============================================================================

# ==============================================================================
# SECURITY SETTINGS
# ==============================================================================

# Remove all logging in release builds (security + performance)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Remove println statements
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Obfuscate everything except what's explicitly kept
-repackageclasses 'w'
-allowaccessmodification

# ==============================================================================
# DEBUG INFORMATION (for crash reports)
# ==============================================================================

# Keep line numbers for crash reporting (but hide source file names)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ==============================================================================
# DATA MODELS (Required for Gson/Retrofit serialization)
# ==============================================================================

# Keep all data model classes
-keep class com.weelo.logistics.data.model.** { *; }
-keep class com.weelo.logistics.data.remote.dto.** { *; }

# ==============================================================================
# GSON CONFIGURATION
# ==============================================================================

-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Gson specific classes
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.examples.android.model.** { <fields>; }

# Application classes that will be serialized/deserialized over Gson
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @Expose fields)
-keep class * extends com.google.gson.TypeAdapter

# ==============================================================================
# RETROFIT CONFIGURATION
# ==============================================================================

-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retrofit does reflection on generic parameters. InnerClasses is required to use Signature and
# EnclosingMethod is required to use InnerClasses.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# Top-level functions that can only be used by Kotlin.
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ==============================================================================
# OKHTTP CONFIGURATION
# ==============================================================================

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==============================================================================
# KOTLIN COROUTINES
# ==============================================================================

# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Most of volatile fields are updated with AFU and should not be mangled
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==============================================================================
# ANDROID SECURITY CRYPTO (for EncryptedSharedPreferences)
# ==============================================================================

-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }

# ==============================================================================
# COMPOSE (if using Jetpack Compose)
# ==============================================================================

-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ==============================================================================
# GOOGLE PLAY SERVICES (Maps, Location)
# ==============================================================================

-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
