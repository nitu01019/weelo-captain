package com.weelo.logistics.broadcast

import android.content.Context
import android.content.SharedPreferences
import com.weelo.logistics.BuildConfig

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
}

interface BroadcastFeatureFlagProvider {
    fun getFlags(): BroadcastFeatureFlags
}

data class DefaultBroadcastFeatureFlags(
    override val broadcastCoordinatorEnabled: Boolean = BuildConfig.DEBUG,
    override val broadcastLocalDeltaApplyEnabled: Boolean = true,
    override val broadcastReconcileRateLimitEnabled: Boolean = true,
    override val broadcastStrictIdValidationEnabled: Boolean = true,
    override val broadcastOverlayInvariantEnforcementEnabled: Boolean = true,
    override val broadcastDisableLegacyWebsocketPath: Boolean = BuildConfig.DEBUG
) : BroadcastFeatureFlags

private class SharedPrefsBroadcastFeatureFlagProvider(
    private val prefs: SharedPreferences
) : BroadcastFeatureFlagProvider {
    override fun getFlags(): BroadcastFeatureFlags {
        return DefaultBroadcastFeatureFlags(
            broadcastCoordinatorEnabled = prefs.getBoolean(KEY_COORDINATOR_ENABLED, BuildConfig.DEBUG),
            broadcastLocalDeltaApplyEnabled = prefs.getBoolean(KEY_LOCAL_DELTA_APPLY_ENABLED, true),
            broadcastReconcileRateLimitEnabled = prefs.getBoolean(KEY_RECONCILE_RATE_LIMIT_ENABLED, true),
            broadcastStrictIdValidationEnabled = prefs.getBoolean(KEY_STRICT_ID_VALIDATION_ENABLED, true),
            broadcastOverlayInvariantEnforcementEnabled = prefs.getBoolean(KEY_OVERLAY_INVARIANT_ENFORCEMENT_ENABLED, true),
            broadcastDisableLegacyWebsocketPath = prefs.getBoolean(KEY_DISABLE_LEGACY_WEBSOCKET_PATH, BuildConfig.DEBUG)
        )
    }

    private companion object {
        const val KEY_COORDINATOR_ENABLED = "broadcast_coordinator_enabled"
        const val KEY_LOCAL_DELTA_APPLY_ENABLED = "broadcast_local_delta_apply_enabled"
        const val KEY_RECONCILE_RATE_LIMIT_ENABLED = "broadcast_reconcile_rate_limit_enabled"
        const val KEY_STRICT_ID_VALIDATION_ENABLED = "broadcast_strict_id_validation_enabled"
        const val KEY_OVERLAY_INVARIANT_ENFORCEMENT_ENABLED = "broadcast_overlay_invariant_enforcement_enabled"
        const val KEY_DISABLE_LEGACY_WEBSOCKET_PATH = "broadcast_disable_legacy_websocket_path"
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
