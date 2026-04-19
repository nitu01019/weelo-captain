package com.weelo.logistics.data.remote.socket

import com.weelo.logistics.data.model.TripAssignedNotification
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * FlexHoldStartedHandlerTest — Captain side of F-C-50
 *
 * Verifies that `SocketExtendedEventHandlers.handleFlexHoldStarted` parses the
 * backend-emitted `flex_hold_started` JSON payload into a [FlexHoldStartedNotification]
 * and publishes it on the [FlexHoldStartedNotification] SharedFlow.
 *
 * Backend contract (weelo-backend `FlexHoldService.createFlexHold`):
 *   event: `flex_hold_started`
 *   payload: { holdId, orderId, phase, expiresAt, baseDurationSeconds, canExtend, maxExtensions }
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlexHoldStartedHandlerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private fun newHandlers(
        flexHoldStarted: MutableSharedFlow<FlexHoldStartedNotification>
    ): SocketExtendedEventHandlers {
        return SocketExtendedEventHandlers(
            serviceScope = testScope,
            resolveEventId = { data, keys -> keys.firstOrNull()?.let { data.optString(it, "") } ?: "" },
            emitHot = { flow, value -> flow.tryEmit(value) },
            _flexHoldStarted = flexHoldStarted,
            _flexHoldExtended = MutableSharedFlow(extraBufferCapacity = 10),
            _cascadeReassigned = MutableSharedFlow(extraBufferCapacity = 10),
            _driverDeclined = MutableSharedFlow(extraBufferCapacity = 10),
            _driverRatingUpdated = MutableSharedFlow(extraBufferCapacity = 10),
            _driverAccepted = MutableSharedFlow(extraBufferCapacity = 10),
            _holdExpired = MutableSharedFlow(extraBufferCapacity = 10),
            _bookingPartiallyFilled = MutableSharedFlow(extraBufferCapacity = 10),
            _requestNoLongerAvailable = MutableSharedFlow(extraBufferCapacity = 10),
            _orderStatusUpdate = MutableSharedFlow(extraBufferCapacity = 10),
            _driverMayBeOffline = MutableSharedFlow(extraBufferCapacity = 10),
            _assignmentStale = MutableSharedFlow(extraBufferCapacity = 10),
            _broadcastDismissed = MutableSharedFlow(extraBufferCapacity = 20)
        )
    }

    @Test
    fun `handleFlexHoldStarted parses canonical backend payload into notification`() = testScope.runTest {
        val flex = MutableSharedFlow<FlexHoldStartedNotification>(extraBufferCapacity = 1)
        val handlers = newHandlers(flex)

        val data = JSONObject().apply {
            put("holdId", "hold-abc-123")
            put("orderId", "order-xyz-456")
            put("phase", "FLEX")
            put("expiresAt", "2026-04-17T12:34:56.789Z")
            put("baseDurationSeconds", 90)
            put("canExtend", true)
            put("maxExtensions", 2)
        }

        handlers.handleFlexHoldStarted(arrayOf(data))
        advanceUntilIdle()

        val notification = flex.first()
        assertEquals("hold-abc-123", notification.holdId)
        assertEquals("order-xyz-456", notification.orderId)
        assertEquals("FLEX", notification.phase)
        assertEquals("2026-04-17T12:34:56.789Z", notification.expiresAt)
        assertEquals(90, notification.baseDurationSeconds)
        assertTrue(notification.canExtend)
        assertEquals(2, notification.maxExtensions)
    }

    @Test
    fun `handleFlexHoldStarted uses safe defaults when fields are missing`() = testScope.runTest {
        val flex = MutableSharedFlow<FlexHoldStartedNotification>(extraBufferCapacity = 1)
        val handlers = newHandlers(flex)

        // Minimal payload — only holdId + orderId
        val data = JSONObject().apply {
            put("holdId", "hold-min")
            put("orderId", "order-min")
        }

        handlers.handleFlexHoldStarted(arrayOf(data))
        advanceUntilIdle()

        val notification = flex.first()
        assertEquals("hold-min", notification.holdId)
        assertEquals("order-min", notification.orderId)
        // Defaults: phase="FLEX", expiresAt="", baseDurationSeconds=0, canExtend=true, maxExtensions=0
        assertEquals("FLEX", notification.phase)
        assertEquals("", notification.expiresAt)
        assertEquals(0, notification.baseDurationSeconds)
        assertTrue(notification.canExtend)
        assertEquals(0, notification.maxExtensions)
    }

    @Test
    fun `handleFlexHoldStarted tolerates empty args array`() {
        val flex = MutableSharedFlow<FlexHoldStartedNotification>(extraBufferCapacity = 1)
        val handlers = newHandlers(flex)

        // Should not throw
        handlers.handleFlexHoldStarted(emptyArray())
        // No emission expected — tryEmit returns false on empty flow, nothing is published
        // The handler returning normally is the assertion.
    }

    @Test
    fun `FlexHoldStartedNotification data class carries all 7 canonical fields`() {
        val n = FlexHoldStartedNotification(
            holdId = "h",
            orderId = "o",
            phase = "FLEX",
            expiresAt = "2026-04-17T00:00:00.000Z",
            baseDurationSeconds = 90,
            canExtend = true,
            maxExtensions = 2
        )
        // PRD-7777 canonical field set — guards against contract drift
        assertEquals("h", n.holdId)
        assertEquals("o", n.orderId)
        assertEquals("FLEX", n.phase)
        assertEquals("2026-04-17T00:00:00.000Z", n.expiresAt)
        assertEquals(90, n.baseDurationSeconds)
        assertTrue(n.canExtend)
        assertEquals(2, n.maxExtensions)
    }

    @Test
    fun `SocketConstants FLEX_HOLD_STARTED matches backend event name`() {
        // Backend: socket.service.ts SocketEvent.FLEX_HOLD_STARTED = 'flex_hold_started'
        // Also present in FCM_FALLBACK_EVENTS for offline transporter pushes.
        assertEquals("flex_hold_started", SocketConstants.FLEX_HOLD_STARTED)
    }
}
