package com.weelo.logistics.broadcast

/**
 * Client-side broadcast dedup layer — prevents duplicate display when the same
 * broadcast arrives via BOTH Socket.IO and FCM (dual-channel delivery).
 *
 * Consolidation (F-C-02 / W1-2): this object is a thin delegate over
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

    /** Legacy cross-channel namespace used by pre-W1-2 builds. */
    internal const val LEGACY_EVENT_CLASS = "broadcast"
    /** Legacy payload-version used by pre-W1-2 builds. */
    internal const val LEGACY_PAYLOAD_VERSION = 1

    /**
     * Legacy signature retained for binary compatibility with call-sites that
     * do not yet know the event class / payload version (notification-open
     * recovery paths, older tests). New ingress points should prefer
     * [BroadcastDedupKey.admit] which carries the semantic event class and
     * payload version and performs the 1-release dual-probe compat shim.
     *
     * Returns true on first arrival, false when already seen inside the TTL.
     */
    @Synchronized
    fun isNew(broadcastId: String): Boolean {
        if (broadcastId.isBlank()) return false
        val key = normalizeDedupKey(LEGACY_EVENT_CLASS, broadcastId, LEGACY_PAYLOAD_VERSION)
        return BroadcastFlowCoordinator.dedupeIdsIsNew(key)
    }

    /**
     * Explicit rollback — used when a caller wants a later retry of the same
     * id to be treated as new (e.g., availability flipped OFFLINE between
     * register and apply).
     *
     * W1-2 note: removes BOTH the legacy and the pre-ingress-namespace keys
     * so that a subsequent [isNew] or [BroadcastDedupKey.admit] call is
     * treated as a first arrival regardless of which scheme wrote the entry.
     */
    @Synchronized
    fun forget(broadcastId: String) {
        if (broadcastId.isBlank()) return
        BroadcastFlowCoordinator.dedupeIdsRemove(
            normalizeDedupKey(LEGACY_EVENT_CLASS, broadcastId, LEGACY_PAYLOAD_VERSION)
        )
        // Also forget any unified pre-ingress entries so a later retry is
        // treated as new on both channels.
        for (eventClass in BroadcastEventClass.values()) {
            BroadcastFlowCoordinator.dedupeIdsRemove(
                BroadcastDedupKey.buildPreIngress(eventClass, broadcastId, null)
            )
        }
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

/**
 * W1-2 / F-C-02 — single source of truth for composite dedup keys.
 *
 * Replaces the hand-rolled key builders that drifted across the codebase
 * (BroadcastFlowCoordinator.dedupeKey and BroadcastDedup's hand-concatenated
 * "broadcast|id|1"). Every ingress point that knows its event class + payload
 * version must funnel through this helper so the schema stays consistent and
 * future edits only need to touch one place.
 *
 * Two key namespaces coexist intentionally:
 *
 * - **Unified (coordinator-authoritative)** — `${eventClass.name}|id|version`
 *   (e.g., `NEW_BROADCAST|order_123|v0`). Produced by [build] and written by
 *   the coordinator's internal dedup at its single-owner decision point.
 *
 * - **Pre-ingress (cross-channel short-circuit)** — `pre:${unified}`. Produced
 *   by [buildPreIngress] and used by socket/FCM gates BEFORE the envelope
 *   reaches the coordinator. The `pre:` prefix keeps it from colliding with
 *   the coordinator's authoritative key, so the first arrival is still
 *   processed by the coordinator while subsequent arrivals on the other
 *   channel short-circuit at the gate.
 *
 * The 1-release compatibility shim in [admit] additionally dual-probes the
 * pre-W1-2 legacy key `"broadcast|id|1"` so that LRU entries written by an
 * older build are still observed as "seen" by a post-W1-2 build during the
 * 10-minute TTL overlap after a deploy.
 *
 * Industry references:
 * - FCM `collapse_key` / APNs `apns-collapse-id`: compound-key dedup with
 *   event class + entity id + version to prevent cross-class collisions.
 * - Sohil Ladhani (Apr 2026): "encode event class + entity id + version as
 *   a dedup key; store with TTL; skip on duplicate."
 */
object BroadcastDedupKey {

    private const val PRE_INGRESS_PREFIX = "pre:"

    /**
     * Build the coordinator-authoritative unified dedup key.
     *
     * Format: `"${eventClass.name}|${id.trim()}|${version ?: "v0"}"`
     *
     * This is the same string format previously computed inline by
     * `BroadcastFlowCoordinator.dedupeKey` — that function now delegates here.
     */
    fun build(
        eventClass: BroadcastEventClass,
        id: String,
        version: String?
    ): String {
        val safeVersion = version?.takeIf { it.isNotBlank() } ?: "v0"
        return "${eventClass.name}|${id.trim()}|$safeVersion"
    }

    /**
     * Build the pre-ingress namespace key — same composite shape as [build]
     * but with a `pre:` prefix so it lives in a separate LRU namespace from
     * the coordinator's authoritative key. This is what socket/FCM gates
     * write on cross-channel short-circuit.
     */
    fun buildPreIngress(
        eventClass: BroadcastEventClass,
        id: String,
        version: String?
    ): String = PRE_INGRESS_PREFIX + build(eventClass, id, version)

    /**
     * Legacy pre-W1-2 key shape used by `BroadcastDedup.isNew(id)` — kept
     * for the 1-release dual-probe compatibility shim so LRU entries
     * written by an older build are still observed as duplicates by a
     * post-W1-2 build during the 10-minute TTL window.
     */
    fun buildLegacy(id: String): String =
        normalizeDedupKey(
            BroadcastDedup.LEGACY_EVENT_CLASS,
            id,
            BroadcastDedup.LEGACY_PAYLOAD_VERSION
        )

    /**
     * Cross-channel pre-ingress admission.
     *
     * Returns `true` iff this (eventClass, id, version) tuple has NOT been
     * admitted via any of the two probed keys:
     *   1. pre-ingress namespace — `"pre:${eventClass.name}|id|version"`
     *   2. legacy cross-channel — `"broadcast|id|1"`
     *
     * Both keys are upserted on a successful admission so a later arrival
     * on the other channel (or from a legacy code path) is observed as a
     * duplicate.
     *
     * Blank ids are rejected without consuming an LRU slot.
     */
    fun admit(
        eventClass: BroadcastEventClass,
        id: String,
        version: String?
    ): Boolean {
        val normalized = id.trim()
        if (normalized.isEmpty()) return false
        val preIngressKey = buildPreIngress(eventClass, normalized, version)
        val legacyKey = buildLegacy(normalized)

        // Dual-probe: probe both keys through the coordinator's LRU. Both
        // keys are upserted regardless of outcome — this means the first
        // arrival populates both namespaces so a later arrival on either
        // channel scheme is observed as a duplicate.
        val preIngressFresh = BroadcastFlowCoordinator.dedupeIdsIsNew(preIngressKey)
        val legacyFresh = BroadcastFlowCoordinator.dedupeIdsIsNew(legacyKey)
        return preIngressFresh && legacyFresh
    }
}
