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

    companion object {
        private const val CHANNEL_ID = "transporter_online_channel"
        private const val CHANNEL_NAME = "Transporter Online"
        private const val NOTIFICATION_ID = 3201

        fun start(context: Context) {
            val intent = Intent(context, TransporterOnlineService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            HeartbeatManager.stop()
            context.stopService(Intent(context, TransporterOnlineService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val role = RetrofitClient.getUserRole()
        val isTransporter = role.equals("transporter", ignoreCase = true)
        val isLoggedIn = RetrofitClient.isLoggedIn()
        val availabilityState = AvailabilityManager.getInstance(this).availabilityState.value

        if (!isLoggedIn || !isTransporter || availabilityState != AvailabilityState.ONLINE) {
            timber.log.Timber.i(
                "⏭️ TransporterOnlineService skipped (loggedIn=%s, role=%s, availability=%s)",
                isLoggedIn,
                role,
                availabilityState
            )
            HeartbeatManager.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!HeartbeatManager.hasLocationPermission(this)) {
            timber.log.Timber.w("⚠️ TransporterOnlineService cannot start: location permission missing")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
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
