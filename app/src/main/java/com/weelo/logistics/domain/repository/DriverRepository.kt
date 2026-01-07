package com.weelo.logistics.domain.repository

import com.weelo.logistics.core.utils.Resource
import com.weelo.logistics.domain.model.CreateDriverRequest
import com.weelo.logistics.domain.model.DriverDomain
import com.weelo.logistics.domain.model.DriverListResponse
import kotlinx.coroutines.flow.Flow

/**
 * Driver Repository Interface
 * Defines contract for driver data operations
 */
interface DriverRepository {
    
    suspend fun createDriver(
        transporterId: String,
        request: CreateDriverRequest
    ): Flow<Resource<DriverDomain>>
    
    suspend fun getDriverList(
        transporterId: String,
        page: Int = 1,
        limit: Int = 20,
        status: String? = null,
        search: String? = null
    ): Flow<Resource<DriverListResponse>>
    
    suspend fun getDriverDetails(
        transporterId: String,
        driverId: String
    ): Flow<Resource<DriverDomain>>
    
    suspend fun updateDriver(
        transporterId: String,
        driverId: String,
        request: CreateDriverRequest
    ): Flow<Resource<DriverDomain>>
    
    suspend fun deleteDriver(
        transporterId: String,
        driverId: String
    ): Flow<Resource<Boolean>>
    
    suspend fun assignVehicle(
        transporterId: String,
        driverId: String,
        vehicleId: String
    ): Flow<Resource<DriverDomain>>
}
