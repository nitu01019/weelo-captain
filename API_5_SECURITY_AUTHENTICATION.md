# 5️⃣ Security & Authentication Guide

## Overview
This document outlines security best practices, authentication mechanisms, and data protection strategies for the Weelo Logistics backend API.

---

## 🔐 Authentication System

### JWT (JSON Web Token) Based Authentication

All API requests require a valid JWT token in the Authorization header.

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Token Structure

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "userId": "DRV-001",
    "role": "DRIVER",
    "transporterId": "TRANS-567",
    "name": "Ramesh Singh",
    "mobile": "+91-9876543210",
    "iat": 1735993020,
    "exp": 1736597820
  }
}
```

### Token Expiry
- **Access Token**: 7 days
- **Refresh Token**: 30 days

---

## 🔑 Authentication Endpoints

### 1. Login with OTP

**Endpoint**: `POST /auth/send-otp`

```json
{
  "mobile": "+91-9876543210",
  "role": "DRIVER"
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "otpSent": true,
    "expiresIn": 300,
    "otpId": "OTP-123456"
  },
  "message": "OTP sent to +91-9876543210"
}
```

### 2. Verify OTP

**Endpoint**: `POST /auth/verify-otp`

```json
{
  "mobile": "+91-9876543210",
  "otp": "123456",
  "otpId": "OTP-123456",
  "deviceInfo": {
    "platform": "android",
    "fcmToken": "fcm_token_here...",
    "deviceId": "device_unique_id"
  }
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "refresh_token_here...",
    "user": {
      "userId": "DRV-001",
      "role": "DRIVER",
      "name": "Ramesh Singh",
      "mobile": "+91-9876543210",
      "profileComplete": true
    },
    "expiresIn": 604800
  }
}
```

### 3. Refresh Token

**Endpoint**: `POST /auth/refresh`

```json
{
  "refreshToken": "refresh_token_here..."
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "accessToken": "new_access_token...",
    "expiresIn": 604800
  }
}
```

### 4. Logout

**Endpoint**: `POST /auth/logout`

```http
Authorization: Bearer <access_token>
```

**Response**:
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

---

## 🛡️ Authorization & Permissions

### Role-Based Access Control (RBAC)

| Role | Permissions |
|------|-------------|
| **CUSTOMER** | Create broadcasts, View assignments, Track trips |
| **TRANSPORTER** | View broadcasts, Create assignments, Manage drivers/vehicles |
| **DRIVER** | View notifications, Accept/Decline trips, Update location |
| **ADMIN** | All permissions + system management |

### Permission Middleware

```javascript
// Example middleware implementation
function authorize(requiredRoles) {
  return (req, res, next) => {
    const token = req.headers.authorization?.replace('Bearer ', '');
    
    if (!token) {
      return res.status(401).json({
        success: false,
        error: {
          code: 'AUTH_001',
          message: 'Authentication required'
        }
      });
    }
    
    try {
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      
      if (!requiredRoles.includes(decoded.role)) {
        return res.status(403).json({
          success: false,
          error: {
            code: 'AUTH_002',
            message: 'Insufficient permissions'
          }
        });
      }
      
      req.user = decoded;
      next();
    } catch (error) {
      return res.status(401).json({
        success: false,
        error: {
          code: 'AUTH_003',
          message: 'Invalid or expired token'
        }
      });
    }
  };
}

// Usage
app.post('/broadcasts', authorize(['CUSTOMER']), createBroadcast);
app.get('/broadcasts/active', authorize(['TRANSPORTER']), getActiveBroadcasts);
app.post('/notifications/:id/accept', authorize(['DRIVER']), acceptTrip);
```

---

## 🔒 Data Encryption

### 1. Location Data Encryption

Encrypt sensitive GPS coordinates before storing/transmitting.

```javascript
const crypto = require('crypto');

function encryptLocation(latitude, longitude) {
  const algorithm = 'aes-256-gcm';
  const key = Buffer.from(process.env.ENCRYPTION_KEY, 'hex');
  const iv = crypto.randomBytes(16);
  
  const cipher = crypto.createCipheriv(algorithm, key, iv);
  
  const data = JSON.stringify({ latitude, longitude });
  let encrypted = cipher.update(data, 'utf8', 'hex');
  encrypted += cipher.final('hex');
  
  const authTag = cipher.getAuthTag();
  
  return {
    encrypted,
    iv: iv.toString('hex'),
    authTag: authTag.toString('hex')
  };
}

function decryptLocation(encrypted, iv, authTag) {
  const algorithm = 'aes-256-gcm';
  const key = Buffer.from(process.env.ENCRYPTION_KEY, 'hex');
  
  const decipher = crypto.createDecipheriv(
    algorithm,
    key,
    Buffer.from(iv, 'hex')
  );
  
  decipher.setAuthTag(Buffer.from(authTag, 'hex'));
  
  let decrypted = decipher.update(encrypted, 'hex', 'utf8');
  decrypted += decipher.final('utf8');
  
  return JSON.parse(decrypted);
}
```

### 2. Personal Data Masking

Mask sensitive data in logs and non-privileged API responses.

```javascript
function maskMobile(mobile) {
  // +91-9876543210 → +91-98765***10
  return mobile.replace(/(\d{5})\d{3}(\d{2})/, '$1***$2');
}

function maskEmail(email) {
  // john.doe@example.com → j***e@example.com
  const [local, domain] = email.split('@');
  return `${local[0]}***${local[local.length - 1]}@${domain}`;
}
```

---

## 🚨 Rate Limiting

### Implementation

```javascript
const rateLimit = require('express-rate-limit');

// General API rate limit
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // 100 requests per window
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_001',
      message: 'Too many requests, please try again later'
    }
  }
});

// Strict rate limit for authentication
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5, // 5 OTP requests per 15 minutes
  skipSuccessfulRequests: true
});

// Location update rate limit (per driver)
const locationLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 10, // 10 location updates per minute
  keyGenerator: (req) => req.user.userId
});

// Apply to routes
app.use('/api/', apiLimiter);
app.post('/auth/send-otp', authLimiter);
app.post('/tracking/:id/location', authorize(['DRIVER']), locationLimiter);
```

### Rate Limit Headers

```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1735994000
```

---

## 🔍 Input Validation

### Request Validation Schema

```javascript
const Joi = require('joi');

// Broadcast creation validation
const createBroadcastSchema = Joi.object({
  pickupLocation: Joi.object({
    latitude: Joi.number().min(-90).max(90).required(),
    longitude: Joi.number().min(-180).max(180).required(),
    address: Joi.string().max(500).required(),
    city: Joi.string().max(100),
    state: Joi.string().max(100),
    pincode: Joi.string().pattern(/^\d{6}$/)
  }).required(),
  dropLocation: Joi.object({
    latitude: Joi.number().min(-90).max(90).required(),
    longitude: Joi.number().min(-180).max(180).required(),
    address: Joi.string().max(500).required()
  }).required(),
  totalTrucksNeeded: Joi.number().integer().min(1).max(100).required(),
  vehicleType: Joi.string().valid(
    'MINI', 'OPEN', 'CONTAINER', 'TRAILER', 
    'TANKER', 'TIPPER', 'BULKER', 'LCV', 'DUMPER'
  ).required(),
  goodsType: Joi.string().max(100).required(),
  weight: Joi.string().max(50),
  notes: Joi.string().max(500),
  isUrgent: Joi.boolean().default(false)
});

// Validation middleware
function validate(schema) {
  return (req, res, next) => {
    const { error } = schema.validate(req.body, { abortEarly: false });
    
    if (error) {
      return res.status(400).json({
        success: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Invalid request data',
          details: error.details.map(d => ({
            field: d.path.join('.'),
            message: d.message
          }))
        }
      });
    }
    
    next();
  };
}

// Usage
app.post('/broadcasts', 
  authorize(['CUSTOMER']),
  validate(createBroadcastSchema),
  createBroadcast
);
```

---

## 🛠️ SQL Injection Prevention

### Use Parameterized Queries

**❌ NEVER DO THIS:**
```javascript
// VULNERABLE TO SQL INJECTION
const query = `SELECT * FROM drivers WHERE mobile = '${mobile}'`;
db.query(query);
```

**✅ ALWAYS DO THIS:**
```javascript
// SAFE - PARAMETERIZED QUERY
const query = 'SELECT * FROM drivers WHERE mobile = ?';
db.query(query, [mobile]);
```

### ORM Usage (Sequelize Example)

```javascript
// Safe by default
const driver = await Driver.findOne({
  where: { mobile: req.body.mobile }
});
```

---

## 🔐 API Key Management

### For Third-Party Integrations

```javascript
// Generate API Key
function generateApiKey() {
  return crypto.randomBytes(32).toString('hex');
}

// Validate API Key middleware
async function validateApiKey(req, res, next) {
  const apiKey = req.headers['x-api-key'];
  
  if (!apiKey) {
    return res.status(401).json({
      success: false,
      error: {
        code: 'API_KEY_001',
        message: 'API key required'
      }
    });
  }
  
  const client = await ApiClient.findOne({ where: { apiKey } });
  
  if (!client || !client.isActive) {
    return res.status(403).json({
      success: false,
      error: {
        code: 'API_KEY_002',
        message: 'Invalid or inactive API key'
      }
    });
  }
  
  req.apiClient = client;
  next();
}
```

---

## 🔒 HTTPS & SSL

### Force HTTPS

```javascript
function requireHTTPS(req, res, next) {
  if (!req.secure && req.get('x-forwarded-proto') !== 'https' && process.env.NODE_ENV === 'production') {
    return res.redirect('https://' + req.get('host') + req.url);
  }
  next();
}

app.use(requireHTTPS);
```

---

## 🚫 CORS Configuration

```javascript
const cors = require('cors');

const corsOptions = {
  origin: function (origin, callback) {
    const allowedOrigins = [
      'https://weelologistics.com',
      'https://app.weelologistics.com',
      'https://admin.weelologistics.com'
    ];
    
    if (!origin || allowedOrigins.includes(origin)) {
      callback(null, true);
    } else {
      callback(new Error('Not allowed by CORS'));
    }
  },
  credentials: true,
  optionsSuccessStatus: 200
};

app.use(cors(corsOptions));
```

---

## 📝 Audit Logging

### Log Security Events

```javascript
async function logSecurityEvent(event) {
  await SecurityLog.create({
    eventType: event.type,
    userId: event.userId,
    ipAddress: event.ip,
    userAgent: event.userAgent,
    action: event.action,
    resource: event.resource,
    result: event.result,
    timestamp: Date.now()
  });
}

// Usage examples
logSecurityEvent({
  type: 'AUTH',
  userId: 'DRV-001',
  ip: req.ip,
  userAgent: req.headers['user-agent'],
  action: 'LOGIN',
  resource: null,
  result: 'SUCCESS'
});

logSecurityEvent({
  type: 'UNAUTHORIZED_ACCESS',
  userId: 'DRV-001',
  ip: req.ip,
  action: 'VIEW_BROADCAST',
  resource: 'BC-2026-001-ABC123',
  result: 'DENIED'
});
```

---

## 🔐 Sensitive Data Storage

### Environment Variables

```bash
# .env file (NEVER commit to git)
JWT_SECRET=your_256_bit_secret_key_here
JWT_REFRESH_SECRET=another_256_bit_secret_key
ENCRYPTION_KEY=32_byte_hex_encryption_key
DB_PASSWORD=database_password
FCM_SERVER_KEY=firebase_server_key
TWILIO_AUTH_TOKEN=twilio_auth_token
```

### Database Password Hashing

```javascript
const bcrypt = require('bcrypt');

// Hash password (if using password auth)
async function hashPassword(password) {
  const saltRounds = 12;
  return await bcrypt.hash(password, saltRounds);
}

// Verify password
async function verifyPassword(password, hash) {
  return await bcrypt.compare(password, hash);
}
```

---

## 🚨 Security Headers

```javascript
const helmet = require('helmet');

app.use(helmet());

// Custom security headers
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('X-XSS-Protection', '1; mode=block');
  res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  next();
});
```

---

## Error Codes

| Code | Message | Description |
|------|---------|-------------|
| AUTH_001 | Authentication required | Missing authorization header |
| AUTH_002 | Insufficient permissions | User role not authorized |
| AUTH_003 | Invalid or expired token | JWT token invalid/expired |
| AUTH_004 | Invalid OTP | OTP verification failed |
| AUTH_005 | OTP expired | OTP timeout (>5 min) |
| RATE_LIMIT_001 | Too many requests | Rate limit exceeded |
| API_KEY_001 | API key required | Missing API key header |
| API_KEY_002 | Invalid API key | API key not found/inactive |
| VALIDATION_ERROR | Invalid request data | Request validation failed |

---

**Next**: [API 6 - WebSocket & Push Notifications →](API_6_WEBSOCKET_PUSH_NOTIFICATIONS.md)
