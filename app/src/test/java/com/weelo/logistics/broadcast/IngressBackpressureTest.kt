package com.weelo.logistics.broadcast

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

/**
 * F-C-11 — SharedFlow/StateFlow ingress split + missedIds reconcile.
 *
 * Bug: A single 1024-capacity Channel(`DROP_OLDEST`) conflated events with
 * state. Burst overflow silently dropped envelopes — recovery relied on
 * server-side replay window not having compacted, which is fragile.
 *
 * Fix: split ingress into
 *   - `broadcastEventFlow: MutableSharedFlow<BroadcastIngressEnvelope>(
 *         replay = 64, extraBufferCapacity = 512, DROP_OLDEST)` (events)
 *   - `availabilityState: MutableStateFlow<AvailabilityState>` (state — already
 *     present, untouched)
 *
 * On `tryEmit` failure (returns false → buffer full), the dropped id is
 * recorded in a `missedIds: MutableStateFlow<Set<String>>`. The next
 * reconcile pass reads-and-clears `missedIds` and passes the set to the
 * dispatcher repository so the server can backfill.
 *
 * GRACEFUL DEGRADATION: if the backend hasn't been extended to accept
 * `missedIds`, the local missedIds queue still triggers a reconcile so the
 * client recovers via cursor replay; the test asserts the local memory
 * reconciler queue is non-empty after a burst.
 *
 * RED proof: against base, the coordinator does not expose
 * `_missedIds: MutableStateFlow<Set<String>>` and there is no `offerEvent()`
 * function — only the legacy `enqueueIngress()` / `Channel<...>` path. The
 * reflective lookups fail.
 */
class IngressBackpressureTest {

    @Before
    fun resetFlags() {
        BroadcastBuildFlags.setOverridesForTesting(
            coordinatorRefactor = true,
            singleOwnerBuffer = false,
            priorityDrain = false,
            sharedFlowIngress = true
        )
        // Drain any previous state.
        invokeMissedIdsReset()
    }

    @After
    fun teardown() {
        BroadcastBuildFlags.resetOverridesForTesting()
        invokeMissedIdsReset()
    }

    /**
     * 1000-msg burst: replay (64) + extra buffer (512) = 576 capacity. The
     * remaining ~424 envelopes overflow `tryEmit`. Each overflow MUST record
     * its broadcastId into [missedIds] so the next reconcile can backfill.
     *
     * The legacy path silently drops oldest items via Channel DROP_OLDEST —
     * no missedIds tracking exists. This test fails RED for two reasons:
     *  (1) `_missedIds` field does not exist, and
     *  (2) `offerEvent()` does not exist (envelopes go through `enqueueIngress`
     *      which routes to `ingressChannel.trySend` only).
     */
    @Test
    fun `burst overflow records missed ids for reconcile backfill`() {
        val totalBurst = 1_000

        repeat(totalBurst) { i ->
            invokeOfferEvent(broadcastId = "burst-$i")
        }

        val missed = invokeMissedIdsSnapshot()
        assertNotNull("missedIds tracking must exist after F-C-11", missed)
        assertTrue(
            "burst > buffer capacity must record missed ids (got=${missed.size})",
            missed.isNotEmpty()
        )
    }

    /**
     * Reconcile MUST drain the missedIds set when called. Pattern:
     *   `missedIds.getAndUpdate { emptySet() }` then pass to dispatchRepo.
     *
     * After F-C-11 fix the helper [requestReconcileWithMissedIds] returns the
     * draining set so callers can attach it to the /dispatch/replay request.
     * Today this method does not exist on the coordinator.
     */
    @Test
    fun `request reconcile drains missed ids exactly once`() {
        // Seed missedIds via offerEvent overflow.
        repeat(1_000) { i -> invokeOfferEvent(broadcastId = "drain-$i") }
        val seeded = invokeMissedIdsSnapshot()
        assertTrue("seed step must add missed ids", seeded.isNotEmpty())

        val drained = invokeDrainMissedIds()
        assertEquals(
            "drainMissedIds must return the same set seen in the snapshot",
            seeded,
            drained
        )

        val afterDrain = invokeMissedIdsSnapshot()
        assertTrue(
            "after drain missedIds must be empty (read-and-clear semantics)",
            afterDrain.isEmpty()
        )
    }

    // ---------------------------------------------------------------------
    // Reflective contract — these accessors ARE the F-C-11 contract.
    // The implementation must expose them for the fix to be testable.
    // ---------------------------------------------------------------------

    private fun invokeOfferEvent(broadcastId: String) {
        val method = findMethod("offerBroadcastEventForTesting", String::class.java)
        method.invoke(BroadcastFlowCoordinator, broadcastId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeMissedIdsSnapshot(): Set<String> {
        val method = findMethod("missedIdsSnapshotForTesting")
        return method.invoke(BroadcastFlowCoordinator) as Set<String>
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeDrainMissedIds(): Set<String> {
        val method = findMethod("drainMissedIdsForTesting")
        return method.invoke(BroadcastFlowCoordinator) as Set<String>
    }

    private fun invokeMissedIdsReset() {
        try {
            val method = findMethod("resetMissedIdsForTesting")
            method.invoke(BroadcastFlowCoordinator)
        } catch (_: NoSuchMethodException) {
            // Pre-fix the method doesn't exist — that's expected RED behavior.
        }
    }

    private fun findMethod(name: String, vararg paramTypes: Class<*>): Method {
        val method = BroadcastFlowCoordinator::class.java.getDeclaredMethod(name, *paramTypes)
        method.isAccessible = true
        return method
    }
}
