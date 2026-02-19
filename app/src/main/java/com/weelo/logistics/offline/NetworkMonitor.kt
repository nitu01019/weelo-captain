package com.weelo.logistics.offline

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * =============================================================================
 * NETWORK MONITOR - Real-time connectivity tracking
 * =============================================================================
 * 
 * OFFLINE SUPPORT:
 * - Monitors network connectivity in real-time
 * - Emits state changes via Flow
 * - Used by UI to show offline indicators
 * - Used by sync service to trigger sync when online
 * 
 * USAGE:
 * val monitor = NetworkMonitor.getInstance(context)
 * monitor.isOnline.collect { isOnline -> ... }
 * 
 * =============================================================================
 */

sealed class NetworkState {
    object Available : NetworkState()
    object Unavailable : NetworkState()
    object Losing : NetworkState()
    object Lost : NetworkState()
}

class NetworkMonitor private constructor(
    private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _networkState = MutableStateFlow<NetworkState>(checkInitialState())
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()
    
    private val _isOnline = MutableStateFlow(checkIsOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    @SuppressLint("StaticFieldLeak")
    companion object {
        private const val TAG = "NetworkMonitor"
        
        @Volatile
        private var instance: NetworkMonitor? = null
        
        fun getInstance(context: Context): NetworkMonitor {
            return instance ?: synchronized(this) {
                instance ?: NetworkMonitor(context.applicationContext).also { 
                    instance = it
                    it.startMonitoring()
                }
            }
        }
    }
    
    /**
     * Check initial network state
     */
    private fun checkInitialState(): NetworkState {
        return if (checkIsOnline()) NetworkState.Available else NetworkState.Unavailable
    }
    
    /**
     * Check if device is currently online
     */
    private fun checkIsOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Start monitoring network changes
     */
    private fun startMonitoring() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                timber.log.Timber.i("🌐 Network AVAILABLE")
                _networkState.value = NetworkState.Available
                _isOnline.value = true
            }
            
            override fun onLosing(network: Network, maxMsToLive: Int) {
                timber.log.Timber.w("🌐 Network LOSING (${maxMsToLive}ms)")
                _networkState.value = NetworkState.Losing
            }
            
            override fun onLost(network: Network) {
                timber.log.Timber.w("🌐 Network LOST")
                _networkState.value = NetworkState.Lost
                _isOnline.value = false
            }
            
            override fun onUnavailable() {
                timber.log.Timber.w("🌐 Network UNAVAILABLE")
                _networkState.value = NetworkState.Unavailable
                _isOnline.value = false
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
                val wasOnline = _isOnline.value
                val nowOnline = hasInternet && isValidated
                
                if (wasOnline != nowOnline) {
                    timber.log.Timber.i("🌐 Network capabilities changed: online=$nowOnline")
                    _isOnline.value = nowOnline
                    _networkState.value = if (nowOnline) NetworkState.Available else NetworkState.Unavailable
                }
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            timber.log.Timber.i("🌐 Network monitoring started (initial: ${if (_isOnline.value) "ONLINE" else "OFFLINE"})")
        } catch (e: Exception) {
            timber.log.Timber.e("Failed to register network callback: ${e.message}")
        }
    }
    
    /**
     * Flow that emits network state changes
     */
    fun observeNetworkState(): Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(NetworkState.Available)
            }
            
            override fun onLosing(network: Network, maxMsToLive: Int) {
                trySend(NetworkState.Losing)
            }
            
            override fun onLost(network: Network) {
                trySend(NetworkState.Lost)
            }
            
            override fun onUnavailable() {
                trySend(NetworkState.Unavailable)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        // Emit initial state
        trySend(checkInitialState())
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
    
    /**
     * Check connectivity synchronously
     */
    fun isCurrentlyOnline(): Boolean = checkIsOnline()
}
