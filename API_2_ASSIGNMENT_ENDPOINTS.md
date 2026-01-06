# 2️⃣ Transporter Assignment APIs

## Overview
These endpoints handle the flow when a transporter responds to a broadcast by selecting trucks and assigning drivers. This is the critical step between broadcast and driver notification.

---

## 2.1 Create Assignment (Transporter responds to broadcast)

**Endpoint**: `POST /assignments`

**Role**: `TRANSPORTER`

**Description**: Transporter responds to broadcast by selecting how many trucks they can provide and assigns drivers to each truck.

### Request Body

```json
{
  "broadcastId": "BC-2026-001-ABC123",
  "trucksTaken": 3,
  "assignments": [
    {
      "vehicleId": "VEH-001-CONT-1234",
      "vehicleNumber": "DL-1A-1234",
      "driverId": "DRV-001",
      "driverName": "Ramesh Singh",
      "driverMobile": "+91-9876543210"
    },
    {
      "vehicleId": "VEH-002-CONT-5678",
      "vehicleNumber": "DL-1A-5678",
      "driverId": "DRV-002",
      "driverName": "Suresh Kumar",
      "driverMobile": "+91-9123456789"
    },
    {
      "vehicleId": "VEH-003-CONT-9012",
      "vehicleNumber": "DL-1A-9012",
      "driverId": "DRV-003",
      "driverName": "Vijay Sharma",
      "driverMobile": "+91-9012345678"
    }
  ],
  "estimatedArrivalTime": "2026-01-05T14:30:00Z",
  "notes": "All drivers are ready and available"
}
```

### Request Parameters

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| broadcastId | String | Yes | The broadcast being responded to |
| trucksTaken | Integer | Yes | Number of trucks (1 to remaining trucks) |
| assignments | Array | Yes | Driver-truck assignments (length must equal trucksTaken) |
| assignments[].vehicleId | String | Yes | Unique vehicle identifier |
| assignments[].vehicleNumber | String | Yes | Vehicle registration number |
| assignments[].driverId | String | Yes | Unique driver identifier |
| assignments[].driverName | String | Yes | Driver's full name |
| assignments[].driverMobile | String | Yes | Driver's mobile number |
| estimatedArrivalTime | DateTime | No | When trucks will reach pickup |
| notes | String | No | Additional information |

### Response (201 Created)

```json
{
  "success": true,
  "data": {
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "broadcastId": "BC-2026-001-ABC123",
    "transporterId": "TRANS-567",
    "transporterName": "Kumar Transport",
    "transporterMobile": "+91-9999888877",
    "trucksTaken": 3,
    "assignments": [
      {
        "driverId": "DRV-001",
        "driverName": "Ramesh Singh",
        "driverMobile": "+91-9876543210",
        "vehicleId": "VEH-001-CONT-1234",
        "vehicleNumber": "DL-1A-1234",
        "status": "PENDING",
        "notificationSent": true,
        "notificationId": "NOTIF-001-ABC"
      },
      {
        "driverId": "DRV-002",
        "driverName": "Suresh Kumar",
        "driverMobile": "+91-9123456789",
        "vehicleId": "VEH-002-CONT-5678",
        "vehicleNumber": "DL-1A-5678",
        "status": "PENDING",
        "notificationSent": true,
        "notificationId": "NOTIF-002-DEF"
      },
      {
        "driverId": "DRV-003",
        "driverName": "Vijay Sharma",
        "driverMobile": "+91-9012345678",
        "vehicleId": "VEH-003-CONT-9012",
        "vehicleNumber": "DL-1A-9012",
        "status": "PENDING",
        "notificationSent": true,
        "notificationId": "NOTIF-003-GHI"
      }
    ],
    "tripDetails": {
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
      "farePerTruck": 2500.00,
      "totalFareForTransporter": 7500.00,
      "goodsType": "Electronics"
    },
    "assignedAt": 1735992900000,
    "estimatedArrivalTime": 1735996200000,
    "status": "PENDING_DRIVER_RESPONSE",
    "broadcastStatus": "PARTIALLY_FILLED",
    "trucksRemainingInBroadcast": 7
  },
  "message": "Assignment created and notifications sent to 3 drivers",
  "timestamp": "2026-01-05T10:35:00Z"
}
```

### Backend Logic

1. **Validate Request**
   - Verify broadcast exists and is ACTIVE/PARTIALLY_FILLED
   - Check trucksTaken <= (totalTrucksNeeded - trucksFilledSoFar)
   - Verify assignments.length === trucksTaken
   - Check all vehicles belong to transporter
   - Verify all drivers are available (not on another trip)

2. **Check Vehicle Availability**
   ```sql
   SELECT vehicle_id FROM vehicles 
   WHERE transporter_id = ? 
   AND vehicle_id IN (?)
   AND status = 'AVAILABLE'
   AND vehicle_type = ? -- matches broadcast requirement
   ```

3. **Check Driver Availability**
   ```sql
   SELECT driver_id FROM drivers
   WHERE transporter_id = ?
   AND driver_id IN (?)
   AND status = 'ACTIVE'
   AND NOT EXISTS (
     SELECT 1 FROM trip_assignments 
     WHERE driver_id = drivers.driver_id 
     AND status IN ('PENDING', 'ACCEPTED', 'IN_PROGRESS')
   )
   ```

4. **Create Assignment Record**
   - Generate unique assignmentId
   - Set status = PENDING_DRIVER_RESPONSE
   - Store all assignment details

5. **Update Broadcast**
   ```sql
   UPDATE broadcasts 
   SET trucks_filled = trucks_filled + ?
   WHERE broadcast_id = ?
   ```
   - If trucks_filled === totalTrucksNeeded: status = FULLY_FILLED
   - Else: status = PARTIALLY_FILLED

6. **Update Vehicle & Driver Status**
   ```sql
   UPDATE vehicles SET status = 'ASSIGNED' WHERE vehicle_id IN (?)
   UPDATE drivers SET status = 'ON_TRIP' WHERE driver_id IN (?)
   ```

7. **Send Driver Notifications** (Critical Step)
   - For each driver in assignments:
     - Create DriverNotification record
     - Send push notification (FCM/APNS)
     - Send WebSocket message if driver online
     - Trigger full-screen alarm in driver app
   - See [API 3 - Driver Notification](#3-driver-notification-apis)

8. **Return Response**
   - Include all assignment details
   - Notification IDs for tracking
   - Updated broadcast status

### Database Schema

```sql
CREATE TABLE trip_assignments (
    assignment_id VARCHAR(50) PRIMARY KEY,
    broadcast_id VARCHAR(50) NOT NULL,
    transporter_id VARCHAR(50) NOT NULL,
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
    response_type VARCHAR(20), -- ACCEPTED, DECLINED
    decline_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (assignment_id) REFERENCES trip_assignments(assignment_id),
    INDEX idx_assignment (assignment_id),
    INDEX idx_driver (driver_id),
    INDEX idx_status (status)
);
```

---

## 2.2 Get Assignment Details

**Endpoint**: `GET /assignments/{assignmentId}`

**Role**: `TRANSPORTER`, `DRIVER`, `CUSTOMER`, `ADMIN`

**Description**: Get detailed information about a specific assignment.

### Request Example

```http
GET /assignments/ASSIGN-2026-001-XYZ
Authorization: Bearer <jwt_token>
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "broadcastId": "BC-2026-001-ABC123",
    "transporterId": "TRANS-567",
    "transporterName": "Kumar Transport",
    "transporterMobile": "+91-9999888877",
    "trucksTaken": 3,
    "assignments": [
      {
        "driverId": "DRV-001",
        "driverName": "Ramesh Singh",
        "driverMobile": "+91-9876543210",
        "vehicleId": "VEH-001-CONT-1234",
        "vehicleNumber": "DL-1A-1234",
        "status": "ACCEPTED",
        "assignedAt": 1735992900000,
        "respondedAt": 1735993020000,
        "responseTime": 120,
        "notificationId": "NOTIF-001-ABC"
      },
      {
        "driverId": "DRV-002",
        "driverName": "Suresh Kumar",
        "driverMobile": "+91-9123456789",
        "vehicleId": "VEH-002-CONT-5678",
        "vehicleNumber": "DL-1A-5678",
        "status": "PENDING",
        "assignedAt": 1735992900000,
        "notificationId": "NOTIF-002-DEF"
      },
      {
        "driverId": "DRV-003",
        "driverName": "Vijay Sharma",
        "driverMobile": "+91-9012345678",
        "vehicleId": "VEH-003-CONT-9012",
        "vehicleNumber": "DL-1A-9012",
        "status": "DECLINED",
        "assignedAt": 1735992900000,
        "respondedAt": 1735993080000,
        "declineReason": "Vehicle has minor issue",
        "notificationId": "NOTIF-003-GHI"
      }
    ],
    "tripDetails": {
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
      "farePerTruck": 2500.00,
      "totalFareForTransporter": 7500.00,
      "goodsType": "Electronics",
      "weight": "5000 kg"
    },
    "assignedAt": 1735992900000,
    "status": "PARTIALLY_ACCEPTED",
    "acceptedCount": 1,
    "pendingCount": 1,
    "declinedCount": 1,
    "timeline": [
      {
        "timestamp": 1735992900000,
        "event": "ASSIGNMENT_CREATED",
        "description": "Transporter assigned 3 trucks and drivers"
      },
      {
        "timestamp": 1735992905000,
        "event": "NOTIFICATIONS_SENT",
        "description": "Push notifications sent to 3 drivers"
      },
      {
        "timestamp": 1735993020000,
        "event": "DRIVER_ACCEPTED",
        "driverId": "DRV-001",
        "description": "Ramesh Singh accepted the trip"
      },
      {
        "timestamp": 1735993080000,
        "event": "DRIVER_DECLINED",
        "driverId": "DRV-003",
        "description": "Vijay Sharma declined (Vehicle has minor issue)"
      }
    ]
  },
  "message": "Assignment details retrieved",
  "timestamp": "2026-01-05T10:38:00Z"
}
```

---

## 2.3 Get Transporter's Active Assignments

**Endpoint**: `GET /assignments/transporter/active`

**Role**: `TRANSPORTER`

**Description**: Get all active assignments for the logged-in transporter.

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| status | String | No | all | Filter: all, pending, accepted, declined |
| page | Integer | No | 1 | Page number |
| limit | Integer | No | 20 | Items per page |

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "assignments": [
      {
        "assignmentId": "ASSIGN-2026-001-XYZ",
        "broadcastId": "BC-2026-001-ABC123",
        "trucksTaken": 3,
        "acceptedCount": 1,
        "pendingCount": 1,
        "declinedCount": 1,
        "status": "PARTIALLY_ACCEPTED",
        "tripSummary": {
          "route": "Connaught Place → Noida",
          "distance": 32.5,
          "farePerTruck": 2500.00,
          "totalFare": 7500.00
        },
        "assignedAt": 1735992900000,
        "needsAction": true
      }
    ],
    "summary": {
      "totalAssignments": 1,
      "totalTrucks": 3,
      "acceptedTrucks": 1,
      "pendingTrucks": 1,
      "declinedTrucks": 1
    }
  },
  "message": "Active assignments retrieved",
  "timestamp": "2026-01-05T10:40:00Z"
}
```

---

## 2.4 Reassign Driver (When driver declines)

**Endpoint**: `POST /assignments/{assignmentId}/reassign`

**Role**: `TRANSPORTER`

**Description**: Reassign a truck to a different driver when the original driver declines.

### Request Body

```json
{
  "originalDriverId": "DRV-003",
  "vehicleId": "VEH-003-CONT-9012",
  "newDriverId": "DRV-005",
  "newDriverName": "Anil Verma",
  "newDriverMobile": "+91-9988776655",
  "notes": "Reassigned to available driver"
}
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "reassignmentId": "REASSIGN-001-ABC",
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "vehicleId": "VEH-003-CONT-9012",
    "vehicleNumber": "DL-1A-9012",
    "previousDriver": {
      "driverId": "DRV-003",
      "driverName": "Vijay Sharma",
      "declinedAt": 1735993080000,
      "declineReason": "Vehicle has minor issue"
    },
    "newDriver": {
      "driverId": "DRV-005",
      "driverName": "Anil Verma",
      "driverMobile": "+91-9988776655",
      "status": "PENDING",
      "notificationSent": true,
      "notificationId": "NOTIF-004-JKL"
    },
    "reassignedAt": 1735993500000,
    "status": "WAITING_FOR_NEW_DRIVER_RESPONSE"
  },
  "message": "Driver reassigned and notification sent to Anil Verma",
  "timestamp": "2026-01-05T10:45:00Z"
}
```

### Backend Logic

1. **Validate Reassignment**
   - Verify original driver has DECLINED status
   - Check new driver is available
   - Verify new driver belongs to same transporter
   - Check vehicle is still assigned to assignment

2. **Create Reassignment Record**
   ```sql
   INSERT INTO trip_reassignments (
     reassignment_id, assignment_id, vehicle_id,
     previous_driver_id, new_driver_id, reassigned_at, status
   ) VALUES (?, ?, ?, ?, ?, ?, 'WAITING_FOR_NEW_DRIVER_RESPONSE')
   ```

3. **Update Driver Assignment**
   ```sql
   UPDATE driver_truck_assignments
   SET driver_id = ?, driver_name = ?, driver_mobile = ?,
       status = 'PENDING', assigned_at = ?, response_type = NULL
   WHERE assignment_id = ? AND vehicle_id = ?
   ```

4. **Send Notification to New Driver**
   - Create new DriverNotification
   - Send push notification with full-screen alarm
   - Update driver status

5. **Notify Previous Driver** (Optional)
   - Send confirmation that they've been unassigned

---

## 2.5 Cancel Assignment

**Endpoint**: `DELETE /assignments/{assignmentId}`

**Role**: `TRANSPORTER`, `ADMIN`

**Description**: Cancel an assignment (only if no drivers have accepted yet).

### Request Body

```json
{
  "reason": "Trucks not available anymore",
  "notifyDrivers": true
}
```

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "assignmentId": "ASSIGN-2026-001-XYZ",
    "previousStatus": "PENDING_DRIVER_RESPONSE",
    "newStatus": "CANCELLED",
    "cancelledAt": 1735994000000,
    "reason": "Trucks not available anymore",
    "trucksReleasedBackToBroadcast": 3,
    "driversNotified": 2
  },
  "message": "Assignment cancelled and drivers notified",
  "timestamp": "2026-01-05T10:50:00Z"
}
```

### Backend Logic

1. **Validate Cancellation**
   - Check if any driver has ACCEPTED (cannot cancel if yes)
   - Only PENDING drivers can be cancelled

2. **Update Assignment Status**
   ```sql
   UPDATE trip_assignments
   SET status = 'CANCELLED', updated_at = ?
   WHERE assignment_id = ?
   ```

3. **Release Vehicles & Drivers**
   ```sql
   UPDATE vehicles SET status = 'AVAILABLE' 
   WHERE vehicle_id IN (SELECT vehicle_id FROM driver_truck_assignments WHERE assignment_id = ?)
   
   UPDATE drivers SET status = 'ACTIVE'
   WHERE driver_id IN (SELECT driver_id FROM driver_truck_assignments WHERE assignment_id = ?)
   ```

4. **Update Broadcast**
   ```sql
   UPDATE broadcasts
   SET trucks_filled = trucks_filled - ?
   WHERE broadcast_id = ?
   ```

5. **Notify Drivers**
   - Send cancellation notification to all PENDING drivers
   - Remove notification from driver's app

---

## 2.6 Assignment Summary for Customer

**Endpoint**: `GET /broadcasts/{broadcastId}/assignments`

**Role**: `CUSTOMER`, `ADMIN`

**Description**: Customer can see all transporters who responded to their broadcast.

### Response (200 OK)

```json
{
  "success": true,
  "data": {
    "broadcastId": "BC-2026-001-ABC123",
    "totalTrucksNeeded": 10,
    "trucksFilledSoFar": 7,
    "trucksRemaining": 3,
    "status": "PARTIALLY_FILLED",
    "assignments": [
      {
        "assignmentId": "ASSIGN-2026-001-XYZ",
        "transporterId": "TRANS-567",
        "transporterName": "Kumar Transport",
        "transporterMobile": "+91-9999888877",
        "transporterRating": 4.5,
        "trucksTaken": 3,
        "acceptedTrucks": 2,
        "pendingTrucks": 1,
        "assignedAt": 1735992900000,
        "status": "PARTIALLY_ACCEPTED",
        "drivers": [
          {
            "driverName": "Ramesh Singh",
            "vehicleNumber": "DL-1A-1234",
            "status": "ACCEPTED"
          },
          {
            "driverName": "Suresh Kumar",
            "vehicleNumber": "DL-1A-5678",
            "status": "ACCEPTED"
          },
          {
            "driverName": "Anil Verma",
            "vehicleNumber": "DL-1A-9012",
            "status": "PENDING"
          }
        ]
      },
      {
        "assignmentId": "ASSIGN-2026-002-ABC",
        "transporterId": "TRANS-890",
        "transporterName": "Fast Logistics",
        "transporterMobile": "+91-8888777766",
        "transporterRating": 4.8,
        "trucksTaken": 4,
        "acceptedTrucks": 4,
        "pendingTrucks": 0,
        "assignedAt": 1735993200000,
        "status": "FULLY_ACCEPTED",
        "drivers": [
          {
            "driverName": "Rahul Sharma",
            "vehicleNumber": "DL-2B-1111",
            "status": "ACCEPTED"
          },
          {
            "driverName": "Amit Patel",
            "vehicleNumber": "DL-2B-2222",
            "status": "ACCEPTED"
          },
          {
            "driverName": "Deepak Singh",
            "vehicleNumber": "DL-2B-3333",
            "status": "ACCEPTED"
          },
          {
            "driverName": "Manoj Kumar",
            "vehicleNumber": "DL-2B-4444",
            "status": "ACCEPTED"
          }
        ]
      }
    ],
    "summary": {
      "totalTransporters": 2,
      "totalDriversAssigned": 7,
      "driversAccepted": 6,
      "driversPending": 1,
      "fulfillmentRate": 70.0
    }
  },
  "message": "Assignment summary retrieved",
  "timestamp": "2026-01-05T10:55:00Z"
}
```

---

## Error Codes

| Code | Message | HTTP Status | Description |
|------|---------|-------------|-------------|
| ASSIGN_001 | Broadcast not available | 404 | Broadcast doesn't exist or expired |
| ASSIGN_002 | Insufficient trucks available | 409 | Requesting more than remaining |
| ASSIGN_003 | Vehicle not available | 409 | Vehicle is already assigned |
| ASSIGN_004 | Driver not available | 409 | Driver is on another trip |
| ASSIGN_005 | Mismatched truck count | 400 | trucksTaken ≠ assignments.length |
| ASSIGN_006 | Vehicle type mismatch | 400 | Vehicle type doesn't match broadcast |
| ASSIGN_007 | Assignment not found | 404 | AssignmentId doesn't exist |
| ASSIGN_008 | Cannot cancel accepted trip | 409 | Driver already accepted |
| ASSIGN_009 | Driver not in assignment | 400 | Driver doesn't belong to assignment |
| ASSIGN_010 | Already reassigned | 409 | Driver already has new assignment |

---

**Next**: [API 3 - Driver Notification Endpoints →](API_3_DRIVER_NOTIFICATION_ENDPOINTS.md)
