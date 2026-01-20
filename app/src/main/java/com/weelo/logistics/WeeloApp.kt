package com.weelo.logistics

import android.app.Application
import com.weelo.logistics.data.remote.RetrofitClient
// import dagger.hilt.android.HiltAndroidApp

/**
 * Weelo Application Class
 * Entry point for the application
 * 
 * Initializes:
 * - RetrofitClient for API calls
 * - Secure token storage
 */
// @HiltAndroidApp
class WeeloApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize RetrofitClient with application context
        // This sets up secure token storage and API client
        RetrofitClient.init(this)
    }
}
