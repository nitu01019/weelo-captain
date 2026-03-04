package com.weelo.logistics.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * =============================================================================
 * LOCATION SETTINGS WATCHER
 * =============================================================================
 *
 * Monitors GPS provider state (enabled/disabled) in real-time.
 * Fires immediately when the transporter toggles GPS off in system settings,
 * so AvailabilityManager can warn or force-offline.
 *
 * LIFECYCLE:
 *   - start() when transporter goes ONLINE
 *   - stop() when transporter goes OFFLINE
 *   - No battery impact when stopped
 *
 * DESIGN:
 *   - Uses LocationManager.requestLocationUpdates with MIN_TIME/MIN_DISTANCE = 0
 *     to receive onProviderEnabled/onProviderDisabled callbacks instantly
 *   - Actual location fixes are ignored (heartbeat handles that)
 *   - Singleton pattern matches HeartbeatManager
 *
 * =============================================================================
 */
class LocationSettingsWatcher private constructor(
    private val appContext: Context
) {
    private val locationManager: LocationManager? by lazy {
        appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private val _isGpsEnabled = MutableStateFlow(checkGpsEnabled())
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

    @Volatile
    private var isWatching = false

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // No-op — we only care about provider state changes.
            // Actual location updates are handled by HeartbeatManager.
        }

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _isGpsEnabled.value = true
                timber.log.Timber.i("📡 GPS enabled — broadcasts can be received")
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _isGpsEnabled.value = false
                timber.log.Timber.w("⚠️ GPS disabled — transporter cannot receive accurate location-based broadcasts")
            }
        }

        @Deprecated("Deprecated in API 29, but required for older devices")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            // Required override for pre-API-29 compatibility
        }
    }

    /**
     * Start monitoring GPS provider state.
     * Call when transporter goes ONLINE.
     * Safe to call multiple times — idempotent.
     */
    fun start() {
        if (isWatching) return

        // Refresh initial state
        _isGpsEnabled.value = checkGpsEnabled()

        if (!hasLocationPermission()) {
            timber.log.Timber.w("⚠️ LocationSettingsWatcher: no location permission, skipping")
            return
        }

        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,          // minTimeMs = 0 → immediate provider state callbacks
                0f,          // minDistanceM = 0 → we don't use actual location fixes
                gpsListener,
                Looper.getMainLooper()
            )
            isWatching = true
            timber.log.Timber.i("✅ LocationSettingsWatcher started")
        } catch (e: SecurityException) {
            timber.log.Timber.w("⚠️ LocationSettingsWatcher start failed: ${e.message}")
            _isGpsEnabled.value = false
        } catch (e: IllegalArgumentException) {
            // GPS_PROVIDER not available on device (rare — emulators etc.)
            timber.log.Timber.w("⚠️ GPS provider not available: ${e.message}")
            _isGpsEnabled.value = false
        }
    }

    /**
     * Stop monitoring GPS provider state.
     * Call when transporter goes OFFLINE.
     * Safe to call multiple times — idempotent.
     */
    fun stop() {
        if (!isWatching) return

        try {
            locationManager?.removeUpdates(gpsListener)
        } catch (_: Exception) {
            // Ignore — best-effort cleanup
        }
        isWatching = false
        timber.log.Timber.i("🛑 LocationSettingsWatcher stopped")
    }

    /**
     * Check current GPS provider state.
     * Used for initial state and by AvailabilityManager.isGpsActuallyEnabled().
     */
    fun checkGpsEnabled(): Boolean {
        return try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        @Volatile
        private var instance: LocationSettingsWatcher? = null

        fun getInstance(context: Context): LocationSettingsWatcher {
            return instance ?: synchronized(this) {
                instance ?: LocationSettingsWatcher(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
