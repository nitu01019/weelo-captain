package com.weelo.logistics.broadcast

import com.weelo.logistics.BuildConfig

/**
 * F-C-05/F-C-10/F-C-11 — Build-time feature flags with test override hook.
 *
 * Production reads `BuildConfig.FF_*` (compile-time `static final boolean`).
 * Tests override via [setOverridesForTesting] / [resetOverridesForTesting].
 *
 * The umbrella flag [coordinatorRefactorEnabled] AND the per-feature flag
 * must both be ON for the new code path to activate. This lets the umbrella
 * act as an emergency kill-switch covering all three sub-features.
 *
 * Defaults: ALL OFF — legacy paths are preserved under
 * `if (!BroadcastBuildFlags.singleOwnerBufferEnabled) { legacy } else { new }`.
 */
object BroadcastBuildFlags {

    @Volatile private var override: Overrides? = null

    private data class Overrides(
        val coordinatorRefactor: Boolean,
        val singleOwnerBuffer: Boolean,
        val priorityDrain: Boolean,
        val sharedFlowIngress: Boolean
    )

    /** Umbrella kill-switch — when OFF, all three sub-features fall back to legacy. */
    val coordinatorRefactorEnabled: Boolean
        get() = override?.coordinatorRefactor ?: BuildConfig.FF_BROADCAST_COORDINATOR_REFACTOR

    /**
     * F-C-05 — single-owner StateFlow buffer in BroadcastFlowCoordinator.
     * BroadcastOverlayManager.startupBufferQueue stops accepting new entries
     * when this is ON; coordinator becomes the sole owner.
     */
    val singleOwnerBufferEnabled: Boolean
        get() = coordinatorRefactorEnabled &&
            (override?.singleOwnerBuffer ?: BuildConfig.FF_BROADCAST_SINGLE_OWNER_BUFFER)

    /**
     * F-C-10 — priority-sorted drain in BroadcastOverlayManager.flushBufferedBroadcasts
     * (or coordinator-owned flushPendingBuffer when bundled with F-C-05).
     */
    val priorityDrainEnabled: Boolean
        get() = coordinatorRefactorEnabled &&
            (override?.priorityDrain ?: BuildConfig.FF_BROADCAST_PRIORITY_DRAIN)

    /**
     * F-C-11 — SharedFlow/StateFlow split for ingress + missedIds reconcile.
     * When ON, BroadcastFlowCoordinator routes envelopes through a SharedFlow
     * with replay + extraBuffer, recording missed ids on overflow and asking
     * the reconciler to backfill them via /dispatch/replay.
     */
    val sharedFlowIngressEnabled: Boolean
        get() = coordinatorRefactorEnabled &&
            (override?.sharedFlowIngress ?: BuildConfig.FF_BROADCAST_SHARED_FLOW_INGRESS)

    /**
     * Test-only override. Pass `null` for any param to fall back to BuildConfig.
     * Always pair with [resetOverridesForTesting] in @After.
     */
    fun setOverridesForTesting(
        coordinatorRefactor: Boolean = BuildConfig.FF_BROADCAST_COORDINATOR_REFACTOR,
        singleOwnerBuffer: Boolean = BuildConfig.FF_BROADCAST_SINGLE_OWNER_BUFFER,
        priorityDrain: Boolean = BuildConfig.FF_BROADCAST_PRIORITY_DRAIN,
        sharedFlowIngress: Boolean = BuildConfig.FF_BROADCAST_SHARED_FLOW_INGRESS
    ) {
        override = Overrides(
            coordinatorRefactor = coordinatorRefactor,
            singleOwnerBuffer = singleOwnerBuffer,
            priorityDrain = priorityDrain,
            sharedFlowIngress = sharedFlowIngress
        )
    }

    fun resetOverridesForTesting() {
        override = null
    }
}
