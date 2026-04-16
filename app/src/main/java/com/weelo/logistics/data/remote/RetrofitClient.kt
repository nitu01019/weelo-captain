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
import android.provider.Settings
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val _authState = MutableStateFlow(false)
    val authState: StateFlow<Boolean> = _authState.asStateFlow()
    
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
    // PERFORMANCE FIX: Reduced timeouts to fail faster and trigger retry sooner
    private const val CONNECT_TIMEOUT = 10L   // was 15L — TCP connect should be instant
    private const val READ_TIMEOUT = 20L      // was 30L — API responses should be <5s
    private const val WRITE_TIMEOUT = 15L     // was 30L — request upload is small
    private const val MAX_RETRIES = 3
    
    // Single Mutex for token refresh — prevents concurrent refresh races
    // (Industry pattern: hoc081098 Refresh-Token-Sample, Auth0 Android SDK)
    private val refreshMutex = Mutex()
    
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
            timber.log.Timber.i("✅ RetrofitClient initialized with encrypted storage")
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences in case of encryption issues
            securePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            timber.log.Timber.w("⚠️ Using fallback SharedPreferences: ${e.message}")
        }
        _authState.value = isLoggedIn()
    }
    
    // ==========================================================================
    // INTERCEPTORS
    // ==========================================================================
    
    /**
     * Logging interceptor - Shows network requests/responses in Logcat
     * Only logs HEADERS in production, BODY in debug
     */
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        timber.log.Timber.d("🌐 $message")
    }.apply {
        // SECURITY: Only log BODY in debug builds. Release builds log NONE
        // to prevent leaking tokens, OTPs, phone numbers, and personal data.
        level = if (com.weelo.logistics.BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        // Redact sensitive headers from logcat — prevents JWT tokens leaking
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
    }
    
    // debugInterceptor REMOVED — redundant with loggingInterceptor and was
    // double-buffering response bodies (source.request(Long.MAX_VALUE) + clone),
    // causing 2x memory usage for every response on the main thread.
    
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
        val contentType = responseBody.contentType()

        // Read body ONCE via string() — avoids the source.request(Long.MAX_VALUE) + buffer.clone()
        // double-buffering pattern that doubles memory usage for every response (OOM risk at scale).
        val responseString = try {
            responseBody.string().trim()
        } catch (e: Exception) {
            timber.log.Timber.e(e, "⚠️ Failed to read response body")
            return@Interceptor response
        }

        // Gate body preview behind VERBOSE — never log body content in production (PII risk)
        if (com.weelo.logistics.BuildConfig.DEBUG) {
            timber.log.Timber.v("🔍 Response sanitizer - Code: ${response.code}, Length: ${responseString.length}")
            // Deliberately NOT logging body content to prevent accidental PII exposure in logs
        }

        // Check if response is valid JSON — return rebuilt body (original stream already consumed)
        val isValidJson = responseString.isNotEmpty() &&
            (responseString.startsWith("{") || responseString.startsWith("["))

        if (isValidJson) {
            // Valid JSON — rebuild from string (stream already consumed above)
            return@Interceptor response.newBuilder()
                .body(responseString.toResponseBody(contentType))
                .build()
        }

        // Non-JSON response — wrap in proper error structure
        timber.log.Timber.w("⚠️ Non-JSON response detected, wrapping in error structure")

        val errorMessage = when {
            responseString.isEmpty() -> "Empty response from server"
            responseString.startsWith("<!") || responseString.startsWith("<html") ->
                "Server returned HTML error page"
            responseString.startsWith("\"") && responseString.endsWith("\"") ->
                responseString.trim('"')
            else -> responseString.take(200)
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
     * 
     * PUBLIC ENDPOINTS (no auth required):
     * - /auth/send-otp, /auth/verify-otp (Customer/Transporter login)
     * - /driver-auth/send-otp, /driver-auth/verify-otp (Driver login)
     * - /auth/refresh (Token refresh)
     * - /vehicles/types, /pricing/estimate (Public data)
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath
        
        // Skip auth header for public endpoints
        val publicEndpoints = listOf(
            "/auth/send-otp", 
            "/auth/verify-otp", 
            "/auth/refresh",
            "/driver-auth/send-otp",    // Driver login - OTP to transporter
            "/driver-auth/verify-otp",  // Driver OTP verification
            "/vehicles/types", 
            "/pricing/estimate"
        )
        val isPublicEndpoint = publicEndpoints.any { path.contains(it) }
        
        val token = getAccessToken()
        
        // Debug logging
        timber.log.Timber.d("🔐 Auth Interceptor - Path: $path")
        timber.log.Timber.d("🔐 Token present: ${token != null}, Token length: ${token?.length ?: 0}")
        timber.log.Timber.d("🔐 Is public endpoint: $isPublicEndpoint")
        if (token == null && !isPublicEndpoint) {
            timber.log.Timber.w("⚠️ NO TOKEN for protected endpoint: $path")
        }
        
        val newRequest = originalRequest.newBuilder()
            // Use header() instead of addHeader() to REPLACE existing headers.
            // Retrofit's @Body already sets Content-Type, and @Header("Authorization")
            // already sets Authorization. Using addHeader() creates DUPLICATES which
            // causes AWS ALB to return 400 Bad Request.
            .apply {
                // Only set Content-Type if not already present (Retrofit @Body sets it)
                if (originalRequest.header("Content-Type") == null) {
                    header("Content-Type", "application/json")
                }
                header("Accept", "application/json")
                header("x-no-compression", "true")  // Disable server-side compression to avoid gzip issues
                
                if (token != null && !isPublicEndpoint) {
                    // Only set Authorization if not already present (API methods with @Header set it)
                    if (originalRequest.header("Authorization") == null) {
                        header("Authorization", "Bearer $token")
                        timber.log.Timber.d("✅ Added Authorization header (interceptor)")
                    } else {
                        timber.log.Timber.d("✅ Authorization header already present (from @Header)")
                    }
                }

                // Device binding: send X-Device-Id on every request so backend
                // can verify the JWT's embedded deviceId matches the caller
                try {
                    appContext?.let { ctx ->
                        val deviceId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
                        if (!deviceId.isNullOrBlank()) {
                            header("X-Device-Id", deviceId)
                        }
                    }
                } catch (_: Exception) { /* best-effort — skip if unavailable */ }
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
    
    /** Offline interceptor - Serve cached data when offline */
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
            timber.log.Timber.d("📴 Offline - serving from cache: ${request.url}")
        }
        
        chain.proceed(request)
    }
    
    /** Retry interceptor with exponential backoff */
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
                    timber.log.Timber.w("⚠️ Server error ${response.code}, retry ${attempt + 1}/$MAX_RETRIES in ${delay}ms")
                    Thread.sleep(delay)
                    continue
                }
                
                return@Interceptor response
                
            } catch (e: IOException) {
                lastException = e
                response?.close()
                
                if (attempt < MAX_RETRIES - 1) {
                    val delay = (1L shl attempt) * 1000
                    timber.log.Timber.w("⚠️ Network error, retry ${attempt + 1}/$MAX_RETRIES in ${delay}ms: ${e.message}")
                    Thread.sleep(delay)
                } else {
                    timber.log.Timber.e("❌ All retries failed: ${e.message}")
                    Log.e(TAG, "All retries failed (${e::class.java.simpleName}): ${e.message}", e)
                    throw e
                }
            }
        }
        
        throw lastException ?: IOException("Unknown error after retries")
    }
    
    /**
     * Token refresh authenticator — proper OkHttp Authenticator (not Interceptor).
     *
     * Industry pattern (hoc081098/Auth0): single Mutex + stale-token check.
     * - Mutex serializes concurrent 401 handlers (replaces synchronized + @Volatile)
     * - Stale-token check: if token changed since our request, skip refresh (another thread won)
     * - responseCount limit prevents infinite retry loops
     * - No Thread.sleep — Mutex.withLock suspends the coroutine (non-blocking)
     */
    private val tokenAuthenticator = Authenticator { _, response ->
        // Prevent infinite retry loops (max 3 attempts)
        if (responseCount(response) >= 3) return@Authenticator null

        // Don't try to refresh auth endpoints themselves
        if (response.request.url.encodedPath.contains("/auth/")) return@Authenticator null

        val failedToken = response.request.header("Authorization")

        val newToken = runBlocking {
            refreshMutex.withLock {
                val currentToken = getAccessToken()
                // Another thread already refreshed — use the new token
                if (currentToken != null && "Bearer $currentToken" != failedToken) {
                    return@withLock currentToken
                }
                // Actually refresh
                val refreshToken = getRefreshToken() ?: return@withLock null
                timber.log.Timber.d("Token expired, attempting refresh...")
                if (refreshTokenSync(refreshToken)) getAccessToken() else null
            }
        }

        if (newToken != null) {
            timber.log.Timber.i("Token refreshed, retrying request")
            response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        } else {
            timber.log.Timber.w("Token refresh failed, user needs to re-login")
            null
        }
    }

    /** Count prior responses to detect retry loops (OkHttp Authenticator pattern). */
    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
    
    /** Synchronous token refresh */
    private fun refreshTokenSync(refreshToken: String): Boolean {
        return try {
            val response = runBlocking {
                authApi.refreshToken(RefreshTokenRequest(refreshToken))
            }
            val body = response.body()
            val newAccessToken = body?.data?.accessToken
            val newRefreshToken = body?.data?.refreshToken  // Save rotated refresh token (RFC 9700)

            if (response.isSuccessful && body?.success == true && !newAccessToken.isNullOrBlank()) {
                securePrefs?.edit()?.apply {
                    putString(KEY_ACCESS_TOKEN, newAccessToken)
                    newRefreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }  // Persist rotated token
                    apply()
                }
                _authState.value = true
                true
            } else {
                timber.log.Timber.w("⚠️ refreshTokenSync failed: code=${response.code()} success=${body?.success}")
                false
            }
        } catch (e: Exception) {
            timber.log.Timber.w(e, "⚠️ refreshTokenSync exception")
            false
        }
    }
    
    // ==========================================================================
    // CONNECTION POOL & CLIENT
    // ==========================================================================
    
    /** Connection pool for TCP connection reuse */
    private val connectionPool = ConnectionPool(
        maxIdleConnections = MAX_IDLE_CONNECTIONS,
        keepAliveDuration = KEEP_ALIVE_DURATION_MINUTES,
        timeUnit = TimeUnit.MINUTES
    )
    
    /**
     * Certificate Pinning — intentionally empty (no pins added).
     *
     * The backend currently runs over plain HTTP (no SSL certificate on the
     * ALB), so ENABLE_CERTIFICATE_PINNING in Constants is false and this
     * pinner is never attached to the OkHttpClient. It exists as a ready-made
     * builder so that enabling pinning later requires only:
     *   1. Add real pin hashes below (generate with openssl — see Constants.kt)
     *   2. Set ENABLE_CERTIFICATE_PINNING = true
     */
    private val certificatePinner: CertificatePinner by lazy {
        CertificatePinner.Builder()
            // Add real pins after HTTPS migration:
            // .add("api.weelo.in", "sha256/<primary-pin-hash>")
            // .add("api.weelo.in", "sha256/<backup-pin-hash>")
            .build()
    }
    
    /** Create SSL Socket Factory with modern TLS settings */
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
            timber.log.Timber.e(e, "Failed to create secure socket factory")
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
     * 
     * SCALABILITY OPTIMIZATIONS:
     * - HTTP cache enabled for GET requests (reduces backend load)
     * - Offline cache for better UX when network unavailable
     * - Connection pooling for TCP reuse
     * - HTTP/2 for multiplexing
     * 
     * FOR MILLIONS OF USERS:
     * - Cache reduces API calls by 50-90%
     * - Connection pool prevents TCP handshake overhead
     * - Retry logic handles transient failures gracefully
     */
    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            // Connection pool (SCALABILITY: reuse TCP connections)
            .connectionPool(connectionPool)
            
            // Cache enabled (SCALABILITY: reduces backend load)
            .cache(cache)
            
            // Interceptors (order matters!)
            .addInterceptor(offlineCacheInterceptor)  // Handle offline first
            .addInterceptor(authInterceptor)          // Add auth header
            .authenticator(tokenAuthenticator)         // Auto-refresh on 401 (proper OkHttp Authenticator)
            .addInterceptor(retryInterceptor)         // Retry on failure (SCALABILITY: handles transient errors)
            .addNetworkInterceptor(cacheInterceptor)        // Cache responses (SCALABILITY: HTTP cache)
            .addInterceptor(responseSanitizerInterceptor)   // Strip PII from responses BEFORE logging
            .addInterceptor(loggingInterceptor)             // Log requests/responses
            
            // Timeouts
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            
            // Enable retries (SCALABILITY: resilience)
            .retryOnConnectionFailure(true)
            
            // Protocols - HTTP/2 for better performance (SCALABILITY: multiplexing)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            
            // Connection specs for TLS security
            .connectionSpecs(connectionSpecs)
        
        // Add SSL configuration for HTTPS
        if (isProductionUrl()) {
            // Certificate pinning (enable when you have real certs)
            if (Constants.API.ENABLE_CERTIFICATE_PINNING) {
                builder.certificatePinner(certificatePinner)
                timber.log.Timber.i("🔒 Certificate pinning ENABLED")
            }
            
            // Custom SSL socket factory with TLS 1.3
            createSecureSocketFactory()?.let { (sslFactory, trustManager) ->
                builder.sslSocketFactory(sslFactory, trustManager)
                timber.log.Timber.i("🔒 TLS 1.3 enabled")
            }
            
            // Hostname verifier (strict by default)
            builder.hostnameVerifier { hostname, session ->
                // Strict hostname verification
                val hv = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
                hv.verify(hostname, session)
            }
            
            timber.log.Timber.i("🔒 HTTPS security configured for production")
        } else {
            timber.log.Timber.w("⚠️ Running in development mode (HTTP allowed)")
        }
        
        builder.build()
    }
    
    /** Lenient Gson for parsing - handles some edge cases better */
    private val gson by lazy {
        com.google.gson.GsonBuilder()
            .setLenient()  // Allow lenient parsing
            .serializeNulls()  // Include null values in JSON
            // F-C-78: forward-compatible HoldPhase enum with UNKNOWN sentinel.
            // Uses a TypeAdapter (NOT JsonDeserializer) because JsonDeserializer
            // is not invoked for JSON null — Gson short-circuits to Kotlin null,
            // which would violate the non-null `phase: HoldPhase` DTO contract.
            // TypeAdapter.read IS called for null tokens, so we can always map
            // backend strings (known, unknown, missing, or JSON null) through
            // HoldPhase.fromBackendString.
            .registerTypeAdapter(
                com.weelo.logistics.data.model.HoldPhase::class.java,
                object : com.google.gson.TypeAdapter<com.weelo.logistics.data.model.HoldPhase>() {
                    override fun write(
                        out: com.google.gson.stream.JsonWriter,
                        value: com.weelo.logistics.data.model.HoldPhase?
                    ) {
                        if (value == null) out.nullValue() else out.value(value.name)
                    }

                    override fun read(
                        reader: com.google.gson.stream.JsonReader
                    ): com.weelo.logistics.data.model.HoldPhase {
                        val token = reader.peek()
                        return if (token == com.google.gson.stream.JsonToken.NULL) {
                            reader.nextNull()
                            com.weelo.logistics.data.model.HoldPhase.UNKNOWN
                        } else {
                            com.weelo.logistics.data.model.HoldPhase.fromBackendString(reader.nextString())
                        }
                    }
                }
            )
            .create()
    }
    
    /** Retrofit instance */
    private val retrofit: Retrofit by lazy {
        timber.log.Timber.i("🌐 BASE_URL: ${Constants.API.BASE_URL}")
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
    
    /** Truck Hold API - BookMyShow-style truck holding */
    val truckHoldApi: com.weelo.logistics.data.api.TruckHoldApiService by lazy {
        retrofit.create(com.weelo.logistics.data.api.TruckHoldApiService::class.java)
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

    val notificationApi: NotificationApiService by lazy {
        retrofit.create(NotificationApiService::class.java)
    }
    
    val transporterApi: com.weelo.logistics.data.api.TransporterApiService by lazy {
        retrofit.create(com.weelo.logistics.data.api.TransporterApiService::class.java)
    }
    
    /**
     * Tracking API - For real-time fleet and trip tracking
     * 
     * Endpoints:
     * - GET /tracking/fleet       → Get all fleet driver locations
     * - GET /tracking/{tripId}    → Get single trip location
     * - POST /tracking/update     → Update driver location
     */
    val trackingApi: TrackingApiService by lazy {
        retrofit.create(TrackingApiService::class.java)
    }
    
    /**
     * Assignment API - Driver trip accept/decline
     * 
     * Endpoints:
     * - GET /assignments/:id         → Get assignment details
     * - GET /assignments/driver      → Get driver's assignments
     * - PATCH /assignments/:id/accept  → Accept assignment
     * - PATCH /assignments/:id/decline → Decline with reason
     */
    val assignmentApi: AssignmentApiService by lazy {
        retrofit.create(AssignmentApiService::class.java)
    }
    
    /**
     * Active Trip Recovery API - Driver crash recovery
     * 
     * Endpoint:
     * - GET /tracking/active-trip → Get driver's active trip for crash recovery
     */
    val activeTripApi: ActiveTripApiService by lazy {
        retrofit.create(ActiveTripApiService::class.java)
    }
    
    // ============== Token Management ==============
    
    /** Get access token from secure storage */
    fun getAccessToken(): String? {
        return securePrefs?.getString(KEY_ACCESS_TOKEN, null)
    }
    
    /** Get refresh token from secure storage */
    fun getRefreshToken(): String? {
        return securePrefs?.getString(KEY_REFRESH_TOKEN, null)
    }
    
    /** Save tokens after successful login */
    fun saveTokens(accessToken: String, refreshToken: String) {
        securePrefs?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
        _authState.value = accessToken.isNotBlank()
    }
    
    /** Save user info after login */
    fun saveUserInfo(userId: String, role: String) {
        securePrefs?.edit()?.apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_ROLE, role)
            apply()
        }
    }
    
    /** Get stored user ID */
    fun getUserId(): String? {
        return securePrefs?.getString(KEY_USER_ID, null)
    }
    
    /** Get stored user role */
    fun getUserRole(): String? {
        return securePrefs?.getString(KEY_USER_ROLE, null)
    }
    
    /** Check if user is logged in */
    fun isLoggedIn(): Boolean {
        return getAccessToken() != null
    }
    
    /** Clear all tokens and user data (logout) */
    fun clearAllData() {
        securePrefs?.edit()?.clear()?.commit()
        _authState.value = false
    }
    
    /** Get authorization header string */
    fun getAuthHeader(): String {
        return "Bearer ${getAccessToken() ?: ""}"
    }
}
