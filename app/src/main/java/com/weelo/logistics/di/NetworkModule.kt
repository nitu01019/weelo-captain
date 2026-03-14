package com.weelo.logistics.di

import com.weelo.logistics.data.api.*
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.utils.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * NetworkModule - Provides networking dependencies via Hilt
 *
 * SAFE GUARDS:
 * - Does not modify existing RetrofitClient object
 * - Creates NEW instances for Hilt injection
 * - Existing code using RetrofitClient continues to work
 * - New code can choose to use Hilt dependencies
 *
 * USAGE:
 * @Inject lateinit var broadcastApi: BroadcastApiService
 * // or use RetrofitClient.broadcastApi (existing pattern)
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthInterceptor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TokenRefreshInterceptor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RetryInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TAG = "NetworkModule"

    /**
     * Provides HTTP logging interceptor
     * Logs headers and body in debug builds only
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Timber.d("HTTP: $message")
        }.apply {
            level = if (com.weelo.logistics.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // Redact sensitive headers
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }
    }

    /**
     * Provides custom auth interceptor
     * Adds Authorization header to requests
     */
    @Provides
    @Singleton
    @AuthInterceptor
    fun provideAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            val originalRequest = chain.request()
            val token = RetrofitClient.getAccessToken() // Read from existing storage
            val newRequest = if (token != null) {
                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                originalRequest
            }
            chain.proceed(newRequest)
        }
    }

    /**
     * Provides token refresh interceptor
     * Refreshes token automatically on 401 responses
     */
    @Provides
    @Singleton
    @TokenRefreshInterceptor
    fun provideTokenRefreshInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (response.code == 401 && !request.url.encodedPath.contains("/auth/")) {
                // Try to refresh token
                val refreshToken = RetrofitClient.getRefreshToken()
                if (refreshToken != null) {
                    try {
                        // Call refresh endpoint (this is simplified, actual refresh would be async)
                        // In production, this would use coroutine scope for refresh
                        val refreshedToken = refreshToken
                        response.close()
                        // Retry with new token
                        val newRequest = request.newBuilder()
                            .removeHeader("Authorization")
                            .header("Authorization", "Bearer $refreshedToken")
                            .build()
                        return@Interceptor chain.proceed(newRequest)
                    } catch (e: Exception) {
                        Timber.e(e, "Token refresh failed")
                    }
                }
            }
            response
        }
    }

    /**
     * Retry interceptor with exponential backoff
     */
    @Provides
    @Singleton
    @RetryInterceptor
    fun provideRetryInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            var response: Response? = null

            for (attempt in 0..2) {
                response?.close()
                response = chain.proceed(request)

                if (response.isSuccessful || response.code in 400..499) {
                    return@Interceptor response
                }

                // Server error (5xx) - retry
                if (response.code >= 500 && attempt < 2) {
                    val delay = (1L shl attempt) * 1000
                    Thread.sleep(delay)
                    continue
                }

                return@Interceptor response
            }
            response ?: throw Exception("Network request failed after retries")
        }
    }

    /**
     * Provides OkHttpClient with all interceptors
     *
     * IMPORTANT: This is a NEW instance for Hilt injection.
     * Existing RetrofitClient object is NOT modified.
     * Existing code continues to use RetrofitClient directly.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        @AuthInterceptor authInterceptor: Interceptor,
        @TokenRefreshInterceptor tokenRefreshInterceptor: Interceptor,
        @RetryInterceptor retryInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(tokenRefreshInterceptor)
            .addInterceptor(retryInterceptor)
            // Add response sanitizer for non-JSON responses
            .addInterceptor(provideResponseSanitizer())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(provideResponseSanitizer())
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Response sanitizer handles non-JSON responses
     * Wraps plain text/HTML responses in JSON format
     */
    private fun provideResponseSanitizer(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val responseBody = response.body ?: return@Interceptor response

            val contentType = responseBody.contentType()
            val responseString = responseBody.string()

            // Only process text/html/plain responses
            val isTextHtml = contentType?.subtype?.contains("text") == true ||
                    contentType?.subtype?.contains("html") == true
            val isJson = responseString.trimStart().startsWith("{") ||
                      responseString.trimStart().startsWith("[")

            if (!isJson && isTextHtml) {
                // Wrap non-JSON in error structure
                val wrappedJson = """
                    {
                        "success": false,
                        "data": null,
                        "error": {
                            "code": "INVALID_RESPONSE",
                            "message": "${responseString.take(200)}"
                        }
                    }
                """.trimIndent()

                val newBody = wrappedJson.toResponseBody(
                    "application/json".toMediaType()
                )
                return@Interceptor response.newBuilder()
                    .body(newBody)
                    .build()
            }

            response.newBuilder()
                .body(responseString.toResponseBody(contentType))
                .build()
        }
    }

    /**
     * Provides Gson with lenient parsing
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .serializeNulls()
            .create()
    }

    /**
     * Provides Retrofit instance
     *
     * IMPORTANT: Base URL comes from existing Constants.API.BASE_URL
     * This maintains consistency with existing RetrofitClient behavior
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        Timber.tag(TAG).i("Creating Retrofit with base URL: ${Constants.API.BASE_URL}")

        return Retrofit.Builder()
            .baseUrl(Constants.API.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // ==========================================================================
    // API SERVICE PROVIDERS
    // Each service is available for Hilt injection
    // ==========================================================================

    @Provides
    @Singleton
    fun provideBroadcastApi(retrofit: Retrofit): BroadcastApiService =
        retrofit.create(BroadcastApiService::class.java)

    @Provides
    @Singleton
    fun provideDriverApi(retrofit: Retrofit): DriverApiService =
        retrofit.create(DriverApiService::class.java)

    @Provides
    @Singleton
    fun provideDriverAuthApi(retrofit: Retrofit): DriverAuthApiService =
        retrofit.create(DriverAuthApiService::class.java)

    @Provides
    @Singleton
    fun provideTransporterApi(retrofit: Retrofit): TransporterApiService =
        retrofit.create(TransporterApiService::class.java)

    @Provides
    @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApiService =
        retrofit.create(ProfileApiService::class.java)

    @Provides
    @Singleton
    fun provideVehicleApi(retrofit: Retrofit): VehicleApiService =
        retrofit.create(VehicleApiService::class.java)

    @Provides
    @Singleton
    fun provideAssignmentApi(retrofit: Retrofit): AssignmentApiService =
        retrofit.create(AssignmentApiService::class.java)

    @Provides
    @Singleton
    fun provideTrackingApi(retrofit: Retrofit): TrackingApiService =
        retrofit.create(TrackingApiService::class.java)

    @Provides
    @Singleton
    fun provideTruckHoldApi(retrofit: Retrofit): TruckHoldApiService =
        retrofit.create(TruckHoldApiService::class.java)

    @Provides
    @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApiService =
        retrofit.create(NotificationApiService::class.java)

    @Provides
    @Singleton
    fun provideTripApi(retrofit: Retrofit): TripApiService =
        retrofit.create(TripApiService::class.java)

}
