package com.weelo.logistics.broadcast

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

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
 * ANDROID 14+ (API 34) COMPLIANCE — F-C-01 fix:
 *   On API ≥34, apps in the "calling/alarm" profile lose default
 *   USE_FULL_SCREEN_INTENT permission (Google Play mandate, Jan 22 2025).
 *   If the OS app-op is denied, `setFullScreenIntent(...)` is silently
 *   dropped → captain misses broadcasts with no user feedback.
 *
 *   Fix: runtime-gate the FSI call via
 *   [NotificationManagerCompat.canUseFullScreenIntent]; when denied, fall
 *   back to a heads-up high-priority notification with the same
 *   sound/vibration/content so the captain still gets alerted.
 *
 *   See: https://developer.android.com/about/versions/14/behavior-changes-14
 *   See: https://source.android.com/docs/core/permissions/fsi-limits
 *
 * THREAD SAFETY: All methods are safe to call from any thread.
 * PERFORMANCE: Single notification build (~1ms). Zero network calls.
 */
object BroadcastFullScreenNotifier {

    private const val TAG = "BroadcastFullScreenNotifier"

    /** Stable notification ID for incoming broadcasts — ensures only one at a time. */
    private const val NOTIFICATION_ID_INCOMING = 9001

    /** Channel ID — must match the IMPORTANCE_HIGH channel in WeeloFirebaseService. */
    // I-9 FIX: Changed "broadcasts" → "broadcasts_v2" to match registered channel.
    // Previous mismatch caused ALL full-screen broadcast notifications to be silently
    // dropped on Android 8+ (~97% devices). Notifications posted to unregistered
    // channels are suppressed by the OS with no error or callback.
    private const val CHANNEL_ID = "broadcasts_v2"

    /**
     * Server-side broadcast decision deadline (match backend broadcast TTL).
     * After this, the notification auto-dismisses so stale prompts don't linger.
     */
    private const val BROADCAST_TIMEOUT_MS = 45_000L

    /**
     * Pure decision helper — returns true iff the OS will honour
     * [NotificationCompat.Builder.setFullScreenIntent] for this process.
     *
     * On API <34 the op is always granted (pre-Android-14 behaviour).
     * On API ≥34 we consult [NotificationManagerCompat.canUseFullScreenIntent]
     * — this is the only reliable check per AOSP
     * (checkSelfPermission() does NOT work on Android 14+).
     *
     * Extracted so it is unit-testable without a real Android Context:
     * callers can inject [sdkInt] and a [canUseFsiFn] supplier.
     */
    internal fun shouldUseFullScreenIntent(
        sdkInt: Int,
        canUseFsiFn: () -> Boolean
    ): Boolean {
        // Build.VERSION_CODES.UPSIDE_DOWN_CAKE == 34 (Android 14).
        if (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return canUseFsiFn()
    }

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
        // F-C-01: Android 14+ fullScreenIntent runtime gate.
        // On API ≥34 with app-op denied, we MUST fall back to heads-up.
        // -----------------------------------------------------------------
        val canFSI = shouldUseFullScreenIntent(
            sdkInt = Build.VERSION.SDK_INT,
            canUseFsiFn = { NotificationManagerCompat.from(appContext).canUseFullScreenIntent() }
        )

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
        // Build notification.
        //   - Same sound/vibration/priority/category on BOTH paths so the
        //     captain still gets a call-like heads-up when FSI is denied.
        //   - `setFullScreenIntent` is applied ONLY if the op is allowed.
        //   - F-C-01 correction: `setAutoCancel(true)` + `setOngoing(true)`
        //     contradicted each other (a call-like notification the user
        //     must answer/decline should NOT auto-cancel on tap). Using
        //     `setOngoing(true) + setAutoCancel(false) + setTimeoutAfter`
        //     so it only dismisses at the server-side deadline.
        // -----------------------------------------------------------------
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(com.weelo.logistics.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setTimeoutAfter(BROADCAST_TIMEOUT_MS)
            .setSound(android.net.Uri.parse("android.resource://${appContext.packageName}/${com.weelo.logistics.R.raw.broadcast_ringtone}"))
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (canFSI) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        val notification = builder.build()

        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_INCOMING, notification)

        // -----------------------------------------------------------------
        // Telemetry — canFSI granted/denied per broadcast.
        //   Reuses existing BROADCAST_GATED stage since BroadcastStage is
        //   an enum (can't add FSI_GATE_CHECK without cross-file edit).
        //   attrs disambiguate the specific gate surface so SREs can filter.
        // -----------------------------------------------------------------
        BroadcastTelemetry.record(
            stage = BroadcastStage.BROADCAST_GATED,
            status = if (canFSI) BroadcastStatus.SUCCESS else BroadcastStatus.SKIPPED,
            reason = if (canFSI) "fsi_granted" else "fsi_denied",
            attrs = mapOf(
                "gate" to "fsi",
                "can_use_fsi" to canFSI.toString(),
                "api" to Build.VERSION.SDK_INT.toString(),
                "broadcast_id" to broadcastId
            )
        )

        timber.log.Timber.i(
            "📲 Full-screen broadcast notification shown: id=%s title=%s canFSI=%s",
            broadcastId, title, canFSI
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
