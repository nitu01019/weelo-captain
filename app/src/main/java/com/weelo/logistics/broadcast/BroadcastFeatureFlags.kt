package com.weelo.logistics.broadcast

import android.content.Context
import android.content.SharedPreferences

/**
 * Broadcast feature flags are app-side, runtime-pluggable switches used for safe phased rollout.
 */
interface BroadcastFeatureFlags {
    val broadcastCoordinatorEnabled: Boolean
    val broadcastLocalDeltaApplyEnabled: Boolean
    val broadcastReconcileRateLimitEnabled: Boolean
    val broadcastStrictIdValidationEnabled: Boolean
    val broadcastOverlayInvariantEnforcementEnabled: Boolean
    val broadcastDisableLegacyWebsocketPath: Boolean
    val broadcastOverlayWatchdogEnabled: Boolean
    val captainCancelEventStrictDedupeEnabled: Boolean
    val captainCanonicalCancelAliasesEnabled: Boolean
    /** When false, the map section in broadcast overlay is completely hidden. */
    val broadcastOverlayMapEnabled: Boolean
    /** When enabled, coordinator performs snapshot reconcile to patch stale broadcast payloads. */
    val captainReconcileSnapshotEnabled: Boolean
    /** Enables map-safe/fallback rendering path for overlay critical actions. */
    val captainOverlaySafeRenderEnabled: Boolean
    /** Enables deterministic burst queue policy for multiple concurrent broadcasts. */
    val captainBurstQueueModeEnabled: Boolean
}

interface BroadcastFeatureFlagProvider {
    fun getFlags(): BroadcastFeatureFlags
}

data class DefaultBroadcastFeatureFlags(
    override val broadcastCoordinatorEnabled: Boolean = true,
    override val broadcastLocalDeltaApplyEnabled: Boolean = true,
    override val broadcastReconcileRateLimitEnabled: Boolean = true,
    override val broadcastStrictIdValidationEnabled: Boolean = true,
    override val broadcastOverlayInvariantEnforcementEnabled: Boolean = true,
    override val broadcastDisableLegacyWebsocketPath: Boolean = false,
    override val broadcastOverlayWatchdogEnabled: Boolean = true,
    override val captainCancelEventStrictDedupeEnabled: Boolean = true,
    override val captainCanonicalCancelAliasesEnabled: Boolean = true,
    override val broadcastOverlayMapEnabled: Boolean = true,
    override val captainReconcileSnapshotEnabled: Boolean = true,
    override val captainOverlaySafeRenderEnabled: Boolean = true,
    override val captainBurstQueueModeEnabled: Boolean = true
) : BroadcastFeatureFlags

private class SharedPrefsBroadcastFeatureFlagProvider(
    private val prefs: SharedPreferences
) : BroadcastFeatureFlagProvider {
    override fun getFlags(): BroadcastFeatureFlags {
        return DefaultBroadcastFeatureFlags(
            broadcastCoordinatorEnabled = prefs.getBoolean(KEY_COORDINATOR_ENABLED, true),
            broadcastLocalDeltaApplyEnabled = prefs.getBoolean(KEY_LOCAL_DELTA_APPLY_ENABLED, true),
            broadcastReconcileRateLimitEnabled = prefs.getBoolean(KEY_RECONCILE_RATE_LIMIT_ENABLED, true),
            broadcastStrictIdValidationEnabled = prefs.getBoolean(KEY_STRICT_ID_VALIDATION_ENABLED, true),
            broadcastOverlayInvariantEnforcementEnabled = prefs.getBoolean(KEY_OVERLAY_INVARIANT_ENFORCEMENT_ENABLED, true),
            broadcastDisableLegacyWebsocketPath = prefs.getBoolean(KEY_DISABLE_LEGACY_WEBSOCKET_PATH, false),
            broadcastOverlayWatchdogEnabled = prefs.getBoolean(KEY_OVERLAY_WATCHDOG_ENABLED, true),
            captainCancelEventStrictDedupeEnabled = prefs.getBoolean(KEY_CAPTAIN_CANCEL_EVENT_STRICT_DEDUPE_ENABLED, true),
            captainCanonicalCancelAliasesEnabled = prefs.getBoolean(KEY_CAPTAIN_CANONICAL_CANCEL_ALIASES_ENABLED, true),
            broadcastOverlayMapEnabled = prefs.getBoolean(KEY_OVERLAY_MAP_ENABLED, true),
            captainReconcileSnapshotEnabled = prefs.getBoolean(KEY_CAPTAIN_RECONCILE_SNAPSHOT_ENABLED, true),
            captainOverlaySafeRenderEnabled = prefs.getBoolean(KEY_CAPTAIN_OVERLAY_SAFE_RENDER_ENABLED, true),
            captainBurstQueueModeEnabled = prefs.getBoolean(KEY_CAPTAIN_BURST_QUEUE_MODE_ENABLED, true)
        )
    }

    private companion object {
        const val KEY_COORDINATOR_ENABLED = "broadcast_coordinator_enabled"
        const val KEY_LOCAL_DELTA_APPLY_ENABLED = "broadcast_local_delta_apply_enabled"
        const val KEY_RECONCILE_RATE_LIMIT_ENABLED = "broadcast_reconcile_rate_limit_enabled"
        const val KEY_STRICT_ID_VALIDATION_ENABLED = "broadcast_strict_id_validation_enabled"
        const val KEY_OVERLAY_INVARIANT_ENFORCEMENT_ENABLED = "broadcast_overlay_invariant_enforcement_enabled"
        const val KEY_DISABLE_LEGACY_WEBSOCKET_PATH = "broadcast_disable_legacy_websocket_path"
        const val KEY_OVERLAY_WATCHDOG_ENABLED = "broadcast_overlay_watchdog_enabled"
        const val KEY_CAPTAIN_CANCEL_EVENT_STRICT_DEDUPE_ENABLED = "captain_cancel_event_strict_dedupe_enabled"
        const val KEY_CAPTAIN_CANONICAL_CANCEL_ALIASES_ENABLED = "captain_canonical_cancel_aliases_enabled"
        const val KEY_OVERLAY_MAP_ENABLED = "broadcast_overlay_map_enabled"
        const val KEY_CAPTAIN_RECONCILE_SNAPSHOT_ENABLED = "captain_reconcile_snapshot_enabled"
        const val KEY_CAPTAIN_OVERLAY_SAFE_RENDER_ENABLED = "captain_overlay_safe_render_enabled"
        const val KEY_CAPTAIN_BURST_QUEUE_MODE_ENABLED = "captain_burst_queue_mode_enabled"
    }
}

/**
 * Global access point used by app/runtime code.
 */
object BroadcastFeatureFlagsRegistry {
    private const val PREFS_NAME = "weelo_prefs"

    @Volatile
    private var provider: BroadcastFeatureFlagProvider = object : BroadcastFeatureFlagProvider {
        override fun getFlags(): BroadcastFeatureFlags = DefaultBroadcastFeatureFlags()
    }

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        provider = SharedPrefsBroadcastFeatureFlagProvider(prefs)
    }

    fun setProviderForTesting(testProvider: BroadcastFeatureFlagProvider) {
        provider = testProvider
    }

    fun current(): BroadcastFeatureFlags = provider.getFlags()
}
