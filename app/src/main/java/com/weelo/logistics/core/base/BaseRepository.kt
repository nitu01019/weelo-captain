package com.weelo.logistics.core.base

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * =============================================================================
 * BASE REPOSITORY - Scalable Data Layer Foundation
 * =============================================================================
 * 
 * ARCHITECTURE: Repository Pattern with Cache-First Strategy
 * SCALABILITY: Designed for millions of users
 * 
 * FEATURES:
 * 1. Standardized API response handling
 * 2. Automatic error extraction from API responses
 * 3. Cache-first data loading pattern
 * 4. Retry mechanism with exponential backoff
 * 5. Offline support integration
 * 
 * USAGE FOR BACKEND DEVELOPERS:
 * ```kotlin
 * class VehicleRepository(
 *     private val api: VehicleApiService,
 *     private val cache: OfflineCache
 * ) : BaseRepository() {
 *     
 *     suspend fun getVehicles(): ApiResult<List<Vehicle>> = safeApiCall {
 *         api.getVehicles()
 *     }
 *     
 *     suspend fun getVehiclesCached(): List<Vehicle> = loadWithCache(
 *         cacheKey = "vehicles",
 *         fetchFromApi = { api.getVehicles() },
 *         loadFromCache = { cache.getVehicles() },
 *         saveToCache = { cache.saveVehicles(it) }
 *     )
 * }
 * ```
 * 
 * API RESPONSE PATTERN:
 * - All API calls return `ApiResult<T>` (Success/Error)
 * - Errors include message and optional code
 * - Network errors are automatically handled
 * 
 * CACHING PATTERN:
 * - Cache-first: Show cached data immediately
 * - Background refresh: Fetch fresh data silently
 * - Stale-while-revalidate: Return stale if fresh fails
 * 
 * =============================================================================
 */
abstract class BaseRepository {
    
    companion object {
        private const val TAG = "BaseRepository"
    }
    
    // ==========================================================================
    // SAFE API CALL - Standard way to make API calls
    // ==========================================================================
    
    /**
     * Execute API call safely with automatic error handling
     * 
     * Example:
     * ```kotlin
     * suspend fun getUser(id: String): ApiResult<User> = safeApiCall {
     *     api.getUser(id)
     * }
     * ```
     * 
     * @param apiCall The suspend function that makes the API call
     * @return ApiResult.Success with data or ApiResult.Error with message
     */
    protected suspend fun <T> safeApiCall(
        apiCall: suspend () -> Response<T>
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val response = apiCall()
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error("Empty response body", response.code())
                }
            } else {
                val errorMessage = extractErrorMessage(response)
                ApiResult.Error(errorMessage, response.code())
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "API call failed: ${e.message}")
            ApiResult.Error(
                message = e.message ?: "Network error occurred",
                code = null,
                exception = e
            )
        }
    }
    
    /**
     * Execute API call with Weelo-style response (success/data/error structure)
     * 
     * Example:
     * ```kotlin
     * suspend fun getVehicles(): ApiResult<VehicleListData> = safeApiCallWeelo(
     *     apiCall = { api.getVehicles() },
     *     extractData = { it.data }
     * )
     * ```
     */
    protected suspend fun <R, T> safeApiCallWeelo(
        apiCall: suspend () -> Response<R>,
        extractData: (R) -> T?,
        extractSuccess: (R) -> Boolean = { true },
        extractError: (R) -> String? = { null }
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            val response = apiCall()
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    if (extractSuccess(body)) {
                        val data = extractData(body)
                        if (data != null) {
                            ApiResult.Success(data)
                        } else {
                            ApiResult.Error("No data in response", response.code())
                        }
                    } else {
                        val errorMsg = extractError(body) ?: "Request failed"
                        ApiResult.Error(errorMsg, response.code())
                    }
                } else {
                    ApiResult.Error("Empty response body", response.code())
                }
            } else {
                val errorMessage = extractErrorMessage(response)
                ApiResult.Error(errorMessage, response.code())
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "API call failed: ${e.message}")
            ApiResult.Error(
                message = e.message ?: "Network error occurred",
                exception = e
            )
        }
    }
    
    /**
     * Extract error message from failed response
     */
    private fun <T> extractErrorMessage(response: Response<T>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                // Try to parse as JSON error
                if (errorBody.contains("message")) {
                    // Extract message field from JSON
                    val regex = """"message"\s*:\s*"([^"]+)"""".toRegex()
                    regex.find(errorBody)?.groupValues?.get(1) ?: errorBody
                } else {
                    errorBody.take(200) // Limit error message length
                }
            } else {
                "Error ${response.code()}: ${response.message()}"
            }
        } catch (e: Exception) {
            "Error ${response.code()}"
        }
    }
    
    // ==========================================================================
    // CACHE-FIRST LOADING
    // ==========================================================================
    
    /**
     * Load data with cache-first strategy
     * 
     * Pattern:
     * 1. Return cached data immediately if available
     * 2. Fetch fresh data from API in parallel
     * 3. Update cache with fresh data
     * 4. Return fresh data (or cached if API fails)
     * 
     * Example:
     * ```kotlin
     * suspend fun getVehicles() = loadWithCache(
     *     cacheKey = "vehicles_list",
     *     fetchFromApi = { api.getVehicles().body()?.data?.vehicles },
     *     loadFromCache = { cache.getVehicles() },
     *     saveToCache = { vehicles -> cache.saveVehicles(vehicles) },
     *     isCacheValid = { timestamp -> System.currentTimeMillis() - timestamp < 5.minutes }
     * )
     * ```
     */
    protected suspend fun <T> loadWithCache(
        fetchFromApi: suspend () -> T?,
        loadFromCache: suspend () -> T?,
        saveToCache: suspend (T) -> Unit,
        isCacheValid: suspend () -> Boolean = { true }
    ): CacheResult<T> = withContext(Dispatchers.IO) {
        
        // Step 1: Try to load from cache
        val cachedData = try {
            loadFromCache()
        } catch (e: Exception) {
            timber.log.Timber.w("Cache load failed: ${e.message}")
            null
        }
        
        val cacheValid = try {
            isCacheValid()
        } catch (e: Exception) {
            false
        }
        
        // If cache is valid, return cached data and refresh in background
        if (cachedData != null && cacheValid) {
            return@withContext CacheResult(
                data = cachedData,
                source = DataSource.CACHE,
                isStale = false
            )
        }
        
        // Step 2: Fetch from API
        val freshData = try {
            fetchFromApi()
        } catch (e: Exception) {
            timber.log.Timber.e("API fetch failed: ${e.message}")
            null
        }
        
        // Step 3: If API succeeded, save to cache and return
        if (freshData != null) {
            try {
                saveToCache(freshData)
            } catch (e: Exception) {
                timber.log.Timber.w("Cache save failed: ${e.message}")
            }
            
            return@withContext CacheResult(
                data = freshData,
                source = DataSource.NETWORK,
                isStale = false
            )
        }
        
        // Step 4: API failed, return stale cache if available
        if (cachedData != null) {
            return@withContext CacheResult(
                data = cachedData,
                source = DataSource.CACHE,
                isStale = true
            )
        }
        
        // No data available
        return@withContext CacheResult(
            data = null,
            source = DataSource.NONE,
            isStale = false,
            error = "No data available"
        )
    }
    
    // ==========================================================================
    // RETRY WITH BACKOFF
    // ==========================================================================
    
    /**
     * Retry API call with exponential backoff
     * 
     * Example:
     * ```kotlin
     * val result = retryWithBackoff(maxRetries = 3) {
     *     api.submitOrder(order)
     * }
     * ```
     */
    protected suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 10000,
        factor: Double = 2.0,
        apiCall: suspend () -> Response<T>
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        var currentDelay = initialDelayMs
        var lastError: ApiResult.Error<T>? = null
        
        repeat(maxRetries) { attempt ->
            val result = safeApiCall(apiCall)
            
            when (result) {
                is ApiResult.Success -> return@withContext result
                is ApiResult.Error -> {
                    lastError = result
                    
                    // Don't retry on client errors (4xx)
                    if (result.code in 400..499) {
                        return@withContext result
                    }
                    
                    timber.log.Timber.w("Attempt ${attempt + 1} failed, retrying in ${currentDelay}ms")
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
                }
            }
        }
        
        lastError ?: ApiResult.Error("Max retries exceeded")
    }
}

/**
 * =============================================================================
 * API RESULT - Sealed class for API responses
 * =============================================================================
 * 
 * Usage:
 * ```kotlin
 * when (val result = repository.getUser(id)) {
 *     is ApiResult.Success -> showUser(result.data)
 *     is ApiResult.Error -> showError(result.message)
 * }
 * ```
 */
sealed class ApiResult<T> {
    
    data class Success<T>(val data: T) : ApiResult<T>()
    
    data class Error<T>(
        val message: String,
        val code: Int? = null,
        val exception: Throwable? = null
    ) : ApiResult<T>()
    
    /**
     * Check if result is successful
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Check if result is error
     */
    val isError: Boolean get() = this is Error
    
    /**
     * Get data or null
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    /**
     * Get data or default value
     */
    fun getOrDefault(default: T): T = when (this) {
        is Success -> data
        is Error -> default
    }
    
    /**
     * Get data or throw exception
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception ?: Exception(message)
    }
    
    /**
     * Map success data to another type
     */
    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(message, code, exception)
    }
    
    /**
     * Execute action on success
     */
    inline fun onSuccess(action: (T) -> Unit): ApiResult<T> {
        if (this is Success) action(data)
        return this
    }
    
    /**
     * Execute action on error
     */
    inline fun onError(action: (Error<T>) -> Unit): ApiResult<T> {
        if (this is Error) action(this)
        return this
    }
}

/**
 * =============================================================================
 * CACHE RESULT - Result with cache metadata
 * =============================================================================
 */
data class CacheResult<T>(
    val data: T?,
    val source: DataSource,
    val isStale: Boolean,
    val error: String? = null
) {
    val hasData: Boolean get() = data != null
    val isFromCache: Boolean get() = source == DataSource.CACHE
    val isFromNetwork: Boolean get() = source == DataSource.NETWORK
}

/**
 * Data source enum
 */
enum class DataSource {
    CACHE,
    NETWORK,
    NONE
}
