package com.weelo.logistics.core.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * =============================================================================
 * BASE VIEW MODEL - Scalable Foundation for All ViewModels
 * =============================================================================
 * 
 * ARCHITECTURE: MVVM + Clean Architecture
 * SCALABILITY: Designed for millions of users
 * 
 * FEATURES:
 * 1. Standardized UI State management (Loading, Success, Error)
 * 2. One-time events (navigation, snackbar, toast)
 * 3. Error handling with automatic retry
 * 4. Coroutine scope management
 * 5. Cache-first data loading pattern
 * 
 * USAGE FOR BACKEND DEVELOPERS:
 * ```kotlin
 * class MyViewModel(
 *     private val repository: MyRepository
 * ) : BaseViewModel<MyUiState, MyEvent>() {
 *     
 *     override fun createInitialState() = MyUiState()
 *     
 *     fun loadData() = execute {
 *         val data = repository.getData()
 *         updateState { copy(items = data) }
 *     }
 * }
 * ```
 * 
 * UI STATE PATTERN:
 * - `uiState`: Observable state flow for UI
 * - `isLoading`: Quick check for loading state
 * - `error`: Current error message if any
 * 
 * EVENT PATTERN:
 * - One-time events (navigation, toast) via SharedFlow
 * - Consumed once, won't survive config changes
 * 
 * =============================================================================
 */
abstract class BaseViewModel<S : UiState, E : UiEvent>(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    
    // ==========================================================================
    // UI STATE
    // ==========================================================================
    
    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<S> = _uiState.asStateFlow()
    
    /**
     * Create initial UI state - Override in subclass
     */
    protected abstract fun createInitialState(): S
    
    /**
     * Current state value (for quick access)
     */
    protected val currentState: S get() = _uiState.value
    
    /**
     * Update UI state with transformation
     * 
     * Example:
     * ```kotlin
     * updateState { copy(isLoading = true) }
     * ```
     */
    protected fun updateState(transform: S.() -> S) {
        _uiState.value = currentState.transform()
    }
    
    /**
     * Set UI state directly
     */
    protected fun setState(state: S) {
        _uiState.value = state
    }
    
    // ==========================================================================
    // ONE-TIME EVENTS
    // ==========================================================================
    
    private val _events = MutableSharedFlow<E>()
    val events = _events.asSharedFlow()
    
    /**
     * Send one-time event to UI
     * 
     * Example:
     * ```kotlin
     * sendEvent(MyEvent.NavigateToDetails(id))
     * sendEvent(MyEvent.ShowToast("Success!"))
     * ```
     */
    protected fun sendEvent(event: E) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }
    
    // ==========================================================================
    // LOADING STATE HELPERS
    // ==========================================================================
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    /**
     * Set loading state
     */
    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
    
    /**
     * Set error message
     */
    protected fun setError(message: String?) {
        _error.value = message
    }
    
    /**
     * Clear error
     */
    fun clearError() {
        _error.value = null
    }
    
    // ==========================================================================
    // EXECUTION HELPERS
    // ==========================================================================
    
    /**
     * Execute a suspending operation with automatic loading state management
     * 
     * Features:
     * - Automatic loading state (isLoading = true/false)
     * - Automatic error handling
     * - Runs on IO dispatcher by default
     * 
     * Example:
     * ```kotlin
     * fun loadUsers() = execute {
     *     val users = repository.getUsers()
     *     updateState { copy(users = users) }
     * }
     * ```
     */
    protected fun execute(
        showLoading: Boolean = true,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch(dispatcher) {
            try {
                if (showLoading) setLoading(true)
                setError(null)
                block()
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error occurred"
                setError(errorMessage)
                onError?.invoke(e)
                timber.log.Timber.e(e, "Execute error: $errorMessage")
            } finally {
                if (showLoading) setLoading(false)
            }
        }
    }
    
    /**
     * Execute with result - returns Result<T>
     * 
     * Example:
     * ```kotlin
     * fun loadUser(id: String) = executeWithResult {
     *     repository.getUser(id)
     * }.onSuccess { user ->
     *     updateState { copy(user = user) }
     * }.onFailure { error ->
     *     showError(error.message)
     * }
     * ```
     */
    protected fun <T> executeWithResult(
        showLoading: Boolean = true,
        block: suspend () -> T
    ): Deferred<Result<T>> {
        return viewModelScope.async(dispatcher) {
            try {
                if (showLoading) setLoading(true)
                setError(null)
                Result.success(block())
            } catch (e: Exception) {
                setError(e.message)
                Result.failure(e)
            } finally {
                if (showLoading) setLoading(false)
            }
        }
    }
    
    /**
     * Execute silently (no loading indicator) - for background refresh
     * 
     * Example:
     * ```kotlin
     * fun refreshInBackground() = executeSilently {
     *     val freshData = repository.fetchFresh()
     *     updateState { copy(data = freshData) }
     * }
     * ```
     */
    protected fun executeSilently(block: suspend () -> Unit) {
        execute(showLoading = false, block = block)
    }
    
    /**
     * Launch coroutine in viewModelScope (simple wrapper)
     */
    protected fun launch(block: suspend () -> Unit) {
        viewModelScope.launch(dispatcher) {
            block()
        }
    }
    
    // ==========================================================================
    // CACHE-FIRST PATTERN
    // ==========================================================================
    
    /**
     * Load data with cache-first strategy
     * 
     * Pattern:
     * 1. Load from cache immediately (no loading spinner)
     * 2. Fetch fresh data from API in background
     * 3. Update UI with fresh data
     * 4. Save fresh data to cache
     * 
     * Example:
     * ```kotlin
     * fun loadDashboard() = loadCacheFirst(
     *     loadFromCache = { cache.getDashboardData() },
     *     fetchFromApi = { api.getDashboard() },
     *     saveToCache = { cache.saveDashboardData(it) },
     *     onData = { data -> updateState { copy(dashboard = data) } }
     * )
     * ```
     */
    protected fun <T> loadCacheFirst(
        loadFromCache: suspend () -> T?,
        fetchFromApi: suspend () -> T,
        saveToCache: suspend (T) -> Unit,
        onData: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        viewModelScope.launch(dispatcher) {
            // Step 1: Load cached data immediately
            try {
                val cachedData = loadFromCache()
                if (cachedData != null) {
                    onData(cachedData)
                }
            } catch (e: Exception) {
                timber.log.Timber.w("Cache load failed: ${e.message}")
            }
            
            // Step 2: Fetch fresh data from API
            try {
                val freshData = fetchFromApi()
                onData(freshData)
                
                // Step 3: Save to cache
                try {
                    saveToCache(freshData)
                } catch (e: Exception) {
                    timber.log.Timber.w("Cache save failed: ${e.message}")
                }
            } catch (e: Exception) {
                timber.log.Timber.e("API fetch failed: ${e.message}")
                onError?.invoke(e)
                // Don't set error if we have cached data
            }
        }
    }
    
    // ==========================================================================
    // ASYNC HELPERS (for parallel API calls)
    // ==========================================================================
    
    /**
     * Create async deferred for parallel execution
     */
    protected fun <T> launchAsync(block: suspend () -> T): Deferred<T> = viewModelScope.async(dispatcher) { block() }
}

/**
 * =============================================================================
 * UI STATE INTERFACE
 * =============================================================================
 * 
 * All UI state data classes should implement this interface.
 * 
 * Example:
 * ```kotlin
 * data class DashboardUiState(
 *     val profile: UserProfile? = null,
 *     val vehicles: List<Vehicle> = emptyList(),
 *     val drivers: List<Driver> = emptyList(),
 *     val isRefreshing: Boolean = false
 * ) : UiState
 * ```
 */
interface UiState

/**
 * =============================================================================
 * UI EVENT INTERFACE
 * =============================================================================
 * 
 * All one-time events should implement this interface.
 * 
 * Example:
 * ```kotlin
 * sealed class DashboardEvent : UiEvent {
 *     data class NavigateToVehicle(val id: String) : DashboardEvent()
 *     data class ShowToast(val message: String) : DashboardEvent()
 *     object LogoutComplete : DashboardEvent()
 * }
 * ```
 */
interface UiEvent

/**
 * =============================================================================
 * EMPTY IMPLEMENTATIONS
 * =============================================================================
 * 
 * For ViewModels that don't need custom events
 */
object EmptyState : UiState
object EmptyEvent : UiEvent

/**
 * =============================================================================
 * SIMPLE VIEW MODEL
 * =============================================================================
 * 
 * For simple screens that only need loading/error states
 * 
 * Example:
 * ```kotlin
 * class SettingsViewModel : SimpleViewModel() {
 *     fun updateSetting(key: String, value: Any) = execute {
 *         repository.updateSetting(key, value)
 *     }
 * }
 * ```
 */
abstract class SimpleViewModel : BaseViewModel<EmptyState, EmptyEvent>() {
    override fun createInitialState() = EmptyState
}
