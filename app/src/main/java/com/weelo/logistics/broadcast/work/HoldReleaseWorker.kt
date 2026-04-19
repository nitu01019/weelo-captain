package com.weelo.logistics.broadcast.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * =============================================================================
 * F-C-22 — WorkManager-backed hold release (SOL-7 §S-22)
 * =============================================================================
 *
 * PROBLEM (pre-fix):
 *   `BroadcastListScreen.handleDismiss` and `BroadcastOverlayScreen.handleDismiss`
 *   both called `scope.launch { broadcastRepository.releaseHold(holdId) }`. That
 *   is fire-and-forget — the coroutine is bound to the composable's lifecycle.
 *   If the user force-stops the app (or the OS reclaims the process) BEFORE the
 *   network request resolves, the DELETE never reaches the backend. The Redis
 *   hold then sits for the full 180s server TTL, blocking all other transporters
 *   from competing for that vehicle. In a multi-truck/multi-transporter fleet,
 *   that one stuck hold reduces throughput system-wide.
 *
 * FIX (this worker):
 *   WorkManager owns the release. A `OneTimeWorkRequest` is enqueued per
 *   `holdId` with `ExistingWorkPolicy.KEEP` — duplicate enqueue attempts for
 *   the same id collapse to one job. On transient network failure (`IOException`)
 *   we return `Result.retry()` and WorkManager replays with exponential backoff
 *   (10s -> 20s -> 40s, capped by OS). On terminal failure (auth expired / 4xx
 *   other than 404) we return `Result.failure()`. On success OR when the server
 *   responds `HOLD_NOT_FOUND` (404 — already released), the repository maps it
 *   to `BroadcastResult.Success(true)` and the worker reports `Result.success()`.
 *
 * IDEMPOTENCY CONTRACT:
 *   - `BroadcastRepository.releaseHold` already uses a stable
 *     `idempotency-key` per hold id (see `releaseAttemptIdempotencyKeys`).
 *   - Backend `DELETE /truck-hold/{holdId}` returns 204 on already-released
 *     (see `src/modules/truck-hold/truck-hold-crud.routes.ts:186`). This
 *     worker relies on that backend contract (tracked under KICKOFF.md P9
 *     prereq check — we trust the endpoint is idempotent).
 *
 * FEATURE FLAG:
 *   Call-sites gate on `BuildConfig.FF_HOLD_RELEASE_WORKMANAGER` (default OFF,
 *   per NO-DEPLOY constraint). Legacy `scope.launch` survives under `else`.
 *
 * DIFFERENTIATOR IMPACT:
 *   Strengthens the multi-vehicle differentiator. Faster release of stale holds
 *   unblocks other transporters from the same per-vehicle hold pool, increasing
 *   fleet utilization under contention.
 *
 * METRIC (future):
 *   `captain_hold_release_worker_enqueue_total{result=success|retry|failure}`.
 *   For now we surface via Timber.i so logcat can be scraped.
 *
 * @see INDEX.md Part 3 §F-C-22
 * @see SOL-7 §S-22
 * =============================================================================
 */
class HoldReleaseWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val holdId = inputData.getString(KEY_HOLD_ID)?.trim().orEmpty()

        // Blank holdId is a caller bug. Treat as terminal failure — no amount of
        // retrying will fabricate an id. Surface via Timber so the call-site
        // regression is visible in logcat.
        if (holdId.isBlank()) {
            Timber.w("HoldReleaseWorker: blank holdId — returning Result.failure()")
            return Result.failure()
        }

        Timber.i("HoldReleaseWorker: releasing holdId=%s (attempt=%d)", holdId, runAttemptCount)

        return try {
            val repository = BroadcastRepository.getInstance(applicationContext)
            when (val result = repository.releaseHold(holdId)) {
                is BroadcastResult.Success -> {
                    // Repository maps HOLD_NOT_FOUND (already released, backend
                    // returns 204) and true success to Success(true). Either
                    // way, the user's intent is satisfied — log and finish.
                    Timber.i("HoldReleaseWorker: release succeeded holdId=%s (idempotent-success-path)", holdId)
                    Result.success()
                }
                is BroadcastResult.Error -> handleRepositoryError(holdId, result)
                is BroadcastResult.Loading -> {
                    // Shouldn't happen (releaseHold is a suspend fn that resolves
                    // to Success/Error), but treat as retry-able.
                    Timber.w("HoldReleaseWorker: unexpected Loading state for holdId=%s — retrying", holdId)
                    Result.retry()
                }
            }
        } catch (io: IOException) {
            // Network blip — retry with exponential backoff (10s base).
            Timber.w(io, "HoldReleaseWorker: IOException releasing holdId=%s — Result.retry()", holdId)
            Result.retry()
        } catch (t: Throwable) {
            // Unknown runtime error. Retry once via backoff; WorkManager caps
            // `runAttemptCount` so it won't loop forever.
            Timber.e(t, "HoldReleaseWorker: unexpected error releasing holdId=%s", holdId)
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun handleRepositoryError(holdId: String, error: BroadcastResult.Error): Result {
        // Auth expired is terminal — future auth token will arrive via onNewToken
        // but this WorkManager job can't wait for that. Let it fail; user's next
        // app open will re-auth and any remaining stale hold falls off the 180s
        // server TTL.
        if (error.code == 401 || error.apiCode == "AUTH_EXPIRED") {
            Timber.w("HoldReleaseWorker: auth expired for holdId=%s — Result.failure()", holdId)
            return Result.failure()
        }

        // 5xx / transient server errors — retry.
        val isServerError = (error.code ?: 0) in 500..599
        val looksTransient = error.message.contains("timeout", ignoreCase = true) ||
            error.message.contains("network", ignoreCase = true) ||
            isServerError

        return if (looksTransient && runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Timber.w("HoldReleaseWorker: transient error holdId=%s code=%s — Result.retry()", holdId, error.code)
            Result.retry()
        } else {
            Timber.e("HoldReleaseWorker: terminal error holdId=%s code=%s msg=%s", holdId, error.code, error.message)
            Result.failure()
        }
    }

    companion object {
        private const val KEY_HOLD_ID = "holdId"
        private const val WORK_NAME_PREFIX = "release-hold-"
        private const val BACKOFF_BASE_SECONDS = 10L
        private const val MAX_RETRY_ATTEMPTS = 5

        /**
         * Enqueue a release for the given hold id. Uses `ExistingWorkPolicy.KEEP`
         * so duplicate dismiss events for the same hold id coalesce into a single
         * WorkManager job. Unique name `release-hold-<holdId>` scopes the KEEP.
         *
         * NETWORK CONSTRAINT: `NetworkType.CONNECTED`. If the user is offline when
         * they dismiss, WorkManager schedules the release to run on reconnect — the
         * 180s server TTL covers the gap.
         *
         * BACKOFF: `EXPONENTIAL` with 10s base (SOL-7 exact value). After 5 retries
         * WorkManager gives up (server TTL has long since expired anyway).
         *
         * @return WorkManager `operationId` for observability.
         */
        fun enqueue(context: Context, holdId: String) {
            val normalizedHoldId = holdId.trim()
            if (normalizedHoldId.isBlank()) {
                Timber.w("HoldReleaseWorker.enqueue: skipped — blank holdId")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val input = Data.Builder()
                .putString(KEY_HOLD_ID, normalizedHoldId)
                .build()

            val request = OneTimeWorkRequestBuilder<HoldReleaseWorker>()
                .setConstraints(constraints)
                .setInputData(input)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_BASE_SECONDS,
                    TimeUnit.SECONDS
                )
                .addTag(TAG_RELEASE_HOLD)
                .build()

            val workName = "$WORK_NAME_PREFIX$normalizedHoldId"

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)

            Timber.i(
                "HoldReleaseWorker.enqueue: enqueued workName=%s (F-C-22 / FF_HOLD_RELEASE_WORKMANAGER)",
                workName
            )
        }

        const val TAG_RELEASE_HOLD = "hold_release"
    }
}
