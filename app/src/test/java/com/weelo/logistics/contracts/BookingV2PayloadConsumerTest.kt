package com.weelo.logistics.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-55 — legacy booking proxy retirement (v2 payload consumer)
 * =============================================================================
 *
 * Backend `booking-payload.helper.ts:116-214` emits a legacy broadcast shape
 * with NO personalized fields. Per INDEX F-C-55:
 *
 *   > Captain parses 6 fields backend legacy booking path never sends
 *   > -> "0 of 0 trucks available" misleading UI
 *
 * Per INDEX.md F-C-55 proposed fix: backend `buildBroadcastPayload` extends
 * to accept `transporterContext?: {transporterId, availableTrucks, ...}` and
 * populates these 6 fields. On captain side, we add a v2 consumer that checks
 * `isPersonalized` before trusting the numbers, falling back to legacy zero
 * defaults when backend hasn't run the v2 builder yet.
 *
 * Invariants:
 *
 *   1. `BookingBroadcastV2Event` exposes all 6 fields.
 *   2. `isPersonalized=false` by default — v2 contract says backend must
 *      explicitly set it to true, so captain doesn't over-claim.
 *   3. Router references `FF_BOOKING_V2_PAYLOAD` (default OFF).
 *   4. EVENT_NAME is documented + testable (contract parity with backend).
 * =============================================================================
 */
class BookingV2PayloadConsumerTest {

    private val routerFile = File(
        "src/main/java/com/weelo/logistics/data/remote/socket/SocketEventRouter.kt"
    )

    private val contractsFile = File(
        "src/main/java/com/weelo/logistics/contracts/GeneratedContracts.kt"
    )

    private val routerSource: String by lazy { routerFile.readText() }
    private val contractsSource: String by lazy { contractsFile.readText() }

    @Test
    fun `BookingBroadcastV2Event is declared`() {
        assertTrue(
            "F-C-55: data class BookingBroadcastV2Event must be declared",
            contractsSource.contains("data class BookingBroadcastV2Event")
        )
    }

    @Test
    fun `BookingBroadcastV2Event declares all 6 personalized fields`() {
        val required = listOf(
            "trucksYouCanProvide",
            "maxTrucksYouCanProvide",
            "yourAvailableTrucks",
            "yourTotalTrucks",
            "trucksStillNeeded",
            "isPersonalized"
        )
        required.forEach { field ->
            assertTrue(
                "F-C-55: BookingBroadcastV2Event must declare `$field`",
                contractsSource.contains(Regex("""val\s+$field\s*:"""))
            )
        }
    }

    @Test
    fun `fromJson defaults isPersonalized to false when backend has not run v2 builder`() {
        val data = org.json.JSONObject().apply { put("broadcastId", "b1") }
        val event = BookingBroadcastV2Event.fromJson(data)
        assertNotNull(event)
        assertFalse(
            "F-C-55: legacy payloads MUST NOT claim personalization — isPersonalized defaults to false",
            event!!.isPersonalized
        )
        assertEquals(0, event.trucksYouCanProvide)
        assertEquals(0, event.maxTrucksYouCanProvide)
    }

    @Test
    fun `fromJson parses populated v2 transporter context`() {
        val data = org.json.JSONObject().apply {
            put("broadcastId", "b1")
            put("trucksYouCanProvide", 2)
            put("maxTrucksYouCanProvide", 5)
            put("yourAvailableTrucks", 3)
            put("yourTotalTrucks", 7)
            put("trucksStillNeeded", 4)
            put("isPersonalized", true)
        }
        val event = BookingBroadcastV2Event.fromJson(data)
        assertNotNull(event)
        assertTrue("F-C-55: backend explicit isPersonalized flag flows through", event!!.isPersonalized)
        assertEquals(2, event.trucksYouCanProvide)
        assertEquals(5, event.maxTrucksYouCanProvide)
        assertEquals(3, event.yourAvailableTrucks)
        assertEquals(7, event.yourTotalTrucks)
        assertEquals(4, event.trucksStillNeeded)
    }

    @Test
    fun `router references FF_BOOKING_V2_PAYLOAD flag`() {
        assertTrue(
            "F-C-55: router must reference BuildConfig.FF_BOOKING_V2_PAYLOAD",
            routerSource.contains("FF_BOOKING_V2_PAYLOAD")
        )
    }

    @Test
    fun `router imports BookingBroadcastV2Event`() {
        assertTrue(
            "F-C-55: router must import BookingBroadcastV2Event from contracts",
            routerSource.contains("import com.weelo.logistics.contracts.BookingBroadcastV2Event")
        )
    }
}
