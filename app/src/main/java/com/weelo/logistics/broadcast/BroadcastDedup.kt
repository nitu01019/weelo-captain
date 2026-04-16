package com.weelo.logistics.broadcast

/**
 * Client-side broadcast dedup layer — prevents duplicate display when the same
 * broadcast arrives via BOTH Socket.IO and FCM (dual-channel delivery).
 *
 * Consolidation (F-C-02): this object is a thin delegate over
 * [BroadcastFlowCoordinator]'s single coordinator-owned LRU+TTL store.
 * Previously it held its own 500-entry FIFO `LinkedHashSet`, which drifted
 * from the coordinator's 10k LRU and from [BroadcastOverlayManager]'s 2k LRU.
 *
 * Industry pattern: "Assign a deduplication key to each notification before
 * it enters the delivery pipeline. Encode user/event/entity/version, store
 * with TTL, skip on duplicate." (Sohil Ladhani, Apr 2026)
 *
 * Thread-safe via the coordinator's internal `synchronized(lock)` in
 * [LruIdSet]. `@Synchronized` retained here for call-site compatibility.
 */
object BroadcastDedup {

    private const val EVENT_CLASS = "broadcast"
    private const val PAYLOAD_VERSION = 1

    /**
     * Returns true if this broadcastId has NOT been seen before (first arrival).
     * Returns false if duplicate (already processed via another channel).
     */
    @Synchronized
    fun isNew(broadcastId: String): Boolean {
        if (broadcastId.isBlank()) return false
        val key = normalizeDedupKey(EVENT_CLASS, broadcastId, PAYLOAD_VERSION)
        return BroadcastFlowCoordinator.dedupeIdsIsNew(key)
    }

    /**
     * Explicit rollback — used when a caller wants a later retry of the same
     * id to be treated as new (e.g., availability flipped OFFLINE between
     * register and apply).
     */
    @Synchronized
    fun forget(broadcastId: String) {
        if (broadcastId.isBlank()) return
        val key = normalizeDedupKey(EVENT_CLASS, broadcastId, PAYLOAD_VERSION)
        BroadcastFlowCoordinator.dedupeIdsRemove(key)
    }

    /** Clear all entries (e.g., on logout). Delegates to the coordinator. */
    @Synchronized
    fun clear() {
        // NOTE: this clears the coordinator's full cross-channel LRU which
        // is the desired logout semantics — dedup history should not bleed
        // across sessions.
        BroadcastFlowCoordinator.dedupeIdsClear()
    }
}
