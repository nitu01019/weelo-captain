package com.weelo.logistics.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Debounce and Throttle Utilities
 * 
 * PERFORMANCE: Prevents excessive API calls and improves UX
 * SCALABILITY: Essential for millions of users to reduce server load
 * 
 * Debounce: Waits for user to stop typing before executing
 * Throttle: Limits execution frequency (max once per time period)
 * 
 * Usage Examples:
 * ```
 * // Debounce search input
 * val debouncedSearch = debounce<String>(500L) { query ->
 *     searchAPI(query)
 * }
 * 
 * // Throttle button clicks
 * val throttledClick = throttle(1000L) {
 *     submitForm()
 * }
 * ```
 */

/**
 * Debounce function - Delays execution until user stops action
 * Perfect for: Search inputs, form validation
 */
fun <T> debounce(
    waitMs: Long = 300L,
    coroutineScope: CoroutineScope,
    action: suspend (T) -> Unit
): (T) -> Unit {
    var debounceJob: Job? = null
    
    return { param: T ->
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(waitMs)
            action(param)
        }
    }
}

/**
 * Throttle function - Limits execution frequency
 * Perfect for: Button clicks, location updates, API calls
 */
fun <T> throttle(
    waitMs: Long = 1000L,
    coroutineScope: CoroutineScope,
    action: suspend (T) -> Unit
): (T) -> Unit {
    var job: Job? = null
    var lastRunTime = 0L
    
    return { param: T ->
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastRunTime >= waitMs) {
            lastRunTime = currentTime
            job?.cancel()
            job = coroutineScope.launch {
                action(param)
            }
        }
    }
}

/**
 * Debounce for StateFlow - For search fields
 */
fun <T> StateFlow<T>.debounce(waitMs: Long = 300L): Flow<T> {
    return this.debounce(waitMs)
}

/**
 * Throttle for StateFlow - For continuous data streams
 */
fun <T> StateFlow<T>.throttleFirst(periodMs: Long = 1000L): Flow<T> {
    return flow {
        var lastEmitTime = 0L
        collect { value ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastEmitTime >= periodMs) {
                lastEmitTime = currentTime
                emit(value)
            }
        }
    }
}

/**
 * Click Debouncer - Prevents multiple rapid button clicks
 * Essential for preventing duplicate API calls
 */
class ClickDebouncer(private val delayMs: Long = 500L) {
    private var lastClickTime = 0L
    
    fun canClick(): Boolean {
        val currentTime = System.currentTimeMillis()
        return if (currentTime - lastClickTime >= delayMs) {
            lastClickTime = currentTime
            true
        } else {
            false
        }
    }
    
    fun reset() {
        lastClickTime = 0L
    }
}

/**
 * Search Debouncer - Specialized for search inputs
 * Prevents API calls on every keystroke
 */
class SearchDebouncer<T>(
    private val delayMs: Long = 500L,
    private val scope: CoroutineScope,
    private val onSearch: suspend (T) -> Unit
) {
    private var searchJob: Job? = null
    
    fun search(query: T) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(delayMs)
            onSearch(query)
        }
    }
    
    fun cancel() {
        searchJob?.cancel()
    }
}

/**
 * API Call Throttler - Limits API call frequency
 * Prevents server overload from millions of users
 */
class ApiThrottler(private val minIntervalMs: Long = 1000L) {
    private val lastCallTimes = mutableMapOf<String, Long>()
    
    fun canMakeCall(endpoint: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastCallTime = lastCallTimes[endpoint] ?: 0L
        
        return if (currentTime - lastCallTime >= minIntervalMs) {
            lastCallTimes[endpoint] = currentTime
            true
        } else {
            false
        }
    }
    
    fun getRemainingWaitTime(endpoint: String): Long {
        val currentTime = System.currentTimeMillis()
        val lastCallTime = lastCallTimes[endpoint] ?: return 0L
        val elapsed = currentTime - lastCallTime
        return maxOf(0L, minIntervalMs - elapsed)
    }
    
    fun reset(endpoint: String) {
        lastCallTimes.remove(endpoint)
    }
}

/**
 * Global instances for common use cases
 */
object GlobalThrottlers {
    val buttonClick = ClickDebouncer(500L)
    val apiCall = ApiThrottler(1000L)
    val search = ApiThrottler(500L)
    val locationUpdate = ApiThrottler(5000L)
}

/**
 * Extension function for composable clicks
 * Usage: modifier.clickableDebounced { /* action */ }
 */
fun interface DebouncedClickListener {
    fun onClick()
}

/**
 * BACKEND INTEGRATION NOTES:
 * 
 * 1. Client-Side Throttling (This file)
 *    - Prevents unnecessary API calls
 *    - Improves UX with instant feedback
 *    - Reduces network traffic
 * 
 * 2. Server-Side Rate Limiting (Required)
 *    - Use Redis for distributed rate limiting
 *    - Implement per-user, per-IP limits
 *    - Return 429 (Too Many Requests) with Retry-After header
 * 
 * 3. Recommended Server-Side Limits:
 *    - Search API: 10 requests/minute per user
 *    - Create/Update: 30 requests/minute per user
 *    - Read operations: 100 requests/minute per user
 *    - Authentication: 5 attempts/15 minutes per phone
 * 
 * 4. Scalability for Millions of Users:
 *    - Use CDN for static content
 *    - Implement request queuing
 *    - Use load balancers
 *    - Cache frequently accessed data
 *    - Optimize database queries with indexing
 */
