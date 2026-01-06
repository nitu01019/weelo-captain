# 3️⃣ Driver Notification & Response APIs

## Overview
These endpoints handle the critical driver notification system - the **FULL-SCREEN ALARM** notification that appears when a transporter assigns a trip. This is the most important UX moment for drivers.

---

## 🚨 Notification System Architecture

### Push Notification Flow
```
TRANSPORTER ASSIGNS DRIVER
         ↓
BACKEND CREATES NOTIFICATION RECORD
         ↓
SEND VIA MULTIPLE CHANNELS:
  1. FCM (Firebase Cloud Messaging) - Android
  2. APNS (Apple Push Notification) - iOS
  3. WebSocket (if driver online)
  4. SMS Backup (if no response in 2 min)
         ↓
DRIVER APP RECEIVES NOTIFICATION
         ↓
APP TRIGGERS FULL-SCREEN ALARM
  - Loud sound/ringtone
  - Vibration
  - Full screen overlay
  - Cannot dismiss easily
  - Auto-decline after timeout (5 min)
         ↓
DRIVER ACCEPTS OR DECLINES
         ↓
BACKEND RECEIVES RESPONSE
         ↓
UPDATE ALL SYSTEMS & NOTIFY TRANSPORTER
```

---

## 3.1 Send Driver Notification (Called by backend after assignment)

**Endpoint**: `POST /notifications/driver`

**Role**: `SYSTEM` (Internal - called after assignment created)

**Description**: Send notification to driver with full-screen alarm trigger.

### Request Body

```json
{
  "notificationId": "NOTIF-001-ABC",
  "assignmentId": "ASSIGN-2026-001-XYZ",
  "driverId": "DRV-001",
  "driverFcmToken": "fcm_token_here...",
  "driverMobile": "+91-9876543210",
  "priority": "HIGH",
  "tripDetails": {
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
    "fare": 2500.00,
    "goodsType": "Electronics",
    "weight": "5000 kg",
    "vehicleNumber": "DL-1A-1234",
    "transporterName": "Kumar Transport",
    "transporterMobile": "+91-9999888877"
  },
  "expiryTime": 1735993200000,
  "notificationSettings": {
    "soundEnabled": true,
    "vibrationEnabled": true,
    "fullScreenEnabled": true,
    "autoDeclineMinutes": 5
  }
}
```

### Response (201 Created)

```json
{
  "success": true,
  "data": {
    "notificationId": "NOTIF-001-ABC",
    "driverId": "DRV-001",
    "status": "SENT",
    "channelsSent": ["FCM", "WEBSOCKET"],
    "fcmMessageId": "fcm_msg_123456",
    "websocketDelivered": true,
    "sentAt": 1735992905000,
    "expiryTime": 1735993200000,
    "timeoutSeconds": 300
  },
  "message": "Notification sent successfully via FCM and WebSocket",
  "timestamp": "2026-01-05T10:35:05Z"
}
```

### FCM Payload Structure

```json
{
  "to": "fcm_token_here...",
  "priority": "high",
  "notification": {
    "title": "🚛 New Trip Assignment!",
    "body": "Connaught Place → Noida | ₹2,500 | 32.5 km",
    "sound": "trip_alarm.mp3",
    "badge": 1,
    "android_channel_id": "trip_notifications",
    "priority": "high"
  },
  "data": {
    "type": "TRIP_ASSIGNMENT",
    "notificationId": "NOTIF-001-ABC",
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "broadcastId": "BC-2026-001-ABC123",
    "driverId": "DRV-001",
    "fullScreenAlarm": "true",
    "expiryTime": "1735993200000",
    "tripData": "{...full trip details as JSON string...}"
  },
  "android": {
    "priority": "high",
    "notification": {
      "sound": "trip_alarm.mp3",
      "channel_id": "trip_notifications",
      "notification_priority": "PRIORITY_MAX",
      "visibility": "public"
    }
  },
  "apns": {
    "headers": {
      "apns-priority": "10",
      "apns-push-type": "alert"
    },
    "payload": {
      "aps": {
        "alert": {
          "title": "🚛 New Trip Assignment!",
          "body": "Connaught Place → Noida | ₹2,500 | 32.5 km"
        },
        "sound": "trip_alarm.mp3",
        "badge": 1,
        "category": "TRIP_ASSIGNMENT",
        "thread-id": "trip-notifications"
      }
    }
  }
}
```

### Backend Logic

1. **Create Notification Record**
   ```sql
   INSERT INTO driver_notifications (
     notification_id, assignment_id, driver_id,
     pickup_address, drop_address, distance, fare,
     sent_at, expiry_time, status
   ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_RESPONSE')
   ```

2. **Get Driver's FCM Token**
   ```sql
   SELECT fcm_token, is_online, last_seen 
   FROM drivers 
   WHERE driver_id = ?
   ```

3. **Send via FCM** (Primary Channel)
   - Use Firebase Admin SDK
   - Set priority to HIGH
   - Include data payload for full-screen trigger
   
   ```javascript
   const message = {
     token: driverFcmToken,
     notification: { title, body, sound },
     data: { type: 'TRIP_ASSIGNMENT', ... },
     android: { priority: 'high' },
     apns: { headers: { 'apns-priority': '10' } }
   };
   
   const response = await admin.messaging().send(message);
   ```

4. **Send via WebSocket** (If Driver Online)
   ```javascript
   if (driver.isOnline) {
     websocket.send(driverId, {
       type: 'TRIP_ASSIGNMENT',
       notificationId: 'NOTIF-001-ABC',
       tripDetails: {...},
       fullScreenAlarm: true
     });
   }
   ```

5. **Schedule Auto-Decline** (After 5 minutes)
   - Add to Redis/job queue
   - If no response after 5 min → auto-decline

6. **Update Assignment Status**
   ```sql
   UPDATE driver_truck_assignments
   SET notification_id = ?, status = 'NOTIFICATION_SENT'
   WHERE assignment_id = ? AND driver_id = ?
   ```

### Database Schema

```sql
CREATE TABLE driver_notifications (
    notification_id VARCHAR(50) PRIMARY KEY,
    assignment_id VARCHAR(50) NOT NULL,
    driver_id VARCHAR(50) NOT NULL,
    pickup_address TEXT NOT NULL,
    drop_address TEXT NOT NULL,
    distance DECIMAL(8,2) NOT NULL,
    estimated_duration INT NOT NULL,
    fare DECIMAL(10,2) NOT NULL,
    goods_type VARCHAR(100),
    sent_at BIGINT NOT NULL,
    expiry_time BIGINT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    sound_played BOOLEAN DEFAULT FALSE,
    status VARCHAR(50) DEFAULT 'PENDING_RESPONSE',
    response_type VARCHAR(20), -- ACCEPTED, DECLINED, EXPIRED
    responded_at BIGINT,
    fcm_message_id VARCHAR(100),
    websocket_delivered BOOLEAN DEFAULT FALSE,
    sms_sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (assignment_id) REFERENCES trip_assignments(assignment_id),
    INDEX idx_driver (driver_id),
    INDEX idx_status (status),
    INDEX idx_expiry (expiry_time)
);
```

---

## 3.2 Driver Accepts Trip

**Endpoint**: `POST /notifications/{notificationId}/accept`

**Role**: `DRIVER`

**Description**: Driver accepts the trip assignment. This triggers GPS tracking initialization.

### Request Body

```json
{
  "driverId": "DRV-001",
  "acceptedAt": 1735993020000,
  "currentLocation": {
    "latitude": 28.6500,
    "longitude": 77.2300
  },
  "estimatedArrivalTime": "2026-01-05T14:00:00Z",
  "deviceInfo": {
    "platform": "android",
    "appVersion": "2.1.0",
    "osVersion": "14"
  }
}
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "notificationId": "NOTIF-001-ABC",
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "driverId": "DRV-001",
    "status": "ACCEPTED",
    "acceptedAt": 1735993020000,
    "responseTime": 115,
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
      "estimatedDuration": 45,
      "fare": 2500.00,
      "goodsType": "Electronics",
      "customerName": "Rajesh Kumar",
      "customerMobile": "+91-9876543210",
      "vehicleNumber": "DL-1A-1234"
    },
    "gpsTracking": {
      "trackingId": "TRACK-001-XYZ",
      "enabled": true,
      "updateIntervalSeconds": 10,
      "trackingUrl": "wss://api.weelologistics.com/tracking/TRACK-001-XYZ"
    },
    "nextSteps": [
      "Enable GPS location sharing",
      "Navigate to pickup location",
      "Contact customer if needed",
      "Start trip when reached pickup"
    ]
  },
  "message": "Trip accepted successfully. GPS tracking initialized.",
  "timestamp": "2026-01-05T10:37:00Z"
}
```

### Backend Logic

1. **Validate Request**
   - Check notification exists and is PENDING_RESPONSE
   - Verify driver is correct recipient
   - Check not expired

2. **Update Notification Status**
   ```sql
   UPDATE driver_notifications
   SET status = 'ACCEPTED', 
       response_type = 'ACCEPTED',
       responded_at = ?,
       is_read = TRUE
   WHERE notification_id = ?
   ```

3. **Update Assignment**
   ```sql
   UPDATE driver_truck_assignments
   SET status = 'ACCEPTED',
       response_type = 'ACCEPTED',
       responded_at = ?
   WHERE assignment_id = ? AND driver_id = ?
   ```

4. **Check if All Drivers Accepted**
   ```sql
   SELECT COUNT(*) as pending_count
   FROM driver_truck_assignments
   WHERE assignment_id = ? AND status = 'PENDING'
   ```
   
   If pending_count = 0:
   ```sql
   UPDATE trip_assignments
   SET status = 'FULLY_ACCEPTED'
   WHERE assignment_id = ?
   ```

5. **Create Trip Record**
   ```sql
   INSERT INTO trips (
     trip_id, assignment_id, broadcast_id,
     driver_id, vehicle_id, customer_id,
     pickup_lat, pickup_lng, drop_lat, drop_lng,
     distance, fare, status, accepted_at
   ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACCEPTED', ?)
   ```

6. **Initialize GPS Tracking**
   ```sql
   INSERT INTO live_trip_tracking (
     tracking_id, trip_id, driver_id, vehicle_id,
     status, tracking_started_at
   ) VALUES (?, ?, ?, ?, 'WAITING_TO_START', ?)
   ```

7. **Notify Transporter** (WebSocket + Push)
   ```json
   {
     "type": "DRIVER_ACCEPTED",
     "assignmentId": "ASSIGN-2026-001-XYZ",
     "driverId": "DRV-001",
     "driverName": "Ramesh Singh",
     "vehicleNumber": "DL-1A-1234",
     "acceptedAt": 1735993020000
   }
   ```

8. **Notify Customer** (WebSocket + Push)
   ```json
   {
     "type": "DRIVER_ASSIGNED",
     "broadcastId": "BC-2026-001-ABC123",
     "driverName": "Ramesh Singh",
     "vehicleNumber": "DL-1A-1234",
     "driverMobile": "+91-9876543210",
     "trackingUrl": "https://track.weelologistics.com/TRACK-001-XYZ"
   }
   ```

9. **Enable GPS Permissions** (Send instruction to driver app)
   - Request location permissions
   - Start background location service
   - Begin sending location updates

---

## 3.3 Driver Declines Trip

**Endpoint**: `POST /notifications/{notificationId}/decline`

**Role**: `DRIVER`

**Description**: Driver declines the trip assignment.

### Request Body

```json
{
  "driverId": "DRV-003",
  "declinedAt": 1735993080000,
  "reason": "VEHICLE_ISSUE",
  "reasonText": "Vehicle has minor mechanical issue",
  "currentLocation": {
    "latitude": 28.6500,
    "longitude": 77.2300
  }
}
```

### Decline Reasons (Enum)

| Code | Display Text |
|------|-------------|
| VEHICLE_ISSUE | Vehicle has a problem |
| PERSONAL_EMERGENCY | Personal emergency |
| ALREADY_BUSY | Already on another trip |
| TOO_FAR | Pickup location too far |
| LOW_FARE | Fare too low |
| OTHER | Other reason |

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "notificationId": "NOTIF-003-GHI",
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "driverId": "DRV-003",
    "status": "DECLINED",
    "declinedAt": 1735993080000,
    "reason": "VEHICLE_ISSUE",
    "reasonText": "Vehicle has minor mechanical issue",
    "responseTime": 180,
    "reassignmentRequired": true,
    "transporterNotified": true
  },
  "message": "Trip declined. Transporter has been notified for reassignment.",
  "timestamp": "2026-01-05T10:38:00Z"
}
```

### Backend Logic

1. **Validate Request**
   - Check notification exists and is PENDING_RESPONSE
   - Verify driver is correct recipient

2. **Update Notification Status**
   ```sql
   UPDATE driver_notifications
   SET status = 'DECLINED',
       response_type = 'DECLINED',
       responded_at = ?,
       decline_reason = ?
   WHERE notification_id = ?
   ```

3. **Update Assignment**
   ```sql
   UPDATE driver_truck_assignments
   SET status = 'DECLINED',
       response_type = 'DECLINED',
       responded_at = ?,
       decline_reason = ?
   WHERE assignment_id = ? AND driver_id = ?
   ```

4. **Create Reassignment Record**
   ```sql
   INSERT INTO trip_reassignments (
     reassignment_id, assignment_id, vehicle_id,
     previous_driver_id, declined_at, decline_reason, status
   ) VALUES (?, ?, ?, ?, ?, ?, 'WAITING_FOR_NEW_DRIVER')
   ```

5. **Update Vehicle Status**
   ```sql
   UPDATE vehicles
   SET status = 'AVAILABLE'
   WHERE vehicle_id = ?
   ```

6. **Update Driver Status**
   ```sql
   UPDATE drivers
   SET status = 'ACTIVE'
   WHERE driver_id = ?
   ```

7. **Notify Transporter** (WebSocket + Push)
   ```json
   {
     "type": "DRIVER_DECLINED",
     "assignmentId": "ASSIGN-2026-001-XYZ",
     "driverId": "DRV-003",
     "driverName": "Vijay Sharma",
     "vehicleNumber": "DL-1A-9012",
     "declinedAt": 1735993080000,
     "reason": "Vehicle has minor mechanical issue",
     "action": "REASSIGN_REQUIRED"
   }
   ```

8. **Record Analytics**
   - Decline rate per driver
   - Most common decline reasons
   - Response time statistics

---

## 3.4 Get Driver's Active Notifications

**Endpoint**: `GET /notifications/driver/active`

**Role**: `DRIVER`

**Description**: Get all active (pending) notifications for driver.

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "notifications": [
      {
        "notificationId": "NOTIF-001-ABC",
        "assignmentId": "ASSIGN-2026-001-XYZ",
        "status": "PENDING_RESPONSE",
        "pickupAddress": "Connaught Place, New Delhi",
        "dropAddress": "Sector 18, Noida",
        "distance": 32.5,
        "estimatedDuration": 45,
        "fare": 2500.00,
        "goodsType": "Electronics",
        "vehicleNumber": "DL-1A-1234",
        "sentAt": 1735992905000,
        "expiryTime": 1735993200000,
        "timeRemaining": 295,
        "isUrgent": false,
        "customerName": "Rajesh Kumar",
        "transporterName": "Kumar Transport"
      }
    ],
    "count": 1
  },
  "message": "Active notifications retrieved",
  "timestamp": "2026-01-05T10:35:10Z"
}
```

---

## 3.5 Mark Notification as Read

**Endpoint**: `PATCH /notifications/{notificationId}/read`

**Role**: `DRIVER`

**Description**: Mark notification as read (driver opened it but hasn't responded yet).

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "notificationId": "NOTIF-001-ABC",
    "isRead": true,
    "readAt": 1735992920000
  },
  "message": "Notification marked as read",
  "timestamp": "2026-01-05T10:35:20Z"
}
```

---

## 3.6 Auto-Decline Expired Notifications (Background Job)

**Endpoint**: `POST /notifications/process-expired` (Internal cron job)

**Role**: `SYSTEM`

**Description**: Background job that runs every minute to auto-decline expired notifications.

### Backend Logic

```javascript
// Cron Job - runs every 60 seconds
async function processExpiredNotifications() {
  const currentTime = Date.now();
  
  // Find expired notifications
  const expired = await db.query(`
    SELECT notification_id, assignment_id, driver_id
    FROM driver_notifications
    WHERE status = 'PENDING_RESPONSE'
    AND expiry_time < ?
  `, [currentTime]);
  
  for (const notification of expired) {
    // Update notification
    await db.query(`
      UPDATE driver_notifications
      SET status = 'EXPIRED', response_type = 'EXPIRED'
      WHERE notification_id = ?
    `, [notification.notification_id]);
    
    // Update assignment
    await db.query(`
      UPDATE driver_truck_assignments
      SET status = 'EXPIRED'
      WHERE assignment_id = ? AND driver_id = ?
    `, [notification.assignment_id, notification.driver_id]);
    
    // Notify transporter about auto-decline
    await notifyTransporter(notification.assignment_id, {
      type: 'DRIVER_AUTO_DECLINED',
      driverId: notification.driver_id,
      reason: 'No response within timeout period'
    });
    
    // Create reassignment record
    await createReassignment(notification);
  }
}
```

---

## 3.7 Get Notification History

**Endpoint**: `GET /notifications/driver/history`

**Role**: `DRIVER`

**Description**: Get driver's notification history.

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| status | String | No | all | Filter: all, accepted, declined, expired |
| page | Integer | No | 1 | Page number |
| limit | Integer | No | 20 | Items per page |
| startDate | DateTime | No | 30 days ago | Start date |
| endDate | DateTime | No | now | End date |

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "notifications": [
      {
        "notificationId": "NOTIF-001-ABC",
        "status": "ACCEPTED",
        "route": "Connaught Place → Noida",
        "distance": 32.5,
        "fare": 2500.00,
        "sentAt": 1735992905000,
        "respondedAt": 1735993020000,
        "responseTime": 115,
        "responseType": "ACCEPTED"
      },
      {
        "notificationId": "NOTIF-002-DEF",
        "status": "DECLINED",
        "route": "India Gate → Gurgaon",
        "distance": 28.3,
        "fare": 2200.00,
        "sentAt": 1735985600000,
        "respondedAt": 1735985750000,
        "responseTime": 150,
        "responseType": "DECLINED",
        "declineReason": "Personal emergency"
      }
    ],
    "statistics": {
      "totalNotifications": 45,
      "accepted": 38,
      "declined": 5,
      "expired": 2,
      "acceptanceRate": 84.4,
      "avgResponseTime": 142
    },
    "pagination": {
      "currentPage": 1,
      "totalPages": 3,
      "totalItems": 45
    }
  },
  "message": "Notification history retrieved",
  "timestamp": "2026-01-05T10:40:00Z"
}
```

---

## 🔔 SMS Backup Notification

If driver doesn't respond within 2 minutes, send SMS as backup:

### SMS Template

```
🚛 URGENT: New trip assigned!
From: Connaught Place
To: Noida
Distance: 32.5 km
Fare: ₹2,500

Open Weelo app to ACCEPT/DECLINE
Expires in 3 minutes!
- Kumar Transport
```

### Backend Logic

```javascript
// After 2 minutes of no response
setTimeout(async () => {
  const notification = await getNotification(notificationId);
  
  if (notification.status === 'PENDING_RESPONSE' && !notification.isRead) {
    // Send SMS
    await smsService.send({
      to: driverMobile,
      message: smsTemplate,
      priority: 'high'
    });
    
    // Update record
    await db.query(`
      UPDATE driver_notifications
      SET sms_sent = TRUE
      WHERE notification_id = ?
    `, [notificationId]);
  }
}, 120000); // 2 minutes
```

---

## Error Codes

| Code | Message | HTTP Status | Description |
|------|---------|-------------|-------------|
| NOTIF_001 | Notification not found | 404 | NotificationId doesn't exist |
| NOTIF_002 | Notification expired | 410 | Cannot respond to expired notification |
| NOTIF_003 | Already responded | 409 | Driver already accepted/declined |
| NOTIF_004 | Invalid driver | 403 | Notification not for this driver |
| NOTIF_005 | FCM token not found | 404 | Driver hasn't registered FCM token |
| NOTIF_006 | Notification send failed | 500 | FCM/APNS send error |
| NOTIF_007 | Invalid decline reason | 400 | Unknown decline reason code |

---

**Next**: [API 4 - GPS Tracking Endpoints →](API_4_GPS_TRACKING_ENDPOINTS.md)
