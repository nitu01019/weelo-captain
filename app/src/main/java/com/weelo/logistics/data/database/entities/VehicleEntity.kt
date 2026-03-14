package com.weelo.logistics.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * VehicleEntity - Room database table for vehicle data
 *
 * Stores transporter's vehicle information locally.
 * Used for offline viewing and truck selection.
 */
@Entity(
    tableName = "vehicles",
    indices = [
        Index(value = ["transporterId"]),
        Index(value = ["vehicleNumber"])
    ]
)
data class VehicleEntity(
    @PrimaryKey val vehicleId: String,
    val transporterId: String,
    val category: String,           // "open", "container", "lcv", etc.
    val subtype: String,            // "17_feet", "32_single", etc.
    val vehicleNumber: String,
    val model: String? = null,
    val year: Int? = null,
    val assignedDriverId: String? = null,
    val status: String,             // "AVAILABLE", "IN_TRANSIT", "MAINTENANCE", "INACTIVE"
    val capacityTons: Double = 0.0,
    val lastServiceDate: Long? = null,
    val insuranceExpiryDate: Long? = null,
    val registrationExpiryDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),

    // Sync Metadata
    val lastSyncedAt: Long = System.currentTimeMillis()
)
