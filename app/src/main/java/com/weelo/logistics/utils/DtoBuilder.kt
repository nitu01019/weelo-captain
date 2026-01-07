package com.weelo.logistics.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DTO (Data Transfer Object) Builder
 * 
 * SECURITY: Send only required fields to backend
 * PERFORMANCE: Reduces payload size for scalability
 * 
 * Purpose:
 * - Minimize data transmission
 * - Prevent accidental data leakage
 * - Optimize bandwidth for millions of users
 * - Clear contract between frontend and backend
 * 
 * Architecture:
 * - Separate DTOs for requests and responses
 * - Validation before building
 * - Type-safe construction
 * - Backend-friendly structure
 */

/**
 * Base DTO interface
 */
interface RequestDto {
    fun validate(): ValidationResult
    fun toMap(): Map<String, Any?>
}

/**
 * Authentication DTOs
 */
@Serializable
data class SendOtpRequest(
    val phone: String,
    val role: String? = null, // "DRIVER" or "TRANSPORTER"
    val deviceId: String? = null
) : RequestDto {
    override fun validate(): ValidationResult {
        return InputValidator.validatePhoneNumber(phone)
    }
    
    override fun toMap(): Map<String, Any?> = buildMap {
        put("phone", phone)
        role?.let { put("role", it) }
        deviceId?.let { put("deviceId", it) }
    }
    
    companion object {
        fun build(phone: String, role: String? = null): Result<SendOtpRequest> {
            val trimmed = phone.trim()
            val validation = InputValidator.validatePhoneNumber(trimmed)
            
            return if (validation.isValid) {
                Result.success(SendOtpRequest(trimmed, role))
            } else {
                Result.failure(IllegalArgumentException(validation.errorMessage))
            }
        }
    }
}

@Serializable
data class VerifyOtpRequest(
    val phone: String,
    val otp: String,
    val deviceId: String? = null
) : RequestDto {
    override fun validate(): ValidationResult {
        val phoneValidation = InputValidator.validatePhoneNumber(phone)
        if (!phoneValidation.isValid) return phoneValidation
        
        return InputValidator.validateOTP(otp)
    }
    
    override fun toMap(): Map<String, Any?> = buildMap {
        put("phone", phone)
        put("otp", otp)
        deviceId?.let { put("deviceId", it) }
    }
    
    companion object {
        fun build(phone: String, otp: String): Result<VerifyOtpRequest> {
            val trimmedPhone = phone.trim()
            val trimmedOtp = otp.trim()
            
            val request = VerifyOtpRequest(trimmedPhone, trimmedOtp)
            val validation = request.validate()
            
            return if (validation.isValid) {
                Result.success(request)
            } else {
                Result.failure(IllegalArgumentException(validation.errorMessage))
            }
        }
    }
}

/**
 * Driver Management DTOs
 */
@Serializable
data class AddDriverRequest(
    val name: String,
    val phone: String,
    val licenseNumber: String,
    val email: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null
) : RequestDto {
    override fun validate(): ValidationResult {
        InputValidator.validateName(name).let { if (!it.isValid) return it }
        InputValidator.validatePhoneNumber(phone).let { if (!it.isValid) return it }
        InputValidator.validateDriverLicense(licenseNumber).let { if (!it.isValid) return it }
        
        // Optional fields
        email?.let { if (it.isNotBlank()) {
            InputValidator.validateEmail(it).let { result -> if (!result.isValid) return result }
        }}
        
        return ValidationResult.Success
    }
    
    override fun toMap(): Map<String, Any?> = buildMap {
        put("name", name.trim())
        put("phone", phone.trim())
        put("licenseNumber", licenseNumber.trim().uppercase())
        email?.takeIf { it.isNotBlank() }?.let { put("email", it.trim().lowercase()) }
        address?.takeIf { it.isNotBlank() }?.let { put("address", it.trim()) }
        city?.takeIf { it.isNotBlank() }?.let { put("city", it.trim()) }
        state?.takeIf { it.isNotBlank() }?.let { put("state", it.trim()) }
        pincode?.takeIf { it.isNotBlank() }?.let { put("pincode", it.trim()) }
    }
    
    companion object {
        fun build(
            name: String,
            phone: String,
            licenseNumber: String,
            email: String? = null,
            address: String? = null,
            city: String? = null,
            state: String? = null,
            pincode: String? = null
        ): Result<AddDriverRequest> {
            val request = AddDriverRequest(
                name = name.trim(),
                phone = phone.trim(),
                licenseNumber = licenseNumber.trim().uppercase(),
                email = email?.trim()?.lowercase(),
                address = address?.trim(),
                city = city?.trim(),
                state = state?.trim(),
                pincode = pincode?.trim()
            )
            
            val validation = request.validate()
            return if (validation.isValid) {
                Result.success(request)
            } else {
                Result.failure(IllegalArgumentException(validation.errorMessage))
            }
        }
    }
}

/**
 * Vehicle Management DTOs
 */
@Serializable
data class AddVehicleRequest(
    val vehicleNumber: String,
    val vehicleType: String, // "CONTAINER", "OPEN", "TANKER", etc.
    val capacity: String,
    val driverId: String? = null,
    val registrationDate: String? = null,
    val insuranceExpiry: String? = null
) : RequestDto {
    override fun validate(): ValidationResult {
        return InputValidator.validateVehicleNumber(vehicleNumber)
    }
    
    override fun toMap(): Map<String, Any?> = buildMap {
        put("vehicleNumber", vehicleNumber.trim().uppercase())
        put("vehicleType", vehicleType)
        put("capacity", capacity)
        driverId?.let { put("driverId", it) }
        registrationDate?.let { put("registrationDate", it) }
        insuranceExpiry?.let { put("insuranceExpiry", it) }
    }
    
    companion object {
        fun build(
            vehicleNumber: String,
            vehicleType: String,
            capacity: String,
            driverId: String? = null
        ): Result<AddVehicleRequest> {
            val request = AddVehicleRequest(
                vehicleNumber = vehicleNumber.trim().uppercase(),
                vehicleType = vehicleType,
                capacity = capacity,
                driverId = driverId
            )
            
            val validation = request.validate()
            return if (validation.isValid) {
                Result.success(request)
            } else {
                Result.failure(IllegalArgumentException(validation.errorMessage))
            }
        }
    }
}

/**
 * Trip Management DTOs
 */
@Serializable
data class CreateTripRequest(
    val origin: String,
    val destination: String,
    val vehicleType: String,
    val vehicleSubtype: String? = null,
    val loadWeight: String? = null,
    val loadDescription: String? = null,
    val pickupDate: String? = null,
    val deliveryDate: String? = null,
    val price: String? = null
) : RequestDto {
    override fun validate(): ValidationResult {
        if (origin.isBlank()) return ValidationResult.Error("Origin is required")
        if (destination.isBlank()) return ValidationResult.Error("Destination is required")
        if (vehicleType.isBlank()) return ValidationResult.Error("Vehicle type is required")
        
        return ValidationResult.Success
    }
    
    override fun toMap(): Map<String, Any?> = buildMap {
        put("origin", DataSanitizer.sanitizeForApi(origin))
        put("destination", DataSanitizer.sanitizeForApi(destination))
        put("vehicleType", vehicleType)
        vehicleSubtype?.let { put("vehicleSubtype", it) }
        loadWeight?.let { put("loadWeight", it) }
        loadDescription?.let { put("loadDescription", DataSanitizer.sanitizeForApi(it)) }
        pickupDate?.let { put("pickupDate", it) }
        deliveryDate?.let { put("deliveryDate", it) }
        price?.let { put("price", it) }
    }
    
    companion object {
        fun build(
            origin: String,
            destination: String,
            vehicleType: String,
            vehicleSubtype: String? = null,
            loadWeight: String? = null,
            loadDescription: String? = null
        ): Result<CreateTripRequest> {
            val request = CreateTripRequest(
                origin = origin.trim(),
                destination = destination.trim(),
                vehicleType = vehicleType,
                vehicleSubtype = vehicleSubtype,
                loadWeight = loadWeight,
                loadDescription = loadDescription?.trim()
            )
            
            val validation = request.validate()
            return if (validation.isValid) {
                Result.success(request)
            } else {
                Result.failure(IllegalArgumentException(validation.errorMessage))
            }
        }
    }
}

/**
 * Location Update DTO (for GPS tracking)
 */
@Serializable
data class LocationUpdateRequest(
    val tripId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val accuracy: Float? = null,
    val speed: Float? = null,
    val bearing: Float? = null
) : RequestDto {
    override fun validate(): ValidationResult {
        if (tripId.isBlank()) return ValidationResult.Error("Trip ID is required")
        if (latitude < -90 || latitude > 90) return ValidationResult.Error("Invalid latitude")
        if (longitude < -180 || longitude > 180) return ValidationResult.Error("Invalid longitude")
        
        return ValidationResult.Success
    }
    
    override fun toMap(): Map<String, Any?> = buildMap {
        put("tripId", tripId)
        put("latitude", latitude)
        put("longitude", longitude)
        put("timestamp", timestamp)
        accuracy?.let { put("accuracy", it) }
        speed?.let { put("speed", it) }
        bearing?.let { put("bearing", it) }
    }
}

/**
 * Helper to convert DTO to JSON string
 */
fun RequestDto.toJson(): String {
    val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false // Don't send null fields
    }
    return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), 
        kotlinx.serialization.json.JsonObject(toMap().mapValues { 
            kotlinx.serialization.json.JsonPrimitive(it.value?.toString())
        })
    )
}

/**
 * BACKEND INTEGRATION GUIDE:
 * 
 * 1. These DTOs define the contract between app and server
 * 2. Backend should validate ALL incoming data (never trust client)
 * 3. Backend should use similar DTOs for type safety
 * 4. Only fields in toMap() are sent to backend
 * 5. Use validation() before sending
 * 
 * Example Usage:
 * ```kotlin
 * // Build DTO with validation
 * val dtoResult = AddDriverRequest.build(
 *     name = name,
 *     phone = phone,
 *     licenseNumber = license
 * )
 * 
 * dtoResult.onSuccess { dto ->
 *     // Send only required fields
 *     val payload = dto.toMap()
 *     api.addDriver(transporterId, payload)
 * }.onFailure { error ->
 *     // Show validation error
 *     showError(error.message)
 * }
 * ```
 * 
 * BENEFITS:
 * - ✅ Type-safe
 * - ✅ Validated before sending
 * - ✅ Only required fields sent
 * - ✅ Reduced payload size
 * - ✅ Clear API contract
 * - ✅ Easy to test
 * - ✅ Scalable for millions
 */
