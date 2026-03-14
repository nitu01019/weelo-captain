package com.weelo.logistics.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * ActiveTripEntity - Stores the driver's currently active trip
 *
 * CRITICAL for crash recovery:
 * - When a driver has an active trip, this entity is persistently stored
 * - On app restart after crash, this trip can be restored
 * - Enables offline operation during the trip
 *
 * Only ONE active trip per driver at any time (upsert pattern used).
 */
@Entity(
    tableName = "active_trips",
    indices = [
        Index(value = ["driverId"]),
        Index(value = ["tripId"])
    ]
)
data class ActiveTripEntity(
    @PrimaryKey val tripId: String,
    val driverId: String,
    val vehicleId: String,
    val vehicleNumber: String,

    // Trip Details
    val transporterId: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,

    // Pickup Location
    val pickupLat: Double,
    val pickupLng: Double,
    val pickupAddress: String,
    val pickupCity: String?,

    // Drop Location
    val dropLat: Double,
    val dropLng: Double,
    val dropAddress: String,
    val dropCity: String?,

    // Trip Info
    val goodsType: String,
    val fare: Double,
    val distanceKm: Double,
    val estimatedDurationMinutes: Long,

    // Status & Progress
    val status: String,
    val currentLeg: Int = 0,           // Which stop driver is on (0 = pickup, N = drop)
    val totalLegs: Int = 1,           // Total number of legs (pickup + stops + drop)

    // Driver Info
    val driverName: String,
    val driverPhone: String,

    // Additional Info
    val notes: String? = null,

    // Sync Metadata
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val isStale: Boolean = false,

    // Last known location (for offline resumption)
    val lastKnownLatitude: Double? = null,
    val lastKnownLongitude: Double? = null,
    val lastKnownLocationTime: Long? = null,

    // Intermediate Stops (if any)
    val stopsJson: String? = null,

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null
)
