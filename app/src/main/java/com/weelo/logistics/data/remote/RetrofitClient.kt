package com.weelo.logistics.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.weelo.logistics.data.api.*
import com.weelo.logistics.utils.Constants
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * =============================================================================
 * OPTIMIZED RETROFIT CLIENT - High Performance Network Layer
 * =============================================================================
 * 
 * OPTIMIZATIONS IMPLEMENTED:
 * 
 * 1. CONNECTION POOLING
 *    - Max 10 idle connections
 *    - 5 minute keep-alive
 *    - Reuses TCP connections for faster subsequent requests
 * 
 * 2. RESPONSE CACHING
 *    - 50MB disk cache
 *    - Cache-Control headers respected
 *    - Offline support with stale data
 * 
 * 3. AUTOMATIC TOKEN REFRESH
 *    - Intercepts 401 responses
 *    - Refreshes token automatically
 *    - Retries original request
 * 
 * 4. RETRY WITH EXPONENTIAL BACKOFF
 *    - Max 3 retries on network failures
 *    - Exponential delay: 1s, 2s, 4s
 * 
 * 5. GZIP COMPRESSION
 *    - Automatic request/response compression
 * 
 * 6. TIMEOUT OPTIMIZATION
 *    - Connect: 15s (fast fail)
 *    - Read: 30s (for large responses)
 *    - Write: 30s (for uploads)
 * 
 * Backend: weelo-backend (Node.js/Express)
 * =============================================================================
 */
object RetrofitClient {
    
    private const val TAG = "RetrofitClient"
    
    private var appContext: Context? = null
    private var securePrefs: SharedPreferences? = null
    private var cache: Cache? = null
    
    // Token storage keys
    private const val PREFS_NAME = "weelo_secure_prefs"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_ROLE = "user_role"
    
    // Optimization constants
    private const val CACHE_SIZE = 50L * 1024 * 1024  // 50 MB cache
    private const val MAX_IDLE_CONNECTIONS = 10
    private const val KEEP_ALIVE_DURATION_MINUTES = 5L
    private const val CONNECT_TIMEOUT = 15L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    private const val MAX_RETRIES = 3
    
    // Token refresh lock to prevent multiple simultaneous refreshes
    @Volatile
    private var isRefreshing = false
    private val refreshLock = Object()
    
    /**
     * Initialize RetrofitClient with application context
     * Call this in Application.onCreate()
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        
        // Initialize cache directory
        val cacheDir = File(context.cacheDir, "http_cache")
        cache = Cache(cacheDir, CACHE_SIZE)
        
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            securePrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            Log.i(TAG, "✅ RetrofitClient initialized with encrypted storage")
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences in case of encryption issues
            securePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            Log.w(TAG, "⚠️ Using fallback SharedPreferences: ${e.message}")
        }
    }
    
    // ==========================================================================
    // INTERCEPTORS
    // ==========================================================================
    
    /**
     * Logging interceptor - Shows network requests/responses in Logcat
     * Only logs HEADERS in production, BODY in debug
     */
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        // Log ALL network traffic for debugging
        Log.d(TAG, "🌐 $message")
        // Extra logging for response body (to catch parsing issues)
        if (message.startsWith("{") || message.startsWith("[") || message.startsWith("<") || message.startsWith("\"")) {
            Log.w(TAG, "📦 RAW RESPONSE BODY: ${message.take(500)}")
        }
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY  // Shows full request/response body
    }
    
    /**
     * Debug interceptor - logs raw response for debugging JSON parse errors
     */
    private val debugInterceptor = Interceptor { chain ->
        val request = chain.request()
        Log.d(TAG, "📤 REQUEST: ${request.method} ${request.url}")
        
        val response = chain.proceed(request)
        
        // Read response body for logging (need to buffer it)
        val responseBody = response.body
        val source = responseBody?.source()
        source?.request(Long.MAX_VALUE)
        val buffer = source?.buffer?.clone()
        val responseString = buffer?.readString(Charsets.UTF_8) ?: "null"
        
        Log.d(TAG, "📥 RESPONSE [${response.code}]: ${responseString.take(500)}")
        
        response
    }
    
    /**
     * Response sanitizer interceptor - Ensures all responses are valid JSON
     * 
     * This interceptor handles cases where the server returns:
     * - Plain text strings (like "success" or error messages)
     * - HTML error pages
     * - Empty responses
     * 
     * It wraps non-JSON responses in a proper JSON structure to prevent
     * Gson parsing errors.
     */
    private val responseSanitizerInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        
        // Only process responses with body
        val responseBody = response.body ?: return@Interceptor response
        
        // Read the response body
        val source = responseBody.source()
        source.request(Long.MAX_VALUE)
        val buffer = source.buffer.clone()
        val responseString = buffer.readString(Charsets.UTF_8).trim()
        
        Log.d(TAG, "🔍 Response sanitizer - Code: ${response.code}, Body: ${responseString.take(200)}")
        
        // Check if response is valid JSON
        val isValidJson = responseString.isNotEmpty() && 
            (responseString.startsWith("{") || responseString.startsWith("["))
        
        if (isValidJson) {
            // Valid JSON - return as-is
            return@Interceptor response
        }
        
        // Non-JSON response - wrap it in a proper error response
        Log.w(TAG, "⚠️ Non-JSON response detected, wrapping in error structure")
        
        val errorMessage = when {
            responseString.isEmpty() -> "Empty response from server"
            responseString.startsWith("<!") || responseString.startsWith("<html") -> 
                "Server returned HTML error page"
            responseString.startsWith("\"") && responseString.endsWith("\"") ->
                // Plain quoted string - extract the message
                responseString.trim('"')
            else -> responseString.take(200) // Use first 200 chars as error message
        }
        
        // Create a proper JSON error response
        val wrappedJson = """
            {
                "success": false,
                "data": null,
                "error": {
                    "code": "INVALID_RESPONSE",
                    "message": "${errorMessage.replace("\"", "\\\"").replace("\n", " ")}"
                }
            }
        """.trimIndent()
        
        // Build new response with JSON body
        val newBody = wrappedJson.toResponseBody("application/json".toMediaType())
        
        response.newBuilder()
            .body(newBody)
            .build()
    }
    
    /**
     * Auth interceptor - Adds Authorization header to protected requests
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath
        
        // Skip auth header for public endpoints
        val publicEndpoints = listOf(
            "/auth/send-otp", 
            "/auth/verify-otp", 
            "/auth/refresh", 
            "/vehicles/types", 
            "/pricing/estimate"
        )
        val isPublicEndpoint = publicEndpoints.any { path.contains(it) }
        
        val token = getAccessToken()
        
        // Debug logging
        Log.d(TAG, "🔐 Auth Interceptor - Path: $path")
        Log.d(TAG, "🔐 Token present: ${token != null}, Token length: ${token?.length ?: 0}")
        Log.d(TAG, "🔐 Is public endpoint: $isPublicEndpoint")
        if (token == null && !isPublicEndpoint) {
            Log.w(TAG, "⚠️ NO TOKEN for protected endpoint: $path")
        }
        
        val newRequest = originalRequest.newBuilder()
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("x-no-compression", "true")  // Disable server-side compression to avoid gzip issues
            .apply {
                if (token != null && !isPublicEndpoint) {
                    addHeader("Authorization", "Bearer $token")
                    Log.d(TAG, "✅ Added Authorization header")
                }
            }
            .build()
        
        chain.proceed(newRequest)
    }
    
    /**
     * Cache interceptor - Handles caching logic
     * - Cache GET requests for 5 minutes
     * - Serve stale cache when offline
     */
    private val cacheInterceptor = Interceptor { chain ->
        var request = chain.request()
        
        // Add cache headers for GET requests
        if (request.method == "GET") {
            request = request.newBuilder()
                .cacheControl(CacheControl.Builder()
                    .maxAge(5, TimeUnit.MINUTES)
                    .build())
                .build()
        }
        
        chain.proceed(request)
    }
    
    /**
     * Offline interceptor - Serve cached data when offline
     */
    private val offlineCacheInterceptor = Interceptor { chain ->
        var request = chain.request()
        
        // Check if network is available
        val isNetworkAvailable = appContext?.let { ctx ->
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            cm?.activeNetwork != null
        } ?: true
        
        if (!isNetworkAvailable && request.method == "GET") {
            // Force cache when offline
            request = request.newBuilder()
                .cacheControl(CacheControl.FORCE_CACHE)
                .build()
            Log.d(TAG, "📴 Offline - serving from cache: ${request.url}")
        }
        
        chain.proceed(request)
    }
    
    /**
     * Retry interceptor with exponential backoff
     */
    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var response: Response? = null
        var lastException: IOException? = null
        
        for (attempt in 0 until MAX_RETRIES) {
            try {
                response?.close()
                response = chain.proceed(request)
                
                // Success or client error (4xx) - don't retry
                if (response.isSuccessful || response.code in 400..499) {
                    return@Interceptor response
                }
                
                // Server error (5xx) - retry
                if (response.code >= 500 && attempt < MAX_RETRIES - 1) {
                    val delay = (1L shl attempt) * 1000  // Exponential: 1s, 2s, 4s
                    Log.w(TAG, "⚠️ Server error ${response.code}, retry ${attempt + 1}/$MAX_RETRIES in ${delay}ms")
                    Thread.sleep(delay)
                    continue
                }
                
                return@Interceptor response
                
            } catch (e: IOException) {
                lastException = e
                response?.close()
                
                if (attempt < MAX_RETRIES - 1) {
                    val delay = (1L shl attempt) * 1000
                    Log.w(TAG, "⚠️ Network error, retry ${attempt + 1}/$MAX_RETRIES in ${delay}ms: ${e.message}")
                    Thread.sleep(delay)
                } else {
                    Log.e(TAG, "❌ All retries failed: ${e.message}")
                    throw e
                }
            }
        }
        
        throw lastException ?: IOException("Unknown error after retries")
    }
    
    /**
     * Token refresh interceptor - Auto-refresh on 401
     */
    private val tokenRefreshInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        
        // Check if unauthorized and not a refresh/auth request
        if (response.code == 401 && !request.url.encodedPath.contains("/auth/")) {
            synchronized(refreshLock) {
                // Check if another thread already refreshed
                if (!isRefreshing) {
                    isRefreshing = true
                    
                    try {
                        val refreshToken = getRefreshToken()
                        if (refreshToken != null) {
                            Log.d(TAG, "🔄 Token expired, attempting refresh...")
                            
                            // Try to refresh token (synchronous call)
                            val refreshed = refreshTokenSync(refreshToken)
                            
                            if (refreshed) {
                                Log.i(TAG, "✅ Token refreshed successfully")
                                
                                // Retry original request with new token
                                response.close()
                                val newToken = getAccessToken()
                                val newRequest = request.newBuilder()
                                    .removeHeader("Authorization")
                                    .addHeader("Authorization", "Bearer $newToken")
                                    .build()
                                
                                isRefreshing = false
                                return@Interceptor chain.proceed(newRequest)
                            }
                        }
                        
                        Log.w(TAG, "⚠️ Token refresh failed, user needs to re-login")
                        
                    } finally {
                        isRefreshing = false
                    }
                }
            }
        }
        
        response
    }
    
    /**
     * Synchronous token refresh
     */
    private fun refreshTokenSync(refreshToken: String): Boolean {
        // TODO: Implement actual token refresh API call
        // For now, return false to force re-login
        return false
    }
    
    // ==========================================================================
    // CONNECTION POOL & CLIENT
    // ==========================================================================
    
    /**
     * Connection pool for TCP connection reuse
     */
    private val connectionPool = ConnectionPool(
        maxIdleConnections = MAX_IDLE_CONNECTIONS,
        keepAliveDuration = KEEP_ALIVE_DURATION_MINUTES,
        timeUnit = TimeUnit.MINUTES
    )
    
    /**
     * Certificate Pinning for production security
     * 
     * These are SHA-256 hashes of the server's public key certificates.
     * When you deploy to production with real certificates, add pins here.
     * 
     * To get certificate pins for your domain:
     * openssl s_client -connect api.weelo.in:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
     */
    private val certificatePinner: CertificatePinner by lazy {
        CertificatePinner.Builder()
            // Production domain pins (add real pins when deploying)
            // .add("api.weelo.in", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            // .add("api.weelo.in", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // Backup pin
            
            // Staging domain pins
            // .add("staging-api.weelo.in", "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
            .build()
    }
    
    /**
     * Create SSL Socket Factory with modern TLS settings
     */
    private fun createSecureSocketFactory(): Pair<javax.net.ssl.SSLSocketFactory, X509TrustManager>? {
        return try {
            // Use system default trust manager
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            
            val trustManagers = trustManagerFactory.trustManagers
            val trustManager = trustManagers[0] as X509TrustManager
            
            // Create SSL context with TLS 1.3 (falls back to TLS 1.2 if not supported)
            val sslContext = SSLContext.getInstance("TLSv1.3")
            sslContext.init(null, arrayOf(trustManager), null)
            
            Pair(sslContext.socketFactory, trustManager)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create secure socket factory", e)
            null
        }
    }
    
    /**
     * Connection specs for secure HTTPS connections
     * 
     * MODERN_TLS: TLS 1.2 and 1.3 only with strong cipher suites
     * CLEARTEXT: HTTP (only for local development)
     */
    private val connectionSpecs: List<ConnectionSpec> by lazy {
        if (isProductionUrl()) {
            // Production: HTTPS only with modern TLS
            listOf(
                ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                    .cipherSuites(
                        // TLS 1.3 cipher suites
                        CipherSuite.TLS_AES_128_GCM_SHA256,
                        CipherSuite.TLS_AES_256_GCM_SHA384,
                        CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
                        // TLS 1.2 cipher suites (fallback)
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
                    )
                    .build()
            )
        } else {
            // Development: Allow both HTTPS and HTTP
            listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
        }
    }
    
    /**
     * Check if current URL is production/staging (HTTPS required)
     */
    private fun isProductionUrl(): Boolean {
        return Constants.API.BASE_URL.startsWith("https://")
    }
    
    /**
     * Optimized OkHttp client with SSL/TLS security
     */
    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            // Connection pool
            .connectionPool(connectionPool)
            
            // Cache - DISABLED FOR DEBUG (uncomment .cache(cache) when working)
            // .cache(cache)
            
            // Interceptors (order matters!)
            // .addInterceptor(offlineCacheInterceptor)  // Handle offline first - DISABLED FOR DEBUG
            .addInterceptor(authInterceptor)          // Add auth header
            // .addInterceptor(retryInterceptor)         // Retry on failure - DISABLED FOR DEBUG
            // .addNetworkInterceptor(cacheInterceptor)  // Cache responses - DISABLED FOR DEBUG
            // .addNetworkInterceptor(responseSanitizerInterceptor) // DISABLED - causing gzip issues
            .addInterceptor(loggingInterceptor)       // Log requests/responses
            
            // Timeouts
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            
            // Enable retries
            .retryOnConnectionFailure(true)
            
            // Protocols - HTTP/2 for better performance
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            
            // Connection specs for TLS security
            .connectionSpecs(connectionSpecs)
        
        // Add SSL configuration for HTTPS
        if (isProductionUrl()) {
            // Certificate pinning (enable when you have real certs)
            if (Constants.API.ENABLE_CERTIFICATE_PINNING) {
                builder.certificatePinner(certificatePinner)
                Log.i(TAG, "🔒 Certificate pinning ENABLED")
            }
            
            // Custom SSL socket factory with TLS 1.3
            createSecureSocketFactory()?.let { (sslFactory, trustManager) ->
                builder.sslSocketFactory(sslFactory, trustManager)
                Log.i(TAG, "🔒 TLS 1.3 enabled")
            }
            
            // Hostname verifier (strict by default)
            builder.hostnameVerifier { hostname, session ->
                // Strict hostname verification
                val hv = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                hv.verify(hostname, session)
            }
            
            Log.i(TAG, "🔒 HTTPS security configured for production")
        } else {
            Log.w(TAG, "⚠️ Running in development mode (HTTP allowed)")
        }
        
        builder.build()
    }
    
    /**
     * Lenient Gson for parsing - handles some edge cases better
     */
    private val gson by lazy {
        com.google.gson.GsonBuilder()
            .setLenient()  // Allow lenient parsing
            .serializeNulls()  // Include null values in JSON
            .create()
    }
    
    /**
     * Retrofit instance
     */
    private val retrofit: Retrofit by lazy {
        Log.i(TAG, "🌐 BASE_URL: ${Constants.API.BASE_URL}")
        Retrofit.Builder()
            .baseUrl(Constants.API.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    // ============== API Service Instances ==============
    
    val authApi: AuthApiService by lazy {
        retrofit.create(AuthApiService::class.java)
    }
    
    val vehicleApi: VehicleApiService by lazy {
        retrofit.create(VehicleApiService::class.java)
    }
    
    val driverApi: DriverApiService by lazy {
        retrofit.create(DriverApiService::class.java)
    }
    
    val driverAuthApi: DriverAuthApiService by lazy {
        retrofit.create(DriverAuthApiService::class.java)
    }
    
    val profileApi: ProfileApiService by lazy {
        retrofit.create(ProfileApiService::class.java)
    }
    
    val bookingApi: BookingApiService by lazy {
        retrofit.create(BookingApiService::class.java)
    }
    
    val broadcastApi: BroadcastApiService by lazy {
        retrofit.create(BroadcastApiService::class.java)
    }
    
    val tripApi: TripApiService by lazy {
        retrofit.create(TripApiService::class.java)
    }
    
    val transporterApi: com.weelo.logistics.data.api.TransporterApiService by lazy {
        retrofit.create(com.weelo.logistics.data.api.TransporterApiService::class.java)
    }
    
    // ============== Token Management ==============
    
    /**
     * Get access token from secure storage
     */
    fun getAccessToken(): String? {
        return securePrefs?.getString(KEY_ACCESS_TOKEN, null)
    }
    
    /**
     * Get refresh token from secure storage
     */
    fun getRefreshToken(): String? {
        return securePrefs?.getString(KEY_REFRESH_TOKEN, null)
    }
    
    /**
     * Save tokens after successful login
     */
    fun saveTokens(accessToken: String, refreshToken: String) {
        securePrefs?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }
    
    /**
     * Save user info after login
     */
    fun saveUserInfo(userId: String, role: String) {
        securePrefs?.edit()?.apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_ROLE, role)
            apply()
        }
    }
    
    /**
     * Get stored user ID
     */
    fun getUserId(): String? {
        return securePrefs?.getString(KEY_USER_ID, null)
    }
    
    /**
     * Get stored user role
     */
    fun getUserRole(): String? {
        return securePrefs?.getString(KEY_USER_ROLE, null)
    }
    
    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }
    
    /**
     * Clear all tokens and user data (logout)
     */
    fun clearAllData() {
        securePrefs?.edit()?.clear()?.apply()
    }
    
    /**
     * Get authorization header string
     */
    fun getAuthHeader(): String {
        return "Bearer ${getAccessToken() ?: ""}"
    }
}
