package com.weelo.logistics.broadcast

import com.weelo.logistics.data.model.BroadcastStatus
import com.weelo.logistics.data.model.BroadcastTrip
import com.weelo.logistics.data.model.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastBoundedStoresTest {

    @Test
    fun `lru id set rejects duplicates`() {
        val set = LruIdSet(maxSize = 2)
        assertTrue(set.add("a"))
        assertFalse(set.add("a"))
        assertTrue(set.add("b"))
        assertTrue(set.add("c")) // evicts oldest ("a")
        assertTrue(set.add("a")) // "a" can be added again after eviction
    }

    @Test
    fun `pending queue expires by ttl`() {
        val queue = PendingBroadcastQueue(maxSize = 10, ttlMs = 1000)
        queue.enqueue(PendingBroadcast(sampleTrip("old"), receivedAtMs = 1000))
        queue.enqueue(PendingBroadcast(sampleTrip("new"), receivedAtMs = 1900))

        val (valid, expired) = queue.drainValid(nowMs = 2500)

        assertEquals(1, valid.size)
        assertEquals("new", valid.first().trip.broadcastId)
        assertEquals(1, expired.size)
        assertEquals("old", expired.first().trip.broadcastId)
    }

    @Test
    fun `state store patches and removes on terminal update`() {
        val store = BroadcastStateStore(maxRenderableBroadcasts = 10)
        store.upsert(sampleTrip("trip_1", totalTrucks = 5, filled = 1))

        val updated = store.patchTrucksRemaining(
            broadcastId = "trip_1",
            totalTrucks = 5,
            trucksFilled = 3,
            trucksRemaining = 2,
            terminalStatuses = setOf("cancelled", "expired"),
            rawStatus = "active"
        )
        assertEquals(3, updated?.trucksFilledSoFar)
        assertEquals(BroadcastStatus.PARTIALLY_FILLED, updated?.status)

        store.patchTrucksRemaining(
            broadcastId = "trip_1",
            totalTrucks = 5,
            trucksFilled = 5,
            trucksRemaining = 0,
            terminalStatuses = setOf("cancelled", "expired"),
            rawStatus = "completed"
        )

        assertTrue(store.snapshotSorted().isEmpty())
    }

    private fun sampleTrip(
        id: String,
        totalTrucks: Int = 2,
        filled: Int = 0
    ): BroadcastTrip {
        return BroadcastTrip(
            broadcastId = id,
            customerId = "customer_$id",
            customerName = "Customer",
            customerMobile = "9999999999",
            pickupLocation = Location(
                latitude = 12.0,
                longitude = 77.0,
                address = "Pickup"
            ),
            dropLocation = Location(
                latitude = 12.5,
                longitude = 77.5,
                address = "Drop"
            ),
            distance = 10.0,
            estimatedDuration = 25L,
            totalTrucksNeeded = totalTrucks,
            trucksFilledSoFar = filled,
            goodsType = "General",
            farePerTruck = 1500.0,
            totalFare = totalTrucks * 1500.0,
            status = BroadcastStatus.ACTIVE,
            broadcastTime = 100L
        )
    }
}
