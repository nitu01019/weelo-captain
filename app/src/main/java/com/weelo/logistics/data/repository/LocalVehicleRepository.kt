package com.weelo.logistics.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weelo.logistics.data.api.VehicleData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * =============================================================================
 * LOCAL VEHICLE REPOSITORY - Offline-First Vehicle Storage
 * =============================================================================
 * 
 * Stores vehicles locally using SharedPreferences with JSON serialization.
 * This allows the app to work WITHOUT a backend connection.
 * 
 * FEATURES:
 * - Add, update, delete vehicles locally
 * - Persist across app restarts
 * - Real-time updates via StateFlow
 * - Sync with backend when available (future)
 * 
 * FOR BACKEND DEVELOPERS:
 * - This is a temporary solution until backend is connected
 * - When backend is ready, sync local data to server
 * - Use this as fallback for offline mode
 * =============================================================================
 */
class LocalVehicleRepository private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    // Observable state for UI
    private val _vehicles = MutableStateFlow<List<VehicleData>>(emptyList())
    val vehicles: StateFlow<List<VehicleData>> = _vehicles.asStateFlow()
    
    // Stats
    private val _stats = MutableStateFlow(VehicleStats())
    val stats: StateFlow<VehicleStats> = _stats.asStateFlow()
    
    init {
        // Load vehicles from storage on init
        loadVehicles()
    }
    
    companion object {
        private const val PREFS_NAME = "weelo_vehicles"
        private const val KEY_VEHICLES = "vehicles_list"
        
        @Volatile
        private var instance: LocalVehicleRepository? = null
        
        fun getInstance(context: Context): LocalVehicleRepository {
            return instance ?: synchronized(this) {
                instance ?: LocalVehicleRepository(context.applicationContext).also { 
                    instance = it 
                }
            }
        }
    }
    
    /**
     * Load vehicles from SharedPreferences
     */
    private fun loadVehicles() {
        val json = prefs.getString(KEY_VEHICLES, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<VehicleData>>() {}.type
                val vehicleList: List<VehicleData> = gson.fromJson(json, type)
                _vehicles.value = vehicleList
                updateStats(vehicleList)
            } catch (e: Exception) {
                _vehicles.value = emptyList()
            }
        }
    }
    
    /**
     * Save vehicles to SharedPreferences
     */
    private fun saveVehicles(vehicles: List<VehicleData>) {
        val json = gson.toJson(vehicles)
        prefs.edit().putString(KEY_VEHICLES, json).apply()
        _vehicles.value = vehicles
        updateStats(vehicles)
    }
    
    /**
     * Update statistics
     */
    private fun updateStats(vehicles: List<VehicleData>) {
        _stats.value = VehicleStats(
            total = vehicles.size,
            available = vehicles.count { it.status == "available" },
            inTransit = vehicles.count { it.status == "in_transit" },
            maintenance = vehicles.count { it.status == "maintenance" }
        )
    }
    
    /**
     * Add a new vehicle
     */
    fun addVehicle(
        vehicleNumber: String,
        vehicleType: String,
        vehicleSubtype: String,
        capacity: String,
        model: String? = null,
        make: String? = null,
        year: Int? = null
    ): VehicleData {
        val newVehicle = VehicleData(
            id = UUID.randomUUID().toString(),
            transporterId = "local_transporter",
            vehicleNumber = vehicleNumber.uppercase().replace(" ", ""),
            vehicleType = vehicleType,
            vehicleSubtype = vehicleSubtype,
            capacity = capacity,
            model = model,
            make = make,
            year = year,
            status = "available",
            isActive = true,
            createdAt = System.currentTimeMillis().toString()
        )
        
        val currentList = _vehicles.value.toMutableList()
        currentList.add(0, newVehicle) // Add to top
        saveVehicles(currentList)
        
        return newVehicle
    }
    
    /**
     * Add multiple vehicles at once
     */
    fun addVehicles(vehiclesToAdd: List<VehicleData>): List<VehicleData> {
        val currentList = _vehicles.value.toMutableList()
        currentList.addAll(0, vehiclesToAdd)
        saveVehicles(currentList)
        return vehiclesToAdd
    }
    
    /**
     * Update vehicle status
     */
    fun updateVehicleStatus(vehicleId: String, newStatus: String) {
        val currentList = _vehicles.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == vehicleId }
        if (index != -1) {
            currentList[index] = currentList[index].copy(status = newStatus)
            saveVehicles(currentList)
        }
    }
    
    /**
     * Delete a vehicle
     */
    fun deleteVehicle(vehicleId: String) {
        val currentList = _vehicles.value.toMutableList()
        currentList.removeAll { it.id == vehicleId }
        saveVehicles(currentList)
    }
    
    /**
     * Get vehicle by ID
     */
    fun getVehicleById(vehicleId: String): VehicleData? {
        return _vehicles.value.find { it.id == vehicleId }
    }
    
    /**
     * Get all vehicles (snapshot)
     */
    fun getAllVehicles(): List<VehicleData> = _vehicles.value
    
    /**
     * Clear all vehicles (for testing)
     */
    fun clearAll() {
        prefs.edit().remove(KEY_VEHICLES).apply()
        _vehicles.value = emptyList()
        _stats.value = VehicleStats()
    }
    
    /**
     * Check if vehicle number already exists
     */
    fun vehicleExists(vehicleNumber: String): Boolean {
        val normalized = vehicleNumber.uppercase().replace(" ", "").replace("-", "")
        return _vehicles.value.any { 
            it.vehicleNumber.uppercase().replace(" ", "").replace("-", "") == normalized 
        }
    }
}

/**
 * Vehicle statistics
 */
data class VehicleStats(
    val total: Int = 0,
    val available: Int = 0,
    val inTransit: Int = 0,
    val maintenance: Int = 0
)
