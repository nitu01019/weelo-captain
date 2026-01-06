# Build & Test Guide - Weelo Logistics

## 🚀 Quick Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 (bundled with Android Studio)
- Android SDK API 34
- Minimum 4GB RAM

---

## 📦 Step 1: Open Project

```bash
# Navigate to project
cd "/Users/nitishbhardwaj/Desktop/weelo captain/WeeloLogistics"

# Open Android Studio
# File → Open → Select "WeeloLogistics" folder
```

---

## ⚙️ Step 2: Gradle Sync

1. Android Studio will automatically start Gradle sync
2. Wait for "Gradle sync finished" (2-5 minutes first time)
3. If errors appear:
   - Click "Sync Now" button
   - File → Invalidate Caches / Restart

**Expected Output:**
```
BUILD SUCCESSFUL in Xs
```

---

## 🔨 Step 3: Build APK

### Option A: Using Android Studio
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
Wait for "Build APK(s)" notification
Click "locate" to find APK
```

### Option B: Using Terminal
```bash
cd "/Users/nitishbhardwaj/Desktop/weelo captain/WeeloLogistics"
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Step 4: Install & Test

### Option 1: Run on Emulator
```
1. Tools → Device Manager
2. Select device (or create new: Pixel 5, API 34)
3. Click Run button (▶️)
4. App will install and launch automatically
```

### Option 2: Run on Physical Device
```
1. Enable Developer Options on Android phone
2. Enable USB Debugging
3. Connect via USB
4. Select device from dropdown
5. Click Run button (▶️)
```

### Option 3: Install APK Manually
```bash
# After building APK
adb install app/build/outputs/apk/debug/app-debug.apk

# If device not found
adb devices

# Launch app
adb shell am start -n com.weelo.logistics/.MainActivity
```

---

## 🧪 Step 5: Test All Flows

### Test 1: Splash Screen ✅
```
Expected:
- See truck emoji (🚛) - 120dp
- "Hello Weelo Captains ⚓" appears after 200ms
- Loading spinner at bottom
- Auto-navigates after 2 seconds
```

### Test 2: Role Selection ✅
```
Expected:
- Header: "You are a:" (28sp, bold)
- Two cards: Transporter (orange) & Driver (blue)
- Each card: 140dp height, 16dp radius
- Tap card → Instant navigation (no Continue button)
```

### Test 3: Transporter Login ✅
```
Steps:
1. Tap "Transporter" card
2. See "Welcome back, Captain! ⚓"
3. Enter mobile: 9876543210
4. Tap "Continue with OTP"

Expected:
- +91 prefix shown (non-editable)
- Only 10 digits allowed
- Orange colored button
- Navigation to OTP screen
```

### Test 4: OTP Verification ✅
```
Steps:
1. See 6 empty boxes (48dp x 56dp each)
2. Type: 1 2 3 4 5 6
3. Wait for auto-submit

Expected:
- Auto-focus first box
- Auto-move to next box after each digit
- Border changes: gray → orange when filled
- Background: white → light orange when filled
- Auto-submit when all 6 entered
- Navigate to Signup on success

Error Test:
- Enter wrong OTP (not 123456)
- See error message
- Boxes shake
- All boxes cleared
- Focus back to first box

Timer Test:
- See "Resend in 00:30"
- Counter decreases every second
- After 30s: "Resend OTP" becomes clickable
```

### Test 5: Transporter Signup ✅
```
Steps:
1. After OTP verification
2. Fill:
   - Name: John Doe
   - Company: ABC Logistics
   - City: Mumbai
3. Check "Terms & Conditions"
4. Tap "Create Account"

Expected:
- Mobile number pre-filled (disabled)
- All fields have * for required
- Company and City are required
- Orange "Create Account" button
- Loading spinner on submit
- Navigate to Transporter Dashboard
```

### Test 6: Driver Signup ✅
```
Steps:
1. From role selection, tap "Driver"
2. Login → OTP → Signup
3. Fill:
   - Name: Ram Kumar
   - License: DL1420110012345 (optional)
   - Emergency: 9876543211 (optional)
4. Check "Terms & Conditions"
5. Tap "Complete Profile"

Expected:
- Mobile number pre-filled (disabled)
- Simpler form than Transporter
- License and Emergency are optional
- Blue "Complete Profile" button
- Navigate to Driver Dashboard
```

### Test 7: Back Navigation ✅
```
Test back button on each screen:
- OTP screen → Back to Login
- Login screen → Back to Role Selection
- Signup screen → Back to OTP

Expected:
- Proper back stack management
- No app crashes
- Data not lost during back navigation
```

---

## 📊 Expected Results

### Build Success Indicators:
```
✅ Gradle sync completed without errors
✅ APK built successfully (~10-15 MB)
✅ App installs on device/emulator
✅ App launches without crashes
✅ Splash animation plays smoothly
✅ All screens render correctly
✅ Navigation works as expected
✅ OTP auto-submit works
✅ Forms validate input
✅ Dashboards display (with mock data)
```

### Performance Metrics:
```
Cold Start Time: < 3 seconds
Screen Transitions: < 300ms
OTP Input: Instant response
Form Validation: Real-time
APK Size: 10-15 MB (debug)
Memory Usage: < 100 MB
```

---

## 🐛 Common Issues & Solutions

### Issue 1: Gradle Sync Failed
```
Error: "SDK location not found"

Solution:
File → Settings → Android SDK
Install: API 34, Build Tools 34.0.0, Android SDK Platform-Tools
```

### Issue 2: Build Failed - Duplicate Class
```
Error: "Duplicate class found"

Solution:
./gradlew clean
./gradlew assembleDebug
```

### Issue 3: App Crashes on Launch
```
Error: App closes immediately after splash

Check Logcat:
View → Tool Windows → Logcat
Filter: "AndroidRuntime"

Common causes:
- Missing permissions in manifest
- Theme not found
- Navigation route mismatch

Solution:
Clean + Rebuild project
Uninstall old app version
Reinstall fresh
```

### Issue 4: OTP Screen Not Working
```
Issue: OTP boxes don't respond

Check:
- Keyboard appears when tapping box?
- Try external keyboard on emulator
- Check Logcat for focus errors

Solution:
Use different emulator (Pixel 5 recommended)
Or test on physical device
```

### Issue 5: Navigation Issues
```
Issue: Clicking role card doesn't navigate

Check Logcat for:
"No activity found to handle Intent"

Solution:
Verify all routes in WeeloNavigation.kt
Ensure role parameter is passed correctly
```

---

## 📝 Test Checklist

Print this and check off:

### Build & Install
- [ ] Project opens in Android Studio
- [ ] Gradle sync successful
- [ ] No build errors
- [ ] APK built successfully
- [ ] App installs on device

### Splash Screen
- [ ] Logo appears centered
- [ ] "Hello Weelo Captains ⚓" visible
- [ ] Loading spinner at bottom
- [ ] Auto-navigates after 2s

### Role Selection
- [ ] Two cards displayed
- [ ] Transporter card is orange
- [ ] Driver card is blue
- [ ] Tap works instantly
- [ ] Navigation happens

### Login (Both Roles)
- [ ] Back button works
- [ ] Correct greeting shown
- [ ] +91 prefix present
- [ ] Mobile input works (10 digits only)
- [ ] Button color matches role
- [ ] Navigation to OTP works

### OTP Verification
- [ ] 6 boxes displayed
- [ ] Auto-focus on first box
- [ ] Auto-move to next box
- [ ] Visual feedback (border/bg color)
- [ ] Auto-submit works
- [ ] Error handling works
- [ ] Timer counts down
- [ ] Resend appears after 30s
- [ ] Demo OTP (123456) accepted

### Signup (Transporter)
- [ ] Mobile pre-filled
- [ ] All required fields marked *
- [ ] Company name field present
- [ ] City field present
- [ ] Terms checkbox works
- [ ] Button disabled until checkbox
- [ ] Loading spinner on submit
- [ ] Navigation to dashboard

### Signup (Driver)
- [ ] Mobile pre-filled
- [ ] Simpler form (no company/city)
- [ ] License field (optional)
- [ ] Emergency contact field
- [ ] Terms checkbox works
- [ ] Navigation to dashboard

### Dashboards
- [ ] Transporter dashboard loads
- [ ] Shows 3 vehicles (mock data)
- [ ] Shows 3 drivers (mock data)
- [ ] Stats displayed correctly
- [ ] Driver dashboard loads
- [ ] Shows availability toggle
- [ ] Shows trip cards

### Performance
- [ ] No lag in animations
- [ ] Smooth scrolling
- [ ] Fast screen transitions
- [ ] No memory leaks
- [ ] No crashes during testing

---

## ✅ Sign-Off

**Tested By:** _____________  
**Date:** _____________  
**Device:** _____________  
**Android Version:** _____________  
**Build:** Debug APK  
**Status:** ☐ Pass  ☐ Fail  

**Notes:**
_______________________________________
_______________________________________
_______________________________________

---

**Next Step:** Deploy to Google Play Internal Testing Track
