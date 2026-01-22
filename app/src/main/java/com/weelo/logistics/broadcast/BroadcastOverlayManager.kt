package com.weelo.logistics.broadcast

import android.util.Log
import com.weelo.logistics.data.model.BroadcastTrip
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
     * @param broadcast The broadcast to show
     * @return true if shown immediately, false if queued
     */
    fun showBroadcast(broadcast: BroadcastTrip): Boolean {
        Log.i(TAG, "📢 New broadcast received: ${broadcast.broadcastId}")
        
        // Check if already in queue (prevent duplicates)
        if (broadcastQueue.any { it.broadcast.broadcastId == broadcast.broadcastId } || 
            _currentBroadcast.value?.broadcastId == broadcast.broadcastId) {
            Log.d(TAG, "⏭️ Broadcast ${broadcast.broadcastId} already in queue/showing, skipping")
            return false
        }
        
        // Check queue size limit
        if (broadcastQueue.size >= MAX_QUEUE_SIZE) {
            Log.w(TAG, "⚠️ Queue full ($MAX_QUEUE_SIZE), removing oldest")
            broadcastQueue.poll() // Remove oldest
        }
        
        val queuedBroadcast = QueuedBroadcast(broadcast)
        
        // If no current broadcast, show immediately
        if (_currentBroadcast.value == null) {
            scope.launch {
                showBroadcastInternal(queuedBroadcast)
            }
            
            scope.launch {
                _broadcastEvents.emit(BroadcastEvent.NewBroadcast(broadcast, 0))
            }
            return true
        }
        
        // Add to queue
        broadcastQueue.offer(queuedBroadcast)
        _queueSize.value = broadcastQueue.size
        
        Log.i(TAG, "📥 Broadcast ${broadcast.broadcastId} queued (position: ${broadcastQueue.size})")
        
        scope.launch {
            _broadcastEvents.emit(BroadcastEvent.NewBroadcast(broadcast, broadcastQueue.size))
        }
        
        return false
    }
    
    /**
     * Accept the current broadcast
     * Navigates to truck selection screen
     */
    fun acceptCurrentBroadcast() {
        scope.launch {
            mutex.withLock {
                val current = _currentBroadcast.value ?: return@launch
                Log.i(TAG, "✅ Broadcast ${current.broadcastId} ACCEPTED")
                
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
                Log.i(TAG, "❌ Broadcast ${current.broadcastId} REJECTED")
                
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
                    Log.d(TAG, "🔄 Broadcast ${current.broadcastId} put back in queue")
                }
                
                hideOverlay()
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
                Log.i(TAG, "🗑️ All broadcasts cleared")
            }
        }
    }
    
    /**
     * Get current queue for debugging
     */
    fun getQueueInfo(): String {
        return "Queue size: ${broadcastQueue.size}, Current: ${_currentBroadcast.value?.broadcastId}, Visible: ${_isOverlayVisible.value}"
    }
    
    // ============== PRIVATE METHODS ==============
    
    private suspend fun showBroadcastInternal(queuedBroadcast: QueuedBroadcast) {
        mutex.withLock {
            // Clean expired broadcasts from queue first
            cleanExpiredBroadcasts()
            
            // Check if this broadcast is expired
            if (queuedBroadcast.isExpired()) {
                Log.w(TAG, "⏰ Broadcast ${queuedBroadcast.broadcast.broadcastId} expired, skipping")
                _broadcastEvents.emit(BroadcastEvent.Expired(queuedBroadcast.broadcast))
                showNextInQueue()
                return
            }
            
            _currentBroadcast.value = queuedBroadcast.broadcast
            _isOverlayVisible.value = true
            _remainingTimeSeconds.value = (queuedBroadcast.remainingTimeMs() / 1000).toInt()
            
            Log.i(TAG, "🎯 Showing broadcast ${queuedBroadcast.broadcast.broadcastId} (${_remainingTimeSeconds.value}s remaining)")
            
            // Start countdown timer
            startCountdownTimer(queuedBroadcast)
        }
    }
    
    private fun startCountdownTimer(queuedBroadcast: QueuedBroadcast) {
        scope.launch {
            while (_currentBroadcast.value?.broadcastId == queuedBroadcast.broadcast.broadcastId) {
                val remaining = queuedBroadcast.remainingTimeMs()
                _remainingTimeSeconds.value = (remaining / 1000).toInt()
                
                if (remaining <= 0) {
                    Log.w(TAG, "⏰ Broadcast ${queuedBroadcast.broadcast.broadcastId} EXPIRED (timeout)")
                    _broadcastEvents.emit(BroadcastEvent.Expired(queuedBroadcast.broadcast))
                    hideOverlayAndShowNext()
                    break
                }
                
                kotlinx.coroutines.delay(1000) // Update every second
            }
        }
    }
    
    private suspend fun hideOverlayAndShowNext() {
        hideOverlay()
        showNextInQueue()
    }
    
    private fun hideOverlay() {
        _currentBroadcast.value = null
        _isOverlayVisible.value = false
        _remainingTimeSeconds.value = 0
    }
    
    private suspend fun showNextInQueue() {
        cleanExpiredBroadcasts()
        
        val next = broadcastQueue.poll()
        _queueSize.value = broadcastQueue.size
        
        if (next != null) {
            Log.d(TAG, "📤 Showing next broadcast from queue: ${next.broadcast.broadcastId}")
            showBroadcastInternal(next)
        } else {
            Log.d(TAG, "📭 Queue empty, no more broadcasts")
        }
    }
    
    private fun cleanExpiredBroadcasts() {
        val expiredCount = broadcastQueue.count { it.isExpired() }
        if (expiredCount > 0) {
            broadcastQueue.removeAll { it.isExpired() }
            _queueSize.value = broadcastQueue.size
            Log.d(TAG, "🧹 Cleaned $expiredCount expired broadcasts from queue")
        }
    }
}
