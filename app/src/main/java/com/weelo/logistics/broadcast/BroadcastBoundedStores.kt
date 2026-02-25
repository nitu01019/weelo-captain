package com.weelo.logistics.broadcast

import com.weelo.logistics.data.model.BroadcastStatus
import com.weelo.logistics.data.model.BroadcastTrip
import java.util.LinkedHashMap

class LruIdSet(
    private val maxSize: Int
) {
    private val lock = Any()
    private val storage = object : LinkedHashMap<String, Unit>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean {
            return size > maxSize
        }
    }

    fun add(id: String): Boolean {
        val normalized = id.trim()
        if (normalized.isEmpty()) return false
        synchronized(lock) {
            val wasNew = !storage.containsKey(normalized)
            storage[normalized] = Unit
            return wasNew
        }
    }

    fun remove(id: String) {
        synchronized(lock) {
            storage.remove(id.trim())
        }
    }

    fun clear() {
        synchronized(lock) {
            storage.clear()
        }
    }
}

data class PendingBroadcast(
    val trip: BroadcastTrip,
    val receivedAtMs: Long
)

class PendingBroadcastQueue(
    private val maxSize: Int,
    private val ttlMs: Long
) {
    private val lock = Any()
    private val queue = ArrayDeque<PendingBroadcast>()

    fun enqueue(item: PendingBroadcast): Boolean {
        synchronized(lock) {
            if (queue.none { it.trip.broadcastId == item.trip.broadcastId } && queue.size >= maxSize) {
                queue.removeFirstOrNull()
            }
            queue.removeAll { it.trip.broadcastId == item.trip.broadcastId }
            queue.addLast(item)
            return true
        }
    }

    fun drainValid(nowMs: Long = System.currentTimeMillis()): Pair<List<PendingBroadcast>, List<PendingBroadcast>> {
        synchronized(lock) {
            val valid = mutableListOf<PendingBroadcast>()
            val expired = mutableListOf<PendingBroadcast>()
            while (queue.isNotEmpty()) {
                val item = queue.removeFirst()
                if (nowMs - item.receivedAtMs <= ttlMs) {
                    valid += item
                } else {
                    expired += item
                }
            }
            return valid to expired
        }
    }

    fun removeById(broadcastId: String) {
        synchronized(lock) {
            queue.removeAll { it.trip.broadcastId == broadcastId }
        }
    }

    fun size(): Int = synchronized(lock) { queue.size }

    fun clear() {
        synchronized(lock) {
            queue.clear()
        }
    }
}

class BroadcastStateStore(
    private val maxRenderableBroadcasts: Int
) {
    private val lock = Any()
    private val byId = LinkedHashMap<String, BroadcastTrip>()

    fun upsert(trip: BroadcastTrip): BroadcastTrip? {
        synchronized(lock) {
            val previous = byId.put(trip.broadcastId, trip)
            trimIfNeededLocked()
            return previous
        }
    }

    fun patchTrucksRemaining(
        broadcastId: String,
        totalTrucks: Int,
        trucksFilled: Int,
        trucksRemaining: Int,
        terminalStatuses: Set<String>,
        rawStatus: String
    ): BroadcastTrip? {
        synchronized(lock) {
            val current = byId[broadcastId] ?: return null
            if (trucksRemaining <= 0 || rawStatus.lowercase() in terminalStatuses) {
                byId.remove(broadcastId)
                return current
            }

            val resolvedTotal = totalTrucks.takeIf { it > 0 } ?: current.totalTrucksNeeded
            val resolvedFilled = trucksFilled
                .coerceAtLeast(0)
                .coerceAtMost(resolvedTotal.coerceAtLeast(0))
            val resolvedRemaining = trucksRemaining
                .coerceAtLeast(0)
                .coerceAtMost(resolvedTotal.coerceAtLeast(0))
            val status = when {
                resolvedRemaining <= 0 -> BroadcastStatus.FULLY_FILLED
                resolvedFilled > 0 -> BroadcastStatus.PARTIALLY_FILLED
                else -> BroadcastStatus.ACTIVE
            }

            val updated = current.copy(
                totalTrucksNeeded = resolvedTotal,
                trucksFilledSoFar = resolvedFilled,
                trucksStillNeeded = resolvedRemaining,
                status = status
            )
            byId[broadcastId] = updated
            trimIfNeededLocked()
            return updated
        }
    }

    fun removeById(broadcastId: String): BroadcastTrip? {
        synchronized(lock) {
            return byId.remove(broadcastId)
        }
    }

    fun snapshotSorted(): List<BroadcastTrip> {
        synchronized(lock) {
            return byId.values
                .sortedWith(
                    compareByDescending<BroadcastTrip> { it.isUrgent }
                        .thenByDescending { it.broadcastTime }
                )
                .take(maxRenderableBroadcasts)
        }
    }

    fun clear() {
        synchronized(lock) {
            byId.clear()
        }
    }

    private fun trimIfNeededLocked() {
        if (byId.size <= maxRenderableBroadcasts) return
        val removeCount = byId.size - maxRenderableBroadcasts
        byId.values
            .sortedWith(
                compareBy<BroadcastTrip> { it.isUrgent }
                    .thenBy { it.broadcastTime }
            )
            .take(removeCount)
            .forEach { byId.remove(it.broadcastId) }
    }
}
