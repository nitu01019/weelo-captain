# 🔌 Backend Integration Guide - Screen by Screen Mapping

## 📋 Document Purpose
This guide provides **exact line-by-line integration points** for backend developers to connect APIs to the frontend screens. Each section shows:
- Screen file path and line numbers
- API endpoints needed
- Request/Response format
- Integration workflow
- Code examples

---

## 📱 Table of Contents - Screens & APIs

### Authentication Screens
1. [Login Screen](#1-login-screen) - Authentication API
2. [Signup Screen](#2-signup-screen) - Registration API
3. [OTP Verification](#3-otp-verification-screen) - OTP APIs

### Transporter Screens
4. [Transporter Dashboard](#4-transporter-dashboard-screen) - Dashboard API
5. [Broadcast List Screen](#5-broadcast-list-screen) - Broadcast APIs
6. [Create Trip Screen](#6-create-trip-screen) - Broadcast Creation API
7. [Driver Assignment Screen](#7-driver-assignment-screen) - Assignment APIs

### Driver Screens
8. [Driver Dashboard](#8-driver-dashboard-screen) - Dashboard API
9. [Trip Notification Screen](#9-trip-notification-screen) - Notification APIs
10. [Trip Accept/Decline Screen](#10-trip-acceptdecline-screen) - Response APIs
11. [Driver Trip Navigation](#11-driver-trip-navigation-screen) - GPS Tracking APIs

### Shared Screens
12. [Live Tracking Screen](#12-live-tracking-screen) - GPS Tracking APIs

---


## 1️⃣ Login Screen

### 📁 File Location
`app/src/main/java/com/weelo/logistics/ui/auth/LoginScreen.kt`

### 🔍 Integration Points

#### Line 26-215: LoginScreen Composable
**Where to integrate:** Lines 26-50 (inside the composable function)

### 🔗 Required API Endpoint

#### POST /api/v1/auth/login
**Base URL:** `https://api.weelologistics.com/v1`

**Request Body:**
```json
{
  "mobileNumber": "+919876543210",
  "password": "user_password"
}
```

**Response (Success - 200):**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "refresh_token_here",
    "user": {
      "id": "u123",
      "name": "John Doe",
      "mobileNumber": "+919876543210",
      "role": "TRANSPORTER",
      "email": "john@example.com",
      "isVerified": true
    },
    "expiresIn": 86400
  },
  "message": "Login successful",
  "timestamp": "2026-01-05T10:30:00Z"
}
```

**Response (Error - 401):**
```json
{
  "success": false,
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid mobile number or password",
    "details": {}
  },
  "timestamp": "2026-01-05T10:30:00Z"
}
```

### 📝 Integration Steps

1. **Add API Service (Create new file)**
   - File: `app/src/main/java/com/weelo/logistics/data/api/AuthApiService.kt`
   
```kotlin
package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<LoginResponse>>
}

data class LoginRequest(
    val mobileNumber: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val refreshToken: String,
    val user: User,
    val expiresIn: Int
)

data class User(
    val id: String,
    val name: String,
    val mobileNumber: String,
    val role: String,
    val email: String?,
    val isVerified: Boolean
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ErrorResponse?,
    val message: String?,
    val timestamp: String
)

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, Any>
)
```

2. **Create Repository (Create new file)**
   - File: `app/src/main/java/com/weelo/logistics/data/repository/AuthRepository.kt`

```kotlin
package com.weelo.logistics.data.repository

import com.weelo.logistics.data.api.AuthApiService
import com.weelo.logistics.data.api.LoginRequest
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val authApiService: AuthApiService
) {
    suspend fun login(mobileNumber: String, password: String): Result<LoginResponse> {
        return try {
            val response = authApiService.login(LoginRequest(mobileNumber, password))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.data!!)
            } else {
                val errorMsg = response.body()?.error?.message ?: "Login failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

3. **Modify LoginScreen.kt**
   - At **Line 27-35**, add repository and state:

```kotlin
@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,  // Pass user role
    onNavigateToSignup: () -> Unit
) {
    // ADD THESE LINES:
    val authRepository = remember { AuthRepository(/* inject or create */) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var mobileNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
```

4. **Replace Login Button Click Handler**
   - Find the login button (around Line 150-170)
   - Replace `onClick = { onLoginSuccess("TRANSPORTER") }` with:

```kotlin
onClick = {
    if (mobileNumber.isNotEmpty() && password.isNotEmpty()) {
        isLoading = true
        errorMessage = null
        
        scope.launch {
            authRepository.login(mobileNumber, password)
                .onSuccess { response ->
                    // Save token
                    saveAuthToken(response.token)
                    saveUserData(response.user)
                    
                    // Navigate based on role
                    onLoginSuccess(response.user.role)
                    isLoading = false
                }
                .onFailure { error ->
                    errorMessage = error.message
                    isLoading = false
                }
        }
    } else {
        errorMessage = "Please enter mobile number and password"
    }
}
```

5. **Add Loading State to UI**
   - Wrap the button content with loading indicator:

```kotlin
Button(
    onClick = { /* login logic */ },
    modifier = Modifier.fillMaxWidth().height(56.dp),
    enabled = !isLoading
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = Color.White
        )
    } else {
        Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
```

6. **Display Error Message**
   - Add after the button (around Line 180):

```kotlin
errorMessage?.let { error ->
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = error,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}
```

### 🔄 Complete Flow

```
USER ENTERS CREDENTIALS
        ↓
CLICKS LOGIN BUTTON (Line ~160)
        ↓
AuthRepository.login() called
        ↓
HTTP POST to /api/v1/auth/login
        ↓
BACKEND VALIDATES CREDENTIALS
        ↓
SUCCESS: Returns JWT token + user data
        ↓
FRONTEND saves token to SharedPreferences
        ↓
Navigate to Dashboard based on role
```

---


## 2️⃣ Signup Screen

### 📁 File Location
`app/src/main/java/com/weelo/logistics/ui/auth/SignupScreen.kt`

### 🔗 Required API Endpoint

#### POST /api/v1/auth/register

**Request Body:**
```json
{
  "name": "John Doe",
  "mobileNumber": "+919876543210",
  "email": "john@example.com",
  "password": "SecurePass123",
  "role": "TRANSPORTER",
  "companyName": "ABC Logistics",
  "gstNumber": "29ABCDE1234F1Z5"
}
```

**Response (Success - 201):**
```json
{
  "success": true,
  "data": {
    "userId": "u123",
    "mobileNumber": "+919876543210",
    "otpSent": true,
    "otpExpiresIn": 300,
    "message": "OTP sent to mobile number"
  },
  "message": "Registration successful. Please verify OTP",
  "timestamp": "2026-01-05T10:30:00Z"
}
```

### 📝 Integration Code

**Add to AuthApiService.kt:**
```kotlin
@POST("auth/register")
suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<RegisterResponse>>

data class RegisterRequest(
    val name: String,
    val mobileNumber: String,
    val email: String?,
    val password: String,
    val role: String,
    val companyName: String?,
    val gstNumber: String?
)

data class RegisterResponse(
    val userId: String,
    val mobileNumber: String,
    val otpSent: Boolean,
    val otpExpiresIn: Int,
    val message: String
)
```

**Integration Point in SignupScreen.kt (Line ~150-180):**
```kotlin
Button(
    onClick = {
        scope.launch {
            val response = authRepository.register(
                name = name,
                mobileNumber = mobileNumber,
                email = email,
                password = password,
                role = selectedRole,
                companyName = companyName,
                gstNumber = gstNumber
            )
            response.onSuccess { data ->
                // Navigate to OTP screen
                onNavigateToOTP(data.userId, data.mobileNumber)
            }
        }
    }
) {
    Text("Sign Up")
}
```

---

## 3️⃣ OTP Verification Screen

### 📁 File Location
`app/src/main/java/com/weelo/logistics/ui/auth/OTPVerificationScreen.kt`

### 🔗 Required API Endpoints

#### POST /api/v1/auth/verify-otp

**Request Body:**
```json
{
  "mobileNumber": "+919876543210",
  "otp": "123456"
}
```

**Response (Success - 200):**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "refresh_token",
    "user": {
      "id": "u123",
      "name": "John Doe",
      "mobileNumber": "+919876543210",
      "role": "TRANSPORTER",
      "isVerified": true
    }
  },
  "message": "OTP verified successfully",
  "timestamp": "2026-01-05T10:30:00Z"
}
```

#### POST /api/v1/auth/resend-otp

**Request Body:**
```json
{
  "mobileNumber": "+919876543210"
}
```

**Response (Success - 200):**
```json
{
  "success": true,
  "data": {
    "otpSent": true,
    "expiresIn": 300,
    "message": "OTP sent successfully"
  },
  "timestamp": "2026-01-05T10:30:00Z"
}
```

### 📝 Integration Code

**Add to AuthApiService.kt:**
```kotlin
@POST("auth/verify-otp")
suspend fun verifyOTP(@Body request: VerifyOTPRequest): Response<ApiResponse<LoginResponse>>

@POST("auth/resend-otp")
suspend fun resendOTP(@Body request: ResendOTPRequest): Response<ApiResponse<ResendOTPResponse>>

data class VerifyOTPRequest(
    val mobileNumber: String,
    val otp: String
)

data class ResendOTPRequest(
    val mobileNumber: String
)

data class ResendOTPResponse(
    val otpSent: Boolean,
    val expiresIn: Int,
    val message: String
)
```

---

## 4️⃣ Transporter Dashboard Screen

### 📁 File Location
`app/src/main/java/com/weelo/logistics/ui/transporter/TransporterDashboardScreen.kt`

### 🔍 Integration Points

**Line 26-50:** Main composable function
**Line 28-34:** LaunchedEffect - Load dashboard data

### 🔗 Required API Endpoint

#### GET /api/v1/transporter/dashboard

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Response (Success - 200):**
```json
{
  "success": true,
  "data": {
    "transporterId": "t123",
    "activeTrips": 8,
    "pendingBroadcasts": 5,
    "availableVehicles": 12,
    "todayRevenue": 45000.00,
    "weekRevenue": 280000.00,
    "monthRevenue": 1200000.00,
    "recentBroadcasts": [
      {
        "id": "b123",
        "customerName": "ABC Company",
        "pickupLocation": "Mumbai",
        "dropLocation": "Delhi",
        "vehicleType": "CONTAINER",
        "quantity": 5,
        "distance": 1420.5,
        "expiresAt": "2026-01-05T18:00:00Z",
        "status": "ACTIVE"
      }
    ],
    "activeTripsDetails": [
      {
        "tripId": "tr123",
        "vehicleNumber": "MH12AB1234",
        "driverName": "Driver Name",
        "status": "IN_PROGRESS",
        "currentLocation": "Pune",
        "destination": "Delhi",
        "progress": 35
      }
    ]
  },
  "timestamp": "2026-01-05T10:30:00Z"
}
```

### 📝 Integration Steps

1. **Create Dashboard API Service**
   - File: `app/src/main/java/com/weelo/logistics/data/api/DashboardApiService.kt`

```kotlin
package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface DashboardApiService {
    @GET("transporter/dashboard")
    suspend fun getTransporterDashboard(
        @Header("Authorization") token: String
    ): Response<ApiResponse<TransporterDashboardData>>
}

data class TransporterDashboardData(
    val transporterId: String,
    val activeTrips: Int,
    val pendingBroadcasts: Int,
    val availableVehicles: Int,
    val todayRevenue: Double,
    val weekRevenue: Double,
    val monthRevenue: Double,
    val recentBroadcasts: List<BroadcastSummary>,
    val activeTripsDetails: List<TripSummary>
)

data class BroadcastSummary(
    val id: String,
    val customerName: String,
    val pickupLocation: String,
    val dropLocation: String,
    val vehicleType: String,
    val quantity: Int,
    val distance: Double,
    val expiresAt: String,
    val status: String
)

data class TripSummary(
    val tripId: String,
    val vehicleNumber: String,
    val driverName: String,
    val status: String,
    val currentLocation: String,
    val destination: String,
    val progress: Int
)
```

2. **Modify TransporterDashboardScreen.kt**
   - At **Line 28-34**, replace with API call:

```kotlin
@Composable
fun TransporterDashboardScreen(
    onNavigateToBroadcasts: () -> Unit = {},
    onNavigateToTrips: () -> Unit = {},
    onNavigateToFleet: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val dashboardService = remember { /* inject DashboardApiService */ }
    var dashboardData by remember { mutableStateOf<TransporterDashboardData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load dashboard data
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val token = getAuthToken() // Get from SharedPreferences
                val response = dashboardService.getTransporterDashboard("Bearer $token")
                
                if (response.isSuccessful && response.body()?.success == true) {
                    dashboardData = response.body()!!.data
                } else {
                    errorMessage = "Failed to load dashboard"
                }
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }
    
    // UI Code continues...
```

3. **Display Data in UI**
   - Replace mock data with API data throughout the screen:

```kotlin
// Line ~70-90: Stats Cards
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    StatsCard(
        title = "Active Trips",
        value = "${dashboardData?.activeTrips ?: 0}",
        icon = Icons.Default.LocalShipping
    )
    StatsCard(
        title = "Pending",
        value = "${dashboardData?.pendingBroadcasts ?: 0}",
        icon = Icons.Default.Notifications
    )
    StatsCard(
        title = "Vehicles",
        value = "${dashboardData?.availableVehicles ?: 0}",
        icon = Icons.Default.DirectionsCar
    )
}
```

---

## 5️⃣ Broadcast List Screen

### 📁 File Location
`app/src/main/java/com/weelo/logistics/ui/transporter/BroadcastListScreen.kt`

### 🔍 Integration Points

**Line 41-50:** LaunchedEffect for loading broadcasts
**Line 193-300:** BroadcastCard component

### 🔗 Required API Endpoints

#### GET /api/v1/broadcasts

**Query Parameters:**
- `status` (optional): ACTIVE, EXPIRED, ACCEPTED, DECLINED
- `page` (optional): Page number (default: 1)
- `limit` (optional): Items per page (default: 20)

**Headers:**
```
Authorization: Bearer <jwt_token>
```

**Response (Success - 200):**
```json
{
  "success": true,
  "data": {
    "broadcasts": [
      {
        "id": "b123",
        "customerId": "c456",
        "customerName": "ABC Logistics",
        "customerMobile": "+919876543210",
        "pickupLocation": {
          "latitude": 19.0760,
          "longitude": 72.8777,
          "address": "Andheri, Mumbai, Maharashtra",
          "city": "Mumbai",
          "state": "Maharashtra",
          "pincode": "400053"
        },
        "dropLocation": {
          "latitude": 28.7041,
          "longitude": 77.1025,
          "address": "Connaught Place, New Delhi",
          "city": "New Delhi",
          "state": "Delhi",
          "pincode": "110001"
        },
        "vehicleType": "CONTAINER_20FT",
        "quantity": 5,
        "quantityAccepted": 2,
        "quantityRemaining": 3,
        "goodsType": "Electronics",
        "weight": "15 Tons",
        "distance": 1420.5,
        "estimatedFare": 85000.00,
        "pickupDate": "2026-01-06T10:00:00Z",
        "expiresAt": "2026-01-05T18:00:00Z",
        "status": "ACTIVE",
        "createdAt": "2026-01-05T09:00:00Z",
        "notes": "Handle with care"
      }
    ],
    "pagination": {
      "currentPage": 1,
      "totalPages": 5,
      "totalItems": 87,
      "itemsPerPage": 20
    }
  },
  "timestamp": "2026-01-05T10:30:00Z"
}
```

#### POST /api/v1/broadcasts/{broadcastId}/respond

**Request Body:**
```json
{
  "action": "ACCEPT",
  "quantity": 3,
  "proposedFare": 82000.00,
  "notes": "Can provide 3 vehicles"
}
```

**Response (Success - 200):**
```json
{
  "success": true,
  "data": {
    "responseId": "resp123",
    "broadcastId": "b123",
    "transporterId": "t123",
    "status": "ACCEPTED",
    "quantity": 3,
    "message": "You have successfully accepted 3 vehicles for this broadcast"
  },
  "message": "Response recorded successfully",
  "timestamp": "2026-01-05T10:30:00Z"
}
```

### 📝 Integration Code

1. **Create Broadcast API Service**
   - File: `app/src/main/java/com/weelo/logistics/data/api/BroadcastApiService.kt`

```kotlin
package com.weelo.logistics.data.api

import retrofit2.Response
import retrofit2.http.*

interface BroadcastApiService {
    @GET("broadcasts")
    suspend fun getBroadcasts(
        @Header("Authorization") token: String,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<BroadcastListResponse>>
    
    @POST("broadcasts/{broadcastId}/respond")
    suspend fun respondToBroadcast(
        @Header("Authorization") token: String,
        @Path("broadcastId") broadcastId: String,
        @Body request: BroadcastResponseRequest
    ): Response<ApiResponse<BroadcastResponseData>>
}

data class BroadcastListResponse(
    val broadcasts: List<BroadcastDetail>,
    val pagination: PaginationInfo
)

data class BroadcastDetail(
    val id: String,
    val customerId: String,
    val customerName: String,
    val customerMobile: String,
    val pickupLocation: LocationDetail,
    val dropLocation: LocationDetail,
    val vehicleType: String,
    val quantity: Int,
    val quantityAccepted: Int,
    val quantityRemaining: Int,
    val goodsType: String,
    val weight: String?,
    val distance: Double,
    val estimatedFare: Double,
    val pickupDate: String,
    val expiresAt: String,
    val status: String,
    val createdAt: String,
    val notes: String?
)

data class LocationDetail(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String?,
    val state: String?,
    val pincode: String?
)

data class PaginationInfo(
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
    val itemsPerPage: Int
)

data class BroadcastResponseRequest(
    val action: String, // ACCEPT or DECLINE
    val quantity: Int,
    val proposedFare: Double?,
    val notes: String?
)

data class BroadcastResponseData(
    val responseId: String,
    val broadcastId: String,
    val transporterId: String,
    val status: String,
    val quantity: Int,
    val message: String
)
```

2. **Modify BroadcastListScreen.kt**
   - At **Line 41-50**, add API call:

```kotlin
@Composable
fun BroadcastListScreen(
    onNavigateToDetails: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val broadcastService = remember { /* inject BroadcastApiService */ }
    var broadcasts by remember { mutableStateOf<List<BroadcastDetail>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Active", "Accepted", "Expired")
    
    // Load broadcasts when tab changes
    LaunchedEffect(selectedTab) {
        scope.launch {
            isLoading = true
            try {
                val token = getAuthToken()
                val status = when(selectedTab) {
                    0 -> "ACTIVE"
                    1 -> "ACCEPTED"
                    2 -> "EXPIRED"
                    else -> null
                }
                
                val response = broadcastService.getBroadcasts(
                    token = "Bearer $token",
                    status = status
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    broadcasts = response.body()!!.data!!.broadcasts
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading = false
            }
        }
    }
```

3. **Add Accept/Decline Action**
   - In BroadcastCard component (Line ~193-300), add button handlers:

```kotlin
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(
        onClick = {
            scope.launch {
                val response = broadcastService.respondToBroadcast(
                    token = "Bearer ${getAuthToken()}",
                    broadcastId = broadcast.id,
                    request = BroadcastResponseRequest(
                        action = "DECLINE",
                        quantity = 0,
                        proposedFare = null,
                        notes = null
                    )
                )
                // Handle response
            }
        },
        modifier = Modifier.weight(1f)
    ) {
        Text("Decline")
    }
    
    Button(
        onClick = {
            // Show quantity picker dialog
            // Then accept with quantity
            scope.launch {
                val response = broadcastService.respondToBroadcast(
                    token = "Bearer ${getAuthToken()}",
                    broadcastId = broadcast.id,
                    request = BroadcastResponseRequest(
                        action = "ACCEPT",
                        quantity = selectedQuantity,
                        proposedFare = broadcast.estimatedFare,
                        notes = null
                    )
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    // Navigate to driver assignment
                    onNavigateToAssignment(broadcast.id, selectedQuantity)
                }
            }
        },
        modifier = Modifier.weight(1f)
    ) {
        Text("Accept")
    }
}
```

---

