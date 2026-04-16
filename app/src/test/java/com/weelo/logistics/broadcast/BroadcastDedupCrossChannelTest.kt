package com.weelo.logistics.broadcast

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F-C-02 — Consolidated cross-channel dedup contract tests.
 *
 * Guarantees that a single event id arriving via different channels
 * (socket-first then FCM, or FCM-first then socket) is deduplicated
 * exactly once, inside the TTL window, across all three former entry
 * points which now delegate to the coordinator-owned LRU+TTL store.
 *
 * Industry references:
 * - Sohil Ladhani (Apr 2026): "encode user/event/entity/version as a
 *   dedup key, store with TTL, skip on duplicate."
 * - WebSocket.org: single-coordinator pattern for dual-channel
 *   (WS + push) delivery deduplication.
 * - FCM collapse_key / APNs apns-collapse-id: compound keys prevent
 *   cross-class collisions.
 */
class BroadcastDedupCrossChannelTest {

    @Before
    fun resetDedupState() {
        // Ensure no state leaks between tests via coordinator singleton.
        BroadcastFlowCoordinator.dedupeIdsClear()
    }

    @After
    fun teardown() {
        BroadcastFlowCoordinator.dedupeIdsClear()
    }

    @Test
    fun `socket-first then FCM-duplicate drops on second arrival`() {
        val id = "trip_socket_first_001"
        // Socket path (SocketEventRouter.kt:230 → BroadcastDedup.isNew)
        assertTrue("socket first arrival should be new", BroadcastDedup.isNew(id))
        // FCM path (WeeloFirebaseService.kt:237 → BroadcastDedup.isNew)
        assertFalse("FCM second arrival should dedupe", BroadcastDedup.isNew(id))
    }

    @Test
    fun `FCM-first then socket-duplicate drops on second arrival`() {
        val id = "trip_fcm_first_002"
        // FCM arrives first (offline-delivered data push).
        assertTrue("FCM first arrival should be new", BroadcastDedup.isNew(id))
        // Socket reconnect replays the same envelope.
        assertFalse("socket second arrival should dedupe", BroadcastDedup.isNew(id))
    }

    @Test
    fun `TTL expiry after window allows same id to be re-accepted`() {
        val ttlMs = 500L
        // Use an injectable fake clock so we don't Thread.sleep in unit tests.
        var fakeNow = 1_000L
        val store = LruIdSet(maxSize = 16, ttlMs = ttlMs, nowProvider = { fakeNow })
        val key = normalizeDedupKey("broadcast", "trip_ttl_003", 1)

        assertTrue("initial arrival is new", store.add(key))
        // Same window → duplicate.
        fakeNow += 100L
        assertFalse("within TTL is duplicate", store.add(key))
        // Advance past TTL → same id should be re-accepted.
        fakeNow += ttlMs + 10L
        assertTrue("after TTL expiry, id is new again", store.add(key))
    }

    @Test
    fun `compound key separates different event classes sharing same broadcastId`() {
        val broadcastId = "trip_compound_004"
        val store = LruIdSet(maxSize = 64, ttlMs = 10_000L)

        // Three different eventClasses with the SAME underlying id must all
        // be accepted because the compound key distinguishes them.
        assertTrue(store.add(normalizeDedupKey("broadcast", broadcastId, 1)))
        assertTrue(store.add(normalizeDedupKey("overlay", broadcastId, 1)))
        assertTrue(store.add(normalizeDedupKey("cancel", broadcastId, 1)))

        // But a repeat of any one class within TTL should be a duplicate.
        assertFalse(store.add(normalizeDedupKey("broadcast", broadcastId, 1)))
    }

    @Test
    fun `normalize key format is eventClass pipe id pipe version`() {
        val key = normalizeDedupKey("broadcast", "abc_123", 1)
        assertEquals("broadcast|abc_123|1", key)
    }

    @Test
    fun `blank broadcastId is rejected from dedup`() {
        // Guard rail: we never consume a blank id slot in the LRU.
        assertFalse("blank id must not be accepted", BroadcastDedup.isNew(""))
        assertFalse("whitespace id must not be accepted", BroadcastDedup.isNew("   "))
    }

    @Test
    fun `forget rollback allows the same id to be re-registered`() {
        val id = "trip_rollback_005"
        assertTrue(BroadcastDedup.isNew(id))
        // Caller decided to drop the event (e.g., offline) and wants a retry
        // to be treated as new.
        BroadcastDedup.forget(id)
        assertTrue("after forget, id should be new again", BroadcastDedup.isNew(id))
    }
}
