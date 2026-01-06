package com.weelo.logistics.data.remote

import com.weelo.logistics.data.api.*
import com.weelo.logistics.utils.Constants
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit Client - Network configuration
 * 
 * BACKEND INTEGRATION NOTES:
 * ==========================
 * 
 * 1. BASE URL CONFIGURATION:
 *    - Update Constants.BASE_URL with your actual backend URL
 *    - Must match the URL used in Weelo app (both apps same backend)
 *    - Example: https://api.weelo.in/v1/
 * 
 * 2. AUTHENTICATION:
 *    - Access token stored securely using EncryptedSharedPreferences
 *    - Token added to all requests via AuthInterceptor
 *    - Auto-refresh token when expired (401 response)
 * 
 * 3. ERROR HANDLING:
 *    - Network errors caught and handled gracefully
 *    - Show user-friendly error messages
 *    - Retry logic for transient failures
 * 
 * 4. SECURITY:
 *    - Use HTTPS only (enforced by OkHttp)
 *    - Certificate pinning for production (optional)
 *    - No sensitive data in logs (disable in production)
 * 
 * 5. PERFORMANCE:
 *    - Connection pooling enabled
 *    - Response caching for GET requests
 *    - Request timeout: 30 seconds
 * 
 * HOW TO USE:
 * ===========
 * val authApi = RetrofitClient.authApiService
 * val response = authApi.sendOTP(SendOTPRequest(mobileNumber = "9876543210"))
 * 
 * if (response.isSuccessful) {
 *     val otpResponse = response.body()
 *     // Handle success
 * } else {
 *     // Handle error
 *     val errorBody = response.errorBody()?.string()
 * }
 */
object RetrofitClient {
    
    // Logging interceptor - Shows network requests/responses in Logcat
    // TODO: Disable in production for security
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    // Auth interceptor - Adds Authorization header to all requests
    // TODO: Implement token management
    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        
        // Get token from secure storage
        // TODO: Implement token retrieval from EncryptedSharedPreferences
        val token = getAccessToken()
        
        // Add Authorization header if token exists
        val newRequest = if (token != null && !request.url.encodedPath.contains("/auth/")) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
        } else {
            request.newBuilder()
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()
        }
        
        val response = chain.proceed(newRequest)
        
        // Handle token expiration (401)
        if (response.code == 401 && token != null) {
            // TODO: Implement token refresh logic
            // 1. Call refreshToken API
            // 2. Save new token
            // 3. Retry original request
        }
        
        response
    }
    
    // OkHttp client configuration
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    // Retrofit instance
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(com.weelo.logistics.utils.Constants.API.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    // API Service instances
    val authApiService: AuthApiService by lazy {
        retrofit.create(AuthApiService::class.java)
    }
    
    val broadcastApiService: BroadcastApiService by lazy {
        retrofit.create(BroadcastApiService::class.java)
    }
    
    val driverApiService: DriverApiService by lazy {
        retrofit.create(DriverApiService::class.java)
    }
    
    val tripApiService: TripApiService by lazy {
        retrofit.create(TripApiService::class.java)
    }
    
    /**
     * Get access token from secure storage
     * TODO: Implement actual token retrieval
     */
    private fun getAccessToken(): String? {
        // TODO: Get from EncryptedSharedPreferences or DataStore
        // Example:
        // return securePreferences.getString("access_token", null)
        return null
    }
    
    /**
     * Save access token to secure storage
     * TODO: Implement actual token storage
     */
    fun saveAccessToken(token: String) {
        // TODO: Save to EncryptedSharedPreferences
        // Example:
        // securePreferences.edit().putString("access_token", token).apply()
    }
    
    /**
     * Clear all tokens (logout)
     * TODO: Implement token clearing
     */
    fun clearTokens() {
        // TODO: Clear from secure storage
        // Example:
        // securePreferences.edit().clear().apply()
    }
}
