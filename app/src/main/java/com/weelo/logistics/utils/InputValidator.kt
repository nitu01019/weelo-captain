package com.weelo.logistics.utils

/**
 * Input Validation Utility
 * 
 * Provides centralized validation for all user inputs
 * Prevents injection attacks, XSS, and malformed data
 * 
 * SECURITY STEP 1: Max/min length limits on all inputs
 * Backend-friendly: Clear error messages for API integration
 */
object InputValidator {
    
    // Regex Patterns (Security Step 2: Pattern validation)
    object Patterns {
        // Indian phone number: Starts with 6-9, exactly 10 digits
        val INDIAN_PHONE = Regex("^[6-9]\\d{9}$")
        
        // International phone with country code
        val INTERNATIONAL_PHONE = Regex("^\\+\\d{1,3}\\d{10,14}$")
        
        // Email RFC 5322 compliant
        val EMAIL = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        
        // OTP: Exactly 6 digits
        val OTP = Regex("^\\d{6}$")
        
        // Indian Driving License: State code + digits
        // Format: HR0619850034958, DL1420110012345
        val INDIAN_LICENSE = Regex("^[A-Z]{2}\\d{13,15}$")
        
        // Indian Vehicle Registration: State code + district + series + number
        // Format: MH12AB1234, DL01CA9999
        val VEHICLE_REGISTRATION = Regex("^[A-Z]{2}\\d{1,2}[A-Z]{1,3}\\d{4}$")
        
        // Indian Pincode: 6 digits
        val PINCODE = Regex("^[1-9]\\d{5}$")
        
        // Name: Letters, spaces, dots, apostrophes, hyphens
        val NAME = Regex("^[a-zA-Z\\s.'-]+$")
        
        // Alphanumeric with spaces
        val ALPHANUMERIC_SPACE = Regex("^[a-zA-Z0-9\\s]+$")
        
        // Numbers only
        val NUMBERS_ONLY = Regex("^\\d+$")
    }
    
    // Field Length Limits (Security: Prevent overflow attacks)
    object Limits {
        // Phone Numbers
        const val PHONE_MIN_LENGTH = 10
        const val PHONE_MAX_LENGTH = 15
        
        // Names
        const val NAME_MIN_LENGTH = 2
        const val NAME_MAX_LENGTH = 100
        
        // Email
        const val EMAIL_MAX_LENGTH = 254 // RFC 5321
        
        // License Numbers
        const val LICENSE_MIN_LENGTH = 8
        const val LICENSE_MAX_LENGTH = 20
        
        // OTP
        const val OTP_LENGTH = 6
        
        // Address
        const val ADDRESS_MIN_LENGTH = 5
        const val ADDRESS_MAX_LENGTH = 500
        
        // City/State
        const val CITY_MIN_LENGTH = 2
        const val CITY_MAX_LENGTH = 100
        
        // Pincode
        const val PINCODE_LENGTH = 6
        
        // Vehicle Registration
        const val VEHICLE_REG_MIN_LENGTH = 8
        const val VEHICLE_REG_MAX_LENGTH = 15
        
        // General Text
        const val TEXT_MIN_LENGTH = 1
        const val TEXT_MAX_LENGTH = 1000
    }
    
    // Phone number validation with regex pattern (Security Step 2)
    fun validatePhoneNumber(phone: String): ValidationResult {
        val trimmedPhone = phone.trim()
        
        return when {
            trimmedPhone.isEmpty() -> 
                ValidationResult.Error("Phone number is required")
            trimmedPhone.length < Limits.PHONE_MIN_LENGTH -> 
                ValidationResult.Error("Phone number must be at least ${Limits.PHONE_MIN_LENGTH} digits")
            trimmedPhone.length > Limits.PHONE_MAX_LENGTH -> 
                ValidationResult.Error("Phone number cannot exceed ${Limits.PHONE_MAX_LENGTH} digits")
            // Regex validation for Indian format
            trimmedPhone.matches(Patterns.INDIAN_PHONE) -> 
                ValidationResult.Success
            // Check international format with +
            trimmedPhone.startsWith("+") && trimmedPhone.substring(1).matches(Patterns.INTERNATIONAL_PHONE) -> 
                ValidationResult.Success
            !trimmedPhone.all { it.isDigit() } -> 
                ValidationResult.Error("Phone number must contain only digits")
            else -> 
                ValidationResult.Error("Invalid phone number format. Use 10 digits starting with 6-9")
        }
    }
    
    // OTP validation with regex pattern (Security Step 2)
    fun validateOTP(otp: String): ValidationResult {
        val trimmedOTP = otp.trim()
        
        return when {
            trimmedOTP.isEmpty() -> 
                ValidationResult.Error("OTP is required")
            trimmedOTP.length != Limits.OTP_LENGTH -> 
                ValidationResult.Error("OTP must be exactly ${Limits.OTP_LENGTH} digits")
            // Regex validation for 6 digits
            !trimmedOTP.matches(Patterns.OTP) -> 
                ValidationResult.Error("OTP must be 6 digits only")
            else -> 
                ValidationResult.Success
        }
    }
    
    // Name validation with regex pattern (Security Step 2)
    fun validateName(name: String): ValidationResult {
        val trimmedName = name.trim()
        
        return when {
            trimmedName.isEmpty() -> 
                ValidationResult.Error("Name is required")
            trimmedName.length < Limits.NAME_MIN_LENGTH -> 
                ValidationResult.Error("Name must be at least ${Limits.NAME_MIN_LENGTH} characters")
            trimmedName.length > Limits.NAME_MAX_LENGTH -> 
                ValidationResult.Error("Name cannot exceed ${Limits.NAME_MAX_LENGTH} characters")
            // Regex validation for name pattern
            !trimmedName.matches(Patterns.NAME) -> 
                ValidationResult.Error("Name can only contain letters, spaces, dots, and hyphens")
            else -> 
                ValidationResult.Success
        }
    }
    
    // Driver license validation with regex pattern (Security Step 2)
    fun validateDriverLicense(license: String): ValidationResult {
        val sanitized = license.trim().uppercase().replace(" ", "").replace("-", "")
        
        return when {
            sanitized.isEmpty() -> 
                ValidationResult.Error("License number is required")
            sanitized.length < Limits.LICENSE_MIN_LENGTH -> 
                ValidationResult.Error("License must be at least ${Limits.LICENSE_MIN_LENGTH} characters")
            sanitized.length > Limits.LICENSE_MAX_LENGTH -> 
                ValidationResult.Error("License cannot exceed ${Limits.LICENSE_MAX_LENGTH} characters")
            // Regex validation for Indian license format
            sanitized.matches(Patterns.INDIAN_LICENSE) -> 
                ValidationResult.Success
            // Allow other formats but with basic validation
            sanitized.matches(Regex("^[A-Z0-9]{${Limits.LICENSE_MIN_LENGTH},${Limits.LICENSE_MAX_LENGTH}}$")) -> 
                ValidationResult.Success
            else -> 
                ValidationResult.Error("Invalid license format. Use format like: HR0619850034958")
        }
    }
    
    // Email validation with regex pattern (Security Step 2)
    fun validateEmail(email: String): ValidationResult {
        if (email.isEmpty()) return ValidationResult.Success // Optional field
        
        val trimmedEmail = email.trim().lowercase()
        
        return when {
            trimmedEmail.length > Limits.EMAIL_MAX_LENGTH -> 
                ValidationResult.Error("Email cannot exceed ${Limits.EMAIL_MAX_LENGTH} characters")
            // Regex validation RFC 5322 compliant
            !trimmedEmail.matches(Patterns.EMAIL) ->
                ValidationResult.Error("Invalid email format. Use format: name@domain.com")
            else -> 
                ValidationResult.Success
        }
    }
    
    // Vehicle number validation with regex pattern (Security Step 2)
    fun validateVehicleNumber(vehicleNumber: String): ValidationResult {
        val sanitized = vehicleNumber.trim().uppercase().replace(" ", "").replace("-", "")
        
        return when {
            sanitized.isEmpty() -> 
                ValidationResult.Error("Vehicle number is required")
            sanitized.length < Limits.VEHICLE_REG_MIN_LENGTH -> 
                ValidationResult.Error("Vehicle number must be at least ${Limits.VEHICLE_REG_MIN_LENGTH} characters")
            sanitized.length > Limits.VEHICLE_REG_MAX_LENGTH -> 
                ValidationResult.Error("Vehicle number cannot exceed ${Limits.VEHICLE_REG_MAX_LENGTH} characters")
            // Regex validation for Indian vehicle format
            !sanitized.matches(Patterns.VEHICLE_REGISTRATION) -> 
                ValidationResult.Error("Invalid format. Use: MH12AB1234 (State+District+Series+Number)")
            else -> 
                ValidationResult.Success
        }
    }
    
    // Location validation with length limits
    fun validateLocation(location: String): ValidationResult {
        val sanitized = sanitizeInput(location)
        
        return when {
            sanitized.isEmpty() -> ValidationResult.Error("Location is required")
            sanitized.length < Limits.CITY_MIN_LENGTH -> 
                ValidationResult.Error("Location must be at least ${Limits.CITY_MIN_LENGTH} characters")
            sanitized.length > Limits.CITY_MAX_LENGTH -> 
                ValidationResult.Error("Location cannot exceed ${Limits.CITY_MAX_LENGTH} characters")
            else -> ValidationResult.Success
        }
    }
    
    // Address validation with length limits
    fun validateAddress(address: String): ValidationResult {
        val trimmedAddress = address.trim()
        
        return when {
            trimmedAddress.isBlank() -> ValidationResult.Error("Address is required")
            trimmedAddress.length < Limits.ADDRESS_MIN_LENGTH -> 
                ValidationResult.Error("Address must be at least ${Limits.ADDRESS_MIN_LENGTH} characters")
            trimmedAddress.length > Limits.ADDRESS_MAX_LENGTH -> 
                ValidationResult.Error("Address cannot exceed ${Limits.ADDRESS_MAX_LENGTH} characters")
            else -> ValidationResult.Success
        }
    }
    
    // Pincode validation with regex pattern (Security Step 2)
    fun validatePincode(pincode: String): ValidationResult {
        val trimmedPincode = pincode.trim()
        
        return when {
            trimmedPincode.isBlank() -> 
                ValidationResult.Error("Pincode is required")
            trimmedPincode.length != Limits.PINCODE_LENGTH -> 
                ValidationResult.Error("Pincode must be exactly ${Limits.PINCODE_LENGTH} digits")
            // Regex validation - cannot start with 0
            !trimmedPincode.matches(Patterns.PINCODE) -> 
                ValidationResult.Error("Invalid pincode. Must be 6 digits, cannot start with 0")
            else -> 
                ValidationResult.Success
        }
    }
    
    // Sanitize input - remove potentially dangerous characters
    fun sanitizeInput(input: String): String {
        return input
            .trim()
            .replace(Regex("[<>\"'&]"), "") // Remove HTML/Script injection chars
            .take(500) // Max length limit
    }
    
    // Generic string validation with length constraints
    fun validateString(
        value: String,
        fieldName: String,
        minLength: Int = 1,
        maxLength: Int = 500,
        allowSpecialChars: Boolean = false
    ): ValidationResult {
        val sanitized = sanitizeInput(value)
        
        return when {
            sanitized.isEmpty() -> ValidationResult.Error("$fieldName is required")
            sanitized.length < minLength -> 
                ValidationResult.Error("$fieldName must be at least $minLength characters")
            sanitized.length > maxLength -> 
                ValidationResult.Error("$fieldName is too long (max $maxLength characters)")
            !allowSpecialChars && sanitized.matches(Regex(".*[<>\"'&;].*")) ->
                ValidationResult.Error("$fieldName contains invalid characters")
            else -> ValidationResult.Success
        }
    }
    
    // Numeric validation with range limits
    fun validateNumeric(
        value: String, 
        fieldName: String, 
        min: Int? = null, 
        max: Int? = null
    ): ValidationResult {
        return try {
            val number = value.toInt()
            when {
                min != null && number < min -> 
                    ValidationResult.Error("$fieldName must be at least $min")
                max != null && number > max -> 
                    ValidationResult.Error("$fieldName must be at most $max")
                else -> ValidationResult.Success
            }
        } catch (e: NumberFormatException) {
            ValidationResult.Error("$fieldName must be a valid number")
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
    
    val isValid: Boolean
        get() = this is Success
    
    val errorMessage: String?
        get() = (this as? Error)?.message
}
