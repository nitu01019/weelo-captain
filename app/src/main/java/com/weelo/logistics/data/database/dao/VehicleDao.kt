package com.weelo.logistics.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.weelo.logistics.data.database.entities.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * VehicleDao - Data Access Object for vehicle operations
 */
@Dao
interface VehicleDao {

    /**
     * Get vehicle by ID
     */
    @Query("SELECT * FROM vehicles WHERE vehicleId = :vehicleId")
    suspend fun getVehicle(vehicleId: String): VehicleEntity?

    /**
     * Get vehicle by number (unique per transporter)
     */
    @Query("SELECT * FROM vehicles WHERE vehicleNumber = :vehicleNumber")
    suspend fun getVehicleByNumber(vehicleNumber: String): VehicleEntity?

    /**
     * Get all vehicles for a transporter
     */
    @Query("SELECT * FROM vehicles WHERE transporterId = :transporterId ORDER BY category, subtype")
    suspend fun getVehicles(transporterId: String): List<VehicleEntity>

    /**
     * Observe vehicles (for Compose)
     */
    @Query("SELECT * FROM vehicles WHERE transporterId = :transporterId ORDER BY category, subtype")
    fun observeVehicles(transporterId: String): Flow<List<VehicleEntity>>

    /**
     * Get vehicles by status
     */
    @Query("SELECT * FROM vehicles WHERE transporterId = :transporterId AND status = :status")
    suspend fun getVehiclesByStatus(transporterId: String, status: String): List<VehicleEntity>

    /**
     * Get available vehicles for a transporter
     */
    @Query("SELECT * FROM vehicles WHERE transporterId = :transporterId AND status = 'AVAILABLE'")
    suspend fun getAvailableVehicles(transporterId: String): List<VehicleEntity>

    /**
     * Get vehicles by category and subtype
     */
    @Query("SELECT * FROM vehicles WHERE transporterId = :transporterId AND category = :category AND subtype = :subtype")
    suspend fun getVehiclesByCategory(transporterId: String, category: String, subtype: String): List<VehicleEntity>

    /**
     * Insert or replace vehicle
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVehicle(vehicle: VehicleEntity)

    /**
     * Insert or replace multiple vehicles
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAllVehicles(vehicles: List<VehicleEntity>)

    /**
     * Update vehicle status
     */
    @Query("UPDATE vehicles SET status = :status WHERE vehicleId = :vehicleId")
    suspend fun updateVehicleStatus(vehicleId: String, status: String)

    /**
     * Update assigned driver
     */
    @Query("UPDATE vehicles SET assignedDriverId = :driverId WHERE vehicleId = :vehicleId")
    suspend fun updateAssignedDriver(vehicleId: String, driverId: String?)

    /**
     * Delete vehicle
     */
    @Query("DELETE FROM vehicles WHERE vehicleId = :vehicleId")
    suspend fun deleteVehicle(vehicleId: String)

    /**
     * Clear all vehicles (for logout or data refresh)
     */
    @Query("DELETE FROM vehicles WHERE transporterId = :transporterId")
    suspend fun clearVehiclesForTransporter(transporterId: String)

    /**
     * Clear all vehicles
     */
    @Query("DELETE FROM vehicles")
    suspend fun clearAllVehicles()

    /**
     * Get vehicle statistics for a transporter
     */
    @Transaction
    suspend fun getVehicleStats(transporterId: String): VehicleStats {
        val vehicles = getVehicles(transporterId)
        val available = vehicles.count { it.status == "AVAILABLE" }
        val inTransit = vehicles.count { it.status == "IN_TRANSIT" }
        val maintenance = vehicles.count { it.status == "MAINTENANCE" }
        val inactive = vehicles.count { it.status == "INACTIVE" }

        // Calculate total capacity
        val totalCapacityTons = vehicles.sumOf { it.capacityTons }

        return VehicleStats(
            total = vehicles.size,
            available = available,
            inTransit = inTransit,
            maintenance = maintenance,
            inactive = inactive,
            totalCapacityTons = totalCapacityTons
        )
    }

    /**
     * Get distinct categories for a transporter
     */
    @Query("SELECT DISTINCT category FROM vehicles WHERE transporterId = :transporterId ORDER BY category")
    suspend fun getCategories(transporterId: String): List<String>

    /**
     * Get subtypes for a category
     */
    @Query("SELECT DISTINCT subtype FROM vehicles WHERE transporterId = :transporterId AND category = :category ORDER BY subtype")
    suspend fun getSubTypes(transporterId: String, category: String): List<String>

    /**
     * Check if vehicle exists by number
     */
    @Query("SELECT COUNT(*) FROM vehicles WHERE transporterId = :transporterId AND vehicleNumber = :vehicleNumber")
    suspend fun vehicleNumberExists(transporterId: String, vehicleNumber: String): Int
}

/**
 * Vehicle statistics summary
 */
data class VehicleStats(
    val total: Int,
    val available: Int,
    val inTransit: Int,
    val maintenance: Int,
    val inactive: Int,
    val totalCapacityTons: Double
)
