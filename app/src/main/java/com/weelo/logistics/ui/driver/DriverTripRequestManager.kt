package com.weelo.logistics.ui.driver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.ArrayDeque
import java.util.Queue

import com.weelo.logistics.data.model.TripAssignedNotification

/**
 * =============================================================================
 * DRIVER TRIP REQUEST MANAGER - THREAD-SAFE SINGLETON
 * =============================================================================
 *
 * Manages the state of driver trip requests.
 * Following BroadcastOverlayManager pattern.
 *
 * RESPONSIBILITIES:
 *  - Queue management for simultaneous requests
 *  - Overlay visibility control
 *  - Thread-safe state mutations
 *
 * THREAD SAFETY:
 *  - Uses Mutex for state mutations (prevents race conditions)
 *  - StateFlow for read-only access (thread-safe by design)
 *
 * SCALABILITY:
 *  - Bounded queue (max 10) prevents memory issues
 *  - Automatic cleanup of old requests
 *  - Minimal memory footprint
 */
object DriverTripRequestManager {

    private const val TAG = "DriverRequestManager"

    // =========================================================================
    // THREAD SAFETY - Mutex for all state mutations
    // =========================================================================
    private val mutex = Mutex()

    // =========================================================================
    // STATE - All flows are thread-safe read-only StateFlow
    // =========================================================================

    /** Current request being displayed in overlay */
    private val _currentRequest = MutableStateFlow<TripAssignedNotification?>(null)
    val currentRequest: StateFlow<TripAssignedNotification?> = _currentRequest.asStateFlow()

    /** Queue for pending requests (bounded to prevent memory issues) */
    private val _requestQueue = MutableStateFlow<Queue<TripAssignedNotification>>(ArrayDeque())
    val queueSize: StateFlow<Int> =
        _requestQueue.map { it.size }.stateIn(
            CoroutineScope(Dispatchers.Default),
            SharingStarted.Eagerly,
            0
        )

    /** Overlay visibility state */
    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    /** Current action state (for UI loading indicators) */
    private val _actionState = MutableStateFlow<ActionState>(ActionState.IDLE)
    val actionState: StateFlow<ActionState> = _actionState.asStateFlow()

    /** Maximum queue size to prevent memory issues */
    private const val MAX_QUEUE_SIZE = 10

    /** Delay between showing requests (ms) */
    private const val REQUEST_DELAY_MS = 500L

    // =========================================================================
    // ACTIONS
    // =========================================================================

    /**
     * Add a new trip request and show overlay
     *
     * Thread-safe: Uses mutex to prevent race conditions
     *
     * @param notification The trip request notification
     * @return true if request was queued, false if duplicate or queue full
     */
    suspend fun addRequest(notification: TripAssignedNotification): Boolean = mutex.withLock {
        Timber.tag(TAG).i("🚛 Adding request: ${notification.assignmentId} (${notification.vehicleNumber})")

        // Check for duplicate request (same assignmentId)
        if (_currentRequest.value?.assignmentId == notification.assignmentId) {
            Timber.tag(TAG).w("Duplicate request ignored: ${notification.assignmentId}")
            return false
        }

        if (_requestQueue.value.any { it.assignmentId == notification.assignmentId }) {
            Timber.tag(TAG).w("Request already queued: ${notification.assignmentId}")
            return false
        }

        if (_isOverlayVisible.value) {
            // Another request is being shown, add to queue
            val queue = ArrayDeque(_requestQueue.value)
            if (queue.size >= MAX_QUEUE_SIZE) {
                Timber.tag(TAG).w("Queue full, dropping oldest request")
                queue.removeFirst()
            }
            queue.addLast(notification)
            _requestQueue.value = queue
            Timber.tag(TAG).i("Request queued (position ${queue.size}): ${notification.assignmentId}")
        } else {
            // Show this request immediately
            _currentRequest.value = notification
            _isOverlayVisible.value = true
            Timber.tag(TAG).i("Showing request: ${notification.assignmentId}")
        }

        return true
    }

    /**
     * Called when driver accepts the request
     *
     * Thread-safe: Uses mutex for state mutation
     *
     * @param assignmentId The assignment ID being accepted
     */
    suspend fun onAccept(assignmentId: String) = mutex.withLock {
        Timber.tag(TAG).i("✅ Accepting: $assignmentId")

        val current = _currentRequest.value
        if (current?.assignmentId == assignmentId) {
            _actionState.value = ActionState.ACCEPTING
            completeCurrentRequest()
            _actionState.value = ActionState.IDLE
        } else {
            Timber.tag(TAG).w("Request not current, ignoring accept: $assignmentId")
        }
    }

    /**
     * Called when driver declines or request expires
     *
     * Thread-safe: Uses mutex for state mutation
     *
     * @param assignmentId The assignment ID being declined
     */
    suspend fun onDecline(assignmentId: String) = mutex.withLock {
        Timber.tag(TAG).i("❌ Declining: $assignmentId")

        val current = _currentRequest.value
        if (current?.assignmentId == assignmentId) {
            _actionState.value = ActionState.DECLINING
            completeCurrentRequest()
            _actionState.value = ActionState.IDLE
        } else {
            Timber.tag(TAG).w("Request not current, ignoring decline: $assignmentId")
        }
    }

    /**
     * Called when overlay is dismissed (without action)
     * Moves current request to requests list for later access
     *
     * Thread-safe: Uses mutex for state mutation
     */
    suspend fun onDismiss() = mutex.withLock {
        val current = _currentRequest.value
        if (current != null) {
            Timber.tag(TAG).i("👋 Dismissing: ${current.assignmentId}")
            // Dismissed requests are removed from queue
            // Future enhancement: Save to historical requests list for later access
            completeCurrentRequest()
        }
    }

    /**
     * Called when customer cancels the order
     * Auto-dismisses the overlay if it matches this order
     *
     * Thread-safe: Uses mutex for state mutation
     *
     * @param orderId The order that was cancelled
     */
    suspend fun onOrderCancelled(orderId: String) = mutex.withLock {
        val current = _currentRequest.value
        if (current?.orderId == orderId) {
            Timber.tag(TAG).i("🚫 Order cancelled: $orderId")
            completeCurrentRequest()
            // Clear queue for this order as well
            val filteredQueue = ArrayDeque(_requestQueue.value.filter { it.orderId != orderId })
            _requestQueue.value = filteredQueue
        }
    }

    /**
     * Called when assignment status changes (e.g., timeout, reassignment)
     * Updates or removes the current request based on new status
     *
     * Thread-safe: Uses mutex for state mutation
     *
     * @param assignmentId The assignment that changed
     * @param newStatus New status
     */
    suspend fun onAssignmentStatusChanged(assignmentId: String, newStatus: String) = mutex.withLock {
        val current = _currentRequest.value
        if (current?.assignmentId == assignmentId) {
            Timber.tag(TAG).i("📊 Status changed: $assignmentId -> $newStatus")

            when (newStatus.lowercase()) {
                "driver_accepted" -> {
                    // Driver already accepted elsewhere, dismiss
                    completeCurrentRequest()
                }
                "driver_declined", "cancelled", "timed_out" -> {
                    // Request no longer valid, dismiss
                    completeCurrentRequest()
                }
                "pending" -> {
                    // Still waiting, update expiresAt if expired
                    if (current.isExpired) {
                        Timber.tag(TAG).i("⏰ Request expired, dismissing: $assignmentId")
                        completeCurrentRequest()
                    }
                }
                else -> {
                    Timber.tag(TAG).w("Unknown status: $newStatus")
                }
            }
        }
    }

    /**
     * Complete the current request and show next in queue
     *
     * Private function - called by all action methods
     * Thread-safe: Must be called within mutex.withLock
     */
    private suspend fun completeCurrentRequest() {
        _currentRequest.value = null
        _isOverlayVisible.value = false

        // Brief delay before showing next request (for visual separation)
        delay(REQUEST_DELAY_MS)

        // Show next request in queue
        val queue = ArrayDeque(_requestQueue.value)
        if (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            _requestQueue.value = queue
            _currentRequest.value = next
            _isOverlayVisible.value = true
            Timber.tag(TAG).i("Showing next request from queue: ${next.assignmentId}")
        }
    }

    /**
     * Clear all state (for logout)
     *
     * Thread-safe: Uses mutex
     */
    suspend fun clear() = mutex.withLock {
        Timber.tag(TAG).i("🧹 Clearing all state")
        _currentRequest.value = null
        _requestQueue.value = ArrayDeque()
        _isOverlayVisible.value = false
        _actionState.value = ActionState.IDLE
    }

    /**
     * Set the action state externally (for pre-API-call loading indicators)
     *
     * Called by WeeloNavigation BEFORE the accept/decline API call so the
     * overlay shows a loading spinner immediately when the driver swipes.
     *
     * Thread-safe: Uses mutex
     */
    suspend fun setActionState(state: ActionState) = mutex.withLock {
        Timber.tag(TAG).i("🔄 Action state: $state")
        _actionState.value = state
    }

    /**
     * Get the queue (for debugging/testing)
     *
     * Thread-safe: Returns a copy
     */
    suspend fun getQueue(): List<TripAssignedNotification> = mutex.withLock {
        _requestQueue.value.toList()
    }
}

/**
 * State of overlay action (for loading indicators)
 */
enum class ActionState {
    IDLE,
    ACCEPTING,
    DECLINING
}
