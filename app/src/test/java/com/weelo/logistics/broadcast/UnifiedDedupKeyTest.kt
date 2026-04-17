package com.weelo.logistics.broadcast

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * W1-2 / F-C-02 — Unified cross-channel dedup key contract.
 *
 * These tests guard the REAL bug: socket and FCM ingress paths historically
 * built dedup keys with DIFFERENT schemas for the same broadcast id, so the
 * coordinator-owned LRU stored the same broadcast under multiple composite
 * keys and a socket+FCM race produced two UI events instead of one.
 *
 * Consolidation target: every ingress point must funnel through
 * [BroadcastDedupKey.admit] (which delegates to
 * [BroadcastFlowCoordinator.dedupeIdsIsNew]) so that a single (id) on two
 * channels collapses to one key admission.
 *
 * Industry references:
 * - Sohil Ladhani (Apr 2026): "encode event class + entity id + version as
 *   a dedup key; store with TTL; skip on duplicate."
 * - FCM `collapse_key` / APNs `apns-collapse-id` compound-key pattern.
 */
class UnifiedDedupKeyTest {

    @Before
    fun resetCoordinator() {
        BroadcastFlowCoordinator.dedupeIdsClear()
    }

    @After
    fun teardown() {
        BroadcastFlowCoordinator.dedupeIdsClear()
    }

    // =========================================================================
    // Socket-first then FCM-second MUST dedup (the current bug).
    // =========================================================================

    @Test
    fun `socket-first NEW_BROADCAST then FCM-second same id is dedup'd`() {
        val id = "trip_cross_001"
        // Simulates socket path: SocketEventRouter coordinator entry point
        // with eventClass NEW_BROADCAST + version v1.
        val socketAdmitted = BroadcastDedupKey.admit(
            BroadcastEventClass.NEW_BROADCAST,
            id,
            version = "v1"
        )
        // Simulates FCM path: WeeloFirebaseService cross-channel check with
        // the SAME broadcastId — regardless of how FCM used to build its key
        // (legacy "broadcast|id|1"), the unified helper must collapse both.
        val fcmAdmitted = BroadcastDedupKey.admit(
            BroadcastEventClass.NEW_BROADCAST,
            id,
            version = "v1"
        )

        assertTrue("socket first arrival must be admitted", socketAdmitted)
        assertFalse("FCM second arrival must be dedup'd", fcmAdmitted)
    }

    // =========================================================================
    // FCM-first then socket-second MUST dedup.
    // =========================================================================

    @Test
    fun `FCM-first then socket-second same id is dedup'd`() {
        val id = "trip_cross_002"
        // FCM arrives first (data push while socket disconnected).
        val fcmAdmitted = BroadcastDedupKey.admit(
            BroadcastEventClass.NEW_BROADCAST,
            id,
            version = "v1"
        )
        // Socket reconnect replays the same envelope.
        val socketAdmitted = BroadcastDedupKey.admit(
            BroadcastEventClass.NEW_BROADCAST,
            id,
            version = "v1"
        )

        assertTrue("FCM first arrival must be admitted", fcmAdmitted)
        assertFalse("socket second arrival must be dedup'd", socketAdmitted)
    }

    // =========================================================================
    // Two DIFFERENT broadcastIds must both be admitted.
    // =========================================================================

    @Test
    fun `two different broadcastIds are both admitted on arrival`() {
        val idA = "trip_cross_003_a"
        val idB = "trip_cross_003_b"

        val admittedA = BroadcastDedupKey.admit(
            BroadcastEventClass.NEW_BROADCAST, idA, version = "v1"
        )
        val admittedB = BroadcastDedupKey.admit(
            BroadcastEventClass.NEW_BROADCAST, idB, version = "v1"
        )

        assertTrue("first broadcast id must be admitted", admittedA)
        assertTrue(
            "second (different) broadcast id must be admitted (not dedup'd)",
            admittedB
        )
    }

    // =========================================================================
    // Compatibility shim — legacy FCM key "broadcast|id|1" must dedup against
    // the new unified key NEW_BROADCAST|id|v0 for 1 release.
    // This covers the scenario where a pre-W1-2 build seeded the LRU with the
    // legacy key and then a post-W1-2 ingress arrives.
    // =========================================================================

    @Test
    fun `legacy FCM key in LRU deduplicates matching new-format admission`() {
        val id = "trip_legacy_004"
        // Seed the coordinator with the legacy key shape (what pre-W1-2
        // BroadcastDedup.isNew would have written).
        val legacyKey = "broadcast|$id|1"
        assertTrue(
            "legacy key seeding should be a fresh insertion",
            BroadcastFlowCoordinator.dedupeIdsIsNew(legacyKey)
        )

        // Now the post-W1-2 path tries to admit the same broadcast — the
        // dual-probe shim must observe the legacy key and drop.
        val admittedPostFix = BroadcastDedupKey.admit(
            BroadcastEventClass.NEW_BROADCAST,
            id,
            version = "v0"
        )

        assertFalse(
            "post-W1-2 admission must be dedup'd against legacy FCM key",
            admittedPostFix
        )
    }

    // =========================================================================
    // Key format contract — guards downstream log/metric parsers.
    // =========================================================================

    @Test
    fun `build produces eventClassName pipe id pipe version with v0 default`() {
        val key = BroadcastDedupKey.build(
            BroadcastEventClass.NEW_BROADCAST, "abc_123", null
        )
        assertEquals("NEW_BROADCAST|abc_123|v0", key)
    }

    @Test
    fun `build trims id whitespace`() {
        val key = BroadcastDedupKey.build(
            BroadcastEventClass.NEW_BROADCAST, "  abc_123  ", "v2"
        )
        assertEquals("NEW_BROADCAST|abc_123|v2", key)
    }

    @Test
    fun `buildLegacy matches historical broadcast pipe id pipe 1 shape`() {
        val key = BroadcastDedupKey.buildLegacy("abc_123")
        assertEquals("broadcast|abc_123|1", key)
    }

    // =========================================================================
    // Blank id guard — never consume an LRU slot for an empty identifier.
    // =========================================================================

    @Test
    fun `admit with blank id returns false without polluting the LRU`() {
        assertFalse(
            "blank id must not be admitted",
            BroadcastDedupKey.admit(
                BroadcastEventClass.NEW_BROADCAST, "", version = "v1"
            )
        )
        assertFalse(
            "whitespace-only id must not be admitted",
            BroadcastDedupKey.admit(
                BroadcastEventClass.NEW_BROADCAST, "   ", version = "v1"
            )
        )
    }
}
