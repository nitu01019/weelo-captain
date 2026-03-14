package com.weelo.logistics.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.weelo.logistics.data.database.entities.DriverProfileEntity
import com.weelo.logistics.data.database.entities.WarningEntity
import kotlinx.coroutines.flow.Flow

/**
 * DriverProfileDao - Data Access Object for driver profile operations
 */
@Dao
interface DriverProfileDao {

    /**
     * Get driver profile by ID
     */
    @Query("SELECT * FROM driver_profiles WHERE driverId = :driverId")
    suspend fun getDriverProfile(driverId: String): DriverProfileEntity?

    /**
     * Observe driver profile (for Compose)
     */
    @Query("SELECT * FROM driver_profiles WHERE driverId = :driverId")
    fun observeDriverProfile(driverId: String): Flow<DriverProfileEntity?>

    /**
     * Get all drivers for a transporter
     */
    @Query("SELECT * FROM driver_profiles WHERE transporterId = :transporterId ORDER BY name ASC")
    suspend fun getDriversByTransporter(transporterId: String): List<DriverProfileEntity>

    /**
     * Observe transporter's drivers (for Compose)
     */
    @Query("SELECT * FROM driver_profiles WHERE transporterId = :transporterId ORDER BY name ASC")
    fun observeDriversByTransporter(transporterId: String): Flow<List<DriverProfileEntity>>

    /**
     * Get available drivers for a transporter
     */
    @Query("SELECT * FROM driver_profiles WHERE transporterId = :transporterId AND isAvailable = 1")
    suspend fun getAvailableDrivers(transporterId: String): List<DriverProfileEntity>

    /**
     * Insert or replace driver profile
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDriverProfile(driver: DriverProfileEntity)

    /**
     * Insert or replace multiple driver profiles
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllDriverProfiles(drivers: List<DriverProfileEntity>)

    /**
     * Update driver availability status
     */
    @Query("UPDATE driver_profiles SET isAvailable = :isAvailable WHERE driverId = :driverId")
    suspend fun updateDriverAvailability(driverId: String, isAvailable: Boolean)

    /**
     * Update driver status
     */
    @Query("UPDATE driver_profiles SET status = :status WHERE driverId = :driverId")
    suspend fun updateDriverStatus(driverId: String, status: String)

    /**
     * Update assigned vehicle
     */
    @Query("UPDATE driver_profiles SET assignedVehicleId = :vehicleId WHERE driverId = :driverId")
    suspend fun updateAssignedVehicle(driverId: String, vehicleId: String?)

    /**
     * Update driver rating
     */
    @Query("UPDATE driver_profiles SET rating = :rating, totalTrips = :totalTrips WHERE driverId = :driverId")
    suspend fun updateDriverRating(driverId: String, rating: Float, totalTrips: Int)

    /**
     * Delete driver profile
     */
    @Query("DELETE FROM driver_profiles WHERE driverId = :driverId")
    suspend fun deleteDriverProfile(driverId: String)

    /**
     * Clear all driver profiles (for logout)
     */
    @Query("DELETE FROM driver_profiles")
    suspend fun clearAllDriverProfiles()

    /**
     * Get driver profile statistics
     */
    @Transaction
    suspend fun getDriverProfileStats(driverId: String): DriverProfileStats? {
        val driver = getDriverProfile(driverId) ?: return null
        val warnings = getWarningsForDriverCount(driverId)
        return DriverProfileStats(
            rating = driver.rating,
            totalTrips = driver.totalTrips,
            warningCount = warnings
        )
    }

    /**
     * Get warnings count for a driver (helper for stats)
     */
    @Query("SELECT COUNT(*) FROM warnings WHERE driverId = :driverId")
    suspend fun getWarningsForDriverCount(driverId: String): Int
}

/**
 * Driver profile statistics summary
 */
data class DriverProfileStats(
    val rating: Float,
    val totalTrips: Int,
    val warningCount: Int
)
