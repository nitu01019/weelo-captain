# 6️⃣ WebSocket & Push Notification Guide

## Overview
This document provides implementation details for real-time communication using WebSockets and Push Notifications (FCM/APNS).

---

## 🌐 WebSocket Architecture

### Connection Flow

```
CLIENT CONNECTS
         ↓
WEBSOCKET HANDSHAKE
         ↓
CLIENT SENDS AUTH TOKEN
         ↓
SERVER VALIDATES & SUBSCRIBES TO CHANNELS
         ↓
BIDIRECTIONAL COMMUNICATION ESTABLISHED
         ↓
REAL-TIME EVENTS FLOW
```

---

## 📡 WebSocket Connection

### Server Implementation (Node.js + Socket.io)

```javascript
const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const jwt = require('jsonwebtoken');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
  cors: {
    origin: process.env.ALLOWED_ORIGINS,
    credentials: true
  }
});

// WebSocket authentication middleware
io.use((socket, next) => {
  const token = socket.handshake.auth.token;
  
  if (!token) {
    return next(new Error('Authentication required'));
  }
  
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    socket.userId = decoded.userId;
    socket.role = decoded.role;
    next();
  } catch (error) {
    next(new Error('Invalid token'));
  }
});

// Connection handler
io.on('connection', (socket) => {
  console.log(`User ${socket.userId} connected (${socket.role})`);
  
  // Subscribe to role-based rooms
  socket.join(`user:${socket.userId}`);
  socket.join(`role:${socket.role}`);
  
  // Driver-specific setup
  if (socket.role === 'DRIVER') {
    socket.on('location:update', async (data) => {
      await handleLocationUpdate(socket, data);
    });
    
    socket.on('trip:accept', async (data) => {
      await handleTripAccept(socket, data);
    });
  }
  
  // Transporter-specific setup
  if (socket.role === 'TRANSPORTER') {
    const transporterId = socket.userId;
    socket.join(`transporter:${transporterId}`);
  }
  
  // Customer-specific setup
  if (socket.role === 'CUSTOMER') {
    const customerId = socket.userId;
    socket.join(`customer:${customerId}`);
  }
  
  // Disconnect handler
  socket.on('disconnect', () => {
    console.log(`User ${socket.userId} disconnected`);
    updateUserStatus(socket.userId, 'offline');
  });
});

// Helper function to emit to specific user
function emitToUser(userId, event, data) {
  io.to(`user:${userId}`).emit(event, data);
}

// Helper function to emit to all transporters
function emitToTransporters(event, data) {
  io.to('role:TRANSPORTER').emit(event, data);
}

server.listen(3000, () => {
  console.log('Server running on port 3000');
});
```

### Client Implementation (Android - Kotlin)

```kotlin
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException

class WebSocketManager(private val token: String) {
    private var socket: Socket? = null
    
    fun connect(onConnect: () -> Unit, onEvent: (String, JSONObject) -> Unit) {
        try {
            val options = IO.Options().apply {
                auth = mapOf("token" to token)
                reconnection = true
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
            }
            
            socket = IO.socket("https://api.weelologistics.com", options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                println("WebSocket connected")
                onConnect()
            }
            
            socket?.on(Socket.EVENT_DISCONNECT) {
                println("WebSocket disconnected")
            }
            
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                println("Connection error: ${args[0]}")
            }
            
            // Listen for trip assignments
            socket?.on("trip:assigned") { args ->
                val data = args[0] as JSONObject
                onEvent("trip:assigned", data)
            }
            
            // Listen for location updates
            socket?.on("location:update") { args ->
                val data = args[0] as JSONObject
                onEvent("location:update", data)
            }
            
            socket?.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }
    
    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }
    
    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
```

---

## 📨 WebSocket Events

### 1. Broadcast Events

#### NEW_BROADCAST (Customer → Transporters)

**Emitted when**: Customer creates a new broadcast

**Sent to**: All transporters within radius

```javascript
// Server-side emit
io.to('role:TRANSPORTER').emit('broadcast:new', {
  type: 'NEW_BROADCAST',
  broadcastId: 'BC-2026-001-ABC123',
  customerId: 'CUST-123456',
  customerName: 'Rajesh Kumar',
  pickupLocation: {
    latitude: 28.7041,
    longitude: 77.1025,
    address: 'Connaught Place, New Delhi'
  },
  dropLocation: {
    latitude: 28.5355,
    longitude: 77.3910,
    address: 'Sector 18, Noida'
  },
  distance: 32.5,
  totalTrucksNeeded: 10,
  trucksFilledSoFar: 0,
  vehicleType: 'CONTAINER',
  farePerTruck: 2500.00,
  isUrgent: true,
  broadcastTime: 1735992600000,
  expiryTime: 1735996200000
});
```

#### BROADCAST_UPDATED

**Emitted when**: Broadcast status changes

```javascript
io.to('role:TRANSPORTER').emit('broadcast:updated', {
  type: 'BROADCAST_UPDATED',
  broadcastId: 'BC-2026-001-ABC123',
  status: 'PARTIALLY_FILLED',
  trucksFilledSoFar: 7,
  trucksRemaining: 3
});
```

---

### 2. Assignment Events

#### DRIVER_ASSIGNED (Transporter → Driver)

**Emitted when**: Transporter assigns a trip to driver

```javascript
io.to(`user:${driverId}`).emit('trip:assigned', {
  type: 'DRIVER_ASSIGNED',
  notificationId: 'NOTIF-001-ABC',
  assignmentId: 'ASSIGN-2026-001-XYZ',
  tripDetails: {
    broadcastId: 'BC-2026-001-ABC123',
    pickupAddress: 'Connaught Place, New Delhi',
    dropAddress: 'Sector 18, Noida',
    distance: 32.5,
    estimatedDuration: 45,
    fare: 2500.00,
    goodsType: 'Electronics',
    vehicleNumber: 'DL-1A-1234',
    customerName: 'Rajesh Kumar',
    customerMobile: '+91-9876543210'
  },
  expiryTime: 1735993200000,
  fullScreenAlarm: true
});
```

#### DRIVER_ACCEPTED (Driver → Transporter)

**Emitted when**: Driver accepts the trip

```javascript
io.to(`transporter:${transporterId}`).emit('driver:accepted', {
  type: 'DRIVER_ACCEPTED',
  assignmentId: 'ASSIGN-2026-001-XYZ',
  driverId: 'DRV-001',
  driverName: 'Ramesh Singh',
  vehicleNumber: 'DL-1A-1234',
  acceptedAt: 1735993020000,
  responseTime: 115
});
```

#### DRIVER_DECLINED (Driver → Transporter)

**Emitted when**: Driver declines the trip

```javascript
io.to(`transporter:${transporterId}`).emit('driver:declined', {
  type: 'DRIVER_DECLINED',
  assignmentId: 'ASSIGN-2026-001-XYZ',
  driverId: 'DRV-003',
  driverName: 'Vijay Sharma',
  vehicleNumber: 'DL-1A-9012',
  declinedAt: 1735993080000,
  reason: 'Vehicle has minor issue',
  reassignmentRequired: true
});
```

---

### 3. Location Tracking Events

#### LOCATION_UPDATE (Driver → Customer/Transporter)

**Emitted when**: Driver sends location update

```javascript
// Emit to customer and transporter
io.to(`customer:${customerId}`).emit('location:update', {
  type: 'LOCATION_UPDATE',
  trackingId: 'TRACK-001-XYZ',
  driverId: 'DRV-001',
  latitude: 28.6550,
  longitude: 77.2350,
  speed: 45.5,
  heading: 135.0,
  timestamp: 1735993030000
});

io.to(`transporter:${transporterId}`).emit('location:update', {
  type: 'LOCATION_UPDATE',
  trackingId: 'TRACK-001-XYZ',
  driverId: 'DRV-001',
  latitude: 28.6550,
  longitude: 77.2350,
  speed: 45.5,
  heading: 135.0,
  timestamp: 1735993030000
});
```

#### TRIP_STATUS_CHANGED

**Emitted when**: Trip status changes (pickup reached, started, completed)

```javascript
io.to(`customer:${customerId}`).emit('trip:status', {
  type: 'TRIP_STATUS_CHANGED',
  tripId: 'TRIP-001-ABC',
  status: 'IN_TRANSIT',
  timestamp: 1735994300000,
  message: 'Driver started the trip'
});
```

---

## 🔔 Push Notifications (FCM)

### Firebase Cloud Messaging Setup

#### Server Configuration

```javascript
const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
admin.initializeApp({
  credential: admin.credential.cert({
    projectId: process.env.FIREBASE_PROJECT_ID,
    clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
    privateKey: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n')
  })
});

// Send push notification function
async function sendPushNotification(fcmToken, notification, data) {
  try {
    const message = {
      token: fcmToken,
      notification: {
        title: notification.title,
        body: notification.body,
        imageUrl: notification.imageUrl
      },
      data: data,
      android: {
        priority: 'high',
        notification: {
          sound: notification.sound || 'default',
          channelId: notification.channelId || 'default',
          priority: 'max',
          visibility: 'public',
          color: '#FF6B35'
        }
      },
      apns: {
        headers: {
          'apns-priority': '10',
          'apns-push-type': 'alert'
        },
        payload: {
          aps: {
            alert: {
              title: notification.title,
              body: notification.body
            },
            sound: notification.sound || 'default',
            badge: 1,
            category: data.type,
            'content-available': 1
          }
        }
      }
    };
    
    const response = await admin.messaging().send(message);
    console.log('Push notification sent:', response);
    return response;
  } catch (error) {
    console.error('Error sending push notification:', error);
    throw error;
  }
}
```

---

### Push Notification Types

#### 1. Trip Assignment Notification (CRITICAL - FULL SCREEN)

```javascript
await sendPushNotification(
  driverFcmToken,
  {
    title: '🚛 New Trip Assignment!',
    body: 'Connaught Place → Noida | ₹2,500 | 32.5 km',
    sound: 'trip_alarm.mp3',
    channelId: 'trip_notifications'
  },
  {
    type: 'TRIP_ASSIGNMENT',
    notificationId: 'NOTIF-001-ABC',
    assignmentId: 'ASSIGN-2026-001-XYZ',
    broadcastId: 'BC-2026-001-ABC123',
    fullScreenAlarm: 'true',
    expiryTime: '1735993200000',
    tripData: JSON.stringify({
      pickupAddress: 'Connaught Place, New Delhi',
      dropAddress: 'Sector 18, Noida',
      distance: 32.5,
      fare: 2500.00
    })
  }
);
```

#### 2. Driver Accepted Notification (Transporter)

```javascript
await sendPushNotification(
  transporterFcmToken,
  {
    title: '✅ Driver Accepted',
    body: 'Ramesh Singh accepted trip assignment',
    sound: 'default',
    channelId: 'assignment_updates'
  },
  {
    type: 'DRIVER_ACCEPTED',
    assignmentId: 'ASSIGN-2026-001-XYZ',
    driverId: 'DRV-001',
    driverName: 'Ramesh Singh'
  }
);
```

#### 3. Broadcast Update Notification (Customer)

```javascript
await sendPushNotification(
  customerFcmToken,
  {
    title: '📦 Broadcast Update',
    body: '7 out of 10 trucks assigned',
    sound: 'default',
    channelId: 'broadcast_updates'
  },
  {
    type: 'BROADCAST_UPDATE',
    broadcastId: 'BC-2026-001-ABC123',
    trucksFilledSoFar: '7',
    trucksRemaining: '3'
  }
);
```

#### 4. Trip Started Notification (Customer)

```javascript
await sendPushNotification(
  customerFcmToken,
  {
    title: '🚚 Driver Started Trip',
    body: 'Ramesh Singh is on the way to pickup',
    sound: 'default',
    channelId: 'trip_updates'
  },
  {
    type: 'TRIP_STARTED',
    tripId: 'TRIP-001-ABC',
    driverId: 'DRV-001',
    trackingUrl: 'https://track.weelologistics.com/TRACK-001-XYZ'
  }
);
```

---

### Android FCM Implementation

#### AndroidManifest.xml

```xml
<manifest>
    <application>
        <!-- FCM Service -->
        <service
            android:name=".services.WeeloFirebaseMessagingService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
        
        <!-- Notification Channels -->
        <meta-data
            android:name="com.google.firebase.messaging.default_notification_channel_id"
            android:value="trip_notifications" />
    </application>
</manifest>
```

#### FCM Service (Kotlin)

```kotlin
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

class WeeloFirebaseMessagingService : FirebaseMessagingService() {
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        val data = remoteMessage.data
        val type = data["type"]
        
        when (type) {
            "TRIP_ASSIGNMENT" -> handleTripAssignment(data)
            "DRIVER_ACCEPTED" -> handleDriverAccepted(data)
            "LOCATION_UPDATE" -> handleLocationUpdate(data)
            "TRIP_STARTED" -> handleTripStarted(data)
            else -> showGenericNotification(remoteMessage)
        }
    }
    
    private fun handleTripAssignment(data: Map<String, String>) {
        val fullScreenAlarm = data["fullScreenAlarm"]?.toBoolean() ?: false
        
        if (fullScreenAlarm) {
            // Launch full-screen activity
            val intent = Intent(this, TripAcceptDeclineScreen::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("notificationId", data["notificationId"])
                putExtra("assignmentId", data["assignmentId"])
                putExtra("tripData", data["tripData"])
            }
            startActivity(intent)
            
            // Play alarm sound
            playAlarmSound()
            
            // Vibrate
            vibratePhone()
        } else {
            showTripNotification(data)
        }
    }
    
    private fun showTripNotification(data: Map<String, String>) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel (Android 8.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "trip_notifications",
                "Trip Assignments",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical trip assignment notifications"
                enableVibration(true)
                enableLights(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    null
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(this, TripAcceptDeclineScreen::class.java).apply {
            putExtra("notificationId", data["notificationId"])
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, "trip_notifications")
            .setSmallIcon(R.drawable.ic_truck)
            .setContentTitle(data["title"] ?: "New Trip Assignment")
            .setContentText(data["body"])
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_check,
                "Accept",
                createAcceptIntent(data["notificationId"])
            )
            .addAction(
                R.drawable.ic_close,
                "Decline",
                createDeclineIntent(data["notificationId"])
            )
            .build()
        
        notificationManager.notify(data["notificationId"].hashCode(), notification)
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Send token to backend
        sendTokenToServer(token)
    }
    
    private fun sendTokenToServer(token: String) {
        // Call API to update FCM token
        ApiService.updateFcmToken(token)
    }
}
```

---

## 📱 Notification Channels (Android)

```kotlin
fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        
        // Trip Assignment Channel (CRITICAL)
        val tripChannel = NotificationChannel(
            "trip_notifications",
            "Trip Assignments",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical trip assignment notifications"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            enableLights(true)
            lightColor = Color.RED
            setSound(
                Uri.parse("android.resource://${context.packageName}/raw/trip_alarm"),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        
        // Assignment Updates Channel
        val assignmentChannel = NotificationChannel(
            "assignment_updates",
            "Assignment Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Updates about driver assignments"
        }
        
        // Broadcast Updates Channel
        val broadcastChannel = NotificationChannel(
            "broadcast_updates",
            "Broadcast Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Updates about broadcast status"
        }
        
        notificationManager.createNotificationChannels(
            listOf(tripChannel, assignmentChannel, broadcastChannel)
        )
    }
}
```

---

## 🔧 Testing Tools

### WebSocket Testing (Browser Console)

```javascript
// Connect to WebSocket
const socket = io('https://api.weelologistics.com', {
  auth: {
    token: 'your_jwt_token_here'
  }
});

// Listen for events
socket.on('connect', () => {
  console.log('Connected');
});

socket.on('trip:assigned', (data) => {
  console.log('Trip assigned:', data);
});

// Emit test event
socket.emit('location:update', {
  latitude: 28.6550,
  longitude: 77.2350,
  speed: 45.5
});
```

### Push Notification Testing (cURL)

```bash
curl -X POST https://fcm.googleapis.com/fcm/send \
  -H "Authorization: key=YOUR_FCM_SERVER_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "to": "DEVICE_FCM_TOKEN",
    "notification": {
      "title": "Test Notification",
      "body": "This is a test"
    },
    "data": {
      "type": "TEST"
    }
  }'
```

---

**Next**: [API 7 - Data Models Reference →](API_7_DATA_MODELS.md)
