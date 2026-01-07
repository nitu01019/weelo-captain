# Error Handling Contract

This document defines the standard error response format and how the UI handles different error scenarios.

---

## Table of Contents

1. [Standard Error Response Format](#standard-error-response-format)
2. [HTTP Status Codes](#http-status-codes)
3. [Error Code Categories](#error-code-categories)
4. [Complete Error Code Reference](#complete-error-code-reference)
5. [UI Error Mapping](#ui-error-mapping)
6. [Validation Error Format](#validation-error-format)
7. [Network Error Handling](#network-error-handling)
8. [Retry Logic](#retry-logic)

---

## Standard Error Response Format

All error responses must follow this structure:

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "field": "fieldName",           // Optional: For validation errors
    "details": { ... },             // Optional: Additional context
    "timestamp": 1704614400000      // Optional: Error timestamp
  }
}
```

### Example Error Responses

**Validation Error**:
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid vehicle number format",
    "field": "vehicleNumber",
    "details": {
      "expected": "XX-00-XX-0000",
      "received": "GJ01AB1234"
    }
  }
}
```

**Authentication Error**:
```json
{
  "success": false,
  "error": {
    "code": "AUTH_TOKEN_EXPIRED",
    "message": "Your session has expired. Please login again."
  }
}
```

**Resource Not Found**:
```json
{
  "success": false,
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Vehicle not found",
    "details": {
      "resourceType": "Vehicle",
      "resourceId": "vehicle_123"
    }
  }
}
```

---

## HTTP Status Codes

The app expects these standard HTTP status codes:

| Status Code | Meaning | When to Use |
|-------------|---------|-------------|
| 200 | OK | Request succeeded |
| 201 | Created | Resource created successfully |
| 400 | Bad Request | Invalid request data, validation errors |
| 401 | Unauthorized | Missing or invalid authentication |
| 403 | Forbidden | Valid auth but insufficient permissions |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Resource already exists (duplicate) |
| 422 | Unprocessable Entity | Validation failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server-side error |
| 503 | Service Unavailable | Service temporarily down |

---

## Error Code Categories

### AUTH - Authentication Errors (401)
- `AUTH_TOKEN_MISSING`
- `AUTH_TOKEN_INVALID`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_TOKEN_REVOKED`
- `AUTH_CREDENTIALS_INVALID`
- `AUTH_SESSION_EXPIRED`

### OTP - OTP Errors (400, 401)
- `OTP_INVALID`
- `OTP_EXPIRED`
- `OTP_MAX_ATTEMPTS`
- `OTP_GENERATION_FAILED`
- `OTP_SEND_FAILED`

### VALIDATION - Input Validation Errors (400, 422)
- `VALIDATION_ERROR`
- `VALIDATION_PHONE_INVALID`
- `VALIDATION_LICENSE_INVALID`
- `VALIDATION_VEHICLE_NUMBER_INVALID`
- `VALIDATION_REQUIRED_FIELD`
- `VALIDATION_FIELD_LENGTH`
- `VALIDATION_FIELD_FORMAT`

### RESOURCE - Resource Errors (404, 409)
- `RESOURCE_NOT_FOUND`
- `RESOURCE_ALREADY_EXISTS`
- `RESOURCE_CONFLICT`
- `RESOURCE_DELETED`

### PERMISSION - Authorization Errors (403)
- `PERMISSION_DENIED`
- `PERMISSION_INSUFFICIENT`
- `PERMISSION_RESOURCE_ACCESS_DENIED`

### BUSINESS - Business Logic Errors (400)
- `DRIVER_NOT_AVAILABLE`
- `VEHICLE_NOT_AVAILABLE`
- `TRIP_ALREADY_STARTED`
- `TRIP_ALREADY_COMPLETED`
- `BROADCAST_EXPIRED`
- `BROADCAST_FULLY_FILLED`

### RATE_LIMIT - Rate Limiting (429)
- `RATE_LIMIT_EXCEEDED`
- `RATE_LIMIT_OTP_EXCEEDED`
- `RATE_LIMIT_API_EXCEEDED`

### SERVER - Server Errors (500, 503)
- `SERVER_ERROR`
- `DATABASE_ERROR`
- `SERVICE_UNAVAILABLE`
- `THIRD_PARTY_SERVICE_ERROR`

---

## Complete Error Code Reference

### Authentication & Authorization

| Error Code | HTTP | Message | UI Action |
|------------|------|---------|-----------|
| AUTH_TOKEN_MISSING | 401 | Authorization token is required | Force logout |
| AUTH_TOKEN_INVALID | 401 | Invalid authorization token | Force logout |
| AUTH_TOKEN_EXPIRED | 401 | Your session has expired. Please login again. | Attempt token refresh, then logout |
| AUTH_TOKEN_REVOKED | 401 | This session has been terminated | Force logout |
| PERMISSION_DENIED | 403 | You don't have permission to perform this action | Show error toast |
| USER_NOT_FOUND | 404 | User account not found | Navigate to signup |
| USER_SUSPENDED | 403 | Your account has been suspended. Contact support. | Show dialog with support info |
| DRIVER_NOT_REGISTERED | 404 | Driver not found. Contact your transporter. | Show message with instructions |

### OTP Errors

| Error Code | HTTP | Message | UI Action |
|------------|------|---------|-----------|
| OTP_INVALID | 400 | Invalid OTP. X attempts remaining. | Show error, allow retry |
| OTP_EXPIRED | 401 | OTP has expired. Please request a new one. | Enable "Resend OTP" button |
| OTP_MAX_ATTEMPTS | 403 | Too many incorrect attempts. Try again after 1 hour. | Disable OTP input, show countdown |
| OTP_SEND_FAILED | 500 | Failed to send OTP. Please try again. | Show error, enable retry button |
| OTP_RATE_LIMIT | 429 | Too many OTP requests. Try again after X minutes. | Show countdown timer |

### Validation Errors

| Error Code | HTTP | Message | UI Action |
|------------|------|---------|-----------|
| VALIDATION_PHONE_INVALID | 400 | Invalid phone number format | Show inline error on phone field |
| VALIDATION_LICENSE_INVALID | 400 | Invalid license number format | Show inline error on license field |
| VALIDATION_VEHICLE_NUMBER_INVALID | 400 | Invalid vehicle number. Format: XX-00-XX-0000 | Show inline error with format example |
| VALIDATION_REQUIRED_FIELD | 400 | {fieldName} is required | Highlight field in red |
| VALIDATION_FIELD_LENGTH | 400 | {fieldName} must be between X and Y characters | Show inline error |
| VALIDATION_FIELD_FORMAT | 400 | {fieldName} has invalid format | Show inline error with expected format |

### Resource Errors

| Error Code | HTTP | Message | UI Action |
|------------|------|---------|-----------|
| VEHICLE_NOT_FOUND | 404 | Vehicle not found | Show "Vehicle not found" message |
| VEHICLE_ALREADY_EXISTS | 409 | Vehicle with number X already exists | Show inline error on vehicle number field |
| DRIVER_NOT_FOUND | 404 | Driver not found | Show "Driver not found" message |
| DRIVER_ALREADY_EXISTS | 409 | Driver with phone X already registered | Show inline error on phone field |
| TRIP_NOT_FOUND | 404 | Trip not found | Show "Trip not found" message |
| BROADCAST_NOT_FOUND | 404 | Broadcast not found or expired | Refresh broadcast list |

### Business Logic Errors

| Error Code | HTTP | Message | UI Action |
|------------|------|---------|-----------|
| DRIVER_NOT_AVAILABLE | 400 | Driver is not available for assignment | Disable driver in selection list |
| VEHICLE_NOT_AVAILABLE | 400 | Vehicle is already assigned to another trip | Disable vehicle in selection list |
| TRIP_ALREADY_STARTED | 400 | Trip has already been started | Refresh trip details |
| TRIP_ALREADY_COMPLETED | 400 | Trip is already completed | Navigate to trip history |
| TRIP_CANNOT_START | 400 | Trip cannot be started at this time | Show error with reason |
| BROADCAST_EXPIRED | 400 | This broadcast has expired | Remove from list, show message |
| BROADCAST_FULLY_FILLED | 400 | All trucks for this broadcast are filled | Remove from list, show message |
| ASSIGNMENT_ALREADY_ACCEPTED | 400 | You have already accepted this trip | Navigate to active trip |
| ASSIGNMENT_EXPIRED | 400 | This assignment has expired | Remove notification |

### Rate Limiting

| Error Code | HTTP | Message | UI Action |
|------------|------|---------|-----------|
| RATE_LIMIT_EXCEEDED | 429 | Too many requests. Try again after X minutes. | Disable action, show countdown |
| RATE_LIMIT_OTP_EXCEEDED | 429 | Too many OTP requests. Try again after X minutes. | Disable "Send OTP", show countdown |

### Server Errors

| Error Code | HTTP | Message | UI Action |
|------------|------|---------|-----------|
| SERVER_ERROR | 500 | Something went wrong. Please try again. | Show retry button |
| DATABASE_ERROR | 500 | Database error occurred. Please try again later. | Show generic error, log to analytics |
| SERVICE_UNAVAILABLE | 503 | Service temporarily unavailable. Please try later. | Show maintenance message |
| SMS_SERVICE_ERROR | 500 | Failed to send SMS. Please try again. | Show error, enable retry |

---

## UI Error Mapping

### How App Handles Different Errors

```kotlin
fun handleApiError(error: ApiError) {
    when (error.code) {
        // Authentication - Force Logout
        "AUTH_TOKEN_MISSING",
        "AUTH_TOKEN_INVALID",
        "AUTH_TOKEN_REVOKED" -> {
            clearUserData()
            navigateToLogin()
            showToast("Please login again")
        }
        
        // Token Expired - Attempt Refresh
        "AUTH_TOKEN_EXPIRED" -> {
            val refreshed = attemptTokenRefresh()
            if (!refreshed) {
                clearUserData()
                navigateToLogin()
            }
        }
        
        // OTP Errors
        "OTP_INVALID" -> {
            showInlineError(error.message)
            incrementOtpAttempts()
            vibrateDevice()
        }
        
        "OTP_EXPIRED" -> {
            enableResendButton()
            showToast(error.message)
        }
        
        "OTP_MAX_ATTEMPTS" -> {
            disableOtpInput()
            showLockedMessage(error.details?.retryAfter)
        }
        
        // Validation Errors
        "VALIDATION_PHONE_INVALID",
        "VALIDATION_LICENSE_INVALID",
        "VALIDATION_VEHICLE_NUMBER_INVALID" -> {
            showFieldError(error.field, error.message)
            highlightField(error.field)
        }
        
        // Resource Not Found
        "VEHICLE_NOT_FOUND",
        "DRIVER_NOT_FOUND",
        "TRIP_NOT_FOUND" -> {
            showErrorDialog(error.message)
            navigateBack()
        }
        
        // Duplicate Resource
        "VEHICLE_ALREADY_EXISTS",
        "DRIVER_ALREADY_EXISTS" -> {
            showFieldError(error.field, error.message)
        }
        
        // Business Logic
        "DRIVER_NOT_AVAILABLE" -> {
            showToast(error.message)
            refreshDriverList()
        }
        
        "BROADCAST_EXPIRED",
        "BROADCAST_FULLY_FILLED" -> {
            removeFromList(error.details?.broadcastId)
            showToast(error.message)
        }
        
        // Rate Limiting
        "RATE_LIMIT_EXCEEDED" -> {
            disableButton()
            showCountdown(error.details?.retryAfter)
        }
        
        // Server Errors
        "SERVER_ERROR",
        "SERVICE_UNAVAILABLE" -> {
            showErrorState(error.message)
            showRetryButton()
        }
        
        // Default
        else -> {
            showToast(error.message ?: "An error occurred")
            logError(error)
        }
    }
}
```

---

## Validation Error Format

For multiple validation errors, return an array:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed for multiple fields",
    "errors": [
      {
        "field": "vehicleNumber",
        "code": "VALIDATION_VEHICLE_NUMBER_INVALID",
        "message": "Invalid vehicle number format"
      },
      {
        "field": "year",
        "code": "VALIDATION_FIELD_RANGE",
        "message": "Year must be between 2000 and 2026"
      }
    ]
  }
}
```

**UI Handling**:
```kotlin
fun handleValidationErrors(errors: List<ValidationError>) {
    errors.forEach { error ->
        showFieldError(error.field, error.message)
    }
    
    // Scroll to first error
    scrollToField(errors.first().field)
}
```

---

## Network Error Handling

### Connection Errors

**No Internet Connection**:
```kotlin
// UI detects no network
if (!isNetworkAvailable()) {
    showOfflineState()
    showRetryButton()
}
```

**Timeout**:
```kotlin
// Request timeout after 30 seconds
if (error is SocketTimeoutException) {
    showToast("Request timed out. Please try again.")
    showRetryButton()
}
```

**Connection Failed**:
```kotlin
if (error is ConnectException) {
    showToast("Unable to connect to server")
    showRetryButton()
}
```

### SSL/Certificate Errors

```kotlin
if (error is SSLException) {
    showErrorDialog(
        title = "Security Error",
        message = "Unable to establish secure connection. Please check your internet connection."
    )
}
```

---

## Retry Logic

### Automatic Retry (Silent)

**GPS Location Updates**:
- Retry: 3 times
- Backoff: Exponential (1s, 2s, 4s)
- On failure: Log error, continue next update

**Token Refresh**:
- Retry: 1 time
- On failure: Force logout

### Manual Retry (User-Initiated)

**API Failures**:
```kotlin
fun setupRetryButton(onRetry: () -> Unit) {
    retryButton.setOnClickListener {
        // Disable button during retry
        retryButton.isEnabled = false
        retryButton.text = "Retrying..."
        
        // Attempt retry
        onRetry()
        
        // Re-enable after 2 seconds
        handler.postDelayed({
            retryButton.isEnabled = true
            retryButton.text = "Retry"
        }, 2000)
    }
}
```

### Exponential Backoff

```kotlin
fun retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelay: Long = 1000,
    action: suspend () -> Result
): Result {
    var attempt = 0
    var delay = initialDelay
    
    while (attempt < maxAttempts) {
        try {
            return action()
        } catch (e: Exception) {
            attempt++
            if (attempt >= maxAttempts) {
                throw e
            }
            delay(delay)
            delay *= 2  // Exponential backoff
        }
    }
}
```

---

## Error Logging

### Client-Side Logging

```kotlin
fun logError(error: ApiError) {
    // Log to analytics (Firebase, Sentry, etc.)
    analytics.logEvent("api_error", mapOf(
        "error_code" to error.code,
        "endpoint" to error.endpoint,
        "http_status" to error.httpStatus,
        "user_id" to currentUserId,
        "timestamp" to System.currentTimeMillis()
    ))
    
    // Log to console in debug mode
    if (BuildConfig.DEBUG) {
        Log.e("ApiError", "Error: ${error.code} - ${error.message}")
    }
}
```

### Server-Side Logging

**Expected Log Format**:
```json
{
  "timestamp": "2026-01-07T10:30:00.000Z",
  "level": "error",
  "errorCode": "VALIDATION_VEHICLE_NUMBER_INVALID",
  "endpoint": "/vehicles/add",
  "userId": "user_123",
  "requestId": "req_xyz",
  "ipAddress": "192.168.1.1",
  "userAgent": "OkHttp/4.9.0",
  "message": "Invalid vehicle number format",
  "stackTrace": "..."
}
```

---

## Error Prevention Best Practices

### Backend Best Practices

1. **Validate Early**: Validate all inputs before processing
2. **Be Specific**: Provide specific error messages with actionable info
3. **Consistent Format**: Always use standard error response format
4. **Include Context**: Add `details` object for debugging
5. **Log Everything**: Log all errors for monitoring
6. **Graceful Degradation**: Handle partial failures gracefully

### Frontend Best Practices

1. **Validate Before Submit**: Client-side validation before API call
2. **Show Inline Errors**: Display errors next to relevant fields
3. **Provide Guidance**: Show format examples for validation errors
4. **Handle Gracefully**: Never crash on API errors
5. **Retry Intelligently**: Use exponential backoff for retries
6. **Offline Support**: Cache data when offline, sync when online

---

## Error Message Guidelines

### Good Error Messages

✅ **Specific and Actionable**:
- "Vehicle number must be in format XX-00-XX-0000. Example: GJ-01-AB-1234"
- "OTP has expired. Please request a new one."
- "Driver Ramesh Kumar is currently on another trip. Please select a different driver."

✅ **User-Friendly Language**:
- "We couldn't find that vehicle. Please check the vehicle number."
- "Your session has expired. Please login again."

### Bad Error Messages

❌ **Too Technical**:
- "NullPointerException at line 123"
- "FK constraint violation on driver_id"

❌ **Too Vague**:
- "Error occurred"
- "Something went wrong"
- "Invalid input"

❌ **No Guidance**:
- "Validation failed"
- "Request rejected"

---

## Summary: Error Handling Checklist

**Backend Requirements**:
- ✅ Use standard error response format
- ✅ Include specific error codes
- ✅ Provide user-friendly messages
- ✅ Add `field` for validation errors
- ✅ Include `details` for debugging
- ✅ Use correct HTTP status codes
- ✅ Log all errors server-side

**Frontend Requirements**:
- ✅ Handle all error codes gracefully
- ✅ Show inline errors for validation
- ✅ Provide retry mechanisms
- ✅ Handle network errors
- ✅ Log errors for analytics
- ✅ Never expose stack traces to users
- ✅ Maintain user context during errors

---

**Next**: See `07_Performance_and_Scaling_Notes.md` for performance requirements.
