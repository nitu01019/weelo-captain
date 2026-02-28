package com.weelo.logistics.broadcast

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.repository.BroadcastFetchQueryMode
import com.weelo.logistics.data.repository.BroadcastRepository
import com.weelo.logistics.data.repository.BroadcastResult

/**
 * Background reconcile hook for app launch/resume.
 *
 * Purpose: recover active broadcasts missed while process was dead/backgrounded.
 */
class BroadcastRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val role = RetrofitClient.getUserRole()
        if (!BroadcastRolePolicy.canHandleBroadcastIngress(role)) {
            return Result.success()
        }

        val repository = BroadcastRepository.getInstance(applicationContext)
        return when (val result = repository.fetchActiveBroadcasts(
            forceRefresh = true,
            queryMode = BroadcastFetchQueryMode.BOOKINGS_REQUESTS_PRIMARY_WITH_BROADCASTS_FALLBACK
        )) {
            is BroadcastResult.Success -> {
                val activeIds = result.data.broadcasts.map { it.broadcastId }
                val unseenCount = BroadcastRecoveryTracker.countUnseenActive(applicationContext, activeIds)
                BroadcastRecoveryTracker.recordRecoveryResult(applicationContext, unseenCount)
                if (activeIds.isNotEmpty()) {
                    BroadcastFlowCoordinator.requestReconcile(force = true)
                }
                BroadcastTelemetry.record(
                    stage = BroadcastStage.NOTIFICATION_RECOVERY,
                    status = BroadcastStatus.SUCCESS,
                    attrs = mapOf(
                        "activeCount" to activeIds.size.toString(),
                        "unseenCount" to unseenCount.toString()
                    )
                )
                Result.success()
            }

            is BroadcastResult.Error -> {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.NOTIFICATION_RECOVERY,
                    status = BroadcastStatus.FAILED,
                    reason = result.message
                )
                Result.retry()
            }

            is BroadcastResult.Loading -> Result.success()
        }
    }
}
