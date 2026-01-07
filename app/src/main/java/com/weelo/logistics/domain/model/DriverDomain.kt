package com.weelo.logistics.domain.model

/**
 * Domain model for Driver
 * Clean domain representation for business logic
 */
data class DriverDomain(
    val id: String,
    val name: String,
    val mobileNumber: String,
    val email: String? = null,
    val licenseNumber: String,
    val licenseExpiry: String,
    val dateOfBirth: String? = null,
    val address: Address? = null,
    val emergencyContact: EmergencyContact? = null,
    val status: DriverStatus,
    val rating: Float = 0f,
    val totalTrips: Int = 0,
    val completedTrips: Int = 0,
    val cancelledTrips: Int = 0,
    val isAvailable: Boolean = true,
    val currentVehicleId: String? = null,
    val profileImageUrl: String? = null,
    val documents: DriverDocuments? = null,
    val performance: DriverPerformance? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Address(
    val street: String,
    val city: String,
    val state: String,
    val pincode: String
)

data class EmergencyContact(
    val name: String,
    val relation: String,
    val mobileNumber: String
)

data class DriverDocuments(
    val licenseImageUrl: String? = null,
    val aadhaarImageUrl: String? = null,
    val photoUrl: String? = null
)

data class DriverPerformance(
    val onTimeDeliveryRate: Float = 0f,
    val avgTripTime: Long = 0, // in minutes
    val totalDistance: Double = 0.0, // in km
    val lastTripDate: Long? = null
)

enum class DriverStatus {
    ACTIVE,
    ON_TRIP,
    INACTIVE,
    SUSPENDED,
    PENDING_VERIFICATION
}

/**
 * Request model for creating driver
 */
data class CreateDriverRequest(
    val name: String,
    val mobileNumber: String,
    val email: String? = null,
    val licenseNumber: String,
    val licenseExpiry: String,
    val dateOfBirth: String? = null,
    val address: Address? = null,
    val emergencyContact: EmergencyContact? = null,
    val documents: DriverDocuments? = null
)

/**
 * Response wrapper for paginated driver list
 */
data class DriverListResponse(
    val drivers: List<DriverDomain>,
    val pagination: Pagination
)

data class Pagination(
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
    val itemsPerPage: Int
)
