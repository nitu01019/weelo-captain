# 1️⃣ Broadcast Management APIs

## Overview
These endpoints manage customer booking broadcasts that are sent to transporters. When a customer needs multiple trucks, a broadcast is created and sent to all eligible transporters in the area.

---

## 1.1 Create Broadcast (Customer creates booking)

**Endpoint**: `POST /broadcasts`

**Role**: `CUSTOMER`

**Description**: Customer creates a new broadcast request for trucks. This automatically notifies all eligible transporters via WebSocket/Push notification.

### Request Body

```json
{
  "pickupLocation": {
    "latitude": 28.7041,
    "longitude": 77.1025,
    "address": "Connaught Place, New Delhi",
    "city": "New Delhi",
    "state": "Delhi",
    "pincode": "110001"
  },
  "dropLocation": {
    "latitude": 28.5355,
    "longitude": 77.3910,
    "address": "Sector 18, Noida",
    "city": "Noida",
    "state": "Uttar Pradesh",
    "pincode": "201301"
  },
  "totalTrucksNeeded": 10,
  "vehicleType": "CONTAINER",
  "goodsType": "Electronics",
  "weight": "5000 kg",
  "scheduledPickupTime": "2026-01-05T14:00:00Z",
  "isUrgent": false,
  "notes": "Fragile items, handle with care",
  "customerPreferences": {
    "requiresInsurance": true,
    "requiresHelpers": false
  }
}
```

### Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| pickupLocation | Location | Yes | Pickup location details |
| dropLocation | Location | Yes | Drop location details |
| totalTrucksNeeded | Integer | Yes | Total number of trucks (min: 1, max: 100) |
| vehicleType | String | Yes | Type: MINI, OPEN, CONTAINER, TRAILER, TANKER, TIPPER, BULKER, LCV, DUMPER |
| goodsType | String | Yes | Type of goods being transported |
| weight | String | No | Weight of goods (format: "5000 kg") |
| scheduledPickupTime | DateTime | No | When to pickup (default: immediate) |
| isUrgent | Boolean | No | Priority flag (default: false) |
| notes | String | No | Special instructions (max: 500 chars) |
| customerPreferences | Object | No | Additional preferences |

### Response (201 Created)

```json
{
  "success": true,
  "data": {
    "broadcastId": "BC-2026-001-ABC123",
    "customerId": "CUST-123456",
    "customerName": "Rajesh Kumar",
    "customerMobile": "+91-9876543210",
    "pickupLocation": {
      "latitude": 28.7041,
      "longitude": 77.1025,
      "address": "Connaught Place, New Delhi",
      "city": "New Delhi",
      "state": "Delhi",
      "pincode": "110001"
    },
    "dropLocation": {
      "latitude": 28.5355,
      "longitude": 77.3910,
      "address": "Sector 18, Noida",
      "city": "Noida",
      "state": "Uttar Pradesh",
      "pincode": "201301"
    },
    "distance": 32.5,
    "estimatedDuration": 45,
    "totalTrucksNeeded": 10,
    "trucksFilledSoFar": 0,
    "vehicleType": "CONTAINER",
    "goodsType": "Electronics",
    "weight": "5000 kg",
    "farePerTruck": 2500.00,
    "totalFare": 25000.00,
    "status": "ACTIVE",
    "broadcastTime": 1735992600000,
    "expiryTime": 1735996200000,
    "isUrgent": false,
    "notes": "Fragile items, handle with care",
    "transportersNotified": 45
  },
  "message": "Broadcast created and sent to 45 transporters",
  "timestamp": "2026-01-05T10:30:00Z"
}
```

### Backend Logic

1. **Validate Request**
   - Check all required fields
   - Validate location coordinates
   - Verify truck count (1-100)

2. **Calculate Fare**
   - Use your pricing algorithm
   - Consider: distance, vehicle type, urgency, demand
   - Set farePerTruck and totalFare

3. **Find Eligible Transporters**
   - Query transporters within 50km of pickup
   - Filter by vehicle type availability
   - Check transporter ratings/status

4. **Create Broadcast Record**
   - Generate unique broadcastId
   - Set status = ACTIVE
   - Set expiryTime (default: 60 minutes)

5. **Send Notifications**
   - Push notification to all eligible transporters
   - WebSocket message to online transporters
   - SMS notification (optional)

6. **Return Response**
   - Include broadcastId for tracking
   - Return calculated fare
   - Number of transporters notified

### Database Schema (Suggested)

```sql
CREATE TABLE broadcasts (
    broadcast_id VARCHAR(50) PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    pickup_lat DECIMAL(10,8) NOT NULL,
    pickup_lng DECIMAL(11,8) NOT NULL,
    pickup_address TEXT NOT NULL,
    drop_lat DECIMAL(10,8) NOT NULL,
    drop_lng DECIMAL(11,8) NOT NULL,
    drop_address TEXT NOT NULL,
    distance DECIMAL(8,2) NOT NULL,
    estimated_duration INT NOT NULL,
    total_trucks_needed INT NOT NULL,
    trucks_filled INT DEFAULT 0,
    vehicle_type VARCHAR(20) NOT NULL,
    goods_type VARCHAR(100) NOT NULL,
    weight VARCHAR(50),
    fare_per_truck DECIMAL(10,2) NOT NULL,
    total_fare DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    broadcast_time BIGINT NOT NULL,
    expiry_time BIGINT,
    is_urgent BOOLEAN DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_broadcast_time (broadcast_time),
    INDEX idx_customer (customer_id)
);
```

---

## 1.2 Get Active Broadcasts (Transporter views available broadcasts)

**Endpoint**: `GET /broadcasts/active`

**Role**: `TRANSPORTER`

**Description**: Transporter fetches all active broadcasts they can respond to.

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| vehicleType | String | No | all | Filter by vehicle type |
| maxDistance | Integer | No | 50 | Max distance from transporter (km) |
| page | Integer | No | 1 | Page number |
| limit | Integer | No | 20 | Items per page |
| sortBy | String | No | urgent | Sort: urgent, fare, distance, time |

### Request Example

```http
GET /broadcasts/active?vehicleType=CONTAINER&maxDistance=30&sortBy=urgent
Authorization: Bearer <transporter_jwt_token>
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "broadcasts": [
      {
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
        "totalTrucksNeeded": 10,
        "trucksFilledSoFar": 3,
        "trucksRemaining": 7,
        "vehicleType": "CONTAINER",
        "goodsType": "Electronics",
        "weight": "5000 kg",
        "farePerTruck": 2500.00,
        "totalFare": 25000.00,
        "status": "PARTIALLY_FILLED",
        "broadcastTime": 1735992600000,
        "expiryTime": 1735996200000,
        "timeRemaining": 3600,
        "isUrgent": true,
        "notes": "Fragile items, handle with care",
        "distanceFromTransporter": 12.3
      },
      {
        "broadcastId": "BC-2026-001-XYZ789",
        "customerId": "CUST-789012",
        "customerName": "Priya Sharma",
        "customerMobile": "+91-9123456780",
        "pickupLocation": {
          "latitude": 28.6139,
          "longitude": 77.2090,
          "address": "India Gate, New Delhi",
          "city": "New Delhi"
        },
        "dropLocation": {
          "latitude": 28.4595,
          "longitude": 77.0266,
          "address": "Gurgaon Cyber City",
          "city": "Gurgaon"
        },
        "distance": 25.8,
        "estimatedDuration": 35,
        "totalTrucksNeeded": 5,
        "trucksFilledSoFar": 0,
        "trucksRemaining": 5,
        "vehicleType": "CONTAINER",
        "goodsType": "Furniture",
        "weight": "3000 kg",
        "farePerTruck": 2000.00,
        "totalFare": 10000.00,
        "status": "ACTIVE",
        "broadcastTime": 1735993200000,
        "expiryTime": 1735996800000,
        "timeRemaining": 4200,
        "isUrgent": false,
        "notes": null,
        "distanceFromTransporter": 18.7
      }
    ],
    "pagination": {
      "currentPage": 1,
      "totalPages": 3,
      "totalItems": 45,
      "itemsPerPage": 20
    }
  },
  "message": "Active broadcasts retrieved",
  "timestamp": "2026-01-05T10:30:00Z"
}
```

### Backend Logic

1. **Get Transporter Location**
   - Fetch transporter's current location from database
   - Or use last known location

2. **Query Active Broadcasts**
   - Status = ACTIVE or PARTIALLY_FILLED
   - expiryTime > current time
   - Within maxDistance radius

3. **Calculate Distance**
   - Calculate distance from transporter to pickup location
   - Use Haversine formula or Google Maps API

4. **Filter Results**
   - Apply vehicleType filter if specified
   - Check transporter has available vehicles

5. **Sort Results**
   - urgent: isUrgent=true first, then by time
   - fare: highest fare first
   - distance: nearest first
   - time: newest first

6. **Add Computed Fields**
   - trucksRemaining = totalTrucksNeeded - trucksFilledSoFar
   - timeRemaining = expiryTime - currentTime (seconds)
   - distanceFromTransporter = calculated distance

---

## 1.3 Get Broadcast Details

**Endpoint**: `GET /broadcasts/{broadcastId}`

**Role**: `TRANSPORTER`, `CUSTOMER`, `ADMIN`

**Description**: Get detailed information about a specific broadcast.

### Request Example

```http
GET /broadcasts/BC-2026-001-ABC123
Authorization: Bearer <jwt_token>
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "broadcastId": "BC-2026-001-ABC123",
    "customerId": "CUST-123456",
    "customerName": "Rajesh Kumar",
    "customerMobile": "+91-9876543210",
    "pickupLocation": {
      "latitude": 28.7041,
      "longitude": 77.1025,
      "address": "Connaught Place, New Delhi",
      "city": "New Delhi",
      "state": "Delhi",
      "pincode": "110001"
    },
    "dropLocation": {
      "latitude": 28.5355,
      "longitude": 77.3910,
      "address": "Sector 18, Noida",
      "city": "Noida",
      "state": "Uttar Pradesh",
      "pincode": "201301"
    },
    "distance": 32.5,
    "estimatedDuration": 45,
    "totalTrucksNeeded": 10,
    "trucksFilledSoFar": 3,
    "vehicleType": "CONTAINER",
    "goodsType": "Electronics",
    "weight": "5000 kg",
    "farePerTruck": 2500.00,
    "totalFare": 25000.00,
    "status": "PARTIALLY_FILLED",
    "broadcastTime": 1735992600000,
    "expiryTime": 1735996200000,
    "isUrgent": true,
    "notes": "Fragile items, handle with care",
    "assignments": [
      {
        "assignmentId": "ASSIGN-001",
        "transporterId": "TRANS-567",
        "transporterName": "Kumar Transport",
        "trucksTaken": 2,
        "assignedAt": 1735992900000,
        "status": "DRIVER_ACCEPTED"
      },
      {
        "assignmentId": "ASSIGN-002",
        "transporterId": "TRANS-890",
        "transporterName": "Fast Logistics",
        "trucksTaken": 1,
        "assignedAt": 1735993200000,
        "status": "PENDING_DRIVER_RESPONSE"
      }
    ],
    "timeline": [
      {
        "timestamp": 1735992600000,
        "event": "BROADCAST_CREATED",
        "description": "Broadcast created and sent to 45 transporters"
      },
      {
        "timestamp": 1735992900000,
        "event": "TRANSPORTER_RESPONDED",
        "description": "Kumar Transport assigned 2 trucks"
      },
      {
        "timestamp": 1735993200000,
        "event": "TRANSPORTER_RESPONDED",
        "description": "Fast Logistics assigned 1 truck"
      }
    ]
  },
  "message": "Broadcast details retrieved",
  "timestamp": "2026-01-05T10:30:00Z"
}
```

---

## 1.4 Update Broadcast Status

**Endpoint**: `PATCH /broadcasts/{broadcastId}/status`

**Role**: `CUSTOMER`, `ADMIN`

**Description**: Update broadcast status (cancel, extend, etc.)

### Request Body

```json
{
  "status": "CANCELLED",
  "reason": "Customer changed plans",
  "notifyTransporters": true
}
```

### Status Transitions

| From | To | Who Can Update |
|------|----|--------------------|
| ACTIVE | CANCELLED | CUSTOMER, ADMIN |
| ACTIVE | PARTIALLY_FILLED | SYSTEM (auto) |
| PARTIALLY_FILLED | FULLY_FILLED | SYSTEM (auto) |
| PARTIALLY_FILLED | CANCELLED | CUSTOMER, ADMIN |
| ACTIVE/PARTIALLY_FILLED | EXPIRED | SYSTEM (auto) |

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "broadcastId": "BC-2026-001-ABC123",
    "previousStatus": "ACTIVE",
    "newStatus": "CANCELLED",
    "updatedAt": 1735993500000,
    "reason": "Customer changed plans",
    "affectedAssignments": 2,
    "transportersNotified": 2
  },
  "message": "Broadcast cancelled and transporters notified",
  "timestamp": "2026-01-05T10:35:00Z"
}
```

### Backend Logic

1. **Validate Status Transition**
   - Check if transition is allowed
   - Verify user has permission

2. **Update Database**
   - Update broadcast status
   - Set updated_at timestamp

3. **Handle Side Effects**
   - If CANCELLED: Notify all assigned transporters
   - If CANCELLED: Update assignment statuses
   - Refund customer if applicable

4. **Send Notifications**
   - Push notification to affected transporters
   - SMS to customer confirming cancellation

---

## 1.5 Broadcast Statistics (For Admin Dashboard)

**Endpoint**: `GET /broadcasts/statistics`

**Role**: `ADMIN`

**Description**: Get aggregate statistics for broadcasts.

### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| startDate | DateTime | No | Start date filter |
| endDate | DateTime | No | End date filter |
| customerId | String | No | Filter by customer |

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "totalBroadcasts": 1250,
    "activeBroadcasts": 45,
    "fullyFilledBroadcasts": 980,
    "partiallyFilledBroadcasts": 125,
    "expiredBroadcasts": 78,
    "cancelledBroadcasts": 22,
    "avgResponseTime": 320,
    "avgFulfillmentRate": 92.5,
    "totalTrucksRequested": 12500,
    "totalTrucksFulfilled": 11563,
    "byVehicleType": {
      "CONTAINER": 450,
      "OPEN": 320,
      "TRAILER": 280,
      "MINI": 200
    },
    "byStatus": {
      "ACTIVE": 45,
      "PARTIALLY_FILLED": 125,
      "FULLY_FILLED": 980,
      "EXPIRED": 78,
      "CANCELLED": 22
    }
  },
  "message": "Statistics retrieved",
  "timestamp": "2026-01-05T10:30:00Z"
}
```

---

## Error Codes

| Code | Message | HTTP Status | Description |
|------|---------|-------------|-------------|
| BROADCAST_001 | Invalid location coordinates | 400 | Lat/lng out of range |
| BROADCAST_002 | Truck count out of range | 400 | Must be 1-100 |
| BROADCAST_003 | Invalid vehicle type | 400 | Unknown vehicle type |
| BROADCAST_004 | Broadcast not found | 404 | BroadcastId doesn't exist |
| BROADCAST_005 | Broadcast expired | 410 | Broadcast has expired |
| BROADCAST_006 | Insufficient balance | 402 | Customer needs to add funds |
| BROADCAST_007 | Invalid status transition | 409 | Cannot change status |
| BROADCAST_008 | Already fully filled | 409 | All trucks assigned |

---

**Next**: [API 2 - Transporter Assignment Endpoints →](API_2_ASSIGNMENT_ENDPOINTS.md)
