# 7️⃣ Data Models Reference

## Overview
Complete reference for all data models used in the Weelo Logistics API. Use these as your database schema and API request/response structures.

---

## 📊 Core Data Models

### 1. BroadcastTrip

Customer broadcast message sent to transporters.

```json
{
  "broadcastId": "string (50)",
  "customerId": "string (50)",
  "customerName": "string (100)",
  "customerMobile": "string (20)",
  
  "pickupLocation": {
    "latitude": "decimal(10,8)",
    "longitude": "decimal(11,8)",
    "address": "text",
    "city": "string (100)",
    "state": "string (100)",
    "pincode": "string (10)"
  },
  
  "dropLocation": {
    "latitude": "decimal(10,8)",
    "longitude": "decimal(11,8)",
    "address": "text",
    "city": "string (100)",
    "state": "string (100)",
    "pincode": "string (10)"
  },
  
  "distance": "decimal(8,2)",
  "estimatedDuration": "int (minutes)",
  
  "totalTrucksNeeded": "int (1-100)",
  "trucksFilledSoFar": "int (0-100)",
  "vehicleType": "enum [MINI, OPEN, CONTAINER, TRAILER, TANKER, TIPPER, BULKER, LCV, DUMPER]",
  "goodsType": "string (100)",
  "weight": "string (50)",
  
  "farePerTruck": "decimal(10,2)",
  "totalFare": "decimal(10,2)",
  
  "status": "enum [ACTIVE, PARTIALLY_FILLED, FULLY_FILLED, EXPIRED, CANCELLED]",
  "broadcastTime": "bigint (timestamp ms)",
  "expiryTime": "bigint (timestamp ms)",
  
  "notes": "text (500)",
  "isUrgent": "boolean"
}
```

**Database Schema**:
```sql
CREATE TABLE broadcasts (
    broadcast_id VARCHAR(50) PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    customer_mobile VARCHAR(20) NOT NULL,
    
    pickup_lat DECIMAL(10,8) NOT NULL,
    pickup_lng DECIMAL(11,8) NOT NULL,
    pickup_address TEXT NOT NULL,
    pickup_city VARCHAR(100),
    pickup_state VARCHAR(100),
    pickup_pincode VARCHAR(10),
    
    drop_lat DECIMAL(10,8) NOT NULL,
    drop_lng DECIMAL(11,8) NOT NULL,
    drop_address TEXT NOT NULL,
    drop_city VARCHAR(100),
    drop_state VARCHAR(100),
    drop_pincode VARCHAR(10),
    
    distance DECIMAL(8,2) NOT NULL,
    estimated_duration INT NOT NULL,
    
    total_trucks_needed INT NOT NULL CHECK (total_trucks_needed BETWEEN 1 AND 100),
    trucks_filled INT DEFAULT 0 CHECK (trucks_filled <= total_trucks_needed),
    vehicle_type VARCHAR(20) NOT NULL,
    goods_type VARCHAR(100) NOT NULL,
    weight VARCHAR(50),
    
    fare_per_truck DECIMAL(10,2) NOT NULL,
    total_fare DECIMAL(10,2) NOT NULL,
    
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PARTIALLY_FILLED', 'FULLY_FILLED', 'EXPIRED', 'CANCELLED')),
    broadcast_time BIGINT NOT NULL,
    expiry_time BIGINT,
    
    notes TEXT,
    is_urgent BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_status (status),
    INDEX idx_customer (customer_id),
    INDEX idx_broadcast_time (broadcast_time),
    INDEX idx_vehicle_type (vehicle_type)
);
```

---

### 2. TripAssignment

Transporter's response to broadcast with driver assignments.

```json
{
  "assignmentId": "string (50)",
  "broadcastId": "string (50)",
  "transporterId": "string (50)",
  "transporterName": "string (100)",
  "transporterMobile": "string (20)",
  
  "trucksTaken": "int (1-100)",
  
  "assignments": [
    {
      "driverId": "string (50)",
      "driverName": "string (100)",
      "driverMobile": "string (20)",
      "vehicleId": "string (50)",
      "vehicleNumber": "string (20)",
      "status": "enum [PENDING, ACCEPTED, DECLINED, EXPIRED, REASSIGNED]"
    }
  ],
  
  "assignedAt": "bigint (timestamp ms)",
  "estimatedArrivalTime": "bigint (timestamp ms)",
  "status": "enum [PENDING_DRIVER_RESPONSE, DRIVER_ACCEPTED, DRIVER_DECLINED, TRIP_STARTED, TRIP_COMPLETED, CANCELLED]",
  "notes": "text"
}
```

**Database Schema**:
```sql
CREATE TABLE trip_assignments (
    assignment_id VARCHAR(50) PRIMARY KEY,
    broadcast_id VARCHAR(50) NOT NULL,
    transporter_id VARCHAR(50) NOT NULL,
    transporter_name VARCHAR(100) NOT NULL,
    transporter_mobile VARCHAR(20) NOT NULL,
    
    trucks_taken INT NOT NULL,
    assigned_at BIGINT NOT NULL,
    estimated_arrival_time BIGINT,
    status VARCHAR(50) DEFAULT 'PENDING_DRIVER_RESPONSE',
    notes TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (broadcast_id) REFERENCES broadcasts(broadcast_id),
    INDEX idx_broadcast (broadcast_id),
    INDEX idx_transporter (transporter_id),
    INDEX idx_status (status)
);

CREATE TABLE driver_truck_assignments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    assignment_id VARCHAR(50) NOT NULL,
    driver_id VARCHAR(50) NOT NULL,
    driver_name VARCHAR(100) NOT NULL,
    driver_mobile VARCHAR(20) NOT NULL,
    vehicle_id VARCHAR(50) NOT NULL,
    vehicle_number VARCHAR(20) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    notification_id VARCHAR(50),
    assigned_at BIGINT NOT NULL,
    responded_at BIGINT,
    response_type VARCHAR(20),
    decline_reason TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (assignment_id) REFERENCES trip_assignments(assignment_id),
    INDEX idx_assignment (assignment_id),
    INDEX idx_driver (driver_id),
    INDEX idx_status (status)
);
```

---

### 3. DriverNotification

Push notification sent to driver.

```json
{
  "notificationId": "string (50)",
  "assignmentId": "string (50)",
  "driverId": "string (50)",
  
  "pickupAddress": "text",
  "dropAddress": "text",
  "distance": "decimal(8,2)",
  "estimatedDuration": "int (minutes)",
  "fare": "decimal(10,2)",
  "goodsType": "string (100)",
  
  "receivedAt": "bigint (timestamp ms)",
  "expiryTime": "bigint (timestamp ms)",
  "isRead": "boolean",
  "soundPlayed": "boolean",
  
  "status": "enum [PENDING_RESPONSE, ACCEPTED, DECLINED, EXPIRED, READ]",
  "responseType": "enum [ACCEPTED, DECLINED, EXPIRED]",
  "respondedAt": "bigint (timestamp ms)",
  
  "fcmMessageId": "string (100)",
  "websocketDelivered": "boolean",
  "smseSent": "boolean"
}
```

**Database Schema**:
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
    response_type VARCHAR(20),
    responded_at BIGINT,
    decline_reason TEXT,
    
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

### 4. Trip

Individual trip record after driver accepts.

```json
{
  "tripId": "string (50)",
  "assignmentId": "string (50)",
  "broadcastId": "string (50)",
  
  "customerId": "string (50)",
  "customerName": "string (100)",
  "customerMobile": "string (20)",
  
  "transporterId": "string (50)",
  "transporterName": "string (100)",
  
  "driverId": "string (50)",
  "driverName": "string (100)",
  "driverMobile": "string (20)",
  
  "vehicleId": "string (50)",
  "vehicleNumber": "string (20)",
  
  "pickupLocation": { /* Location object */ },
  "dropLocation": { /* Location object */ },
  
  "distance": "decimal(8,2)",
  "estimatedDuration": "int",
  "fare": "decimal(10,2)",
  "goodsType": "string (100)",
  "weight": "string (50)",
  
  "status": "enum [ACCEPTED, AT_PICKUP, IN_TRANSIT, AT_DROP, COMPLETED, CANCELLED]",
  
  "acceptedAt": "bigint",
  "pickupReachedAt": "bigint",
  "tripStartedAt": "bigint",
  "dropReachedAt": "bigint",
  "completedAt": "bigint",
  
  "actualDistance": "decimal(8,2)",
  "actualDuration": "int",
  
  "notes": "text"
}
```

**Database Schema**:
```sql
CREATE TABLE trips (
    trip_id VARCHAR(50) PRIMARY KEY,
    assignment_id VARCHAR(50) NOT NULL,
    broadcast_id VARCHAR(50) NOT NULL,
    
    customer_id VARCHAR(50) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    customer_mobile VARCHAR(20) NOT NULL,
    
    transporter_id VARCHAR(50) NOT NULL,
    transporter_name VARCHAR(100) NOT NULL,
    
    driver_id VARCHAR(50) NOT NULL,
    driver_name VARCHAR(100) NOT NULL,
    driver_mobile VARCHAR(20) NOT NULL,
    
    vehicle_id VARCHAR(50) NOT NULL,
    vehicle_number VARCHAR(20) NOT NULL,
    
    pickup_lat DECIMAL(10,8) NOT NULL,
    pickup_lng DECIMAL(11,8) NOT NULL,
    pickup_address TEXT NOT NULL,
    
    drop_lat DECIMAL(10,8) NOT NULL,
    drop_lng DECIMAL(11,8) NOT NULL,
    drop_address TEXT NOT NULL,
    
    distance DECIMAL(8,2) NOT NULL,
    estimated_duration INT NOT NULL,
    fare DECIMAL(10,2) NOT NULL,
    goods_type VARCHAR(100),
    weight VARCHAR(50),
    
    status VARCHAR(20) DEFAULT 'ACCEPTED',
    
    accepted_at BIGINT NOT NULL,
    pickup_reached_at BIGINT,
    trip_started_at BIGINT,
    drop_reached_at BIGINT,
    completed_at BIGINT,
    
    actual_distance DECIMAL(8,2),
    actual_duration INT,
    
    notes TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (assignment_id) REFERENCES trip_assignments(assignment_id),
    INDEX idx_driver (driver_id),
    INDEX idx_customer (customer_id),
    INDEX idx_transporter (transporter_id),
    INDEX idx_status (status),
    INDEX idx_accepted_at (accepted_at)
);
```

---

### 5. LiveTripTracking

Real-time GPS tracking data.

```json
{
  "trackingId": "string (50)",
  "tripId": "string (50)",
  "driverId": "string (50)",
  "vehicleId": "string (50)",
  
  "currentLatitude": "decimal(10,8)",
  "currentLongitude": "decimal(11,8)",
  "currentSpeed": "float (km/h)",
  "heading": "float (degrees 0-360)",
  "altitude": "float (meters)",
  
  "tripStatus": "enum [INITIALIZED, WAITING_TO_START, AT_PICKUP, IN_TRANSIT, AT_DROP, COMPLETED, STOPPED]",
  
  "trackingStartedAt": "bigint",
  "lastUpdated": "bigint",
  "isLocationSharing": "boolean",
  
  "updateIntervalSeconds": "int (default: 10)"
}
```

**Database Schema**:
```sql
CREATE TABLE live_trip_tracking (
    tracking_id VARCHAR(50) PRIMARY KEY,
    trip_id VARCHAR(50) NOT NULL,
    driver_id VARCHAR(50) NOT NULL,
    vehicle_id VARCHAR(50) NOT NULL,
    
    current_latitude DECIMAL(10,8),
    current_longitude DECIMAL(11,8),
    current_speed FLOAT DEFAULT 0,
    heading FLOAT DEFAULT 0,
    altitude FLOAT,
    
    trip_status VARCHAR(20) DEFAULT 'INITIALIZED',
    
    tracking_started_at BIGINT NOT NULL,
    last_updated BIGINT,
    is_location_sharing BOOLEAN DEFAULT TRUE,
    update_interval_seconds INT DEFAULT 10,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (trip_id) REFERENCES trips(trip_id),
    INDEX idx_trip (trip_id),
    INDEX idx_driver (driver_id),
    INDEX idx_last_updated (last_updated)
);

CREATE TABLE location_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tracking_id VARCHAR(50) NOT NULL,
    latitude DECIMAL(10,8) NOT NULL,
    longitude DECIMAL(11,8) NOT NULL,
    accuracy FLOAT,
    speed FLOAT,
    heading FLOAT,
    altitude FLOAT,
    timestamp BIGINT NOT NULL,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (tracking_id) REFERENCES live_trip_tracking(tracking_id),
    INDEX idx_tracking (tracking_id),
    INDEX idx_timestamp (timestamp)
);
```

---

### 6. User Models

#### Driver

```json
{
  "driverId": "string (50)",
  "transporterId": "string (50)",
  "name": "string (100)",
  "mobile": "string (20)",
  "email": "string (100)",
  
  "licenseNumber": "string (50)",
  "licenseExpiry": "bigint",
  "aadharNumber": "string (12) [encrypted]",
  
  "assignedVehicleId": "string (50)",
  "isAvailable": "boolean",
  "status": "enum [ACTIVE, ON_TRIP, INACTIVE, SUSPENDED]",
  
  "rating": "decimal(2,1)",
  "totalTrips": "int",
  "completedTrips": "int",
  "cancelledTrips": "int",
  
  "fcmToken": "string (255)",
  "isOnline": "boolean",
  "lastSeen": "bigint",
  
  "profileImageUrl": "string (500)",
  "createdAt": "bigint",
  "verifiedAt": "bigint"
}
```

**Database Schema**:
```sql
CREATE TABLE drivers (
    driver_id VARCHAR(50) PRIMARY KEY,
    transporter_id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    mobile VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(100),
    
    license_number VARCHAR(50) NOT NULL,
    license_expiry BIGINT,
    aadhar_number_encrypted TEXT,
    
    assigned_vehicle_id VARCHAR(50),
    is_available BOOLEAN DEFAULT TRUE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    
    rating DECIMAL(2,1) DEFAULT 0.0,
    total_trips INT DEFAULT 0,
    completed_trips INT DEFAULT 0,
    cancelled_trips INT DEFAULT 0,
    
    fcm_token VARCHAR(255),
    is_online BOOLEAN DEFAULT FALSE,
    last_seen BIGINT,
    
    profile_image_url VARCHAR(500),
    created_at BIGINT NOT NULL,
    verified_at BIGINT,
    
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_transporter (transporter_id),
    INDEX idx_mobile (mobile),
    INDEX idx_status (status),
    INDEX idx_is_available (is_available)
);
```

#### Transporter

```sql
CREATE TABLE transporters (
    transporter_id VARCHAR(50) PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL,
    mobile VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(100),
    
    gst_number VARCHAR(15),
    pan_number VARCHAR(10),
    
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    pincode VARCHAR(10),
    
    location_lat DECIMAL(10,8),
    location_lng DECIMAL(11,8),
    
    status VARCHAR(20) DEFAULT 'ACTIVE',
    rating DECIMAL(2,1) DEFAULT 0.0,
    
    total_vehicles INT DEFAULT 0,
    total_drivers INT DEFAULT 0,
    total_trips INT DEFAULT 0,
    
    fcm_token VARCHAR(255),
    is_online BOOLEAN DEFAULT FALSE,
    
    created_at BIGINT NOT NULL,
    verified_at BIGINT,
    
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_mobile (mobile),
    INDEX idx_status (status)
);
```

#### Customer

```sql
CREATE TABLE customers (
    customer_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    mobile VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(100),
    
    company_name VARCHAR(200),
    gst_number VARCHAR(15),
    
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    
    status VARCHAR(20) DEFAULT 'ACTIVE',
    rating DECIMAL(2,1) DEFAULT 0.0,
    
    total_bookings INT DEFAULT 0,
    total_spend DECIMAL(12,2) DEFAULT 0.0,
    
    fcm_token VARCHAR(255),
    
    created_at BIGINT NOT NULL,
    verified_at BIGINT,
    
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_mobile (mobile),
    INDEX idx_status (status)
);
```

---

### 7. Vehicle Model

```sql
CREATE TABLE vehicles (
    vehicle_id VARCHAR(50) PRIMARY KEY,
    transporter_id VARCHAR(50) NOT NULL,
    vehicle_number VARCHAR(20) UNIQUE NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL,
    
    manufacturer VARCHAR(100),
    model VARCHAR(100),
    year INT,
    capacity VARCHAR(50),
    
    registration_expiry BIGINT,
    insurance_expiry BIGINT,
    fitness_expiry BIGINT,
    pollution_expiry BIGINT,
    
    status VARCHAR(20) DEFAULT 'AVAILABLE',
    assigned_driver_id VARCHAR(50),
    
    last_service_date BIGINT,
    total_trips INT DEFAULT 0,
    total_distance DECIMAL(10,2) DEFAULT 0.0,
    
    created_at BIGINT NOT NULL,
    
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    FOREIGN KEY (transporter_id) REFERENCES transporters(transporter_id),
    INDEX idx_transporter (transporter_id),
    INDEX idx_vehicle_number (vehicle_number),
    INDEX idx_vehicle_type (vehicle_type),
    INDEX idx_status (status)
);
```

---

## 🔄 Status Enums

### BroadcastStatus
- `ACTIVE` - Broadcasting to transporters
- `PARTIALLY_FILLED` - Some trucks assigned
- `FULLY_FILLED` - All trucks assigned
- `EXPIRED` - Time expired
- `CANCELLED` - Customer cancelled

### AssignmentStatus
- `PENDING_DRIVER_RESPONSE` - Waiting for drivers
- `DRIVER_ACCEPTED` - Driver accepted
- `DRIVER_DECLINED` - Driver declined
- `TRIP_STARTED` - Trip in progress
- `TRIP_COMPLETED` - Trip completed
- `CANCELLED` - Assignment cancelled

### TripStatus
- `ACCEPTED` - Driver accepted
- `AT_PICKUP` - Reached pickup
- `IN_TRANSIT` - Trip started
- `AT_DROP` - Reached drop
- `COMPLETED` - Delivered
- `CANCELLED` - Cancelled

### DriverStatus
- `ACTIVE` - Available for trips
- `ON_TRIP` - Currently on trip
- `INACTIVE` - Not available
- `SUSPENDED` - Account suspended

### VehicleType
- `MINI` - Mini trucks
- `OPEN` - Open body trucks
- `CONTAINER` - Container trucks
- `TRAILER` - Trailers
- `TANKER` - Tanker trucks
- `TIPPER` - Tipper trucks
- `BULKER` - Bulk carriers
- `LCV` - Light commercial vehicles
- `DUMPER` - Dumper trucks

---

**End of API Documentation**

For implementation support, refer to:
- [Main Overview](BACKEND_API_SPECIFICATION.md)
- [Broadcast APIs](API_1_BROADCAST_ENDPOINTS.md)
- [Assignment APIs](API_2_ASSIGNMENT_ENDPOINTS.md)
- [Notification APIs](API_3_DRIVER_NOTIFICATION_ENDPOINTS.md)
- [GPS Tracking APIs](API_4_GPS_TRACKING_ENDPOINTS.md)
- [Security Guide](API_5_SECURITY_AUTHENTICATION.md)
- [WebSocket Guide](API_6_WEBSOCKET_PUSH_NOTIFICATIONS.md)
