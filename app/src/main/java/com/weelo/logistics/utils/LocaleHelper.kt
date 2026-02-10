package com.weelo.logistics.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.*

/**
 * LocaleHelper - Handle app language changes
 * 
 * This ensures the app displays in the user's selected language
 * Production-ready, scalable implementation
 * 
 * Usage:
 * - Call setLocale() when language preference is loaded
 * - Call in Application.onCreate() and each Activity.attachBaseContext()
 */
object LocaleHelper {
    
    /**
     * Set app locale based on language code
     * Call this in Application.onCreate() and Activity.attachBaseContext()
     * 
     * @param context Application or Activity context
     * @param languageCode ISO 639-1 code (en, hi, ta, etc.)
     * @return Updated context with new locale
     */
    fun setLocale(context: Context, languageCode: String): Context {
        return updateResources(context, languageCode)
    }
    
    /**
     * Update configuration with new locale
     */
    private fun updateResources(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
            context
        }
    }
    
    /**
     * Get current app locale
     */
    fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }
    
    /**
     * Supported language codes
     */
    val SUPPORTED_LANGUAGES = setOf(
        "en", // English
        "hi", // Hindi
        "ta", // Tamil
        "te", // Telugu
        "ml", // Malayalam
        "kn", // Kannada
        "mr", // Marathi
        "gu", // Gujarati
        "bn", // Bengali
        "pa", // Punjabi
        "or", // Odia
        "raj" // Rajasthani
    )
}
