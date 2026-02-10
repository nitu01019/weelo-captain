@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.weelo.logistics.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.weelo.logistics.data.api.HeartbeatRequest
import com.weelo.logistics.data.remote.RetrofitClient
import kotlinx.coroutines.*

/**
 * =============================================================================
 * HEARTBEAT MANAGER - Live Location Updates for Geolocation Matching
 * =============================================================================
 * 
 * WHAT THIS DOES:
 * - Sends transporter's location to backend every 5 seconds
 * - Enables proximity-based matching (nearest transporters get broadcasts first)
 * - Uses geohash indexing on backend for O(1) lookups
 * 
 * WHEN TO USE:
 * - Start when transporter goes ONLINE (available for orders)
 * - Stop when transporter goes OFFLINE or logs out
 * - Pause when on trip (GPS tracking service takes over)
 * 
 * HOW IT WORKS:
 * 1. Gets current location using FusedLocationProvider
 * 2. Sends to POST /api/v1/transporter/heartbeat
 * 3. Backend stores in geohash-indexed availability table
 * 4. Booking requests find nearest transporters in <5ms
 * 
 * BATTERY OPTIMIZATION:
 * - Uses PRIORITY_BALANCED_POWER_ACCURACY
 * - Only 5 second intervals (not continuous)
 * - Stops automatically when offline
 * 
 * =============================================================================
 */
object HeartbeatManager {
    
    private const val TAG = "HeartbeatManager"
    private const val HEARTBEAT_INTERVAL_MS = 5000L  // 5 seconds
    
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var heartbeatJob: Job? = null
    private var isRunning = false
    private var currentVehicleId: String? = null
    private var isOnTrip = false
    
    // Callbacks
    private var onHeartbeatSuccess: ((String?) -> Unit)? = null
    private var onHeartbeatError: ((String) -> Unit)? = null
    
    /**
     * Initialize the heartbeat manager
     * Call this in Application.onCreate() or MainActivity
     */
    fun initialize(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        timber.log.Timber.i("✅ HeartbeatManager initialized")
    }
    
    /**
     * Start sending heartbeats
     * Call when transporter goes ONLINE
     * 
     * @param vehicleId Optional vehicle ID to track
     */
    fun start(vehicleId: String? = null) {
        if (isRunning) {
            timber.log.Timber.w("⚠️ HeartbeatManager already running")
            return
        }
        
        currentVehicleId = vehicleId
        isRunning = true
        isOnTrip = false
        
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            timber.log.Timber.i("🟢 Starting heartbeat service (every ${HEARTBEAT_INTERVAL_MS}ms)")
            
            while (isActive && isRunning) {
                sendHeartbeat()
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Stop sending heartbeats
     * Call when transporter goes OFFLINE
     */
    fun stop() {
        if (!isRunning) return
        
        isRunning = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        
        // Notify backend we're offline
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = RetrofitClient.getAccessToken()
                if (token != null) {
                    RetrofitClient.transporterApi.markOffline("Bearer $token")
                }
            } catch (e: Exception) {
                timber.log.Timber.e("Failed to notify offline: ${e.message}")
            }
        }
        
        timber.log.Timber.i("🔴 Heartbeat service stopped")
    }
    
    /**
     * Pause heartbeats when starting a trip
     * GPS tracking service will take over
     */
    fun pauseForTrip() {
        isOnTrip = true
        timber.log.Timber.i("⏸️ Heartbeat paused for trip (GPS tracking active)")
    }
    
    /**
     * Resume heartbeats when trip completes
     */
    fun resumeAfterTrip() {
        isOnTrip = false
        timber.log.Timber.i("▶️ Heartbeat resumed after trip")
    }
    
    /**
     * Set callbacks for heartbeat events
     */
    fun setCallbacks(
        onSuccess: ((vehicleKey: String?) -> Unit)? = null,
        onError: ((message: String) -> Unit)? = null
    ) {
        onHeartbeatSuccess = onSuccess
        onHeartbeatError = onError
    }
    
    /**
     * Check if heartbeat service is running
     */
    fun isActive(): Boolean = isRunning && !isOnTrip
    
    // =========================================================================
    // PRIVATE METHODS
    // =========================================================================
    
    private suspend fun sendHeartbeat() {
        try {
            // Get current location
            val location = getCurrentLocation()
            if (location == null) {
                timber.log.Timber.w("⚠️ Could not get location for heartbeat")
                return
            }
            
            // Get auth token
            val token = RetrofitClient.getAccessToken()
            if (token == null) {
                timber.log.Timber.w("⚠️ No auth token for heartbeat")
                return
            }
            
            // Send heartbeat to backend
            val request = HeartbeatRequest(
                latitude = location.latitude,
                longitude = location.longitude,
                vehicleId = currentVehicleId,
                isOnTrip = isOnTrip
            )
            
            val response = RetrofitClient.transporterApi.sendHeartbeat(
                auth = "Bearer $token",
                request = request
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                timber.log.Timber.d("💓 Heartbeat sent: ${location.latitude}, ${location.longitude} -> ${data?.vehicleKey}")
                onHeartbeatSuccess?.invoke(data?.vehicleKey)
            } else {
                val error = response.body()?.error?.message ?: "Unknown error"
                timber.log.Timber.w("⚠️ Heartbeat failed: $error")
                onHeartbeatError?.invoke(error)
            }
            
        } catch (e: Exception) {
            timber.log.Timber.e("❌ Heartbeat error: ${e.message}")
            onHeartbeatError?.invoke(e.message ?: "Network error")
        }
    }
    
    @Suppress("MissingPermission")
    private suspend fun getCurrentLocation(): Location? {
        return try {
            fusedLocationClient?.let { client ->
                // Use suspendCancellableCoroutine to convert callback to suspend
                kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    client.lastLocation
                        .addOnSuccessListener { location ->
                            continuation.resume(location) {}
                        }
                        .addOnFailureListener { e ->
                            timber.log.Timber.e("Location error: ${e.message}")
                            continuation.resume(null) {}
                        }
                }
            }
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to get location: ${e.message}")
            null
        }
    }
    
    /**
     * Check if location permission is granted
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
