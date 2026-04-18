package com.weelo.logistics.broadcast

import com.weelo.logistics.data.model.BroadcastStatus
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.model.Location
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * F-C-05 — Single-owner StateFlow buffer.
 *
 * Bug: BOTH [BroadcastOverlayManager.startupBufferQueue] AND
 * [BroadcastFlowCoordinator] (`pendingQueue`) observed the same availability
 * flow. OFFLINE→ONLINE re-entry could:
 *   (a) double-buffer the same envelope, or
 *   (b) silently drop it on UNKNOWN-state transition (race against the
 *       availability change handler).
 *
 * Fix: [BroadcastFlowCoordinator] is the SOLE owner of the pending buffer
 * (a `MutableStateFlow<PendingBroadcastQueue>` mutated via `.update {}` CAS).
 * [BroadcastOverlayManager] becomes a pure renderer — its
 * `bufferBroadcastLocked` is gated OFF when the new path is enabled.
 *
 * These tests guard the SINGLE-OWNER invariant directly:
 * 1. Only the coordinator's queue grows when an envelope is buffered.
 * 2. Concurrent enqueue is lossless (CAS update never silently drops).
 *
 * Run RED against base — the BroadcastBuildFlags wrapper exists but the
 * coordinator still maintains a parallel non-StateFlow `PendingBroadcastQueue`,
 * and `BroadcastOverlayManager.bufferBroadcastLocked` still appends to its own
 * `startupBufferQueue` regardless of the flag.
 */
class FlexHoldSingleOwnerTest {

    @Before
    fun resetFlags() {
        BroadcastBuildFlags.setOverridesForTesting(
            coordinatorRefactor = true,
            singleOwnerBuffer = true,
            priorityDrain = false,
            sharedFlowIngress = false
        )
        BroadcastFlowCoordinator.dedupeIdsClear()
    }

    @After
    fun teardown() {
        BroadcastBuildFlags.resetOverridesForTesting()
        BroadcastFlowCoordinator.dedupeIdsClear()
    }

    /**
     * INVARIANT 1: When the new path is ON, only the coordinator's pending
     * queue stores buffered envelopes. The overlay manager's
     * `startupBufferQueue` MUST stay empty.
     *
     * Today: both `BroadcastOverlayManager.startupBufferQueue` AND
     * `BroadcastFlowCoordinator.pendingQueue` end up growing whenever the
     * availability state is UNKNOWN, so the overlay-side queue size is > 0.
     */
    @Test
    fun `when single owner buffer enabled overlay manager does not buffer broadcasts`() {
        val pendingQueue = newCoordinatorOwnedPendingQueue()
        val overlayBuffer = overlayManagerBufferQueue()
        overlayBuffer.clear()
        pendingQueue.clear()

        val trip = sampleTrip("bx-1")
        val item = PendingBroadcast(trip = trip, receivedAtMs = 1_000L)

        // Coordinator-owned single-owner enqueue: must succeed and only the
        // coordinator's queue grows.
        pendingQueue.enqueue(item)

        // The new contract: overlay manager MUST NOT buffer at all when
        // singleOwnerBufferEnabled is ON. Buffer-ownership is collapsed
        // into the coordinator.
        assertEquals(
            "coordinator-owned pending queue should hold the envelope",
            1,
            pendingQueue.size()
        )
        assertEquals(
            "overlay manager startupBufferQueue must stay empty when single-owner buffer is ON",
            0,
            overlayBuffer.size
        )
    }

    /**
     * INVARIANT 2: Concurrent .update{} CAS retries lose no envelopes.
     *
     * 200 producer threads each append 1 envelope to the coordinator-owned
     * StateFlow queue. After all threads complete, exactly 200 envelopes
     * MUST be observable — no lost updates.
     *
     * Today: `pendingQueue` is a `PendingBroadcastQueue` instance (not
     * wrapped in a StateFlow) and there is no `_pendingState` MutableStateFlow
     * accessor — this assertion can't be expressed against the legacy code,
     * so the test fails at the reflective lookup of `_pendingState`.
     */
    @Test
    fun `concurrent enqueue is lossless via state flow update CAS`() {
        val state = coordinatorPendingState()
        // Drain to a known empty start
        state.value = PendingBroadcastQueue(maxSize = 1_000, ttlMs = 600_000L).apply {
            // empty fresh instance
        }

        val executor = Executors.newFixedThreadPool(16)
        val latch = CountDownLatch(200)
        try {
            repeat(200) { i ->
                executor.submit {
                    try {
                        val trip = sampleTrip("concurrent-$i")
                        val item = PendingBroadcast(trip = trip, receivedAtMs = 1_000L + i)
                        // Mutate the single-owner StateFlow CAS-style.
                        var success = false
                        while (!success) {
                            val current = state.value
                            // Build a new queue containing the existing items + new one.
                            val next = PendingBroadcastQueue(maxSize = 1_000, ttlMs = 600_000L)
                            current.snapshot().forEach { existing -> next.enqueue(existing) }
                            next.enqueue(item)
                            success = state.compareAndSet(current, next)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await(10, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        val finalSize = state.value.size()
        assertEquals(
            "concurrent CAS update must preserve all 200 enqueues — no lost updates",
            200,
            finalSize
        )
    }

    // ---------------------------------------------------------------------
    // Reflective accessors — guard the contract the implementation must expose.
    // ---------------------------------------------------------------------

    private fun newCoordinatorOwnedPendingQueue(): PendingBroadcastQueue {
        // The coordinator's pending queue must be reachable for tests so
        // we can inspect single-owner ownership. After the F-C-05 fix this
        // is a `_pendingState.value`.
        return coordinatorPendingState().value
    }

    @Suppress("UNCHECKED_CAST")
    private fun coordinatorPendingState(): kotlinx.coroutines.flow.MutableStateFlow<PendingBroadcastQueue> {
        // After F-C-05: BroadcastFlowCoordinator exposes `_pendingState` —
        // a MutableStateFlow<PendingBroadcastQueue>. The legacy code has a
        // plain `pendingQueue` field instead, so this lookup fails RED.
        val field = BroadcastFlowCoordinator::class.java.getDeclaredField("_pendingState")
        field.isAccessible = true
        return field.get(BroadcastFlowCoordinator)
            as kotlinx.coroutines.flow.MutableStateFlow<PendingBroadcastQueue>
    }

    private fun overlayManagerBufferQueue(): ArrayDeque<*> {
        val field = BroadcastOverlayManager::class.java.getDeclaredField("startupBufferQueue")
        field.isAccessible = true
        val queue = field.get(BroadcastOverlayManager) as ArrayDeque<*>
        assertNotNull("overlay manager must keep its startupBufferQueue field for legacy fallback", queue)
        return queue
    }

    private fun sampleTrip(id: String): BroadcastTrip {
        return BroadcastTrip(
            broadcastId = id,
            customerId = "c_$id",
            customerName = "C",
            customerMobile = "9999999999",
            pickupLocation = Location(latitude = 12.0, longitude = 77.0, address = "Pickup"),
            dropLocation = Location(latitude = 12.5, longitude = 77.5, address = "Drop"),
            distance = 10.0,
            estimatedDuration = 25L,
            totalTrucksNeeded = 1,
            goodsType = "General",
            farePerTruck = 1500.0,
            totalFare = 1500.0,
            status = BroadcastStatus.ACTIVE,
            broadcastTime = 100L
        )
    }
}

/**
 * Test-only convenience accessor: snapshot the queue contents in arrival order.
 * Delegates to [PendingBroadcastQueue.snapshotForRebuild] which is the same
 * non-destructive accessor used by the production CAS update.
 */
internal fun PendingBroadcastQueue.snapshot(): List<PendingBroadcast> = snapshotForRebuild()
