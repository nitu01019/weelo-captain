# Data Models and Schemas

This document provides complete data structure specifications for all request and response payloads.

---

## Table of Contents

1. [Common Data Types](#common-data-types)
2. [Authentication Models](#authentication-models)
3. [User Models](#user-models)
4. [Vehicle Models](#vehicle-models)
5. [Driver Models](#driver-models)
6. [Trip Models](#trip-models)
7. [Broadcast Models](#broadcast-models)
8. [Dashboard Models](#dashboard-models)
9. [Notification Models](#notification-models)
10. [Validation Rules Summary](#validation-rules-summary)

---

## Common Data Types

### Location
**Usage**: Pickup/drop locations for trips

```typescript
{
  "latitude": number,          // -90 to 90
  "longitude": number,         // -180 to 180
  "address": string,           // Full address
  "city": string,              // Optional
  "state": string,             // Optional
  "pincode": string            // Optional, 6 digits
}
```

**Example**:
```json
{
  "latitude": 23.0225,
  "longitude": 72.5714,
  "address": "Warehouse A, Narol, Ahmedabad, Gujarat",
  "city": "Ahmedabad",
  "state": "Gujarat",
  "pincode": "382405"
}
```

### Pagination
**Usage**: List responses with pagination

```typescript
{
  "page": number,              // Current page (1-indexed)
  "limit": number,             // Items per page
  "total": number,             // Total items
  "pages": number              // Total pages
}
```

### Standard Response Wrapper

**Success**:
```json
{
  "success": true,
  "data": { ... },
  "message": "Operation successful"
}
```

**Error**:
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "field": "fieldName",      // Optional: For validation errors
    "details": { ... }         // Optional: Additional context
  }
}
```

---

## Authentication Models

### SendOTPRequest
```typescript
{
  "mobileNumber": string,      // Required, 10 digits
  "countryCode": string        // Optional, default "+91"
}
```

**Validation**:
- `mobileNumber`: Required, exactly 10 digits, numeric only
- `countryCode`: Optional, format: `+[1-4 digits]`

### SendOTPResponse
```json
{
  "success": true,
  "message": "OTP sent successfully",
  "otpId": "otp_abc123xyz",           // Unique OTP identifier
  "expiryTime": 300,                  // Seconds until expiry (5 minutes)
  "maskedNumber": "98765XXXXX"        // Masked phone number
}
```

### VerifyOTPRequest
```typescript
{
  "mobileNumber": string,      // Required, 10 digits
  "otp": string,               // Required, 6 digits
  "otpId": string,             // Required, from SendOTPResponse
  "deviceInfo": {
    "deviceId": string,        // Required, unique device identifier
    "model": string,           // Optional, device model
    "os": string               // Optional, OS version
  }
}
```

**Validation**:
- `otp`: Required, exactly 6 digits
- `otpId`: Required, valid OTP session
- `deviceId`: Required for session tracking

### VerifyOTPResponse
```json
{
  "success": true,
  "message": "Login successful",
  "user": {
    "id": "user_123",
    "name": "Rajesh Kumar",
    "mobileNumber": "9876543210",
    "email": "rajesh@example.com",
    "roles": ["TRANSPORTER"],              // Array: ["TRANSPORTER", "DRIVER"]
    "profileImageUrl": "https://...",
    "isActive": true
  },
  "tokens": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "refresh_token_xyz",
    "expiresIn": 604800                    // Seconds (7 days)
  },
  "isNewUser": false
}
```

### DriverOTPRequest (Special Flow)
```typescript
{
  "driverPhone": string,       // Required, 10 digits
  "deviceId": string           // Optional
}
```

### DriverOTPResponse
```json
{
  "success": true,
  "message": "OTP sent to your transporter",
  "transporterName": "ABC Logistics",
  "transporterPhone": "91234XXXXX",      // Masked
  "otpSentTo": "transporter",
  "expiryMinutes": 5
}
```

### DriverAuthResponse
```json
{
  "success": true,
  "message": "Login successful",
  "driver": {
    "id": "driver_123",
    "phone": "9876543210",
    "name": "Ramesh Kumar",
    "transporterId": "transporter_123",
    "transporterName": "ABC Logistics",
    "status": "active",                    // active, inactive, suspended
    "licenseNumber": "DL1420110012345",
    "vehicleAssigned": "vehicle_001"
  },
  "authToken": "jwt_token",
  "refreshToken": "refresh_token"
}
```

---

## User Models

### User (Core User Object)
```typescript
{
  "id": string,                // Unique user ID
  "name": string,              // Full name
  "mobileNumber": string,      // 10 digits
  "email": string,             // Optional
  "roles": string[],           // ["TRANSPORTER", "DRIVER"]
  "profileImageUrl": string,   // Optional, CDN URL
  "createdAt": number,         // Unix timestamp (milliseconds)
  "isActive": boolean
}
```

### TransporterProfile
```typescript
{
  "userId": string,
  "companyName": string,       // Optional
  "gstNumber": string,         // Optional, 15 characters
  "address": string,           // Optional
  "totalVehicles": number,
  "totalDrivers": number,
  "verificationStatus": string // "PENDING", "VERIFIED", "REJECTED"
}
```

### DriverProfile
```typescript
{
  "userId": string,
  "licenseNumber": string,
  "licenseExpiryDate": number, // Unix timestamp
  "experience": number,        // Years
  "assignedTransporterId": string,
  "isAvailable": boolean,
  "rating": number,            // 0.0 to 5.0
  "totalTrips": number,
  "verificationStatus": string
}
```

---

## Vehicle Models

### Vehicle (Complete Object)
```typescript
{
  "id": string,
  "transporterId": string,
  "category": {
    "id": string,              // "open", "container", "lcv", "mini", "trailer", "tipper", "tanker", "dumper", "bulker"
    "name": string,            // "Open Truck", "Container", etc.
    "icon": string,            // Emoji or icon identifier
    "imageResId": number       // Optional: Resource ID for mobile
  },
  "subtype": {
    "id": string,              // "32_single", "17_feet", etc.
    "name": string,            // "32 Feet Single Axle", "17 Feet"
    "capacityTons": number     // Capacity in tons
  },
  "vehicleNumber": string,     // Format: XX-00-XX-0000
  "model": string,             // Optional: "Tata Prima", "Ashok Leyland"
  "year": number,              // Optional: 2000-2026
  "assignedDriverId": string,  // Optional
  "assignedDriverName": string,// Optional
  "status": string,            // "AVAILABLE", "IN_TRANSIT", "MAINTENANCE", "INACTIVE"
  "lastServiceDate": number,   // Optional, Unix timestamp
  "insuranceExpiryDate": number, // Optional
  "registrationExpiryDate": number, // Optional
  "createdAt": number
}
```

### AddVehicleRequest
```typescript
{
  "transporterId": string,     // Required
  "category": string,          // Required, one of 9 categories
  "subtype": string,           // Required, must match category
  "vehicleNumber": string,     // Required, format: XX-00-XX-0000
  "model": string,             // Optional, max 100 chars
  "year": number,              // Optional, 1900-2026
  "capacity": string,          // Auto-calculated from subtype
  "documents": {               // Optional
    "registrationCert": string, // URL
    "insurance": string,        // URL
    "permit": string            // URL
  }
}
```

**Validation Rules**:
- `vehicleNumber`: Must match regex `^[A-Z]{2}-\d{2}-[A-Z]{2}-\d{4}$`
- Must be unique across all vehicles
- `category`: Must be one of: open, container, lcv, mini, trailer, tipper, tanker, dumper, bulker
- `subtype`: Must belong to selected category

### VehicleListResponse
```typescript
{
  "success": boolean,
  "vehicles": Vehicle[],
  "total": number,
  "pagination": {
    "page": number,
    "limit": number,
    "total": number,
    "pages": number
  }
}
```

### UpdateVehicleRequest
```typescript
{
  "status": string,            // Optional: "AVAILABLE", "MAINTENANCE", etc.
  "model": string,             // Optional
  "year": number,              // Optional
  "assignedDriverId": string   // Optional
}
```

---

## Driver Models

### Driver (Complete Object)
```typescript
{
  "id": string,
  "name": string,              // 3-100 characters
  "mobileNumber": string,      // 10 digits, unique
  "licenseNumber": string,     // Format: DL + state code + year + number
  "transporterId": string,
  "assignedVehicleId": string, // Optional
  "assignedVehicleNumber": string, // Optional
  "isAvailable": boolean,
  "rating": number,            // 0.0 to 5.0
  "totalTrips": number,
  "profileImageUrl": string,   // Optional
  "status": string,            // "ACTIVE", "ON_TRIP", "INACTIVE", "SUSPENDED"
  "createdAt": number
}
```

### AddDriverRequest
```typescript
{
  "transporterId": string,     // Required
  "name": string,              // Required, 3-100 chars
  "mobileNumber": string,      // Required, 10 digits
  "licenseNumber": string,     // Required, format: DL\d{12,14}
  "licenseExpiry": string,     // Optional, ISO date "2025-12-31"
  "emergencyContact": string,  // Optional, 10 digits
  "address": string            // Optional, max 500 chars
}
```

**Validation Rules**:
- `name`: 3-100 characters, letters and spaces only
- `mobileNumber`: Exactly 10 digits, unique in system
- `licenseNumber`: Format `^DL\d{12,14}$`, unique
- `licenseExpiry`: Must be future date

### AddDriverResponse
```json
{
  "success": true,
  "driver": { ...Driver object... },
  "message": "Driver added successfully. Invitation sent via SMS.",
  "invitationId": "inv_123"
}
```

### DriverEarnings
```typescript
{
  "driverId": string,
  "todayEarnings": number,     // Amount in rupees
  "weekEarnings": number,
  "monthEarnings": number,
  "totalEarnings": number,
  "todayTrips": number,
  "totalTrips": number,
  "breakdown": [               // Optional: Daily breakdown
    {
      "date": string,          // ISO date "2026-01-07"
      "trips": number,
      "earnings": number
    }
  ]
}
```

### DriverPerformance
```typescript
{
  "driverId": string,
  "rating": number,            // 0.0 to 5.0
  "totalTrips": number,
  "completedTrips": number,
  "cancelledTrips": number,
  "avgTripTime": number,       // Minutes
  "totalDistance": number,     // Kilometers
  "onTimeDeliveryRate": number // Percentage 0-100
}
```

---

## Trip Models

### Trip (Complete Object)
```typescript
{
  "id": string,
  "transporterId": string,
  "vehicleId": string,
  "vehicleNumber": string,     // For display
  "driverId": string,          // Optional, assigned later
  "driverName": string,        // Optional
  "pickupLocation": Location,
  "dropLocation": Location,
  "distance": number,          // Kilometers
  "estimatedDuration": number, // Minutes
  "status": string,            // See TripStatus enum below
  "customerName": string,
  "customerMobile": string,    // 10 digits
  "goodsType": string,         // "Electronics", "Food", etc.
  "weight": string,            // Optional: "5000 kg"
  "fare": number,              // Amount in rupees
  "createdAt": number,
  "startedAt": number,         // Optional
  "completedAt": number,       // Optional
  "notes": string              // Optional
}
```

### TripStatus Enum
```
PENDING        // Created, waiting for driver assignment
ASSIGNED       // Assigned to driver
ACCEPTED       // Driver accepted
REJECTED       // Driver rejected
IN_PROGRESS    // Trip started, driver on the way
COMPLETED      // Trip finished successfully
CANCELLED      // Trip cancelled
```

### CreateTripRequest
```typescript
{
  "transporterId": string,     // Required
  "vehicleId": string,         // Required
  "driverId": string,          // Optional, can assign later
  "pickupLocation": Location,  // Required
  "dropLocation": Location,    // Required
  "distance": number,          // Required, kilometers
  "estimatedDuration": number, // Required, minutes
  "customerName": string,      // Required, 3-100 chars
  "customerMobile": string,    // Required, 10 digits
  "goodsType": string,         // Required, max 100 chars
  "weight": string,            // Optional
  "fare": number,              // Required, positive number
  "notes": string              // Optional, max 1000 chars
}
```

### StartTripRequest
```typescript
{
  "driverId": string,
  "startLocation": {
    "latitude": number,
    "longitude": number
  },
  "timestamp": number          // Unix timestamp
}
```

### CompleteTripRequest
```typescript
{
  "driverId": string,
  "endLocation": {
    "latitude": number,
    "longitude": number
  },
  "completedAt": number,
  "actualDistance": number,    // Optional: Actual distance traveled
  "notes": string              // Optional: Delivery notes
}
```

### UpdateLocationRequest (GPS Tracking)
```typescript
{
  "driverId": string,
  "latitude": number,          // -90 to 90
  "longitude": number,         // -180 to 180
  "speed": number,             // km/h
  "heading": number,           // 0-360 degrees
  "timestamp": number,         // Unix timestamp
  "accuracy": number           // Optional: GPS accuracy in meters
}
```

**Frequency**: Every 10-30 seconds during active trip

### TripTracking (Response)
```typescript
{
  "tripId": string,
  "driverId": string,
  "currentLocation": Location,
  "currentLatitude": number,
  "currentLongitude": number,
  "tripStatus": string,
  "startedAt": number,
  "currentSpeed": number,      // km/h
  "heading": number,           // Degrees
  "lastUpdated": number,       // Unix timestamp
  "isLocationSharing": boolean
}
```

---

## Broadcast Models

### BroadcastTrip (Customer Broadcast)
```typescript
{
  "broadcastId": string,
  "customerId": string,
  "customerName": string,
  "customerMobile": string,    // 10 digits
  "pickupLocation": Location,
  "dropLocation": Location,
  "distance": number,          // Kilometers
  "estimatedDuration": number, // Minutes
  "totalTrucksNeeded": number, // Total trucks required
  "trucksFilledSoFar": number, // Already assigned by other transporters
  "vehicleType": {             // Required vehicle type
    "id": string,
    "name": string
  },
  "goodsType": string,
  "weight": string,            // Optional
  "farePerTruck": number,      // Fare for each truck
  "totalFare": number,         // Total fare for all trucks
  "status": string,            // See BroadcastStatus enum
  "broadcastTime": number,     // Unix timestamp
  "expiryTime": number,        // Optional
  "isUrgent": boolean,
  "notes": string              // Optional
}
```

### BroadcastStatus Enum
```
ACTIVE             // Currently broadcasting
PARTIALLY_FILLED   // Some trucks assigned, need more
FULLY_FILLED       // All trucks assigned
EXPIRED            // Time limit exceeded
CANCELLED          // Customer cancelled
```

### AcceptBroadcastRequest
```typescript
{
  "transporterId": string,
  "trucksTaken": number,       // Number of trucks transporter is taking
  "assignments": [             // Array of vehicle-driver assignments
    {
      "vehicleId": string,
      "vehicleNumber": string,
      "driverId": string,
      "driverName": string
    }
  ]
}
```

**Validation**:
- `trucksTaken`: Must equal `assignments.length`
- `trucksTaken`: Must not exceed `(totalTrucksNeeded - trucksFilledSoFar)`
- All `vehicleId` and `driverId` must be valid and available

### AcceptBroadcastResponse
```json
{
  "success": true,
  "assignmentId": "assignment_123",
  "message": "Broadcast accepted. Drivers notified.",
  "assignments": [
    {
      "assignmentId": "assignment_123_1",
      "driverId": "driver_001",
      "vehicleId": "vehicle_001",
      "status": "PENDING_DRIVER_RESPONSE"
    }
  ]
}
```

### TripAssignment
```typescript
{
  "assignmentId": string,
  "broadcastId": string,
  "transporterId": string,
  "trucksTaken": number,
  "assignments": [
    {
      "driverId": string,
      "driverName": string,
      "vehicleId": string,
      "vehicleNumber": string,
      "status": string         // "PENDING", "ACCEPTED", "DECLINED"
    }
  ],
  "pickupLocation": Location,
  "dropLocation": Location,
  "distance": number,
  "farePerTruck": number,
  "goodsType": string,
  "assignedAt": number,
  "status": string             // See AssignmentStatus enum
}
```

### AssignmentStatus Enum
```
PENDING_DRIVER_RESPONSE  // Waiting for driver to accept/decline
DRIVER_ACCEPTED          // Driver accepted
DRIVER_DECLINED          // Driver declined
TRIP_STARTED             // Driver started trip
TRIP_COMPLETED           // Trip finished
CANCELLED                // Assignment cancelled
```

### DriverTripNotification
```typescript
{
  "notificationId": string,
  "assignmentId": string,
  "driverId": string,
  "pickupAddress": string,
  "dropAddress": string,
  "distance": number,
  "estimatedDuration": number, // Minutes
  "fare": number,
  "goodsType": string,
  "receivedAt": number,
  "isRead": boolean,
  "soundPlayed": boolean,
  "expiryTime": number,        // Optional
  "status": string             // "PENDING_RESPONSE", "ACCEPTED", "DECLINED", "EXPIRED"
}
```

---

## Dashboard Models

### TransporterDashboard
```typescript
{
  "totalVehicles": number,
  "activeVehicles": number,    // AVAILABLE + IN_TRANSIT
  "totalDrivers": number,
  "activeDrivers": number,     // ACTIVE + ON_TRIP
  "activeTrips": number,       // Currently in progress
  "todayRevenue": number,      // Rupees
  "todayTrips": number,
  "completedTrips": number,    // All time
  "recentTrips": Trip[]        // Last 5-10 trips
}
```

### DriverDashboard
```typescript
{
  "isAvailable": boolean,      // Driver online/offline status
  "activeTrip": Trip,          // Optional, current trip if any
  "todayTrips": number,
  "todayEarnings": number,
  "todayDistance": number,     // Kilometers
  "weekEarnings": number,
  "monthEarnings": number,
  "rating": number,            // 0.0 to 5.0
  "totalTrips": number,
  "pendingTrips": Trip[]       // Optional, pending assignments
}
```

---

## Notification Models

### Notification (Generic)
```typescript
{
  "id": string,
  "userId": string,
  "title": string,             // Max 100 chars
  "message": string,           // Max 500 chars
  "type": string,              // See NotificationType enum
  "timestamp": number,
  "isRead": boolean,
  "data": object               // Optional, type-specific data
}
```

### NotificationType Enum
```
TRIP_ASSIGNED          // Trip assigned to driver
TRIP_ACCEPTED          // Driver accepted trip
TRIP_REJECTED          // Driver rejected trip
TRIP_STARTED           // Trip started
TRIP_COMPLETED         // Trip completed
TRIP_CANCELLED         // Trip cancelled
DRIVER_REGISTERED      // New driver registered
VEHICLE_ADDED          // New vehicle added
PAYMENT_RECEIVED       // Payment received
GENERAL                // General notification
```

### FCM Push Notification Payload
```typescript
{
  "to": string,                // FCM token
  "notification": {
    "title": string,
    "body": string,
    "sound": "default"
  },
  "data": {
    "type": string,            // NotificationType
    "entityId": string,        // tripId, driverId, etc.
    "action": string,          // "VIEW_TRIP", "ACCEPT_TRIP", etc.
    "timestamp": string
  }
}
```

---

## Validation Rules Summary

### Phone Numbers
- **Format**: Exactly 10 digits
- **Regex**: `^\d{10}$`
- **Unique**: Must be unique in system

### Vehicle Numbers
- **Format**: XX-00-XX-0000 (State-District-Series-Number)
- **Regex**: `^[A-Z]{2}-\d{2}-[A-Z]{2}-\d{4}$`
- **Example**: `GJ-01-AB-1234`
- **Unique**: Must be unique across all vehicles

### License Numbers
- **Format**: DL + 12-14 digits
- **Regex**: `^DL\d{12,14}$`
- **Example**: `DL1420110012345`
- **Unique**: Must be unique per driver

### OTP
- **Format**: Exactly 6 digits
- **Expiry**: 5 minutes (300 seconds)
- **Max Attempts**: 3 invalid attempts before lockout

### Names
- **Min Length**: 3 characters
- **Max Length**: 100 characters
- **Allowed**: Letters, spaces, hyphens, dots

### Fare/Earnings
- **Type**: Number (float)
- **Min**: 0.01
- **Max**: 999999.99
- **Currency**: INR (Indian Rupees)

### Coordinates
- **Latitude**: -90 to 90
- **Longitude**: -180 to 180
- **Accuracy**: Up to 6 decimal places

### Dates
- **Format**: Unix timestamp (milliseconds since epoch)
- **Example**: `1704614400000` (January 7, 2026)

### Timestamps
- **Format**: Unix timestamp in milliseconds
- **Timezone**: UTC
- **Client Conversion**: App handles timezone conversion

---

## Field Length Limits

| Field | Min | Max |
|-------|-----|-----|
| name | 3 | 100 |
| mobileNumber | 10 | 10 |
| licenseNumber | 12 | 15 |
| vehicleNumber | 13 | 13 |
| address | 10 | 500 |
| goodsType | 3 | 100 |
| notes | 0 | 1000 |
| otp | 6 | 6 |

---

**Next**: See `05_Auth_and_Security_Expectations.md` for authentication implementation details.
