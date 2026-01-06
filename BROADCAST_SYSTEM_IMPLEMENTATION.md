# BROADCAST-BASED TRIP ASSIGNMENT SYSTEM - UI IMPLEMENTATION

## 📋 Overview

This document provides a comprehensive guide to the newly implemented broadcast-based trip assignment system for the Weelo Captain app. This system allows customers to broadcast trip requirements to multiple transporters, who can then select trucks and assign drivers who must accept or decline the trips.

---

## 🎯 System Flow

### Complete User Journey:

```
CUSTOMER → Broadcasts trip (10 trucks needed)
    ↓
TRANSPORTER 1 → Sees broadcast → Selects 3 trucks → Assigns drivers
TRANSPORTER 2 → Sees broadcast → Selects 4 trucks → Assigns drivers
TRANSPORTER 3 → Sees broadcast → Selects 3 trucks → Assigns drivers
    ↓
DRIVERS → Receive notifications → Accept/Decline
    ↓
ACCEPTED DRIVERS → Location tracking begins
DECLINED DRIVERS → Transporter reassigns to different driver
```

---

## 📱 Screens Implemented

### 1. **BroadcastListScreen.kt** - Transporter View
**Location:** `ui/transporter/BroadcastListScreen.kt`

**Purpose:** Display all active customer broadcasts to transporters

**Features:**
- Real-time broadcast updates (WebSocket/polling)
- Filter by: All, Active, Urgent, Nearby
- Shows: Customer name, route, trucks needed, pricing, distance
- Urgent broadcasts highlighted in red
- Badge counter for new broadcasts

**Backend Integration Points:**
```kotlin
// Fetch active broadcasts
suspend fun getActiveBroadcasts(transporterId: String): List<BroadcastTrip>

// Real-time updates via WebSocket
websocket.onMessage { broadcast ->
    // Add new broadcast to list
    // Show notification
    // Play sound
}
```

---

### 2. **TruckSelectionScreen.kt** - Transporter View
**Location:** `ui/transporter/TruckSelectionScreen.kt`

**Purpose:** Select trucks to commit from transporter's fleet

**Features:**
- Shows broadcast summary (customer, route, fare)
- Lists available vehicles (status = AVAILABLE)
- Checkbox selection with counter (e.g., 3/10 selected)
- Prevents over-selection
- Visual feedback for selected trucks
- Confirmation dialog before proceeding

**Backend Integration Points:**
```kotlin
// Fetch available vehicles
suspend fun getAvailableVehicles(
    transporterId: String, 
    vehicleType: VehicleType
): List<Vehicle>

// Vehicle must be:
// - status = AVAILABLE
// - not on another trip
// - matches required vehicle type
```

---

### 3. **DriverAssignmentScreen.kt** - Transporter View
**Location:** `ui/transporter/DriverAssignmentScreen.kt`

**Purpose:** Assign specific driver to each selected truck

**Features:**
- Shows all selected trucks (from previous screen)
- For each truck: pick driver from available list
- Driver picker bottom sheet with search
- Progress indicator (e.g., 2/3 drivers assigned)
- Shows driver details: name, rating, trips
- Cannot proceed until all trucks have drivers
- Success dialog after sending notifications

**Backend Integration Points:**
```kotlin
// Fetch available drivers
suspend fun getAvailableDrivers(transporterId: String): List<Driver>

// Create assignment
suspend fun createTripAssignment(
    broadcastId: String,
    assignments: List<DriverTruckAssignment>
): TripAssignment

// Send push notifications to each driver
suspend fun sendDriverNotifications(driverIds: List<String>)
```

---

### 4. **DriverTripNotificationScreen.kt** - Driver View
**Location:** `ui/driver/DriverTripNotificationScreen.kt`

**Purpose:** Show all pending trip notifications to driver

**Features:**
- Real-time notification updates
- Pending vs Earlier sections
- Pulsing dot indicator for new notifications
- Badge count on notification icon
- Shows: route, fare, distance, time
- "RESPOND" badge for pending notifications
- Sound + vibration on new notification arrival

**Backend Integration Points:**
```kotlin
// Fetch driver notifications
suspend fun getDriverNotifications(driverId: String): List<DriverNotification>

// Push notification via FCM
fcm.send(
    to: driverToken,
    notification: {
        title: "New Trip Assignment",
        body: "₹85,000 • Delhi → Mumbai",
        sound: "default",
        vibration: [200, 100, 200]
    }
)

// Play notification sound
MediaPlayer.create(context, R.raw.notification_sound).start()
```

---

### 5. **TripAcceptDeclineScreen.kt** - Driver View
**Location:** `ui/driver/TripAcceptDeclineScreen.kt`

**Purpose:** Full trip details with Accept/Decline options

**Features:**
- Large earnings display (₹85,000)
- Complete route details (pickup → drop)
- Trip information (distance, duration, goods type)
- Assigned vehicle details
- Important notes section
- Timer for urgent requests
- Dual buttons: Decline (outlined, red) / Accept (filled, green)
- Confirmation dialogs for both actions
- Optional decline reason input

**Backend Integration Points:**
```kotlin
// Accept trip
suspend fun acceptTrip(notificationId: String, driverId: String) {
    // Update assignment status to DRIVER_ACCEPTED
    // Update driver status to ON_TRIP
    // Start GPS tracking
    // Notify transporter
}

// Decline trip
suspend fun declineTrip(
    notificationId: String, 
    driverId: String,
    reason: String?
) {
    // Update assignment status to DRIVER_DECLINED
    // Create TripReassignment record
    // Notify transporter
    // Mark driver as available
}
```

---

### 6. **TripStatusManagementScreen.kt** - Transporter View
**Location:** `ui/transporter/TripStatusManagementScreen.kt`

**Purpose:** Monitor driver responses and manage reassignments

**Features:**
- Real-time status updates (every 5 seconds)
- Summary card: Accepted/Pending/Declined counts
- Progress bar showing acceptance rate
- Individual driver cards with status
- Green = Accepted, Yellow = Pending, Red = Declined
- "Reassign Driver" button for declined drivers
- "Track Location" button for accepted drivers
- Auto-refresh indicator

**Backend Integration Points:**
```kotlin
// Fetch assignment with real-time statuses
suspend fun getTripAssignment(assignmentId: String): TripAssignment

// WebSocket for real-time updates
websocket.onMessage { update ->
    when (update.type) {
        "DRIVER_ACCEPTED" -> updateDriverStatus(green)
        "DRIVER_DECLINED" -> updateDriverStatus(red)
    }
}

// Poll for updates (alternative to WebSocket)
while (true) {
    val updated = fetchAssignmentStatus(assignmentId)
    updateUI(updated)
    delay(5000)
}
```

---

### 7. **LiveTrackingScreen.kt** - Shared View
**Location:** `ui/shared/LiveTrackingScreen.kt`

**Purpose:** Real-time GPS tracking of driver during trip

**Features:**
- Map view (Google Maps integration required)
- Driver's live location marker
- Route from pickup to drop
- Bottom sheet with trip details
- Current speed and heading display
- ETA calculation
- "Complete Trip" button
- Location sharing indicator

**Backend Integration Points:**
```kotlin
// Continuous GPS updates from driver
suspend fun updateDriverLocation(
    driverId: String,
    latitude: Double,
    longitude: Double,
    speed: Float,
    heading: Float
) {
    // Save to LiveTripTracking table
    // Broadcast to transporter via WebSocket
}

// Fetch live tracking data
suspend fun getLiveTracking(tripId: String): LiveTripTracking

// Google Maps Integration
implementation 'com.google.maps.android:maps-compose:2.11.4'
implementation 'com.google.android.gms:play-services-location:21.0.1'

// Request location permissions
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

---

## 🗂️ Data Models

### **Broadcast.kt** - Core Models
**Location:** `data/model/Broadcast.kt`

**Key Models:**

1. **BroadcastTrip** - Customer's trip request
   - Broadcast details, pricing, location
   - Total trucks needed vs filled
   - Status tracking

2. **TripAssignment** - Transporter's commitment
   - Trucks selected + driver assignments
   - Assignment status tracking

3. **DriverTruckAssignment** - Individual driver-vehicle pair
   - Driver response status (PENDING/ACCEPTED/DECLINED)

4. **DriverNotification** - Push notification to driver
   - Trip preview details
   - Expiry time
   - Read/unread status

5. **TripReassignment** - Decline handling
   - Previous driver info
   - New driver assignment

6. **LiveTripTracking** - GPS tracking
   - Real-time coordinates
   - Speed, heading, timestamp

**Status Enums:**
- `BroadcastStatus`: ACTIVE, PARTIALLY_FILLED, FULLY_FILLED, EXPIRED, CANCELLED
- `AssignmentStatus`: PENDING_DRIVER_RESPONSE, DRIVER_ACCEPTED, DRIVER_DECLINED, TRIP_STARTED, TRIP_COMPLETED
- `DriverResponseStatus`: PENDING, ACCEPTED, DECLINED, EXPIRED, REASSIGNED
- `NotificationStatus`: PENDING_RESPONSE, ACCEPTED, DECLINED, EXPIRED, READ

---

## 🔄 Mock Repository Methods

### **MockDataRepository.kt** - Testing Functions
**Location:** `data/repository/MockDataRepository.kt`

**New Methods Added:**

```kotlin
// Broadcast Management
suspend fun getMockBroadcasts(): List<BroadcastTrip>
suspend fun getMockBroadcastById(id: String): BroadcastTrip?

// Vehicle & Driver Selection
suspend fun getMockAvailableVehicles(transporterId: String): List<Vehicle>
suspend fun getMockVehiclesByIds(ids: List<String>): List<Vehicle>
suspend fun getMockAvailableDrivers(transporterId: String): List<Driver>

// Assignment Management
suspend fun getMockAssignmentDetails(assignmentId: String): TripAssignment

// Driver Notifications
suspend fun getMockDriverNotifications(driverId: String): List<DriverNotification>
suspend fun getMockNotificationById(notificationId: String): DriverNotification?

// Tracking
suspend fun getMockTripById(tripId: String): Trip?
suspend fun getMockLiveTracking(driverId: String): LiveTripTracking
```

---

## 🧭 Navigation Routes

### **Screen.kt** - New Routes Added
**Location:** `ui/navigation/Screen.kt`

**Transporter Routes:**
- `broadcast_list` - View all broadcasts
- `truck_selection/{broadcastId}` - Select trucks
- `driver_assignment/{broadcastId}/{vehicleIds}` - Assign drivers
- `trip_status/{assignmentId}` - Monitor responses

**Driver Routes:**
- `driver_trip_notifications/{driverId}` - View notifications
- `trip_accept_decline/{notificationId}` - Accept/Decline trip

**Shared Routes:**
- `live_tracking/{tripId}/{driverId}` - Real-time tracking

---

## 🎨 UI Components & Design

### Color Coding:
- **Success (Green)**: Accepted, Available, Active
- **Warning (Yellow/Orange)**: Pending, Waiting
- **Error (Red)**: Declined, Urgent, Cancelled
- **Primary (Blue/Orange)**: Actions, Selected items
- **Info (Blue)**: Information cards

### Animations:
- Pulsing dot for new notifications
- Content size animations for expanding cards
- Progress bar transitions
- Loading states with spinners

### Icons Used:
- `Icons.Default.Notifications` - Notifications
- `Icons.Default.LocalShipping` - Trucks/Vehicles
- `Icons.Default.Person` - Drivers
- `Icons.Default.LocationOn` - Drop location
- `Icons.Default.TripOrigin` - Pickup location
- `Icons.Default.CheckCircle` - Accepted
- `Icons.Default.Cancel` - Declined
- `Icons.Default.HourglassEmpty` - Pending

---

## 🔐 Security & Permissions

### Required Permissions:
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Runtime Permissions:
- Location: Request before starting tracking
- Notifications: Request for push notifications
- Background location: For continuous tracking

---

## 🧪 Testing the UI

### Test Flow:
1. **Transporter Login** → View broadcasts
2. **Select broadcast** → Pick 3 trucks
3. **Assign drivers** → Choose from available list
4. **Switch to Driver** → See notifications
5. **Accept trip** → Start tracking
6. **View live location** → Monitor progress

### Mock Data:
- 3 sample broadcasts (Reliance, Amazon, Adani)
- 5 sample vehicles (different types)
- 3 sample drivers (with ratings)
- 3 sample notifications (different statuses)

---

## 🚀 Backend Integration Checklist

### For Backend Developer:

#### 1. **Database Tables Needed:**
- ✅ `broadcasts` (customer trip requests)
- ✅ `trip_assignments` (transporter commitments)
- ✅ `driver_truck_assignments` (individual assignments)
- ✅ `driver_notifications` (push notifications)
- ✅ `trip_reassignments` (decline handling)
- ✅ `live_trip_tracking` (GPS coordinates)

#### 2. **API Endpoints Needed:**
```
POST   /api/broadcasts/active                    // Get active broadcasts
GET    /api/broadcasts/{id}                      // Get broadcast details
POST   /api/vehicles/available                   // Get available vehicles
POST   /api/drivers/available                    // Get available drivers
POST   /api/assignments/create                   // Create assignment
GET    /api/assignments/{id}                     // Get assignment status
POST   /api/trips/accept                         // Driver accepts
POST   /api/trips/decline                        // Driver declines
POST   /api/tracking/update                      // Update GPS location
GET    /api/tracking/{tripId}/live              // Get live location
```

#### 3. **WebSocket Events:**
```javascript
// Transporter events
socket.on('new_broadcast', broadcast => {})
socket.on('driver_accepted', update => {})
socket.on('driver_declined', update => {})

// Driver events
socket.on('trip_assigned', notification => {})
socket.on('trip_cancelled', tripId => {})
```

#### 4. **Push Notifications (FCM):**
```json
{
  "to": "driver_fcm_token",
  "notification": {
    "title": "New Trip Assignment",
    "body": "₹85,000 • Delhi → Mumbai • 1420 km",
    "sound": "default"
  },
  "data": {
    "type": "trip_assignment",
    "notificationId": "n1",
    "assignmentId": "a1"
  }
}
```

#### 5. **GPS Tracking:**
- Update frequency: Every 5-10 seconds
- Accuracy: GPS_PROVIDER (high accuracy)
- Background tracking: Use Foreground Service
- Battery optimization: Use Fused Location Provider

---

## 📊 Status Flow Diagram

```
BROADCAST
├── ACTIVE → Customer creates request
├── PARTIALLY_FILLED → Some transporters committed
├── FULLY_FILLED → All trucks assigned
├── EXPIRED → Time limit reached
└── CANCELLED → Customer cancelled

ASSIGNMENT
├── PENDING_DRIVER_RESPONSE → Waiting for drivers
├── DRIVER_ACCEPTED → Driver said yes
├── DRIVER_DECLINED → Driver said no → REASSIGNMENT
├── TRIP_STARTED → Driver began trip
└── TRIP_COMPLETED → Trip finished

DRIVER_RESPONSE
├── PENDING → Notification sent
├── ACCEPTED → Driver accepted
├── DECLINED → Driver declined
├── EXPIRED → No response in time
└── REASSIGNED → Assigned to someone else
```

---

## 🎯 Key Features Summary

### ✅ **Transporter Features:**
1. View real-time customer broadcasts
2. Select trucks from available fleet
3. Assign drivers to each truck
4. Monitor driver responses in real-time
5. Reassign if driver declines
6. Track driver location during trip

### ✅ **Driver Features:**
1. Receive push notifications for assignments
2. View trip details (route, fare, distance)
3. Accept or decline trips
4. Provide decline reason (optional)
5. Start GPS tracking on acceptance
6. Complete trip after delivery

### ✅ **System Features:**
1. Real-time updates via WebSocket
2. Sound + vibration alerts
3. Badge counters for notifications
4. Status color coding
5. Progress tracking
6. Mock data for testing

---

## 📝 Important Notes for Backend Developer

1. **All UI screens are COMPLETE and FUNCTIONAL** - Just connect real APIs
2. **Mock methods in repository show expected API structure** - Use as reference
3. **Status enums are clearly defined** - Use exact same names in backend
4. **Comments explain WHAT backend should do** - Follow the instructions
5. **Navigation is set up** - Just wire up the screens in NavHost
6. **Location tracking requires Google Maps API key** - Add to manifest
7. **Push notifications require FCM setup** - Configure google-services.json

---

## 🔧 Next Steps

### For UI (Completed):
✅ All screens implemented
✅ All data models created
✅ Mock repository methods added
✅ Navigation routes defined
✅ Comprehensive documentation

### For Backend (TODO):
1. Create database tables
2. Implement REST APIs
3. Set up WebSocket server
4. Configure FCM for push notifications
5. Implement GPS tracking service
6. Add Google Maps API key
7. Test integration with UI

---

## 📞 Support

For questions or clarifications, refer to:
- **Data Models:** `Broadcast.kt` - Extensively documented
- **UI Screens:** Each screen has detailed header comments
- **Mock Repository:** Shows expected API behavior
- **This Document:** Complete system overview

---

**Version:** 1.0  
**Date:** January 2026  
**Status:** UI Implementation Complete ✅
