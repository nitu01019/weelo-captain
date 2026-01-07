package com.weelo.logistics.ui.transporter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weelo.logistics.data.model.Vehicle
import com.weelo.logistics.utils.InputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * VehicleViewModel - Scalable Fleet Management
 * 
 * Handles all vehicle/fleet operations for transporters
 * 
 * Backend Endpoints:
 * POST   /api/v1/transporter/{transporterId}/vehicles    - Add vehicle
 * GET    /api/v1/transporter/{transporterId}/vehicles    - Fleet list
 * GET    /api/v1/transporter/vehicle/{vehicleId}         - Vehicle details
 * PUT    /api/v1/transporter/vehicle/{vehicleId}         - Update vehicle
 * DELETE /api/v1/transporter/vehicle/{vehicleId}         - Delete vehicle
 * GET    /api/v1/vehicle-types                           - Get vehicle types/categories
 */
class VehicleViewModel : ViewModel() {
    
    // Fleet List State
    private val _fleetState = MutableStateFlow<FleetState>(FleetState.Loading)
    val fleetState: StateFlow<FleetState> = _fleetState.asStateFlow()
    
    // Vehicle Details State
    private val _vehicleDetailsState = MutableStateFlow<VehicleDetailsState>(VehicleDetailsState.Loading)
    val vehicleDetailsState: StateFlow<VehicleDetailsState> = _vehicleDetailsState.asStateFlow()
    
    // Action State (Add/Update/Delete)
    private val _vehicleActionState = MutableStateFlow<VehicleActionState>(VehicleActionState.Idle)
    val vehicleActionState: StateFlow<VehicleActionState> = _vehicleActionState.asStateFlow()
    
    /**
     * Load Fleet List
     * Endpoint: GET /api/v1/transporter/{transporterId}/vehicles?page=1&limit=20&status=active
     * 
     * Scalability: Pagination, filtering by status/type
     */
    fun loadFleet(
        transporterId: String,
        page: Int = 1,
        limit: Int = 20,
        status: String? = null
    ) {
        viewModelScope.launch {
            _fleetState.value = FleetState.Loading
            
            withContext(Dispatchers.IO) {
                // TODO: Replace with actual API call
                // val result = vehicleRepository.getFleet(transporterId, page, limit, status)
                
                _fleetState.value = FleetState.Success(
                    vehicles = emptyList(),
                    hasMore = false
                )
            }
        }
    }
    
    /**
     * Add New Vehicle
     * Endpoint: POST /api/v1/transporter/{transporterId}/vehicles
     * Body: { vehicleNumber, vehicleType, subtype, capacity, ... }
     * 
     * Scalability: Validation before API call
     */
    fun addVehicle(transporterId: String, vehicleData: Map<String, Any>) {
        viewModelScope.launch {
            // Validate vehicle number
            val vehicleNumber = vehicleData["vehicleNumber"] as? String ?: ""
            val validation = InputValidator.validateVehicleNumber(vehicleNumber)
            
            if (!validation.isValid) {
                _vehicleActionState.value = VehicleActionState.Error(
                    validation.errorMessage ?: "Invalid vehicle number"
                )
                return@launch
            }
            
            _vehicleActionState.value = VehicleActionState.Loading("Adding vehicle...")
            
            withContext(Dispatchers.IO) {
                // TODO: Replace with actual API call
                // val result = vehicleRepository.addVehicle(transporterId, vehicleData)
                
                _vehicleActionState.value = VehicleActionState.Success("Vehicle added successfully")
                loadFleet(transporterId)
            }
        }
    }
    
    /**
     * Load Vehicle Details
     * Endpoint: GET /api/v1/transporter/vehicle/{vehicleId}
     */
    fun loadVehicleDetails(vehicleId: String) {
        viewModelScope.launch {
            _vehicleDetailsState.value = VehicleDetailsState.Loading
            
            withContext(Dispatchers.IO) {
                // TODO: Replace with actual API call
                // val result = vehicleRepository.getVehicleDetails(vehicleId)
                
                _vehicleDetailsState.value = VehicleDetailsState.Success(vehicle = null)
            }
        }
    }
    
    /**
     * Update Vehicle
     * Endpoint: PUT /api/v1/transporter/vehicle/{vehicleId}
     * Body: { status, assignedDriver, ... }
     */
    fun updateVehicle(vehicleId: String, updates: Map<String, Any>) {
        viewModelScope.launch {
            _vehicleActionState.value = VehicleActionState.Loading("Updating vehicle...")
            
            withContext(Dispatchers.IO) {
                // TODO: Replace with actual API call
                // val result = vehicleRepository.updateVehicle(vehicleId, updates)
                
                _vehicleActionState.value = VehicleActionState.Success("Vehicle updated")
                loadVehicleDetails(vehicleId)
            }
        }
    }
    
    /**
     * Delete Vehicle
     * Endpoint: DELETE /api/v1/transporter/vehicle/{vehicleId}
     */
    fun deleteVehicle(vehicleId: String, transporterId: String) {
        viewModelScope.launch {
            _vehicleActionState.value = VehicleActionState.Loading("Removing vehicle...")
            
            withContext(Dispatchers.IO) {
                // TODO: Replace with actual API call
                // val result = vehicleRepository.deleteVehicle(vehicleId)
                
                _vehicleActionState.value = VehicleActionState.Success("Vehicle removed")
                loadFleet(transporterId)
            }
        }
    }
    
    fun resetActionState() {
        _vehicleActionState.value = VehicleActionState.Idle
    }
}

// Fleet State
sealed class FleetState {
    object Loading : FleetState()
    data class Success(
        val vehicles: List<Vehicle>,
        val hasMore: Boolean
    ) : FleetState()
    data class Error(val message: String) : FleetState()
}

// Vehicle Details State
sealed class VehicleDetailsState {
    object Loading : VehicleDetailsState()
    data class Success(val vehicle: Vehicle?) : VehicleDetailsState()
    data class Error(val message: String) : VehicleDetailsState()
}

// Vehicle Action State
sealed class VehicleActionState {
    object Idle : VehicleActionState()
    data class Loading(val message: String) : VehicleActionState()
    data class Success(val message: String) : VehicleActionState()
    data class Error(val message: String) : VehicleActionState()
}
