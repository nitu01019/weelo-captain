package com.weelo.logistics.data.database.dao

import androidx.room.*
import com.weelo.logistics.data.database.entities.BufferedLocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * =============================================================================
 * BUFFERED LOCATION DAO - Phase 3 Offline Resilience
 * =============================================================================
 *
 * Database access methods for buffered GPS location points.
 *
 * USED BY:
 * - OfflineLocationBuffer: Buffer points when offline
 * - OfflineSyncService: Upload buffered points on reconnect
 * - NetworkMonitor: Cleanup old data
 *
 * KEY OPERATIONS:
 * - Insert: Add point when offline
 * - Get pending: Points needing upload (uploaded=false)
 * - Mark uploaded: After successful batch upload
 * - Get failed: Points with uploadError (for retry)
 * - Delete old: Cleanup after trip completes (7 days for uploaded)
 * =============================================================================
 */
@Dao
interface BufferedLocationDao {

    /**
     * Insert a single buffered location
     * Used by GPS tracking service when offline
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: BufferedLocationEntity): Long

    /**
     * Insert multiple buffered locations (batch)
     * Used for bulk insert when coming online
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<BufferedLocationEntity>): List<Long>

    /**
     * Get all pending locations for a trip
     * Excludes already uploaded points
     * Used by sync service to build batch upload payload
     *
     * LIMIT: Returns up to BATCH_UPLOAD_SIZE points
     */
    @Query("""
        SELECT * FROM buffered_locations
        WHERE tripId = :tripId AND uploaded = 0
        ORDER BY createdTimestamp ASC
        LIMIT :limit
    """)
    suspend fun getPendingForTrip(tripId: String, limit: Int = 100): List<BufferedLocationEntity>

    /**
     * Get all pending locations for a trip as Flow (reactive)
     * Used by UI to show upload progress
     */
    @Query("""
        SELECT * FROM buffered_locations
        WHERE tripId = :tripId AND uploaded = 0
        ORDER BY createdTimestamp ASC
    """)
    fun getPendingForTripFlow(tripId: String): Flow<List<BufferedLocationEntity>>

    /**
     * Count pending locations for a trip
     * Used to show "X points pending upload" banner
     */
    @Query("""
        SELECT COUNT(*) FROM buffered_locations
        WHERE tripId = :tripId AND uploaded = 0
    """)
    suspend fun countPendingForTrip(tripId: String): Int

    /**
     * Mark specific locations as uploaded
     * Called after successful batch upload
     *
     * @param ids List of location IDs that were successfully uploaded
     */
    @Query("""
        UPDATE buffered_locations
        SET uploaded = 1,
            uploadedTimestamp = :uploadedTimestamp
        WHERE id IN (:ids)
    """)
    suspend fun markAsUploaded(ids: List<Long>, uploadedTimestamp: Long = System.currentTimeMillis())

    /**
     * Mark ALL pending locations for a trip as uploaded
     * Called after successful sync when trip completes
     */
    @Query("""
        UPDATE buffered_locations
        SET uploaded = 1,
            uploadedTimestamp = :uploadedTimestamp
        WHERE tripId = :tripId AND uploaded = 0
    """)
    suspend fun markAllForTripAsUploaded(
        tripId: String,
        uploadedTimestamp: Long = System.currentTimeMillis()
    )

    /**
     * Increment upload attempt count for failed uploads
     * Used for retry logic with exponential backoff
     */
    @Query("""
        UPDATE buffered_locations
        SET uploadAttempts = uploadAttempts + 1,
            lastUploadAttempt = :timestamp,
            uploadError = :error
        WHERE id IN (:ids)
    """)
    suspend fun markUploadFailed(ids: List<Long>, error: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Get failed upload attempts (for retry)
     * Points that have been attempted but not uploaded yet
     * AND haven't exceeded max attempts
     */
    @Query("""
        SELECT * FROM buffered_locations
        WHERE uploaded = 0
          AND uploadAttempts > 0
          AND uploadAttempts < :maxAttempts
        ORDER BY uploadAttempts ASC
        LIMIT :limit
    """)
    suspend fun getFailedUploads(maxAttempts: Int, limit: Int = 100): List<BufferedLocationEntity>

    /**
     * Get uploaded points for audit/cleanup
     * Points uploaded more than X days ago should be deleted
     */
    @Query("""
        SELECT * FROM buffered_locations
        WHERE uploaded = 1
          AND uploadedTimestamp < :beforeTimestamp
        LIMIT :limit
    """)
    suspend fun getOldUploaded(beforeTimestamp: Long, limit: Int = 500): List<BufferedLocationEntity>

    /**
     * Delete specific records
     * Called after successful upload cleanup or trip completion
     */
    @Delete
    suspend fun delete(locations: List<BufferedLocationEntity>)

    /**
     * Delete all buffered locations for a trip
     * Called when trip is completed successfully
     * All pending points should have been uploaded by then
     */
    @Query("DELETE FROM buffered_locations WHERE tripId = :tripId")
    suspend fun deleteForTrip(tripId: String)

    /**
     * Delete all uploaded records older than retention period
     * Called periodically by cleanup job
     */
    @Query("""
        DELETE FROM buffered_locations
        WHERE uploaded = 1
          AND uploadedTimestamp < :beforeTimestamp
    """)
    suspend fun deleteOldUploaded(beforeTimestamp: Long): Int

    /**
     * Get all unuploaded locations across all trips
     * Used for force sync (all pending points)
     */
    @Query("""
        SELECT * FROM buffered_locations
        WHERE uploaded = 0
        ORDER BY createdTimestamp ASC
        LIMIT :limit
    """)
    suspend fun getAllPending(limit: Int = 1000): List<BufferedLocationEntity>

    /**
     * Count all pending locations across all trips
     * Used for global sync status
     */
    @Query("SELECT COUNT(*) FROM buffered_locations WHERE uploaded = 0")
    suspend fun countAllPending(): Int

    /**
     * Get total statistics
     * Used for monitoring and debugging
     */
    @Query("""
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN uploaded = 0 THEN 1 ELSE 0 END) as pending,
            SUM(CASE WHEN uploaded = 1 THEN 1 ELSE 0 END) as uploaded
        FROM buffered_locations
    """)
    suspend fun getStatistics(): LocationStats

    /**
     * Delete all records (for testing/data reset)
     */
    @Query("DELETE FROM buffered_locations")
    suspend fun deleteAll()
}

/**
 * Statistics summary for buffered locations
 */
data class LocationStats(
    val total: Int,
    val pending: Int,
    val uploaded: Int
)
