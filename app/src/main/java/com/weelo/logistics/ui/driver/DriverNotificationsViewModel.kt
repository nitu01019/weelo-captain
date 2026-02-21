package com.weelo.logistics.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.data.remote.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * =============================================================================
 * DRIVER NOTIFICATIONS VIEWMODEL — Real Data Source
 * =============================================================================
 *
 * Replaces hardcoded getSampleNotifications() with real data from:
 *   - GET /api/v1/driver/trips (completed trips → generates trip notifications)
 *   - GET /api/v1/driver/earnings (payments → generates payment notifications)
 *
 * FUTURE: When backend adds a /notifications endpoint, this ViewModel just
 * swaps the data source — Screen code stays unchanged.
 *
 * STATE MANAGEMENT:
 *   - notificationsState: Loading → Success | Error
 *   - filterType: "All" | "Trips" | "Payments" (local filter on cached data)
 *
 * SCALABILITY: Derives notifications from existing API data — no new endpoint needed.
 * MODULARITY: Screen observes StateFlow — zero API knowledge.
 * EASY UNDERSTANDING: One function: loadNotifications() → done.
 * SAME CODING STANDARD: Follows DriverEarningsViewModel pattern.
 * =============================================================================
 */
class DriverNotificationsViewModel : ViewModel() {

    companion object {
        private const val TAG = "DriverNotificationsVM"
    }

    // =========================================================================
    // STATE
    // =========================================================================

    private val _notificationsState = MutableStateFlow<NotificationsState>(NotificationsState.Loading)
    val notificationsState: StateFlow<NotificationsState> = _notificationsState.asStateFlow()

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    /** Full list cache — filtering is done locally */
    private var allNotifications: List<NotificationItem> = emptyList()

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Load notifications derived from real trip/earnings data.
     */
    fun loadNotifications() {
        if (allNotifications.isNotEmpty()) {
            _notificationsState.value = NotificationsState.Success(
                applyFilter(allNotifications, _selectedFilter.value)
            )
            return
        }

        viewModelScope.launch {
            _notificationsState.value = NotificationsState.Loading

            try {
                val driverApi = RetrofitClient.driverApi

                // Fetch recent trips (generates trip notifications)
                val tripsResponse = driverApi.getDriverTrips(limit = 20)
                val tripsBody = tripsResponse.body()
                if (!tripsResponse.isSuccessful || tripsBody?.success != true) {
                    val backendMessage = tripsBody?.error?.message?.takeIf { it.isNotBlank() }
                        ?: runCatching { tripsResponse.errorBody()?.string() }.getOrNull()?.takeIf { it.isNotBlank() }
                    val statusMessage = "Failed to load notifications (${tripsResponse.code()})"
                    val errorMessage = backendMessage?.let { "$statusMessage: $it" } ?: statusMessage
                    Timber.w("$TAG: $errorMessage")
                    allNotifications = emptyList()
                    _notificationsState.value = NotificationsState.Error(errorMessage)
                    return@launch
                }
                val trips = tripsBody.data?.trips ?: emptyList()

                // Build notifications from real trip data
                val notifications = mutableListOf<NotificationItem>()

                trips.forEach { trip ->
                    when (trip.status) {
                        "completed" -> {
                            notifications.add(
                                NotificationItem(
                                    id = "trip-complete-${trip.id}",
                                    title = "Trip Completed",
                                    message = "Your trip to ${trip.drop.address.take(40)} has been completed successfully",
                                    type = NotificationItemType.TRIP_COMPLETED,
                                    timestamp = trip.completedAt ?: trip.createdAt ?: "",
                                    isRead = true,
                                    tripId = trip.id
                                )
                            )
                            // Payment notification for completed trips
                            if (trip.fare > 0) {
                                notifications.add(
                                    NotificationItem(
                                        id = "payment-${trip.id}",
                                        title = "Payment Received",
                                        message = "₹${String.format("%,.0f", trip.fare)} has been credited for trip to ${trip.drop.address.take(30)}",
                                        type = NotificationItemType.PAYMENT_RECEIVED,
                                        timestamp = trip.completedAt ?: trip.createdAt ?: "",
                                        isRead = true,
                                        tripId = trip.id
                                    )
                                )
                            }
                        }
                        "in_progress", "active", "partially_filled" -> {
                            notifications.add(
                                NotificationItem(
                                    id = "trip-started-${trip.id}",
                                    title = "Trip In Progress",
                                    message = "Your trip from ${trip.pickup.address.take(30)} to ${trip.drop.address.take(30)} is active",
                                    type = NotificationItemType.TRIP_STARTED,
                                    timestamp = trip.startedAt ?: trip.createdAt ?: "",
                                    isRead = false,
                                    tripId = trip.id
                                )
                            )
                        }
                        "assigned", "driver_accepted" -> {
                            notifications.add(
                                NotificationItem(
                                    id = "trip-assigned-${trip.id}",
                                    title = "New Trip Assigned",
                                    message = "You have been assigned a trip from ${trip.pickup.address.take(30)} to ${trip.drop.address.take(30)}",
                                    type = NotificationItemType.TRIP_ASSIGNED,
                                    timestamp = trip.createdAt ?: "",
                                    isRead = false,
                                    tripId = trip.id
                                )
                            )
                        }
                    }
                }

                // Sort by timestamp (newest first)
                notifications.sortByDescending { it.timestamp }

                allNotifications = notifications
                _notificationsState.value = NotificationsState.Success(
                    applyFilter(notifications, _selectedFilter.value)
                )
                Timber.d("$TAG: Loaded ${notifications.size} notifications from ${trips.size} trips")

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "$TAG: Failed to load notifications")
                _notificationsState.value = NotificationsState.Error(e.message ?: "Network error")
            }
        }
    }

    /**
     * Update filter and re-apply locally.
     */
    fun setFilter(filter: String) {
        _selectedFilter.value = filter
        if (allNotifications.isNotEmpty()) {
            _notificationsState.value = NotificationsState.Success(
                applyFilter(allNotifications, filter)
            )
        }
    }

    /**
     * Mark notification as read (local state only — backend can add endpoint later).
     */
    fun markAsRead(notificationId: String) {
        allNotifications = allNotifications.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }
        _notificationsState.value = NotificationsState.Success(
            applyFilter(allNotifications, _selectedFilter.value)
        )
    }

    /**
     * Force refresh.
     */
    fun refresh() {
        allNotifications = emptyList()
        loadNotifications()
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun applyFilter(items: List<NotificationItem>, filter: String): List<NotificationItem> {
        return when (filter) {
            "Trips" -> items.filter {
                it.type in listOf(
                    NotificationItemType.TRIP_ASSIGNED,
                    NotificationItemType.TRIP_STARTED,
                    NotificationItemType.TRIP_COMPLETED
                )
            }
            "Payments" -> items.filter { it.type == NotificationItemType.PAYMENT_RECEIVED }
            "Unread" -> items.filter { !it.isRead }
            else -> items
        }
    }
}

// =============================================================================
// STATE + DATA CLASSES
// =============================================================================

sealed class NotificationsState {
    object Loading : NotificationsState()
    data class Success(val notifications: List<NotificationItem>) : NotificationsState()
    data class Error(val message: String) : NotificationsState()
}

enum class NotificationItemType {
    TRIP_ASSIGNED,
    TRIP_STARTED,
    TRIP_COMPLETED,
    PAYMENT_RECEIVED,
    GENERAL
}

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationItemType,
    val timestamp: String,
    val isRead: Boolean,
    val tripId: String? = null
)
