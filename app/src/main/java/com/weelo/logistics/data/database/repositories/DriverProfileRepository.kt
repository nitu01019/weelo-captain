package com.weelo.logistics.data.database.repositories

import com.weelo.logistics.data.database.dao.DriverProfileDao
import com.weelo.logistics.data.database.entities.DriverProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * DriverProfileRepository - Repository for driver profile operations
 *
 * Provides clean API for driver profile data with Room persistence.
 * Does NOT modify any existing driver API calls - only adds local caching.
 */
class DriverProfileRepository(
    private val driverProfileDao: DriverProfileDao
) {

    /**
     * Get driver profile by ID
     */
    suspend fun getDriverProfile(driverId: String): DriverProfileEntity? = withContext(Dispatchers.IO) {
        driverProfileDao.getDriverProfile(driverId)
    }

    /**
     * Observe driver profile (for reactive UI)
     */
    fun observeDriverProfile(driverId: String): Flow<DriverProfileEntity?> {
        return driverProfileDao.observeDriverProfile(driverId)
    }

    /**
     * Get all drivers for a transporter
     */
    suspend fun getDriversByTransporter(transporterId: String): List<DriverProfileEntity> = withContext(Dispatchers.IO) {
        driverProfileDao.getDriversByTransporter(transporterId)
    }

    /**
     * Observe transporter's drivers
     */
    fun observeDriversByTransporter(transporterId: String): Flow<List<DriverProfileEntity>> {
        return driverProfileDao.observeDriversByTransporter(transporterId)
    }

    /**
     * Get available drivers
     */
    suspend fun getAvailableDrivers(transporterId: String): List<DriverProfileEntity> = withContext(Dispatchers.IO) {
        driverProfileDao.getAvailableDrivers(transporterId)
    }

    /**
     * Save driver profile (upsert pattern)
     */
    suspend fun saveDriverProfile(driver: DriverProfileEntity) = withContext(Dispatchers.IO) {
        driverProfileDao.saveDriverProfile(driver)
    }

    /**
     * Save multiple drivers atomically
     */
    suspend fun saveAllDriverProfiles(drivers: List<DriverProfileEntity>) = withContext(Dispatchers.IO) {
        driverProfileDao.saveAllDriverProfiles(drivers)
    }

    /**
     * Update driver availability
     */
    suspend fun updateDriverAvailability(driverId: String, isAvailable: Boolean) = withContext(Dispatchers.IO) {
        driverProfileDao.updateDriverAvailability(driverId, isAvailable)
    }

    /**
     * Update driver status
     */
    suspend fun updateDriverStatus(driverId: String, status: String) = withContext(Dispatchers.IO) {
        driverProfileDao.updateDriverStatus(driverId, status)
    }

    /**
     * Update assigned vehicle
     */
    suspend fun updateAssignedVehicle(driverId: String, vehicleId: String?) = withContext(Dispatchers.IO) {
        driverProfileDao.updateAssignedVehicle(driverId, vehicleId)
    }

    /**
     * Update driver rating
     */
    suspend fun updateDriverRating(driverId: String, rating: Float, totalTrips: Int) = withContext(Dispatchers.IO) {
        driverProfileDao.updateDriverRating(driverId, rating, totalTrips)
    }

    /**
     * Delete driver profile
     */
    suspend fun deleteDriverProfile(driverId: String) = withContext(Dispatchers.IO) {
        driverProfileDao.deleteDriverProfile(driverId)
    }

    /**
     * Get driver statistics
     */
    suspend fun getDriverStats(driverId: String) = withContext(Dispatchers.IO) {
        driverProfileDao.getDriverProfileStats(driverId)
    }

    /**
     * Clear all driver profiles (for logout or data reset)
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        driverProfileDao.clearAllDriverProfiles()
    }
}
