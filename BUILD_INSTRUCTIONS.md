# Build Instructions - Weelo Logistics

## 🚀 Quick Start

### Prerequisites
- **Android Studio:** Hedgehog (2023.1.1) or newer
- **JDK:** 17 (bundled with Android Studio)
- **Minimum Android SDK:** API 24 (Android 7.0)
- **Target Android SDK:** API 34 (Android 14)

---

## 📦 Step-by-Step Build Guide

### Step 1: Open Project in Android Studio

```bash
# Navigate to project directory
cd "/Users/nitishbhardwaj/Desktop/weelo captain/WeeloLogistics"

# Open Android Studio
# File -> Open -> Select "WeeloLogistics" folder
```

### Step 2: Sync Gradle

1. Wait for Android Studio to open the project
2. Gradle will automatically start syncing
3. Wait for "Gradle sync finished" notification (may take 2-5 minutes first time)
4. If sync fails, click "Sync Now" in the banner

**Common Sync Issues:**
- **Issue:** "SDK not found"
  - **Solution:** File → Settings → Appearance & Behavior → System Settings → Android SDK
  - Install SDK Platform 34 and Build Tools 34.0.0

- **Issue:** "JDK version incompatible"
  - **Solution:** File → Settings → Build, Execution, Deployment → Build Tools → Gradle
  - Set Gradle JDK to "Embedded JDK (17)"

### Step 3: Configure Emulator (if needed)

**Option A: Use Existing Emulator**
1. Tools → Device Manager
2. Select any Android device (API 24+)
3. Click Play button

**Option B: Create New Emulator**
1. Tools → Device Manager → Create Device
2. Select Phone → Pixel 5 or any device
3. System Image → API 34 (Android 14)
4. Finish and launch

**Option C: Use Physical Device**
1. Enable Developer Options on your Android phone
2. Enable USB Debugging
3. Connect via USB
4. Select device from device dropdown

### Step 4: Run the App

**Method 1: Using Run Button**
1. Select device from dropdown (top toolbar)
2. Click green "Run" button (▶️)
3. Wait for build to complete
4. App will launch on device/emulator

**Method 2: Using Terminal**
```bash
# From project root
./gradlew installDebug

# Or on Windows
gradlew.bat installDebug
```

**Method 3: Using Keyboard Shortcut**
- Mac: `Shift + F10`
- Windows/Linux: `Shift + F10`

---

## 🧪 Testing the App

### Login Credentials
```
Mobile Number: Any number (e.g., 1234567890)
Password: 123456
```

### Test Flows

#### Flow 1: Transporter Role
1. Launch app → Complete onboarding
2. Login with credentials above
3. Select "Transporter" role
4. You'll see:
   - Total Vehicles: 3
   - Active Drivers: 3
   - Active Trips: 1
   - Today's Revenue: ₹4500
   - Recent trips list

#### Flow 2: Driver Role
1. Login (or switch from transporter)
2. Select "Driver" role
3. You'll see:
   - Availability toggle
   - Active trip (if driver "d1")
   - Today's stats (trips, earnings, distance, rating)
   - Pending trip requests

#### Flow 3: Both Roles (Dual Role)
1. Login
2. Select "Both" role
3. Navigate to Transporter dashboard first
4. (Role switching UI to be implemented)

---

## 🔧 Build Variants

### Debug Build (Default)
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

**Note:** Release build requires signing configuration (not included in this setup)

---

## 📱 APK Installation

### From Android Studio
1. Build → Build Bundle(s) / APK(s) → Build APK(s)
2. Wait for build to complete
3. Click "locate" in notification
4. Transfer APK to device and install

### Using ADB
```bash
# After building APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or install and run
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.weelo.logistics/.MainActivity
```

---

## 🐛 Troubleshooting

### Issue: Build Fails with "Duplicate class" error
**Solution:**
```bash
# Clean and rebuild
./gradlew clean
./gradlew assembleDebug
```

### Issue: App crashes on launch
**Check:**
1. Logcat for error messages (View → Tool Windows → Logcat)
2. Ensure minimum SDK version (API 24)
3. Clear app data and reinstall

### Issue: "Hilt not configured" error
**Solution:**
1. Check that `WeeloApp.kt` has `@HiltAndroidApp` annotation
2. Check that `MainActivity.kt` has `@AndroidEntryPoint` annotation
3. Clean and rebuild project

### Issue: Compose preview not working
**Solution:**
1. Enable Compose preview: File → Settings → Editor → Compose → Enable preview
2. Invalidate caches: File → Invalidate Caches / Restart

### Issue: Navigation not working
**Check:**
1. All screens are imported in `WeeloNavigation.kt`
2. Navigation routes match in `Screen.kt`
3. Check Logcat for navigation errors

---

## 📊 Project Statistics

```
Total Files: ~40 Kotlin files
Lines of Code: ~5000+
Build Time (first): ~2-3 minutes
Build Time (incremental): ~20-30 seconds
APK Size (debug): ~10-15 MB
APK Size (release): ~5-8 MB (with ProGuard)
```

---

## 🎯 What Works in Current Build

### ✅ Fully Functional
- Splash screen with animation
- Onboarding (3 pages)
- Login/Signup with validation
- Role selection
- Transporter dashboard with mock data
- Driver dashboard with mock data
- All reusable components
- Theme switching (light/dark ready)
- Navigation between screens

### ⚠️ Mock Data Only
- All API calls use `MockDataRepository`
- No real backend connection
- No GPS tracking yet
- No image uploads yet

### 🚧 Not Yet Implemented
- Fleet management screens
- Driver management screens
- Trip management screens
- Role switching component
- Bottom navigation
- Profile & Settings
- Maps integration
- Backend API integration

---

## 📂 Important Files for Backend Team

### Data Models
```
app/src/main/java/com/weelo/logistics/data/model/
├── User.kt           # User and role models
├── Vehicle.kt        # Vehicle model + 29 vehicle types
├── Driver.kt         # Driver model
├── Trip.kt           # Trip and location models
└── Dashboard.kt      # Dashboard data models
```

### Mock Repository (API Contract Reference)
```
app/src/main/java/com/weelo/logistics/data/repository/
└── MockDataRepository.kt  # Shows expected API responses
```

### API Integration Points
All screens currently use:
```kotlin
val repository = remember { MockDataRepository() }
```

Replace with:
```kotlin
@Inject lateinit var repository: DataRepository
```

---

## 🔐 Signing Configuration (For Release)

**Not configured yet.** To add:

1. Generate keystore:
```bash
keytool -genkey -v -keystore weelo-release.keystore \
  -alias weelo -keyalg RSA -keysize 2048 -validity 10000
```

2. Add to `app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../weelo-release.keystore")
            storePassword = "YOUR_STORE_PASSWORD"
            keyAlias = "weelo"
            keyPassword = "YOUR_KEY_PASSWORD"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

---

## 📱 App Permissions

Current permissions in `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

**Note:** Location permissions are declared but not yet requested at runtime.

---

## 🎨 Customization

### Change App Name
Edit: `app/src/main/res/values/strings.xml`
```xml
<string name="app_name">Your App Name</string>
```

### Change Colors
Edit: `app/src/main/java/com/weelo/logistics/ui/theme/Color.kt`
```kotlin
val Primary = Color(0xFFFF6B35)  // Change this
```

### Change Package Name
1. Refactor → Rename package in Android Studio
2. Update `applicationId` in `app/build.gradle.kts`
3. Update `AndroidManifest.xml`

---

## 📞 Support

### For Build Issues
- Check Android Studio logs
- Check Gradle console
- Search error on Stack Overflow
- Contact development team

### For Code Understanding
- Read `PROJECT_GUIDE.md`
- Read `IMPLEMENTATION_STATUS.md`
- Check inline code comments
- Review PRD documents

---

## ✅ Pre-Flight Checklist

Before sharing the app:
- [ ] Build completes without errors
- [ ] App launches successfully
- [ ] Login works with demo credentials
- [ ] Both dashboards display correctly
- [ ] No crashes in Logcat
- [ ] Tested on Android 7.0+ device
- [ ] APK size is reasonable (<20MB)

---

**Last Updated:** January 5, 2026  
**Build Version:** 1.0.0  
**Build Status:** ✅ Stable - Ready for Demo
