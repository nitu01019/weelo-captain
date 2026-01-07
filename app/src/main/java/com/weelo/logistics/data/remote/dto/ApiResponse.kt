package com.weelo.logistics.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Generic API Response wrapper
 * Consistent response structure for all endpoints
 */
data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: T,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("error")
    val error: ApiError? = null,
    
    @SerializedName("timestamp")
    val timestamp: String? = null
)

data class ApiError(
    @SerializedName("code")
    val code: String,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("details")
    val details: Map<String, Any>? = null
)

/**
 * Paginated response wrapper
 */
data class PaginatedResponse<T>(
    @SerializedName("items")
    val items: List<T>,
    
    @SerializedName("pagination")
    val pagination: PaginationInfo
)

data class PaginationInfo(
    @SerializedName("currentPage")
    val currentPage: Int,
    
    @SerializedName("totalPages")
    val totalPages: Int,
    
    @SerializedName("totalItems")
    val totalItems: Int,
    
    @SerializedName("itemsPerPage")
    val itemsPerPage: Int
)
