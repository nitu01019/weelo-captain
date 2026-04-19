package com.weelo.logistics.broadcast.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-60 — BroadcastExpediteWorker unit tests (SOL-8 §F-C-60 captain consumer)
 * =============================================================================
 *
 * Companion to the backend P8 `FF_FCM_DATA_ONLY_FULLSCREEN` rollout. When the
 * backend flag is ON, FULLSCREEN_TYPES FCM payloads arrive with the
 * `notification` block stripped — ONLY `data` is populated. In Doze, the
 * Firebase SDK does not show a system notification for data-only payloads and
 * INSTEAD fires `onMessageReceived` on the captain's `FirebaseMessagingService`,
 * which has 10 seconds before Doze quashes it. To guarantee the fullscreen
 * intent fires, we hand off to a `BroadcastExpediteWorker` which uses WorkManager
 * expedited execution (`OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`
 * fallback) to wake the overlay pipeline.
 *
 * These tests verify the load-bearing contract at the source level:
 *
 *   1. `BroadcastExpediteWorker` file exists and extends `CoroutineWorker`.
 *   2. The worker pulls `type`, `broadcastId`, `receivedAtMs`, and
 *      `payloadVersion` from `inputData`, then feeds
 *      `BroadcastFlowCoordinator.ingestFcmEnvelope(...)`.
 *   3. The worker sets expedited execution
 *      (`setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`) so
 *      Doze cannot silently drop it.
 *   4. `WeeloFirebaseService.onMessageReceived` branches on
 *      `BuildConfig.FF_FCM_DATA_ONLY_HANDLER`:
 *        - flag ON + data-only + FULLSCREEN-class type -> enqueue the worker
 *        - flag ON + hybrid payload -> legacy path (notification shown by SDK,
 *          foreground flow emits) still runs
 *        - flag OFF -> 100% legacy path
 *      The flag must default OFF (NO-DEPLOY constraint, per release-train rule).
 *   5. Backend flag `FF_FCM_DATA_ONLY_FULLSCREEN` must remain OFF until captain
 *      release is at >=90% DAU — in NO-DEPLOY we ship the worker but keep both
 *      flags OFF.
 *
 * Per INDEX.md §F-C-60 / SOL-8. Preserves the two-phase hold differentiator —
 * data-only payloads for `driver_assignment` type wake the captain reliably.
 * =============================================================================
 */
class BroadcastExpediteWorkerTest {

    companion object {
        private const val APP_SRC = "/private/tmp/weelo-p9-t4/app/src/main/java/com/weelo/logistics"
        private val worker = File("$APP_SRC/broadcast/work/BroadcastExpediteWorker.kt")
        private val fcmService = File("$APP_SRC/data/remote/WeeloFirebaseService.kt")
        private val buildGradle = File("/private/tmp/weelo-p9-t4/app/build.gradle.kts")
    }

    @Test
    fun `BroadcastExpediteWorker file exists`() {
        assertTrue(
            "F-C-60 worker must exist at ${worker.absolutePath}",
            worker.exists()
        )
    }

    @Test
    fun `BroadcastExpediteWorker extends CoroutineWorker`() {
        val src = worker.readText()
        assertTrue(
            "Must be a CoroutineWorker for suspension-friendly async work.",
            src.contains("CoroutineWorker") && src.contains(": CoroutineWorker(")
        )
    }

    @Test
    fun `BroadcastExpediteWorker reads type and broadcastId from inputData`() {
        val src = worker.readText()
        assertTrue(
            "Worker must extract the FCM type (new_broadcast, booking_cancelled, ...).",
            src.contains("inputData.getString") && src.contains("\"type\"")
        )
        assertTrue(
            "Worker must extract the broadcast/order id for downstream coordinator.",
            src.contains("\"broadcastId\"") || src.contains("\"normalizedId\"")
        )
    }

    @Test
    fun `BroadcastExpediteWorker wakes BroadcastFlowCoordinator via ingestFcmEnvelope`() {
        val src = worker.readText()
        assertTrue(
            "Worker must call BroadcastFlowCoordinator.ingestFcmEnvelope — the single ingress seam.",
            src.contains("BroadcastFlowCoordinator") &&
                src.contains("ingestFcmEnvelope(")
        )
        assertTrue(
            "Ingress envelope must tag source=FCM so the coordinator records the correct telemetry.",
            src.contains("BroadcastIngressSource.FCM")
        )
    }

    @Test
    fun `BroadcastExpediteWorker exposes expedited enqueue helper`() {
        val src = worker.readText()
        assertTrue(
            "Companion helper must exist to enqueue the worker from the FCM service.",
            src.contains("fun enqueue(")
        )
        assertTrue(
            "Enqueue must call setExpedited so Android doesn't delay beyond Doze's 10s window.",
            src.contains("setExpedited(") &&
                src.contains("OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST")
        )
    }

    @Test
    fun `BroadcastExpediteWorker returns success when coordinator disabled`() {
        val src = worker.readText()
        // Graceful no-op when the coordinator feature flag is OFF — the worker
        // must not crash, just skip. SOL-8 requirement for captain working with
        // both payload formats.
        assertTrue(
            "Worker must early-return Result.success when coordinator flag is OFF (no-op).",
            src.contains("broadcastCoordinatorEnabled") &&
                src.contains("Result.success()")
        )
    }

    @Test
    fun `BroadcastExpediteWorker returns failure when broadcastId is blank`() {
        val src = worker.readText()
        assertTrue(
            "Blank broadcastId is a malformed payload — terminal failure, not retry.",
            (src.contains("isBlank()") || src.contains("isNullOrBlank()")) &&
                src.contains("Result.failure()")
        )
    }

    @Test
    fun `BroadcastExpediteWorker enqueue uses unique work name per broadcastId`() {
        val src = worker.readText()
        assertTrue(
            "Must use enqueueUniqueWork so duplicate FCM pushes for the same broadcast collapse to one work item.",
            src.contains("enqueueUniqueWork(")
        )
        assertTrue(
            "Unique work name must be scoped per broadcastId (expedite-broadcast-<id>).",
            src.contains("expedite-broadcast-\$") ||
                src.contains("expedite-broadcast-") ||
                src.contains("broadcast-expedite-")
        )
    }

    @Test
    fun `WeeloFirebaseService gates data-only FCM handling on FF_FCM_DATA_ONLY_HANDLER`() {
        val src = fcmService.readText()
        assertTrue(
            "FCM service must check the captain-side flag before routing through the worker.",
            src.contains("FF_FCM_DATA_ONLY_HANDLER")
        )
        assertTrue(
            "When flag is ON and payload is data-only, must enqueue BroadcastExpediteWorker.",
            src.contains("BroadcastExpediteWorker.enqueue") ||
                src.contains("BroadcastExpediteWorker")
        )
    }

    @Test
    fun `WeeloFirebaseService preserves legacy hybrid path`() {
        val src = fcmService.readText()
        // The existing `ingestFcmEnvelope` + `showNotification` path must still run
        // when the flag is OFF or when `notification` block is present.
        assertTrue(
            "Legacy ingestFcmEnvelope call must remain (hybrid payload path).",
            src.contains("BroadcastFlowCoordinator.ingestFcmEnvelope")
        )
        assertTrue(
            "Legacy showNotification call must remain (foreground/hybrid UI rendering).",
            src.contains("showNotification(")
        )
    }

    @Test
    fun `WeeloFirebaseService detects data-only payload via null notification block`() {
        val src = fcmService.readText()
        // Data-only detection: RemoteMessage.notification == null. That's the
        // canonical signal that the backend stripped the block.
        assertTrue(
            "Must detect data-only by checking `remoteMessage.notification == null` (SDK contract).",
            src.contains("notification == null") || src.contains("remoteMessage.notification == null")
        )
    }

    @Test
    fun `WeeloFirebaseService defines fullscreen type set`() {
        val src = fcmService.readText()
        // FULLSCREEN_TYPES set — these are the types that need the wake-via-worker
        // path. Must at minimum include `new_broadcast` (the original FSI target).
        assertTrue(
            "FULLSCREEN_TYPES constant or set must be declared to identify wake-critical types.",
            src.contains("FULLSCREEN_TYPES") || src.contains("fullscreenTypes") ||
                src.contains("FULLSCREEN_FCM_TYPES")
        )
    }

    @Test
    fun `FF_FCM_DATA_ONLY_HANDLER is declared in build gradle and defaults OFF`() {
        val src = buildGradle.readText()
        assertTrue(
            "Flag must be declared as a boolean BuildConfig field.",
            src.contains("FF_FCM_DATA_ONLY_HANDLER")
        )
        val flagLineRegex = Regex(
            """buildConfigField\(\s*"boolean"\s*,\s*"FF_FCM_DATA_ONLY_HANDLER"\s*,\s*"false"\s*\)"""
        )
        assertTrue(
            "Default MUST be false (NO-DEPLOY + release-train gating; captain release must precede backend flip).",
            flagLineRegex.containsMatchIn(src)
        )
    }

    @Test
    fun `BroadcastExpediteWorker does not spawn extra coroutine scopes`() {
        val src = worker.readText()
        // doWork() is structured concurrency by default — spawning our own scope
        // would undermine WorkManager lifecycle and timeout handling.
        assertFalse(
            "Worker must not spawn GlobalScope or ad-hoc CoroutineScope inside doWork.",
            src.contains("GlobalScope.launch") || src.contains("CoroutineScope(Dispatchers")
        )
    }

    @Test
    fun `BroadcastExpediteWorker tags enqueue path for observability`() {
        val src = worker.readText()
        assertTrue(
            "Enqueue helper must log at INFO so we can trace data-only wakeups in logcat.",
            src.contains("Timber.i") || src.contains("Timber.d") || src.contains("Log.i")
        )
    }

    @Test
    fun `BroadcastExpediteWorker reads optional payloadVersion and receivedAtMs`() {
        val src = worker.readText()
        // These fields drive the envelope's dedup & freshness checks in the coordinator.
        assertTrue(
            "Envelope must include receivedAtMs for latency telemetry.",
            src.contains("receivedAtMs")
        )
        assertTrue(
            "Envelope must include optional payloadVersion so downstream dedup works.",
            src.contains("payloadVersion")
        )
    }
}
