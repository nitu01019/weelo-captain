# Authentication and Security Expectations

This document explains how the mobile app expects authentication and authorization to work, and what security measures the backend must implement.

---

## Table of Contents

1. [Authentication Flow Overview](#authentication-flow-overview)
2. [JWT Token Implementation](#jwt-token-implementation)
3. [OTP System Requirements](#otp-system-requirements)
4. [Driver Authentication (Special Flow)](#driver-authentication-special-flow)
5. [Token Management](#token-management)
6. [Session Handling](#session-handling)
7. [Security Headers](#security-headers)
8. [Rate Limiting Requirements](#rate-limiting-requirements)
9. [Input Validation and Sanitization](#input-validation-and-sanitization)
10. [Error Handling for Auth](#error-handling-for-auth)
11. [OWASP Compliance](#owasp-compliance)

---

## Authentication Flow Overview

### Standard User Flow (Transporter/Customer)

```
┌──────────────┐
│ User enters  │
│ phone number │
└──────┬───────┘
       │
       ▼
┌─────────────────────────┐
│ POST /auth/send-otp     │
│ {mobileNumber}          │
└──────┬──────────────────┘
       │
       ▼
┌─────────────────────────┐
│ Backend generates OTP   │
│ Stores in cache/Redis   │
│ Sends SMS via provider  │
└──────┬──────────────────┘
       │
       ▼
┌─────────────────────────┐
│ User enters 6-digit OTP │
└──────┬──────────────────┘
       │
       ▼
┌─────────────────────────┐
│ POST /auth/verify-otp   │
│ {mobileNumber, otp}     │
└──────┬──────────────────┘
       │
       ▼
┌─────────────────────────┐
│ Backend validates OTP   │
│ Generates JWT tokens    │
│ Returns user + tokens   │
└──────┬──────────────────┘
       │
       ▼
┌─────────────────────────┐
│ App stores tokens in    │
│ EncryptedSharedPrefs    │
│ All subsequent requests │
│ include Bearer token    │
└─────────────────────────┘
```

### Driver Authentication Flow (Different)

```
┌──────────────────────┐
│ Driver enters their  │
│ phone number         │
└──────┬───────────────┘
       │
       ▼
┌──────────────────────────────┐
│ POST /driver/send-otp        │
│ {driverPhone}                │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ Backend looks up driver      │
│ Finds assigned transporter   │
│ Sends OTP to TRANSPORTER     │
│ (not to driver!)             │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ Driver sees message:         │
│ "OTP sent to ABC Logistics"  │
│ Driver asks transporter      │
│ for OTP                      │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ Driver enters OTP            │
│ POST /driver/verify-otp      │
└──────┬───────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ Backend validates OTP        │
│ Returns driver auth token    │
└──────────────────────────────┘
```

**Why this flow?**
- Security: Transporters control driver access
- Accountability: Transporters know when drivers log in
- Authorization: Only registered drivers can access

---

## JWT Token Implementation

### Token Structure

The app expects JWT tokens with the following claims:

```json
{
  "sub": "user_123",              // User ID
  "phone": "9876543210",          // Phone number
  "roles": ["TRANSPORTER"],       // User roles
  "name": "Rajesh Kumar",         // User name
  "iat": 1704614400,              // Issued at (Unix timestamp)
  "exp": 1705219200,              // Expiry (Unix timestamp)
  "jti": "token_unique_id"        // JWT ID (for revocation)
}
```

### Token Types

**Access Token**:
- **Purpose**: Short-lived token for API access
- **Expiry**: 7 days (604800 seconds)
- **Storage**: EncryptedSharedPreferences on device
- **Usage**: Included in `Authorization: Bearer {token}` header

**Refresh Token**:
- **Purpose**: Long-lived token to get new access tokens
- **Expiry**: 30 days (2592000 seconds)
- **Storage**: EncryptedSharedPreferences on device
- **Usage**: Only for `POST /auth/refresh-token` endpoint

### Token Generation

```typescript
// Backend implementation example
function generateAccessToken(user: User): string {
  return jwt.sign(
    {
      sub: user.id,
      phone: user.mobileNumber,
      roles: user.roles,
      name: user.name,
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 604800, // 7 days
      jti: generateUniqueId()
    },
    JWT_SECRET,
    { algorithm: 'HS256' }
  );
}
```

### Token Validation

Backend must validate tokens on every protected endpoint:

```typescript
// Middleware example
function validateToken(req, res, next) {
  const token = req.headers.authorization?.split(' ')[1];
  
  if (!token) {
    return res.status(401).json({
      success: false,
      error: {
        code: "AUTH_TOKEN_MISSING",
        message: "Authorization token is required"
      }
    });
  }
  
  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    req.user = decoded;
    next();
  } catch (error) {
    if (error.name === 'TokenExpiredError') {
      return res.status(401).json({
        success: false,
        error: {
          code: "AUTH_TOKEN_EXPIRED",
          message: "Token has expired. Please refresh."
        }
      });
    }
    
    return res.status(401).json({
      success: false,
      error: {
        code: "AUTH_TOKEN_INVALID",
        message: "Invalid authorization token"
      }
    });
  }
}
```

### How App Handles Token Expiry

```kotlin
// App-side logic
class TokenInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        
        // If token expired
        if (response.code == 401 && response.body?.string()?.contains("AUTH_TOKEN_EXPIRED") == true) {
            // Attempt token refresh
            val newToken = refreshAccessToken()
            
            if (newToken != null) {
                // Retry original request with new token
                val newRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(newRequest)
            } else {
                // Refresh failed, force logout
                forceLogout()
            }
        }
        
        return response
    }
}
```

---

## OTP System Requirements

### OTP Generation

**Requirements**:
- 6-digit numeric code
- Cryptographically random (not pseudo-random)
- Unique per session
- Cannot be reused

**Example Implementation**:
```typescript
function generateOTP(): string {
  // Use crypto library for true randomness
  const randomBytes = crypto.randomBytes(3);
  const otp = randomBytes.readUIntBE(0, 3) % 1000000;
  return otp.toString().padStart(6, '0');
}
```

### OTP Storage

**Use Redis or in-memory cache**:
```typescript
// Store OTP with expiry
redis.setex(
  `otp:${mobileNumber}`,
  300, // 5 minutes
  JSON.stringify({
    otp: generatedOTP,
    attempts: 0,
    createdAt: Date.now()
  })
);
```

### OTP Validation

**Rules**:
1. **Expiry**: Valid for exactly 5 minutes (300 seconds)
2. **Max Attempts**: 3 incorrect attempts allowed
3. **Rate Limiting**: Max 3 OTP requests per phone number per hour
4. **One-Time Use**: Delete OTP immediately after successful verification

**Implementation**:
```typescript
async function verifyOTP(mobileNumber: string, userOTP: string): Promise<boolean> {
  const storedData = await redis.get(`otp:${mobileNumber}`);
  
  if (!storedData) {
    throw new Error("OTP_EXPIRED");
  }
  
  const data = JSON.parse(storedData);
  
  // Check expiry
  if (Date.now() - data.createdAt > 300000) {
    await redis.del(`otp:${mobileNumber}`);
    throw new Error("OTP_EXPIRED");
  }
  
  // Check attempts
  if (data.attempts >= 3) {
    await redis.del(`otp:${mobileNumber}`);
    throw new Error("OTP_MAX_ATTEMPTS_EXCEEDED");
  }
  
  // Verify OTP
  if (data.otp !== userOTP) {
    data.attempts++;
    await redis.setex(
      `otp:${mobileNumber}`,
      300 - Math.floor((Date.now() - data.createdAt) / 1000),
      JSON.stringify(data)
    );
    throw new Error("OTP_INVALID");
  }
  
  // Success - delete OTP
  await redis.del(`otp:${mobileNumber}`);
  return true;
}
```

### SMS Provider Integration

**Expected Behavior**:
- Send SMS within 5 seconds
- Use reliable provider (Twilio, AWS SNS, MSG91)
- Handle failures gracefully
- Log all SMS attempts

**SMS Message Template**:
```
Your Weelo Captain OTP is: {OTP}
Valid for 5 minutes. Do not share with anyone.
```

### OTP Security Measures

1. **No OTP in Logs**: Never log OTP values
2. **HTTPS Only**: All OTP endpoints must use HTTPS
3. **Constant Time Comparison**: Prevent timing attacks
4. **Brute Force Protection**: Lock account after 5 failed OTP attempts

---

## Driver Authentication (Special Flow)

### Why Different?

Drivers are registered by transporters. The driver authentication flow ensures:
1. Only registered drivers can log in
2. Transporters control driver access
3. Transporters are notified of driver logins

### Implementation

**Step 1: Driver Sends Phone Number**

`POST /driver/send-otp`

```typescript
async function sendDriverOTP(driverPhone: string) {
  // 1. Look up driver
  const driver = await db.query(
    "SELECT * FROM drivers WHERE phone = ?",
    [driverPhone]
  );
  
  if (!driver) {
    throw new ApiError(404, "DRIVER_NOT_FOUND", 
      "Driver not found. Please contact your transporter to register.");
  }
  
  if (driver.status === 'SUSPENDED') {
    throw new ApiError(403, "DRIVER_SUSPENDED", 
      "Your account has been suspended. Contact your transporter.");
  }
  
  // 2. Get driver's transporter
  const transporter = await db.query(
    "SELECT * FROM transporters WHERE id = ?",
    [driver.transporterId]
  );
  
  // 3. Generate OTP
  const otp = generateOTP();
  
  // 4. Store OTP linked to driver phone
  await redis.setex(
    `driver_otp:${driverPhone}`,
    300,
    JSON.stringify({
      otp: otp,
      driverId: driver.id,
      transporterId: transporter.id,
      attempts: 0,
      createdAt: Date.now()
    })
  );
  
  // 5. Send SMS to TRANSPORTER (not driver)
  await sendSMS(transporter.phone, 
    `Driver ${driver.name} (${driverPhone}) is logging in. OTP: ${otp}`
  );
  
  // 6. Return response
  return {
    success: true,
    message: "OTP sent to your transporter",
    transporterName: transporter.companyName || transporter.name,
    transporterPhone: maskPhone(transporter.phone),
    otpSentTo: "transporter",
    expiryMinutes: 5
  };
}
```

**Step 2: Driver Verifies OTP**

`POST /driver/verify-otp`

```typescript
async function verifyDriverOTP(driverPhone: string, otp: string) {
  // Similar to standard OTP verification
  // But uses driver_otp: prefix
  
  const isValid = await verifyOTP(`driver_otp:${driverPhone}`, otp);
  
  if (!isValid) {
    throw new ApiError(401, "OTP_INVALID", "Invalid OTP");
  }
  
  // Get driver details
  const driver = await getDriverByPhone(driverPhone);
  
  // Generate tokens
  const accessToken = generateAccessToken({
    id: driver.id,
    phone: driver.phone,
    roles: ['DRIVER'],
    name: driver.name
  });
  
  const refreshToken = generateRefreshToken(driver.id);
  
  return {
    success: true,
    message: "Login successful",
    driver: driver,
    authToken: accessToken,
    refreshToken: refreshToken
  };
}
```

---

## Token Management

### Refresh Token Flow

**When**: App automatically refreshes token when access token expires

**Endpoint**: `POST /auth/refresh-token`

**Request**:
```json
{
  "refreshToken": "refresh_token_xyz"
}
```

**Backend Logic**:
```typescript
async function refreshAccessToken(refreshToken: string) {
  // 1. Verify refresh token
  let decoded;
  try {
    decoded = jwt.verify(refreshToken, REFRESH_TOKEN_SECRET);
  } catch (error) {
    throw new ApiError(401, "REFRESH_TOKEN_INVALID", "Invalid refresh token");
  }
  
  // 2. Check if token is revoked
  const isRevoked = await redis.get(`revoked:${decoded.jti}`);
  if (isRevoked) {
    throw new ApiError(401, "REFRESH_TOKEN_REVOKED", "Token has been revoked");
  }
  
  // 3. Get user
  const user = await getUserById(decoded.sub);
  if (!user || !user.isActive) {
    throw new ApiError(401, "USER_INACTIVE", "User account is inactive");
  }
  
  // 4. Generate new access token
  const newAccessToken = generateAccessToken(user);
  
  return {
    success: true,
    accessToken: newAccessToken,
    expiresIn: 604800
  };
}
```

### Token Revocation

**Use Cases**:
- User logs out
- User changes password
- Account suspended
- Security breach

**Implementation**:
```typescript
async function revokeToken(jti: string) {
  // Store token ID in blacklist with expiry matching token expiry
  await redis.setex(
    `revoked:${jti}`,
    604800, // Match token expiry
    'true'
  );
}

// Middleware to check revocation
function checkRevocation(token: DecodedToken) {
  const isRevoked = await redis.get(`revoked:${token.jti}`);
  if (isRevoked) {
    throw new ApiError(401, "TOKEN_REVOKED", "Token has been revoked");
  }
}
```

---

## Session Handling

### Device Tracking

Track user sessions by device:

```typescript
interface Session {
  userId: string;
  deviceId: string;
  deviceModel: string;
  os: string;
  ipAddress: string;
  lastActiveAt: number;
  fcmToken: string; // For push notifications
}

// Store active sessions
await db.insert('sessions', {
  userId: user.id,
  deviceId: deviceInfo.deviceId,
  deviceModel: deviceInfo.model,
  os: deviceInfo.os,
  ipAddress: req.ip,
  lastActiveAt: Date.now(),
  createdAt: Date.now()
});
```

### Multi-Device Support

- Allow users to log in from multiple devices
- Each device gets its own refresh token
- Logout from one device doesn't affect others
- "Logout from all devices" option available

### Session Expiry

- **Access Token**: 7 days
- **Refresh Token**: 30 days
- **Inactive Session**: Auto-logout after 30 days of inactivity

---

## Security Headers

All API responses must include security headers:

```http
Strict-Transport-Security: max-age=31536000; includeSubDomains
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'
```

---

## Rate Limiting Requirements

### API Rate Limits

| Endpoint | Limit | Window |
|----------|-------|--------|
| POST /auth/send-otp | 3 requests | 1 hour per IP |
| POST /auth/verify-otp | 5 requests | 5 minutes per phone |
| POST /auth/refresh-token | 10 requests | 1 hour per user |
| GET /driver/dashboard | 60 requests | 1 minute per user |
| POST /trips/{id}/location | 120 requests | 1 minute per trip |
| All other APIs | 100 requests | 1 minute per user |

### Implementation

Use Redis for distributed rate limiting:

```typescript
async function checkRateLimit(key: string, limit: number, window: number): Promise<boolean> {
  const current = await redis.incr(key);
  
  if (current === 1) {
    await redis.expire(key, window);
  }
  
  return current <= limit;
}

// Usage
const isAllowed = await checkRateLimit(
  `rate_limit:send_otp:${req.ip}`,
  3,
  3600
);

if (!isAllowed) {
  throw new ApiError(429, "RATE_LIMIT_EXCEEDED", 
    "Too many requests. Please try again later.");
}
```

### Rate Limit Response

```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests. Please try again after 30 minutes.",
    "retryAfter": 1800
  }
}
```

**Headers**:
```http
X-RateLimit-Limit: 3
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1704616200
Retry-After: 1800
```

---

## Input Validation and Sanitization

### Server-Side Validation

**Never trust client input**. Always validate on backend:

```typescript
// Phone number validation
function validatePhoneNumber(phone: string): boolean {
  return /^\d{10}$/.test(phone);
}

// Vehicle number validation
function validateVehicleNumber(number: string): boolean {
  return /^[A-Z]{2}-\d{2}-[A-Z]{2}-\d{4}$/.test(number);
}

// Sanitize text input
function sanitize(input: string): string {
  return input
    .trim()
    .replace(/[<>]/g, '')  // Remove HTML tags
    .substring(0, 500);     // Max length
}
```

### SQL Injection Prevention

Use parameterized queries:

```typescript
// ❌ BAD - Vulnerable to SQL injection
const query = `SELECT * FROM users WHERE phone = '${phone}'`;

// ✅ GOOD - Parameterized query
const query = `SELECT * FROM users WHERE phone = ?`;
const result = await db.query(query, [phone]);
```

### XSS Prevention

Sanitize all text inputs before storing:

```typescript
import DOMPurify from 'isomorphic-dompurify';

function sanitizeHTML(dirty: string): string {
  return DOMPurify.sanitize(dirty, { 
    ALLOWED_TAGS: [],
    ALLOWED_ATTR: [] 
  });
}
```

---

## Error Handling for Auth

### Standard Auth Errors

| Error Code | HTTP Status | Message | UI Action |
|------------|-------------|---------|-----------|
| AUTH_TOKEN_MISSING | 401 | Authorization token required | Force logout |
| AUTH_TOKEN_INVALID | 401 | Invalid token | Force logout |
| AUTH_TOKEN_EXPIRED | 401 | Token expired | Attempt refresh |
| OTP_INVALID | 400 | Invalid OTP | Allow retry (3 max) |
| OTP_EXPIRED | 401 | OTP expired | Allow resend |
| OTP_MAX_ATTEMPTS | 403 | Too many attempts | Lock for 1 hour |
| PHONE_INVALID | 400 | Invalid phone number | Show inline error |
| USER_NOT_FOUND | 404 | User not registered | Navigate to signup |
| USER_SUSPENDED | 403 | Account suspended | Show message, contact support |

### Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "OTP_INVALID",
    "message": "Invalid OTP. 2 attempts remaining.",
    "field": "otp",
    "attemptsRemaining": 2
  }
}
```

---

## OWASP Compliance

The app implements OWASP Mobile Top 10 security measures:

### M1: Improper Platform Usage
- Uses EncryptedSharedPreferences for sensitive data
- Validates SSL certificates

### M2: Insecure Data Storage
- Tokens stored in EncryptedSharedPreferences only
- No sensitive data in logs
- No sensitive data in plain text files

### M3: Insecure Communication
- HTTPS only (TLS 1.2+)
- Certificate pinning (optional)
- No HTTP fallback

### M4: Insecure Authentication
- OTP-based authentication
- JWT tokens with expiry
- Refresh token rotation

### M5: Insufficient Cryptography
- AES-256 for encrypted storage
- Strong random number generation for OTP

### M6: Insecure Authorization
- Role-based access control
- JWT claims validation
- Backend validates all permissions

### M7: Client Code Quality
- Input validation on both client and server
- Error handling prevents crashes

### M8: Code Tampering
- ProGuard obfuscation enabled
- Root detection (optional)

### M9: Reverse Engineering
- Code obfuscation
- String encryption for sensitive values

### M10: Extraneous Functionality
- No debug code in production
- Logging disabled in release builds

---

## Summary: Security Checklist

**Authentication**:
- ✅ OTP-based phone authentication
- ✅ JWT tokens with proper expiry
- ✅ Refresh token mechanism
- ✅ Token revocation support

**Authorization**:
- ✅ Role-based access control
- ✅ JWT claims validation
- ✅ Per-endpoint permission checks

**Data Protection**:
- ✅ HTTPS only
- ✅ Encrypted storage on device
- ✅ No sensitive data in logs

**Attack Prevention**:
- ✅ Rate limiting on all endpoints
- ✅ SQL injection prevention
- ✅ XSS prevention
- ✅ CSRF tokens (for web)

**Session Management**:
- ✅ Multi-device support
- ✅ Session tracking
- ✅ Auto-logout on inactivity

---

**Next**: See `06_Error_Handling_Contract.md` for standardized error responses.
