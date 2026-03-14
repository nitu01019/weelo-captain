package com.weelo.logistics.offline.sync

import android.content.Context
import com.weelo.logistics.data.database.dao.ActiveTripDao
import com.weelo.logistics.data.database.dao.BufferedLocationDao
import com.weelo.logistics.data.database.entities.ActiveTripEntity
import com.weelo.logistics.data.database.entities.BufferedLocationEntity
import com.weelo.logistics.offline.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * =============================================================================
 * OFFLINE SYNC COORDINATOR - Phase 3 (Enterprise Grade)
 * =============================================================================
 *
 * Manages all offline data synchronization between local and backend.
 * Coordinates:
 * - GPS location buffering (via BufferedLocationService)
 * - Batch uploads (via WorkManager)
 * - Status change queuing (via TripActionQueue)
 * - Network state handling (via NetworkMonitor)
 *
 * ARCHITECTURE (Industry Standard - Uber, Ola, DoorDash):
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    OFFLINE SYNC COORDINATOR                      │
 * ├─────────────────────────────────────────────────────────────────┤
 * │                                                                 │
 * │  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────┐  │
 * │  │ BufferedLocation │  │  TripActionQueue │  │  Local DB   │  │
 * │  │    Service       │  │                  │  │ (Room)      │  │
 * │  └────────┬─────────┘  └────────┬─────────┘  └──────┬──────┘  │
 * │           │                    │                    │          │
 * │           │                    │                    │          │
 * │           ▼                    ▼                    ▼          │
 * │  ┌──────────────────────────────────────────────────────┐    │
 * │  │           OFFLINE SYNC ENGINE                         │    │
 * │  │  - Exponential backoff (1s→2s→4s→8s→16s→30s)       │    │
 * │  │  - Circuit breaker (fails after N attempts)          │    │
 * │  │  - Conflict resolution (backend wins)               │    │
 * │  └────────────────────┬─────────────────────────────────┘    │
 * │                    │                                          │
 * │                    ▼                                          │
 * │  ┌──────────────────────────────────────────────────────┐    │
 * │  │         WORKMANAGER SCHEDULER                          │    │
 * │  │  - Enqueues sync jobs on network change               │    │
 * │  │  - Survives app restart/force-kill                    │    │
 * │  │  - Respects battery optimization                     │    │
 * │  └────────────────────┬─────────────────────────────────┘    │
 * │                           │                                   │
 * │                           ▼                                   │
 * │  ┌──────────────────────────────────────────────────────┐    │
 * │  │              BACKEND API                              │    │
 * │  │  POST /tracking/batch (GPS points)                   │    │
 * │  │  PUT /tracking/trip/:id/status (status changes)      │    │
 * │  └──────────────────────────────────────────────────────┘    │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * USAGE:
 * ```
 * // On app start
 * syncCoordinator.initialize()
 *
 * // When network changes
 * syncCoordinator.onNetworkChanged(isOnline)
 *
 * // Check sync status
 * val status = syncCoordinator.getSyncStatus()
 *
 * // Force sync
 * syncCoordinator.forceSync()
 * ```
 *
 * CONFLICT RESOLUTION STRATEGY:
 * 1. Backend is SINGLE SOURCE OF TRUTH
 * 2. Offline changes are best-effort
 * 3. If backend rejects client data → server wins
 * 4. Timestamp-based deduplication for GPS points
 * 5. Last-write-wins for status changes (with timestamp)
 *
 * INDUSTRY ALIGNMENT:
 * ✓ Uber: WorkManager + Network constraints
 * ✓ Ola: Exponential backoff + Circuit breaker
 * ✓ DoorDash: Backend-wins conflict resolution
 * ✓ Google: Jetpack Retry policies
 * =============================================================================
 */
@Singleton
class OfflineSyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bufferedLocationDao: BufferedLocationDao,
    private val activeTripDao: ActiveTripDao,
    private val networkMonitor: NetworkMonitor,
    private val bufferedLocationService: com.weelo.logistics.offline.buffer.BufferedLocationService
) {
    companion object {
        private const val TAG = "OfflineSyncCoordinator"

        /**
         * Maximum retry attempts before giving up
         */
        private const val MAX_RETRY_ATTEMPTS = 5

        /**
         * Exponential backoff configuration (same as WorkManager)
         */
        private val BACKOFF_DELAYS = longArrayOf(
            1_000L,   // 1 second
            2_000L,   // 2 seconds
            4_000L,   // 4 seconds
            8_000L,   // 8 seconds
            16_000L,  // 16 seconds
            30_000L   // 30 seconds (max)
        )

        /**
         * Sync interval (background polling)
         */
        private const val SYNC_INTERVAL_MS = 60_000L // 1 minute
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Sync state
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Sync status
    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // Sync failure info
    private val _lastFailure = MutableStateFlow<SyncFailure?>(null)
    val lastFailure: StateFlow<SyncFailure?> = _lastFailure.asStateFlow()

    private var syncJob: kotlinx.coroutines.Job? = null
    private var backgroundSyncJob: kotlinx.coroutines.Job? = null

    /**
     * Initialize the sync coordinator
     *
     * Call this on app startup (MainActivity onCreate)
     */
    fun initialize() {
        Timber.tag(TAG).d("Initializing OfflineSyncCoordinator")

        // Start background sync monitoring
        startBackgroundSync()

        // Check for pending data on startup
        checkPendingSync()
    }

    /**
     * Handle network state changes
     *
     * Called by NetworkMonitor when network state changes
     */
    fun onNetworkChanged(isOnline: Boolean) {
        if (isOnline) {
            Timber.tag(TAG).i("Network came online - triggering sync")
            enqueueSync()
        } else {
            Timber.tag(TAG).i("Network went offline - pausing sync")
            cancelSync()
        }
    }

    /**
     * Enqueue a sync job via WorkManager
     *
     * This is the preferred method for sync - it survives app restarts.
     */
    fun enqueueSync(immediate: Boolean = false) {
        if (!networkMonitor.isOnline.value) {
            Timber.tag(TAG).d("Skipping sync - offline")
            return
        }

        Timber.tag(TAG).d("Enqueuing sync job (immediate=$immediate)")
        OfflineSyncWorker.enqueue(context, immediate)
    }

    /**
     * Start immediate sync (blocking)
     *
     * Used when user manually triggers sync or when force-sync needed.
     * Returns immediately and shows status via _syncState flow.
     */
    fun forceSync() {
        if (!networkMonitor.isOnline.value) {
            _lastFailure.value = SyncFailure(
                reason = "Offline",
                message = "Cannot sync while offline",
                timestamp = System.currentTimeMillis()
            )
            return
        }

        // Cancel any existing sync
        cancelSync()

        // Start new sync
        syncJob = serviceScope.launch {
            performFullSync()
        }
    }

    /**
     * Perform full sync (all pending data)
     */
    private suspend fun performFullSync() {
        _syncState.value = SyncState.Syncing

        try {
            // Step 1: Sync buffered locations
            val locationResult = syncBufferedLocations()

            // Step 2: Sync pending status changes (future)
            // val statusResult = syncPendingStatusChanges()

            // Step 3: Check for active trip updates
            // val tripResult = syncActiveTripUpdates()

            // Update sync status
            _syncStatus.value = SyncStatus(
                lastSyncTime = System.currentTimeMillis(),
                pendingLocations = bufferedLocationDao.countAllPending(),
                lastSyncResult = if (locationResult.success) "SUCCESS" else "FAILED"
            )

            _syncState.value = if (locationResult.success) {
                SyncState.Idle
            } else {
                SyncState.Failed(locationResult.error ?: "Unknown error")
            }

            _lastFailure.value = if (!locationResult.success) {
                SyncFailure(
                    reason = "Sync failed",
                    message = locationResult.error ?: "Unknown error",
                    timestamp = System.currentTimeMillis()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Sync failed")
            _syncState.value = SyncState.Failed(e.message ?: "Unknown error")
            _lastFailure.value = SyncFailure(
                reason = "Exception",
                message = e.message ?: "Unknown error",
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Sync buffered GPS locations
     */
    private suspend fun syncBufferedLocations(): SyncResult {
        Timber.tag(TAG).d("Syncing buffered locations")

        val totalPending = bufferedLocationDao.countAllPending()
        if (totalPending == 0) {
            return SyncResult(success = true, itemsSynced = 0)
        }

        var totalSynced = 0
        var totalErrors = 0
        var shouldRetry = false

        val batchSize = 100 // Backend limit
        var processed = 0

        while (processed < totalPending) {
            val batch = bufferedLocationDao.getAllPending(batchSize)
            if (batch.isEmpty()) break

            // Group by tripId
            val byTrip = batch.groupBy { it.tripId }

            for ((tripId, points) in byTrip) {
                val result = uploadLocationBatch(tripId, points)
                totalSynced += result.synced
                totalErrors += result.errors

                if (result.shouldRetry) {
                    shouldRetry = true
                }
            }

            processed += batch.size

            // Rate limiting: pause between batches
            if (processed < totalPending) {
                delay(500)
            }
        }

        return SyncResult(
            success = totalErrors == 0,
            itemsSynced = totalSynced,
            shouldRetry = shouldRetry
        )
    }

    /**
     * Upload a batch of locations for a trip
     */
    private suspend fun uploadLocationBatch(
        tripId: String,
        points: List<BufferedLocationEntity>
    ): BatchSyncResult {
        try {
            val trackingApi = com.weelo.logistics.data.remote.RetrofitClient.trackingApi

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

            // Upload
            val response = trackingApi.uploadBatch(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data
                data ?: return BatchSyncResult(0, points.size, true)

                // Mark accepted points as uploaded
                val acceptedIds = points.take(data.accepted).map { it.id }
                if (acceptedIds.isNotEmpty()) {
                    bufferedLocationDao.markAsUploaded(acceptedIds)
                }

                // Mark failed points
                val rejectedIds = points.drop(data.accepted).map { it.id }
                if (rejectedIds.isNotEmpty()) {
                    val reason = buildString {
                        if (data.duplicate > 0) append(";dup:$data.duplicate")
                        if (data.stale > 0) append(";stale:$data.stale")
                        if (data.invalid > 0) append(";inv:$data.invalid")
                    }
                    bufferedLocationDao.markUploadFailed(rejectedIds, reason)
                }

                Timber.tag(TAG).v(
                    "Batch synced for $tripId: accepted=$data.accepted, " +
                    "duplicate=$data.duplicate, stale=$data.stale, invalid=$data.invalid"
                )

                // Sync ActiveTripEntity (if exists)
                syncActiveTripEntity(tripId)

                return BatchSyncResult(
                    synced = data.accepted,
                    errors = data.stale + data.duplicate + data.invalid,
                    shouldRetry = false
                )
            }

            // Request failed
            val error = response.body()?.error?.message ?: "Upload failed"
            bufferedLocationDao.markUploadFailed(points.map { it.id }, error)
            return BatchSyncResult(0, points.size, true)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Batch upload failed for trip $tripId")
            bufferedLocationDao.markUploadFailed(points.map { it.id }, e.message ?: "Unknown error")
            return BatchSyncResult(0, points.size, true)
        }
    }

    /**
     * Sync ActiveTripEntity from backend response
     */
    private suspend fun syncActiveTripEntity(tripId: String) {
        try {
            val activeTrip = activeTripDao.getActiveTripByTripId(tripId)
            if (activeTrip != null) {
                // Update last synced timestamp
                val updated = activeTrip.copy(
                    lastSyncedAt = Instant.now().toEpochMilli()
                )
                activeTripDao.saveActiveTrip(updated)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to sync ActiveTripEntity for $tripId")
        }
    }

    /**
     * Check for pending sync on startup
     */
    private fun checkPendingSync() {
        serviceScope.launch {
            try {
                val stats = bufferedLocationDao.getStatistics()
                _syncStatus.value = SyncStatus(
                    pendingLocations = stats.pending
                )

                if (stats.pending > 0 && networkMonitor.isOnline.value) {
                    Timber.tag(TAG).i("Found ${stats.pending} pending locations - enqueuing sync")
                    enqueueSync()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to check pending sync")
            }
        }
    }

    /**
     * Start background sync monitoring
     *
     * Periodically checks for pending data and triggers sync if online.
     * Runs even if app is in background (via WorkManager).
     */
    private fun startBackgroundSync() {
        backgroundSyncJob = serviceScope.launch {
            while (true) {
                delay(SYNC_INTERVAL_MS)

                if (networkMonitor.isOnline.value) {
                    // Only enqueue if WorkManager is not already running
                    if (!OfflineSyncWorker.isEnqueued(context)) {
                        enqueueSync()
                    }
                }
            }
        }
    }

    /**
     * Cancel current sync
     */
    private fun cancelSync() {
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Get current sync status
     */
    fun getSyncStatus(): SyncStatus {
        return _syncStatus.value
    }

    /**
     * Cleanup old data
     */
    fun cleanup() {
        serviceScope.launch {
            bufferedLocationService.cleanupOldUploaded()
        }
    }
}

// =============================================================================
// Data Classes
// =============================================================================

/**
 * Sync state
 */
sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Failed(val error: String) : SyncState()
}

/**
 * Sync status information
 */
data class SyncStatus(
    val lastSyncTime: Long = 0L,
    val pendingLocations: Int = 0,
    val pendingStatusChanges: Int = 0,
    val lastSyncResult: String = ""
) {
    val hasPending: Boolean
        get() = pendingLocations > 0 || pendingStatusChanges > 0

    val isSyncSuccessful: Boolean
        get() = lastSyncResult == "SUCCESS"
}

/**
 * Sync failure information
 */
data class SyncFailure(
    val reason: String,
    val message: String,
    val timestamp: Long
)

/**
 * Result of a sync operation
 */
data class SyncResult(
    val success: Boolean,
    val itemsSynced: Int = 0,
    val shouldRetry: Boolean = false,
    val error: String? = null
)

/**
 * Result of batch upload
 */
private data class BatchSyncResult(
    val synced: Int,
    val errors: Int,
    val shouldRetry: Boolean
)
