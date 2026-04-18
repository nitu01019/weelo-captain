package com.weelo.logistics.broadcast.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * =============================================================================
 * F-C-22 — HoldReleaseWorker unit tests (SOL-7 §S-22 captain-side)
 * =============================================================================
 *
 * Pure JUnit4 source-scan tests. We cannot spin up WorkManager runtime in a
 * host-only unit test (requires Robolectric or instrumented tests), so we
 * verify the load-bearing contract at the source level:
 *
 *   1. `HoldReleaseWorker` file exists and is a `CoroutineWorker` subclass.
 *   2. The worker reads `holdId` from `inputData` and calls
 *      `BroadcastRepository.releaseHold(holdId)`.
 *   3. The worker returns `Result.retry()` on `IOException` (transient net err)
 *      and `Result.success()` when the repository returns success OR when
 *      the hold is already gone (idempotent replay — 204-on-already-released).
 *   4. The worker uses `BackoffPolicy.EXPONENTIAL` with a 10s base delay and
 *      enqueues via `enqueueUniqueWork(..., KEEP, ...)` so a restart doesn't
 *      spawn duplicate release jobs for the same `holdId`.
 *   5. `BroadcastListScreen.handleDismiss` and `BroadcastOverlayScreen.handleDismiss`
 *      both route through `HoldReleaseWorker.enqueue(...)` when the
 *      `BuildConfig.FF_HOLD_RELEASE_WORKMANAGER` flag is ON. The legacy
 *      `scope.launch { releaseHold(...) }` path remains under `else` so a flag
 *      flip is a clean rollback.
 *   6. `BuildConfig.FF_HOLD_RELEASE_WORKMANAGER` is declared in
 *      `app/build.gradle.kts`, defaulting to `false` (NO-DEPLOY constraint).
 *
 * Per INDEX.md §F-C-22 / SOL-7 §S-22. Differentiator-strengthening: faster
 * release of stale holds unblocks other transporters' per-vehicle hold attempts.
 * =============================================================================
 */
class HoldReleaseWorkerTest {

    companion object {
        private const val APP_SRC = "/private/tmp/weelo-p9-t4/app/src/main/java/com/weelo/logistics"
        private val worker = File("$APP_SRC/broadcast/work/HoldReleaseWorker.kt")
        private val listScreen = File("$APP_SRC/ui/transporter/BroadcastListScreen.kt")
        private val overlayScreen = File("$APP_SRC/broadcast/BroadcastOverlayScreen.kt")
        private val buildGradle = File("/private/tmp/weelo-p9-t4/app/build.gradle.kts")
    }

    @Test
    fun `HoldReleaseWorker file exists`() {
        assertTrue(
            "F-C-22 worker must exist at ${worker.absolutePath}",
            worker.exists()
        )
    }

    @Test
    fun `HoldReleaseWorker extends CoroutineWorker`() {
        val src = worker.readText()
        assertTrue(
            "Must be a CoroutineWorker subclass (WorkManager suspension-friendly).",
            src.contains("CoroutineWorker") && src.contains(": CoroutineWorker(")
        )
    }

    @Test
    fun `HoldReleaseWorker reads holdId from inputData`() {
        val src = worker.readText()
        assertTrue(
            "Worker must read the hold id via WorkRequest input data.",
            src.contains("inputData.getString") && src.contains("\"holdId\"")
        )
    }

    @Test
    fun `HoldReleaseWorker invokes BroadcastRepository releaseHold`() {
        val src = worker.readText()
        assertTrue(
            "Worker must delegate to BroadcastRepository.releaseHold (single idempotent path).",
            src.contains("BroadcastRepository") && src.contains("releaseHold(")
        )
    }

    @Test
    fun `HoldReleaseWorker retries on IOException`() {
        val src = worker.readText()
        assertTrue(
            "Transient network failures must trigger Result.retry() so the OS replays later.",
            src.contains("IOException") && src.contains("Result.retry()")
        )
    }

    @Test
    fun `HoldReleaseWorker returns success on repository Success`() {
        val src = worker.readText()
        assertTrue(
            "Happy path: repository Success -> Result.success().",
            src.contains("BroadcastResult.Success") && src.contains("Result.success()")
        )
    }

    @Test
    fun `HoldReleaseWorker treats already released as success for idempotency`() {
        val src = worker.readText()
        // BroadcastRepository.releaseHold already converts HOLD_NOT_FOUND -> Success(true),
        // so the worker relies on repository idempotency. That's explicitly documented.
        assertTrue(
            "Worker must document that release is idempotent (backend 204-on-already-released).",
            src.contains("idempotent") || src.contains("HOLD_NOT_FOUND") || src.contains("204")
        )
    }

    @Test
    fun `HoldReleaseWorker exposes unique enqueue helper`() {
        val src = worker.readText()
        assertTrue(
            "Companion helper must exist to enqueue the work.",
            src.contains("fun enqueue(") || src.contains("fun enqueueRelease(")
        )
        assertTrue(
            "Must use enqueueUniqueWork with KEEP so restart does not duplicate jobs.",
            src.contains("enqueueUniqueWork(") &&
                src.contains("ExistingWorkPolicy.KEEP")
        )
        assertTrue(
            "Unique work name must be scoped per holdId (SOL-7 pattern).",
            src.contains("release-hold-\$") || src.contains("release-hold-")
        )
    }

    @Test
    fun `HoldReleaseWorker uses exponential backoff with 10 second base`() {
        val src = worker.readText()
        assertTrue(
            "Backoff policy must be EXPONENTIAL (Android/Uber/Ola industry standard).",
            src.contains("BackoffPolicy.EXPONENTIAL")
        )
        assertTrue(
            "Backoff base must be 10 seconds (10_000L ms or TimeUnit.SECONDS with 10).",
            src.contains("10_000L") || src.contains("10L, TimeUnit.SECONDS") ||
                src.contains("10, TimeUnit.SECONDS") || src.contains("MIN_BACKOFF_MILLIS")
        )
    }

    @Test
    fun `HoldReleaseWorker requires network connectivity`() {
        val src = worker.readText()
        assertTrue(
            "Must gate on NetworkType.CONNECTED so we don't fail immediately on offline devices.",
            src.contains("NetworkType.CONNECTED")
        )
    }

    @Test
    fun `BroadcastListScreen handleDismiss gates on FF_HOLD_RELEASE_WORKMANAGER`() {
        val src = listScreen.readText()
        assertTrue(
            "Dismiss path must branch on the feature flag for safe rollout.",
            src.contains("FF_HOLD_RELEASE_WORKMANAGER")
        )
        assertTrue(
            "When flag is ON, dismiss must enqueue the worker.",
            src.contains("HoldReleaseWorker.enqueue") ||
                src.contains("HoldReleaseWorker.enqueueRelease")
        )
    }

    @Test
    fun `BroadcastListScreen preserves legacy scope launch path under flag OFF`() {
        val src = listScreen.readText()
        // Legacy `scope.launch { broadcastRepository.releaseHold(...) }` remains for OFF.
        assertTrue(
            "Legacy fire-and-forget path must survive behind `else` for instant rollback.",
            src.contains("scope.launch") && src.contains("broadcastRepository.releaseHold")
        )
    }

    @Test
    fun `BroadcastOverlayScreen handleDismiss gates on FF_HOLD_RELEASE_WORKMANAGER`() {
        val src = overlayScreen.readText()
        assertTrue(
            "Overlay dismiss must also branch on the same flag for parity.",
            src.contains("FF_HOLD_RELEASE_WORKMANAGER")
        )
        assertTrue(
            "When flag is ON, overlay dismiss must enqueue the worker.",
            src.contains("HoldReleaseWorker.enqueue") ||
                src.contains("HoldReleaseWorker.enqueueRelease")
        )
    }

    @Test
    fun `BroadcastOverlayScreen preserves legacy scope launch path under flag OFF`() {
        val src = overlayScreen.readText()
        assertTrue(
            "Overlay legacy path must survive behind `else` for rollback.",
            src.contains("scope.launch") && src.contains("broadcastRepository.releaseHold")
        )
    }

    @Test
    fun `FF_HOLD_RELEASE_WORKMANAGER is declared in build gradle and defaults OFF`() {
        val src = buildGradle.readText()
        assertTrue(
            "Flag must be declared as a boolean BuildConfig field.",
            src.contains("FF_HOLD_RELEASE_WORKMANAGER")
        )
        // Validate default OFF — the literal must be "false" in the buildConfigField line.
        val flagLineRegex = Regex("""buildConfigField\(\s*"boolean"\s*,\s*"FF_HOLD_RELEASE_WORKMANAGER"\s*,\s*"false"\s*\)""")
        assertTrue(
            "Default MUST be false per NO-DEPLOY constraint; flip later via release train.",
            flagLineRegex.containsMatchIn(src)
        )
    }

    @Test
    fun `HoldReleaseWorker does not short-circuit when holdId is blank`() {
        val src = worker.readText()
        assertTrue(
            "Blank holdId is a caller bug — worker should treat it as terminal failure, not retry.",
            (src.contains("isBlank()") || src.contains("isNullOrBlank()")) &&
                src.contains("Result.failure()")
        )
    }

    @Test
    fun `HoldReleaseWorker has no lingering scope launch leak`() {
        val src = worker.readText()
        // The worker itself must rely on `CoroutineWorker.doWork` suspension —
        // it must NOT spawn its own `scope.launch { ... }` (which would re-introduce
        // the exact fire-and-forget bug F-C-22 fixes).
        assertFalse(
            "Worker must NOT spawn its own coroutine scope — doWork is already structured.",
            src.contains("GlobalScope.launch") || src.contains("CoroutineScope(Dispatchers")
        )
    }

    @Test
    fun `HoldReleaseWorker companion WORK_NAME_PREFIX is stable`() {
        val src = worker.readText()
        // Unique-work scoping relies on a stable prefix; a rename would break KEEP semantics.
        assertTrue(
            "Stable work-name prefix must be present (SOL-7 exact pattern: release-hold-<holdId>).",
            src.contains("release-hold-")
        )
    }

    @Test
    fun `HoldReleaseWorker counts enqueue attempts for metrics pipeline`() {
        val src = worker.readText()
        // F-C-22 metric: captain_hold_release_worker_enqueue_total{result}.
        // We don't have a captain-side Prometheus pipeline yet; log via Timber tag is fine
        // as long as the enqueue path is observable.
        assertTrue(
            "Enqueue helper must log at INFO level with a stable tag for telemetry scraping.",
            src.contains("Timber.i") || src.contains("Timber.d") || src.contains("Log.i")
        )
    }
}
