# Performance and Scaling Notes

This document outlines performance expectations, optimization requirements, and scaling considerations for the backend.

---

## Table of Contents

1. [Performance Expectations](#performance-expectations)
2. [Response Time Requirements](#response-time-requirements)
3. [Pagination Requirements](#pagination-requirements)
4. [Caching Strategy](#caching-strategy)
5. [Database Optimization](#database-optimization)
6. [Real-Time Updates](#real-time-updates)
7. [GPS Tracking Performance](#gps-tracking-performance)
8. [Idempotency Requirements](#idempotency-requirements)
9. [Scalability Considerations](#scalability-considerations)
10. [Load Testing Targets](#load-testing-targets)

---

## Performance Expectations

### Response Time Targets

| Operation Type | Target | Maximum Acceptable |
|----------------|--------|-------------------|
| Authentication (OTP) | < 2 seconds | 5 seconds |
| Data Retrieval (GET) | < 500ms | 1 second |
| Data Creation (POST) | < 1 second | 2 seconds |
| Data Update (PUT) | < 1 second | 2 seconds |
| GPS Location Update | < 200ms | 500ms |
| Real-time Notification | Immediate | 2 seconds |
| Dashboard Load | < 1 second | 2 seconds |
| List with Pagination | < 500ms | 1 second |

### Why These Targets Matter

**User Experience**:
- Users expect instant feedback
- Slow responses lead to abandonment
- GPS tracking requires near-real-time updates

**Business Impact**:
- Delayed notifications = missed opportunities
- Slow dashboard = poor decision making
- GPS lag = inaccurate tracking

---

## Response Time Requirements

### Critical Paths (Must Be Fast)

#### 1. GPS Location Updates
**Frequency**: Every 10-30 seconds during active trip  
**Target**: < 200ms  
**Volume**: Up to 3 updates/second per active trip

**Optimization Required**:
```typescript
// Fast write to database
app.post('/trips/:tripId/location', async (req, res) => {
  // Validate quickly
  if (!req.body.latitude || !req.body.longitude) {
    return res.status(400).json({ error: 'Invalid location' });
  }
  
  // Write to database asynchronously
  // Don't wait for write to complete
  saveLocationAsync(req.params.tripId, req.body);
  
  // Respond immediately
  res.status(200).json({ success: true });
  
  // Optional: Broadcast to WebSocket clients
  broadcastLocationUpdate(req.params.tripId, req.body);
});

// Async save function
async function saveLocationAsync(tripId: string, location: LocationData) {
  // Use connection pool
  // Batch writes if possible
  await db.query(
    'INSERT INTO trip_locations (trip_id, lat, lng, timestamp) VALUES (?, ?, ?, ?)',
    [tripId, location.latitude, location.longitude, Date.now()]
  );
}
```

#### 2. Push Notifications
**Target**: < 2 seconds from event to device  
**Critical For**: Trip assignments, status updates

**Implementation**:
```typescript
// Send notification immediately, don't wait
async function notifyDriver(driverId: string, notification: Notification) {
  // Send FCM notification asynchronously
  fcm.send({
    to: driver.fcmToken,
    notification: {
      title: notification.title,
      body: notification.body,
      sound: 'default'
    },
    data: notification.data
  }).catch(err => {
    // Log error but don't fail request
    logger.error('FCM send failed', err);
  });
  
  // Store in database asynchronously
  saveNotificationAsync(driverId, notification);
}
```

#### 3. Dashboard Load
**Target**: < 1 second  
**Contains**: Aggregated statistics, recent trips

**Optimization**:
```typescript
// Use materialized view or cached aggregates
app.get('/transporter/dashboard', async (req, res) => {
  const transporterId = req.query.transporterId;
  
  // Try cache first
  const cached = await redis.get(`dashboard:${transporterId}`);
  if (cached) {
    return res.json(JSON.parse(cached));
  }
  
  // Fetch from database (optimized query)
  const dashboard = await getDashboardData(transporterId);
  
  // Cache for 1 minute
  await redis.setex(`dashboard:${transporterId}`, 60, JSON.stringify(dashboard));
  
  res.json(dashboard);
});
```

---

## Pagination Requirements

### Standard Pagination

**Default Page Size**: 20 items  
**Maximum Page Size**: 100 items  
**Minimum Page Size**: 10 items

**Request Format**:
```
GET /vehicles?transporterId=123&page=1&limit=20&status=AVAILABLE
```

**Response Format**:
```json
{
  "success": true,
  "vehicles": [...],
  "total": 150,
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 150,
    "pages": 8,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

### Cursor-Based Pagination (Recommended for GPS History)

For large datasets like GPS tracking history, use cursor-based pagination:

**Request**:
```
GET /trips/123/locations?limit=100&cursor=eyJ0aW1lc3RhbXAiOjE3MDQ2MTQ0MDAwMDB9
```

**Response**:
```json
{
  "success": true,
  "locations": [...],
  "cursor": {
    "next": "eyJ0aW1lc3RhbXAiOjE3MDQ2MTU0MDAwMDB9",
    "hasMore": true
  }
}
```

### Optimization Tips

1. **Index pagination fields**:
   ```sql
   CREATE INDEX idx_vehicles_transporter_created 
   ON vehicles(transporter_id, created_at DESC);
   ```

2. **Use LIMIT/OFFSET efficiently**:
   ```sql
   -- Good for small offsets
   SELECT * FROM vehicles 
   WHERE transporter_id = ? 
   ORDER BY created_at DESC 
   LIMIT 20 OFFSET 0;
   
   -- Better for large offsets (cursor-based)
   SELECT * FROM vehicles 
   WHERE transporter_id = ? 
   AND created_at < ?
   ORDER BY created_at DESC 
   LIMIT 20;
   ```

3. **Count total rows efficiently**:
   ```sql
   -- Use approximate count for large tables
   SELECT reltuples::bigint AS estimate 
   FROM pg_class 
   WHERE relname = 'vehicles';
   ```

---

## Caching Strategy

### What to Cache

| Data Type | Cache Duration | Invalidation Trigger |
|-----------|----------------|---------------------|
| Dashboard statistics | 1 minute | On data change |
| Vehicle list | 5 minutes | On vehicle add/update |
| Driver list | 5 minutes | On driver add/update |
| User profile | 1 hour | On profile update |
| Vehicle catalog | 24 hours | Never (static) |
| OTP | 5 minutes | On verification |
| Rate limit counters | Varies | TTL expiry |

### Cache Implementation

**Redis Cache Pattern**:
```typescript
async function getCachedData<T>(
  key: string,
  ttl: number,
  fetchFunction: () => Promise<T>
): Promise<T> {
  // Try cache first
  const cached = await redis.get(key);
  if (cached) {
    return JSON.parse(cached);
  }
  
  // Fetch from source
  const data = await fetchFunction();
  
  // Store in cache
  await redis.setex(key, ttl, JSON.stringify(data));
  
  return data;
}

// Usage
const vehicles = await getCachedData(
  `vehicles:${transporterId}`,
  300, // 5 minutes
  () => db.query('SELECT * FROM vehicles WHERE transporter_id = ?', [transporterId])
);
```

### Cache Invalidation

**Strategy**: Cache-aside with explicit invalidation

```typescript
// When vehicle is updated
async function updateVehicle(vehicleId: string, updates: VehicleUpdate) {
  // Update database
  await db.query('UPDATE vehicles SET ... WHERE id = ?', [vehicleId]);
  
  // Invalidate cache
  const vehicle = await db.query('SELECT * FROM vehicles WHERE id = ?', [vehicleId]);
  await redis.del(`vehicles:${vehicle.transporterId}`);
  await redis.del(`vehicle:${vehicleId}`);
}
```

### Cache-Control Headers

```typescript
app.get('/vehicles', (req, res) => {
  // Set cache headers for CDN/browser caching
  res.set({
    'Cache-Control': 'private, max-age=300', // 5 minutes
    'ETag': generateETag(data),
    'Last-Modified': new Date(lastModified).toUTCString()
  });
  
  res.json(data);
});
```

---

## Database Optimization

### Required Indexes

**Users Table**:
```sql
CREATE INDEX idx_users_phone ON users(mobile_number);
CREATE INDEX idx_users_roles ON users USING GIN(roles);
```

**Vehicles Table**:
```sql
CREATE INDEX idx_vehicles_transporter ON vehicles(transporter_id);
CREATE INDEX idx_vehicles_status ON vehicles(status);
CREATE INDEX idx_vehicles_number ON vehicles(vehicle_number);
CREATE INDEX idx_vehicles_driver ON vehicles(assigned_driver_id);
```

**Drivers Table**:
```sql
CREATE INDEX idx_drivers_transporter ON drivers(transporter_id);
CREATE INDEX idx_drivers_phone ON drivers(mobile_number);
CREATE INDEX idx_drivers_status ON drivers(status);
```

**Trips Table**:
```sql
CREATE INDEX idx_trips_transporter ON trips(transporter_id);
CREATE INDEX idx_trips_driver ON trips(driver_id);
CREATE INDEX idx_trips_vehicle ON trips(vehicle_id);
CREATE INDEX idx_trips_status ON trips(status);
CREATE INDEX idx_trips_created ON trips(created_at DESC);
```

**GPS Locations Table**:
```sql
CREATE INDEX idx_locations_trip_time ON trip_locations(trip_id, timestamp DESC);
-- Consider partitioning by date for large datasets
```

### Query Optimization

**Use SELECT only needed columns**:
```sql
-- ❌ Bad
SELECT * FROM vehicles WHERE transporter_id = ?;

-- ✅ Good
SELECT id, vehicle_number, status, assigned_driver_id 
FROM vehicles 
WHERE transporter_id = ?;
```

**Use JOINs efficiently**:
```sql
-- ✅ Good - Single query with JOIN
SELECT 
  v.id, v.vehicle_number, v.status,
  d.name AS driver_name
FROM vehicles v
LEFT JOIN drivers d ON v.assigned_driver_id = d.id
WHERE v.transporter_id = ?;
```

**Avoid N+1 queries**:
```typescript
// ❌ Bad - N+1 queries
const vehicles = await db.query('SELECT * FROM vehicles WHERE transporter_id = ?', [id]);
for (const vehicle of vehicles) {
  vehicle.driver = await db.query('SELECT * FROM drivers WHERE id = ?', [vehicle.driver_id]);
}

// ✅ Good - Single query with JOIN
const vehicles = await db.query(`
  SELECT 
    v.*, 
    d.name as driver_name,
    d.phone as driver_phone
  FROM vehicles v
  LEFT JOIN drivers d ON v.assigned_driver_id = d.id
  WHERE v.transporter_id = ?
`, [id]);
```

### Connection Pooling

```typescript
// Configure connection pool
const pool = mysql.createPool({
  host: 'localhost',
  user: 'user',
  password: 'password',
  database: 'weelo',
  connectionLimit: 100,      // Max connections
  queueLimit: 0,             // Unlimited queue
  waitForConnections: true,
  connectTimeout: 10000,     // 10 seconds
  acquireTimeout: 10000
});
```

---

## Real-Time Updates

### WebSocket vs Polling

**Use WebSocket for**:
- Live GPS tracking
- Broadcast status updates
- Driver response notifications
- Real-time chat (if implemented)

**Use Polling for**:
- Dashboard statistics (every 30-60 seconds)
- Notification list (every 60 seconds)
- Non-critical updates

### WebSocket Implementation

**Server**:
```typescript
import WebSocket from 'ws';

const wss = new WebSocket.Server({ port: 8080 });

// Store active connections
const connections = new Map<string, Set<WebSocket>>();

wss.on('connection', (ws, req) => {
  // Authenticate
  const userId = authenticateWebSocket(req);
  
  // Store connection
  if (!connections.has(userId)) {
    connections.set(userId, new Set());
  }
  connections.get(userId).add(ws);
  
  // Handle disconnect
  ws.on('close', () => {
    connections.get(userId)?.delete(ws);
  });
});

// Broadcast location update
function broadcastLocationUpdate(tripId: string, location: Location) {
  const trip = getTripById(tripId);
  const transporterId = trip.transporterId;
  
  // Send to all transporter's connections
  const transporterConnections = connections.get(transporterId);
  if (transporterConnections) {
    const message = JSON.stringify({
      type: 'location_update',
      tripId: tripId,
      location: location
    });
    
    transporterConnections.forEach(ws => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(message);
      }
    });
  }
}
```

**Client (App already implements)**:
```kotlin
class LocationWebSocket(private val tripId: String) {
    private var webSocket: WebSocket? = null
    
    fun connect() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("wss://api.weelo.in/ws")
            .addHeader("Authorization", "Bearer $token")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val update = Json.decodeFromString<LocationUpdate>(text)
                if (update.tripId == tripId) {
                    updateMapMarker(update.location)
                }
            }
        })
    }
}
```

---

## GPS Tracking Performance

### High-Volume GPS Data

**Expected Volume**:
- 100 active trips simultaneously
- 3 location updates per trip per minute
- **Total**: 300 writes/minute = 5 writes/second

**Optimization Strategies**:

1. **Batch Inserts**:
```typescript
// Collect locations for 5 seconds, then batch insert
const locationBuffer: LocationUpdate[] = [];

function bufferLocation(location: LocationUpdate) {
  locationBuffer.push(location);
}

setInterval(async () => {
  if (locationBuffer.length === 0) return;
  
  const batch = [...locationBuffer];
  locationBuffer.length = 0;
  
  // Batch insert
  await db.query(
    'INSERT INTO trip_locations (trip_id, lat, lng, timestamp) VALUES ?',
    [batch.map(loc => [loc.tripId, loc.latitude, loc.longitude, loc.timestamp])]
  );
}, 5000); // Every 5 seconds
```

2. **Time-Series Database** (Optional):
```typescript
// Use InfluxDB or TimescaleDB for GPS data
const influx = new InfluxDB({
  host: 'localhost',
  database: 'gps_tracking'
});

function writeLocation(location: LocationUpdate) {
  influx.writePoints([{
    measurement: 'trip_locations',
    tags: {
      trip_id: location.tripId,
      driver_id: location.driverId
    },
    fields: {
      latitude: location.latitude,
      longitude: location.longitude,
      speed: location.speed
    },
    timestamp: location.timestamp
  }]);
}
```

3. **Data Retention**:
```sql
-- Archive old GPS data after 90 days
CREATE EVENT archive_old_locations
ON SCHEDULE EVERY 1 DAY
DO
  DELETE FROM trip_locations 
  WHERE timestamp < UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 90 DAY)) * 1000;
```

---

## Idempotency Requirements

### What Needs Idempotency

Operations that can be retried must be idempotent:

1. **Trip Start/Complete**: Use idempotency key
2. **Payment Processing**: Critical - must be idempotent
3. **Notification Sending**: Prevent duplicate notifications
4. **Driver Assignment**: Prevent double assignment

### Implementation

**Using Idempotency Keys**:
```typescript
app.post('/trips/:tripId/start', async (req, res) => {
  const idempotencyKey = req.headers['idempotency-key'];
  
  if (!idempotencyKey) {
    return res.status(400).json({ error: 'Idempotency key required' });
  }
  
  // Check if already processed
  const existing = await redis.get(`idempotency:${idempotencyKey}`);
  if (existing) {
    // Return cached response
    return res.json(JSON.parse(existing));
  }
  
  // Process request
  const result = await startTrip(req.params.tripId, req.body);
  
  // Cache result for 24 hours
  await redis.setex(`idempotency:${idempotencyKey}`, 86400, JSON.stringify(result));
  
  res.json(result);
});
```

**App generates idempotency key**:
```kotlin
fun generateIdempotencyKey(action: String, entityId: String): String {
    return "${action}_${entityId}_${System.currentTimeMillis()}"
}

// Usage
val idempotencyKey = generateIdempotencyKey("start_trip", tripId)
api.startTrip(tripId, idempotencyKey)
```

---

## Scalability Considerations

### Horizontal Scaling

**Stateless API Servers**:
- No session state on server
- All state in Redis/Database
- Use load balancer (AWS ELB, Nginx)

**Database Scaling**:
- Read replicas for GET requests
- Write to primary, read from replicas
- Connection pooling

**Caching Layer**:
- Redis cluster for distributed cache
- Cache-aside pattern

### Load Balancing

```nginx
upstream api_servers {
    least_conn;  # Use least connections algorithm
    server api1.weelo.in:3000;
    server api2.weelo.in:3000;
    server api3.weelo.in:3000;
    
    keepalive 32;
}

server {
    listen 443 ssl;
    server_name api.weelo.in;
    
    location / {
        proxy_pass http://api_servers;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
    }
}
```

### Database Sharding (Future)

For very large scale (10,000+ transporters):

```typescript
// Shard by transporter_id
function getShardForTransporter(transporterId: string): number {
  return Math.abs(hashCode(transporterId)) % NUM_SHARDS;
}

function getDatabaseConnection(transporterId: string): DatabaseConnection {
  const shard = getShardForTransporter(transporterId);
  return dbConnections[shard];
}
```

---

## Load Testing Targets

### Expected Load

**Launch (Month 1)**:
- 100 transporters
- 500 drivers
- 1,000 vehicles
- 50 concurrent active trips

**Year 1 Target**:
- 1,000 transporters
- 5,000 drivers
- 10,000 vehicles
- 500 concurrent active trips

### Load Testing Scenarios

**Scenario 1: Dashboard Load**
- Concurrent users: 100
- Request: GET /transporter/dashboard
- Target: < 1 second response time
- Success rate: > 99%

**Scenario 2: GPS Tracking**
- Concurrent trips: 500
- Updates: 3 per minute per trip
- Total throughput: 25 writes/second
- Target: < 200ms per write

**Scenario 3: Authentication Peak**
- Concurrent logins: 50
- Request: POST /auth/send-otp, POST /auth/verify-otp
- Target: < 2 seconds per OTP send
- Success rate: > 99.5%

### Load Testing Tools

Use **Apache JMeter**, **k6**, or **Artillery**:

```javascript
// k6 load test example
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  stages: [
    { duration: '2m', target: 100 },  // Ramp up to 100 users
    { duration: '5m', target: 100 },  // Stay at 100 users
    { duration: '2m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests < 500ms
  },
};

export default function () {
  const res = http.get('https://api.weelo.in/v1/transporter/dashboard', {
    headers: { 'Authorization': `Bearer ${__ENV.TOKEN}` },
  });
  
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  sleep(1);
}
```

---

## Monitoring and Alerts

### Key Metrics to Monitor

1. **Response Times**:
   - p50, p95, p99 response times per endpoint
   - Alert if p95 > threshold

2. **Error Rates**:
   - 4xx errors (client errors)
   - 5xx errors (server errors)
   - Alert if > 1% error rate

3. **Throughput**:
   - Requests per second
   - Active connections

4. **Database**:
   - Connection pool usage
   - Slow query log
   - Replication lag

5. **Cache**:
   - Hit rate
   - Memory usage

### Alerting Rules

```yaml
# Prometheus alert example
groups:
  - name: api_alerts
    rules:
      - alert: HighResponseTime
        expr: http_request_duration_seconds{quantile="0.95"} > 1
        for: 5m
        annotations:
          summary: "API response time is high"
          
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.01
        for: 2m
        annotations:
          summary: "High error rate detected"
```

---

## Summary: Performance Checklist

**Response Times**:
- ✅ GPS updates < 200ms
- ✅ Dashboard load < 1 second
- ✅ List queries < 500ms

**Optimization**:
- ✅ Pagination on all lists
- ✅ Caching for frequently accessed data
- ✅ Database indexes on all query fields
- ✅ Connection pooling

**Scalability**:
- ✅ Stateless API design
- ✅ Horizontal scaling support
- ✅ Load balancing ready

**Reliability**:
- ✅ Idempotency for critical operations
- ✅ Retry logic with backoff
- ✅ Monitoring and alerting

---

**Next**: See `08_Environment_and_Configuration.md` for deployment setup.
