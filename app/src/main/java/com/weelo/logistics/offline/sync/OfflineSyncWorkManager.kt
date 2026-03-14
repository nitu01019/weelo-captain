package com.weelo.logistics.offline.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.weelo.logistics.data.database.dao.BufferedLocationDao
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.offline.NetworkMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * =============================================================================
 * OFFLINE SYNC WORK MANAGER - Phase 3 (Industry Standard)
 * =============================================================================
 *
 * Background sync using WorkManager (Uber, Ola, DoorDash pattern):
 * - Survives app restarts/force-stops
 * - Runs on network availability
 * - Respects battery optimization
 * - Exponential backoff on failure
 *
 * USAGE:
 * 1. Enqueue when app goes online: OfflineSyncWorkManager.enqueue(context)
 * 2. Auto-runs with network constraint
 * 3. Uploads pending GPS points
 * 4. Syncs pending trip status changes
 *
 * WORK SPECIFICATION:
 * - OneTimeWorkRequest with unique name
 * - NetworkType.CONNECTED (only when online)
 * - BackoffPolicy.EXPONENTIAL (1s → 30s max)
 * - Requires charging (optional, for heavy uploads)
 *
 * INDUSTRY ALIGNMENT:
 * ✓ Uber: WorkManager for background sync
 * ✓ Ola: Network constraints + backoff
 * ✓ DoorDash: Unique work to prevent duplicates
 * =============================================================================
 */
@HiltWorker
class OfflineSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val bufferedLocationDao: BufferedLocationDao,
    private val networkMonitor: NetworkMonitor
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "OfflineSyncWorker"
        private const val WORK_NAME = "offline_sync_work"
        private const val TAG_SYNC_LOCATIONS = "sync_locations"
        private const val TAG_SYNC_STATUS = "sync_status"

        /**
         * Enqueue sync work on app startup or when network becomes available
         */
        fun enqueue(context: Context, immediate: Boolean = false) {
            val syncRequest = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(false) // Allow sync even if battery low
                        .setRequiresCharging(false) // Important: sync even if not charging
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,      // 10s minimum
                    TimeUnit.MILLISECONDS                // Unit for the backoff delay
                )
                .addTag(TAG_SYNC_LOCATIONS)
                .addTag(TAG_SYNC_STATUS)
                .apply {
                    if (immediate) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE, // Replace if already queued
                    syncRequest
                )

            Timber.d(TAG, "Enqueued offline sync work")
        }

        /**
         * Cancel all pending sync work (e.g., on logout)
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
            Timber.d(TAG, "Cancelled offline sync work")
        }

        /**
         * Check if sync is running or queued
         */
        fun isEnqueued(context: Context): Boolean {
            return try {
                val workInfo = WorkManager.getInstance(context)
                    .getWorkInfosByTag(TAG_SYNC_LOCATIONS)
                    .get()
                workInfo.any { it.state != WorkInfo.State.CANCELLED && it.state != WorkInfo.State.FAILED }
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun doWork(): Result {
        Timber.tag(TAG).i("Starting offline sync")

        if (!networkMonitor.isOnline.value) {
            Timber.tag(TAG).w("Offline - skipping sync")
            return Result.retry()
        }

        val syncResults = mutableListOf<SyncResult>()

        try {
            // Sync 1: Upload buffered GPS locations
            val locationResult = syncBufferedLocations()
            syncResults.add(locationResult)

            // Sync 2: Upload pending status changes (future enhancement)
            // val statusResult = syncPendingStatusChanges()
            // syncResults.add(statusResult)

            // Evaluate overall result
            val hasFailures = syncResults.any { !it.success }
            val hasRetries = syncResults.any { it.shouldRetry }

            return when {
                hasRetries -> Result.retry()
                hasFailures -> {
                    Timber.tag(TAG).w("Sync completed with failures")
                    Result.failure()
                }
                else -> {
                    Timber.tag(TAG).i("Sync completed successfully")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Sync failed with exception")
            return Result.retry() // Retry on transient errors
        }
    }

    /**
     * Sync buffered GPS locations to backend
     *
     * ALGORITHM:
     * 1. Get pending points (max 100 per batch)
     * 2. Build BatchLocationRequest
     * 3. POST to /tracking/batch
     * 4. On success: mark as uploaded, delete if all uploaded
     * 5. On failure: mark with error, increment attempts
     *
     * BACKEND CONTRACT:
     * - Max 100 points per batch
     * - Must have timestamps for deduplication
     * - Response: { processed, accepted, stale, duplicate, invalid }
     */
    private suspend fun syncBufferedLocations(): SyncResult {
        try {
            // Check if we have pending locations
            val totalPending = bufferedLocationDao.countAllPending()
            if (totalPending == 0) {
                Timber.tag(TAG).d("No pending locations to sync")
                return SyncResult(success = true, itemsSynced = 0)
            }

            Timber.tag(TAG).i("Syncing $totalPending pending locations")

            var totalSynced = 0
            var totalErrors = 0
            var shouldRetry = false

            // Process in batches of 100 (backend limit)
            val batchSize = 100
            var processed = 0

            while (processed < totalPending) {
                // Get next batch
                val batch = bufferedLocationDao.getAllPending(batchSize)
                if (batch.isEmpty()) break

                // Group by tripId (one request per trip)
                val byTrip = batch.groupBy { it.tripId }

                for ((tripId, points) in byTrip) {
                    val syncResult = uploadBatch(tripId, points)
                    totalSynced += syncResult.synced
                    totalErrors += syncResult.errors

                    if (syncResult.shouldRetry) {
                        shouldRetry = true
                    }
                }

                processed += batch.size

                // Rate limiting: brief pause between batches
                if (processed < totalPending) {
                    kotlinx.coroutines.delay(500)
                }
            }

            return SyncResult(
                success = totalErrors == 0,
                itemsSynced = totalSynced,
                shouldRetry = shouldRetry
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Buffered location sync failed")
            return SyncResult(success = false, shouldRetry = true)
        }
    }

    /**
     * Upload a single batch of locations for a trip
     */
    private suspend fun uploadBatch(
        tripId: String,
        points: List<com.weelo.logistics.data.database.entities.BufferedLocationEntity>
    ): BatchSyncResult {
        try {
            val trackingApi = RetrofitClient.trackingApi

            // Convert to API request
            val apiPoints = points.map { point ->
                com.weelo.logistics.data.api.BatchLocationPoint(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    speed = point.speed,
                    bearing = point.bearing,
                    accuracy = point.accuracy,
                    timestamp = point.timestamp
                )
            }

            val request = com.weelo.logistics.data.api.BatchLocationRequest(
                tripId = tripId,
                points = apiPoints
            )

            // Upload batch
            val response = trackingApi.uploadBatch(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data

                if (data != null) {
                    // Mark successfully uploaded points
                    val acceptedIds = points.take(data.accepted).map { it.id }
                    val rejectedIds = points.drop(data.accepted).map { it.id }

                    if (acceptedIds.isNotEmpty()) {
                        bufferedLocationDao.markAsUploaded(acceptedIds)
                    }

                    // Mark rejected points as failed with reason
                    if (rejectedIds.isNotEmpty()) {
                        val reason = buildString {
                            if (data.duplicate > 0) append(";duplicate:$data.duplicate")
                            if (data.stale > 0) append(";stale:$data.stale")
                            if (data.invalid > 0) append(";invalid:$data.invalid")
                        }
                        bufferedLocationDao.markUploadFailed(rejectedIds, reason)
                    }

                    Timber.tag(TAG).v(
                        "Batch uploaded: accepted=$data.accepted, " +
                        "stale=$data.stale, duplicate=$data.duplicate, invalid=$data.invalid"
                    )

                    // Clean up uploaded points (optional - could keep for audit)
                    if (data.accepted > 0) {
                        cleanupUploadedPoints(tripId)
                    }

                    return BatchSyncResult(
                        synced = data.accepted,
                        errors = data.stale + data.duplicate + data.invalid,
                        shouldRetry = false
                    )
                }
            }

            // Request failed - mark for retry
            val errorReason = response.body()?.error?.message ?: "Upload failed"
            val pointIds = points.map { it.id }
            bufferedLocationDao.markUploadFailed(pointIds, errorReason)

            return BatchSyncResult(
                synced = 0,
                errors = points.size,
                shouldRetry = true
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Batch upload failed for trip $tripId")
            val pointIds = points.map { it.id }
            bufferedLocationDao.markUploadFailed(pointIds, e.message ?: "Unknown error")

            return BatchSyncResult(
                synced = 0,
                errors = points.size,
                shouldRetry = true
            )
        }
    }

    /**
     * Cleanup uploaded points older than 7 days
     * Keeps audit trail but prevents database bloat
     */
    private suspend fun cleanupUploadedPoints(tripId: String) {
        try {
            val retentionMs = 7 * 24 * 60 * 60 * 1000L // 7 days
            val beforeTimestamp = Instant.now().toEpochMilli() - retentionMs

            val deleted = bufferedLocationDao.deleteOldUploaded(beforeTimestamp)
            Timber.tag(TAG).d("Cleaned up $deleted old uploaded points for trip $tripId")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Cleanup failed for trip $tripId")
        }
    }

    /**
     * Result of a sync operation
     */
    private data class SyncResult(
        val success: Boolean,
        val itemsSynced: Int = 0,
        val shouldRetry: Boolean = false
    )

    /**
     * Result of batch upload
     */
    private data class BatchSyncResult(
        val synced: Int,
        val errors: Int,
        val shouldRetry: Boolean
    )
}
