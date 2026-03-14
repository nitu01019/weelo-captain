package com.weelo.logistics.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * =============================================================================
 * BUFFERED LOCATION ENTITY - Phase 3 Offline Resilience
 * =============================================================================
 *
 * Stores GPS location points buffered locally when driver is offline.
 * Uploads to backend in batches when network resumes.
 *
 * SYNC FLOW:
 * 1. Driver offline → Store location in this table
 * 2. Network resumes → Get all points for trip
 * 3. Call POST /tracking/batch with up to 100 points
 * 4. Delete successfully uploaded points
 * 5. Retry failed uploads with exponential backoff
 *
 * DATA LIFECYCLE:
 * - Created when offline
 * - Flagged as uploaded after successful batch sync
 * - Deleted 7 days after upload (for audit trail)
 * - Deleted immediately if trip completed/cancelled successfully
 *
 * SCALABILITY:
 * - Assumes ~1 point/30 seconds = 120 points/hour = 2880 points/day
 * - With offline driver: ~10K points max (reasonable for Room DB)
 * - Indexed on (tripId + uploaded) for fast sync queries
 * =============================================================================
 */
@Entity(
    tableName = "buffered_locations",
    indices = [
        Index(value = ["tripId", "uploaded"]),
        Index(value = ["createdTimestamp"]),
        Index(value = ["uploaded"])
    ]
)
data class BufferedLocationEntity(
    /**
     * Primary key - auto-generated
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Trip ID associated with this location
     * Groups all location points for a single trip
     */
    val tripId: String,

    /**
     * GPS latitude (decimal degrees)
     * Range: -90 to 90
     */
    val latitude: Double,

    /**
     * GPS longitude (decimal degrees)
     * Range: -180 to 180
     */
    val longitude: Double,

    /**
     * Speed in km/h (optional)
     * 0 if stationary or not available
     */
    val speed: Float = 0f,

    /**
     * Heading/bearing in degrees (optional)
     * 0-360, matching compass directions
     */
    val bearing: Float = 0f,

    /**
     * GPS accuracy in meters (optional)
     * Higher = less accurate. 10m = good, 100m = poor.
     */
    val accuracy: Float? = null,

    /**
     * Timestamp when location was captured (ISO 8601)
     * Format: "2026-03-14T10:30:00.000Z"
     * Used by backend to detect duplicates and order points
     */
    val timestamp: String,

    /**
     * Local creation timestamp (for cleanup)
     * Used to delete old uploaded points (7-day retention)
     */
    val createdTimestamp: Long = 0L,

    /**
     * Has this point been uploaded to backend?
     * false = Pending upload (trip was active when offline)
     * true = Successfully uploaded (kept for audit, deleted after 7 days)
     */
    val uploaded: Boolean = false,

    /**
     * Upload attempt count (for retry logic)
     * Increment after each failed batch upload
     * Skip if attempts > MAX_UPLOAD_ATTEMPTS (corrupted/stale data)
     */
    val uploadAttempts: Int = 0,

    /**
     * Last upload attempt timestamp (for rate limiting)
     * Prevents spamming backend with immediate retries
     */
    val lastUploadAttempt: Long? = null,

    /**
     * When point was successfully uploaded to backend
     * Used for cleanup (delete after RETENTION_DAYS)
     * Null if not yet uploaded
     */
    val uploadedTimestamp: Long? = null,

    /**
     * Upload error message (if upload failed)
     * For debugging and determining if point should be retried
     */
    val uploadError: String? = null
)

/**
 * Constants for buffered location management
 */
object BufferedLocationConstants {
    const val MAX_UPLOAD_ATTEMPTS = 3
    const val BATCH_UPLOAD_SIZE = 100      // Backend limit
    const val RETENTION_DAYS = 7L
    const val RETENTION_MS = RETENTION_DAYS * 24 * 60 * 60 * 1000
}
