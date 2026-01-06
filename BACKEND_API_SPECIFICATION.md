# 🚛 Weelo Logistics - Backend API Specification

## 📋 Overview

This document provides complete backend API specifications for the Weelo Logistics notification and GPS tracking system. The system handles:

1. **Broadcast Flow**: Customer → Transporter (multiple trucks needed)
2. **Assignment Flow**: Transporter → Driver (truck + driver assignment)
3. **Notification Flow**: Real-time notifications to drivers (full-screen alarm)
4. **GPS Tracking Flow**: Real-time location tracking after driver acceptance
5. **Customer Tracking**: Live location sharing to customer

---

## 🎯 System Flow Summary

```
CUSTOMER CREATES BOOKING
         ↓
    BROADCAST SENT TO TRANSPORTERS (Push/WebSocket)
         ↓
TRANSPORTER VIEWS BROADCAST
    - Sees truck type & quantity needed
    - Checks availability
         ↓
TRANSPORTER SELECTS TRUCKS
    - Can take partial (e.g., 3 out of 10 trucks)
    - Assigns drivers to each truck
         ↓
DRIVER NOTIFICATION (ALARM - FULL SCREEN)
    - Sound + Vibration
    - Shows trip details
    - Accept/Decline options
         ↓
DRIVER ACCEPTS
    - GPS tracking starts automatically
    - Location shared every 5-10 seconds
         ↓
CUSTOMER SEES LIVE LOCATION
    - Real-time driver position on map
    - ETA updates
         ↓
TRIP COMPLETION
    - Driver marks as complete
    - Tracking stops
```

---

## 🏗️ Architecture Principles

### Modularity
- Each endpoint has single responsibility
- Separated concerns: Broadcast, Assignment, Notification, Tracking
- Independent microservices possible

### Scalability
- WebSocket for real-time communication
- Message queues (Redis/RabbitMQ) for notifications
- Horizontal scaling support
- Database sharding ready

### Security
- JWT authentication on all endpoints
- Role-based access control (RBAC)
- Encrypted GPS coordinates
- Rate limiting
- Input validation

### Easy Understanding
- RESTful conventions
- Clear naming
- Comprehensive examples
- Status codes explained

---

## 🔐 Authentication

All endpoints require authentication via JWT token in header:

```http
Authorization: Bearer <jwt_token>
```

### User Roles
- `CUSTOMER` - Creates broadcasts
- `TRANSPORTER` - Receives broadcasts, assigns drivers
- `DRIVER` - Receives assignments, shares location
- `ADMIN` - System administration

---

## 📡 Base URL

```
Production: https://api.weelologistics.com/v1
Staging: https://staging-api.weelologistics.com/v1
Development: http://localhost:3000/api/v1
```

---

## 🔔 Real-time Communication Setup

### WebSocket Connection

```javascript
// Connect to WebSocket
const ws = new WebSocket('wss://api.weelologistics.com/ws');

// Authenticate after connection
ws.send(JSON.stringify({
  type: 'authenticate',
  token: 'jwt_token_here'
}));

// Listen for events
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  switch(data.type) {
    case 'broadcast_new':
      // New broadcast for transporter
      break;
    case 'driver_assignment':
      // Driver assigned to trip
      break;
    case 'location_update':
      // Driver location update
      break;
  }
};
```

---

## 📊 Response Format

All API responses follow this structure:

### Success Response
```json
{
  "success": true,
  "data": { /* response data */ },
  "message": "Operation successful",
  "timestamp": "2026-01-05T10:30:00Z"
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": { /* additional error details */ }
  },
  "timestamp": "2026-01-05T10:30:00Z"
}
```

### HTTP Status Codes
- `200` - Success
- `201` - Created
- `400` - Bad Request (validation error)
- `401` - Unauthorized (authentication required)
- `403` - Forbidden (insufficient permissions)
- `404` - Not Found
- `409` - Conflict (duplicate/state mismatch)
- `429` - Too Many Requests (rate limited)
- `500` - Internal Server Error

---

## 📖 Table of Contents

1. [Broadcast Management APIs](#1-broadcast-management-apis)
2. [Transporter Assignment APIs](#2-transporter-assignment-apis)
3. [Driver Notification APIs](#3-driver-notification-apis)
4. [GPS Tracking APIs](#4-gps-tracking-apis)
5. [Customer Tracking APIs](#5-customer-tracking-apis)
6. [WebSocket Events](#6-websocket-events)
7. [Push Notifications](#7-push-notifications)
8. [Data Models](#8-data-models)
9. [Error Codes](#9-error-codes)
10. [Testing Guide](#10-testing-guide)

---

**Next Sections**: Detailed endpoint specifications follow...
