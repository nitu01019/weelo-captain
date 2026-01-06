# 🚀 QUICK START GUIDE - Broadcast System

## For Backend Developer: How to Test the UI

### Step 1: Understand the Flow
```
Customer Broadcast → Transporter Selects Trucks → Assigns Drivers → 
Driver Gets Notification → Accepts/Declines → Location Tracking
```

### Step 2: Files to Connect

#### **Data Models** (Already Created ✅)
- `data/model/Broadcast.kt` - All broadcast system models

#### **UI Screens** (Already Created ✅)
- `ui/transporter/BroadcastListScreen.kt`
- `ui/transporter/TruckSelectionScreen.kt`
- `ui/transporter/DriverAssignmentScreen.kt`
- `ui/transporter/TripStatusManagementScreen.kt`
- `ui/driver/DriverTripNotificationScreen.kt`
- `ui/driver/TripAcceptDeclineScreen.kt`
- `ui/shared/LiveTrackingScreen.kt`

#### **Mock Repository** (Already Updated ✅)
- `data/repository/MockDataRepository.kt` - Contains sample data and methods

#### **Navigation** (Already Updated ✅)
- `ui/navigation/Screen.kt` - Routes defined

### Step 3: What YOU Need to Do

#### A. Add Screens to Navigation Graph
In `ui/navigation/WeeloNavigation.kt`:

```kotlin
// Add these composables to your NavHost

// Transporter Broadcasts
composable(Screen.BroadcastList.route) {
    BroadcastListScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToBroadcastDetails = { broadcastId ->
            navController.navigate(Screen.TruckSelection.createRoute(broadcastId))
        }
    )
}

composable(Screen.TruckSelection.route) { backStackEntry ->
    val broadcastId = backStackEntry.arguments?.getString("broadcastId") ?: ""
    TruckSelectionScreen(
        broadcastId = broadcastId,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToDriverAssignment = { bId, vehicleIds ->
            navController.navigate(
                Screen.DriverAssignment.createRoute(bId, vehicleIds.joinToString(","))
            )
        }
    )
}

composable(Screen.DriverAssignment.route) { backStackEntry ->
    val broadcastId = backStackEntry.arguments?.getString("broadcastId") ?: ""
    val vehicleIdsStr = backStackEntry.arguments?.getString("vehicleIds") ?: ""
    val vehicleIds = vehicleIdsStr.split(",")
    
    DriverAssignmentScreen(
        broadcastId = broadcastId,
        selectedVehicleIds = vehicleIds,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToTracking = {
            navController.navigate(Screen.TripStatusManagement.createRoute("assignment_1"))
        }
    )
}

composable(Screen.TripStatusManagement.route) { backStackEntry ->
    val assignmentId = backStackEntry.arguments?.getString("assignmentId") ?: ""
    TripStatusManagementScreen(
        assignmentId = assignmentId,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToReassign = { aId, vId -> 
            // Navigate back to driver assignment
        },
        onNavigateToTracking = { driverId ->
            navController.navigate(Screen.LiveTracking.createRoute("trip1", driverId))
        }
    )
}

// Driver Screens
composable(Screen.DriverTripNotifications.route) { backStackEntry ->
    val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
    DriverTripNotificationScreen(
        driverId = driverId,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToTripDetails = { notificationId ->
            navController.navigate(Screen.TripAcceptDecline.createRoute(notificationId))
        }
    )
}

composable(Screen.TripAcceptDecline.route) { backStackEntry ->
    val notificationId = backStackEntry.arguments?.getString("notificationId") ?: ""
    TripAcceptDeclineScreen(
        notificationId = notificationId,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToTracking = { assignmentId ->
            navController.navigate(Screen.LiveTracking.createRoute("trip1", "d1"))
        }
    )
}

// Shared Tracking
composable(Screen.LiveTracking.route) { backStackEntry ->
    val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
    val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
    LiveTrackingScreen(
        tripId = tripId,
        driverId = driverId,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToComplete = { navController.popBackStack() }
    )
}
```

#### B. Add Entry Points to Dashboards

**In TransporterDashboardScreen.kt:**
```kotlin
// Add this to Quick Actions
QuickActionCard(
    icon = Icons.Default.Notifications,
    title = "View Broadcasts",
    modifier = Modifier.weight(1f),
    onClick = { navController.navigate(Screen.BroadcastList.route) }
)
```

**In DriverDashboardScreen.kt:**
```kotlin
// Add notification badge in top bar
IconButton(onClick = { 
    navController.navigate(Screen.DriverTripNotifications.createRoute("d1")) 
}) {
    BadgedBox(badge = { Badge { Text("2") } }) {
        Icon(Icons.Default.Notifications, null, tint = TextPrimary)
    }
}
```

### Step 4: Test the UI Flow

1. **Start App** → Login as Transporter
2. **Dashboard** → Click "View Broadcasts"
3. **Broadcast List** → Click any broadcast card
4. **Truck Selection** → Select 2-3 trucks → Confirm
5. **Driver Assignment** → Assign driver to each truck → Send
6. **Status Management** → See driver statuses (mock data shows mixed states)
7. **Switch Role** → Switch to Driver role
8. **Notifications** → See pending trip notifications
9. **Accept Trip** → Click notification → Accept
10. **Live Tracking** → View map with location tracking

### Step 5: Replace Mock with Real APIs

#### Example: Broadcast List
**Current (Mock):**
```kotlin
LaunchedEffect(Unit) {
    scope.launch {
        broadcasts = repository.getMockBroadcasts()
        isLoading = false
    }
}
```

**Replace with:**
```kotlin
LaunchedEffect(Unit) {
    scope.launch {
        try {
            val response = apiService.getActiveBroadcasts(transporterId)
            if (response.isSuccessful) {
                broadcasts = response.body() ?: emptyList()
            }
        } catch (e: Exception) {
            // Handle error
        } finally {
            isLoading = false
        }
    }
}
```

### Step 6: Add Real-Time Updates

#### WebSocket Example:
```kotlin
// In BroadcastListScreen
LaunchedEffect(Unit) {
    webSocketManager.connect()
    webSocketManager.onNewBroadcast { broadcast ->
        broadcasts = broadcasts + broadcast
        playNotificationSound()
        showBadge = true
    }
}
```

#### FCM Push Notification:
```kotlin
// In MyFirebaseMessagingService
override fun onMessageReceived(message: RemoteMessage) {
    val data = message.data
    when (data["type"]) {
        "trip_assignment" -> {
            showNotification(
                title = "New Trip Assignment",
                body = data["message"],
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            )
            // Update UI if app is open
            EventBus.post(NewTripNotificationEvent(data["notificationId"]))
        }
    }
}
```

### Step 7: Implement GPS Tracking

```kotlin
// In DriverDashboardScreen (when trip accepted)
class LocationTrackingService : Service() {
    private val locationClient by lazy { 
        LocationServices.getFusedLocationProviderClient(this) 
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        val locationRequest = LocationRequest.create().apply {
            interval = 5000 // 5 seconds
            fastestInterval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        
        locationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        
        return START_STICKY
    }
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                // Send to backend
                scope.launch {
                    apiService.updateDriverLocation(
                        driverId = getCurrentDriverId(),
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speed = location.speed,
                        heading = location.bearing
                    )
                }
            }
        }
    }
}
```

---

## 🎯 Testing Checklist

- [ ] Can view broadcast list
- [ ] Can select trucks from broadcast
- [ ] Can assign drivers to trucks
- [ ] Can view assignment status
- [ ] Driver receives notification
- [ ] Driver can accept/decline
- [ ] Live tracking shows on map
- [ ] Status updates in real-time

---

## 🐛 Common Issues & Solutions

### Issue 1: Screens not appearing
**Solution:** Make sure you added all composables to NavHost in WeeloNavigation.kt

### Issue 2: Mock data not showing
**Solution:** Check MockDataRepository initialization in init block

### Issue 3: Navigation arguments null
**Solution:** Verify route parameters match Screen.kt definitions

### Issue 4: Map not showing
**Solution:** Add Google Maps API key to AndroidManifest.xml
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_API_KEY_HERE"/>
```

---

## 📚 Documentation Reference

- **Complete Guide:** `BROADCAST_SYSTEM_IMPLEMENTATION.md`
- **Data Models:** `app/src/main/java/com/weelo/logistics/data/model/Broadcast.kt`
- **Mock Data:** `app/src/main/java/com/weelo/logistics/data/repository/MockDataRepository.kt`
- **All Screens:** `app/src/main/java/com/weelo/logistics/ui/`

---

## ✅ Summary

**What's Done:**
- ✅ 7 complete UI screens
- ✅ All data models with enums
- ✅ Mock repository with sample data
- ✅ Navigation routes defined
- ✅ Comprehensive documentation

**What You Need:**
- Wire up screens in NavHost
- Replace mock methods with real APIs
- Implement WebSocket for real-time updates
- Set up FCM for push notifications
- Add GPS tracking service
- Configure Google Maps

---

**Need Help?** All code is heavily commented. Check the header of each screen file for detailed explanation.
