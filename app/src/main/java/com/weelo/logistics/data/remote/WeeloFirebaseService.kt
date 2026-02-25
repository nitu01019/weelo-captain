package com.weelo.logistics.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.weelo.logistics.MainActivity
import com.weelo.logistics.R
import com.weelo.logistics.broadcast.BroadcastFeatureFlagsRegistry
import com.weelo.logistics.broadcast.BroadcastFlowCoordinator
import com.weelo.logistics.broadcast.BroadcastIngressEnvelope
import com.weelo.logistics.broadcast.BroadcastIngressSource
import com.weelo.logistics.broadcast.BroadcastRolePolicy
import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * =============================================================================
 * FIREBASE CLOUD MESSAGING SERVICE - Push Notifications
 * =============================================================================
 * 
 * Handles push notifications from weelo-backend when app is:
 * - In background (shows system notification)
 * - In foreground (emits to flow for in-app handling)
 * 
 * NOTIFICATION TYPES:
 * - new_broadcast: New booking request available
 * - assignment_update: Your assignment status changed
 * - trip_update: Trip status changed
 * - payment: Payment received/pending
 * 
 * FOR BACKEND DEVELOPERS:
 * - Send FCM via: Firebase Admin SDK
 * - Required fields: type, title, body
 * - Optional: data payload with booking/trip details
 * 
 * SCALABILITY:
 * - FCM handles millions of devices automatically
 * - Use topics for broadcast to vehicle types
 * - Use individual tokens for targeted notifications
 * =============================================================================
 */
class WeeloFirebaseService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "WeeloFCM"
        private const val PREFS_NAME = "weelo_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"
        
        // Notification channels
        const val CHANNEL_BROADCASTS = "broadcasts"
        const val CHANNEL_TRIPS = "trips"
        const val CHANNEL_PAYMENTS = "payments"
        const val CHANNEL_GENERAL = "general"
        
        // Notification types from backend
        const val TYPE_NEW_BROADCAST = "new_broadcast"
        const val TYPE_NEW_TRUCK_REQUEST = "new_truck_request"
        const val TYPE_ASSIGNMENT_UPDATE = "assignment_update"
        const val TYPE_TRIP_UPDATE = "trip_update"
        const val TYPE_TRIP_ASSIGNED = "trip_assigned"
        const val TYPE_DRIVER_TIMEOUT = "driver_timeout"
        const val TYPE_BOOKING_CANCELLED = "booking_cancelled"
        const val TYPE_BOOKING_EXPIRED = "booking_expired"
        const val TYPE_TRIP_STATUS_UPDATE = "trip_status_update"
        const val TYPE_DRIVER_ACCEPTED = "driver_accepted"
        const val TYPE_TRUCKS_CONFIRMED = "trucks_confirmed"
        const val TYPE_BOOKING_COMPLETED = "booking_completed"
        const val TYPE_DRIVER_OFFLINE = "driver_offline"
        const val TYPE_PAYMENT = "payment"
        const val TYPE_GENERAL = "general"
        
        // Flow for foreground notifications
        private val _foregroundNotifications = MutableSharedFlow<FCMNotification>(
            replay = 0,
            extraBufferCapacity = 20
        )
        val foregroundNotifications: SharedFlow<FCMNotification> = _foregroundNotifications.asSharedFlow()
        
        // Store FCM token for backend registration
        private var _fcmToken: String? = null
        val fcmToken: String? get() = _fcmToken

        fun cacheToken(context: Context, token: String) {
            _fcmToken = token
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FCM_TOKEN, token)
                .apply()
        }

        fun getCachedToken(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, null)
        }
        
        // Token update callback
        var onTokenRefresh: ((String) -> Unit)? = null
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        _fcmToken = getCachedToken(this)
    }
    
    /**
     * Called when FCM token is generated or refreshed
     * Backend needs this token to send targeted notifications
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        timber.log.Timber.i("🔑 New FCM token generated")
        cacheToken(this, token)
        
        CoroutineScope(Dispatchers.IO).launch {
            NotificationTokenSync.registerCurrentToken(reason = "fcm_service_on_new_token")
        }

        // Notify the app to register token with backend
        onTokenRefresh?.invoke(token)
        timber.log.Timber.d("📡 FCM token refresh callback dispatched")
    }
    
    /**
     * Called when a message is received
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        timber.log.Timber.i("📩 FCM message received from: ${remoteMessage.from}")
        
        // Get notification data
        val data = remoteMessage.data
        val notification = remoteMessage.notification
        
        val rawType = data["type"] ?: TYPE_GENERAL
        val type = normalizeNotificationType(rawType)
        val title = notification?.title ?: data["title"] ?: "Weelo"
        val body = notification?.body ?: data["body"] ?: ""
        val userRole = RetrofitClient.getUserRole()
        val canDisplayNotification = BroadcastRolePolicy.canDisplayNotification(userRole, type)
        val canHandleBroadcastIngress = BroadcastRolePolicy.canHandleBroadcastIngress(userRole)

        timber.log.Timber.d("Type: $type, Title: $title, Body: $body")
        timber.log.Timber.d("Data: $data")
        
        val normalizedBroadcastId = sequenceOf(
            data["broadcastId"],
            data["orderId"],
            data["bookingId"],
            data["id"]
        ).firstOrNull { !it.isNullOrBlank() }

        BroadcastTelemetry.record(
            stage = BroadcastStage.FCM_RECEIVED,
            status = BroadcastStatus.SUCCESS,
            attrs = mapOf(
                "type" to type,
                "rawType" to rawType,
                "hasId" to (!normalizedBroadcastId.isNullOrBlank()).toString(),
                "role" to (userRole ?: "unknown"),
                "displayAllowed" to canDisplayNotification.toString()
            )
        )

        if (!canDisplayNotification) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.FCM_RECEIVED,
                status = BroadcastStatus.SKIPPED,
                reason = "role_not_allowed_for_notification_type",
                attrs = mapOf(
                    "type" to type,
                    "rawType" to rawType,
                    "role" to (userRole ?: "unknown")
                )
            )
            return
        }

        // Create FCM notification object
        val fcmNotification = FCMNotification(
            type = type,
            title = title,
            body = body,
            data = data,
            broadcastId = normalizedBroadcastId,
            tripId = data["tripId"],
            assignmentId = data["assignmentId"],
            amount = data["amount"]?.toDoubleOrNull()
        )

        if (BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled &&
            type == TYPE_NEW_BROADCAST &&
            canHandleBroadcastIngress
        ) {
            BroadcastFlowCoordinator.ingestFcmEnvelope(
                BroadcastIngressEnvelope(
                    source = BroadcastIngressSource.FCM,
                    rawEventName = type,
                    normalizedId = normalizedBroadcastId.orEmpty(),
                    receivedAtMs = System.currentTimeMillis(),
                    payloadVersion = data["payloadVersion"],
                    broadcast = null
                )
            )
        }
        
        // Emit to flow for foreground handling
        CoroutineScope(Dispatchers.IO).launch {
            _foregroundNotifications.emit(fcmNotification)
        }
        
        // ================================================================
        // FCM-TRIGGERED BROADCAST REMOVAL
        // When booking is cancelled/expired, remove from overlay immediately
        // This covers the case where socket was disconnected but FCM arrived
        // ================================================================
        if ((type == TYPE_BOOKING_CANCELLED || type == TYPE_BOOKING_EXPIRED) && canHandleBroadcastIngress) {
            val broadcastIdToRemove = fcmNotification.broadcastId
            if (!broadcastIdToRemove.isNullOrEmpty()) {
                timber.log.Timber.i("🚫 FCM: Removing broadcast $broadcastIdToRemove (type=$type)")
                if (BroadcastFeatureFlagsRegistry.current().broadcastCoordinatorEnabled) {
                    BroadcastFlowCoordinator.ingestFcmEnvelope(
                        BroadcastIngressEnvelope(
                            source = BroadcastIngressSource.FCM,
                            rawEventName = type,
                            normalizedId = broadcastIdToRemove,
                            receivedAtMs = System.currentTimeMillis(),
                            payloadVersion = data["payloadVersion"],
                            broadcast = null
                        )
                    )
                } else {
                    com.weelo.logistics.broadcast.BroadcastOverlayManager.removeEverywhere(broadcastIdToRemove)
                }
            }
        }
        
        // Show notification (for background or if app wants to show it)
        showNotification(fcmNotification)
    }

    private fun normalizeNotificationType(rawType: String): String {
        val normalized = rawType.trim().lowercase()
        return when (normalized) {
            TYPE_NEW_TRUCK_REQUEST -> TYPE_NEW_BROADCAST
            else -> normalized
        }
    }
    
    /**
     * Create notification channels (required for Android 8.0+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Broadcasts channel - High importance for new booking requests
            val broadcastsChannel = NotificationChannel(
                CHANNEL_BROADCASTS,
                "New Booking Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new booking requests"
                enableVibration(true)
                enableLights(true)
            }
            
            // Trips channel - Default importance for trip updates
            val tripsChannel = NotificationChannel(
                CHANNEL_TRIPS,
                "Trip Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for trip status changes"
            }
            
            // Payments channel - High importance for payment updates
            val paymentsChannel = NotificationChannel(
                CHANNEL_PAYMENTS,
                "Payments",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for payments"
                enableVibration(true)
            }
            
            // General channel
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General notifications"
            }
            
            notificationManager.createNotificationChannels(
                listOf(broadcastsChannel, tripsChannel, paymentsChannel, generalChannel)
            )
            
            timber.log.Timber.d("Notification channels created")
        }
    }
    
    /**
     * Show a system notification
     */
    private fun showNotification(notification: FCMNotification) {
        val channelId = when (notification.type) {
            TYPE_NEW_BROADCAST -> CHANNEL_BROADCASTS
            TYPE_TRIP_UPDATE -> CHANNEL_TRIPS
            TYPE_TRIP_ASSIGNED -> CHANNEL_TRIPS        // New trip assigned — HIGH priority
            TYPE_DRIVER_TIMEOUT -> CHANNEL_TRIPS       // Driver didn't respond — HIGH priority
            TYPE_ASSIGNMENT_UPDATE -> CHANNEL_TRIPS    // Accept/decline update
            TYPE_TRIP_STATUS_UPDATE -> CHANNEL_TRIPS   // Phase 5: Trip status change
            TYPE_DRIVER_ACCEPTED -> CHANNEL_TRIPS      // Phase 5: Driver accepted trip
            TYPE_TRUCKS_CONFIRMED -> CHANNEL_TRIPS     // Phase 5: Trucks confirmed for customer
            TYPE_BOOKING_COMPLETED -> CHANNEL_TRIPS    // Phase 5: All deliveries complete
            TYPE_DRIVER_OFFLINE -> CHANNEL_TRIPS       // Phase 5: Driver may be offline
            TYPE_BOOKING_CANCELLED -> CHANNEL_BROADCASTS
            TYPE_BOOKING_EXPIRED -> CHANNEL_BROADCASTS
            TYPE_PAYMENT -> CHANNEL_PAYMENTS
            else -> CHANNEL_GENERAL
        }
        
        // Intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // Pass notification data to activity
            putExtra("notification_type", notification.type)
            notification.broadcastId?.let { putExtra("broadcast_id", it) }
            notification.tripId?.let { putExtra("trip_id", it) }
            notification.assignmentId?.let { putExtra("assignment_id", it) }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Build notification
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(
                when (notification.type) {
                    TYPE_NEW_BROADCAST, TYPE_TRIP_ASSIGNED, TYPE_DRIVER_TIMEOUT -> 
                        NotificationCompat.PRIORITY_HIGH
                    else -> 
                        NotificationCompat.PRIORITY_DEFAULT
                }
            )
        
        // Add big text style for longer messages
        if (notification.body.length > 40) {
            notificationBuilder.setStyle(
                NotificationCompat.BigTextStyle().bigText(notification.body)
            )
        }
        
        // Add action buttons for broadcasts
        if (notification.type == TYPE_NEW_BROADCAST && notification.broadcastId != null) {
            val viewIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("notification_type", TYPE_NEW_BROADCAST)
                putExtra("broadcast_id", notification.broadcastId)
                putExtra("action", "view")
            }
            val viewPendingIntent = PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt() + 1,
                viewIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            notificationBuilder.addAction(0, "View Details", viewPendingIntent)
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Use unique ID for each notification
        val notificationId = (notification.broadcastId ?: notification.tripId ?: System.currentTimeMillis().toString()).hashCode()
        
        notificationManager.notify(notificationId, notificationBuilder.build())
        
        timber.log.Timber.d("📬 Notification shown: ${notification.title}")
    }
    
}

/**
 * FCM Notification data class
 */
data class FCMNotification(
    val type: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val broadcastId: String? = null,
    val tripId: String? = null,
    val assignmentId: String? = null,
    val amount: Double? = null
)
