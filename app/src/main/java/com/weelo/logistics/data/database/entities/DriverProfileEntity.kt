package com.weelo.logistics.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * DriverProfileEntity - Room database table for driver profile data
 *
 * Stores driver information locally for offline access.
 * This is purely for STORAGE - all API calls to driver endpoints remain unchanged.
 */
@Entity(
    tableName = "driver_profiles",
    indices = [
        androidx.room.Index(value = ["transporterId"]),
        androidx.room.Index(value = ["mobileNumber"])
    ]
)
data class DriverProfileEntity(
    @PrimaryKey val driverId: String,
    val name: String,
    val mobileNumber: String,
    val licenseNumber: String,
    val transporterId: String,
    val assignedVehicleId: String? = null,
    val isAvailable: Boolean = true,
    val rating: Float = 0f,
    val totalTrips: Int = 0,
    val profileImageUrl: String? = null,
    val status: String, // "ACTIVE", "ON_TRIP", "INACTIVE", "SUSPENDED"
    val createdAt: Long = System.currentTimeMillis(),

    // Sync Metadata
    val lastSyncedAt: Long = System.currentTimeMillis(),

    // Additional Info persisted for offline view
    val dateOfBirth: String? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,

    // Documents status (cached for offline checking)
    val licenseVerified: Boolean = false,
    val insuranceVerified: Boolean = false,
    val rcVerified: Boolean = false
)
