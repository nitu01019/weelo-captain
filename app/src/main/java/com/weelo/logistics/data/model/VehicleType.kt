package com.weelo.logistics.data.model

/**
 * High-level vehicle type classification
 * Used for initial categorization before truck categories
 */
data class VehicleType(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val isAvailable: Boolean = true // false = "Coming Soon"
)

/**
 * Catalog of all vehicle types
 */
object VehicleTypeCatalog {
    
    fun getAllVehicleTypes(): List<VehicleType> {
        return listOf(
            VehicleType(
                id = "truck",
                name = "Truck",
                description = "Commercial trucks for cargo transport",
                icon = "🚛",
                isAvailable = true
            ),
            VehicleType(
                id = "tractor",
                name = "Tractor",
                description = "Agricultural and construction tractors",
                icon = "🚜",
                isAvailable = false // Coming soon
            ),
            VehicleType(
                id = "jcb",
                name = "JCB",
                description = "Construction equipment and excavators",
                icon = "🏗️",
                isAvailable = false // Coming soon
            ),
            VehicleType(
                id = "tempo",
                name = "Tempo",
                description = "Light commercial vehicles",
                icon = "🚐",
                isAvailable = false // Coming soon
            )
        )
    }
    
    fun getVehicleTypeById(id: String): VehicleType? {
        return getAllVehicleTypes().find { it.id == id }
    }
}
