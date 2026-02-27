package com.weelo.logistics.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.weelo.logistics.MainActivity
import com.weelo.logistics.R
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.offline.AvailabilityManager
import com.weelo.logistics.offline.AvailabilityState

/**
 * Foreground service that keeps transporter heartbeat active when user is online,
 * even if the app task is closed.
 */
class TransporterOnlineService : Service() {

    enum class StartResult {
        STARTED,
        SKIPPED_MISSING_PERMISSION,
        SKIPPED_NOT_TRANSPORTER,
        SKIPPED_NOT_LOGGED_IN,
        SKIPPED_OFFLINE_STATE
    }

    companion object {
        private const val CHANNEL_ID = "transporter_online_channel"
        private const val CHANNEL_NAME = "Transporter Online"
        private const val NOTIFICATION_ID = 3201

        fun start(
            context: Context,
            expectedAvailability: AvailabilityState? = null
        ): StartResult {
            val appContext = context.applicationContext
            val startResult = evaluateStartPreconditions(appContext, expectedAvailability)
            if (startResult != StartResult.STARTED) {
                timber.log.Timber.w("⏭️ TransporterOnlineService start skipped: %s", startResult)
                return startResult
            }

            val intent = Intent(appContext, TransporterOnlineService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            return StartResult.STARTED
        }

        fun stop(context: Context) {
            HeartbeatManager.stop()
            val appContext = context.applicationContext
            appContext.stopService(Intent(appContext, TransporterOnlineService::class.java))
        }

        private fun evaluateStartPreconditions(
            context: Context,
            expectedAvailability: AvailabilityState? = null
        ): StartResult {
            val isLoggedIn = RetrofitClient.isLoggedIn()
            if (!isLoggedIn) return StartResult.SKIPPED_NOT_LOGGED_IN

            val role = RetrofitClient.getUserRole()
            val isTransporter = role.equals("transporter", ignoreCase = true)
            if (!isTransporter) return StartResult.SKIPPED_NOT_TRANSPORTER

            val availabilityState = expectedAvailability
                ?: AvailabilityManager.getInstance(context).availabilityState.value
            if (availabilityState != AvailabilityState.ONLINE) {
                return StartResult.SKIPPED_OFFLINE_STATE
            }

            if (!HeartbeatManager.hasLocationPermission(context)) {
                return StartResult.SKIPPED_MISSING_PERMISSION
            }

            return StartResult.STARTED
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires startForeground() shortly after startForegroundService().
        // Call it before any early-return path to avoid process-killing timeout crashes.
        startForeground(NOTIFICATION_ID, createNotification())

        val startResult = evaluateStartPreconditions(this)
        if (startResult != StartResult.STARTED) {
            timber.log.Timber.w("⏭️ TransporterOnlineService no-op after foreground start: %s", startResult)
            HeartbeatManager.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        HeartbeatManager.start()
        timber.log.Timber.i("💓 TransporterOnlineService started (heartbeat active)")

        // Restart if killed by system while online toggle is still on.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        timber.log.Timber.i("💔 TransporterOnlineService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps transporter online for instant broadcast requests"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("You are online")
            .setContentText("Listening for new broadcast requests")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

}
