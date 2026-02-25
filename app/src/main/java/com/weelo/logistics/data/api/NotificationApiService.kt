package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Notification token registration endpoints.
 *
 * Backend routes:
 * - POST /notifications/register-token
 * - DELETE /notifications/unregister-token
 */
interface NotificationApiService {

    @POST("notifications/register-token")
    suspend fun registerToken(
        @Header("Authorization") token: String,
        @Body request: RegisterNotificationTokenRequest
    ): Response<NotificationTokenResponse>

    @HTTP(method = "DELETE", path = "notifications/unregister-token", hasBody = true)
    suspend fun unregisterToken(
        @Header("Authorization") token: String,
        @Body request: UnregisterNotificationTokenRequest
    ): Response<NotificationTokenResponse>
}

data class RegisterNotificationTokenRequest(
    val token: String,
    val deviceType: String = "android",
    val deviceId: String? = null
)

data class UnregisterNotificationTokenRequest(
    val token: String
)

data class NotificationTokenResponse(
    val success: Boolean,
    val data: NotificationTokenData? = null,
    val error: ApiErrorInfo? = null
)

data class NotificationTokenData(
    val message: String? = null,
    val userId: String? = null,
    val role: String? = null
)
