package com.weelo.logistics.broadcast

import android.app.Application
import android.content.Context
import com.weelo.logistics.core.notification.BroadcastSoundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * =============================================================================
 * F-C-06 — App-scoped audio controller for the broadcast overlay.
 * =============================================================================
 *
 * WHY THIS EXISTS (SOL-7 §S-06, INDEX.md:1568-1580):
 *   The previous implementation lived inside a `DisposableEffect` in
 *   [BroadcastOverlayScreen]. `onDispose` is triggered by Compose only AFTER
 *   `AnimatedVisibility`'s 150 ms exit animation completes — so on FCM-cancel
 *   the sound kept playing for ~150 ms past the logical dismiss.
 *
 *   This controller moves audio lifecycle out of composition entirely. It
 *   listens to [BroadcastFlowCoordinator.currentBroadcast] for the edges
 *   null→present (play) and present→null (stop), and it additionally
 *   subscribes to [BroadcastCoordinatorEvent.Dismissed] so dismissal fires
 *   `stopImmediate` synchronously — before the animation exit starts.
 *
 * INDUSTRY PATTERN:
 *   Signal-Android's app-scoped audio owner. Composition renders visuals;
 *   audio owner renders sound. The two lifecycles are intentionally decoupled
 *   because their natural durations differ (sound is "logical dismiss",
 *   animation is "visual dismiss" + 150 ms exit).
 *
 * FEATURE FLAG (default OFF):
 *   [com.weelo.logistics.BuildConfig.FF_BROADCAST_AUDIO_CONTROLLER]. When OFF,
 *   [init] is a no-op and the legacy `DisposableEffect` in
 *   [BroadcastOverlayScreen] owns the sound. When ON, composition drops the
 *   `DisposableEffect` body and this controller owns both play and stop.
 *
 * THREAD SAFETY:
 *   Single background coroutine (SupervisorJob + Dispatchers.Default). The
 *   underlying [BroadcastSoundService] is already thread-safe via its own
 *   synchronized MediaPlayer ownership.
 * =============================================================================
 */
object BroadcastAudioController {

    private const val TAG = "BroadcastAudioCtl"

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var initialized = false
    @Volatile private var appContext: Context? = null
    @Volatile private var currentJob: Job? = null
    @Volatile private var dismissJob: Job? = null
    @Volatile private var lastStartedBroadcastId: String? = null

    /**
     * Initialize the controller. Wired from [com.weelo.logistics.WeeloApp.onCreate]
     * behind the `FF_BROADCAST_AUDIO_CONTROLLER` BuildConfig flag. Calling this
     * multiple times is safe — the second call is a no-op.
     */
    fun init(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = application.applicationContext
            initialized = true
        }
        timber.log.Timber.tag(TAG).i("🔊 BroadcastAudioController initialized (app-scoped)")
        observeCurrentBroadcast()
        observeDismissedEvents()
    }

    /**
     * Synchronous stop — exposed for the tests and the legacy
     * `DisposableEffect` fall-back (when callers want to explicitly hand off
     * ownership on flag flips at runtime).
     *
     * Completes in <10 ms, comfortably inside the 150 ms
     * `AnimatedVisibility` exit animation. This is the core contract.
     */
    fun stopImmediate() {
        val ctx = appContext ?: return
        try {
            BroadcastSoundService.getInstance(ctx).stopSound()
            lastStartedBroadcastId = null
        } catch (t: Throwable) {
            timber.log.Timber.tag(TAG).w(t, "stopImmediate failed (non-fatal)")
        }
    }

    // ------------------------------------------------------------------
    // Internal — one coroutine per observed stream
    // ------------------------------------------------------------------

    // Note: in this codebase the "current broadcast" StateFlow lives on
    // BroadcastOverlayManager (the render-owner), not BroadcastFlowCoordinator
    // (the ingress-owner). They will be unified under F-C-05, but until that
    // lands we subscribe to BroadcastOverlayManager for the current-broadcast
    // edge and to BroadcastFlowCoordinator.events for the Dismissed event so
    // the controller lines up with SOL-7 §S-06's dismissal contract today.
    private fun observeCurrentBroadcast() {
        currentJob?.cancel()
        currentJob = appScope.launch {
            BroadcastOverlayManager.currentBroadcast.collect { trip ->
                val ctx = appContext ?: return@collect
                if (trip == null) {
                    // Logical dismiss edge — stop synchronously.
                    stopImmediate()
                } else if (trip.broadcastId != lastStartedBroadcastId) {
                    lastStartedBroadcastId = trip.broadcastId
                    try {
                        BroadcastSoundService
                            .getInstance(ctx)
                            .playLoopingSound(isUrgent = trip.isUrgent)
                    } catch (t: Throwable) {
                        timber.log.Timber
                            .tag(TAG)
                            .w(t, "playLoopingSound failed for %s", trip.broadcastId)
                    }
                }
            }
        }
    }

    private fun observeDismissedEvents() {
        dismissJob?.cancel()
        dismissJob = appScope.launch {
            BroadcastFlowCoordinator
                .events
                .filterIsInstance<BroadcastCoordinatorEvent.Dismissed>()
                .collect { dismissed ->
                    if (dismissed.id == lastStartedBroadcastId) {
                        // Beat the 150 ms AnimatedVisibility exit race: stop
                        // synchronously on the logical-dismiss edge rather
                        // than waiting for composition teardown.
                        stopImmediate()
                    }
                }
        }
    }

    /**
     * Test-only — reset the internal state so unit tests can re-init without
     * residue. Not called from production code.
     */
    internal fun resetForTesting() {
        currentJob?.cancel()
        dismissJob?.cancel()
        currentJob = null
        dismissJob = null
        initialized = false
        appContext = null
        lastStartedBroadcastId = null
    }
}
