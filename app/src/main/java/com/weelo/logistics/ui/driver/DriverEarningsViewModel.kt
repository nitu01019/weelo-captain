package com.weelo.logistics.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.data.api.EarningsBreakdown
import com.weelo.logistics.data.api.EarningsResponseData
import com.weelo.logistics.data.api.TripData
import com.weelo.logistics.data.remote.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * =============================================================================
 * DRIVER EARNINGS VIEWMODEL — Real API Data for Earnings Screen
 * =============================================================================
 *
 * Replaces hardcoded values in DriverEarningsScreen with real data from:
 *   - GET /api/v1/driver/earnings?period=today|week|month
 *   - GET /api/v1/driver/trips?status=completed (trip-wise breakdown)
 *
 * STATE MANAGEMENT:
 *   - earningsState: Loading → Success (with data) or Error
 *   - selectedPeriod: today | week | month (triggers re-fetch)
 *   - Cached per period — switching back is instant
 *
 * SCALABILITY:
 *   - Backend aggregates earnings from DB with indexed queries
 *   - Response is cached in Redis (5min TTL) on backend
 *   - Client caches per period — no redundant API calls
 *
 * MODULARITY:
 *   - ViewModel is standalone — no dependency on Dashboard
 *   - EarningsScreen just observes StateFlow — no API knowledge
 *   - Easy to test with fake data
 *
 * EASY UNDERSTANDING:
 *   - One function: loadEarnings(period) — does everything
 *   - Clear state machine: Loading → Success | Error
 *   - Data classes match backend response exactly
 *
 * =============================================================================
 */
class DriverEarningsViewModel : ViewModel() {

    companion object {
        private const val TAG = "DriverEarningsVM"
    }

    // =========================================================================
    // STATE
    // =========================================================================

    private val _earningsState = MutableStateFlow<EarningsState>(EarningsState.Loading)
    val earningsState: StateFlow<EarningsState> = _earningsState.asStateFlow()

    private val _selectedPeriod = MutableStateFlow("Month")
    val selectedPeriod: StateFlow<String> = _selectedPeriod.asStateFlow()

    /** Cache per period — switching tabs doesn't re-fetch */
    private val earningsCache = mutableMapOf<String, EarningsData>()
    private var loadJob: Job? = null

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Load earnings for a period. Checks cache first.
     *
     * @param period "Today", "Week", or "Month" (UI labels)
     */
    fun loadEarnings(period: String) {
        loadJob?.cancel()
        _selectedPeriod.value = period

        // Check cache
        earningsCache[period]?.let { cached ->
            _earningsState.value = EarningsState.Success(cached)
            Timber.d("$TAG: Cache HIT for $period")
            return
        }

        // Fetch from API
        val requestedPeriod = period
        loadJob = viewModelScope.launch {
            _earningsState.value = EarningsState.Loading

            try {
                val driverApi = RetrofitClient.driverApi
                val apiPeriod = when (requestedPeriod) {
                    "Today" -> "today"
                    "Week" -> "week"
                    else -> "month"
                }

                // Fetch earnings summary + completed trips in parallel
                val earningsResponse = driverApi.getDriverEarnings(apiPeriod)
                val tripsResponse = driverApi.getDriverTrips(status = "completed", limit = 20)

                if (_selectedPeriod.value != requestedPeriod) return@launch

                if (!earningsResponse.isSuccessful) {
                    _earningsState.value = EarningsState.Error("API error ${earningsResponse.code()}")
                    Timber.w("$TAG: Earnings API error ${earningsResponse.code()} for $requestedPeriod")
                    return@launch
                }
                if (earningsResponse.body()?.success != true) {
                    _earningsState.value = EarningsState.Error("Failed to load earnings")
                    Timber.w("$TAG: success=false for $requestedPeriod")
                    return@launch
                }

                val earningsData = earningsResponse.body()?.data
                val tripsData = if (tripsResponse.isSuccessful && tripsResponse.body()?.success == true)
                    tripsResponse.body()?.data else null

                if (earningsData != null) {
                    val result = EarningsData(
                        totalEarnings = earningsData.totalEarnings,
                        tripCount = earningsData.totalTrips,
                        avgPerTrip = earningsData.averagePerTrip,
                        pendingAmount = 0.0, // Backend can add this field later
                        trips = tripsData?.trips?.map { trip: com.weelo.logistics.data.api.TripData ->
                            EarningItem(
                                tripId = trip.id.take(12).uppercase(),
                                route = "${trip.pickup.address.take(20)} → ${trip.drop.address.take(20)}",
                                date = trip.completedAt ?: trip.createdAt ?: "Unknown",
                                // fare is Double from API — convert safely to Int (avoid crash on NaN/Inf)
                                amount = trip.fare.takeIf { it.isFinite() }?.toInt() ?: 0,
                                status = if (trip.status == "completed") "Paid" else "Pending"
                            )
                        } ?: emptyList()
                    )

                    if (_selectedPeriod.value != requestedPeriod) return@launch

                    // Cache it
                    earningsCache[requestedPeriod] = result
                    _earningsState.value = EarningsState.Success(result)
                    Timber.d("$TAG: Loaded $requestedPeriod — ₹${result.totalEarnings}, ${result.tripCount} trips")
                } else {
                    if (_selectedPeriod.value != requestedPeriod) return@launch
                    _earningsState.value = EarningsState.Error("Failed to load earnings")
                    Timber.w("$TAG: API returned null data for $requestedPeriod")
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (_selectedPeriod.value != requestedPeriod) return@launch
                Timber.e(e, "$TAG: Failed to load earnings for $requestedPeriod")
                _earningsState.value = EarningsState.Error(e.message ?: "Network error")
            }
        }
    }

    /**
     * Force refresh — clears cache for current period.
     */
    fun refresh() {
        earningsCache.remove(_selectedPeriod.value)
        loadEarnings(_selectedPeriod.value)
    }
}

// =============================================================================
// STATE + DATA CLASSES
// =============================================================================

sealed class EarningsState {
    object Loading : EarningsState()
    data class Success(val data: EarningsData) : EarningsState()
    data class Error(val message: String) : EarningsState()
}

/**
 * Earnings data for a specific period.
 * Maps directly to what DriverEarningsScreen displays.
 */
data class EarningsData(
    val totalEarnings: Double,
    val tripCount: Int,
    val avgPerTrip: Double,
    val pendingAmount: Double,
    val trips: List<EarningItem>
)
