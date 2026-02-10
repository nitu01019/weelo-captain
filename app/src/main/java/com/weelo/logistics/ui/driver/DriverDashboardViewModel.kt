package com.weelo.logistics.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.data.model.ActiveTrip
import com.weelo.logistics.data.model.CompletedTrip
import com.weelo.logistics.data.model.DashboardData
import com.weelo.logistics.data.model.DriverNotification
import com.weelo.logistics.data.model.EarningsSummary
import com.weelo.logistics.data.model.DashNotificationType
import com.weelo.logistics.data.model.PerformanceMetrics
import com.weelo.logistics.data.model.TripProgressStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * DriverDashboardViewModel - Manages driver dashboard state and operations
 * 
 * RAPIDO-STYLE CACHE-FIRST PATTERN:
 * - Keeps last known data in memory
 * - Only shows Loading on very first load (cache empty)
 * - Never clears data - always shows cached while refreshing
 * - Back navigation shows cached data instantly (0ms)
 * 
 * Backend Integration:
 * - Replace mock functions with repository API calls
 * - Keep state management structure as-is
 */
class DriverDashboardViewModel : ViewModel() {

    // State — starts as Idle (not Loading) to prevent skeleton flash on first frame.
    // Loading is only set after 150ms grace period expires in loadDashboardData().
    private val _dashboardState = MutableStateFlow<DriverDashboardState>(DriverDashboardState.Idle)
    val dashboardState: StateFlow<DriverDashboardState> = _dashboardState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    
    // =========================================================================
    // INITIAL LOAD TRACKING
    // =========================================================================
    // true  → first-ever load (cache empty) → show skeleton
    // false → subsequent visits / cache exists → show content instantly
    //
    // SCALABILITY: O(1) boolean check, no allocation.
    // MODULARITY: ViewModel owns this state; UI simply observes.
    // =========================================================================
    private val _isInitialLoad = MutableStateFlow(true)
    val isInitialLoad: StateFlow<Boolean> = _isInitialLoad.asStateFlow()
    
    // =========================================================================
    // RAPIDO-STYLE: Keep last known data for instant display on back navigation
    // =========================================================================
    private var cachedDashboardData: DashboardData? = null
    private var lastFetchTime: Long = 0
    private val STALE_TIME_MS = 60_000L // 60 seconds

    init {
        // Don't auto-load - wait for screen to trigger
        // If we have cached data, show it instantly
        cachedDashboardData?.let {
            _dashboardState.value = DriverDashboardState.Success(it)
            _isInitialLoad.value = false
        }
    }

    // =========================================================================
    // SMART LOADING — 150ms Grace Period
    // =========================================================================
    // PROBLEM: If data resolves instantly (<150ms, e.g., mock/cache), showing
    // a skeleton for 1-2 frames then fading it out looks like a "flash/flicker".
    //
    // SOLUTION (Industry Standard — used by Instagram, Uber, Rapido):
    // 1. Start fetching data immediately
    // 2. Start a parallel 150ms timer
    // 3. If data arrives BEFORE timer → emit Success directly (no skeleton)
    // 4. If timer fires BEFORE data → show Loading (skeleton)
    //
    // RESULT: Fast responses never show skeleton. Slow responses (>150ms)
    // get a polished skeleton loading experience.
    //
    // SCALABILITY: Works on all devices — grace period adapts naturally.
    // MODULARITY: Pure ViewModel logic — UI just observes state.
    // =========================================================================
    private var loadingGraceJob: Job? = null
    
    companion object {
        private const val TAG = "DriverDashboardVM"
        private const val LOADING_GRACE_MS = 150L // Show skeleton only after this
    }
    
    /**
     * Load dashboard data - SMART LOADING + CACHE-FIRST
     * 
     * - Cache exists + fresh → show instantly (0ms, no skeleton)
     * - Cache exists + stale → show cache, refresh in background
     * - No cache → 150ms grace → skeleton if still loading
     */
    fun loadDashboardData(driverId: String = "d1") {
        // Cancel any pending grace timer
        loadingGraceJob?.cancel()
        
        viewModelScope.launch {
            // =========================================================================
            // STEP 1: Check cache first (INSTANT - 0ms)
            // =========================================================================
            cachedDashboardData?.let { cached ->
                timber.log.Timber.d("📦 Showing cached dashboard data (instant)")
                _dashboardState.value = DriverDashboardState.Success(cached)
                
                // Skip fetch if data is fresh
                if (System.currentTimeMillis() - lastFetchTime < STALE_TIME_MS) {
                    timber.log.Timber.d("📦 Cache is fresh, skipping API call")
                    return@launch
                }
            }
            
            // =========================================================================
            // STEP 2: Smart Loading — only show skeleton after 150ms grace period
            // =========================================================================
            // If no cache, start a grace timer. If data arrives within 150ms,
            // the user never sees a skeleton — it goes straight to content.
            // =========================================================================
            if (cachedDashboardData == null && _dashboardState.value !is DriverDashboardState.Loading) {
                loadingGraceJob = viewModelScope.launch {
                    delay(LOADING_GRACE_MS)
                    // Grace period expired, data still not here → show skeleton
                    val current = _dashboardState.value
                    if (current !is DriverDashboardState.Success) {
                        _dashboardState.value = DriverDashboardState.Loading
                        timber.log.Timber.d("⏳ Grace period expired → showing skeleton")
                    }
                }
            }
            
            try {
                // TODO: Backend API Call
                // val response = repository.getDashboardData(driverId)
                // cachedDashboardData = response
                // lastFetchTime = System.currentTimeMillis()
                // _dashboardState.value = DriverDashboardState.Success(response)
                
                // FOR NOW: Show empty state (no mock data)
                val dashboardData = DashboardData(
                    driverId = driverId,
                    earnings = EarningsSummary(
                        today = 0.0,
                        todayTrips = 0,
                        weekly = 0.0,
                        weeklyTrips = 0,
                        monthly = 0.0,
                        monthlyTrips = 0,
                        pendingPayment = 0.0
                    ),
                    performance = PerformanceMetrics(
                        rating = 0.0,
                        totalRatings = 0,
                        acceptanceRate = 0.0,
                        onTimeDeliveryRate = 0.0,
                        completionRate = 0.0,
                        totalTrips = 0,
                        totalDistance = 0.0
                    ),
                    activeTrip = null,
                    recentTrips = emptyList(),
                    notifications = emptyList(),
                    isOnline = false,
                    lastUpdated = System.currentTimeMillis()
                )
                
                // Cancel grace timer — data arrived before skeleton was needed
                loadingGraceJob?.cancel()
                
                // Cache the data for future instant display
                cachedDashboardData = dashboardData
                lastFetchTime = System.currentTimeMillis()
                _dashboardState.value = DriverDashboardState.Success(dashboardData)
                _isInitialLoad.value = false
                
                timber.log.Timber.d("✅ Dashboard data loaded (skeleton skipped: ${loadingGraceJob?.isCancelled})")
            } catch (e: Exception) {
                // Cancel grace timer on error too
                loadingGraceJob?.cancel()
                
                // RAPIDO-STYLE: If we have cached data, keep showing it on error
                if (cachedDashboardData != null) {
                    timber.log.Timber.w("⚠️ API failed, showing cached data")
                } else {
                    _dashboardState.value = DriverDashboardState.Error(
                        e.message ?: "Failed to load dashboard. Check your connection."
                    )
                }
            }
        }
    }

    /**
     * Refresh dashboard data (pull-to-refresh)
     * 
     * PERFORMANCE: Removed artificial delay(1000) for instant refresh
     */
    fun refresh(driverId: String = "d1") {
        viewModelScope.launch {
            _isRefreshing.value = true
            // PERFORMANCE: Removed delay(1000) - was causing 1s perceived lag
            loadDashboardData(driverId)
            _isRefreshing.value = false
        }
    }

    /**
     * Toggle driver online/offline status
     * 
     * BACKEND TODO: Replace with actual API call
     * API: POST /api/v1/driver/status
     * Body: { "driverId": "string", "status": "ONLINE|OFFLINE" }
     */
    @Suppress("UNUSED_PARAMETER")
    fun toggleOnlineStatus(driverId: String = "d1") {
        viewModelScope.launch {
            val currentState = _dashboardState.value
            if (currentState is DriverDashboardState.Success) {
                val updatedData = currentState.data.copy(
                    isOnline = !currentState.data.isOnline,
                    lastUpdated = System.currentTimeMillis()
                )
                _dashboardState.value = DriverDashboardState.Success(updatedData)
                
                // BACKEND TODO: Call API here
                // repository.updateDriverStatus(driverId, if (updatedData.isOnline) "ONLINE" else "OFFLINE")
            }
        }
    }

    /**
     * Mark notification as read
     * 
     * BACKEND TODO: Replace with actual API call
     * API: PUT /api/v1/driver/notifications/{notificationId}/read
     */
    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            val currentState = _dashboardState.value
            if (currentState is DriverDashboardState.Success) {
                val updatedNotifications = currentState.data.notifications.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                }
                val updatedData = currentState.data.copy(notifications = updatedNotifications)
                _dashboardState.value = DriverDashboardState.Success(updatedData)
                
                // BACKEND TODO: Call API here
                // repository.markNotificationAsRead(notificationId)
            }
        }
    }

    /**
     * Real-time update handler (AJAX-style)
     * 
     * Called by WebSocket/FCM when new data arrives from backend
     * Updates state WITHOUT page refresh - pure reactive update
     * 
     * BACKEND TODO: 
     * 1. Set up WebSocket connection for real-time updates
     * 2. Set up FCM for push notifications
     * 3. Call this function when new data arrives
     * 
     * Example WebSocket usage:
     * ```
     * webSocket.onMessage { message ->
     *     val newData = json.decodeFromString<DashboardData>(message)
     *     updateDashboardData(newData)
     * }
     * ```
     */
    fun updateDashboardData(newData: DashboardData) {
        viewModelScope.launch {
            // Real-time update - no loading state, instant UI update
            _dashboardState.value = DriverDashboardState.Success(newData)
        }
    }
    
    /**
     * Update specific trip data (when transporter books)
     * Recalculates earnings automatically
     */
    fun onNewTripReceived(trip: CompletedTrip) {
        viewModelScope.launch {
            val currentState = _dashboardState.value
            if (currentState is DriverDashboardState.Success) {
                val updatedTrips = listOf(trip) + currentState.data.recentTrips
                val updatedEarnings = calculateEarnings(updatedTrips)
                val updatedPerformance = calculatePerformance(updatedTrips)
                
                val updatedData = currentState.data.copy(
                    recentTrips = updatedTrips,
                    earnings = updatedEarnings,
                    performance = updatedPerformance,
                    lastUpdated = System.currentTimeMillis()
                )
                
                _dashboardState.value = DriverDashboardState.Success(updatedData)
            }
        }
    }
    
    /**
     * Update active trip status in real-time
     */
    fun onActiveTripUpdated(activeTrip: ActiveTrip?) {
        viewModelScope.launch {
            val currentState = _dashboardState.value
            if (currentState is DriverDashboardState.Success) {
                val updatedData = currentState.data.copy(
                    activeTrip = activeTrip,
                    lastUpdated = System.currentTimeMillis()
                )
                _dashboardState.value = DriverDashboardState.Success(updatedData)
            }
        }
    }

    // =============================================================================
    // DYNAMIC CALCULATION LOGIC - Calculates based on real trip data
    // =============================================================================
    
    /**
     * Calculate earnings from trip list
     * Called automatically when new trip arrives
     */
    private fun calculateEarnings(trips: List<CompletedTrip>): EarningsSummary {
        val todayStart = getTodayStartTimestamp()
        val weekStart = getWeekStartTimestamp()
        val monthStart = getMonthStartTimestamp()
        
        // Filter trips by time period
        val todayTrips = trips.filter { it.completedAt >= todayStart }
        val weekTrips = trips.filter { it.completedAt >= weekStart }
        val monthTrips = trips.filter { it.completedAt >= monthStart }
        
        return EarningsSummary(
            today = todayTrips.sumOf { it.earnings },
            todayTrips = todayTrips.size,
            weekly = weekTrips.sumOf { it.earnings },
            weeklyTrips = weekTrips.size,
            monthly = monthTrips.sumOf { it.earnings },
            monthlyTrips = monthTrips.size,
            pendingPayment = 0.0 // Backend will provide this
        )
    }
    
    /**
     * Calculate performance metrics from trip list
     */
    private fun calculatePerformance(trips: List<CompletedTrip>): PerformanceMetrics {
        if (trips.isEmpty()) {
            return PerformanceMetrics(
                rating = 0.0,
                totalRatings = 0,
                acceptanceRate = 0.0,
                onTimeDeliveryRate = 0.0,
                completionRate = 0.0,
                totalTrips = 0,
                totalDistance = 0.0
            )
        }
        
        val ratedTrips = trips.filter { it.rating != null }
        val averageRating = if (ratedTrips.isNotEmpty()) {
            ratedTrips.sumOf { it.rating ?: 0.0 } / ratedTrips.size
        } else {
            0.0
        }
        
        return PerformanceMetrics(
            rating = averageRating,
            totalRatings = ratedTrips.size,
            acceptanceRate = 0.0, // Backend calculates (accepted / total requests)
            onTimeDeliveryRate = 0.0, // Backend calculates
            completionRate = 0.0, // Backend calculates
            totalTrips = trips.size,
            totalDistance = trips.sumOf { it.distance }
        )
    }
    
    // Helper functions for time calculations
    private fun getTodayStartTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun getWeekStartTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun getMonthStartTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

/**
 * Driver Dashboard UI State
 * 
 * STATE MACHINE:
 *   Idle → Loading (after 150ms grace) → Success
 *   Idle → Success (if data arrives within 150ms grace — no skeleton shown)
 *   Success → Success (data update, no animation)
 *   Loading/Idle → Error (on failure with no cache)
 *   Error → Loading → Success (on retry)
 *
 * INDUSTRY STANDARD: Idle state prevents first-frame skeleton flash.
 * Loading is only shown if data fetch genuinely takes > 150ms.
 */
sealed class DriverDashboardState {
    /** Initial state — before any data fetch. Shows skeleton in dashboard. */
    object Idle : DriverDashboardState()
    /** Data is loading and took > 150ms — show skeleton shimmer. */
    object Loading : DriverDashboardState()
    /** Data loaded successfully — show dashboard content. */
    data class Success(val data: DashboardData) : DriverDashboardState()
    /** Data fetch failed with no cache available — show error + retry. */
    data class Error(val message: String) : DriverDashboardState()
}
