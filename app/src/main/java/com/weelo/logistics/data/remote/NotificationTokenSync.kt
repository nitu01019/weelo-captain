package com.weelo.logistics.data.remote

import com.weelo.logistics.broadcast.BroadcastStage
import com.weelo.logistics.broadcast.BroadcastStatus
import com.weelo.logistics.broadcast.BroadcastTelemetry
import com.weelo.logistics.data.api.RegisterNotificationTokenRequest
import com.weelo.logistics.data.api.UnregisterNotificationTokenRequest
import java.util.Locale

/**
 * App-side FCM token lifecycle sync with backend notification routes.
 *
 * This keeps backend push routing deterministic even when socket reconnects
 * are delayed or the app process is restarted.
 */
object NotificationTokenSync {

    suspend fun registerCurrentToken(reason: String): Boolean {
        val accessToken = RetrofitClient.getAccessToken()?.takeIf { it.isNotBlank() }
            ?: run {
                recordSkipped(reason, "missing_access_token")
                return false
            }
        val role = RetrofitClient.getUserRole()?.lowercase(Locale.US)
        if (role != "transporter" && role != "driver") {
            recordSkipped(reason, "unsupported_role_${role ?: "unknown"}")
            return false
        }
        val fcmToken = WeeloFirebaseService.fcmToken?.takeIf { it.isNotBlank() }
            ?: run {
                recordSkipped(reason, "missing_fcm_token")
                return false
            }

        return try {
            val response = RetrofitClient.notificationApi.registerToken(
                token = "Bearer $accessToken",
                request = RegisterNotificationTokenRequest(
                    token = fcmToken,
                    deviceType = "android",
                    deviceId = RetrofitClient.getUserId()
                )
            )
            if (response.isSuccessful && response.body()?.success == true) {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.SOCKET_AUTH,
                    status = BroadcastStatus.SUCCESS,
                    reason = "notification_token_registered",
                    attrs = mapOf(
                        "role" to role,
                        "trigger" to reason,
                        "httpCode" to response.code().toString()
                    )
                )
                true
            } else {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.SOCKET_AUTH,
                    status = BroadcastStatus.FAILED,
                    reason = "notification_token_register_failed",
                    attrs = mapOf(
                        "role" to role,
                        "trigger" to reason,
                        "httpCode" to response.code().toString()
                    )
                )
                false
            }
        } catch (t: Throwable) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.SOCKET_AUTH,
                status = BroadcastStatus.FAILED,
                reason = "notification_token_register_exception",
                attrs = mapOf(
                    "role" to role,
                    "trigger" to reason,
                    "message" to (t.message ?: "unknown")
                )
            )
            false
        }
    }

    suspend fun unregisterCurrentToken(reason: String): Boolean {
        val accessToken = RetrofitClient.getAccessToken()?.takeIf { it.isNotBlank() }
            ?: run {
                recordSkipped(reason, "missing_access_token")
                return false
            }
        val role = RetrofitClient.getUserRole()?.lowercase(Locale.US)
        if (role != "transporter" && role != "driver") {
            recordSkipped(reason, "unsupported_role_${role ?: "unknown"}")
            return false
        }
        val fcmToken = WeeloFirebaseService.fcmToken?.takeIf { it.isNotBlank() }
            ?: run {
                recordSkipped(reason, "missing_fcm_token")
                return false
            }

        return try {
            val response = RetrofitClient.notificationApi.unregisterToken(
                token = "Bearer $accessToken",
                request = UnregisterNotificationTokenRequest(token = fcmToken)
            )
            if (response.isSuccessful && response.body()?.success == true) {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.SOCKET_AUTH,
                    status = BroadcastStatus.SUCCESS,
                    reason = "notification_token_unregistered",
                    attrs = mapOf(
                        "role" to role,
                        "trigger" to reason,
                        "httpCode" to response.code().toString()
                    )
                )
                true
            } else {
                BroadcastTelemetry.record(
                    stage = BroadcastStage.SOCKET_AUTH,
                    status = BroadcastStatus.FAILED,
                    reason = "notification_token_unregister_failed",
                    attrs = mapOf(
                        "role" to role,
                        "trigger" to reason,
                        "httpCode" to response.code().toString()
                    )
                )
                false
            }
        } catch (t: Throwable) {
            BroadcastTelemetry.record(
                stage = BroadcastStage.SOCKET_AUTH,
                status = BroadcastStatus.FAILED,
                reason = "notification_token_unregister_exception",
                attrs = mapOf(
                    "role" to role,
                    "trigger" to reason,
                    "message" to (t.message ?: "unknown")
                )
            )
            false
        }
    }

    private fun recordSkipped(reason: String, skipReason: String) {
        BroadcastTelemetry.record(
            stage = BroadcastStage.SOCKET_AUTH,
            status = BroadcastStatus.SKIPPED,
            reason = skipReason,
            attrs = mapOf("trigger" to reason)
        )
    }
}
