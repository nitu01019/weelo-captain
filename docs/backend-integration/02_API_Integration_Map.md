# API Integration Map - Complete Endpoint Catalog

## Table of Contents
1. [Authentication APIs](#authentication-apis)
2. [Transporter Dashboard APIs](#transporter-dashboard-apis)
3. [Fleet Management APIs](#fleet-management-apis)
4. [Driver Management APIs](#driver-management-apis)
5. [Trip Management APIs](#trip-management-apis)
6. [Broadcast System APIs](#broadcast-system-apis)
7. [Driver APIs](#driver-apis)
8. [GPS Tracking APIs](#gps-tracking-apis)
9. [Notification APIs](#notification-apis)

---

## Authentication APIs

### 1. Send OTP (Transporter/Customer Login)

**Endpoint**: `POST /auth/send-otp`  
**Purpose**: Send OTP to user's mobile number for authentication  
**Triggering UI**: Login Screen - When user enters phone number and clicks "Send OTP"  
**Authentication Required**: No

**Request Payload**:
```json
{
  "mobileNumber": "9876543210",
  "countryCode": "+91"
}
```

**Validation Rules**:
- `mobileNumber`: Required, exactly 10 digits, numeric only
- `countryCode`: Optional, defaults to "+91"

**Success Response (200)**:
```json
{
  "success": true,
  "message": "OTP sent successfully",
  "otpId": "otp_abc123xyz",
  "expiryTime": 300,
  "maskedNumber": "98765XXXXX"
}
```

**Error Responses**:
- `400`: Invalid phone number format
- `429`: Too many OTP requests (rate limited)
- `500`: SMS service error

**UI Behavior**:
- **On Success**: Navigate to OTP verification screen, show masked number, start countdown timer
- **On Error**: Show error message, allow retry after cooldown

---

### 2. Verify OTP

**Endpoint**: `POST /auth/verify-otp`  
**Purpose**: Verify OTP and authenticate user  
**Triggering UI**: OTP Verification Screen - When user enters 6-digit OTP  
**Authentication Required**: No

**Request Payload**:
```json
{
  "mobileNumber": "9876543210",
  "otp": "123456",
  "otpId": "otp_abc123xyz",
  "deviceInfo": {
    "deviceId": "device_unique_id",
    "model": "Samsung Galaxy S21",
    "os": "Android 12"
  }
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Login successful",
  "user": {
    "id": "user_123",
    "name": "Rajesh Kumar",
    "mobileNumber": "9876543210",
    "email": "rajesh@example.com",
    "roles": ["TRANSPORTER"],
    "profileImageUrl": "https://cdn.weelo.in/profiles/user_123.jpg",
    "isActive": true
  },
  "tokens": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "refresh_token_xyz",
    "expiresIn": 604800
  },
  "isNewUser": false
}
```

**Error Responses**:
- `400`: Invalid OTP
- `401`: OTP expired
- `404`: User not found (redirect to signup)
- `500`: Server error

**UI Behavior**:
- **On Success**: Store tokens securely, navigate to role-based dashboard (Transporter or Driver)
- **On Error**: Show error message, allow retry (max 3 attempts), option to resend OTP

---

### 3. Driver Send OTP (Special Flow)

**Endpoint**: `POST /api/v1/driver/send-otp`  
**Purpose**: Send OTP to driver's transporter (not to driver)  
**Triggering UI**: Login Screen - When driver enters their phone number  
**Authentication Required**: No

**Request Payload**:
```json
{
  "driverPhone": "9876543210",
  "deviceId": "device_unique_id"
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "OTP sent to your transporter",
  "transporterName": "ABC Logistics",
  "transporterPhone": "91234XXXXX",
  "otpSentTo": "transporter",
  "expiryMinutes": 5
}
```

**Error Responses**:
- `404`: Driver not found - "Please contact your transporter to register you first"
- `403`: Driver account suspended
- `500`: SMS service error

**UI Behavior**:
- **On Success**: Show message "OTP sent to {transporterName}", navigate to OTP screen
- **On Error**: Show appropriate error message with instructions

---

### 4. Refresh Token

**Endpoint**: `POST /auth/refresh-token`  
**Purpose**: Get new access token using refresh token  
**Triggering UI**: Automatic - When access token expires  
**Authentication Required**: Yes (Refresh Token)

**Request Payload**:
```json
{
  "refreshToken": "refresh_token_xyz"
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "accessToken": "new_access_token",
  "expiresIn": 604800
}
```

**UI Behavior**:
- **On Success**: Update stored token, retry failed request
- **On Error**: Force logout, navigate to login screen

---

### 5. Logout

**Endpoint**: `POST /auth/logout`  
**Purpose**: Invalidate current session  
**Triggering UI**: Settings Screen - When user clicks "Logout"  
**Authentication Required**: Yes

**Request Payload**:
```json
{
  "accessToken": "current_token",
  "deviceId": "device_unique_id"
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

**UI Behavior**:
- Clear all stored tokens and user data
- Navigate to login screen

---

## Transporter Dashboard APIs

### 6. Get Transporter Dashboard

**Endpoint**: `GET /transporter/dashboard`  
**Purpose**: Fetch dashboard statistics and recent activity  
**Triggering UI**: Transporter Dashboard Screen - On screen load  
**Authentication Required**: Yes

**Query Parameters**:
```
?transporterId=transporter_123
```

**Success Response (200)**:
```json
{
  "success": true,
  "dashboard": {
    "totalVehicles": 25,
    "activeVehicles": 18,
    "totalDrivers": 30,
    "activeDrivers": 22,
    "activeTrips": 12,
    "todayRevenue": 45000.00,
    "todayTrips": 8,
    "completedTrips": 1250,
    "recentTrips": [
      {
        "id": "trip_001",
        "vehicleNumber": "GJ-01-AB-1234",
        "driverName": "Ramesh Kumar",
        "status": "IN_PROGRESS",
        "pickupLocation": "Ahmedabad",
        "dropLocation": "Mumbai",
        "fare": 5000.00,
        "startedAt": 1704614400000
      }
    ]
  }
}
```

**UI Behavior**:
- **On Success**: Display statistics cards, show recent trips list
- **On Error**: Show error state with retry button

---

## Fleet Management APIs

### 7. Get Vehicles

**Endpoint**: `GET /vehicles`  
**Purpose**: Fetch all vehicles for transporter  
**Triggering UI**: Fleet List Screen - On screen load  
**Authentication Required**: Yes

**Query Parameters**:
```
?transporterId=transporter_123
&status=AVAILABLE
&page=1
&limit=20
```

**Success Response (200)**:
```json
{
  "success": true,
  "vehicles": [
    {
      "id": "vehicle_001",
      "transporterId": "transporter_123",
      "category": {
        "id": "container",
        "name": "Container",
        "icon": "📦"
      },
      "subtype": {
        "id": "32_single",
        "name": "32 Feet Single Axle",
        "capacityTons": 20.0
      },
      "vehicleNumber": "GJ-01-AB-1234",
      "model": "Tata Prima",
      "year": 2022,
      "assignedDriverId": "driver_001",
      "assignedDriverName": "Ramesh Kumar",
      "status": "AVAILABLE",
      "lastServiceDate": 1704000000000,
      "insuranceExpiryDate": 1735622400000,
      "createdAt": 1672531200000
    }
  ],
  "total": 25,
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 25,
    "pages": 2
  }
}
```

**UI Behavior**:
- **On Success**: Display vehicle cards in grid/list, show status badges
- **On Error**: Show empty state with "Add Vehicle" button

---

### 8. Add Vehicle

**Endpoint**: `POST /vehicles/add`  
**Purpose**: Add new vehicle to fleet  
**Triggering UI**: Add Vehicle Screen - When user completes vehicle details form  
**Authentication Required**: Yes

**Request Payload**:
```json
{
  "transporterId": "transporter_123",
  "category": "container",
  "subtype": "32_single",
  "vehicleNumber": "GJ-01-AB-1234",
  "model": "Tata Prima",
  "year": 2022,
  "capacity": "20 tons",
  "documents": {
    "registrationCert": "https://cdn.weelo.in/docs/rc_123.pdf",
    "insurance": "https://cdn.weelo.in/docs/insurance_123.pdf",
    "permit": "https://cdn.weelo.in/docs/permit_123.pdf"
  }
}
```

**Validation Rules**:
- `vehicleNumber`: Required, format "XX-00-XX-0000", must be unique
- `category`: Required, must be one of 9 valid categories (open, container, lcv, mini, trailer, tipper, tanker, dumper, bulker)
- `subtype`: Required, must match selected category's subtypes
- `model`: Optional, max 100 characters
- `year`: Optional, 1900-2026

**Success Response (201)**:
```json
{
  "success": true,
  "vehicle": {
    "id": "vehicle_new_123",
    "transporterId": "transporter_123",
    "category": { ... },
    "subtype": { ... },
    "vehicleNumber": "GJ-01-AB-1234",
    "status": "AVAILABLE",
    "createdAt": 1704614400000
  },
  "message": "Vehicle added successfully"
}
```

**Error Responses**:
- `400`: Invalid vehicle number format
- `409`: Vehicle number already exists
- `422`: Validation error (invalid category/subtype)

**UI Behavior**:
- **On Success**: Show success message, navigate back to fleet list
- **On Error**: Show error message inline, keep form data

---

### 9. Update Vehicle

**Endpoint**: `PUT /vehicles/{vehicleId}`  
**Purpose**: Update vehicle details  
**Triggering UI**: Vehicle Details Screen - When user saves changes  
**Authentication Required**: Yes

**Request Payload**:
```json
{
  "status": "MAINTENANCE",
  "model": "Tata Prima 2023",
  "year": 2023,
  "assignedDriverId": "driver_002"
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "vehicle": { ... },
  "message": "Vehicle updated successfully"
}
```

**UI Behavior**:
- **On Success**: Update local data, show success toast
- **On Error**: Revert changes, show error message

---

### 10. Delete Vehicle

**Endpoint**: `DELETE /vehicles/{vehicleId}`  
**Purpose**: Remove vehicle from fleet  
**Triggering UI**: Vehicle Details Screen - When user confirms deletion  
**Authentication Required**: Yes

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Vehicle deleted successfully"
}
```

**UI Behavior**:
- **On Success**: Navigate back to fleet list, refresh list
- **On Error**: Show error message, keep vehicle in list

---

## Driver Management APIs

### 11. Get Drivers

**Endpoint**: `GET /drivers`  
**Purpose**: Fetch all drivers for transporter  
**Triggering UI**: Driver List Screen - On screen load  
**Authentication Required**: Yes

**Query Parameters**:
```
?transporterId=transporter_123
&status=ACTIVE
&page=1
&limit=20
```

**Success Response (200)**:
```json
{
  "success": true,
  "drivers": [
    {
      "id": "driver_001",
      "name": "Ramesh Kumar",
      "mobileNumber": "9876543210",
      "licenseNumber": "DL1420110012345",
      "transporterId": "transporter_123",
      "assignedVehicleId": "vehicle_001",
      "assignedVehicleNumber": "GJ-01-AB-1234",
      "isAvailable": true,
      "rating": 4.5,
      "totalTrips": 150,
      "profileImageUrl": "https://cdn.weelo.in/profiles/driver_001.jpg",
      "status": "ACTIVE",
      "createdAt": 1672531200000
    }
  ],
  "total": 30,
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 30,
    "pages": 2
  }
}
```

**UI Behavior**:
- **On Success**: Display driver cards, show availability status
- **On Error**: Show empty state with "Add Driver" button

---

### 12. Add Driver

**Endpoint**: `POST /drivers/add`  
**Purpose**: Add new driver to transporter's team  
**Triggering UI**: Add Driver Screen - When user submits driver form  
**Authentication Required**: Yes

**Request Payload**:
```json
{
  "transporterId": "transporter_123",
  "name": "Ramesh Kumar",
  "mobileNumber": "9876543210",
  "licenseNumber": "DL1420110012345",
  "licenseExpiry": "2025-12-31",
  "emergencyContact": "9876543211",
  "address": "123 Street, Ahmedabad, Gujarat"
}
```

**Validation Rules**:
- `name`: Required, 3-100 characters
- `mobileNumber`: Required, 10 digits, unique
- `licenseNumber`: Required, valid format (DL + state code + year + number)
- `licenseExpiry`: Optional, future date
- `emergencyContact`: Optional, 10 digits

**Success Response (201)**:
```json
{
  "success": true,
  "driver": {
    "id": "driver_new_123",
    "name": "Ramesh Kumar",
    "mobileNumber": "9876543210",
    "status": "ACTIVE",
    "createdAt": 1704614400000
  },
  "message": "Driver added successfully. Invitation sent via SMS.",
  "invitationId": "inv_123"
}
```

**Error Responses**:
- `400`: Invalid license number format
- `409`: Mobile number already registered
- `422`: Validation errors

**UI Behavior**:
- **On Success**: Show success message, navigate to driver list
- **On Error**: Show error message inline, keep form data

---

### 13. Get Driver Performance

**Endpoint**: `GET /drivers/{driverId}/performance`  
**Purpose**: Fetch driver performance metrics  
**Triggering UI**: Driver Details Screen - On screen load  
**Authentication Required**: Yes

**Success Response (200)**:
```json
{
  "success": true,
  "performance": {
    "totalTrips": 150,
    "completedTrips": 145,
    "cancelledTrips": 5,
    "averageRating": 4.5,
    "totalEarnings": 125000.00,
    "totalDistance": 15000.5,
    "avgTripTime": 180,
    "onTimeDeliveryRate": 95.5
  }
}
```

**UI Behavior**:
- Display performance metrics in cards
- Show charts and graphs

---

## Trip Management APIs

### 14. Create Trip

**Endpoint**: `POST /trips/create`  
**Purpose**: Create new trip  
**Triggering UI**: Create Trip Screen - When user submits trip details  
**Authentication Required**: Yes

**Request Payload**:
```json
{
  "transporterId": "transporter_123",
  "vehicleId": "vehicle_001",
  "driverId": "driver_001",
  "pickupLocation": {
    "latitude": 23.0225,
    "longitude": 72.5714,
    "address": "Warehouse A, Ahmedabad, Gujarat",
    "city": "Ahmedabad",
    "state": "Gujarat",
    "pincode": "380001"
  },
  "dropLocation": {
    "latitude": 19.0760,
    "longitude": 72.8777,
    "address": "Warehouse B, Mumbai, Maharashtra",
    "city": "Mumbai",
    "state": "Maharashtra",
    "pincode": "400001"
  },
  "distance": 540.5,
  "estimatedDuration": 480,
  "customerName": "ABC Industries",
  "customerMobile": "9999999999",
  "goodsType": "Electronics",
  "weight": "5000 kg",
  "fare": 15000.00,
  "notes": "Handle with care. Fragile items."
}
```

**Success Response (201)**:
```json
{
  "success": true,
  "trip": {
    "id": "trip_new_123",
    "status": "PENDING",
    "createdAt": 1704614400000
  },
  "message": "Trip created successfully"
}
```

**UI Behavior**:
- **On Success**: Show success message, navigate to trip list
- **On Error**: Show error message, keep form data

---

### 15. Start Trip

**Endpoint**: `POST /trips/{tripId}/start`  
**Purpose**: Mark trip as started  
**Triggering UI**: Trip Details Screen (Driver) - When driver clicks "Start Trip"  
**Authentication Required**: Yes

**Request Payload**:
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

**Success Response (200)**:
```json
{
  "success": true,
  "trip": {
    "id": "trip_123",
    "status": "IN_PROGRESS",
    "startedAt": 1704614400000
  },
  "message": "Trip started successfully"
}
```

**UI Behavior**:
- **On Success**: Update trip status, start GPS tracking, show navigation UI
- **On Error**: Show error message, allow retry

---

### 16. Update Location (GPS Tracking)

**Endpoint**: `POST /trips/{tripId}/location`  
**Purpose**: Send real-time GPS location updates  
**Triggering UI**: Automatic - Every 10-30 seconds during active trip  
**Authentication Required**: Yes

**Request Payload**:
```json
{
  "driverId": "driver_001",
  "latitude": 23.0225,
  "longitude": 72.5714,
  "speed": 65.5,
  "heading": 180.0,
  "timestamp": 1704614400000,
  "accuracy": 10.5
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Location updated"
}
```

**UI Behavior**:
- Silent background update
- No UI change unless error

---

### 17. Complete Trip

**Endpoint**: `POST /trips/{tripId}/complete`  
**Purpose**: Mark trip as completed  
**Triggering UI**: Trip Navigation Screen (Driver) - When driver clicks "Complete Trip"  
**Authentication Required**: Yes

**Request Payload**:
```json
{
  "driverId": "driver_001",
  "endLocation": {
    "latitude": 19.0760,
    "longitude": 72.8777
  },
  "completedAt": 1704632400000,
  "actualDistance": 545.2,
  "notes": "Delivered successfully"
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "trip": {
    "id": "trip_123",
    "status": "COMPLETED",
    "completedAt": 1704632400000,
    "earnings": 15000.00
  },
  "message": "Trip completed successfully"
}
```

**UI Behavior**:
- **On Success**: Stop GPS tracking, show completion message, navigate to dashboard
- **On Error**: Show error message, allow retry

---

## Broadcast System APIs

### 18. Get Active Broadcasts

**Endpoint**: `GET /broadcasts/active`  
**Purpose**: Fetch active broadcasts for transporter  
**Triggering UI**: Broadcast List Screen - On screen load  
**Authentication Required**: Yes

**Query Parameters**:
```
?transporterId=transporter_123
&page=1
&limit=20
```

**Success Response (200)**:
```json
{
  "success": true,
  "broadcasts": [
    {
      "broadcastId": "broadcast_001",
      "customerId": "customer_123",
      "customerName": "ABC Industries",
      "customerMobile": "9999999999",
      "pickupLocation": {
        "latitude": 23.0225,
        "longitude": 72.5714,
        "address": "Ahmedabad, Gujarat"
      },
      "dropLocation": {
        "latitude": 19.0760,
        "longitude": 72.8777,
        "address": "Mumbai, Maharashtra"
      },
      "distance": 540.5,
      "estimatedDuration": 480,
      "totalTrucksNeeded": 10,
      "trucksFilledSoFar": 3,
      "vehicleType": {
        "id": "container",
        "name": "Container"
      },
      "goodsType": "Electronics",
      "weight": "50 tons",
      "farePerTruck": 15000.00,
      "totalFare": 150000.00,
      "status": "ACTIVE",
      "broadcastTime": 1704614400000,
      "expiryTime": 1704618000000,
      "isUrgent": false
    }
  ],
  "total": 5,
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 5,
    "pages": 1
  }
}
```

**UI Behavior**:
- **On Success**: Display broadcast cards, highlight urgent broadcasts
- **On Error**: Show empty state or error message

---

### 19. Accept Broadcast

**Endpoint**: `POST /broadcasts/{broadcastId}/accept`  
**Purpose**: Accept broadcast and select trucks  
**Triggering UI**: Truck Selection Screen - When transporter assigns drivers  
**Authentication Required**: Yes

**Request Payload**:
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
    },
    {
      "vehicleId": "vehicle_002",
      "vehicleNumber": "GJ-01-AB-5678",
      "driverId": "driver_002",
      "driverName": "Suresh Patel"
    },
    {
      "vehicleId": "vehicle_003",
      "vehicleNumber": "GJ-01-AB-9012",
      "driverId": "driver_003",
      "driverName": "Mahesh Shah"
    }
  ]
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "assignmentId": "assignment_123",
  "message": "Broadcast accepted. Drivers notified.",
  "assignments": [
    {
      "assignmentId": "assignment_123_1",
      "driverId": "driver_001",
      "status": "PENDING_DRIVER_RESPONSE"
    }
  ]
}
```

**UI Behavior**:
- **On Success**: Show success message, navigate to assignment tracking screen
- **On Error**: Show error message, allow retry

---

## Driver APIs

### 20. Get Driver Dashboard

**Endpoint**: `GET /driver/dashboard`  
**Purpose**: Fetch driver dashboard data  
**Triggering UI**: Driver Dashboard Screen - On screen load  
**Authentication Required**: Yes

**Query Parameters**:
```
?driverId=driver_001
```

**Success Response (200)**:
```json
{
  "success": true,
  "dashboard": {
    "isAvailable": true,
    "activeTrip": {
      "id": "trip_123",
      "pickupLocation": "Ahmedabad",
      "dropLocation": "Mumbai",
      "distance": 540.5,
      "fare": 15000.00,
      "status": "IN_PROGRESS"
    },
    "todayTrips": 3,
    "todayEarnings": 25000.00,
    "todayDistance": 800.5,
    "weekEarnings": 85000.00,
    "monthEarnings": 325000.00,
    "rating": 4.5,
    "totalTrips": 150,
    "pendingTrips": []
  }
}
```

**UI Behavior**:
- **On Success**: Display dashboard cards, show active trip if exists
- **On Error**: Show error state with retry

---

### 21. Update Driver Availability

**Endpoint**: `PUT /driver/availability`  
**Purpose**: Toggle driver online/offline status  
**Triggering UI**: Driver Dashboard - When driver toggles availability switch  
**Authentication Required**: Yes

**Request Payload**:
```json
{
  "driverId": "driver_001",
  "isAvailable": true
}
```

**Success Response (200)**:
```json
{
  "success": true,
  "message": "Availability updated",
  "isAvailable": true
}
```

**UI Behavior**:
- **On Success**: Update toggle state, show "You're now online/offline"
- **On Error**: Revert toggle, show error message

---

### 22. Get Driver Notifications

**Endpoint**: `GET /driver/notifications`  
**Purpose**: Fetch trip notifications for driver  
**Triggering UI**: Driver Notifications Screen - On screen load  
**Authentication Required**: Yes

**Query Parameters**:
```
?driverId=driver_001
&page=1
&limit=20
```

**Success Response (200)**:
```json
{
  "success": true,
  "notifications": [
    {
      "notificationId": "notif_001",
      "assignmentId": "assignment_123",
      "type": "TRIP_ASSIGNED",
      "pickupAddress": "Ahmedabad, Gujarat",
      "dropAddress": "Mumbai, Maharashtra",
      "distance": 540.5,
      "estimatedDuration": 480,
      "fare": 15000.00,
      "goodsType": "Electronics",
      "receivedAt": 1704614400000,
      "isRead": false,
      "expiryTime": 1704618000000,
      "status": "PENDING_RESPONSE"
    }
  ],
  "total": 5
}
```

**UI Behavior**:
- **On Success**: Display notification list, highlight unread
- **On Error**: Show empty state

---

### 23. Get Driver Earnings

**Endpoint**: `GET /driver/earnings`  
**Purpose**: Fetch detailed earnings breakdown  
**Triggering UI**: Driver Earnings Screen - On screen load  
**Authentication Required**: Yes

**Query Parameters**:
```
?driverId=driver_001
&period=month
&month=1
&year=2026
```

**Success Response (200)**:
```json
{
  "success": true,
  "earnings": {
    "todayEarnings": 25000.00,
    "weekEarnings": 85000.00,
    "monthEarnings": 325000.00,
    "totalEarnings": 1250000.00,
    "todayTrips": 3,
    "totalTrips": 150,
    "breakdown": [
      {
        "date": "2026-01-07",
        "trips": 3,
        "earnings": 25000.00
      }
    ]
  }
}
```

**UI Behavior**:
- Display earnings cards
- Show charts and breakdown

---

## Summary

**Total Endpoints**: 23+  
**Categories**: 9  
**Authentication**: Required for all except login/OTP endpoints  
**Base URL**: `https://api.weelo.in/v1/`

### Quick Reference

| Category | Endpoints | Purpose |
|----------|-----------|---------|
| Authentication | 5 | Login, OTP, Token management |
| Dashboard | 2 | Transporter & Driver dashboards |
| Fleet Management | 4 | CRUD operations for vehicles |
| Driver Management | 3 | CRUD operations for drivers |
| Trip Management | 4 | Trip lifecycle management |
| Broadcast System | 2 | Broadcast management |
| Driver APIs | 4 | Driver-specific operations |
| GPS Tracking | 1 | Real-time location updates |
| Notifications | 1 | Push notification management |

**Next**: See `03_Screen_Wise_Integration.md` for detailed screen flows.
