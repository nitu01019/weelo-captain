package com.weelo.logistics.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.weelo.logistics.data.database.entities.ActiveTripEntity
import kotlinx.coroutines.flow.Flow

/**
 * ActiveTripDao - Data Access Object for the driver's currently active trip
 *
 * CRITICAL FOR CRASH RECOVERY:
 * This table stores the driver's single active trip.
 * On app restart, the app can query this table and restore the active trip.
 */
@Dao
interface ActiveTripDao {

    /**
     * Get active trip by driver ID
     * Returns the driver's currently active trip, or null if none.
     */
    @Query("SELECT * FROM active_trips WHERE driverId = :driverId")
    suspend fun getActiveTripEntity(driverId: String): ActiveTripEntity?

    /**
     * Observe active trip (for Compose)
     */
    @Query("SELECT * FROM active_trips WHERE driverId = :driverId")
    fun observeActiveTrip(driverId: String): Flow<ActiveTripEntity?>

    /**
     * Get active trip by trip ID
     */
    @Query("SELECT * FROM active_trips WHERE tripId = :tripId")
    suspend fun getActiveTripByTripId(tripId: String): ActiveTripEntity?

    /**
     * Save or update active trip
     *
     * IMPORTANT: Uses REPLACE so there's always only ONE active trip per driver.
     * When a driver gets a new trip, it replaces the old one (which should be completed).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveTrip(trip: ActiveTripEntity)

    /**
     * Remove active trip (when trip is completed or cancelled)
     */
    @Query("DELETE FROM active_trips WHERE driverId = :driverId")
    suspend fun clearActiveTrip(driverId: String)

    /**
     * Remove active trip by trip ID
     */
    @Query("DELETE FROM active_trips WHERE tripId = :tripId")
    suspend fun clearActiveTripByTripId(tripId: String)

    /**
     * Update trip status
     */
    @Query("UPDATE active_trips SET status = :status, lastSyncedAt = :timestamp WHERE tripId = :tripId")
    suspend fun updateStatus(tripId: String, status: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update trip progress (current leg)
     */
    @Query("UPDATE active_trips SET currentLeg = :currentLeg, lastSyncedAt = :timestamp WHERE tripId = :tripId")
    suspend fun updateProgress(tripId: String, currentLeg: Int, timestamp: Long = System.currentTimeMillis())

    /**
     * Update status and progress together (for multi-stop trips)
     */
    @Query("UPDATE active_trips SET status = :status, currentLeg = :currentLeg, lastSyncedAt = :timestamp WHERE tripId = :tripId")
    suspend fun updateStatusAndProgress(
        tripId: String,
        status: String,
        currentLeg: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Update last known location
     */
    @Query("UPDATE active_trips SET lastKnownLatitude = :lat, lastKnownLongitude = :lng, lastKnownLocationTime = :timestamp WHERE tripId = :tripId")
    suspend fun updateLocation(tripId: String, lat: Double, lng: Double, timestamp: Long = System.currentTimeMillis())

    /**
     * Mark trip as started
     */
    @Query("UPDATE active_trips SET startedAt = :timestamp, status = 'ACCEPTED' WHERE tripId = :tripId")
    suspend fun markTripStarted(tripId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Check if a trip is currently active
     */
    @Query("SELECT COUNT(*) FROM active_trips WHERE tripId = :tripId")
    suspend fun isActiveTrip(tripId: String): Int

    /**
     * Check if a driver has any active trip
     */
    @Query("SELECT COUNT(*) FROM active_trips WHERE driverId = :driverId")
    suspend fun hasActiveTrip(driverId: String): Boolean

    /**
     * Get all active trips (for debugging)
     */
    @Query("SELECT * FROM active_trips ORDER BY createdAt DESC")
    suspend fun getAllActiveTrips(): List<ActiveTripEntity>

    /**
     * Clear all active trips (for data reset/logout)
     */
    @Query("DELETE FROM active_trips")
    suspend fun clearAllActiveTrips()

    /**
     * Mark trip as stale (when offline and data might be outdated)
     */
    @Query("UPDATE active_trips SET isStale = 1 WHERE tripId = :tripId")
    suspend fun markAsStale(tripId: String)

    /**
     * Mark trip as fresh (when synced with backend)
     */
    @Query("UPDATE active_trips SET isStale = 0 WHERE tripId = :tripId")
    suspend fun markAsFresh(tripId: String)

    /**
     * Get active trips with sync status (for sync coordinator)
     */
    @Query("SELECT * FROM active_trips WHERE isStale = 1")
    suspend fun getStaleTrips(): List<ActiveTripEntity>

    /**
     * Get last sync time for active trip
     */
    @Query("SELECT lastSyncedAt FROM active_trips WHERE driverId = :driverId")
    suspend fun getLastSyncTime(driverId: String): Long?

    /**
     * Get active trips for multiple drivers (batch query)
     */
    @Query("SELECT * FROM active_trips WHERE driverId IN (:driverIds)")
    suspend fun getActiveTripsByDriverIds(driverIds: List<String>): List<ActiveTripEntity>

    /**
     * Save or update multiple active trips
     * Uses REPLACE for each - each driver can have only one active trip
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllActiveTrips(trips: List<ActiveTripEntity>)

    /**
     * Get active trip with vehicle details (transaction)
     */
    @Transaction
    suspend fun getActiveTripForDriver(driverId: String): ActiveTripData? {
        val trip = getActiveTripEntity(driverId) ?: return null
        return ActiveTripData(
            trip = trip,
            isStale = trip.isStale,
            lastSyncedAt = trip.lastSyncedAt
        )
    }
}

/**
 * Active trip data wrapper
 */
data class ActiveTripData(
    val trip: ActiveTripEntity,
    val isStale: Boolean,
    val lastSyncedAt: Long
)
