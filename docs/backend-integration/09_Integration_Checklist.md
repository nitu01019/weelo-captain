# Backend Integration Checklist

This is a step-by-step guide for backend developers to implement and integrate the Weelo Captain API.

---

## Table of Contents

1. [Phase 1: Setup and Foundation](#phase-1-setup-and-foundation)
2. [Phase 2: Authentication Module](#phase-2-authentication-module)
3. [Phase 3: Core Modules](#phase-3-core-modules)
4. [Phase 4: Real-Time Features](#phase-4-real-time-features)
5. [Phase 5: Testing and Optimization](#phase-5-testing-and-optimization)
6. [Phase 6: Deployment](#phase-6-deployment)
7. [Verification Steps](#verification-steps)
8. [Common Issues and Solutions](#common-issues-and-solutions)

---

## Phase 1: Setup and Foundation

### 1.1 Environment Setup

- [ ] **Install Required Software**
  - Node.js 18+ / Python 3.9+ / Java 11+ (choose your stack)
  - MySQL 8.0+ or PostgreSQL 13+
  - Redis 6.0+
  - Git

- [ ] **Create Project Structure**
  ```
  weelo-backend/
  ├── src/
  │   ├── controllers/
  │   ├── services/
  │   ├── models/
  │   ├── middleware/
  │   ├── routes/
  │   └── utils/
  ├── config/
  ├── migrations/
  ├── tests/
  └── docs/
  ```

- [ ] **Setup Environment Variables**
  - Create `.env.development`, `.env.staging`, `.env.production`
  - Add all required variables from `08_Environment_and_Configuration.md`
  - Use secrets manager for production

- [ ] **Initialize Version Control**
  ```bash
  git init
  git remote add origin <repository-url>
  ```

### 1.2 Database Setup

- [ ] **Create Database**
  ```sql
  CREATE DATABASE weelo_production CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```

- [ ] **Create Database Schema**
  ```sql
  -- Users table
  CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    mobile_number VARCHAR(10) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    roles JSON NOT NULL,
    profile_image_url TEXT,
    fcm_token TEXT,
    device_id VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    created_at BIGINT NOT NULL,
    updated_at BIGINT
  );
  
  -- Vehicles table
  CREATE TABLE vehicles (
    id VARCHAR(36) PRIMARY KEY,
    transporter_id VARCHAR(36) NOT NULL,
    category_id VARCHAR(50) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    subtype_id VARCHAR(50) NOT NULL,
    subtype_name VARCHAR(100) NOT NULL,
    capacity_tons DECIMAL(10,2) NOT NULL,
    vehicle_number VARCHAR(20) UNIQUE NOT NULL,
    model VARCHAR(100),
    year INT,
    assigned_driver_id VARCHAR(36),
    status ENUM('AVAILABLE', 'IN_TRANSIT', 'MAINTENANCE', 'INACTIVE') DEFAULT 'AVAILABLE',
    last_service_date BIGINT,
    insurance_expiry_date BIGINT,
    registration_expiry_date BIGINT,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (transporter_id) REFERENCES users(id),
    FOREIGN KEY (assigned_driver_id) REFERENCES drivers(id)
  );
  
  -- Drivers table
  CREATE TABLE drivers (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    mobile_number VARCHAR(10) UNIQUE NOT NULL,
    license_number VARCHAR(20) UNIQUE NOT NULL,
    transporter_id VARCHAR(36) NOT NULL,
    assigned_vehicle_id VARCHAR(36),
    is_available BOOLEAN DEFAULT true,
    rating DECIMAL(3,2) DEFAULT 0,
    total_trips INT DEFAULT 0,
    profile_image_url TEXT,
    status ENUM('ACTIVE', 'ON_TRIP', 'INACTIVE', 'SUSPENDED') DEFAULT 'ACTIVE',
    created_at BIGINT NOT NULL,
    FOREIGN KEY (transporter_id) REFERENCES users(id),
    FOREIGN KEY (assigned_vehicle_id) REFERENCES vehicles(id)
  );
  
  -- Trips table
  CREATE TABLE trips (
    id VARCHAR(36) PRIMARY KEY,
    transporter_id VARCHAR(36) NOT NULL,
    vehicle_id VARCHAR(36) NOT NULL,
    driver_id VARCHAR(36),
    pickup_latitude DECIMAL(10,7) NOT NULL,
    pickup_longitude DECIMAL(10,7) NOT NULL,
    pickup_address TEXT NOT NULL,
    drop_latitude DECIMAL(10,7) NOT NULL,
    drop_longitude DECIMAL(10,7) NOT NULL,
    drop_address TEXT NOT NULL,
    distance DECIMAL(10,2),
    estimated_duration INT,
    status ENUM('PENDING', 'ASSIGNED', 'ACCEPTED', 'REJECTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED') DEFAULT 'PENDING',
    customer_name VARCHAR(100) NOT NULL,
    customer_mobile VARCHAR(10) NOT NULL,
    goods_type VARCHAR(100) NOT NULL,
    weight VARCHAR(50),
    fare DECIMAL(10,2) NOT NULL,
    created_at BIGINT NOT NULL,
    started_at BIGINT,
    completed_at BIGINT,
    notes TEXT,
    FOREIGN KEY (transporter_id) REFERENCES users(id),
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(id),
    FOREIGN KEY (driver_id) REFERENCES drivers(id)
  );
  
  -- GPS Tracking table
  CREATE TABLE trip_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id VARCHAR(36) NOT NULL,
    driver_id VARCHAR(36) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    speed DECIMAL(5,2),
    heading DECIMAL(5,2),
    accuracy DECIMAL(5,2),
    timestamp BIGINT NOT NULL,
    FOREIGN KEY (trip_id) REFERENCES trips(id),
    FOREIGN KEY (driver_id) REFERENCES drivers(id),
    INDEX idx_trip_time (trip_id, timestamp DESC)
  );
  
  -- Broadcasts table
  CREATE TABLE broadcasts (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    customer_mobile VARCHAR(10) NOT NULL,
    pickup_latitude DECIMAL(10,7) NOT NULL,
    pickup_longitude DECIMAL(10,7) NOT NULL,
    pickup_address TEXT NOT NULL,
    drop_latitude DECIMAL(10,7) NOT NULL,
    drop_longitude DECIMAL(10,7) NOT NULL,
    drop_address TEXT NOT NULL,
    distance DECIMAL(10,2),
    estimated_duration INT,
    total_trucks_needed INT NOT NULL,
    trucks_filled_so_far INT DEFAULT 0,
    vehicle_type_id VARCHAR(50) NOT NULL,
    vehicle_type_name VARCHAR(100) NOT NULL,
    goods_type VARCHAR(100) NOT NULL,
    weight VARCHAR(50),
    fare_per_truck DECIMAL(10,2) NOT NULL,
    total_fare DECIMAL(10,2) NOT NULL,
    status ENUM('ACTIVE', 'PARTIALLY_FILLED', 'FULLY_FILLED', 'EXPIRED', 'CANCELLED') DEFAULT 'ACTIVE',
    broadcast_time BIGINT NOT NULL,
    expiry_time BIGINT,
    is_urgent BOOLEAN DEFAULT false,
    notes TEXT
  );
  
  -- Notifications table
  CREATE TABLE notifications (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    data JSON,
    is_read BOOLEAN DEFAULT false,
    timestamp BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user_time (user_id, timestamp DESC)
  );
  ```

- [ ] **Create Indexes**
  ```sql
  CREATE INDEX idx_users_phone ON users(mobile_number);
  CREATE INDEX idx_vehicles_transporter ON vehicles(transporter_id);
  CREATE INDEX idx_vehicles_status ON vehicles(status);
  CREATE INDEX idx_drivers_transporter ON drivers(transporter_id);
  CREATE INDEX idx_drivers_phone ON drivers(mobile_number);
  CREATE INDEX idx_trips_transporter ON trips(transporter_id);
  CREATE INDEX idx_trips_driver ON trips(driver_id);
  CREATE INDEX idx_trips_status ON trips(status);
  ```

### 1.3 Redis Setup

- [ ] **Configure Redis**
  - Set password
  - Enable persistence (AOF)
  - Configure maxmemory policy

- [ ] **Test Redis Connection**
  ```bash
  redis-cli -h localhost -p 6379 -a <password> ping
  ```

---

## Phase 2: Authentication Module

### 2.1 OTP System

- [ ] **Implement OTP Generation**
  ```typescript
  function generateOTP(): string {
    return crypto.randomInt(100000, 999999).toString();
  }
  ```

- [ ] **Implement OTP Storage (Redis)**
  ```typescript
  async function storeOTP(phone: string, otp: string) {
    await redis.setex(`otp:${phone}`, 300, JSON.stringify({
      otp: otp,
      attempts: 0,
      createdAt: Date.now()
    }));
  }
  ```

- [ ] **Integrate SMS Service**
  - Configure Twilio/AWS SNS/MSG91
  - Test SMS sending in development
  - Implement retry logic for failures

- [ ] **Create Send OTP Endpoint**
  - `POST /auth/send-otp`
  - Validate phone number format
  - Check rate limit (3 OTPs per hour)
  - Generate and store OTP
  - Send SMS
  - Return success response

- [ ] **Create Verify OTP Endpoint**
  - `POST /auth/verify-otp`
  - Validate OTP from Redis
  - Check expiry (5 minutes)
  - Track attempts (max 3)
  - Create or fetch user
  - Generate JWT tokens
  - Return user + tokens

### 2.2 JWT Implementation

- [ ] **Create JWT Token Generator**
  ```typescript
  function generateAccessToken(user: User): string {
    return jwt.sign(
      { sub: user.id, phone: user.phone, roles: user.roles },
      JWT_SECRET,
      { expiresIn: '7d' }
    );
  }
  ```

- [ ] **Create Authentication Middleware**
  ```typescript
  async function authenticateToken(req, res, next) {
    const token = req.headers.authorization?.split(' ')[1];
    if (!token) return res.status(401).json({ error: 'Token required' });
    
    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      req.user = decoded;
      next();
    } catch (err) {
      return res.status(401).json({ error: 'Invalid token' });
    }
  }
  ```

- [ ] **Implement Token Refresh Endpoint**
  - `POST /auth/refresh-token`
  - Verify refresh token
  - Check if revoked
  - Generate new access token

- [ ] **Implement Logout Endpoint**
  - `POST /auth/logout`
  - Revoke tokens (add to blacklist)
  - Clear user session

### 2.3 Driver Authentication

- [ ] **Implement Driver OTP Flow**
  - `POST /driver/send-otp`
  - Look up driver by phone
  - Find assigned transporter
  - Send OTP to transporter's phone
  - Return transporter info

- [ ] **Test Driver Login Flow**
  - Register test driver
  - Attempt login as driver
  - Verify OTP sent to transporter
  - Verify login success

---

## Phase 3: Core Modules

### 3.1 Fleet Management

- [ ] **Vehicle CRUD Endpoints**
  - `GET /vehicles` - List vehicles with pagination
  - `POST /vehicles/add` - Add new vehicle
  - `GET /vehicles/{id}` - Get vehicle details
  - `PUT /vehicles/{id}` - Update vehicle
  - `DELETE /vehicles/{id}` - Delete vehicle
  - `POST /vehicles/{id}/assign-driver` - Assign driver

- [ ] **Implement Vehicle Validation**
  - Vehicle number format: XX-00-XX-0000
  - Check duplicate vehicle numbers
  - Validate category and subtype
  - Verify transporter ownership

- [ ] **Vehicle Catalog Integration**
  - Store 9 vehicle categories
  - Store subtypes for each category
  - API to get categories and subtypes

### 3.2 Driver Management

- [ ] **Driver CRUD Endpoints**
  - `GET /drivers` - List drivers with pagination
  - `POST /drivers/add` - Add new driver
  - `GET /drivers/{id}` - Get driver details
  - `PUT /drivers/{id}` - Update driver
  - `DELETE /drivers/{id}` - Delete driver
  - `GET /drivers/{id}/performance` - Get performance metrics

- [ ] **Driver Invitation System**
  - Send SMS invitation when driver added
  - Store invitation status
  - Track driver onboarding

- [ ] **Driver Performance Tracking**
  - Calculate total trips, earnings
  - Track ratings
  - Calculate on-time delivery rate

### 3.3 Trip Management

- [ ] **Trip CRUD Endpoints**
  - `POST /trips/create` - Create new trip
  - `GET /trips/{id}` - Get trip details
  - `POST /trips/{id}/start` - Start trip
  - `POST /trips/{id}/complete` - Complete trip
  - `POST /trips/{id}/cancel` - Cancel trip
  - `GET /trips` - List trips with filters

- [ ] **Trip Status Management**
  - Implement status transitions
  - Validate status changes
  - Send notifications on status change

### 3.4 Dashboard APIs

- [ ] **Transporter Dashboard**
  - `GET /transporter/dashboard`
  - Aggregate vehicle count
  - Aggregate driver count
  - Calculate today's revenue
  - Fetch recent trips

- [ ] **Driver Dashboard**
  - `GET /driver/dashboard`
  - Get active trip
  - Calculate earnings (today, week, month)
  - Get pending notifications

---

## Phase 4: Real-Time Features

### 4.1 GPS Tracking

- [ ] **Location Update Endpoint**
  - `POST /trips/{tripId}/location`
  - Validate trip is active
  - Store location in database
  - Broadcast to WebSocket clients
  - Return success immediately (< 200ms)

- [ ] **Live Tracking Endpoint**
  - `GET /trips/{tripId}/tracking`
  - Fetch latest location
  - Return current position and status

- [ ] **Location History Endpoint**
  - `GET /trips/{tripId}/locations`
  - Return paginated location history
  - Use cursor-based pagination

### 4.2 Broadcast System

- [ ] **Broadcast Endpoints**
  - `GET /broadcasts/active` - List active broadcasts
  - `GET /broadcasts/{id}` - Get broadcast details
  - `POST /broadcasts/create` - Create broadcast
  - `POST /broadcasts/{id}/accept` - Accept broadcast

- [ ] **Broadcast Logic**
  - Track trucks filled vs needed
  - Update status (ACTIVE → PARTIALLY_FILLED → FULLY_FILLED)
  - Handle expiry
  - Send notifications to eligible drivers

### 4.3 Push Notifications

- [ ] **FCM Integration**
  - Configure Firebase Admin SDK
  - Store FCM tokens
  - Create notification sending service

- [ ] **Notification Endpoints**
  - `GET /driver/notifications` - Get notifications
  - `PUT /driver/notifications/{id}/read` - Mark as read
  - `PUT /driver/fcm-token` - Update FCM token

- [ ] **Notification Types**
  - Trip assigned
  - Trip accepted/rejected
  - Trip started/completed
  - Broadcast available

### 4.4 WebSocket (Optional)

- [ ] **Setup WebSocket Server**
  - Configure WebSocket on port 8080 or same port
  - Authenticate connections
  - Store active connections

- [ ] **WebSocket Events**
  - `location_update` - Real-time GPS updates
  - `broadcast_update` - Broadcast status changes
  - `driver_response` - Driver accept/decline

---

## Phase 5: Testing and Optimization

### 5.1 Unit Testing

- [ ] **Test Authentication**
  - OTP generation and validation
  - JWT token generation and validation
  - Token refresh and revocation

- [ ] **Test Core Modules**
  - Vehicle CRUD operations
  - Driver CRUD operations
  - Trip lifecycle

- [ ] **Test Edge Cases**
  - Invalid input validation
  - Duplicate entries
  - Permission checks

### 5.2 Integration Testing

- [ ] **End-to-End Flow Tests**
  - Complete authentication flow
  - Vehicle addition and assignment
  - Trip creation and completion
  - Broadcast and driver response

- [ ] **Mobile App Integration**
  - Test with actual mobile app
  - Verify all screens load correctly
  - Test error handling

### 5.3 Performance Testing

- [ ] **Load Testing**
  - Dashboard endpoint (100 concurrent users)
  - GPS location updates (500 trips)
  - Authentication (50 logins/minute)

- [ ] **Database Optimization**
  - Verify all indexes exist
  - Check slow query log
  - Optimize N+1 queries

- [ ] **Caching Implementation**
  - Cache dashboard data (1 min)
  - Cache vehicle/driver lists (5 min)
  - Cache user profiles (1 hour)

### 5.4 Security Audit

- [ ] **OWASP Compliance**
  - SQL injection prevention
  - XSS prevention
  - CSRF protection
  - Rate limiting
  - Input validation

- [ ] **Penetration Testing**
  - Test authentication bypass
  - Test authorization flaws
  - Test rate limit bypass

---

## Phase 6: Deployment

### 6.1 Staging Deployment

- [ ] **Setup Staging Environment**
  - Configure staging server
  - Setup staging database
  - Configure staging Redis

- [ ] **Deploy to Staging**
  - Build Docker image
  - Push to container registry
  - Deploy with docker-compose

- [ ] **Staging Testing**
  - Run smoke tests
  - Test with QA team
  - Test mobile app integration

### 6.2 Production Deployment

- [ ] **Production Infrastructure**
  - Setup production servers
  - Configure load balancer
  - Setup database with replication
  - Setup Redis cluster

- [ ] **SSL/TLS Configuration**
  - Obtain SSL certificate
  - Configure Nginx/ALB
  - Force HTTPS

- [ ] **Deploy to Production**
  - Run database migrations
  - Deploy API servers
  - Verify health checks

- [ ] **Post-Deployment**
  - Monitor logs
  - Check error rates
  - Verify API response times

### 6.3 Monitoring Setup

- [ ] **Application Monitoring**
  - Setup Sentry for error tracking
  - Setup New Relic/DataDog for APM
  - Configure log aggregation

- [ ] **Infrastructure Monitoring**
  - Setup CloudWatch/Prometheus
  - Monitor CPU, memory, disk
  - Setup alerts

- [ ] **Business Metrics**
  - Track API usage
  - Monitor conversion rates
  - Track user growth

---

## Verification Steps

### Test Each Module

**Authentication**:
```bash
# Send OTP
curl -X POST https://api.weelo.in/v1/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"mobileNumber": "9876543210"}'

# Verify OTP
curl -X POST https://api.weelo.in/v1/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"mobileNumber": "9876543210", "otp": "123456"}'
```

**Vehicle Management**:
```bash
# Add vehicle
curl -X POST https://api.weelo.in/v1/vehicles/add \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"transporterId": "trans_123", "category": "container", ...}'

# List vehicles
curl -X GET "https://api.weelo.in/v1/vehicles?transporterId=trans_123" \
  -H "Authorization: Bearer $TOKEN"
```

**GPS Tracking**:
```bash
# Send location
curl -X POST https://api.weelo.in/v1/trips/trip_123/location \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"latitude": 23.0225, "longitude": 72.5714, ...}'
```

### Mobile App Integration Test

1. [ ] Install app on test device
2. [ ] Complete login flow
3. [ ] Test transporter features (add vehicle, driver)
4. [ ] Test driver features (receive notification, accept trip)
5. [ ] Test GPS tracking during active trip
6. [ ] Verify push notifications work

---

## Common Issues and Solutions

### Issue: OTP not received

**Solution**:
- Check SMS service credentials
- Verify phone number format (+91 prefix)
- Check SMS service logs
- Verify rate limits not exceeded

### Issue: JWT token expired

**Solution**:
- Implement token refresh flow
- Check token expiry settings
- Verify refresh token is working

### Issue: GPS updates slow

**Solution**:
- Check database indexes on trip_locations
- Implement batch inserts
- Use async processing
- Consider time-series database

### Issue: High database load

**Solution**:
- Enable query caching
- Add missing indexes
- Use read replicas
- Implement connection pooling

### Issue: Push notifications not delivered

**Solution**:
- Verify FCM server key
- Check FCM token is updated
- Verify device has internet connection
- Check notification channel settings

---

## Success Criteria

Your backend integration is complete when:

- [ ] ✅ All 23+ API endpoints implemented and tested
- [ ] ✅ Authentication works (OTP + JWT)
- [ ] ✅ Mobile app can login and navigate
- [ ] ✅ Vehicles and drivers can be added
- [ ] ✅ Trips can be created and completed
- [ ] ✅ GPS tracking works in real-time
- [ ] ✅ Push notifications delivered
- [ ] ✅ Dashboard loads < 1 second
- [ ] ✅ All endpoints respond < 1 second
- [ ] ✅ Error rate < 0.1%
- [ ] ✅ Load tested (100 concurrent users)
- [ ] ✅ Security audit passed
- [ ] ✅ Monitoring and alerts configured

---

## Next Steps After Integration

1. **User Acceptance Testing**: Test with real users
2. **Beta Launch**: Release to limited users
3. **Monitor and Iterate**: Track metrics, fix bugs
4. **Scale**: Add more servers as user base grows
5. **Feature Additions**: Implement additional features based on feedback

---

## Support

For questions during integration:
- Review UI code in `/app/src/main/java/com/weelo/logistics/`
- Check API service interfaces for request/response formats
- Refer to data models for field specifications
- Contact mobile team for UI behavior clarification

---

**Congratulations!** You now have complete documentation to integrate the backend. Follow this checklist step-by-step for a successful implementation.
