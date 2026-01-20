package com.weelo.logistics.data.model

/**
 * Vehicle Type Model - Used by AddVehicleScreen
 * Main vehicle types: Truck, Tractor, JCB, Tempo
 * Uses icon names for Material Icons
 */
data class VehicleType(
    val id: String,
    val name: String,
    val category: String,
    val capacityTons: Double,
    val description: String = "",
    val iconRes: Int? = null,
    val isAvailable: Boolean = true,
    val icon: String? = null,
    val iconName: String = "" // For identifying which Material Icon to use
)

/**
 * Catalog of main vehicle types for first selection screen
 * Only 4 types: Truck, Tractor, JCB, Tempo
 */
object VehicleTypeCatalog {
    
    private val vehicleTypes = listOf(
        VehicleType(
            id = "truck",
            name = "Truck",
            category = "Commercial",
            capacityTons = 0.0,
            description = "All types of trucks for logistics",
            isAvailable = true,
            iconName = "truck"
        ),
        VehicleType(
            id = "tractor",
            name = "Tractor",
            category = "Agriculture",
            capacityTons = 0.0,
            description = "Agricultural tractors",
            isAvailable = false, // Coming soon
            iconName = "tractor"
        ),
        VehicleType(
            id = "jcb",
            name = "JCB",
            category = "Construction",
            capacityTons = 0.0,
            description = "JCB and construction equipment",
            isAvailable = false, // Coming soon
            iconName = "jcb"
        ),
        VehicleType(
            id = "tempo",
            name = "Tempo",
            category = "Light Commercial",
            capacityTons = 0.0,
            description = "Light commercial vehicles",
            isAvailable = false, // Coming soon
            iconName = "tempo"
        )
    )
    
    fun getAllVehicleTypes(): List<VehicleType> = vehicleTypes
    
    fun getByCategory(category: String): List<VehicleType> = 
        vehicleTypes.filter { it.category == category }
    
    fun getById(id: String): VehicleType? = 
        vehicleTypes.find { it.id == id }
}
