// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    // Firebase Google Services
    id("com.google.gms.google-services") version "4.4.0" apply false
    // Disabled Hilt - not needed for UI-only implementation
    // id("com.google.dagger.hilt.android") version "2.48" apply false
}
