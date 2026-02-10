package com.weelo.logistics.broadcast

import android.content.Context
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.offline.AvailabilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * BroadcastOverlayManager - Global Singleton for Managing Broadcast Popups
 * =========================================================================
 * 
 * RAPIDO-STYLE BROADCAST SYSTEM:
 * - Shows full-screen overlay when new broadcast arrives
 * - Manages queue of broadcasts (FIFO)
 * - Thread-safe for millions of concurrent users
 * - Auto-expires old broadcasts
 * 
 * USAGE:
 * 1. WebSocket receives broadcast → BroadcastOverlayManager.showBroadcast(broadcast)
 * 2. UI observes currentBroadcast StateFlow
 * 3. When overlay shown, user accepts/rejects
 * 4. Next broadcast in queue is shown
 * 
 * SCALABILITY FEATURES:
 * - ConcurrentLinkedQueue for thread-safe queue operations
 * - Mutex for synchronized state updates
 * - Max queue size to prevent memory issues
 * - Auto-cleanup of expired broadcasts
 * 
 * FOR BACKEND DEVELOPER:
 * - This is the central hub for all incoming broadcasts
 * - Connect SocketIOService.newBroadcasts to this manager
 * - Call showBroadcast() when WebSocket receives new_broadcast event
 */
object BroadcastOverlayManager {
    
    private const val TAG = "BroadcastOverlayManager"
    
    // Configuration
    private const val MAX_QUEUE_SIZE = 10 // Max broadcasts in queue
    private const val BROADCAST_TIMEOUT_MS = 60 * 1000L // 1 minute (60 seconds)
    
    // Coroutine scope for background operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    
    // Broadcast queue (thread-safe)
    private val broadcastQueue = ConcurrentLinkedQueue<QueuedBroadcast>()
    
    // ==========================================================================
    // AVAILABILITY CHECK - Context for checking online/offline status
    // ==========================================================================
    private var appContext: Context? = null
    
    /**
     * Initialize with application context
     * Call this from MainActivity or Application class
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        timber.log.Timber.i("✅ BroadcastOverlayManager initialized with context")
    }
    
    /**
     * Check if user is available to receive broadcasts
     * Returns false if user has toggled to OFFLINE
     */
    private fun isUserAvailable(): Boolean {
        val context = appContext ?: return true // Default to available if no context
        val availabilityManager = AvailabilityManager.getInstance(context)
        return availabilityManager.isAvailable.value
    }
    
    // ============== STATE FLOWS (Observable by UI) ==============
    
    /**
     * Current broadcast being displayed in overlay
     * null = no overlay shown
     */
    private val _currentBroadcast = MutableStateFlow<BroadcastTrip?>(null)
    val currentBroadcast: StateFlow<BroadcastTrip?> = _currentBroadcast.asStateFlow()
    
    /**
     * Whether overlay should be visible
     */
    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()
    
    /**
     * Remaining time for current broadcast (in seconds)
     */
    private val _remainingTimeSeconds = MutableStateFlow(0)
    val remainingTimeSeconds: StateFlow<Int> = _remainingTimeSeconds.asStateFlow()
    
    /**
     * Queue size for UI display
     */
    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()
    
    /**
     * Current index in the carousel (for < > navigation)
     */
    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    /**
     * Total count of all active broadcasts (for "1 of 5" display)
     */
    private val _totalBroadcastCount = MutableStateFlow(0)
    val totalBroadcastCount: StateFlow<Int> = _totalBroadcastCount.asStateFlow()
    
    /**
     * List of all active broadcasts (for carousel navigation)
     * Newest first for priority display
     */
    private val _allBroadcasts = mutableListOf<BroadcastTrip>()
    
    /**
     * Events for UI actions (accept, reject, expire)
     */
    private val _broadcastEvents = MutableSharedFlow<BroadcastEvent>()
    val broadcastEvents: SharedFlow<BroadcastEvent> = _broadcastEvents.asSharedFlow()
    
    // ============== DATA CLASSES ==============
    
    /**
     * Wrapper for queued broadcasts with metadata
     */
    private data class QueuedBroadcast(
        val broadcast: BroadcastTrip,
        val receivedAt: Long = System.currentTimeMillis(),
        val expiresAt: Long = System.currentTimeMillis() + BROADCAST_TIMEOUT_MS
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
        fun remainingTimeMs(): Long = maxOf(0, expiresAt - System.currentTimeMillis())
    }
    
    /**
     * Events emitted when broadcast state changes
     */
    sealed class BroadcastEvent {
        data class Accepted(val broadcast: BroadcastTrip) : BroadcastEvent()
        data class Rejected(val broadcast: BroadcastTrip) : BroadcastEvent()
        data class Expired(val broadcast: BroadcastTrip) : BroadcastEvent()
        data class NewBroadcast(val broadcast: BroadcastTrip, val queuePosition: Int) : BroadcastEvent()
    }
    
    // ============== PUBLIC API ==============
    
    /**
     * Show a new broadcast in the overlay
     * If another broadcast is showing, this one is queued
     * 
     * NEW BEHAVIOR: Newest broadcasts get PRIORITY
     * - New broadcasts are added to the FRONT of the list
     * - Auto-switches to show the newest broadcast immediately
     * - User can navigate with < > arrows to see older broadcasts
     * 
     * AVAILABILITY CHECK:
     * - If user is OFFLINE (toggled availability off), broadcast is NOT shown
     * - User will not be disturbed when they don't want broadcasts
     * - Backend also excludes offline users, but this is client-side safety
     * 
     * @param broadcast The broadcast to show
     * @return true if shown immediately, false if queued or blocked
     */
    fun showBroadcast(broadcast: BroadcastTrip): Boolean {
        timber.log.Timber.i("╔══════════════════════════════════════════════════════════════╗")
        timber.log.Timber.i("║  📢 SHOW BROADCAST CALLED                                    ║")
        timber.log.Timber.i("╠══════════════════════════════════════════════════════════════╣")
        timber.log.Timber.i("║  Broadcast ID: ${broadcast.broadcastId}")
        timber.log.Timber.i("║  Customer: ${broadcast.customerName}")
        timber.log.Timber.i("║  Trucks Needed: ${broadcast.totalTrucksNeeded}")
        timber.log.Timber.i("║  Is Urgent: ${broadcast.isUrgent}")
        timber.log.Timber.i("║  App Context: ${if (appContext != null) "INITIALIZED" else "NULL - NOT INITIALIZED!"}")
        timber.log.Timber.i("║  Current overlay visible: ${_isOverlayVisible.value}")
        timber.log.Timber.i("║  Current broadcast count: ${_allBroadcasts.size}")
        timber.log.Timber.i("╚══════════════════════════════════════════════════════════════╝")
        
        // =====================================================================
        // AVAILABILITY CHECK - Don't show if user is OFFLINE
        // =====================================================================
        val available = isUserAvailable()
        timber.log.Timber.i("   User availability check: $available")
        if (!available) {
            timber.log.Timber.w("⏸️ Broadcast ${broadcast.broadcastId} NOT shown - user is OFFLINE")
            return false
        }
        
        // Check if already exists (prevent duplicates)
        if (_allBroadcasts.any { it.broadcastId == broadcast.broadcastId } ||
            broadcastQueue.any { it.broadcast.broadcastId == broadcast.broadcastId }) {
            timber.log.Timber.d("⏭️ Broadcast ${broadcast.broadcastId} already exists, skipping")
            return false
        }
        
        // Check total size limit
        if (_allBroadcasts.size >= MAX_QUEUE_SIZE) {
            timber.log.Timber.w("⚠️ List full ($MAX_QUEUE_SIZE), removing oldest")
            _allBroadcasts.removeAt(_allBroadcasts.size - 1) // Remove oldest (last item)
        }
        
        // Add to FRONT of list (newest first for priority)
        _allBroadcasts.add(0, broadcast)
        _totalBroadcastCount.value = _allBroadcasts.size
        
        val queuedBroadcast = QueuedBroadcast(broadcast)
        
        // ALWAYS show the newest broadcast immediately (priority!)
        // This auto-switches to the new broadcast
        scope.launch {
            mutex.withLock {
                _currentIndex.value = 0 // Newest is always at index 0
                showBroadcastInternal(queuedBroadcast)
            }
        }
        
        scope.launch {
            _broadcastEvents.emit(BroadcastEvent.NewBroadcast(broadcast, 0))
        }
        
        timber.log.Timber.i("🎯 New broadcast shown with priority: ${broadcast.broadcastId} (total: ${_allBroadcasts.size})")
        return true
    }
    
    /**
     * Accept the current broadcast
     * Navigates to truck selection screen
     */
    fun acceptCurrentBroadcast() {
        scope.launch {
            mutex.withLock {
                val current = _currentBroadcast.value ?: return@launch
                timber.log.Timber.i("✅ Broadcast ${current.broadcastId} ACCEPTED")
                
                _broadcastEvents.emit(BroadcastEvent.Accepted(current))
                hideOverlayAndShowNext()
            }
        }
    }
    
    /**
     * Reject the current broadcast
     * Shows next in queue or hides overlay
     */
    fun rejectCurrentBroadcast() {
        scope.launch {
            mutex.withLock {
                val current = _currentBroadcast.value ?: return@launch
                timber.log.Timber.i("❌ Broadcast ${current.broadcastId} REJECTED")
                
                _broadcastEvents.emit(BroadcastEvent.Rejected(current))
                hideOverlayAndShowNext()
            }
        }
    }
    
    /**
     * Dismiss overlay without accept/reject (user pressed back)
     * Broadcast goes back to queue (if not expired)
     */
    fun dismissOverlay() {
        scope.launch {
            mutex.withLock {
                val current = _currentBroadcast.value ?: return@launch
                
                // Put back in queue if not expired
                val queuedBroadcast = QueuedBroadcast(current)
                if (!queuedBroadcast.isExpired()) {
                    broadcastQueue.offer(queuedBroadcast)
                    _queueSize.value = broadcastQueue.size
                    timber.log.Timber.d("🔄 Broadcast ${current.broadcastId} put back in queue")
                }
                
                hideOverlayInternal()
            }
        }
    }
    
    /**
     * Clear all broadcasts (on logout)
     */
    fun clearAll() {
        scope.launch {
            mutex.withLock {
                broadcastQueue.clear()
                _currentBroadcast.value = null
                _isOverlayVisible.value = false
                _remainingTimeSeconds.value = 0
                _queueSize.value = 0
                _currentIndex.value = 0
                _allBroadcasts.clear()
                timber.log.Timber.i("🗑️ All broadcasts cleared")
            }
        }
    }
    
    /**
     * Remove a specific broadcast by order ID (when customer cancels)
     * 
     * IMPORTANT: Call this when receiving 'order_cancelled' WebSocket event
     * This ensures cancelled orders are immediately removed from all transporters' screens
     * 
     * @param orderId The order ID to remove
     */
    fun removeBroadcast(orderId: String) {
        scope.launch {
            mutex.withLock {
                // Remove from current if showing
                if (_currentBroadcast.value?.broadcastId == orderId) {
                    timber.log.Timber.i("🚫 Removing CURRENT broadcast (order cancelled): $orderId")
                    hideOverlayAndShowNext()
                    return@withLock
                }
                
                // Remove from queue
                val removed = broadcastQueue.removeAll { it.broadcast.broadcastId == orderId }
                if (removed) {
                    _queueSize.value = broadcastQueue.size
                    timber.log.Timber.i("🚫 Removed broadcast from queue (order cancelled): $orderId")
                }
                
                // Remove from all broadcasts list
                _allBroadcasts.removeAll { it.broadcastId == orderId }
                updateCurrentIndex()
            }
        }
    }
    
    /**
     * Navigate to previous broadcast in carousel
     */
    fun showPreviousBroadcast() {
        scope.launch {
            mutex.withLock {
                if (_allBroadcasts.size <= 1) return@withLock
                
                val newIndex = if (_currentIndex.value > 0) {
                    _currentIndex.value - 1
                } else {
                    _allBroadcasts.size - 1 // Wrap around
                }
                
                _currentIndex.value = newIndex
                val broadcast = _allBroadcasts.getOrNull(newIndex)
                if (broadcast != null) {
                    _currentBroadcast.value = broadcast
                    timber.log.Timber.d("⬅️ Showing previous broadcast: ${broadcast.broadcastId} (${newIndex + 1}/${_allBroadcasts.size})")
                }
            }
        }
    }
    
    /**
     * Navigate to next broadcast in carousel
     */
    fun showNextBroadcast() {
        scope.launch {
            mutex.withLock {
                if (_allBroadcasts.size <= 1) return@withLock
                
                val newIndex = if (_currentIndex.value < _allBroadcasts.size - 1) {
                    _currentIndex.value + 1
                } else {
                    0 // Wrap around
                }
                
                _currentIndex.value = newIndex
                val broadcast = _allBroadcasts.getOrNull(newIndex)
                if (broadcast != null) {
                    _currentBroadcast.value = broadcast
                    timber.log.Timber.d("➡️ Showing next broadcast: ${broadcast.broadcastId} (${newIndex + 1}/${_allBroadcasts.size})")
                }
            }
        }
    }
    
    /**
     * Get current queue for debugging
     */
    fun getQueueInfo(): String {
        return "Queue size: ${broadcastQueue.size}, Current: ${_currentBroadcast.value?.broadcastId}, Visible: ${_isOverlayVisible.value}, AllBroadcasts: ${_allBroadcasts.size}"
    }
    
    private fun updateCurrentIndex() {
        val current = _currentBroadcast.value
        if (current != null && _allBroadcasts.isNotEmpty()) {
            val index = _allBroadcasts.indexOfFirst { it.broadcastId == current.broadcastId }
            if (index >= 0) {
                _currentIndex.value = index
            }
        }
    }
    
    // ============== PRIVATE METHODS ==============
    
    /**
     * Internal method to show broadcast - MUST be called from within mutex.withLock
     * DO NOT call mutex.withLock inside this method - it will cause deadlock!
     */
    private suspend fun showBroadcastInternal(queuedBroadcast: QueuedBroadcast) {
        // NOTE: Mutex is already held by caller - DO NOT lock again!
        
        // Clean expired broadcasts from queue first
        cleanExpiredBroadcasts()
        
        // Check if this broadcast is expired
        if (queuedBroadcast.isExpired()) {
            timber.log.Timber.w("⏰ Broadcast ${queuedBroadcast.broadcast.broadcastId} expired, skipping")
            _broadcastEvents.emit(BroadcastEvent.Expired(queuedBroadcast.broadcast))
            showNextInQueueInternal()
            return
        }
        
        timber.log.Timber.i("╔══════════════════════════════════════════════════════════════╗")
        timber.log.Timber.i("║  🎯 SETTING OVERLAY VISIBLE = TRUE                           ║")
        timber.log.Timber.i("╠══════════════════════════════════════════════════════════════╣")
        timber.log.Timber.i("║  Broadcast: ${queuedBroadcast.broadcast.broadcastId}")
        timber.log.Timber.i("║  Before: _isOverlayVisible = ${_isOverlayVisible.value}")
        timber.log.Timber.i("║  Before: _currentBroadcast = ${_currentBroadcast.value?.broadcastId}")
        
        _currentBroadcast.value = queuedBroadcast.broadcast
        _isOverlayVisible.value = true
        _remainingTimeSeconds.value = (queuedBroadcast.remainingTimeMs() / 1000).toInt()
        
        timber.log.Timber.i("║  After: _isOverlayVisible = ${_isOverlayVisible.value}")
        timber.log.Timber.i("║  After: _currentBroadcast = ${_currentBroadcast.value?.broadcastId}")
        timber.log.Timber.i("║  Timer: ${_remainingTimeSeconds.value}s remaining")
        timber.log.Timber.i("╚══════════════════════════════════════════════════════════════╝")
        
        // Start countdown timer
        startCountdownTimer(queuedBroadcast)
    }
    
    /**
     * Internal method to show next in queue - called when mutex is already held
     */
    private suspend fun showNextInQueueInternal() {
        cleanExpiredBroadcasts()
        
        val next = broadcastQueue.poll()
        _queueSize.value = broadcastQueue.size
        
        if (next != null) {
            timber.log.Timber.d("📤 Showing next broadcast from queue: ${next.broadcast.broadcastId}")
            showBroadcastInternal(next)
        } else {
            timber.log.Timber.d("📭 Queue empty, no more broadcasts")
        }
    }
    
    private fun startCountdownTimer(queuedBroadcast: QueuedBroadcast) {
        scope.launch {
            while (_currentBroadcast.value?.broadcastId == queuedBroadcast.broadcast.broadcastId) {
                val remaining = queuedBroadcast.remainingTimeMs()
                _remainingTimeSeconds.value = (remaining / 1000).toInt()
                
                if (remaining <= 0) {
                    timber.log.Timber.w("⏰ Broadcast ${queuedBroadcast.broadcast.broadcastId} EXPIRED (timeout)")
                    _broadcastEvents.emit(BroadcastEvent.Expired(queuedBroadcast.broadcast))
                    // Need to acquire mutex since we're NOT inside a mutex block here
                    mutex.withLock {
                        hideOverlayInternal()
                        showNextInQueueInternal()
                    }
                    break
                }
                
                kotlinx.coroutines.delay(1000) // Update every second
            }
        }
    }
    
    /**
     * Hide overlay and show next - called from within mutex.withLock
     */
    private suspend fun hideOverlayAndShowNext() {
        // NOTE: Mutex is already held by caller
        hideOverlayInternal()
        showNextInQueueInternal()
    }
    
    /**
     * Internal hide overlay - does NOT acquire mutex
     */
    private fun hideOverlayInternal() {
        timber.log.Timber.i("🔽 Hiding overlay - setting _isOverlayVisible = false")
        _currentBroadcast.value = null
        _isOverlayVisible.value = false
        _remainingTimeSeconds.value = 0
    }
    
    private fun cleanExpiredBroadcasts() {
        val expiredCount = broadcastQueue.count { it.isExpired() }
        if (expiredCount > 0) {
            broadcastQueue.removeAll { it.isExpired() }
            _queueSize.value = broadcastQueue.size
            timber.log.Timber.d("🧹 Cleaned $expiredCount expired broadcasts from queue")
        }
    }
}
