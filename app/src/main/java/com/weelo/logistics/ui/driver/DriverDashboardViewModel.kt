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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * DriverDashboardViewModel - Manages driver dashboard state and operations
 * 
 * Responsibilities:
 * - Load and refresh dashboard data
 * - Handle driver status changes (online/offline)
 * - Manage notifications
 * - Calculate real-time earnings
 * - Simulate active trip updates
 * 
 * Backend Integration:
 * - Replace mock functions with repository API calls
 * - Keep state management structure as-is
 */
class DriverDashboardViewModel : ViewModel() {

    // State
    private val _dashboardState = MutableStateFlow<DriverDashboardState>(DriverDashboardState.Loading)
    val dashboardState: StateFlow<DriverDashboardState> = _dashboardState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // Don't auto-load - wait for backend connection
        // Backend will trigger loadDashboardData() when connected
        // No auto-refresh - use WebSocket/FCM for real-time updates
    }

    /**
     * Load dashboard data from backend
     * 
     * BACKEND INTEGRATION POINT:
     * Call this function when:
     * 1. Screen opens
     * 2. User pulls to refresh
     * 3. WebSocket/FCM notification received
     * 
     * Replace the body with: repository.getDashboardData(driverId)
     */
    fun loadDashboardData(driverId: String = "d1") {
        viewModelScope.launch {
            try {
                _dashboardState.value = DriverDashboardState.Loading
                
                // TODO: Backend API Call
                // val response = repository.getDashboardData(driverId)
                // _dashboardState.value = DriverDashboardState.Success(response)
                
                // FOR NOW: Show empty state (no mock data)
                _dashboardState.value = DriverDashboardState.Success(
                    DashboardData(
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
                )
            } catch (e: Exception) {
                _dashboardState.value = DriverDashboardState.Error(
                    e.message ?: "Failed to load dashboard. Check your connection."
                )
            }
        }
    }

    /**
     * Refresh dashboard data (pull-to-refresh)
     */
    fun refresh(driverId: String = "d1") {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(1000)
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
        val now = System.currentTimeMillis()
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
 */
sealed class DriverDashboardState {
    object Loading : DriverDashboardState()
    data class Success(val data: DashboardData) : DriverDashboardState()
    data class Error(val message: String) : DriverDashboardState()
}
