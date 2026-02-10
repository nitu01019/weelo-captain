# Weelo Captain - ProGuard Rules for Production
# Optimizes APK size and performance

# Keep application classes
-keep class com.weelo.logistics.** { *; }

# Retrofit & OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }

# Coil (Image Loading)
-keep class coil.** { *; }

# Kotlin Coroutines
-keepclassmembernames class kotlinx.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Optimize code
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Enable aggressive optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-allowaccessmodification
-repackageclasses ''
