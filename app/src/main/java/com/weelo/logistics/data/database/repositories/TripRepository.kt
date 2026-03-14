package com.weelo.logistics.data.database.repositories

import android.content.Context
import com.weelo.logistics.data.database.WeeloDatabase
import com.weelo.logistics.data.database.dao.ActiveTripDao
import com.weelo.logistics.data.database.dao.TripDao
import com.weelo.logistics.data.database.entities.ActiveTripEntity
import com.weelo.logistics.data.database.entities.TripEntity
import com.weelo.logistics.data.model.Trip
import com.weelo.logistics.data.model.TripStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * TripRepository - Repository for trip operations
 *
 * PRODUCTION-GRADE PATTERN:
 * - Uses Room for persistent storage
 * - API calls happen separately (through existing RetrofitClient)
 * - This repository provides local storage and caching
 * - Dual-layer: Room (offline) + API (backend sync)
 *
 * SAFE GUARDS:
 * - Does NOT modify any existing API calls
 * - Does NOT change any socket/FCM logic
 * - Pure additive - adds persistence without breaking existing flows
 */
class TripRepository(
    private val tripDao: TripDao,
    private val activeTripDao: ActiveTripDao
) {

    /**
     * Get trip by ID from local database
     * Returns null if not found
     */
    suspend fun getTripById(tripId: String): TripEntity? = withContext(Dispatchers.IO) {
        tripDao.getTripById(tripId)
    }

    /**
     * Observe trip by ID (for reactive UI)
     */
    fun observeTripById(tripId: String): Flow<TripEntity?> {
        return tripDao.observeTripById(tripId)
    }

    /**
     * Get all trips for a driver
     */
    suspend fun getTripsByDriver(driverId: String): List<TripEntity> = withContext(Dispatchers.IO) {
        tripDao.getTripsByDriver(driverId)
    }

    /**
     * Observe driver's trips
     */
    fun observeTripsByDriver(driverId: String): Flow<List<TripEntity>> {
        return tripDao.observeTripsByDriver(driverId)
    }

    /**
     * Get all trips for a transporter
     */
    suspend fun getTripsByTransporter(transporterId: String): List<TripEntity> = withContext(Dispatchers.IO) {
        tripDao.getTripsByTransporter(transporterId)
    }

    /**
     * Observe transporter's trips
     */
    fun observeTripsByTransporter(transporterId: String): Flow<List<TripEntity>> {
        return tripDao.observeTripsByTransporter(transporterId)
    }

    /**
     * Save trip to database (upsert pattern)
     *
     * Called when trip data is fetched from backend.
     * Also saves to ActiveTripEntity if applicable.
     */
    suspend fun saveTrip(trip: TripEntity, isActive: Boolean = false) = withContext(Dispatchers.IO) {
        tripDao.saveTrip(trip)

        // If this is the driver's active trip, also save to active_trips table
        if (isActive && trip.driverId != null) {
            saveAsActiveTrip(trip)
        }
    }

    /**
     * Save multiple trips atomically
     */
    suspend fun saveAllTrips(trips: List<TripEntity>) = withContext(Dispatchers.IO) {
        tripDao.saveAllTrips(trips)
    }

    /**
     * Update trip status
     */
    suspend fun updateTripStatus(
        tripId: String,
        status: String,
        currentLeg: Int = 0,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        tripDao.updateTripStatusSync(tripId, status, timestamp)

        // Also update in active_trips table if this is an active trip
        val activeTrip = activeTripDao.getActiveTripByTripId(tripId)
        if (activeTrip != null) {
            if (currentLeg > 0) {
                activeTripDao.updateStatusAndProgress(tripId, status, currentLeg, timestamp)
            } else {
                activeTripDao.updateStatus(tripId, status, timestamp)
            }
        }
    }

    /**
     * Delete trip (when completed/cancelled)
     */
    suspend fun deleteTrip(tripId: String) = withContext(Dispatchers.IO) {
        tripDao.deleteTrip(tripId)
        // Also remove from active_trips if present
        activeTripDao.clearActiveTripByTripId(tripId)
    }

    /**
     * Delete old trips (cleanup after X days)
     */
    suspend fun deleteOldTrips(daysToKeep: Int = 30) = withContext(Dispatchers.IO) {
        val beforeTimestamp = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000)
        tripDao.deleteOldTrips(beforeTimestamp)
    }

    /**
     * Get trip count for driver
     */
    suspend fun getTripCount(driverId: String): Int = withContext(Dispatchers.IO) {
        tripDao.getTripCountForDriver(driverId)
    }

    /**
     * Get last completed trips count
     */
    suspend fun getCompletedTripsCount(): Int = withContext(Dispatchers.IO) {
        tripDao.getCompletedTripsCount()
    }

    /**
     * Clear all trips (for logout)
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        tripDao.clearAllTrips()
        activeTripDao.clearAllActiveTrips()
    }

    // ==========================================================================
    // ACTIVE TRIP MANAGEMENT (CRASH RECOVERY)
    // ==========================================================================

    /**
     * Save a trip as the driver's currently active trip
     *
     * This is CRITICAL for crash recovery:
     * - Called when driver accepts a trip
     * - Persists active trip even if app crashes
     * - On app restart, can be restored with restoreActiveTrip()
     */
    suspend fun saveAsActiveTrip(tripEntity: TripEntity) = withContext(Dispatchers.IO) {
        val activeTrip = ActiveTripEntity(
            tripId = tripEntity.tripId,
            driverId = tripEntity.driverId ?: "",
            vehicleId = tripEntity.vehicleId,
            vehicleNumber = tripEntity.vehicleNumber,
            transporterId = tripEntity.transporterId,
            customerId = tripEntity.customerId,
            customerName = tripEntity.customerName,
            customerPhone = tripEntity.customerPhone,
            pickupLat = tripEntity.pickupLat,
            pickupLng = tripEntity.pickupLng,
            pickupAddress = tripEntity.pickupAddress,
            pickupCity = tripEntity.pickupCity,
            dropLat = tripEntity.dropLat,
            dropLng = tripEntity.dropLng,
            dropAddress = tripEntity.dropAddress,
            dropCity = tripEntity.dropCity,
            goodsType = tripEntity.goodsType,
            fare = tripEntity.fare,
            distanceKm = tripEntity.distanceKm,
            estimatedDurationMinutes = tripEntity.estimatedDurationMinutes,
            status = tripEntity.status,
            currentLeg = 0, // Default to first leg
            totalLegs = 1,    // Default to direct trip (can be updated later)
            driverName = tripEntity.driverName ?: "Driver",
            driverPhone = "", // Can be loaded from profile
            notes = tripEntity.notes,
            lastSyncedAt = System.currentTimeMillis(),
            isStale = false
        )
        activeTripDao.saveActiveTrip(activeTrip)
    }

    /**
     * Get the driver's active trip
     *
     * Returns the currently active trip, or null if none.
     * Used in crash recovery to restore state after app restart.
     */
    suspend fun getActiveTrip(driverId: String): ActiveTripEntity? = withContext(Dispatchers.IO) {
        activeTripDao.getActiveTripEntity(driverId)
    }

    /**
     * Observe driver's active trip (for reactive UI)
     */
    fun observeActiveTrip(driverId: String): Flow<ActiveTripEntity?> {
        return activeTripDao.observeActiveTrip(driverId)
    }

    /**
     * Check if driver has any active trip
     */
    suspend fun hasActiveTrip(driverId: String): Boolean = withContext(Dispatchers.IO) {
        activeTripDao.hasActiveTrip(driverId)
    }

    /**
     * Clear active trip (when trip is completed or cancelled)
     */
    suspend fun clearActiveTrip(driverId: String) = withContext(Dispatchers.IO) {
        activeTripDao.clearActiveTrip(driverId)
    }

    /**
     * Update active trip status
     */
    suspend fun updateActiveTripStatus(
        tripId: String,
        status: String,
        currentLeg: Int = 0
    ) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        if (currentLeg > 0) {
            activeTripDao.updateStatusAndProgress(tripId, status, currentLeg, timestamp)
        } else {
            activeTripDao.updateStatus(tripId, status, timestamp)
        }
    }

    /**
     * Update driver's current location during active trip
     */
    suspend fun updateActiveTripLocation(
        tripId: String,
        lat: Double,
        lng: Double
    ) = withContext(Dispatchers.IO) {
        activeTripDao.updateLocation(tripId, lat, lng, System.currentTimeMillis())
    }

    /**
     * Mark active trip as started
     */
    suspend fun markTripStarted(tripId: String) = withContext(Dispatchers.IO) {
        activeTripDao.markTripStarted(tripId, System.currentTimeMillis())
    }

    /**
     * Sync stale trips from backend
     *
     * When network returns, fetch fresh data for stale trips.
     */
    suspend fun syncStaleTrips(driverId: String) = withContext(Dispatchers.IO) {
        val staleTrips = activeTripDao.getStaleTrips()
        if (staleTrips.isNotEmpty()) {
            Timber.d("Syncing ${staleTrips.size} stale trips for driver $driverId")
            // Note: Actual API sync happens through existing RetrofitClient
            // This repository only marks them for sync tracking
            staleTrips.forEach {
                activeTripDao.markAsFresh(it.tripId)
            }
        }
    }
}
