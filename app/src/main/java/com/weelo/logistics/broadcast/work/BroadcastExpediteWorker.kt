package com.weelo.logistics.broadcast.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.weelo.logistics.broadcast.BroadcastFeatureFlagsRegistry
import com.weelo.logistics.broadcast.BroadcastFlowCoordinator
import com.weelo.logistics.broadcast.BroadcastIngressEnvelope
import com.weelo.logistics.broadcast.BroadcastIngressSource
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * =============================================================================
 * F-C-60 — Data-only FCM wake-up worker (SOL-8 §F-C-60 captain consumer)
 * =============================================================================
 *
 * PROBLEM (pre-fix):
 *   Backend sends FCM with `notification + data` hybrid for FULLSCREEN_TYPES
 *   (e.g. `new_broadcast`). Firebase SDK behavior in Doze:
 *     - Hybrid (`notification` present): SDK renders system notification and
 *       SKIPS `onMessageReceived`. Captain's overlay dedup + `ingestFcmEnvelope`
 *       path never runs — the wake-via-data pipeline is silently bypassed.
 *     - Data-only (`notification` missing): SDK fires `onMessageReceived` with
 *       a 10-second wallclock budget before Doze reclaims the process.
 *
 *   With the hybrid payload, time-critical broadcasts (e.g. driver-facing FSI)
 *   only show as a regular heads-up — there's no guaranteed wake of the overlay
 *   activity. Google's April 2025 guidance is: for time-critical, send
 *   data-only with `priority=high` + `ttl<=60s` so `onMessageReceived` fires.
 *
 * FIX (backend P8 + this worker):
 *   Backend `FF_FCM_DATA_ONLY_FULLSCREEN` (default OFF) strips the `notification`
 *   block for FULLSCREEN_TYPES — only `data` remains. Captain's FCM service
 *   detects `remoteMessage.notification == null` for known fullscreen types
 *   and hands off to THIS worker via `enqueue(...)`. The worker:
 *     1. Reads the envelope fields (type, broadcastId, payloadVersion,
 *        receivedAtMs) from `inputData`.
 *     2. Calls `BroadcastFlowCoordinator.ingestFcmEnvelope(...)` which uses
 *        the normal dedup/reconcile path — no forked ingress logic.
 *     3. Returns `Result.success()` unconditionally (coordinator handles its
 *        own error paths + drop reasons).
 *
 *   Expedited execution (`setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`)
 *   tells WorkManager to run ASAP, with graceful fallback to normal priority
 *   if the app has exhausted its expedited quota (Android 12+).
 *
 * FEATURE FLAG:
 *   Captain-side `BuildConfig.FF_FCM_DATA_ONLY_HANDLER` (default OFF). Backend
 *   `FF_FCM_DATA_ONLY_FULLSCREEN` MUST stay OFF until captain release is at
 *   >=90% DAU (release-train rule — see SOL-8 §F-C-60 Play Store gate). In
 *   NO-DEPLOY mode both flags stay OFF and this worker is dormant.
 *
 * RELEASE TRAIN (informational):
 *   1. Ship captain with this worker (flag OFF).
 *   2. Wait for release to >=90% DAU.
 *   3. Flip captain flag -> ON via SharedPrefs.
 *   4. Flip backend flag `FF_FCM_DATA_ONLY_FULLSCREEN` -> 10% canary.
 *   5. Monitor `fcm_push_priority_total{priority=high}` + `overlay_shown_total`.
 *   6. Expand to 100% after 48h clean.
 *
 * DIFFERENTIATOR IMPACT:
 *   Preserves the two-phase hold differentiator. Data-only FCM for
 *   `driver_assignment` type wakes the captain reliably, ensuring FLEX->CONFIRMED
 *   transitions surface to the driver in Doze.
 *
 * @see INDEX.md Part 3 §F-C-60
 * @see SOL-8 §F-C-60
 * =============================================================================
 */
class BroadcastExpediteWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Graceful no-op when coordinator is disabled — F-C-60 requires working
        // with both hybrid and data-only payloads without crashing. If the user
        // has broadcastCoordinator turned off via SharedPrefs, we silently skip.
        val flags = BroadcastFeatureFlagsRegistry.current()
        if (!flags.broadcastCoordinatorEnabled) {
            Timber.i("BroadcastExpediteWorker: coordinator disabled — Result.success() (no-op)")
            return Result.success()
        }

        val type = inputData.getString(KEY_TYPE)?.trim().orEmpty()
        val broadcastId = inputData.getString(KEY_BROADCAST_ID)?.trim().orEmpty()
        val payloadVersion = inputData.getString(KEY_PAYLOAD_VERSION)?.takeIf { it.isNotBlank() }
        val receivedAtMs = inputData.getLong(KEY_RECEIVED_AT_MS, System.currentTimeMillis())

        if (broadcastId.isBlank()) {
            // Malformed payload — backend produced a data-only FCM without a
            // broadcastId. Don't retry; escalate as Result.failure() so it shows
            // up in dashboards.
            Timber.w("BroadcastExpediteWorker: blank broadcastId for type=%s — Result.failure()", type)
            return Result.failure()
        }

        Timber.i(
            "BroadcastExpediteWorker: ingesting data-only FCM type=%s broadcastId=%s payloadVersion=%s",
            type,
            broadcastId,
            payloadVersion ?: "(none)"
        )

        BroadcastFlowCoordinator.ingestFcmEnvelope(
            BroadcastIngressEnvelope(
                source = BroadcastIngressSource.FCM,
                rawEventName = type.ifBlank { DEFAULT_RAW_EVENT_NAME },
                normalizedId = broadcastId,
                receivedAtMs = receivedAtMs,
                payloadVersion = payloadVersion,
                broadcast = null
            )
        )

        return Result.success()
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_BROADCAST_ID = "broadcastId"
        private const val KEY_PAYLOAD_VERSION = "payloadVersion"
        private const val KEY_RECEIVED_AT_MS = "receivedAtMs"
        private const val WORK_NAME_PREFIX = "expedite-broadcast-"
        private const val DEFAULT_RAW_EVENT_NAME = "new_broadcast"
        private const val BACKOFF_BASE_SECONDS = 10L

        /**
         * Enqueue an expedited work request to wake the overlay pipeline for a
         * data-only FCM payload. Uses `setExpedited` so the job runs within
         * Android's expedited budget (Doze-immune). If the app is out of quota,
         * `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST` downgrades to
         * normal priority — still runs, just without the fast-lane.
         *
         * Unique work name `expedite-broadcast-<broadcastId>` means duplicate
         * FCM pushes for the same broadcast (socket + FCM fan-out) collapse to
         * a single worker run.
         */
        fun enqueue(
            context: Context,
            type: String,
            broadcastId: String,
            payloadVersion: String?,
            receivedAtMs: Long
        ) {
            val normalizedId = broadcastId.trim()
            if (normalizedId.isBlank()) {
                Timber.w("BroadcastExpediteWorker.enqueue: skipped — blank broadcastId")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val input = Data.Builder()
                .putString(KEY_TYPE, type.trim())
                .putString(KEY_BROADCAST_ID, normalizedId)
                .putString(KEY_PAYLOAD_VERSION, payloadVersion?.takeIf { it.isNotBlank() })
                .putLong(KEY_RECEIVED_AT_MS, receivedAtMs)
                .build()

            val request = OneTimeWorkRequestBuilder<BroadcastExpediteWorker>()
                .setConstraints(constraints)
                .setInputData(input)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_BASE_SECONDS,
                    TimeUnit.SECONDS
                )
                .addTag(TAG_EXPEDITE_BROADCAST)
                .build()

            val workName = "$WORK_NAME_PREFIX$normalizedId"

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)

            Timber.i(
                "BroadcastExpediteWorker.enqueue: enqueued workName=%s type=%s (F-C-60 / FF_FCM_DATA_ONLY_HANDLER)",
                workName,
                type
            )
        }

        const val TAG_EXPEDITE_BROADCAST = "broadcast_expedite"
    }
}
