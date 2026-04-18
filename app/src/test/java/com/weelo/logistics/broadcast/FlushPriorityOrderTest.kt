package com.weelo.logistics.broadcast

import com.weelo.logistics.data.model.BroadcastStatus
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.model.Location
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * F-C-10 — Priority-sorted buffer drain.
 *
 * Bug: [BroadcastOverlayManager.flushBufferedBroadcasts] used a FIFO drain
 * (`while (removeFirstOrNull)`); only the FIRST flushed item was force-shown
 * regardless of urgency or expiry. Per Uber airport-queue research, the
 * earliest-deadline / urgent broadcast should always be the one promoted to
 * the foreground when multiple buffered envelopes drain together.
 *
 * Fix: drain becomes `snapshot.sortedWith(queuePriorityComparator)` and the
 * first sorted item is force-shown.
 *
 * The implementation lives behind [BroadcastBuildFlags.priorityDrainEnabled].
 *
 * RED proof: against base, the priority-drain helper does not exist, so the
 * reflective method lookup fails. After GREEN, the helper exists and ranks
 * the urgent earliest-deadline broadcast first.
 */
class FlushPriorityOrderTest {

    @Before
    fun resetFlags() {
        BroadcastBuildFlags.setOverridesForTesting(
            coordinatorRefactor = true,
            singleOwnerBuffer = false,
            priorityDrain = true,
            sharedFlowIngress = false
        )
    }

    @After
    fun teardown() {
        BroadcastBuildFlags.resetOverridesForTesting()
    }

    /**
     * Buffer contains three items received over the same 200ms window:
     *   - bx-40s    (expires 40s out, normal priority, fare 2000)
     *   - bx-urgent (expires 5s out, urgent flag, fare 3000)
     *   - bx-60s    (expires 60s out, normal priority, fare 1500)
     *
     * Earliest-deadline + urgent first → bx-urgent must be at index 0 of the
     * sorted drain output. Today the FIFO drain returns `bx-40s` first, so
     * this assertion fails RED.
     */
    @Test
    fun `priority drain promotes urgent earliest-deadline first`() {
        val now = 100_000L
        val items = listOf(
            BroadcastOverlayManager.BufferedBroadcast(
                trip = sampleTrip("bx-40s", expiryOffsetMs = 40_000L, urgent = false, fare = 2000.0),
                receivedAtMs = now
            ),
            BroadcastOverlayManager.BufferedBroadcast(
                trip = sampleTrip("bx-urgent", expiryOffsetMs = 5_000L, urgent = true, fare = 3000.0),
                receivedAtMs = now + 100L
            ),
            BroadcastOverlayManager.BufferedBroadcast(
                trip = sampleTrip("bx-60s", expiryOffsetMs = 60_000L, urgent = false, fare = 1500.0),
                receivedAtMs = now - 50L
            )
        )

        val sorted = invokeDrainSort(items, nowMs = now)

        assertEquals(
            "first drained envelope must be the urgent earliest-deadline one",
            "bx-urgent",
            sorted.first().trip.broadcastId
        )
        assertEquals(
            "drain must preserve all envelopes — none lost in priority sort",
            3,
            sorted.size
        )
        // Tail order: 40s before 60s (earliest deadline first)
        assertEquals(
            "after the urgent item, earliest deadline wins",
            "bx-40s",
            sorted[1].trip.broadcastId
        )
        assertEquals(
            "longest deadline drains last",
            "bx-60s",
            sorted[2].trip.broadcastId
        )
    }

    /**
     * When priority drain is enabled but the buffer holds a single item, the
     * helper still returns it (no crash, no drop).
     */
    @Test
    fun `priority drain with single buffered envelope returns it unchanged`() {
        val items = listOf(
            BroadcastOverlayManager.BufferedBroadcast(
                trip = sampleTrip("only-one", expiryOffsetMs = 30_000L, urgent = false, fare = 1000.0),
                receivedAtMs = 1L
            )
        )
        val sorted = invokeDrainSort(items, nowMs = 0L)
        assertEquals(1, sorted.size)
        assertEquals("only-one", sorted.first().trip.broadcastId)
    }

    // ---------------------------------------------------------------------
    // Reflective accessor — the GREEN fix exposes a package-internal helper
    // `sortBufferedForDrain(items: List<BufferedBroadcast>, nowMs: Long)
    // : List<BufferedBroadcast>` on BroadcastOverlayManager.
    // The legacy code has no such helper, so this lookup fails RED.
    // ---------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun invokeDrainSort(
        items: List<BroadcastOverlayManager.BufferedBroadcast>,
        nowMs: Long
    ): List<BroadcastOverlayManager.BufferedBroadcast> {
        val method = BroadcastOverlayManager::class.java.getDeclaredMethod(
            "sortBufferedForDrain",
            List::class.java,
            Long::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(BroadcastOverlayManager, items, nowMs)
            as List<BroadcastOverlayManager.BufferedBroadcast>
    }

    private fun sampleTrip(
        id: String,
        expiryOffsetMs: Long,
        urgent: Boolean,
        fare: Double
    ): BroadcastTrip {
        val now = 100_000L
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
            farePerTruck = fare,
            totalFare = fare,
            status = BroadcastStatus.ACTIVE,
            broadcastTime = now,
            expiryTime = now + expiryOffsetMs,
            isUrgent = urgent,
            serverTimeMs = now
        )
    }
}
