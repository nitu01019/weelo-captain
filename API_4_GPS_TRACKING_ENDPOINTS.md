# 4️⃣ GPS Tracking & Location APIs

## Overview
These endpoints manage real-time GPS tracking after a driver accepts a trip. Location updates are sent continuously to track the driver's journey from pickup to delivery.

---

## 📍 GPS Tracking Flow

```
DRIVER ACCEPTS TRIP
         ↓
GPS TRACKING INITIALIZED
         ↓
DRIVER APP REQUESTS LOCATION PERMISSIONS
         ↓
BACKGROUND LOCATION SERVICE STARTS
         ↓
LOCATION UPDATES SENT EVERY 10 SECONDS
  - Latitude, Longitude
  - Speed, Heading
  - Timestamp, Accuracy
         ↓
BACKEND STORES & BROADCASTS LOCATION
  - Save to database
  - Send to customer via WebSocket
  - Send to transporter via WebSocket
         ↓
CUSTOMER SEES LIVE LOCATION ON MAP
         ↓
TRIP COMPLETES → TRACKING STOPS
```

---

## 4.1 Initialize GPS Tracking (After driver accepts)

**Endpoint**: `POST /tracking/initialize`

**Role**: `DRIVER` (Auto-called after acceptance)

**Description**: Initialize GPS tracking session for the trip.

### Request Body

```json
{
  "tripId": "TRIP-001-ABC",
  "driverId": "DRV-001",
  "vehicleId": "VEH-001-CONT-1234",
  "currentLocation": {
    "latitude": 28.6500,
    "longitude": 77.2300,
    "accuracy": 15.5
  },
  "deviceInfo": {
    "platform": "android",
    "osVersion": "14",
    "appVersion": "2.1.0",
    "deviceModel": "Samsung Galaxy S23"
  }
}
```

### Response (201 Created)

```json
{
  "success": true,
  "data": {
    "trackingId": "TRACK-001-XYZ",
    "tripId": "TRIP-001-ABC",
    "driverId": "DRV-001",
    "vehicleId": "VEH-001-CONT-1234",
    "status": "INITIALIZED",
    "trackingStartedAt": 1735993020000,
    "updateIntervalSeconds": 10,
    "websocketUrl": "wss://api.weelologistics.com/tracking/ws/TRACK-001-XYZ",
    "settings": {
      "distanceFilter": 10,
      "desiredAccuracy": "HIGH",
      "backgroundMode": true,
      "batteryOptimization": "BALANCED"
    }
  },
  "message": "GPS tracking initialized successfully",
  "timestamp": "2026-01-05T10:37:00Z"
}
```

### Backend Logic

1. **Create Tracking Record**
```sql
INSERT INTO live_trip_tracking (
  tracking_id, trip_id, driver_id, vehicle_id,
  status, tracking_started_at, update_interval_seconds
) VALUES (?, ?, ?, ?, 'INITIALIZED', ?, 10)
```

2. **Initialize WebSocket Channel**
- Create dedicated channel for this trip
- Subscribe customer and transporter

3. **Store Initial Location**
```sql
INSERT INTO location_history (
  tracking_id, latitude, longitude, accuracy, timestamp
) VALUES (?, ?, ?, ?, ?)
```

---

## 4.2 Send Location Update

**Endpoint**: `POST /tracking/{trackingId}/location`

**Role**: `DRIVER`

**Description**: Send real-time location update. Called every 10 seconds by driver app.

### Request Body

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

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "trackingId": "TRACK-001-XYZ",
    "locationStored": true,
    "broadcastSent": true,
    "distanceTraveled": 0.8,
    "distanceToPickup": 12.5,
    "eta": 18
  },
  "timestamp": "2026-01-05T10:37:10Z"
}
```

### Backend Logic

1. **Validate Tracking Session**
```sql
SELECT status FROM live_trip_tracking WHERE tracking_id = ?
```

2. **Store Location**
```sql
INSERT INTO location_history (
  tracking_id, latitude, longitude, accuracy, 
  speed, heading, altitude, timestamp
) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
```

3. **Update Current Location**
```sql
UPDATE live_trip_tracking
SET current_latitude = ?,
    current_longitude = ?,
    current_speed = ?,
    heading = ?,
    last_updated = ?
WHERE tracking_id = ?
```

4. **Broadcast to WebSocket Subscribers**
```javascript
websocket.broadcast(`tracking/${trackingId}`, {
  type: 'LOCATION_UPDATE',
  trackingId: 'TRACK-001-XYZ',
  latitude: 28.6550,
  longitude: 77.2350,
  speed: 45.5,
  heading: 135.0,
  timestamp: 1735993030000
});
```

5. **Calculate Metrics**
- Distance traveled
- Distance to pickup/drop
- ETA calculation

---

## 4.3 Get Live Location (Customer/Transporter view)

**Endpoint**: `GET /tracking/{trackingId}/live`

**Role**: `CUSTOMER`, `TRANSPORTER`, `ADMIN`

**Description**: Get current live location of driver.

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "trackingId": "TRACK-001-XYZ",
    "tripId": "TRIP-001-ABC",
    "driverName": "Ramesh Singh",
    "driverMobile": "+91-9876543210",
    "vehicleNumber": "DL-1A-1234",
    "currentLocation": {
      "latitude": 28.6550,
      "longitude": 77.2350,
      "accuracy": 12.3,
      "timestamp": 1735993030000
    },
    "status": "IN_PROGRESS",
    "speed": 45.5,
    "heading": 135.0,
    "tripProgress": {
      "distanceTraveled": 5.2,
      "distanceRemaining": 27.3,
      "progressPercentage": 16.0,
      "estimatedTimeRemaining": 36
    },
    "pickup": {
      "latitude": 28.7041,
      "longitude": 77.1025,
      "address": "Connaught Place, New Delhi",
      "reached": false,
      "distanceFromDriver": 12.5,
      "eta": 18
    },
    "drop": {
      "latitude": 28.5355,
      "longitude": 77.3910,
      "address": "Sector 18, Noida",
      "reached": false,
      "distanceFromDriver": 27.3,
      "eta": 36
    },
    "lastUpdated": 1735993030000
  },
  "message": "Live location retrieved",
  "timestamp": "2026-01-05T10:37:10Z"
}
```

---

## 4.4 Get Location History

**Endpoint**: `GET /tracking/{trackingId}/history`

**Role**: `CUSTOMER`, `TRANSPORTER`, `ADMIN`

**Description**: Get complete route history of the trip.

### Query Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| startTime | Long | trip start | Filter from timestamp |
| endTime | Long | now | Filter to timestamp |
| limit | Integer | 1000 | Max points to return |

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "trackingId": "TRACK-001-XYZ",
    "tripId": "TRIP-001-ABC",
    "locations": [
      {
        "latitude": 28.6500,
        "longitude": 77.2300,
        "speed": 0,
        "heading": 0,
        "timestamp": 1735993020000
      },
      {
        "latitude": 28.6510,
        "longitude": 77.2310,
        "speed": 25.5,
        "heading": 45,
        "timestamp": 1735993030000
      }
    ],
    "totalPoints": 2,
    "totalDistanceTraveled": 5.2,
    "tripDuration": 600,
    "averageSpeed": 31.2
  },
  "message": "Location history retrieved",
  "timestamp": "2026-01-05T10:50:00Z"
}
```

---

## 4.5 Mark Pickup Reached

**Endpoint**: `POST /tracking/{trackingId}/pickup-reached`

**Role**: `DRIVER`

**Description**: Driver marks that they've reached pickup location.

### Request Body

```json
{
  "reachedAt": 1735994000000,
  "currentLocation": {
    "latitude": 28.7041,
    "longitude": 77.1025
  },
  "notes": "Reached pickup location, loading goods"
}
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "trackingId": "TRACK-001-XYZ",
    "tripStatus": "AT_PICKUP",
    "pickupReachedAt": 1735994000000,
    "customerNotified": true,
    "nextAction": "START_LOADING"
  },
  "message": "Pickup location reached",
  "timestamp": "2026-01-05T10:53:20Z"
}
```

---

## 4.6 Start Trip (After loading)

**Endpoint**: `POST /tracking/{trackingId}/start-trip`

**Role**: `DRIVER`

**Description**: Driver starts the trip after loading goods.

### Request Body

```json
{
  "startedAt": 1735994300000,
  "currentLocation": {
    "latitude": 28.7041,
    "longitude": 77.1025
  },
  "loadingCompletedAt": 1735994300000,
  "notes": "Goods loaded, starting journey"
}
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "trackingId": "TRACK-001-XYZ",
    "tripStatus": "IN_TRANSIT",
    "tripStartedAt": 1735994300000,
    "route": {
      "from": "Connaught Place, New Delhi",
      "to": "Sector 18, Noida",
      "distance": 32.5,
      "estimatedDuration": 45
    },
    "customerNotified": true
  },
  "message": "Trip started successfully",
  "timestamp": "2026-01-05T10:58:20Z"
}
```

---

## 4.7 Mark Drop Reached

**Endpoint**: `POST /tracking/{trackingId}/drop-reached`

**Role**: `DRIVER`

**Description**: Driver marks arrival at drop location.

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "trackingId": "TRACK-001-XYZ",
    "tripStatus": "AT_DROP",
    "dropReachedAt": 1735996800000,
    "customerNotified": true,
    "nextAction": "COMPLETE_TRIP"
  },
  "message": "Drop location reached",
  "timestamp": "2026-01-05T11:40:00Z"
}
```

---

## 4.8 Complete Trip

**Endpoint**: `POST /tracking/{trackingId}/complete`

**Role**: `DRIVER`

**Description**: Complete the trip after unloading.

### Request Body

```json
{
  "completedAt": 1735997100000,
  "currentLocation": {
    "latitude": 28.5355,
    "longitude": 77.3910
  },
  "unloadingCompletedAt": 1735997100000,
  "otp": "1234",
  "customerSignature": "base64_signature_image",
  "notes": "Delivered successfully"
}
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "trackingId": "TRACK-001-XYZ",
    "tripId": "TRIP-001-ABC",
    "tripStatus": "COMPLETED",
    "completedAt": 1735997100000,
    "trackingStopped": true,
    "tripSummary": {
      "totalDistance": 32.8,
      "totalDuration": 67,
      "fare": 2500.00,
      "startTime": 1735994300000,
      "endTime": 1735997100000
    },
    "paymentStatus": "PENDING",
    "earnings": {
      "grossAmount": 2500.00,
      "commission": 250.00,
      "netAmount": 2250.00
    }
  },
  "message": "Trip completed successfully",
  "timestamp": "2026-01-05T11:45:00Z"
}
```

---

## WebSocket Events

### Subscribe to Tracking

```javascript
// Connect to WebSocket
ws = new WebSocket('wss://api.weelologistics.com/tracking/ws/TRACK-001-XYZ');

// Authenticate
ws.send(JSON.stringify({
  type: 'auth',
  token: 'jwt_token'
}));

// Listen for location updates
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  if (data.type === 'LOCATION_UPDATE') {
    updateMapMarker(data.latitude, data.longitude);
  }
};
```

### Location Update Event

```json
{
  "type": "LOCATION_UPDATE",
  "trackingId": "TRACK-001-XYZ",
  "latitude": 28.6550,
  "longitude": 77.2350,
  "speed": 45.5,
  "heading": 135.0,
  "timestamp": 1735993030000
}
```

---

**Next**: [API 5 - Security & Authentication →](API_5_SECURITY_AUTHENTICATION.md)
