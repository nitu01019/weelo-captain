# Weelo Logistics - Project Guide

## 📋 Overview

**Weelo Logistics** is a unified Android application built with **Kotlin** and **Jetpack Compose** that serves both **Transporters** (fleet owners) and **Drivers** in a single app. Users can have one or both roles and seamlessly switch between them.

---

## 🏗️ Architecture

### Tech Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture Pattern:** MVVM (Model-View-ViewModel)
- **Navigation:** Jetpack Navigation Component
- **Dependency Injection:** Hilt (configured)
- **Data Storage:** DataStore (preferences), Room (prepared for future)
- **Network:** Retrofit (prepared for backend integration)
- **Maps:** Google Maps SDK (for GPS tracking)

### Project Structure

```
WeeloLogistics/
├── app/
│   ├── src/main/
│   │   ├── java/com/weelo/logistics/
│   │   │   ├── data/
│   │   │   │   ├── model/              # Data models
│   │   │   │   │   ├── User.kt         # User, roles, profiles
│   │   │   │   │   ├── Vehicle.kt      # Vehicle model + 29 vehicle types
│   │   │   │   │   ├── Driver.kt       # Driver model
│   │   │   │   │   ├── Trip.kt         # Trip, tracking, location
│   │   │   │   │   └── Dashboard.kt    # Dashboard data models
│   │   │   │   └── repository/         # Data repositories
│   │   │   │       ├── MockDataRepository.kt        # Mock data for testing
│   │   │   │       └── UserPreferencesRepository.kt # Session management
│   │   │   │
│   │   │   ├── ui/
│   │   │   │   ├── theme/              # Design system
│   │   │   │   │   ├── Color.kt        # Color palette
│   │   │   │   │   ├── Type.kt         # Typography
│   │   │   │   │   ├── Theme.kt        # Material theme
│   │   │   │   │   └── Spacing.kt      # Spacing constants
│   │   │   │   │
│   │   │   │   ├── components/         # Reusable UI components
│   │   │   │   │   ├── Buttons.kt      # Button variants
│   │   │   │   │   ├── Cards.kt        # Card components
│   │   │   │   │   ├── Inputs.kt       # Text fields
│   │   │   │   │   └── TopBars.kt      # Top app bars
│   │   │   │   │
│   │   │   │   ├── auth/               # Authentication screens
│   │   │   │   │   ├── SplashScreen.kt
│   │   │   │   │   ├── OnboardingScreen.kt
│   │   │   │   │   ├── LoginScreen.kt
│   │   │   │   │   ├── SignupScreen.kt
│   │   │   │   │   └── RoleSelectionScreen.kt
│   │   │   │   │
│   │   │   │   ├── transporter/        # Transporter screens
│   │   │   │   │   └── TransporterDashboardScreen.kt
│   │   │   │   │
│   │   │   │   ├── driver/             # Driver screens
│   │   │   │   │   └── DriverDashboardScreen.kt
│   │   │   │   │
│   │   │   │   └── navigation/         # Navigation setup
│   │   │   │       ├── Screen.kt       # Screen routes
│   │   │   │       └── WeeloNavigation.kt
│   │   │   │
│   │   │   ├── WeeloApp.kt            # Application class
│   │   │   └── MainActivity.kt        # Main activity
│   │   │
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── colors.xml
│   │   │   │   ├── strings.xml
│   │   │   │   ├── themes.xml
│   │   │   │   └── dimens.xml
│   │   │   └── xml/
│   │   │       ├── backup_rules.xml
│   │   │       └── data_extraction_rules.xml
│   │   │
│   │   └── AndroidManifest.xml
│   │
│   ├── build.gradle.kts                # App dependencies
│   └── proguard-rules.pro
│
├── build.gradle.kts                    # Project config
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
├── README.md
└── PROJECT_GUIDE.md (this file)
```

---

## 🎨 Design System

### Colors
- **Primary:** `#FF6B35` (Orange) - Transporter actions
- **Secondary:** `#2196F3` (Blue) - Driver actions
- **Success:** `#4CAF50` - Available, completed states
- **Warning:** `#FFC107` - Pending states
- **Error:** `#F44336` - Error, cancelled states

### Typography
- Uses system default (Roboto)
- Material 3 typography scale

### Components
All reusable components are in `ui/components/`:
- `PrimaryButton`, `SecondaryButton`, `WeeloTextButton`
- `InfoCard`, `StatusChip`, `ListItemCard`, `SectionCard`
- `PrimaryTextField`, `SearchTextField`
- `PrimaryTopBar`, `SimpleTopBar`

---

## 📊 Data Models

### Core Models

#### User & Roles
```kotlin
data class User(
    val id: String,
    val name: String,
    val mobileNumber: String,
    val roles: List<UserRole>, // TRANSPORTER, DRIVER, or both
    ...
)

enum class UserRole {
    TRANSPORTER,
    DRIVER
}
```

#### Vehicle (29 Types)
```kotlin
enum class VehicleType {
    // 2-Wheeler: BIKE, SCOOTER
    // 3-Wheeler: AUTO, E_RICKSHAW
    // LCV: TATA_ACE, PICKUP, MINI_TRUCK, CHHOTA_HATHI, MAHINDRA_BOLERO
    // MCV: TRUCK_14_FEET to TRUCK_22_FEET, EICHER_10_15
    // HCV: TRUCK_24_FEET to TRUCK_32_FEET, CONTAINER variants, TRAILER, MULTI_AXLE
    // Specialized: TANKER, REFRIGERATED_TRUCK, DUMPER, TIPPER, FLATBED, LOW_BED_TRAILER
}
```

#### Trip
```kotlin
data class Trip(
    val id: String,
    val transporterId: String,
    val vehicleId: String,
    val driverId: String?,
    val pickupLocation: Location,
    val dropLocation: Location,
    val status: TripStatus,
    val fare: Double,
    ...
)
```

---

## 🚀 Features Implemented

### ✅ Completed Features

#### 1. **Design System**
- Complete color palette
- Typography system
- Reusable components (Buttons, Cards, Inputs)
- Theme configuration

#### 2. **Data Layer**
- All data models (User, Vehicle, Driver, Trip)
- 29 vehicle types catalog
- Mock data repository with realistic data
- User preferences repository

#### 3. **Authentication Flow**
- Splash screen with animation
- 3-page onboarding
- Login screen (demo: any mobile + password "123456")
- Signup screen with validation
- Role selection (Transporter/Driver/Both)

#### 4. **Transporter Dashboard**
- Overview cards (vehicles, drivers, trips, revenue)
- Quick actions (Add Vehicle, Add Driver, New Trip)
- Recent trips list
- Empty state handling

#### 5. **Driver Dashboard**
- Availability toggle (Online/Offline)
- Active trip display
- Stats (trips, earnings, distance, rating)
- Pending trip requests
- Accept/Reject trip actions

---

## 🔧 How to Build & Run

### Prerequisites
- **Android Studio:** Arctic Fox or newer
- **JDK:** 17
- **Android SDK:** API 24+ (minimum), API 34 (target)

### Steps

1. **Open Project**
   ```bash
   cd "/Users/nitishbhardwaj/Desktop/weelo captain/WeeloLogistics"
   # Open in Android Studio
   ```

2. **Sync Gradle**
   - Android Studio will automatically sync
   - Wait for dependencies to download

3. **Run App**
   - Select emulator or connected device
   - Click Run button or press `Shift + F10`

4. **Login**
   - Use any mobile number
   - Password: `123456`
   - Select role: Transporter/Driver/Both

---

## 📱 User Flows

### Flow 1: First Time User
```
Splash → Onboarding (3 pages) → Login/Signup → Role Selection → Dashboard
```

### Flow 2: Returning User
```
Splash → (Check session) → Dashboard
```

### Flow 3: Transporter Dashboard
```
Dashboard → View Stats → Quick Actions → View Recent Trips
```

### Flow 4: Driver Dashboard
```
Dashboard → Toggle Availability → View Active Trip → Accept/Reject Pending Trips
```

---

## 🔄 Role Switching (For Dual-Role Users)

**Note:** Role switching UI is prepared in navigation but not yet fully implemented in dashboards. 

**Planned Implementation:**
- Top bar dropdown to switch between Transporter and Driver modes
- Different bottom navigation based on active role
- Seamless state preservation when switching

---

## 🧪 Testing

### Mock Data
The `MockDataRepository` provides realistic sample data:
- 3 vehicles (various types)
- 3 drivers (with different stats)
- 3 trips (pending, in-progress, completed)

### Test Scenarios

1. **Login Test**
   - Mobile: `1234567890`
   - Password: `123456`
   - Expected: Login successful

2. **Transporter Dashboard**
   - Shows 3 vehicles, 3 drivers
   - Shows revenue and trip stats
   - Displays recent trips

3. **Driver Dashboard**
   - Toggle availability on/off
   - Shows active trip if in progress
   - Shows pending trip requests

---

## 📦 Dependencies

### Core
```kotlin
androidx.core:core-ktx:1.12.0
androidx.lifecycle:lifecycle-runtime-ktx:2.6.2
androidx.activity:activity-compose:1.8.2
```

### Compose
```kotlin
androidx.compose:compose-bom:2023.10.01
androidx.compose.material3:material3
androidx.compose.material:material-icons-extended
androidx.navigation:navigation-compose:2.7.6
```

### Hilt (Dependency Injection)
```kotlin
com.google.dagger:hilt-android:2.48
```

### Data
```kotlin
androidx.room:room-runtime:2.6.1
androidx.datastore:datastore-preferences:1.0.0
com.google.code.gson:gson:2.10.1
```

### Network (Prepared)
```kotlin
com.squareup.retrofit2:retrofit:2.9.0
com.squareup.okhttp3:okhttp:4.12.0
```

### Maps
```kotlin
com.google.android.gms:play-services-maps:18.2.0
com.google.android.gms:play-services-location:21.0.1
```

---

## 🎯 Next Steps (For Backend Team)

### 1. **API Endpoints Needed**

#### Authentication
```
POST /api/auth/login
POST /api/auth/signup
POST /api/auth/verify-otp
```

#### Transporter
```
GET  /api/transporter/dashboard
GET  /api/transporter/vehicles
POST /api/transporter/vehicles
PUT  /api/transporter/vehicles/{id}
DELETE /api/transporter/vehicles/{id}

GET  /api/transporter/drivers
POST /api/transporter/drivers
PUT  /api/transporter/drivers/{id}

GET  /api/transporter/trips
POST /api/transporter/trips
PUT  /api/transporter/trips/{id}
```

#### Driver
```
GET  /api/driver/dashboard
PUT  /api/driver/availability
GET  /api/driver/trips
POST /api/driver/trips/{id}/accept
POST /api/driver/trips/{id}/reject
POST /api/driver/trips/{id}/start
POST /api/driver/trips/{id}/complete
```

### 2. **WebSocket for Real-time**
```
ws://api/tracking/{tripId}  // GPS location updates
ws://api/notifications/{userId}  // Push notifications
```

### 3. **Data Models Match**
All data models in `data/model/` are designed to match API responses. Review and adjust as needed.

---

## 🐛 Known Issues & TODOs

### TODOs
- [ ] Add Fleet Management screens (list, add, edit vehicles)
- [ ] Add Driver Management screens (list, add, edit drivers)
- [ ] Add Trip Management screens (create, assign, track)
- [ ] Implement Role Switcher component
- [ ] Add Profile and Settings screens
- [ ] Implement GPS tracking service
- [ ] Add Map integration for trip tracking
- [ ] Connect to real backend API
- [ ] Add push notifications
- [ ] Add image upload for vehicles/drivers
- [ ] Add offline support with Room database
- [ ] Add unit tests
- [ ] Add UI tests

### Known Issues
- None currently (UI only implementation with mock data)

---

## 📚 Code Guidelines

### For Backend Integration

1. **Replace Mock Repository**
   ```kotlin
   // Current
   val repository = remember { MockDataRepository() }
   
   // Replace with
   @Inject lateinit var repository: DataRepository
   ```

2. **Add ViewModel**
   ```kotlin
   @HiltViewModel
   class TransporterDashboardViewModel @Inject constructor(
       private val repository: DataRepository
   ) : ViewModel() {
       // Business logic here
   }
   ```

3. **Handle Loading States**
   ```kotlin
   sealed class UiState<out T> {
       object Loading : UiState<Nothing>()
       data class Success<T>(val data: T) : UiState<T>()
       data class Error(val message: String) : UiState<Nothing>()
   }
   ```

---

## 📞 Support

For questions about the codebase, contact the development team or refer to:
- PRD documents in `/Desktop/WEELO_UNIFIED_APP_PRDs/`
- This PROJECT_GUIDE.md
- Inline code comments

---

## 📄 License

Proprietary - Weelo Logistics © 2026

---

**Last Updated:** January 5, 2026  
**Version:** 1.0.0  
**Status:** Development - UI Implementation Complete
