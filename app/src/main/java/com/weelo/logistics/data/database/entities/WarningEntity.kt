package com.weelo.logistics.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * WarningEntity - Room database table for driver warnings
 *
 * Stores warnings issued to drivers (late pickup, customer complaints, etc.)
 * Allows offline viewing of warnings and earnings score calculation.
 */
@Entity(
    tableName = "warnings",
    indices = [
        Index(value = ["driverId"]),
        Index(value = ["tripId"])
    ]
)
data class WarningEntity(
    @PrimaryKey val warningId: String,
    val driverId: String,
    val warningType: String,        // "late_arrival", "customer_complaint", "route_deviation", etc.
    val reason: String,
    val severity: String,           // "INFO", "WARNING", "CRITICAL"
    val tripId: String? = null,
    val customerId: String? = null,
    val vehicleId: String? = null,
    val issuedAt: Long = System.currentTimeMillis(),
    val isAcknowledged: Boolean = false,
    val acknowledgedAt: Long? = null,
    val pointsDeducted: Int = 0,
    val notes: String? = null,

    // Sync Metadata
    val lastSyncedAt: Long = System.currentTimeMillis()
)
