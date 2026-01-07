package com.weelo.logistics.data.model

import com.weelo.logistics.R

/**
 * Vehicle model - PRD-06 Compliant
 * Supports complex categories with subtypes
 */
data class Vehicle(
    val id: String,
    val transporterId: String,
    val category: TruckCategory, // Main category (Open, Container, LCV, etc.)
    val subtype: TruckSubtype, // Specific subtype (17 Feet, 32 Feet Single Axle, etc.)
    val vehicleNumber: String, // GJ-01-AB-1234
    val model: String? = null, // Tata, Mahindra, etc.
    val year: Int? = null,
    val assignedDriverId: String? = null,
    val status: VehicleStatus = VehicleStatus.AVAILABLE,
    val lastServiceDate: Long? = null,
    val insuranceExpiryDate: Long? = null,
    val registrationExpiryDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    // Helper to get display name
    val displayName: String
        get() = "${category.name} - ${subtype.name}"
    
    // Helper to get capacity
    val capacityText: String
        get() = "${subtype.capacityTons} Ton"
}

/**
 * Vehicle Status
 */
enum class VehicleStatus {
    AVAILABLE,      // Ready for trip
    IN_TRANSIT,     // On a trip
    MAINTENANCE,    // Under maintenance
    INACTIVE        // Not in service
}

/**
 * Truck Category - Main vehicle type
 * PRD-06: 9 main categories
 */
data class TruckCategory(
    val id: String,
    val name: String,
    val icon: String,  // Kept for backward compatibility, but use imageResId for proper images
    val imageResId: Int? = null,  // Drawable resource ID for vehicle image
    val description: String
)

/**
 * Truck Subtype - Specific variant within a category
 * PRD-06: Each category has multiple subtypes
 */
data class TruckSubtype(
    val id: String,
    val name: String,
    val capacityTons: Double
)

/**
 * PRD-06 Compliant Vehicle Catalog
 * 9 Categories with subtypes
 */
object VehicleCatalog {
    
    // 1. OPEN TRUCK
    val OPEN_TRUCK = TruckCategory(
        id = "open",
        name = "Open Truck",
        icon = "🚛",
        imageResId = R.drawable.vehicle_open,
        description = "Open body trucks for general cargo"
    )
    
    val OPEN_TRUCK_SUBTYPES = listOf(
        // By Length
        TruckSubtype("17_feet", "17 Feet", 7.5),
        TruckSubtype("19_feet", "19 Feet", 9.0),
        TruckSubtype("20_feet", "20 Feet", 10.0),
        TruckSubtype("22_feet", "22 Feet", 12.0),
        TruckSubtype("24_feet", "24 Feet", 15.0),
        // By Wheels
        TruckSubtype("10_wheeler", "10 Wheeler", 16.0),
        TruckSubtype("12_wheeler", "12 Wheeler", 20.0),
        TruckSubtype("14_wheeler", "14 Wheeler", 25.0),
        TruckSubtype("16_wheeler", "16 Wheeler", 35.0),
        TruckSubtype("18_wheeler", "18 Wheeler", 43.0)
    )
    
    // 2. CONTAINER
    val CONTAINER = TruckCategory(
        id = "container",
        name = "Container",
        icon = "📦",
        imageResId = R.drawable.vehicle_container,
        description = "Container trucks for enclosed cargo"
    )
    
    val CONTAINER_SUBTYPES = listOf(
        TruckSubtype("19_feet", "19 Feet", 7.5),
        TruckSubtype("20_feet", "20 Feet", 9.0),
        TruckSubtype("22_feet", "22 Feet", 12.0),
        TruckSubtype("24_feet", "24 Feet", 15.0),
        TruckSubtype("32_single", "32 Feet Single Axle", 20.0),
        TruckSubtype("32_multi", "32 Feet Multi Axle", 25.0),
        TruckSubtype("32_triple", "32 Feet Triple Axle", 30.0)
    )
    
    // 3. LCV
    val LCV = TruckCategory(
        id = "lcv",
        name = "LCV",
        icon = "🚐",
        imageResId = R.drawable.vehicle_lcv,
        description = "Light Commercial Vehicles"
    )
    
    val LCV_SUBTYPES = listOf(
        // LCV Open
        TruckSubtype("14_open", "14 Feet Open", 2.5),
        TruckSubtype("17_open", "17 Feet Open", 3.5),
        TruckSubtype("19_open", "19 Feet Open", 4.5),
        TruckSubtype("20_open", "20 Feet Open", 5.0),
        TruckSubtype("22_open", "22 Feet Open", 6.0),
        TruckSubtype("24_open", "24 Feet Open", 7.0),
        // LCV Container
        TruckSubtype("14_container", "14 Feet Container", 2.5),
        TruckSubtype("17_container", "17 Feet Container", 3.5),
        TruckSubtype("19_container", "19 Feet Container", 4.5),
        TruckSubtype("20_container", "20 Feet Container", 5.0),
        TruckSubtype("22_container", "22 Feet Container", 6.0),
        TruckSubtype("24_container", "24 Feet Container", 7.0),
        TruckSubtype("32_sxl", "32 Feet SXL Container", 7.0)
    )
    
    // 4. MINI/PICKUP
    val MINI_PICKUP = TruckCategory(
        id = "mini",
        name = "Mini/Pickup",
        icon = "🛻",
        imageResId = R.drawable.vehicle_mini,
        description = "Small pickup trucks"
    )
    
    val MINI_PICKUP_SUBTYPES = listOf(
        TruckSubtype("dost", "Pickup Truck - Dost", 1.75),
        TruckSubtype("ace", "Mini Truck - Tata Ace", 0.875)
    )
    
    // 5. TRAILER
    val TRAILER = TruckCategory(
        id = "trailer",
        name = "Trailer",
        icon = "🚛",
        imageResId = R.drawable.vehicle_trailer,
        description = "Heavy trailer trucks"
    )
    
    val TRAILER_SUBTYPES = listOf(
        TruckSubtype("8_11", "8-11 Ton Trailer", 9.5),
        TruckSubtype("12_15", "12-15 Ton Trailer", 13.5),
        TruckSubtype("16_19", "16-19 Ton Trailer", 17.5),
        TruckSubtype("20_22", "20-22 Ton Trailer", 21.0),
        TruckSubtype("23_25", "23-25 Ton Trailer", 24.0),
        TruckSubtype("26_28", "26-28 Ton Trailer", 27.0),
        TruckSubtype("29_31", "29-31 Ton Trailer", 30.0),
        TruckSubtype("32_35", "32-35 Ton Trailer", 33.5),
        TruckSubtype("36_41", "36-41 Ton Trailer", 38.5),
        TruckSubtype("42_plus", "42+ Ton Trailer", 43.0)
    )
    
    // 6. TIPPER
    val TIPPER = TruckCategory(
        id = "tipper",
        name = "Tipper",
        icon = "🚜",
        imageResId = R.drawable.vehicle_tipper,
        description = "Tipping trucks for loose material"
    )
    
    val TIPPER_SUBTYPES = listOf(
        TruckSubtype("9_11", "9-11 Ton Tipper", 10.0),
        TruckSubtype("15_17", "15-17 Ton Tipper", 16.0),
        TruckSubtype("18_19", "18-19 Ton Tipper", 18.5),
        TruckSubtype("20_24", "20-24 Ton Tipper", 22.0),
        TruckSubtype("25", "25 Ton Tipper", 25.0),
        TruckSubtype("26_28", "26-28 Ton Tipper", 27.0),
        TruckSubtype("29", "29 Ton Tipper", 29.0),
        TruckSubtype("30", "30 Ton Tipper", 30.0)
    )
    
    // 7. TANKER - Named types (Water, Oil, Gas, Milk, Chemical)
    // Each type will have tonnage options in UI
    val TANKER = TruckCategory(
        id = "tanker",
        name = "Tanker",
        icon = "🛢️",
        imageResId = R.drawable.vehicle_tanker,
        description = "Liquid transport tankers"
    )
    
    val TANKER_SUBTYPES = listOf(
        TruckSubtype("8_11", "8-11 Ton Tanker", 9.5),
        TruckSubtype("12_15", "12-15 Ton Tanker", 13.5),
        TruckSubtype("16_20", "16-20 Ton Tanker", 18.0),
        TruckSubtype("21_25", "21-25 Ton Tanker", 23.0),
        TruckSubtype("26_29", "26-29 Ton Tanker", 27.5),
        TruckSubtype("30_31", "30-31 Ton Tanker", 30.5),
        TruckSubtype("32_35", "32-35 Ton Tanker", 33.5),
        TruckSubtype("36", "36 Ton Tanker", 36.0)
    )
    
    // 8. DUMPER - Tonnage-based like Bulker
    val DUMPER = TruckCategory(
        id = "dumper",
        name = "Dumper",
        icon = "🚛",
        imageResId = R.drawable.vehicle_dumper,
        description = "Dumper trucks for construction material"
    )
    
    val DUMPER_SUBTYPES = listOf(
        TruckSubtype("9_11", "9-11 Ton Dumper", 10.0),
        TruckSubtype("12_15", "12-15 Ton Dumper", 13.5),
        TruckSubtype("16_19", "16-19 Ton Dumper", 17.5),
        TruckSubtype("20_22", "20-22 Ton Dumper", 21.0),
        TruckSubtype("23_25", "23-25 Ton Dumper", 24.0),
        TruckSubtype("26_28", "26-28 Ton Dumper", 27.0),
        TruckSubtype("29_30", "29-30 Ton Dumper", 29.5),
        TruckSubtype("31_plus", "31+ Ton Dumper", 35.0)
    )

    // 9. BULKER
    val BULKER = TruckCategory(
        id = "bulker",
        name = "Bulker",
        icon = "🚛",
        imageResId = R.drawable.vehicle_bulker,
        description = "Bulk carriers for cement/ash"
    )

    val BULKER_SUBTYPES = listOf(
        TruckSubtype("20_22", "20-22 Ton Bulker", 21.0),
        TruckSubtype("23_25", "23-25 Ton Bulker", 24.0),
        TruckSubtype("26_28", "26-28 Ton Bulker", 27.0),
        TruckSubtype("29_31", "29-31 Ton Bulker", 30.0),
        TruckSubtype("32_plus", "32+ Ton Bulker", 35.0)
    )
    
    // Helper function to get all categories (9 categories)
    fun getAllCategories(): List<TruckCategory> = listOf(
        OPEN_TRUCK, CONTAINER, LCV, MINI_PICKUP, TRAILER, TIPPER, TANKER, DUMPER, BULKER
    )
    
    // Helper function to get subtypes for a category
    fun getSubtypesForCategory(categoryId: String): List<TruckSubtype> = when (categoryId) {
        "open" -> OPEN_TRUCK_SUBTYPES
        "container" -> CONTAINER_SUBTYPES
        "lcv" -> LCV_SUBTYPES
        "mini" -> MINI_PICKUP_SUBTYPES
        "trailer" -> TRAILER_SUBTYPES
        "tipper" -> TIPPER_SUBTYPES
        "tanker" -> TANKER_SUBTYPES
        "dumper" -> DUMPER_SUBTYPES
        "bulker" -> BULKER_SUBTYPES
        else -> emptyList()
    }
}
