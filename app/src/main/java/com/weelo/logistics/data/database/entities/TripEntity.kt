package com.weelo.logistics.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.weelo.logistics.data.model.Location
import com.weelo.logistics.data.model.TripStatus

/**
 * TripEntity - Room database table for persistent trip storage
 *
 * This entity stores trip data locally so the app can work offline.
 * Data is synced with the backend when network is available.
 *
 * IMPORTANT: This is for LOCAL STORAGE only. The actual API calls happen as usual.
 * This just ensures data survives app restarts and crashes.
 */
@Entity(
    tableName = "trips",
    indices = [
        androidx.room.Index(value = ["driverId"]),
        androidx.room.Index(value = ["vehicleId"]),
        androidx.room.Index(value = ["status"])
    ]
)
data class TripEntity(
    @PrimaryKey val tripId: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,

    // Pickup Location
    val pickupLat: Double,
    val pickupLng: Double,
    val pickupAddress: String,
    val pickupCity: String?,
    val pickupState: String?,
    val pickupPincode: String?,

    // Drop Location
    val dropLat: Double,
    val dropLng: Double,
    val dropAddress: String,
    val dropCity: String?,
    val dropState: String?,
    val dropPincode: String?,

    // Trip Details
    val distanceKm: Double,
    val estimatedDurationMinutes: Long,
    val fare: Double,
    val goodsType: String,
    val weight: String?,

    // Vehicle & Driver Info
    val transporterId: String,
    val vehicleId: String,
    val vehicleNumber: String,
    val driverId: String?,
    val driverName: String?,

    // Status & Timing
    val status: String,           // Stored as String, converted via TripStatusConverter
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,

    // Sync Metadata
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val isOffline: Boolean = false,  // True if data is stale (fetched while offline)

    // Additional Info
    val notes: String? = null
) {
    /**
     * Create Location object from entity fields
     */
    fun toPickupLocation(): Location {
        return Location(
            latitude = pickupLat,
            longitude = pickupLng,
            address = pickupAddress,
            city = pickupCity,
            state = pickupState,
            pincode = pickupPincode
        )
    }

    /**
     * Create Location object from entity fields
     */
    fun toDropLocation(): Location {
        return Location(
            latitude = dropLat,
            longitude = dropLng,
            address = dropAddress,
            city = dropCity,
            state = dropState,
            pincode = dropPincode
        )
    }
}
