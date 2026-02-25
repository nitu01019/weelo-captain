package com.weelo.logistics.ui.transporter

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.data.api.DriverListData
import com.weelo.logistics.data.api.UserProfile
import com.weelo.logistics.data.api.VehicleListData
import com.weelo.logistics.data.cache.VehicleStats
import com.weelo.logistics.data.remote.RetrofitClient
import com.weelo.logistics.data.remote.SocketIOService
import com.weelo.logistics.data.remote.OrderCancelledNotification
import com.weelo.logistics.data.remote.DriverAddedNotification
import com.weelo.logistics.data.remote.DriverStatusChangedNotification
import com.weelo.logistics.data.remote.DriversUpdatedNotification
import com.weelo.logistics.offline.OfflineCache
import com.weelo.logistics.ui.utils.SocketUiEventDeduper
import com.weelo.logistics.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TransporterDashboardUiState {
    data object Loading : TransporterDashboardUiState

    data class Content(
        val profile: UserProfile?,
        val vehicleStats: VehicleListData?,
        val driverStats: DriverListData?,
        val isRefreshing: Boolean,
        val errorMessage: String?,
        val isBackendConnected: Boolean,
        val showFirstRunSetup: Boolean
    ) : TransporterDashboardUiState
}

sealed interface TransporterDashboardUiEvent {
    data class ShowOrderCancelledSnackbar(
        val notification: OrderCancelledNotification
    ) : TransporterDashboardUiEvent
}

class TransporterDashboardViewModel(
    appContext: Context,
    private val mainViewModel: MainViewModel
) : ViewModel() {

    private val appContext = appContext.applicationContext
    private val offlineCache = OfflineCache.getInstance(this.appContext)
    private val socketEventDeduper = SocketUiEventDeduper(maxEntries = 256)

    private val _uiState = MutableStateFlow<TransporterDashboardUiState>(TransporterDashboardUiState.Loading)
    val uiState: StateFlow<TransporterDashboardUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<TransporterDashboardUiEvent>(extraBufferCapacity = 16)
    val uiEvents: SharedFlow<TransporterDashboardUiEvent> = _uiEvents.asSharedFlow()

    private var bootstrapJob: Job? = null
    private var profile: UserProfile? = null
    private var cachedVehicleStats: VehicleListData? = null
    private var cachedDriverStats: DriverListData? = null
    private var driverStatsOverride: DriverListData? = null
    private var profileErrorMessage: String? = null
    private var isBackendConnected: Boolean = true
    private var profileRefreshing: Boolean = false
    private var bootstrapStarted = false
    private var bootstrapCacheLoaded = false
    private var mainStatsSettled = false
    private var sawMainLoading = false
    private var settleTimeoutJob: Job? = null

    init {
        observeMainState()
        observeRealtimeEvents()
    }

    fun bootstrapIfNeeded() {
        if (bootstrapStarted) return
        bootstrapStarted = true

        // Idempotent guards inside MainViewModel prevent duplicate startup churn.
        mainViewModel.loadVehiclesIfNeeded()
        mainViewModel.loadDriversIfNeeded()

        bootstrapJob = viewModelScope.launch {
            val cache = withContext(Dispatchers.IO) { offlineCache.getDashboardCache() }
            profile = cache.profile
            cachedVehicleStats = cache.vehicleStats
            cachedDriverStats = cache.driverStats
            bootstrapCacheLoaded = true
            publishUiState()

            startMainStatsSettlementTimeout()
            refreshProfile()
        }
    }

    fun retryRefresh() {
        viewModelScope.launch {
            refreshProfile()
            mainViewModel.forceRefreshVehicles()
            mainViewModel.forceRefreshDrivers()
        }
    }

    private fun observeMainState() {
        viewModelScope.launch {
            combine(
                mainViewModel.vehicleStats,
                mainViewModel.driverStats,
                mainViewModel.vehiclesLoading,
                mainViewModel.driversLoading
            ) { vehicleStats, driverStats, vehiclesLoading, driversLoading ->
                Quadruple(vehicleStats, driverStats, vehiclesLoading, driversLoading)
            }.collect { (vehicleStats, driverStats, vehiclesLoading, driversLoading) ->
                if (vehiclesLoading || driversLoading) {
                    sawMainLoading = true
                }
                if (!mainStatsSettled && !vehiclesLoading && !driversLoading) {
                    val hasMeaningfulNetworkState =
                        sawMainLoading ||
                            vehicleStats.total > 0 ||
                            driverStats.total > 0 ||
                            cachedVehicleStats != null ||
                            cachedDriverStats != null
                    if (hasMeaningfulNetworkState) {
                        mainStatsSettled = true
                    }
                }
                publishUiState(vehicleStats = vehicleStats, driverStats = driverStats)
            }
        }
    }

    private fun observeRealtimeEvents() {
        viewModelScope.launch {
            SocketIOService.driverAdded.collect { notification ->
                val key = "driver_added|${notification.driverId}|${notification.totalDrivers}|${notification.availableCount}|${notification.onTripCount}"
                if (!socketEventDeduper.shouldHandle(key)) return@collect
                applyDriverAdded(notification)
            }
        }
        viewModelScope.launch {
            SocketIOService.driversUpdated.collect { notification ->
                val key = "drivers_updated|${notification.action}|${notification.driverId}|${notification.totalDrivers}|${notification.availableCount}|${notification.onTripCount}"
                if (!socketEventDeduper.shouldHandle(key)) return@collect
                applyDriversUpdated(notification)
            }
        }
        viewModelScope.launch {
            SocketIOService.driverStatusChanged.collect { notification ->
                val key = "driver_status|${notification.driverId}|${notification.timestamp}|${notification.action}"
                if (!socketEventDeduper.shouldHandle(key)) return@collect
                applyDriverStatusChanged(notification)
            }
        }
        viewModelScope.launch {
            SocketIOService.orderCancelled.collect { notification ->
                val key = "order_cancelled|${notification.orderId}|${notification.cancelledAt}"
                if (!socketEventDeduper.shouldHandle(key)) return@collect
                _uiEvents.tryEmit(TransporterDashboardUiEvent.ShowOrderCancelledSnackbar(notification))
                // Refresh counts from shared app cache/network source without touching backend contracts.
                mainViewModel.forceRefreshVehicles()
                mainViewModel.forceRefreshDrivers()
            }
        }
    }

    private fun applyDriverAdded(notification: DriverAddedNotification) {
        driverStatsOverride = DriverListData(
            drivers = emptyList(),
            total = notification.totalDrivers,
            online = notification.availableCount,
            offline = notification.onTripCount
        )
        publishUiState()
    }

    private fun applyDriversUpdated(notification: DriversUpdatedNotification) {
        driverStatsOverride = DriverListData(
            drivers = emptyList(),
            total = notification.totalDrivers,
            online = notification.availableCount,
            offline = notification.onTripCount
        )
        publishUiState()
    }

    private fun applyDriverStatusChanged(notification: DriverStatusChangedNotification) {
        val current = currentEffectiveDriverStats() ?: return
        val delta = if (notification.isOnline) 1 else -1
        driverStatsOverride = current.copy(
            online = maxOf(0, current.online + delta),
            offline = maxOf(0, current.offline - delta)
        )
        publishUiState()
    }

    private fun currentEffectiveDriverStats(): DriverListData? {
        driverStatsOverride?.let { return it }
        if (mainStatsSettled) {
            return mainViewModel.driverStats.value
        }
        return cachedDriverStats ?: mainViewModel.driverStats.value.takeIf { it.total > 0 || it.online > 0 || it.offline > 0 }
    }

    private suspend fun refreshProfile() {
        profileRefreshing = true
        publishUiState()
        try {
            val response = withContext(Dispatchers.IO) { RetrofitClient.profileApi.getProfile() }
            if (response.isSuccessful && response.body()?.success == true) {
                profile = response.body()?.data?.user
                profileErrorMessage = null
                isBackendConnected = true
                withContext(Dispatchers.IO) {
                    offlineCache.saveDashboardData(
                        profile = profile,
                        vehicleStats = currentEffectiveVehicleStats(),
                        driverStats = currentEffectiveDriverStats()
                    )
                }
            } else {
                isBackendConnected = false
                if (profile == null) {
                    profileErrorMessage = appContext.getString(com.weelo.logistics.R.string.cannot_connect_backend)
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            isBackendConnected = false
            if (profile == null) {
                profileErrorMessage = appContext.getString(com.weelo.logistics.R.string.cannot_connect_backend)
            }
            timber.log.Timber.w(e, "Profile refresh failed")
        } finally {
            profileRefreshing = false
            publishUiState()
        }
    }

    private fun currentEffectiveVehicleStats(): VehicleListData? {
        if (mainStatsSettled) {
            return mainViewModel.vehicleStats.value.toVehicleListData()
        }
        val liveStats = mainViewModel.vehicleStats.value
        return cachedVehicleStats ?: liveStats.toVehicleListDataNullable()
    }

    private fun publishUiState(
        vehicleStats: VehicleStats = mainViewModel.vehicleStats.value,
        driverStats: DriverListData = mainViewModel.driverStats.value
    ) {
        val effectiveVehicleStats = if (mainStatsSettled) {
            vehicleStats.toVehicleListData()
        } else {
            cachedVehicleStats ?: vehicleStats.toVehicleListDataNullable()
        }

        val effectiveDriverStats = driverStatsOverride ?: if (mainStatsSettled) {
            driverStats
        } else {
            cachedDriverStats ?: driverStats.takeIf { it.total > 0 || it.online > 0 || it.offline > 0 }
        }

        val isLoading = !bootstrapCacheLoaded &&
            profile == null &&
            effectiveVehicleStats == null &&
            effectiveDriverStats == null &&
            profileErrorMessage == null

        if (isLoading) {
            _uiState.value = TransporterDashboardUiState.Loading
            return
        }

        val isRefreshing = profileRefreshing || mainViewModel.vehiclesLoading.value || mainViewModel.driversLoading.value
        val totalVehicles = effectiveVehicleStats?.total ?: 0
        val totalDrivers = effectiveDriverStats?.total ?: 0
        val showFirstRunSetup = mainStatsSettled &&
            totalVehicles == 0 &&
            totalDrivers == 0 &&
            profileErrorMessage == null

        _uiState.value = TransporterDashboardUiState.Content(
            profile = profile,
            vehicleStats = effectiveVehicleStats,
            driverStats = effectiveDriverStats,
            isRefreshing = isRefreshing,
            errorMessage = profileErrorMessage,
            isBackendConnected = isBackendConnected,
            showFirstRunSetup = showFirstRunSetup
        )
    }

    private fun startMainStatsSettlementTimeout() {
        settleTimeoutJob?.cancel()
        settleTimeoutJob = viewModelScope.launch {
            delay(1500)
            if (!mainStatsSettled) {
                timber.log.Timber.d("⏱️ Transporter dashboard stats settlement timeout reached")
                mainStatsSettled = true
                publishUiState()
            }
        }
    }
}

private fun VehicleStats.toVehicleListData(): VehicleListData = VehicleListData(
    vehicles = emptyList(),
    total = total,
    available = available,
    inTransit = inTransit,
    maintenance = maintenance
)

private fun VehicleStats.toVehicleListDataNullable(): VehicleListData? {
    return if (total == 0 && available == 0 && inTransit == 0 && maintenance == 0) null else toVehicleListData()
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

class TransporterDashboardViewModelFactory(
    private val appContext: Context,
    private val mainViewModel: MainViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TransporterDashboardViewModel(
            appContext = appContext.applicationContext,
            mainViewModel = mainViewModel
        ) as T
    }
}
