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

    /**
     * Compute pickup distance locally from driver GPS when backend doesn't provide it.
     * Uses Android's Location.distanceBetween (Vincenty formula — more accurate than haversine).
     */
    companion object {
        /** Max age for GPS fix — reject locations older than 5 minutes */
        private const val MAX_LOCATION_AGE_MS = 5 * 60 * 1000L
        /** Road distance multiplier (straight-line → road distance, same as backend) */
        private const val ROAD_FACTOR = 1.4
        /** Average trucking speed assumption (km/h) */
        private const val AVG_SPEED_KMH = 30.0
    }

    private fun enrichWithLocalPickupDistance(trip: BroadcastTrip): BroadcastTrip {
        if (trip.pickupDistanceKm > 0.0) return trip
        if (trip.pickupLocation.latitude == 0.0 || trip.pickupLocation.longitude == 0.0) return trip
        return try {
            val ctx = com.weelo.logistics.WeeloApp.getInstance()?.applicationContext ?: return trip
            val locationManager = ctx.getSystemService(
                android.content.Context.LOCATION_SERVICE
            ) as? android.location.LocationManager ?: return trip
            @Suppress("MissingPermission")
            val driverLoc = locationManager.getLastKnownLocation(
                android.location.LocationManager.GPS_PROVIDER
            ) ?: locationManager.getLastKnownLocation(
                android.location.LocationManager.NETWORK_PROVIDER
            ) ?: return trip

            // Staleness guard: reject GPS fixes older than 5 minutes
            if (System.currentTimeMillis() - driverLoc.time > MAX_LOCATION_AGE_MS) {
                timber.log.Timber.d("📍 Skipping stale GPS fix (%d seconds old) for %s",
                    (System.currentTimeMillis() - driverLoc.time) / 1000, trip.broadcastId)
                return trip
            }

            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                driverLoc.latitude, driverLoc.longitude,
                trip.pickupLocation.latitude, trip.pickupLocation.longitude,
                results
            )
            val distKm = results[0] / 1000.0
            val roundedDist = Math.round(distKm * 10.0) / 10.0
            // Apply 1.4x road factor (same as backend) then estimate at 30 km/h
            val roadDistKm = distKm * ROAD_FACTOR
            val etaMinutes = ((roadDistKm / AVG_SPEED_KMH) * 60).toInt().coerceAtLeast(1)
            timber.log.Timber.d(
                "📍 StateStore computed local pickup: id=%s, dist=%.1fkm, eta=%dmin",
                trip.broadcastId, roundedDist, etaMinutes
            )
            trip.copy(pickupDistanceKm = roundedDist, pickupEtaMinutes = etaMinutes)
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to compute local pickup distance for %s", trip.broadcastId)
            trip
        }
    }

    fun upsert(trip: BroadcastTrip): BroadcastTrip? {
        // Compute local pickup distance OUTSIDE the lock (GPS IPC should never be inside a lock)
        val enriched = enrichWithLocalPickupDistance(trip)
        synchronized(lock) {
            val previous = byId[trip.broadcastId]
            // Preserve existing computed distance through HTTP refresh cycles
            val merged = if (previous != null &&
                enriched.pickupDistanceKm <= 0.0 &&
                previous.pickupDistanceKm > 0.0
            ) {
                timber.log.Timber.d("🔒 StateStore PRESERVE pickupDistanceKm: id=%s, keeping=%.1f",
                    enriched.broadcastId, previous.pickupDistanceKm)
                enriched.copy(
                    pickupDistanceKm = previous.pickupDistanceKm,
                    pickupEtaMinutes = previous.pickupEtaMinutes
                )
            } else {
                enriched
            }
            byId[merged.broadcastId] = merged
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
