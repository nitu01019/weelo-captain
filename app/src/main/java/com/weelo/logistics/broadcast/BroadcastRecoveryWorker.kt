package com.weelo.logistics.broadcast

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.weelo.logistics.data.remote.RetrofitClient

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

        BroadcastFlowCoordinator.requestReconcile(force = true)
        BroadcastTelemetry.record(
            stage = BroadcastStage.NOTIFICATION_RECOVERY,
            status = BroadcastStatus.SUCCESS,
            attrs = mapOf(
                "mode" to "coordinator_reconcile_only"
            )
        )
        return Result.success()
    }
}
