# Weelo Captain - Logistics Management App

## Quick Start

### For Backend Developer
Start here: **BACKEND_INTEGRATION_GUIDE_FOR_DEVELOPER.md**

### Key Documentation
1. **DRIVER_AUTH_BACKEND_READY.md** - Driver authentication implementation
2. **SECURITY_HARDENING_COMPLETE.md** - Security features and OWASP compliance
3. **API_SPECIFICATION.md** - Complete API documentation (see API_*.md files)

### Build the App
```bash
./gradlew assembleDebug
```

APK Location: `app/build/outputs/apk/debug/app-debug.apk`

### Testing
- Temporary OTP: **123456**
- Test phone: Any 10-digit number (9876543210)

## Project Structure
- `/app/src/main/java/com/weelo/logistics/` - Source code
- `/app/src/main/res/` - Resources (layouts, drawables, strings)
- `build.gradle.kts` - Build configuration

## Features Implemented
✅ Phone-based authentication (OTP)
✅ Driver & Transporter roles
✅ Dark futuristic UI
✅ Security hardening (OWASP compliant)
✅ Input validation & rate limiting
✅ Backend-ready architecture

## Support
See backend documentation files for detailed implementation guides.
