# Weelo Logistics - Unified App

**Version:** 2.0  
**Platform:** Android (Kotlin)  
**Architecture:** MVVM + Jetpack Compose  
**Package:** `com.weelo.logistics`

## 🎯 Overview

Single unified app for Transporters and Drivers with role-based access and seamless role switching.

## 🏗️ Architecture

- **UI Layer:** Jetpack Compose
- **Architecture Pattern:** MVVM (Model-View-ViewModel)
- **Navigation:** Jetpack Navigation Component
- **Dependency Injection:** Hilt
- **Data:** Room Database + Mock Repositories

## 📁 Project Structure

```
app/
├── data/               # Data models, repositories
├── domain/             # Business logic (optional)
├── ui/                 # All UI components
│   ├── theme/          # Theme, colors, typography
│   ├── components/     # Reusable components
│   ├── auth/           # Login, signup, onboarding
│   ├── transporter/    # Transporter screens
│   ├── driver/         # Driver screens
│   └── shared/         # Shared screens
└── utils/              # Helper classes
```

## 🚀 Features

### Transporter Role
- Fleet Management
- Driver Management
- Trip Assignment
- Live Tracking
- Reports & Analytics

### Driver Role
- Trip Management
- GPS Tracking
- Earnings Tracking
- Navigation

### Dual Role
- Role Switching
- Combined Dashboard
- Unified Profile

## 🎨 Design System

- **Primary Color:** #FF6B35 (Orange)
- **Secondary Color:** #2196F3 (Blue)
- **Typography:** System Default (Roboto)
- **Components:** Material Design 3

## 📦 Dependencies

See `build.gradle` files for complete dependency list.

## 🧪 Testing

Mock data repositories included for UI testing without backend.

---

**Created:** January 2026  
**Last Updated:** January 2026
