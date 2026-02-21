import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
    // Firebase
    id("com.google.gms.google-services")
    // Hilt temporarily disabled for build
    // id("com.google.dagger.hilt.android") version "2.48"
    // kotlin("kapt")
}

android {
    namespace = "com.weelo.logistics"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.weelo.captain"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Load Maps key from local.properties (preferred) or environment (CI).
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        val mapsApiKey = localProperties.getProperty("MAPS_API_KEY")
            ?: System.getenv("MAPS_API_KEY")
            ?: ""
        manifestPlaceholders += mapOf("MAPS_API_KEY" to mapsApiKey)
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    buildTypes {
        debug {
            // Debug build - development settings
            isMinifyEnabled = false
            isDebuggable = true
            
            // Debug suffix for package name (allows both debug and release on same device)
            // applicationIdSuffix = ".debug"  // Removed to keep consistent package name for API keys
            // versionNameSuffix = "-debug"
        }
        
        release {
            // =================================================================
            // RELEASE BUILD - PRODUCTION SECURITY SETTINGS
            // =================================================================
            // SECURITY: Enable code shrinking and obfuscation
            // This makes reverse engineering much harder
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // TODO: Add signing config for release
            // signingConfig = signingConfigs.getByName("release")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
    
    // Performance optimizations
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Treat all warnings as warnings (not errors)
        abortOnError = false
        warningsAsErrors = false
        // Disable non-actionable checks
        disable += setOf(
            "UnusedResources",       // XML resources kept for future use / theming
            "GradleDependency",      // Dependency upgrades tracked separately
            "IconLocation",          // PNGs moved to drawable-nodpi (acceptable)
            "IconLauncherShape",     // Launcher icons are correct for brand
            "IconDuplicates",        // Intentional same-icon across densities
            "OldTargetApi",          // targetSdk upgrade tracked separately
            "AppBundleLocaleChanges" // Play Core lib not needed for sideloaded APKs
        )
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // ⚡ Splash Screen API - Eliminates cold start white screen
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    
    // Hilt for Dependency Injection - Temporarily disabled
    // implementation("com.google.dagger:hilt-android:2.48")
    // kapt("com.google.dagger:hilt-android-compiler:2.48")
    // implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Room Database (Disabled - using mock data for now)
    // implementation("androidx.room:room-runtime:2.6.1")
    // implementation("androidx.room:room-ktx:2.6.1")
    // kapt("androidx.room:room-compiler:2.6.1")
    
    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Gson for JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Google Maps (for future GPS tracking)
    // Google Maps & Location Services
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")  // Updated to match customer app
    implementation("com.google.android.gms:play-services-auth:21.5.0")      // SMS Retriever API (zero-permission OTP auto-read)
    implementation("com.google.android.gms:play-services-auth-api-phone:18.1.0") // Phone number hint — 18.3.0 requires Kotlin 2.1 metadata (incompatible with this project's Kotlin 1.9)
    implementation("com.google.maps.android:maps-compose:4.3.0")            // Compose Maps for embedded maps
    implementation("com.google.maps.android:android-maps-utils:3.8.2")      // Map utilities (markers, clustering)
    
    // Kotlin coroutines support for Google Play Services
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // Coil for Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // CameraX for camera capture
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    
    // Security - Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Retrofit (prepared for future backend integration)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // OkHttp logging interceptor for debugging
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Timber - Production-safe logging (logs stripped in release builds)
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // Socket.IO for real-time communication
    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "org.json", module = "json")
    }
    
    // Firebase for Push Notifications
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// kapt {
//     correctErrorTypes = true
// }
