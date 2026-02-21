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
    
    // =========================================================================
    // COLD START PROTECTION — Driver starts OFFLINE on fresh app launch
    // =========================================================================
    // When the app is killed/force-closed, the DB `isAvailable` stays `true`
    // because there's no process-death handler calling goOffline().
    // Redis TTL (35s) expires → driver is invisible to transporters, BUT the
    // DB still says `isAvailable = true`.
    //
    // On next app launch, loadDashboardData() used to read this stale `true`,
    // call setOnlineLocally(true) → auto-start heartbeat → driver appears
    // online WITHOUT pressing the toggle. This is the "always on" bug.
    //
    // FIX: On the FIRST load of a fresh session (cold start), we:
    //   1. Force the backend to goOffline() (reset stale DB state)
    //   2. Display isOnline = false in the UI
    //   3. Do NOT call setOnlineLocally(true) — no heartbeat auto-start
    //
    // After the user explicitly toggles ON in this session, subsequent
    // loadDashboardData() calls sync normally with backend state.
    //
    // This matches Rapido/Ola/Uber behavior: driver always starts offline
    // on fresh app launch and must explicitly go online.
    // =========================================================================
    private var hasUserToggledThisSession = false

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
    fun loadDashboardData(driverId: String = ""): Job {
        // Cancel any pending grace timer
        loadingGraceJob?.cancel()
        
        return viewModelScope.launch {
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
                // =============================================================
                // REAL API CALL — GET /api/v1/driver/dashboard
                // =============================================================
                // Fetches: earnings, performance, active trip, recent trips
                //
                // SCALABILITY: Single API call returns everything the dashboard needs.
                //   Backend aggregates from DB + Redis. Cached 60s on backend.
                // MODULARITY: ViewModel doesn't know about Retrofit — uses RetrofitClient.
                // EASY UNDERSTANDING: API response → DashboardData mapping is clear.
                // =============================================================
                val driverApi = com.weelo.logistics.data.remote.RetrofitClient.driverApi
                val dashboardResponse = driverApi.getDriverDashboard()
                val activeResponse = driverApi.getActiveTrip()
                val earningsMonthResponse = driverApi.getDriverEarnings("month")
                val earningsWeekResponse = driverApi.getDriverEarnings("week")

                // Only use body if response was successful
                val apiDashboard = if (dashboardResponse.isSuccessful) dashboardResponse.body()?.data else {
                    timber.log.Timber.w("⚠️ Dashboard API failed: ${dashboardResponse.code()}")
                    null
                }
                val apiActiveTrip = if (activeResponse.isSuccessful) activeResponse.body()?.data?.trip else {
                    timber.log.Timber.w("⚠️ Active trip API failed: ${activeResponse.code()}")
                    null
                }
                val apiEarnings = if (earningsMonthResponse.isSuccessful) earningsMonthResponse.body()?.data else {
                    timber.log.Timber.w("⚠️ Monthly earnings API failed: ${earningsMonthResponse.code()}")
                    null
                }
                val apiWeeklyEarnings = if (earningsWeekResponse.isSuccessful) earningsWeekResponse.body()?.data else {
                    timber.log.Timber.w("⚠️ Weekly earnings API failed: ${earningsWeekResponse.code()}")
                    null
                }
                
                // =============================================================
                // REAL PERFORMANCE DATA — GET /api/v1/driver/performance
                // Phase 5: No more hardcoded 0s — all from real assignments
                // =============================================================
                val performanceResponse = try {
                    driverApi.getDriverPerformance()
                } catch (e: Exception) {
                    timber.log.Timber.w("⚠️ Performance API failed, using defaults: ${e.message}")
                    null
                }
                val apiPerformance = performanceResponse?.body()?.data

                // =============================================================
                // REAL ONLINE STATUS — GET /api/v1/driver/availability
                // Fetches DB isAvailable state from backend.
                //
                // CRITICAL: If API fails, we must NOT default to false!
                // That would snap the toggle to OFFLINE mid-session and stop
                // heartbeat — driver becomes invisible to transporters.
                //
                // FALLBACK ORDER on API failure:
                //   1. Use cached isOnline (preserves current session state)
                //   2. Use SocketIOService._isOnlineLocally (heartbeat truth)
                //   3. Last resort: false (only on very first load)
                // =============================================================
                val availabilityResponse = try {
                    driverApi.getAvailability()
                } catch (e: Exception) {
                    timber.log.Timber.w("⚠️ Availability API failed: ${e.message}")
                    null
                }
                
                val apiIsOnline = availabilityResponse?.body()?.data?.isOnline
                val availabilityApiFailed = (apiIsOnline == null)
                
                // Determine fallback: current cached state → local heartbeat state → false
                val currentLocalState = cachedDashboardData?.isOnline
                    ?: com.weelo.logistics.data.remote.SocketIOService.isOnlineLocally()
                
                val backendIsOnline = apiIsOnline ?: currentLocalState
                
                if (availabilityApiFailed) {
                    timber.log.Timber.w("⚠️ Availability API failed/null — preserving current state: $currentLocalState")
                }
                
                // =============================================================
                // COLD START PROTECTION + HEARTBEAT SYNC
                // =============================================================
                // CASE 1: Cold start (first load, user hasn't toggled yet)
                //   → FORCE OFFLINE regardless of backend state
                //   → Do NOT call backend API (would set cooldown, block toggle)
                //   → Driver must explicitly press toggle to go online
                //   → Matches Rapido/Ola/Uber behavior
                //
                // CASE 2: Active toggle in progress (_isToggling = true)
                //   → PRESERVE optimistic state — toggle's API call is truth
                //   → Prevents race where refresh overwrites toggle
                //
                // CASE 3: Normal refresh (user has toggled this session)
                //   → Sync with backend IF API succeeded
                //   → If API failed, preserve current state (don't snap back)
                // =============================================================
                val effectiveIsOnline: Boolean
                
                if (!hasUserToggledThisSession) {
                    // COLD START — Force offline, no API call, no heartbeat
                    effectiveIsOnline = false
                    com.weelo.logistics.data.remote.SocketIOService.setOnlineLocally(false)
                    
                    if (backendIsOnline) {
                        timber.log.Timber.i("🔄 Cold start: stale DB isAvailable=true detected (Redis TTL handles visibility)")
                    }
                } else if (_isToggling.value) {
                    // ACTIVE TOGGLE — preserve optimistic state
                    effectiveIsOnline = cachedDashboardData?.isOnline ?: backendIsOnline
                } else {
                    // NORMAL REFRESH — sync with backend only if API succeeded
                    if (!availabilityApiFailed) {
                        effectiveIsOnline = backendIsOnline
                        com.weelo.logistics.data.remote.SocketIOService.setOnlineLocally(backendIsOnline)
                    } else {
                        // API failed — preserve current local state to prevent snap-back
                        effectiveIsOnline = currentLocalState
                        timber.log.Timber.w("⚠️ Keeping current online state ($currentLocalState) — API unavailable")
                    }
                }

                val dashboardData = DashboardData(
                    driverId = driverId.ifEmpty { com.weelo.logistics.data.remote.RetrofitClient.getUserId() ?: "" },
                    earnings = EarningsSummary(
                        today = apiDashboard?.stats?.todayEarnings ?: 0.0,
                        todayTrips = apiDashboard?.stats?.todayTrips ?: 0,
                        weekly = apiDashboard?.stats?.weekEarnings ?: 0.0,
                        weeklyTrips = apiWeeklyEarnings?.totalTrips ?: 0,
                        monthly = apiDashboard?.stats?.monthEarnings ?: 0.0,
                        monthlyTrips = apiEarnings?.totalTrips ?: 0,
                        pendingPayment = 0.0 // Backend will add this field
                    ),
                    performance = PerformanceMetrics(
                        // Performance API is primary source; dashboard stats is fallback
                        // Both now return real data from DB — no more hardcoded 4.5
                        rating = apiPerformance?.rating ?: apiDashboard?.stats?.rating?.toDouble() ?: 0.0,
                        totalRatings = apiPerformance?.totalRatings ?: apiDashboard?.stats?.totalRatings ?: 0,
                        acceptanceRate = apiPerformance?.acceptanceRate ?: apiDashboard?.stats?.acceptanceRate?.toDouble() ?: 0.0,
                        onTimeDeliveryRate = apiPerformance?.onTimeDeliveryRate ?: apiDashboard?.stats?.onTimeDeliveryRate?.toDouble() ?: 0.0,
                        completionRate = apiPerformance?.completionRate ?: 0.0,
                        totalTrips = apiPerformance?.totalTrips ?: apiDashboard?.stats?.totalTrips ?: 0,
                        totalDistance = apiPerformance?.totalDistance ?: apiDashboard?.stats?.totalDistance?.toDouble() ?: 0.0
                    ),
                    activeTrip = apiActiveTrip?.let { trip: com.weelo.logistics.data.api.TripData ->
                        val startTime = parseIsoTimestamp(trip.startedAt) ?: System.currentTimeMillis()
                        val estimatedDuration = if (trip.distanceKm > 0) {
                            // Estimate: distance / 30 km/h average speed, in minutes
                            ((trip.distanceKm / 30.0) * 60).toInt()
                        } else 0
                        ActiveTrip(
                            tripId = trip.id,
                            customerName = trip.customerName ?: "Customer",
                            pickupAddress = trip.pickup.address,
                            dropAddress = trip.drop.address,
                            vehicleType = trip.vehicleType ?: "",
                            estimatedEarning = trip.fare,
                            startTime = startTime,
                            estimatedDistance = trip.distanceKm,
                            estimatedDuration = estimatedDuration,
                            currentStatus = when (trip.status.lowercase()) {
                                "heading_to_pickup", "driver_accepted" -> TripProgressStatus.EN_ROUTE_TO_PICKUP
                                "at_pickup", "loading_complete" -> TripProgressStatus.AT_PICKUP
                                "in_transit" -> TripProgressStatus.IN_TRANSIT
                                "arrived_at_drop" -> TripProgressStatus.AT_DROP
                                "completed" -> TripProgressStatus.COMPLETED
                                else -> TripProgressStatus.EN_ROUTE_TO_PICKUP
                            }
                        )
                    },
                    recentTrips = apiDashboard?.recentTrips?.map { trip: com.weelo.logistics.data.api.TripData ->
                        val completedAt = parseIsoTimestamp(trip.completedAt) ?: System.currentTimeMillis()
                        val startAt = parseIsoTimestamp(trip.startedAt)
                        val duration = if (startAt != null) {
                            ((completedAt - startAt) / 60_000).toInt().coerceAtLeast(0)
                        } else 0
                        CompletedTrip(
                            tripId = trip.id,
                            customerName = trip.customerName ?: "Customer",
                            pickupAddress = trip.pickup.address,
                            dropAddress = trip.drop.address,
                            vehicleType = trip.vehicleType ?: "",
                            earnings = trip.fare,
                            distance = trip.distanceKm,
                            duration = duration,
                            completedAt = completedAt,
                            rating = null
                        )
                    } ?: emptyList(),
                    notifications = emptyList(),
                    // effectiveIsOnline already handles all 3 cases:
                    // cold start (forced false), active toggle (preserved), normal sync
                    isOnline = effectiveIsOnline,
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
    fun refresh(driverId: String = "") {
        viewModelScope.launch {
            _isRefreshing.value = true
            // PERFORMANCE: Removed delay(1000) - was causing 1s perceived lag
            // Await the load job so _isRefreshing stays true until data is actually loaded
            loadDashboardData(driverId).join()
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
    /**
     * Toggle driver online/offline status.
     *
     * Calls PUT /api/v1/driver/availability with new status.
     * Updates UI optimistically (instant toggle), reverts on API failure.
     *
     * SCALABILITY: Single API call, O(1) Redis update on backend.
     * MODULARITY: ViewModel handles optimistic update + rollback.
     */
    // =========================================================================
    // TOGGLE SPAM PROTECTION — Frontend Guard
    // =========================================================================
    // Prevents concurrent toggle calls + exposes toggling state to UI
    // for cooldown visual indicator (2s disabled switch).
    //
    // DEFENSE IN DEPTH: Backend also enforces 5s cooldown + 10/5min window.
    // This frontend guard is for UX smoothness, not security.
    // =========================================================================
    private val _isToggling = MutableStateFlow(false)
    val isToggling: StateFlow<Boolean> = _isToggling.asStateFlow()
    
    private val TOGGLE_COOLDOWN_MS = 2000L // 2-second UI cooldown

    @Suppress("UNUSED_PARAMETER")
    fun toggleOnlineStatus(driverId: String = "") {
        // Guard: prevent concurrent toggle calls (double-tap protection)
        if (_isToggling.value) {
            timber.log.Timber.w("⚠️ Toggle already in progress — ignoring")
            return
        }
        
        viewModelScope.launch {
            val currentState = _dashboardState.value
            if (currentState is DriverDashboardState.Success) {
                _isToggling.value = true
                val newIsOnline = !currentState.data.isOnline

                try {
                    // Optimistic update — instant UI toggle
                    val updatedData = currentState.data.copy(
                        isOnline = newIsOnline,
                        lastUpdated = System.currentTimeMillis()
                    )
                    _dashboardState.value = DriverDashboardState.Success(updatedData)
                    cachedDashboardData = updatedData

                    // Start heartbeat IMMEDIATELY — don't wait for API response.
                    // This ensures Redis presence key is refreshed before any
                    // dashboard reload can check it. If API fails, we stop it below.
                    com.weelo.logistics.data.remote.SocketIOService.setOnlineLocally(newIsOnline)

                    // Real API call
                    try {
                        val driverApi = com.weelo.logistics.data.remote.RetrofitClient.driverApi
                        val response = driverApi.updateAvailability(
                            com.weelo.logistics.data.api.UpdateAvailabilityRequest(isOnline = newIsOnline)
                        )
                        if (response.isSuccessful) {
                            // Mark session toggled ONLY after backend confirms — prevents
                            // cold-start safeguard from being disabled on failed toggles
                            hasUserToggledThisSession = true
                            timber.log.Timber.i("✅ Driver is now ${if (newIsOnline) "ONLINE" else "OFFLINE"} (API confirmed)")
                        } else if (response.code() == 429) {
                            // Rate limited — goOnline/goOffline was NOT called on backend
                            // Must revert UI + heartbeat to match actual backend state
                            timber.log.Timber.w("⚠️ Toggle rate limited (429) — reverting")
                            com.weelo.logistics.data.remote.SocketIOService.setOnlineLocally(!newIsOnline)
                            val revertedData = updatedData.copy(isOnline = !newIsOnline)
                            _dashboardState.value = DriverDashboardState.Success(revertedData)
                            cachedDashboardData = revertedData
                        } else if (response.code() == 409) {
                            // Lock conflict — another toggle in progress, backend state uncertain
                            // Revert to be safe — next dashboard refresh will sync correct state
                            timber.log.Timber.w("⚠️ Toggle conflict (409) — reverting")
                            com.weelo.logistics.data.remote.SocketIOService.setOnlineLocally(!newIsOnline)
                            val revertedData = updatedData.copy(isOnline = !newIsOnline)
                            _dashboardState.value = DriverDashboardState.Success(revertedData)
                            cachedDashboardData = revertedData
                        } else {
                            timber.log.Timber.w("⚠️ Availability update failed: ${response.code()}")
                            // Revert heartbeat + UI on failure
                            com.weelo.logistics.data.remote.SocketIOService.setOnlineLocally(!newIsOnline)
                            val revertedData = updatedData.copy(isOnline = !newIsOnline)
                            _dashboardState.value = DriverDashboardState.Success(revertedData)
                            cachedDashboardData = revertedData
                        }
                    } catch (e: Exception) {
                        timber.log.Timber.e(e, "❌ Availability API failed")
                        // Revert heartbeat + UI on error
                        com.weelo.logistics.data.remote.SocketIOService.setOnlineLocally(!newIsOnline)
                        val revertedData = updatedData.copy(isOnline = !newIsOnline)
                        _dashboardState.value = DriverDashboardState.Success(revertedData)
                        cachedDashboardData = revertedData
                    }
                    
                    // 2-second cooldown — prevents rapid re-toggle
                    // isToggling stays true, UI shows disabled switch
                    delay(TOGGLE_COOLDOWN_MS)
                } finally {
                    // CRITICAL: Always reset _isToggling, even if coroutine is cancelled
                    // during the 2s delay. Without this, the toggle button gets permanently
                    // locked if the composable recomposes or the Activity is destroyed.
                    _isToggling.value = false
                }
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

    /**
     * Parse ISO 8601 timestamp string → epoch millis.
     * Returns null on parse failure so callers can use fallback values.
     * Uses SimpleDateFormat for minSdk 24 compatibility (no java.time.Instant).
     */
    private fun parseIsoTimestamp(timestamp: String?): Long? {
        if (timestamp.isNullOrBlank()) return null
        return try {
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
            )
            for (format in formats) {
                try {
                    val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    return sdf.parse(timestamp)?.time
                } catch (_: Exception) {}
            }
            null
        } catch (e: Exception) {
            timber.log.Timber.w("⚠️ Failed to parse timestamp: $timestamp")
            null
        }
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
