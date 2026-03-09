package com.weelo.logistics.broadcast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * =============================================================================
 * BROADCAST STATE SYNC — Shared singleton for cross-screen coordination
 * =============================================================================
 *
 * Both the Overlay and the Request Screen are independent, but certain actions
 * on one screen must propagate to the other:
 *
 *   1. IGNORE on Request Screen → also remove from Overlay
 *   2. FULLY ACCEPTED (drivers assigned) → remove from Request Screen list
 *
 * This singleton is in-memory only — no persistence needed. On app restart,
 * the server is the source of truth for which broadcasts are active.
 *
 * Thread-safe: MutableStateFlow is atomic.
 * =============================================================================
 */
object BroadcastStateSync {

    /**
     * Broadcast IDs that have been fully accepted (truck hold confirmed +
     * drivers assigned). The Request Screen observes this to remove completed
     * broadcasts from its noticeboard list.
     */
    private val _fullyAccepted = MutableStateFlow<Set<String>>(emptySet())
    val fullyAccepted: StateFlow<Set<String>> = _fullyAccepted.asStateFlow()

    /**
     * Broadcast IDs ignored from the Request Screen. The Overlay should
     * remove these via BroadcastOverlayManager.removeEverywhere() at the
     * call-site — this flow exists for any additional observers.
     */
    private val _ignoredByList = MutableStateFlow<Set<String>>(emptySet())
    val ignoredByList: StateFlow<Set<String>> = _ignoredByList.asStateFlow()

    /**
     * Mark a broadcast as fully accepted (drivers assigned successfully).
     * Request Screen will observe this and remove the broadcast from its list.
     */
    fun markFullyAccepted(broadcastId: String) {
        if (broadcastId.isBlank()) return
        timber.log.Timber.i("🔄 [StateSync] Marked fully accepted: $broadcastId")
        _fullyAccepted.update { it + broadcastId }
    }

    /**
     * Mark a broadcast as ignored from the Request Screen.
     * Caller should also call BroadcastOverlayManager.removeEverywhere()
     * to remove from overlay queue.
     */
    fun markIgnoredByList(broadcastId: String) {
        if (broadcastId.isBlank()) return
        timber.log.Timber.i("🔄 [StateSync] Marked ignored by list: $broadcastId")
        _ignoredByList.update { it + broadcastId }
    }

    /**
     * Clear a broadcast from both sets. Called after the broadcast has been
     * removed from all screens.
     */
    fun clear(broadcastId: String) {
        _fullyAccepted.update { it - broadcastId }
        _ignoredByList.update { it - broadcastId }
    }

    /**
     * Clear all state (e.g. on logout or session reset).
     */
    fun clearAll() {
        _fullyAccepted.value = emptySet()
        _ignoredByList.value = emptySet()
    }
}
