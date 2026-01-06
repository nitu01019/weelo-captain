package com.weelo.logistics.utils

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Security Utilities for Weelo Logistics
 * Provides encryption, hashing, and token generation
 * For scalability to millions of users
 */
object SecurityUtils {
    
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val KEY_SIZE = 256
    
    /**
     * Generate secure random token
     * Use for session tokens, OTP, etc.
     */
    fun generateSecureToken(length: Int = 32): String {
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }
    
    /**
     * Hash password using SHA-256
     * IMPORTANT: In production, use bcrypt or Argon2
     */
    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    /**
     * Verify password against hash
     */
    fun verifyPassword(password: String, hash: String): Boolean {
        return hashPassword(password) == hash
    }
    
    /**
     * Generate AES encryption key
     */
    fun generateAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(KEY_SIZE)
        return keyGenerator.generateKey()
    }
    
    /**
     * Encrypt sensitive data (e.g., license numbers, addresses)
     */
    fun encryptData(data: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        val ivSpec = IvParameterSpec(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encrypted = cipher.doFinal(data.toByteArray())
        
        // Combine IV and encrypted data
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    /**
     * Decrypt sensitive data
     */
    fun decryptData(encryptedData: String, secretKey: SecretKey): String {
        val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 16)
        val encrypted = combined.copyOfRange(16, combined.size)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        
        val decrypted = cipher.doFinal(encrypted)
        return String(decrypted)
    }
    
    /**
     * Sanitize input to prevent injection attacks
     */
    fun sanitizeInput(input: String): String {
        return input
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
            .trim()
    }
    
    /**
     * Validate mobile number format (Indian)
     */
    fun isValidMobileNumber(mobile: String): Boolean {
        val cleanMobile = mobile.replace("+91", "").replace(" ", "").replace("-", "")
        return cleanMobile.matches(Regex("^[6-9]\\d{9}$"))
    }
    
    /**
     * Validate email format
     */
    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    /**
     * Generate OTP (6 digits)
     */
    fun generateOTP(): String {
        val random = SecureRandom()
        val otp = random.nextInt(900000) + 100000 // 100000 to 999999
        return otp.toString()
    }
    
    /**
     * Rate limiting helper
     * Track attempts to prevent brute force
     */
    class RateLimiter(private val maxAttempts: Int, private val timeWindowMs: Long) {
        private val attempts = mutableMapOf<String, MutableList<Long>>()
        
        fun isAllowed(identifier: String): Boolean {
            val now = System.currentTimeMillis()
            val userAttempts = attempts.getOrPut(identifier) { mutableListOf() }
            
            // Remove old attempts outside time window
            userAttempts.removeAll { it < now - timeWindowMs }
            
            if (userAttempts.size >= maxAttempts) {
                return false
            }
            
            userAttempts.add(now)
            return true
        }
        
        fun reset(identifier: String) {
            attempts.remove(identifier)
        }
    }
}

/**
 * Input Validator for all forms
 * Ensures data quality and security
 */
object InputValidator {
    
    fun validateName(name: String): ValidationResult {
        return when {
            name.isBlank() -> ValidationResult.Error("Name is required")
            name.length < 2 -> ValidationResult.Error("Name must be at least 2 characters")
            name.length > 100 -> ValidationResult.Error("Name is too long")
            !name.matches(Regex("^[a-zA-Z\\s.]+$")) -> ValidationResult.Error("Name contains invalid characters")
            else -> ValidationResult.Success
        }
    }
    
    fun validateMobileNumber(mobile: String): ValidationResult {
        val cleanMobile = mobile.replace("+91", "").replace(" ", "").replace("-", "")
        return when {
            cleanMobile.isBlank() -> ValidationResult.Error("Mobile number is required")
            !SecurityUtils.isValidMobileNumber(cleanMobile) -> ValidationResult.Error("Invalid mobile number")
            else -> ValidationResult.Success
        }
    }
    
    fun validateLicenseNumber(license: String): ValidationResult {
        return when {
            license.isBlank() -> ValidationResult.Success // Optional field
            license.length < 10 -> ValidationResult.Error("Invalid license number")
            !license.matches(Regex("^[A-Z]{2}\\d{2}\\d{11}$")) -> ValidationResult.Error("License format: AB0120110012345")
            else -> ValidationResult.Success
        }
    }
    
    fun validateCompanyName(company: String): ValidationResult {
        return when {
            company.isBlank() -> ValidationResult.Error("Company name is required")
            company.length < 2 -> ValidationResult.Error("Company name too short")
            company.length > 200 -> ValidationResult.Error("Company name too long")
            else -> ValidationResult.Success
        }
    }
    
    fun validateCity(city: String): ValidationResult {
        return when {
            city.isBlank() -> ValidationResult.Error("City is required")
            city.length < 2 -> ValidationResult.Error("City name too short")
            !city.matches(Regex("^[a-zA-Z\\s]+$")) -> ValidationResult.Error("Invalid city name")
            else -> ValidationResult.Success
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}
