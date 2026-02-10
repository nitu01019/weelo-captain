package com.weelo.logistics.data.repository

import android.content.Context
import android.net.Uri
import com.weelo.logistics.data.api.CompleteDriverProfileResponse
import com.weelo.logistics.data.api.ProfileApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream

/**
 * =============================================================================
 * DRIVER PROFILE REPOSITORY
 * =============================================================================
 * 
 * Handles driver profile completion with photo uploads.
 * 
 * Features:
 * - Converts URIs to MultipartBody.Part for API upload
 * - Handles file operations efficiently
 * - Integrates with backend S3 upload API
 * 
 * Scalability:
 * - Uses streaming for large files
 * - Efficient memory management
 * - Optimized for concurrent uploads
 * 
 * =============================================================================
 */
class DriverProfileRepository(
    private val profileApiService: ProfileApiService,
    private val context: Context
) {
    
    /**
     * Complete driver profile with photos
     * 
     * @param licenseNumber Driver's license number
     * @param vehicleType Preferred vehicle type
     * @param address Optional address
     * @param language Preferred language (default: "en")
     * @param driverPhotoUri URI of driver's photo
     * @param licenseFrontUri URI of license front photo
     * @param licenseBackUri URI of license back photo
     * @return API response with profile data
     */
    suspend fun completeDriverProfile(
        licenseNumber: String,
        vehicleType: String,
        address: String? = null,
        language: String = "en",
        driverPhotoUri: Uri,
        licenseFrontUri: Uri,
        licenseBackUri: Uri
    ): Response<CompleteDriverProfileResponse> {
        
        timber.log.Timber.d("Converting URIs to multipart...")
        
        // Convert URIs to MultipartBody.Part
        val driverPhotoPart = uriToMultipartPart(driverPhotoUri, "driverPhoto")
        timber.log.Timber.d("Driver photo converted")
        
        val licenseFrontPart = uriToMultipartPart(licenseFrontUri, "licenseFront")
        timber.log.Timber.d("License front converted")
        
        val licenseBackPart = uriToMultipartPart(licenseBackUri, "licenseBack")
        timber.log.Timber.d("License back converted")
        
        // Create request bodies for text fields
        val licenseNumberBody = licenseNumber.toRequestBody("text/plain".toMediaTypeOrNull())
        val vehicleTypeBody = vehicleType.toRequestBody("text/plain".toMediaTypeOrNull())
        val addressBody = address?.toRequestBody("text/plain".toMediaTypeOrNull())
        val languageBody = language.toRequestBody("text/plain".toMediaTypeOrNull())
        
        timber.log.Timber.d("Making API call to complete-profile endpoint...")
        timber.log.Timber.d("Token present: ${com.weelo.logistics.data.remote.RetrofitClient.getAccessToken() != null}")
        
        // Make API call
        return profileApiService.completeDriverProfile(
            licenseNumber = licenseNumberBody,
            vehicleType = vehicleTypeBody,
            address = addressBody,
            language = languageBody,
            driverPhoto = driverPhotoPart,
            licenseFront = licenseFrontPart,
            licenseBack = licenseBackPart
        )
    }
    
    /**
     * Convert URI to MultipartBody.Part
     * 
     * Handles content resolver URIs and file URIs efficiently.
     */
    private fun uriToMultipartPart(uri: Uri, fieldName: String): MultipartBody.Part {
        try {
            timber.log.Timber.d("Converting $fieldName from URI: $uri")
            
            // Create a temporary file from URI
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val bytesCopied = inputStream.copyTo(outputStream)
                    timber.log.Timber.d("$fieldName: Copied $bytesCopied bytes to ${file.name}")
                }
            } ?: throw IllegalStateException("Could not open input stream for URI: $uri")
            
            if (!file.exists() || file.length() == 0L) {
                throw IllegalStateException("File is empty or does not exist: ${file.name}")
            }
            
            // Create request body
            val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            
            // Create multipart part
            return MultipartBody.Part.createFormData(
                fieldName,
                file.name,
                requestBody
            )
        } catch (e: Exception) {
            timber.log.Timber.e("Error converting $fieldName: ${e.message}")
            throw e
        }
    }
    
    /**
     * Clean up temporary files
     */
    fun cleanupTempFiles() {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("upload_")) {
                file.delete()
            }
        }
    }
}
