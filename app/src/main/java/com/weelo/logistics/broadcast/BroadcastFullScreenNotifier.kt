package com.weelo.logistics.broadcast

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Builds and shows a high-priority notification with [setFullScreenIntent] for
 * background broadcasts — the industry-standard "incoming call" pattern used by
 * Uber, Rapido, and WhatsApp calls.
 *
 * BEHAVIOR:
 * ┌──────────────────────────────────────────────────────────────┐
 * │ Screen OFF / Lock screen → Android opens Activity directly  │
 * │ Screen ON, app background → heads-up notification appears   │
 * │ App in foreground → caller should NOT invoke this class      │
 * └──────────────────────────────────────────────────────────────┘
 *
 * THREAD SAFETY: All methods are safe to call from any thread.
 * PERFORMANCE: Single notification build (~1ms). Zero network calls.
 */
object BroadcastFullScreenNotifier {

    private const val TAG = "BroadcastFullScreenNotifier"

    /** Stable notification ID for incoming broadcasts — ensures only one at a time. */
    private const val NOTIFICATION_ID_INCOMING = 9001

    /** Channel ID — must match the IMPORTANCE_HIGH channel in WeeloFirebaseService. */
    private const val CHANNEL_ID = "broadcasts"

    /**
     * Show a full-screen incoming broadcast notification.
     *
     * @param context  Application or service context.
     * @param broadcastId  The broadcast ID (passed to BroadcastIncomingActivity).
     * @param title  Notification title (e.g., "New Booking Request").
     * @param body   Notification body (e.g., "Delhi → Mumbai • 3 trucks").
     */
    fun showIncomingBroadcast(
        context: Context,
        broadcastId: String,
        title: String,
        body: String
    ) {
        val appContext = context.applicationContext

        // -----------------------------------------------------------------
        // Full-screen intent → opens BroadcastIncomingActivity
        // -----------------------------------------------------------------
        val fullScreenIntent = Intent(appContext, BroadcastIncomingActivity::class.java).apply {
            putExtra("broadcast_id", broadcastId)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID_INCOMING,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // -----------------------------------------------------------------
        // Content intent → tapping heads-up opens BroadcastIncomingActivity
        // -----------------------------------------------------------------
        val contentIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID_INCOMING + 1,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // -----------------------------------------------------------------
        // Build notification
        // -----------------------------------------------------------------
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(com.weelo.logistics.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_INCOMING, notification)

        timber.log.Timber.i(
            "📲 Full-screen broadcast notification shown: id=%s title=%s",
            broadcastId, title
        )
    }

    /**
     * Dismiss the incoming broadcast notification.
     * Called when: user accepts/rejects, broadcast expires, or overlay handles it.
     */
    fun dismiss(context: Context) {
        val notificationManager =
            context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_INCOMING)
        timber.log.Timber.d("📲 Full-screen broadcast notification dismissed")
    }
}
