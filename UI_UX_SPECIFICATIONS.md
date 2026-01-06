# 🎨 UI/UX Specifications for Backend Developer

## Overview
This document explains the **exact UI screens** built in the Android app and what **API responses** the backend must provide. The frontend is **already built** - backend must match these specifications exactly.

---

## 📱 App Structure

```
WEELO LOGISTICS APP
├── Customer App (Future - Not built yet)
├── Transporter App ✅ BUILT
│   ├── Dashboard
│   ├── Broadcast List (Receive notifications)
│   ├── Fleet Management (Vehicles)
│   ├── Driver Management
│   ├── Trip Management
│   └── Assignment Flow
└── Driver App ✅ BUILT
    ├── Dashboard
    ├── Trip Notification (ALARM)
    ├── Trip Navigation
    └── Trip History
```

---

## 🚨 IMPORTANT: Booking Rules

### ❌ NO Direct Booking by Transporter
- Transporter **CANNOT** create their own bookings
- Transporter **CANNOT** initiate trips manually
- All bookings come from **BROADCAST SYSTEM ONLY**

### ✅ Only Broadcast-Based Flow
```
1. Customer creates broadcast
2. Transporter receives notification
3. Transporter responds to broadcast
4. Transporter assigns drivers
5. Drivers receive notification
```

**Backend must ONLY allow broadcast-based trip creation!**

---

## 📊 TRANSPORTER APP - UI Screens

### 1. Transporter Dashboard Screen

**File:** `TransporterDashboardScreen.kt`

**What it shows:**
- Today's statistics (trips, revenue, active vehicles)
- Quick action cards
- Recent broadcast notifications
- Fleet summary

**API Needed:**
```
GET /dashboard/transporter
Authorization: Bearer <jwt_token>
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "transporterName": "Kumar Transport",
    "todayStats": {
      "activeTrips": 5,
      "completedTrips": 12,
      "totalRevenue": 45000.00,
      "activeVehicles": 8,
      "totalVehicles": 15
    },
    "recentBroadcasts": [
      {
        "broadcastId": "BC-2026-001-ABC123",
        "pickupCity": "New Delhi",
        "dropCity": "Noida",
        "trucksNeeded": 10,
        "trucksRemaining": 3,
        "farePerTruck": 2500.00,
        "isUrgent": true,
        "postedTime": 1735992600000
      }
    ],
    "activeAssignments": [
      {
        "assignmentId": "ASSIGN-001",
        "trucksTaken": 3,
        "driversAccepted": 2,
        "driversPending": 1,
        "status": "PARTIALLY_ACCEPTED"
      }
    ]
  }
}
```

---

### 2. Broadcast List Screen (Main Screen)

**File:** `BroadcastListScreen.kt`

**What it shows:**
- All active broadcasts (customer needs)
- Search and filter options
- Broadcast cards with details
- Distance from transporter location

**UI Elements:**
- 🔔 Notification badge
- 🔍 Search bar
- 🎚️ Filter chips (All, Urgent, Near Me)
- 📋 Broadcast cards showing:
  - Pickup → Drop locations
  - Vehicle type icon
  - Number of trucks needed/remaining
  - Fare per truck
  - Distance from transporter
  - "URGENT" badge if urgent
  - Time remaining

**API Needed:**
```
GET /broadcasts/active?vehicleType=all&maxDistance=50&sortBy=urgent
Authorization: Bearer <jwt_token>
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "broadcasts": [
      {
        "broadcastId": "BC-2026-001-ABC123",
        "customerId": "CUST-123456",
        "customerName": "Rajesh Kumar",
        "customerMobile": "+91-9876543210",
        "pickupLocation": {
          "latitude": 28.7041,
          "longitude": 77.1025,
          "address": "Connaught Place, New Delhi",
          "city": "New Delhi"
        },
        "dropLocation": {
          "latitude": 28.5355,
          "longitude": 77.3910,
          "address": "Sector 18, Noida",
          "city": "Noida"
        },
        "distance": 32.5,
        "estimatedDuration": 45,
        "totalTrucksNeeded": 10,
        "trucksFilledSoFar": 7,
        "trucksRemaining": 3,
        "vehicleType": "CONTAINER",
        "vehicleTypeIcon": "🚚",
        "goodsType": "Electronics",
        "weight": "5000 kg",
        "farePerTruck": 2500.00,
        "totalFare": 25000.00,
        "status": "PARTIALLY_FILLED",
        "broadcastTime": 1735992600000,
        "expiryTime": 1735996200000,
        "timeRemaining": 3600,
        "isUrgent": true,
        "notes": "Fragile items",
        "distanceFromTransporter": 12.3
      }
    ],
    "pagination": {
      "currentPage": 1,
      "totalPages": 3,
      "totalItems": 45
    }
  }
}
```

**Filter Options UI Expects:**
- "All" - All broadcasts
- "Urgent" - isUrgent = true
- "Near Me" - distanceFromTransporter < 20km

---

### 3. Fleet List Screen (Vehicle Management)

**File:** `FleetListScreen.kt`

**What it shows:**
- All vehicles owned by transporter
- Search by vehicle number
- Filter by status (All, Available, In Transit, Maintenance)
- Vehicle cards with:
  - Vehicle icon/emoji
  - Vehicle number (e.g., DL-1A-1234)
  - Vehicle type name
  - Capacity
  - Status chip

**API Needed:**
```
GET /vehicles?transporterId=<id>&status=all
Authorization: Bearer <jwt_token>
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "vehicles": [
      {
        "vehicleId": "VEH-001-CONT-1234",
        "transporterId": "TRANS-567",
        "vehicleNumber": "DL-1A-1234",
        "vehicleType": "CONTAINER",
        "category": {
          "name": "Container",
          "icon": "🚛",
          "baseType": "CONTAINER"
        },
        "displayName": "32ft Container Truck",
        "capacity": "32 tons",
        "capacityText": "Capacity: 32 tons",
        "status": "AVAILABLE",
        "assignedDriverId": null,
        "assignedDriverName": null,
        "lastServiceDate": 1735000000000,
        "registrationExpiry": 1767225600000,
        "insuranceExpiry": 1767225600000,
        "totalTrips": 156,
        "totalDistance": 45230.5
      },
      {
        "vehicleId": "VEH-002-OPEN-5678",
        "vehicleNumber": "DL-2B-5678",
        "vehicleType": "OPEN",
        "category": {
          "name": "Open Body",
          "icon": "🚚",
          "baseType": "OPEN"
        },
        "displayName": "22ft Open Body Truck",
        "capacity": "15 tons",
        "capacityText": "Capacity: 15 tons",
        "status": "IN_TRANSIT",
        "assignedDriverId": "DRV-001",
        "assignedDriverName": "Ramesh Singh",
        "totalTrips": 89
      }
    ],
    "summary": {
      "totalVehicles": 15,
      "availableVehicles": 8,
      "inTransit": 5,
      "maintenance": 2
    }
  }
}
```

**Vehicle Status Values:**
- `AVAILABLE` - Ready for assignment
- `IN_TRANSIT` - Currently on a trip
- `MAINTENANCE` - Under maintenance
- `INACTIVE` - Not operational

---

### 4. Driver List Screen

**File:** `DriverListScreen.kt`

**What it shows:**
- All drivers working for transporter
- Search by name or mobile
- Filter by status (All, Available, On Trip, Inactive)
- Driver cards with:
  - Driver avatar/icon
  - Name
  - Mobile number
  - Rating (⭐ 4.5)
  - Total trips
  - Status chip

**API Needed:**
```
GET /drivers?transporterId=<id>&status=all
Authorization: Bearer <jwt_token>
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "drivers": [
      {
        "driverId": "DRV-001",
        "transporterId": "TRANS-567",
        "name": "Ramesh Singh",
        "mobileNumber": "+91-9876543210",
        "email": "ramesh@example.com",
        "licenseNumber": "DL1420110012345",
        "licenseExpiry": 1767225600000,
        "profileImageUrl": "https://...",
        "assignedVehicleId": "VEH-001-CONT-1234",
        "assignedVehicleNumber": "DL-1A-1234",
        "status": "ACTIVE",
        "isAvailable": true,
        "rating": 4.5,
        "totalTrips": 234,
        "completedTrips": 230,
        "cancelledTrips": 4,
        "isOnline": true,
        "lastSeen": 1735993500000
      },
      {
        "driverId": "DRV-002",
        "name": "Suresh Kumar",
        "mobileNumber": "+91-9123456789",
        "licenseNumber": "DL1420110054321",
        "status": "ON_TRIP",
        "isAvailable": false,
        "rating": 4.8,
        "totalTrips": 189,
        "currentTripId": "TRIP-001-ABC"
      }
    ],
    "summary": {
      "totalDrivers": 20,
      "availableDrivers": 12,
      "onTrip": 6,
      "offline": 2
    }
  }
}
```

**Driver Status Values:**
- `ACTIVE` - Active driver (check isAvailable for current availability)
- `ON_TRIP` - Currently on a trip
- `INACTIVE` - Not working
- `SUSPENDED` - Account suspended

---


### 5. Add Vehicle Screen (Multi-Step)

**File:** `AddVehicleScreen.kt`

**What it shows:**
- **Step 1:** Select vehicle type (Container, Open, Trailer, etc.)
- **Step 2:** Select category and subtype with images
- **Step 3:** Enter vehicle details (number, capacity, documents)

**Vehicle Types Available:**
```
🚛 Container Trucks
🚚 Open Body Trucks  
🚜 Tractor/Trailer
🛻 Mini Trucks
🚐 Tempo/LCV
🚛 Tanker
⛟ Tipper
🚛 Bulker
🚛 Dumper
```

**API Needed:**
```
POST /vehicles
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "transporterId": "TRANS-567",
  "vehicleNumber": "DL-1A-9999",
  "vehicleType": "CONTAINER",
  "categoryName": "Container",
  "subtypeName": "32ft Container",
  "capacity": "32 tons",
  "manufacturer": "Tata",
  "model": "LPT 3118",
  "year": 2023,
  "registrationDate": 1704067200000,
  "registrationExpiry": 1767225600000,
  "insuranceExpiry": 1767225600000,
  "fitnessExpiry": 1767225600000,
  "pollutionExpiry": 1751328000000
}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "vehicleId": "VEH-003-CONT-9999",
    "vehicleNumber": "DL-1A-9999",
    "vehicleType": "CONTAINER",
    "displayName": "32ft Container Truck",
    "status": "AVAILABLE",
    "createdAt": 1735993500000
  },
  "message": "Vehicle added successfully"
}
```

---

### 6. Add Driver Screen

**File:** `AddDriverScreen.kt`

**What it shows:**
- Driver information form:
  - Full Name
  - Mobile Number (10 digits)
  - License Number
  - License Valid Till
  - Emergency Contact
  - Address (optional)
- Info card: "Driver will receive SMS invitation"

**API Needed:**
```
POST /drivers
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "transporterId": "TRANS-567",
  "name": "Vijay Sharma",
  "mobileNumber": "+91-9012345678",
  "licenseNumber": "DL1420110098765",
  "licenseExpiry": 1767225600000,
  "emergencyContact": "+91-9876543210",
  "address": "Sector 15, Noida"
}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "driverId": "DRV-003",
    "name": "Vijay Sharma",
    "mobileNumber": "+91-9012345678",
    "status": "ACTIVE",
    "isAvailable": true,
    "smsSent": true,
    "createdAt": 1735993500000
  },
  "message": "Driver added successfully. SMS invitation sent."
}
```

**Backend Actions:**
1. Create driver record
2. Send SMS invitation to driver's mobile
3. SMS should contain app download link
4. Return success response

---

### 7. Truck Selection Screen (Assignment Flow)

**File:** `TruckSelectionScreen.kt`

**What it shows:**
- Broadcast details at top
- Number of trucks needed
- Slider: "How many trucks can you provide?" (1 to remaining trucks)
- List of available vehicles matching the type
- Select vehicles (multi-select checkboxes)
- For each selected vehicle → assign driver
- "Confirm Assignment" button

**User Flow:**
```
1. Transporter sees broadcast needs 10 CONTAINER trucks
2. Transporter moves slider to 3 (can provide 3 trucks)
3. System shows available CONTAINER trucks
4. Transporter selects 3 vehicles
5. For each vehicle, selects a driver from dropdown
6. Clicks "Confirm Assignment"
```

**API Needed:**
```
GET /vehicles/available?vehicleType=CONTAINER&transporterId=TRANS-567
Authorization: Bearer <jwt_token>
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "availableVehicles": [
      {
        "vehicleId": "VEH-001-CONT-1234",
        "vehicleNumber": "DL-1A-1234",
        "displayName": "32ft Container Truck",
        "capacity": "32 tons",
        "status": "AVAILABLE"
      },
      {
        "vehicleId": "VEH-004-CONT-7890",
        "vehicleNumber": "DL-3C-7890",
        "displayName": "32ft Container Truck",
        "capacity": "32 tons",
        "status": "AVAILABLE"
      }
    ],
    "count": 2
  }
}
```

---

### 8. Driver Assignment Screen

**File:** `DriverAssignmentScreen.kt`

**What it shows:**
- List of selected vehicles
- For each vehicle:
  - Vehicle number and details
  - Dropdown to select driver
  - Only shows available drivers
- "Confirm & Notify Drivers" button

**API Needed:**
```
GET /drivers/available?transporterId=TRANS-567
Authorization: Bearer <jwt_token>
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "availableDrivers": [
      {
        "driverId": "DRV-001",
        "name": "Ramesh Singh",
        "mobileNumber": "+91-9876543210",
        "rating": 4.5,
        "totalTrips": 234,
        "isOnline": true
      },
      {
        "driverId": "DRV-002",
        "name": "Suresh Kumar",
        "mobileNumber": "+91-9123456789",
        "rating": 4.8,
        "totalTrips": 189,
        "isOnline": false
      }
    ],
    "count": 2
  }
}
```

**Final Assignment API:**
```
POST /assignments
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "broadcastId": "BC-2026-001-ABC123",
  "transporterId": "TRANS-567",
  "trucksTaken": 3,
  "assignments": [
    {
      "vehicleId": "VEH-001-CONT-1234",
      "vehicleNumber": "DL-1A-1234",
      "driverId": "DRV-001",
      "driverName": "Ramesh Singh",
      "driverMobile": "+91-9876543210"
    },
    {
      "vehicleId": "VEH-004-CONT-7890",
      "vehicleNumber": "DL-3C-7890",
      "driverId": "DRV-002",
      "driverName": "Suresh Kumar",
      "driverMobile": "+91-9123456789"
    },
    {
      "vehicleId": "VEH-007-CONT-4567",
      "vehicleNumber": "DL-5D-4567",
      "driverId": "DRV-005",
      "driverName": "Anil Verma",
      "driverMobile": "+91-9988776655"
    }
  ],
  "estimatedArrivalTime": "2026-01-05T14:30:00Z",
  "notes": "All drivers ready"
}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "broadcastId": "BC-2026-001-ABC123",
    "transporterId": "TRANS-567",
    "trucksTaken": 3,
    "assignments": [
      {
        "driverId": "DRV-001",
        "driverName": "Ramesh Singh",
        "vehicleNumber": "DL-1A-1234",
        "status": "PENDING",
        "notificationSent": true,
        "notificationId": "NOTIF-001-ABC"
      },
      {
        "driverId": "DRV-002",
        "driverName": "Suresh Kumar",
        "vehicleNumber": "DL-3C-7890",
        "status": "PENDING",
        "notificationSent": true,
        "notificationId": "NOTIF-002-DEF"
      },
      {
        "driverId": "DRV-005",
        "driverName": "Anil Verma",
        "vehicleNumber": "DL-5D-4567",
        "status": "PENDING",
        "notificationSent": true,
        "notificationId": "NOTIF-003-GHI"
      }
    ],
    "assignedAt": 1735992900000,
    "status": "PENDING_DRIVER_RESPONSE",
    "driversNotified": 3
  },
  "message": "Assignment created and notifications sent to 3 drivers"
}
```

**Backend Must:**
1. Validate broadcast exists and has remaining trucks
2. Check all vehicles are available
3. Check all drivers are available
4. Create assignment record
5. Update vehicle status to "ASSIGNED"
6. Update driver status to "ON_TRIP"
7. **SEND PUSH NOTIFICATIONS TO ALL DRIVERS** (CRITICAL!)
8. Update broadcast trucks_filled count
9. Return success response

---

## 📱 DRIVER APP - UI Screens

### 9. Driver Dashboard Screen

**File:** `DriverDashboardScreen.kt`

**What it shows:**
- Greeting: "Hello Driver! 👋"
- Availability toggle (Available/Offline)
- Today's summary cards:
  - Trips count
  - Distance covered
  - Earnings (₹)
- Current trip card (if on trip)

**API Needed:**
```
GET /dashboard/driver
Authorization: Bearer <jwt_token>
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "driverId": "DRV-001",
    "driverName": "Ramesh Singh",
    "isAvailable": true,
    "isOnline": true,
    "todayStats": {
      "tripsCompleted": 3,
      "distanceCovered": 125.5,
      "earnings": 7500.00,
      "hoursOnline": 6.5
    },
    "currentTrip": null,
    "pendingNotifications": 0,
    "rating": 4.5,
    "totalTrips": 234
  }
}
```

**Update Availability API:**
```
PATCH /drivers/{driverId}/availability
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "isAvailable": true
}
```

---

### 10. Trip Accept/Decline Screen (FULL-SCREEN ALARM) 🚨

**File:** `TripAcceptDeclineScreen.kt`

**What it shows:**
- **FULL-SCREEN overlay** (cannot be dismissed easily)
- **Loud alarm sound playing**
- **Phone vibrating**
- Trip details:
  - "🚛 NEW TRIP ASSIGNMENT!"
  - Pickup location
  - Drop location
  - Distance
  - Fare amount
  - Goods type
  - Customer name & mobile
  - Transporter name
  - Vehicle number assigned
- **Two large buttons:**
  - 🟢 ACCEPT (Green)
  - 🔴 DECLINE (Red)
- **Countdown timer:** "Expires in 4:59"
- **Auto-decline after 5 minutes**

**This is triggered by FCM Push Notification:**

**FCM Payload Sent by Backend:**
```json
{
  "to": "driver_fcm_token",
  "priority": "high",
  "notification": {
    "title": "🚛 New Trip Assignment!",
    "body": "Connaught Place → Noida | ₹2,500 | 32.5 km",
    "sound": "trip_alarm.mp3"
  },
  "data": {
    "type": "TRIP_ASSIGNMENT",
    "notificationId": "NOTIF-001-ABC",
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "broadcastId": "BC-2026-001-ABC123",
    "fullScreenAlarm": "true",
    "expiryTime": "1735993200000",
    "tripData": "{...full trip JSON...}"
  }
}
```

**Driver Accepts - API Call:**
```
POST /notifications/{notificationId}/accept
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "driverId": "DRV-001",
  "acceptedAt": 1735993020000,
  "currentLocation": {
    "latitude": 28.6500,
    "longitude": 77.2300
  }
}
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "notificationId": "NOTIF-001-ABC",
    "tripId": "TRIP-001-ABC",
    "status": "ACCEPTED",
    "tripDetails": {
      "tripId": "TRIP-001-ABC",
      "broadcastId": "BC-2026-001-ABC123",
      "pickupLocation": {
        "latitude": 28.7041,
        "longitude": 77.1025,
        "address": "Connaught Place, New Delhi"
      },
      "dropLocation": {
        "latitude": 28.5355,
        "longitude": 77.3910,
        "address": "Sector 18, Noida"
      },
      "distance": 32.5,
      "fare": 2500.00,
      "customerName": "Rajesh Kumar",
      "customerMobile": "+91-9876543210",
      "vehicleNumber": "DL-1A-1234"
    },
    "gpsTracking": {
      "trackingId": "TRACK-001-XYZ",
      "enabled": true,
      "updateIntervalSeconds": 10
    }
  },
  "message": "Trip accepted. GPS tracking initialized."
}
```

**Driver Declines - API Call:**
```
POST /notifications/{notificationId}/decline
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "driverId": "DRV-001",
  "declinedAt": 1735993080000,
  "reason": "VEHICLE_ISSUE",
  "reasonText": "Vehicle has minor mechanical issue",
  "currentLocation": {
    "latitude": 28.6500,
    "longitude": 77.2300
  }
}
```

**Decline Reasons (Dropdown):**
- `VEHICLE_ISSUE` - Vehicle has a problem
- `PERSONAL_EMERGENCY` - Personal emergency
- `ALREADY_BUSY` - Already on another trip
- `TOO_FAR` - Pickup location too far
- `LOW_FARE` - Fare too low
- `OTHER` - Other reason

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "notificationId": "NOTIF-001-ABC",
    "status": "DECLINED",
    "transporterNotified": true
  },
  "message": "Trip declined. Transporter has been notified."
}
```

---

### 11. Trip Navigation Screen (During Trip)

**File:** `DriverTripNavigationScreen.kt`

**What it shows:**
- Map view with route
- Trip status buttons:
  - "Reached Pickup" button
  - "Start Trip" button (after reaching pickup)
  - "Reached Drop" button
  - "Complete Trip" button
- Current location marker
- Customer contact button
- Trip details card

**GPS Location Updates (Every 10 seconds):**
```
POST /tracking/{trackingId}/location
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "latitude": 28.6550,
  "longitude": 77.2350,
  "accuracy": 12.3,
  "speed": 45.5,
  "heading": 135.0,
  "altitude": 215.5,
  "timestamp": 1735993030000
}
```

**Mark Pickup Reached:**
```
POST /tracking/{trackingId}/pickup-reached
Authorization: Bearer <jwt_token>
```

**Start Trip:**
```
POST /tracking/{trackingId}/start-trip
Authorization: Bearer <jwt_token>
```

**Mark Drop Reached:**
```
POST /tracking/{trackingId}/drop-reached
Authorization: Bearer <jwt_token>
```

**Complete Trip:**
```
POST /tracking/{trackingId}/complete
Authorization: Bearer <jwt_token>
```

**Request Body:**
```json
{
  "completedAt": 1735997100000,
  "otp": "1234",
  "customerSignature": "base64_image_string",
  "notes": "Delivered successfully"
}
```

---

## 🔔 Real-time Notifications (WebSocket)

### Transporter Receives Broadcast Notification

**WebSocket Event:**
```json
{
  "type": "broadcast:new",
  "data": {
    "broadcastId": "BC-2026-001-ABC123",
    "pickupCity": "New Delhi",
    "dropCity": "Noida",
    "vehicleType": "CONTAINER",
    "trucksNeeded": 10,
    "farePerTruck": 2500.00,
    "isUrgent": true,
    "expiryTime": 1735996200000
  }
}
```

**App Behavior:**
- Show notification badge
- Play notification sound
- Add to broadcast list
- If app is open, refresh broadcast list

---

### Transporter Receives Driver Response

**WebSocket Event (Driver Accepted):**
```json
{
  "type": "driver:accepted",
  "data": {
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "driverId": "DRV-001",
    "driverName": "Ramesh Singh",
    "vehicleNumber": "DL-1A-1234",
    "acceptedAt": 1735993020000
  }
}
```

**WebSocket Event (Driver Declined):**
```json
{
  "type": "driver:declined",
  "data": {
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "driverId": "DRV-003",
    "driverName": "Vijay Sharma",
    "vehicleNumber": "DL-1A-9012",
    "declinedAt": 1735993080000,
    "reason": "Vehicle has minor issue",
    "reassignmentRequired": true
  }
}
```

**App Behavior:**
- Show toast notification
- Update assignment screen
- If all drivers accepted → show success message
- If driver declined → show reassignment option

---

## 🎨 UI/UX Design Elements

### Color Scheme (Material 3)
```kotlin
Primary = Color(0xFFFF6B35)        // Orange
PrimaryVariant = Color(0xFFE55A2E)
Secondary = Color(0xFF004E89)      // Blue
Success = Color(0xFF2ECC71)        // Green
Warning = Color(0xFFF39C12)        // Yellow
Error = Color(0xFFE74C3C)          // Red
Surface = Color(0xFFF5F5F5)        // Light gray
White = Color(0xFFFFFFFF)
```

### Status Chips
- **Available** - Green background
- **In Transit** - Blue background
- **Pending** - Yellow background
- **Completed** - Gray background
- **Cancelled** - Red background

### Icons & Emojis Used
- 🚛 Container trucks
- 🚚 Open trucks
- 🚜 Tractors
- 🛻 Mini trucks
- 👤 Driver avatar
- 📍 Location marker
- ⭐ Rating star
- 🔔 Notifications
- 📞 Phone call
- ⚡ Urgent badge

---

## 📐 UI Layout Specifications

### Card Dimensions
- Card corner radius: `12.dp`
- Card elevation: `2.dp`
- Card padding: `16.dp`

### Spacing
- Screen padding: `16.dp`
- Item spacing in lists: `12.dp`
- Column spacing: `16.dp`
- Button height: `48.dp`

### Typography
- Headline: Bold, 24sp
- Title: Bold, 18sp
- Body: Regular, 14sp
- Caption: Regular, 12sp

---

## ⚠️ Critical Backend Requirements

### 1. Notification Delivery (HIGHEST PRIORITY)
```
✅ Must deliver FCM push in < 2 seconds
✅ Must include fullScreenAlarm flag
✅ Must include all trip details in data payload
✅ Must auto-decline after 5 minutes if no response
✅ Must send WebSocket backup if driver online
✅ Must send SMS backup after 2 minutes if no response
```

### 2. GPS Tracking
```
✅ Accept location every 10 seconds
✅ Broadcast via WebSocket to customer immediately
✅ Store in database asynchronously
✅ Calculate distance and ETA
```

### 3. Real-time Updates
```
✅ WebSocket connection required
✅ Broadcast events to correct users
✅ Handle disconnections gracefully
```

### 4. Data Validation
```
✅ Validate all required fields
✅ Check vehicle/driver availability
✅ Prevent double booking
✅ Handle race conditions (multiple transporters)
```

---

## 🚫 What Backend Should NOT Allow

```
❌ Transporter creating direct bookings (must be broadcast-based only)
❌ Assigning unavailable vehicles
❌ Assigning unavailable drivers
❌ Exceeding broadcast truck limit
❌ Expired broadcast assignments
❌ Duplicate assignments
```

---

## 📱 Screen Navigation Flow

```
TRANSPORTER APP
├── Login
├── Dashboard
├── Broadcast List ← Main Screen
│   └── Broadcast Details
│       └── Truck Selection
│           └── Driver Assignment
│               └── Confirmation
├── Fleet List
│   ├── Add Vehicle
│   └── Vehicle Details
└── Driver List
    ├── Add Driver
    └── Driver Details

DRIVER APP
├── Login
├── Dashboard
├── Trip Notification (Full-screen alarm)
│   ├── Accept → Trip Navigation
│   └── Decline → Back to Dashboard
└── Trip Navigation
    └── Complete → Trip Summary
```

---

## ✅ Backend Developer Checklist

### API Endpoints to Build
- [ ] GET /dashboard/transporter
- [ ] GET /dashboard/driver
- [ ] GET /broadcasts/active
- [ ] GET /vehicles (with filters)
- [ ] POST /vehicles
- [ ] GET /drivers (with filters)
- [ ] POST /drivers
- [ ] GET /vehicles/available
- [ ] GET /drivers/available
- [ ] POST /assignments (CRITICAL - triggers notifications)
- [ ] POST /notifications/{id}/accept
- [ ] POST /notifications/{id}/decline
- [ ] POST /tracking/{id}/location
- [ ] POST /tracking/{id}/pickup-reached
- [ ] POST /tracking/{id}/start-trip
- [ ] POST /tracking/{id}/complete

### Real-time Features
- [ ] WebSocket server setup
- [ ] FCM integration (high priority)
- [ ] Notification queue system
- [ ] Auto-decline background job

### Data Management
- [ ] Vehicle management CRUD
- [ ] Driver management CRUD
- [ ] Assignment creation with notifications
- [ ] GPS tracking storage

---

**Frontend is 100% built. Backend must match these specifications exactly!** 🎯
