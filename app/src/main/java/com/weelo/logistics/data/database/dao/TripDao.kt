package com.weelo.logistics.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.weelo.logistics.data.database.entities.TripEntity
import kotlinx.coroutines.flow.Flow

/**
 * TripDao - Data Access Object for trip operations
 *
 * All operations aresuspend functions (run off main thread).
 * Uses Flow for observable queries (Compose-friendly).
 */
@Dao
interface TripDao {

    /**
     * Get trip by ID
     */
    @Query("SELECT * FROM trips WHERE tripId = :tripId")
    suspend fun getTripById(tripId: String): TripEntity?

    /**
     * Observe trip by ID (for Compose)
     */
    @Query("SELECT * FROM trips WHERE tripId = :tripId")
    fun observeTripById(tripId: String): Flow<TripEntity?>

    /**
     * Get all trips for a specific driver
     */
    @Query("SELECT * FROM trips WHERE driverId = :driverId ORDER BY createdAt DESC")
    suspend fun getTripsByDriver(driverId: String): List<TripEntity>

    /**
     * Observe driver's trips (for Compose)
     */
    @Query("SELECT * FROM trips WHERE driverId = :driverId ORDER BY createdAt DESC")
    fun observeTripsByDriver(driverId: String): Flow<List<TripEntity>>

    /**
     * Get all trips for a specific transporter
     */
    @Query("SELECT * FROM trips WHERE transporterId = :transporterId ORDER BY createdAt DESC")
    suspend fun getTripsByTransporter(transporterId: String): List<TripEntity>

    /**
     * Observe transporter's trips (for Compose)
     */
    @Query("SELECT * FROM trips WHERE transporterId = :transporterId ORDER BY createdAt DESC")
    fun observeTripsByTransporter(transporterId: String): Flow<List<TripEntity>>

    /**
     * Get trip by status
     */
    @Query("SELECT * FROM trips WHERE status = :status")
    suspend fun getTripsByStatus(status: String): List<TripEntity>

    /**
     * Insert or replace trip
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTrip(trip: TripEntity)

    /**
     * Insert or replace multiple trips
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllTrips(trips: List<TripEntity>)

    /**
     * Update trip status
     */
    @Query("UPDATE trips SET status = :status, lastSyncedAt = :timestamp WHERE tripId = :tripId")
    suspend fun updateTripStatus(tripId: String, status: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update trip status AND current leg (for multi-stop trips)
     */
    @Query("UPDATE trips SET status = :status, lastSyncedAt = :timestamp WHERE tripId = :tripId")
    suspend fun updateTripStatusSync(tripId: String, status: String, timestamp: Long)

    /**
     * Save trip to active trips (for current driver)
     */
    @Transaction
    suspend fun saveTripToActive(trip: TripEntity) {
        saveTrip(trip)
        // Note: Trip to ActiveTrip conversion is handled in repository layer
        // This is because ActiveTripEntity has different structure (simplified fields)
    }

    /**
     * Delete trip
     */
    @Query("DELETE FROM trips WHERE tripId = :tripId")
    suspend fun deleteTrip(tripId: String)

    /**
     * Delete old trips (older than given timestamp)
     */
    @Query("DELETE FROM trips WHERE createdAt < :beforeTimestamp")
    suspend fun deleteOldTrips(beforeTimestamp: Long)

    /**
     * Get completed trips count
     */
    @Query("SELECT COUNT(*) FROM trips WHERE status = 'COMPLETED'")
    suspend fun getCompletedTripsCount(): Int

    /**
     * Get total trips for driver
     */
    @Query("SELECT COUNT(*) FROM trips WHERE driverId = :driverId")
    suspend fun getTripCountForDriver(driverId: String): Int

    /**
     * Get last sync time
     */
    @Query("SELECT MAX(lastSyncedAt) FROM trips WHERE driverId = :driverId")
    suspend fun getLastSyncTime(driverId: String): Long?

    /**
     * Mark all trips as stale (when going offline)
     */
    @Query("UPDATE trips SET isOffline = true WHERE driverId = :driverId")
    suspend fun markAllAsStale(driverId: String)

    /**
     * Clear all trips (for logout)
     */
    @Query("DELETE FROM trips")
    suspend fun clearAllTrips()

    /**
     * Get pending updates (trips modified offline)
     */
    @Query("SELECT * FROM trips WHERE isOffline = 1")
    suspend fun getPendingUpdates(): List<TripEntity>

    /**
     * Mark trip as synced
     */
    @Query("UPDATE trips SET isOffline = 0, lastSyncedAt = :timestamp WHERE tripId = :tripId")
    suspend fun markAsSynced(tripId: String, timestamp: Long = System.currentTimeMillis())
}
