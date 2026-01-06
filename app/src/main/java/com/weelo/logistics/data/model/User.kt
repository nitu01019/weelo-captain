package com.weelo.logistics.data.model

/**
 * User model - Represents a user in the system
 * Can have one or both roles (Transporter, Driver)
 */
data class User(
    val id: String,
    val name: String,
    val mobileNumber: String,
    val email: String? = null,
    val roles: List<UserRole>,
    val profileImageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

/**
 * User Role - Defines what the user can do
 */
enum class UserRole {
    TRANSPORTER,    // Fleet owner/manager
    DRIVER          // Vehicle driver
}

/**
 * Transporter Profile - Additional details for transporter role
 */
data class TransporterProfile(
    val userId: String,
    val companyName: String? = null,
    val gstNumber: String? = null,
    val address: String? = null,
    val totalVehicles: Int = 0,
    val totalDrivers: Int = 0,
    val verificationStatus: VerificationStatus = VerificationStatus.PENDING
)

/**
 * Driver Profile - Additional details for driver role
 */
data class DriverProfile(
    val userId: String,
    val licenseNumber: String,
    val licenseExpiryDate: Long,
    val experience: Int = 0, // in years
    val assignedTransporterId: String? = null,
    val isAvailable: Boolean = true,
    val rating: Float = 0f,
    val totalTrips: Int = 0,
    val verificationStatus: VerificationStatus = VerificationStatus.PENDING
)

/**
 * Verification Status
 */
enum class VerificationStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
