# Backend Integration Documentation - Overview

## Purpose of This Documentation

This documentation provides **backend developers** with everything they need to implement the backend APIs for the Weelo Captain logistics application. The mobile UI is already complete and functional. This documentation explains:

1. What the app does
2. How the UI expects the backend to behave
3. Exact API specifications required
4. Data models and validation rules
5. Error handling expectations
6. Security and authentication requirements

## Application Overview

**Weelo Captain** is a logistics management mobile application built with Android/Kotlin and Jetpack Compose. It connects **transporters** (fleet owners) with **drivers** to manage vehicle fleets and delivery trips efficiently.

### Key Actors

1. **Transporter** (Fleet Owner/Manager)
   - Manages fleet of vehicles (trucks, containers, trailers, etc.)
   - Manages drivers
   - Creates trips and broadcasts them to drivers
   - Tracks live trip status
   - Views analytics and earnings

2. **Driver** (Vehicle Operator)
   - Receives trip assignments via push notifications
   - Accepts or declines trips
   - Tracks active trips with GPS
   - Views earnings and performance
   - Reports trip status

3. **Customer** (Not in this app, but referenced)
   - Creates trip requests that get broadcast to transporters
   - This customer interface is handled by a separate app/system

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     WEELO CAPTAIN MOBILE APP                     │
│                    (Android - Jetpack Compose)                   │
│                                                                   │
│  ┌──────────────────┐              ┌──────────────────┐         │
│  │   Transporter    │              │     Driver       │         │
│  │      Flow        │              │      Flow        │         │
│  └──────────────────┘              └──────────────────┘         │
│           │                                  │                   │
└───────────┼──────────────────────────────────┼───────────────────┘
            │                                  │
            │         REST API (HTTPS)         │
            │         Base URL: https://api.weelo.in/v1/           
            └──────────────────┬───────────────┘
                               │
                ┌──────────────▼──────────────┐
                │                             │
                │    BACKEND SERVER (TODO)    │
                │                             │
                │  ┌──────────────────────┐   │
                │  │  Authentication      │   │
                │  │  - JWT Tokens        │   │
                │  │  - OTP via SMS       │   │
                │  └──────────────────────┘   │
                │                             │
                │  ┌──────────────────────┐   │
                │  │  Core Services       │   │
                │  │  - Fleet Management  │   │
                │  │  - Driver Management │   │
                │  │  - Trip Management   │   │
                │  │  - Broadcast System  │   │
                │  └──────────────────────┘   │
                │                             │
                │  ┌──────────────────────┐   │
                │  │  Real-time Services  │   │
                │  │  - GPS Tracking      │   │
                │  │  - Push Notifications│   │
                │  │  - WebSocket         │   │
                │  └──────────────────────┘   │
                │                             │
                └─────────────┬───────────────┘
                              │
                    ┌─────────▼─────────┐
                    │                   │
                    │  Database         │
                    │  (PostgreSQL/     │
                    │   MongoDB)        │
                    │                   │
                    └───────────────────┘
```

## Core Flow: UI → Backend Interaction

### 1. Authentication Flow

```
User opens app → Splash screen → Login screen
                                      │
                                      ▼
User enters phone number → POST /auth/send-otp
                                      │
                                      ▼
                    Backend sends SMS OTP → User enters OTP
                                                    │
                                                    ▼
                                    POST /auth/verify-otp
                                                    │
                                                    ▼
                        Backend returns JWT token + user details
                                                    │
                                                    ▼
                            App stores token securely → Navigate to dashboard
```

### 2. Transporter Flow: Creating a Trip Broadcast

```
Transporter Dashboard → Create Trip
                              │
                              ▼
                    Fill trip details (pickup, drop, etc.)
                              │
                              ▼
                    POST /broadcasts/create
                              │
                              ▼
            Backend broadcasts to eligible drivers via FCM
                              │
                              ▼
        Transporter views broadcast list (GET /broadcasts/active)
                              │
                              ▼
            Drivers respond (accept/decline) → Transporter notified
```

### 3. Driver Flow: Accepting a Trip

```
Driver Dashboard (idle) → Driver toggles availability ON
                                      │
                                      ▼
                    PUT /driver/availability {isAvailable: true}
                                      │
                                      ▼
            Backend sends push notification when trip broadcast arrives
                                      │
                                      ▼
                    Driver sees notification → Opens trip details
                                                        │
                                                        ▼
                                        Driver reviews trip (pickup, drop, fare)
                                                        │
                                                        ▼
                                        Driver accepts → POST /broadcasts/{id}/accept
                                                                      │
                                                                      ▼
                                        Backend assigns trip to driver → Transporter notified
```

### 4. Trip Tracking Flow

```
Driver starts trip → POST /trips/{tripId}/start
                                │
                                ▼
            Backend marks trip as IN_PROGRESS
                                │
                                ▼
    Driver's GPS location sent every 10-30 seconds → POST /trips/{tripId}/location
                                                            │
                                                            ▼
                            Backend updates live tracking → Transporter can view on map
                                                            │
                                                            ▼
                            Driver completes trip → POST /trips/{tripId}/complete
                                                            │
                                                            ▼
                                    Backend calculates earnings, updates stats
```

## Technology Stack (Mobile App - Already Built)

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Networking**: Retrofit 2
- **Authentication**: JWT tokens stored in EncryptedSharedPreferences
- **Real-time**: WebSocket + Firebase Cloud Messaging (FCM)
- **Maps**: Google Maps SDK (for GPS tracking)

## What Backend Needs to Implement

### Core Modules

1. **Authentication Module**
   - OTP generation and verification
   - JWT token management
   - Session handling
   - Token refresh mechanism

2. **Fleet Management Module**
   - Vehicle CRUD operations
   - Support for 9 vehicle categories with subtypes (see Vehicle Catalog)
   - Driver-to-vehicle assignment

3. **Driver Management Module**
   - Driver registration and onboarding
   - Driver authentication (OTP sent to transporter)
   - Driver performance tracking
   - Availability management

4. **Trip & Broadcast Module**
   - Broadcast creation and distribution
   - Driver response handling (accept/decline)
   - Trip assignment logic
   - Trip status management

5. **GPS Tracking Module**
   - Real-time location updates
   - Location history storage
   - Live tracking API

6. **Notification Module**
   - Push notifications via FCM
   - In-app notification management
   - WebSocket for real-time updates

7. **Analytics Module**
   - Dashboard statistics (trips, earnings, etc.)
   - Performance metrics
   - Earnings calculations

## Base URL and API Versioning

```
Base URL: https://api.weelo.in/v1/
```

All endpoints documented in this guide are relative to this base URL.

Example:
- Endpoint: `POST /auth/send-otp`
- Full URL: `https://api.weelo.in/v1/auth/send-otp`

## Authentication Method

All endpoints (except authentication endpoints) require a JWT Bearer token in the `Authorization` header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

The app will include this header in every API call after login.

## Response Format Standard

All API responses should follow this standard format:

### Success Response
```json
{
  "success": true,
  "data": { ... },
  "message": "Operation successful"
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid phone number format",
    "field": "mobileNumber"
  }
}
```

## Expected Response Times

- Authentication: < 2 seconds
- Data retrieval (lists): < 1 second
- Data creation/update: < 1 second
- Location updates: < 500ms
- Real-time notifications: Immediate (via WebSocket/FCM)

## Data Persistence Requirements

The app expects the backend to:
- Store all user, vehicle, driver, and trip data permanently
- Maintain location history for completed trips (at least 90 days)
- Keep notification history (at least 30 days)
- Archive completed trip data (minimum 1 year)

## Real-Time Communication

The app uses two mechanisms for real-time updates:

1. **Firebase Cloud Messaging (FCM)**: For push notifications
   - New trip broadcasts
   - Trip acceptance/rejection
   - Trip status changes

2. **WebSocket** (Optional but recommended): For live updates
   - Real-time trip tracking
   - Broadcast status updates
   - Driver location updates

## Security Expectations

1. **HTTPS Only**: All API calls must use HTTPS
2. **Token Expiry**: JWT tokens should expire after 7 days
3. **OTP Expiry**: OTP should expire after 5 minutes
4. **Rate Limiting**: Implement rate limiting to prevent abuse
5. **Input Validation**: Validate all input data on backend
6. **SQL Injection Prevention**: Use parameterized queries
7. **XSS Prevention**: Sanitize all text inputs

## Environment Configuration

The app expects these configurations:

### Development Environment
```
BASE_URL=https://dev-api.weelo.in/v1/
WEBSOCKET_URL=wss://dev-api.weelo.in/ws
FCM_SERVER_KEY=<your-fcm-key>
```

### Production Environment
```
BASE_URL=https://api.weelo.in/v1/
WEBSOCKET_URL=wss://api.weelo.in/ws
FCM_SERVER_KEY=<your-fcm-key>
```

## Next Steps for Backend Developer

1. Read `02_API_Integration_Map.md` for complete API endpoint catalog
2. Read `03_Screen_Wise_Integration.md` to understand UI flow and when APIs are called
3. Read `04_Data_Models_and_Schemas.md` for request/response structure details
4. Read `05_Auth_and_Security_Expectations.md` for authentication implementation
5. Read `06_Error_Handling_Contract.md` for error response standards
6. Read `07_Performance_and_Scaling_Notes.md` for performance requirements
7. Read `08_Environment_and_Configuration.md` for deployment setup
8. Follow `09_Integration_Checklist.md` to implement step-by-step

## Support and Questions

For any questions or clarifications about the UI behavior or API requirements:
- Review the Kotlin source code in `/app/src/main/java/com/weelo/logistics/`
- Check API service interfaces in `/app/src/main/java/com/weelo/logistics/data/api/`
- Review data models in `/app/src/main/java/com/weelo/logistics/data/model/`

## Version History

- **v1.0** (Current): Initial backend integration documentation
- UI Version: Weelo Captain v1.0.0
- Last Updated: January 2026
