package com.weelo.logistics.broadcast

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BroadcastRecoveryScheduler {
    private const val UNIQUE_WORK_NAME = "broadcast_recovery_reconcile"

    fun schedule(context: Context, trigger: String) {
        val request = OneTimeWorkRequestBuilder<BroadcastRecoveryWorker>()
            .setInitialDelay(2, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("broadcast_recovery")
            .build()

        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)

        BroadcastTelemetry.record(
            stage = BroadcastStage.NOTIFICATION_RECOVERY,
            status = BroadcastStatus.SUCCESS,
            reason = "scheduled",
            attrs = mapOf("trigger" to trigger)
        )
    }
}
