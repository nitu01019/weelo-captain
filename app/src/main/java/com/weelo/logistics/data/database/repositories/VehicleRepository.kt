package com.weelo.logistics.data.database.repositories

import com.weelo.logistics.data.database.dao.VehicleDao
import com.weelo.logistics.data.database.entities.VehicleEntity
import com.weelo.logistics.data.database.dao.VehicleStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * VehicleRepository - Repository for vehicle operations
 *
 * Provides clean API for vehicle data with Room persistence.
 * Does NOT modify any existing vehicle API calls - only adds local caching.
 */
class VehicleRepository(
    private val vehicleDao: VehicleDao
) {

    /**
     * Get vehicle by ID
     */
    suspend fun getVehicle(vehicleId: String): VehicleEntity? = withContext(Dispatchers.IO) {
        vehicleDao.getVehicle(vehicleId)
    }

    /**
     * Get vehicle by number
     */
    suspend fun getVehicleByNumber(vehicleNumber: String): VehicleEntity? = withContext(Dispatchers.IO) {
        vehicleDao.getVehicleByNumber(vehicleNumber)
    }

    /**
     * Get all vehicles for a transporter
     */
    suspend fun getVehicles(transporterId: String): List<VehicleEntity> = withContext(Dispatchers.IO) {
        vehicleDao.getVehicles(transporterId)
    }

    /**
     * Observe vehicles (for reactive UI)
     */
    fun observeVehicles(transporterId: String): Flow<List<VehicleEntity>> {
        return vehicleDao.observeVehicles(transporterId)
    }

    /**
     * Get vehicles by status
     */
    suspend fun getVehiclesByStatus(
        transporterId: String,
        status: String
    ): List<VehicleEntity> = withContext(Dispatchers.IO) {
        vehicleDao.getVehiclesByStatus(transporterId, status)
    }

    /**
     * Get available vehicles
     */
    suspend fun getAvailableVehicles(transporterId: String): List<VehicleEntity> = withContext(Dispatchers.IO) {
        vehicleDao.getAvailableVehicles(transporterId)
    }

    /**
     * Get vehicles by category and subtype
     */
    suspend fun getVehiclesByCategory(
        transporterId: String,
        category: String,
        subtype: String
    ): List<VehicleEntity> = withContext(Dispatchers.IO) {
        vehicleDao.getVehiclesByCategory(transporterId, category, subtype)
    }

    /**
     * Save vehicle (upsert pattern)
     */
    suspend fun saveVehicle(vehicle: VehicleEntity) = withContext(Dispatchers.IO) {
        vehicleDao.saveVehicle(vehicle)
    }

    /**
     * Save multiple vehicles atomically
     */
    suspend fun saveAllVehicles(vehicles: List<VehicleEntity>) = withContext(Dispatchers.IO) {
        vehicleDao.saveAllVehicles(vehicles)
    }

    /**
     * Update vehicle status
     */
    suspend fun updateVehicleStatus(vehicleId: String, status: String) = withContext(Dispatchers.IO) {
        vehicleDao.updateVehicleStatus(vehicleId, status)
    }

    /**
     * Update assigned driver
     */
    suspend fun updateAssignedDriver(vehicleId: String, driverId: String?) = withContext(Dispatchers.IO) {
        vehicleDao.updateAssignedDriver(vehicleId, driverId)
    }

    /**
     * Delete vehicle
     */
    suspend fun deleteVehicle(vehicleId: String) = withContext(Dispatchers.IO) {
        vehicleDao.deleteVehicle(vehicleId)
    }

    /**
     * Get vehicle statistics
     */
    suspend fun getVehicleStats(transporterId: String): VehicleStats = withContext(Dispatchers.IO) {
        vehicleDao.getVehicleStats(transporterId)
    }

    /**
     * Get distinct categories
     */
    suspend fun getCategories(transporterId: String): List<String> = withContext(Dispatchers.IO) {
        vehicleDao.getCategories(transporterId)
    }

    /**
     * Get subtypes for a category
     */
    suspend fun getSubTypes(transporterId: String, category: String): List<String> = withContext(Dispatchers.IO) {
        vehicleDao.getSubTypes(transporterId, category)
    }

    /**
     * Check if vehicle number exists (for validation)
     */
    suspend fun vehicleNumberExists(
        transporterId: String,
        vehicleNumber: String
    ): Boolean = withContext(Dispatchers.IO) {
        vehicleDao.vehicleNumberExists(transporterId, vehicleNumber) > 0
    }

    /**
     * Clear all vehicles for a transporter (logout/data reset)
     */
    suspend fun clearVehicles(transporterId: String) = withContext(Dispatchers.IO) {
        vehicleDao.clearVehiclesForTransporter(transporterId)
    }

    /**
     * Clear all vehicles
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        vehicleDao.clearAllVehicles()
    }
}
