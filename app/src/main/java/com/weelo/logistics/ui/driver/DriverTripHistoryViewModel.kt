package com.weelo.logistics.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * DRIVER TRIP HISTORY VIEWMODEL — Real API Data
 * =============================================================================
 *
 * Replaces MockDataRepository in DriverTripHistoryScreen with real data from:
 *   - GET /api/v1/driver/trips?status=<filter>&limit=50
 *
 * STATE MANAGEMENT:
 *   - tripHistoryState: Loading → Success (with data) or Error
 *   - filter + search are local UI state; API re-fetched on filter change
 *   - Cached per filter — switching tabs is instant
 *
 * SCALABILITY:
 *   - Backend uses indexed queries on driverId + status
 *   - Pagination support (limit/offset) — ready for infinite scroll
 *   - Client-side search on already-fetched data (no extra API calls)
 *
 * MODULARITY:
 *   - Standalone — no dependency on Dashboard ViewModel
 *   - Screen just observes StateFlow — zero API knowledge
 *
 * EASY UNDERSTANDING:
 *   - loadTrips(filter) → fetches from API → emits state
 *   - searchTrips(query) → filters locally on cached data
 *
 * SAME CODING STANDARD:
 *   - Follows exact same pattern as DriverEarningsViewModel
 *
 * =============================================================================
 */
class DriverTripHistoryViewModel : ViewModel() {

    companion object {
        private const val TAG = "DriverTripHistoryVM"
    }

    // =========================================================================
    // STATE
    // =========================================================================

    private val _tripHistoryState = MutableStateFlow<TripHistoryState>(TripHistoryState.Loading)
    val tripHistoryState: StateFlow<TripHistoryState> = _tripHistoryState.asStateFlow()

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Cache per filter — switching tabs doesn't re-fetch */
    private val tripsCache = mutableMapOf<String, List<TripHistoryItem>>()
    private var loadJob: Job? = null

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Load trips for a filter. Checks cache first.
     *
     * @param filter "All", "Completed", or "Cancelled"
     */
    fun loadTrips(filter: String = "All") {
        _selectedFilter.value = filter
        val requestedFilter = filter

        // Check cache
        tripsCache[requestedFilter]?.let { cached ->
            _tripHistoryState.value = TripHistoryState.Success(
                filterBySearch(cached, _searchQuery.value)
            )
            Timber.d("$TAG: Cache HIT for filter=$requestedFilter")
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _tripHistoryState.value = TripHistoryState.Loading

            try {
                val driverApi = RetrofitClient.driverApi
                val apiStatus = when (requestedFilter) {
                    "Completed" -> "completed"
                    "Cancelled" -> "cancelled"
                    else -> null // All trips
                }

                val response = driverApi.getDriverTrips(
                    status = apiStatus,
                    limit = 50,
                    offset = 0
                )

                if (_selectedFilter.value != requestedFilter) return@launch

                if (!response.isSuccessful) {
                    _tripHistoryState.value = TripHistoryState.Error("API error ${response.code()}")
                    Timber.w("$TAG: API error ${response.code()} for filter=$requestedFilter")
                    return@launch
                }
                if (response.body()?.success != true) {
                    _tripHistoryState.value = TripHistoryState.Error("Failed to load trips")
                    Timber.w("$TAG: success=false for filter=$requestedFilter")
                    return@launch
                }

                val tripsData = response.body()?.data

                if (tripsData != null) {
                    val items = tripsData.trips.map { trip: TripData ->
                        TripHistoryItem(
                            id = trip.id,
                            customerName = trip.customerName ?: "Customer",
                            pickupAddress = trip.pickup.address,
                            dropAddress = trip.drop.address,
                            distanceKm = trip.distanceKm,
                            fare = trip.fare,
                            status = trip.status,
                            createdAt = trip.createdAt ?: "",
                            completedAt = trip.completedAt,
                            vehicleType = trip.vehicleType ?: ""
                        )
                    }

                    if (_selectedFilter.value != requestedFilter) return@launch

                    // Cache it
                    tripsCache[requestedFilter] = items
                    _tripHistoryState.value = TripHistoryState.Success(
                        filterBySearch(items, _searchQuery.value)
                    )
                    Timber.d("$TAG: Loaded $requestedFilter — ${items.size} trips")
                } else {
                    // null data from API is an error — don't silently show empty list
                    if (_selectedFilter.value != requestedFilter) return@launch
                    _tripHistoryState.value = TripHistoryState.Error("Failed to load trips")
                    Timber.w("$TAG: API returned null data for filter=$requestedFilter")
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (_selectedFilter.value != requestedFilter) return@launch
                Timber.e(e, "$TAG: Failed to load trips for filter=$requestedFilter")
                _tripHistoryState.value = TripHistoryState.Error(e.message ?: "Network error")
            }
        }
    }

    /**
     * Update search query and filter cached trips locally.
     */
    fun updateSearch(query: String) {
        _searchQuery.value = query
        val currentFilter = _selectedFilter.value
        tripsCache[currentFilter]?.let { cached ->
            _tripHistoryState.value = TripHistoryState.Success(filterBySearch(cached, query))
        }
    }

    /**
     * Force refresh — clears cache for current filter.
     */
    fun refresh() {
        tripsCache.remove(_selectedFilter.value)
        loadTrips(_selectedFilter.value)
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun filterBySearch(trips: List<TripHistoryItem>, query: String): List<TripHistoryItem> {
        if (query.isBlank()) return trips
        val q = query.lowercase().trim()
        return trips.filter {
            it.customerName.lowercase().contains(q) ||
            it.pickupAddress.lowercase().contains(q) ||
            it.dropAddress.lowercase().contains(q)
        }
    }
}

// =============================================================================
// STATE + DATA CLASSES
// =============================================================================

sealed class TripHistoryState {
    object Loading : TripHistoryState()
    data class Success(val trips: List<TripHistoryItem>) : TripHistoryState()
    data class Error(val message: String) : TripHistoryState()
}

/**
 * Trip history item — maps to backend GET /api/v1/driver/trips response.
 */
data class TripHistoryItem(
    val id: String,
    val customerName: String,
    val pickupAddress: String,
    val dropAddress: String,
    val distanceKm: Double,
    val fare: Double,
    val status: String,
    val createdAt: String,
    val completedAt: String?,
    val vehicleType: String
)
