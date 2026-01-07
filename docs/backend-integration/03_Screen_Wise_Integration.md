# Screen-Wise Integration Guide

This document maps every UI screen to its backend API requirements, explaining when APIs are called and how the UI responds.

---

## Table of Contents

### Authentication Flow
1. [Splash Screen](#1-splash-screen)
2. [Onboarding Screen](#2-onboarding-screen)
3. [Login Screen](#3-login-screen)
4. [OTP Verification Screen](#4-otp-verification-screen)
5. [Role Selection Screen](#5-role-selection-screen)

### Transporter Flow
6. [Transporter Dashboard](#6-transporter-dashboard)
7. [Fleet List Screen](#7-fleet-list-screen)
8. [Add Vehicle Screen](#8-add-vehicle-screen)
9. [Vehicle Details Screen](#9-vehicle-details-screen)
10. [Driver List Screen](#10-driver-list-screen)
11. [Add Driver Screen](#11-add-driver-screen)
12. [Driver Details Screen](#12-driver-details-screen)
13. [Trip List Screen](#13-trip-list-screen)
14. [Create Trip Screen](#14-create-trip-screen)
15. [Trip Details Screen](#15-trip-details-screen)
16. [Broadcast List Screen](#16-broadcast-list-screen)
17. [Truck Selection Screen](#17-truck-selection-screen)
18. [Driver Assignment Screen](#18-driver-assignment-screen)

### Driver Flow
19. [Driver Dashboard](#19-driver-dashboard)
20. [Driver Trip Notifications](#20-driver-trip-notifications)
21. [Trip Accept/Decline Screen](#21-trip-acceptdecline-screen)
22. [Trip Navigation Screen](#22-trip-navigation-screen)
23. [Driver Earnings Screen](#23-driver-earnings-screen)
24. [Driver Performance Screen](#24-driver-performance-screen)
25. [Driver Profile Edit Screen](#25-driver-profile-edit-screen)

### Shared Screens
26. [Live Tracking Screen](#26-live-tracking-screen)
27. [Settings Screen](#27-settings-screen)

---

## Authentication Flow

### 1. Splash Screen

**File**: `SplashScreen.kt`  
**Purpose**: App entry point, checks if user is already logged in

#### API Calls

**On Load**:
- **None** - Pure client-side logic

#### Logic Flow
```
1. Display splash logo (2 seconds)
2. Check if JWT token exists in secure storage
   ├─ Token exists → Validate token
   │  ├─ Valid → Navigate to role-based dashboard
   │  └─ Invalid/Expired → Navigate to Login
   └─ No token → Navigate to Onboarding
```

#### Backend Integration
- If token exists, optionally call `GET /auth/me` to validate
- If validation fails, clear token and go to login

---

### 2. Onboarding Screen

**File**: `OnboardingScreen.kt`  
**Purpose**: First-time user introduction screens

#### API Calls
- **None** - Static content only

#### User Actions
- Swipe through onboarding slides
- Click "Get Started" → Navigate to Login Screen

---

### 3. Login Screen

**File**: `LoginScreen.kt`  
**Purpose**: User enters phone number to initiate authentication

#### API Calls

**When**: User enters phone number and clicks "Send OTP" button

**API**: `POST /auth/send-otp`

**Request**:
```kotlin
// UI validates before sending
val phoneNumber = phoneInput.text // Must be 10 digits
if (phoneNumber.length == 10 && phoneNumber.all { it.isDigit() }) {
    sendOTP(phoneNumber)
}
```

```json
{
  "mobileNumber": "9876543210",
  "countryCode": "+91"
}
```

**On Success (200)**:
```kotlin
// Navigate to OTP screen
navController.navigate("otp_verification/${phoneNumber}/${response.otpId}")

// Show toast
Toast.show("OTP sent to ${response.maskedNumber}")
```

**On Error**:
- `400`: Show "Invalid phone number"
- `429`: Show "Too many requests. Try after ${seconds} seconds"
- `500`: Show "Unable to send OTP. Please try again"

#### UI Validation (Client-Side)
- Phone number: Exactly 10 digits
- Enable "Send OTP" button only when valid
- Disable button during API call (show loading)

---

### 4. OTP Verification Screen

**File**: `OTPVerificationScreen.kt`  
**Purpose**: User enters 6-digit OTP to verify identity

#### API Calls

**When**: User enters 6th digit (auto-submit) or clicks "Verify"

**API**: `POST /auth/verify-otp`

**Request**:
```json
{
  "mobileNumber": "9876543210",
  "otp": "123456",
  "otpId": "otp_abc123xyz",
  "deviceInfo": {
    "deviceId": "unique_device_id",
    "model": "Samsung Galaxy S21",
    "os": "Android 12"
  }
}
```

**On Success (200)**:
```kotlin
// Store tokens securely
secureStorage.saveToken(response.tokens.accessToken)
secureStorage.saveRefreshToken(response.tokens.refreshToken)

// Store user data
userPreferences.saveUser(response.user)

// Navigate based on role
if (response.user.roles.contains("TRANSPORTER")) {
    navController.navigate("transporter_dashboard") {
        popUpTo("login") { inclusive = true }
    }
} else if (response.user.roles.contains("DRIVER")) {
    navController.navigate("driver_dashboard") {
        popUpTo("login") { inclusive = true }
    }
}
```

**On Error**:
- `400`: Show "Invalid OTP. Please try again" (vibrate + red highlight)
- `401`: Show "OTP expired. Please request a new one"
- `404`: Show "User not found. Please sign up"
- Max 3 attempts, then force resend OTP

#### Additional Features

**Resend OTP Button**:
- Disabled for 60 seconds after initial send
- Countdown timer displayed
- Clicking resends: `POST /auth/send-otp`

**Auto-Read OTP** (Android):
- SMS auto-read permission
- Parse OTP from SMS automatically

---

### 5. Role Selection Screen

**File**: `RoleSelectionScreen.kt`  
**Purpose**: For users with multiple roles (Transporter + Driver)

#### API Calls
- **None** - Client-side selection only

#### Logic
```kotlin
// User selects role
if (selectedRole == "TRANSPORTER") {
    navController.navigate("transporter_dashboard")
} else {
    navController.navigate("driver_dashboard")
}
```

---

## Transporter Flow

### 6. Transporter Dashboard

**File**: `TransporterDashboardScreen.kt`  
**Purpose**: Main dashboard showing fleet statistics and activity

#### API Calls

**On Screen Load** (Initial + Pull-to-Refresh):

1. **GET /transporter/dashboard**
   ```kotlin
   val transporterId = userPreferences.getUserId()
   val response = api.getTransporterDashboard(transporterId)
   ```

**Success Response Handling**:
```kotlin
dashboardState = DashboardState.Success(
    totalVehicles = response.dashboard.totalVehicles,
    activeVehicles = response.dashboard.activeVehicles,
    totalDrivers = response.dashboard.totalDrivers,
    activeDrivers = response.dashboard.activeDrivers,
    activeTrips = response.dashboard.activeTrips,
    todayRevenue = response.dashboard.todayRevenue,
    todayTrips = response.dashboard.todayTrips,
    recentTrips = response.dashboard.recentTrips
)
```

**On Error**:
```kotlin
dashboardState = DashboardState.Error("Failed to load dashboard")
// Show retry button
```

#### User Actions on Dashboard

1. **Click "Manage Fleet"** → Navigate to Fleet List Screen
2. **Click "Manage Drivers"** → Navigate to Driver List Screen
3. **Click "Active Trips"** → Navigate to Trip List Screen
4. **Click "Broadcasts"** → Navigate to Broadcast List Screen
5. **Click Recent Trip** → Navigate to Trip Details Screen

#### Refresh Behavior
- **Pull-to-Refresh**: Re-call dashboard API
- **Auto-Refresh**: Every 30 seconds when screen is active

---

### 7. Fleet List Screen

**File**: `FleetListScreen.kt`  
**Purpose**: Display all vehicles in fleet with filtering

#### API Calls

**On Screen Load**:

**API**: `GET /vehicles?transporterId={id}&status={filter}&page={page}&limit=20`

**Request**:
```kotlin
val transporterId = userPreferences.getUserId()
val status = selectedFilter // "AVAILABLE", "IN_TRANSIT", "MAINTENANCE", or null for all
val page = currentPage

api.getVehicles(
    transporterId = transporterId,
    status = status,
    page = page,
    limit = 20
)
```

**Success Response Handling**:
```kotlin
vehicleList.addAll(response.vehicles)
totalVehicles = response.total
canLoadMore = response.pagination.page < response.pagination.pages

// Display vehicle cards
vehicles.forEach { vehicle ->
    VehicleCard(
        vehicleNumber = vehicle.vehicleNumber,
        category = vehicle.category.name,
        subtype = vehicle.subtype.name,
        status = vehicle.status,
        assignedDriver = vehicle.assignedDriverName,
        onClick = { navigateToVehicleDetails(vehicle.id) }
    )
}
```

**On Error**:
- Show empty state with "Add Vehicle" button
- If not first page, show "Failed to load more" toast

#### User Actions

1. **Click "+ Add Vehicle"** → Navigate to Add Vehicle Screen
2. **Click Vehicle Card** → Navigate to Vehicle Details Screen (pass vehicleId)
3. **Select Filter (All/Available/In Transit/Maintenance)** → Re-call API with filter
4. **Scroll to Bottom** → Load next page (pagination)
5. **Search Vehicle** → Filter client-side or call API with search param

#### Pagination
```kotlin
LazyColumn {
    items(vehicles) { vehicle -> VehicleCard(vehicle) }
    
    // Load more when reaching end
    item {
        if (canLoadMore) {
            LaunchedEffect(Unit) {
                loadNextPage()
            }
        }
    }
}
```

---

### 8. Add Vehicle Screen

**File**: `AddVehicleScreen.kt`  
**Purpose**: Multi-step form to add new vehicle to fleet

#### Steps
1. **Select Category** (Open, Container, LCV, Mini, Trailer, Tipper, Tanker, Dumper, Bulker)
2. **Select Subtype** (Based on category, e.g., "32 Feet Single Axle")
3. **Enter Details** (Vehicle Number, Model, Year)

#### API Calls

**When**: User completes all steps and clicks "Add Vehicle"

**API**: `POST /vehicles/add`

**Request**:
```kotlin
val request = AddVehicleRequest(
    transporterId = userPreferences.getUserId(),
    category = selectedCategory.id,
    subtype = selectedSubtype.id,
    vehicleNumber = vehicleNumberInput.text, // e.g., "GJ-01-AB-1234"
    model = modelInput.text,
    year = yearInput.text.toInt(),
    capacity = "${selectedSubtype.capacityTons} tons",
    documents = null // Optional: Upload later
)

api.addVehicle(token, request)
```

**On Success (201)**:
```kotlin
// Show success message
Toast.show("Vehicle added successfully")

// Navigate back to fleet list
navController.popBackStack("fleet_list", inclusive = false)

// Fleet list will refresh automatically
```

**On Error**:
- `400`: Show "Invalid vehicle number format" (inline error)
- `409`: Show "Vehicle number already exists"
- `422`: Show validation errors on respective fields

#### UI Validation (Before API Call)

```kotlin
fun validateVehicleNumber(number: String): Boolean {
    // Format: XX-00-XX-0000
    val regex = Regex("^[A-Z]{2}-\\d{2}-[A-Z]{2}-\\d{4}$")
    return regex.matches(number)
}

fun enableAddButton(): Boolean {
    return selectedCategory != null &&
           selectedSubtype != null &&
           validateVehicleNumber(vehicleNumber) &&
           year in 2000..2026
}
```

---

### 9. Vehicle Details Screen

**File**: `VehicleDetailsScreen.kt`  
**Purpose**: View and edit vehicle details, assign driver

#### API Calls

**On Screen Load**:

**API**: `GET /vehicles/{vehicleId}` (if needed, or use cached data from list)

#### User Actions

**1. Assign Driver**:
- **UI**: Click "Assign Driver" → Show driver selection dialog
- **API**: `POST /vehicles/{vehicleId}/assign-driver`
  ```json
  {
    "driverId": "driver_001"
  }
  ```
- **On Success**: Update UI, show "Driver assigned successfully"

**2. Update Status**:
- **UI**: Change status dropdown (Available → Maintenance)
- **API**: `PUT /vehicles/{vehicleId}`
  ```json
  {
    "status": "MAINTENANCE"
  }
  ```
- **On Success**: Update local state

**3. Delete Vehicle**:
- **UI**: Click "Delete" → Show confirmation dialog
- **API**: `DELETE /vehicles/{vehicleId}`
- **On Success**: Navigate back to fleet list

---

### 10. Driver List Screen

**File**: `DriverListScreen.kt`  
**Purpose**: Display all drivers with filtering

#### API Calls

**On Screen Load**:

**API**: `GET /drivers?transporterId={id}&status={filter}&page={page}&limit=20`

**Similar to Fleet List Screen** - See section 7 for pattern

#### User Actions
1. **Click "+ Add Driver"** → Navigate to Add Driver Screen
2. **Click Driver Card** → Navigate to Driver Details Screen
3. **Filter by Status** (All/Active/Inactive/On Trip) → Re-call API

---

### 11. Add Driver Screen

**File**: `AddDriverScreen.kt`  
**Purpose**: Form to add new driver

#### API Calls

**When**: User fills form and clicks "Add Driver"

**API**: `POST /drivers/add`

**Request**:
```json
{
  "transporterId": "transporter_123",
  "name": "Ramesh Kumar",
  "mobileNumber": "9876543210",
  "licenseNumber": "DL1420110012345",
  "licenseExpiry": "2025-12-31",
  "emergencyContact": "9876543211",
  "address": "123 Street, Ahmedabad"
}
```

**On Success (201)**:
```kotlin
// Show success message
showDialog(
    title = "Driver Added",
    message = "Invitation sent to ${response.driver.name} via SMS"
)

// Navigate back
navController.popBackStack()
```

**On Error**:
- `409`: Show "Mobile number already registered"
- `400`: Show inline validation errors

#### UI Validation
```kotlin
// License format: DL + StateCode + Year + Number
// Example: DL1420110012345
fun validateLicense(license: String): Boolean {
    val regex = Regex("^DL\\d{12,14}$")
    return regex.matches(license)
}

// Phone: 10 digits
fun validatePhone(phone: String): Boolean {
    return phone.length == 10 && phone.all { it.isDigit() }
}
```

---

### 12. Driver Details Screen

**File**: `DriverDetailsScreen.kt`  
**Purpose**: View driver profile, performance, and trip history

#### API Calls

**On Screen Load**:

1. **GET /drivers/{driverId}/performance**
   - Display performance metrics (rating, trips, earnings)

2. **GET /driver/trips/history?driverId={id}**
   - Display recent trip history

#### User Actions

**1. View Performance**:
- Display metrics from performance API
- Show charts (trips over time, earnings)

**2. Update Driver**:
- **API**: `PUT /drivers/{driverId}`
- Update name, license expiry, status

**3. Delete Driver**:
- **API**: `DELETE /drivers/{driverId}`
- Show confirmation dialog
- On success, navigate back to driver list

---

### 13. Trip List Screen

**File**: `TripListScreen.kt`  
**Purpose**: View all trips (active, completed, cancelled)

#### API Calls

**On Screen Load**:

**API**: `GET /trips?transporterId={id}&status={filter}&page={page}&limit=20`

**Filter Options**:
- All Trips
- Active (IN_PROGRESS)
- Pending (ASSIGNED, PENDING)
- Completed
- Cancelled

#### User Actions
1. **Click "+ Create Trip"** → Navigate to Create Trip Screen
2. **Click Trip Card** → Navigate to Trip Details Screen
3. **Filter by Status** → Re-call API with filter

---

### 14. Create Trip Screen

**File**: `CreateTripScreen.kt`  
**Purpose**: Create new trip with pickup/drop locations

#### API Calls

**When**: User submits trip form

**API**: `POST /trips/create`

**Request**:
```json
{
  "transporterId": "transporter_123",
  "vehicleId": "vehicle_001",
  "driverId": "driver_001",
  "pickupLocation": {
    "latitude": 23.0225,
    "longitude": 72.5714,
    "address": "Warehouse A, Ahmedabad"
  },
  "dropLocation": {
    "latitude": 19.0760,
    "longitude": 72.8777,
    "address": "Warehouse B, Mumbai"
  },
  "distance": 540.5,
  "estimatedDuration": 480,
  "customerName": "ABC Industries",
  "customerMobile": "9999999999",
  "goodsType": "Electronics",
  "weight": "5000 kg",
  "fare": 15000.00
}
```

**On Success**:
- Show "Trip created successfully"
- Navigate to trip list

---

### 15. Trip Details Screen

**File**: `TripDetailsScreen.kt`  
**Purpose**: View trip details and track status

#### API Calls

**On Screen Load**:

1. **GET /trips/{tripId}**
   - Fetch trip details

2. **GET /trips/{tripId}/tracking** (if trip is active)
   - Fetch real-time location

#### Live Tracking
- If trip status is IN_PROGRESS
- Poll tracking API every 10 seconds
- Display driver location on map

---

### 16. Broadcast List Screen

**File**: `BroadcastListScreen.kt`  
**Purpose**: View active broadcasts from customers

#### API Calls

**On Screen Load**:

**API**: `GET /broadcasts/active?transporterId={id}&page={page}&limit=20`

**Auto-Refresh**: Every 15 seconds to check for new broadcasts

**Success Response**:
```kotlin
broadcasts.forEach { broadcast ->
    BroadcastCard(
        customerName = broadcast.customerName,
        route = "${broadcast.pickupLocation.address} → ${broadcast.dropLocation.address}",
        totalTrucks = broadcast.totalTrucksNeeded,
        trucksFilled = broadcast.trucksFilledSoFar,
        farePerTruck = broadcast.farePerTruck,
        isUrgent = broadcast.isUrgent,
        onClick = { navigateToTruckSelection(broadcast.broadcastId) }
    )
}
```

#### User Actions
1. **Click Broadcast Card** → Navigate to Truck Selection Screen
2. **Pull to Refresh** → Re-call API

---

### 17. Truck Selection Screen

**File**: `TruckSelectionScreen.kt`  
**Purpose**: Select trucks to assign to broadcast

#### API Calls

**On Screen Load**:

1. **GET /broadcasts/{broadcastId}** - Get broadcast details
2. **GET /vehicles?transporterId={id}&status=AVAILABLE** - Get available vehicles

#### User Actions

**Select Trucks and Assign Drivers**:

**When**: User selects trucks and drivers, clicks "Assign"

**API**: `POST /broadcasts/{broadcastId}/accept`

**Request**:
```json
{
  "transporterId": "transporter_123",
  "trucksTaken": 3,
  "assignments": [
    {
      "vehicleId": "vehicle_001",
      "vehicleNumber": "GJ-01-AB-1234",
      "driverId": "driver_001",
      "driverName": "Ramesh Kumar"
    }
  ]
}
```

**On Success**:
- Show "Broadcast accepted. Drivers notified."
- Navigate to Driver Assignment Screen (track responses)

---

### 18. Driver Assignment Screen

**File**: `DriverAssignmentScreen.kt`  
**Purpose**: Track driver responses (accept/decline)

#### API Calls

**On Screen Load + Auto-Refresh**:

**API**: `GET /assignments/{assignmentId}/status`

**Auto-Refresh**: Every 5 seconds until all drivers respond

**Display**:
```kotlin
assignments.forEach { assignment ->
    DriverAssignmentCard(
        driverName = assignment.driverName,
        vehicleNumber = assignment.vehicleNumber,
        status = assignment.status, // PENDING, ACCEPTED, DECLINED
        responseTime = assignment.respondedAt
    )
}
```

#### Status Indicators
- **PENDING**: Yellow indicator, "Waiting for response"
- **ACCEPTED**: Green indicator, "Accepted"
- **DECLINED**: Red indicator, "Declined" (show reassign button)

---

## Driver Flow

### 19. Driver Dashboard

**File**: `DriverDashboardScreen.kt`  
**Purpose**: Driver's main screen showing earnings and active trip

#### API Calls

**On Screen Load**:

**API**: `GET /driver/dashboard?driverId={id}`

**Success Response**:
```kotlin
DriverDashboard(
    isAvailable = response.isAvailable,
    activeTrip = response.activeTrip,
    todayTrips = response.todayTrips,
    todayEarnings = response.todayEarnings,
    weekEarnings = response.weekEarnings,
    monthEarnings = response.monthEarnings,
    rating = response.rating
)
```

#### User Actions

**1. Toggle Availability**:
- **UI**: Switch button at top
- **API**: `PUT /driver/availability`
  ```json
  {
    "driverId": "driver_001",
    "isAvailable": true
  }
  ```
- **On Success**: Update switch state, show "You're now online"

**2. View Active Trip**:
- If `activeTrip != null`, show trip card
- Click → Navigate to Trip Navigation Screen

**3. View Notifications**:
- Click notification bell → Navigate to Driver Trip Notifications

**4. View Earnings**:
- Click earnings card → Navigate to Driver Earnings Screen

---

### 20. Driver Trip Notifications

**File**: `DriverTripNotificationScreen.kt`  
**Purpose**: View trip assignment notifications

#### API Calls

**On Screen Load**:

**API**: `GET /driver/notifications?driverId={id}&page={page}&limit=20`

**Auto-Refresh**: Every 10 seconds (or use WebSocket)

**Display**:
```kotlin
notifications.forEach { notification ->
    NotificationCard(
        pickup = notification.pickupAddress,
        drop = notification.dropAddress,
        fare = notification.fare,
        distance = notification.distance,
        expiresIn = calculateTimeLeft(notification.expiryTime),
        isRead = notification.isRead,
        onClick = { navigateToAcceptDecline(notification.notificationId) }
    )
}
```

#### User Actions
- Click notification → Navigate to Trip Accept/Decline Screen

---

### 21. Trip Accept/Decline Screen

**File**: `TripAcceptDeclineScreen.kt`  
**Purpose**: View trip details and accept or decline

#### API Calls

**On Screen Load**:

**API**: `GET /notifications/{notificationId}`

#### User Actions

**1. Accept Trip**:
- **UI**: Click "Accept Trip" button
- **API**: `POST /broadcasts/{broadcastId}/accept`
  ```json
  {
    "driverId": "driver_001",
    "notificationId": "notif_001",
    "response": "ACCEPTED"
  }
  ```
- **On Success**: 
  - Show "Trip accepted!"
  - Navigate to Trip Navigation Screen

**2. Decline Trip**:
- **UI**: Click "Decline" button
- **API**: `POST /broadcasts/{broadcastId}/decline`
  ```json
  {
    "driverId": "driver_001",
    "notificationId": "notif_001",
    "reason": "Vehicle issue"
  }
  ```
- **On Success**:
  - Show "Trip declined"
  - Navigate back to dashboard

---

### 22. Trip Navigation Screen

**File**: `DriverTripNavigationScreen.kt`  
**Purpose**: Active trip tracking with GPS

#### API Calls

**On Screen Load**:

1. **GET /trips/{tripId}** - Get trip details

#### User Actions

**1. Start Trip**:
- **UI**: Click "Start Trip" button (when at pickup location)
- **API**: `POST /trips/{tripId}/start`
  ```json
  {
    "driverId": "driver_001",
    "startLocation": {
      "latitude": 23.0225,
      "longitude": 72.5714
    },
    "timestamp": 1704614400000
  }
  ```
- **On Success**: 
  - Status changes to IN_PROGRESS
  - Start GPS tracking

**2. Send Location Updates** (Every 10-30 seconds):
- **API**: `POST /trips/{tripId}/location`
  ```json
  {
    "driverId": "driver_001",
    "latitude": 23.0225,
    "longitude": 72.5714,
    "speed": 65.5,
    "heading": 180.0,
    "timestamp": 1704614400000
  }
  ```
- **Background Service**: Continues even if screen is off

**3. Complete Trip**:
- **UI**: Click "Complete Trip" button (when at drop location)
- **API**: `POST /trips/{tripId}/complete`
  ```json
  {
    "driverId": "driver_001",
    "endLocation": {
      "latitude": 19.0760,
      "longitude": 72.8777
    },
    "completedAt": 1704632400000,
    "actualDistance": 545.2
  }
  ```
- **On Success**:
  - Stop GPS tracking
  - Show completion message with earnings
  - Navigate to dashboard

**4. Cancel Trip**:
- **API**: `POST /trips/{tripId}/cancel`
- Show reason selection dialog

---

### 23. Driver Earnings Screen

**File**: `DriverEarningsScreen.kt`  
**Purpose**: Detailed earnings breakdown

#### API Calls

**On Screen Load**:

**API**: `GET /driver/earnings?driverId={id}&period=month&month=1&year=2026`

**Display**:
- Today, Week, Month tabs
- Earnings cards
- Trip breakdown list
- Charts (if needed)

---

### 24. Driver Performance Screen

**File**: `DriverPerformanceScreen.kt`  
**Purpose**: View performance metrics

#### API Calls

**On Screen Load**:

**API**: `GET /drivers/{driverId}/performance`

**Display**:
- Rating (stars)
- Total trips
- Completion rate
- On-time delivery rate
- Average trip time

---

### 25. Driver Profile Edit Screen

**File**: `DriverProfileEditScreen.kt`  
**Purpose**: Update driver profile

#### API Calls

**When**: User saves changes

**API**: `PUT /driver/profile`

**Request**:
```json
{
  "name": "Ramesh Kumar",
  "email": "ramesh@example.com",
  "emergencyContact": "9876543211",
  "address": "New address"
}
```

---

## Shared Screens

### 26. Live Tracking Screen

**File**: `LiveTrackingScreen.kt`  
**Purpose**: Real-time GPS tracking on map (for both transporter and driver)

#### API Calls

**Continuous (Every 5-10 seconds)**:

**API**: `GET /trips/{tripId}/tracking`

**Response**:
```json
{
  "currentLocation": {
    "latitude": 23.0225,
    "longitude": 72.5714
  },
  "speed": 65.5,
  "heading": 180.0,
  "lastUpdated": 1704614400000
}
```

**Display on Map**:
- Driver's current location (marker)
- Pickup location (green marker)
- Drop location (red marker)
- Route path
- ETA calculation

---

### 27. Settings Screen

**File**: `TransporterSettingsScreen.kt` / `DriverSettingsScreen.kt`  
**Purpose**: App settings and logout

#### User Actions

**1. Update FCM Token**:
- **When**: On first app launch or token refresh
- **API**: `PUT /driver/fcm-token` or `PUT /transporter/fcm-token`
  ```json
  {
    "userId": "user_123",
    "fcmToken": "fcm_token_xyz",
    "deviceId": "device_id"
  }
  ```

**2. Logout**:
- **API**: `POST /auth/logout`
- Clear all local data
- Navigate to login

---

## Summary: API Call Patterns

### On Screen Load
- Most screens call GET APIs immediately
- Show loading state while fetching
- Cache data when appropriate

### On User Action
- Validate input client-side first
- Show loading indicator during API call
- Disable buttons to prevent double submission
- Handle success and error responses appropriately

### Real-Time Updates
- Use pull-to-refresh for manual refresh
- Auto-refresh for critical screens (dashboard, broadcasts)
- WebSocket for instant notifications (optional but recommended)
- Background GPS tracking for active trips

### Error Handling
- Show user-friendly error messages
- Provide retry options
- Maintain local state during errors
- Log errors for debugging

---

**Next**: See `04_Data_Models_and_Schemas.md` for detailed request/response structures.
