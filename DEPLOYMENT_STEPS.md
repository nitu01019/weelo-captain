# Deployment Steps - Driver Real-Time Updates

## ✅ What's Already Done

### Backend Code Changes
- ✅ Added driver events to socket service
- ✅ Added real-time emission when driver is created
- ✅ TypeScript compiled successfully
- ✅ Docker image built successfully

### Android App Code Changes
- ✅ Added driver event listeners to SocketIOService
- ✅ Added event handlers for driver_added and drivers_updated
- ✅ Updated TransporterDashboard to listen and update UI
- ✅ Data classes created for notifications

---

## 🚀 Deployment Steps

### Step 1: Push Docker Image to ECR

```bash
cd /Users/nitishbhardwaj/Desktop/Weelo-backend

# Login to ECR
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 318774499084.dkr.ecr.ap-south-1.amazonaws.com

# Build and push (already running in background)
docker buildx build --platform linux/amd64 --target production --push -t 318774499084.dkr.ecr.ap-south-1.amazonaws.com/weelo-backend:latest .
```

### Step 2: Update ECS Service

```bash
# Force new deployment to pull latest image
aws ecs update-service \
  --cluster weelocluster \
  --service weelobackendtask-service-joxh3c0r \
  --force-new-deployment \
  --region ap-south-1
```

### Step 3: Build Android APK

```bash
cd "/Users/nitishbhardwaj/Desktop/Weelo captain"

# Build debug APK
./gradlew assembleDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: Install and Test

1. Install APK on device/emulator
2. Login as transporter
3. Go to Dashboard - note driver count
4. Navigate to "Add Driver"
5. Add a new driver
6. Return to Dashboard
7. **✅ VERIFY:** Driver count updates instantly!

---

## 🧪 Testing Checklist

- [ ] Backend deployed to ECS
- [ ] Android app installed with new changes
- [ ] WebSocket connected (check logs)
- [ ] Add driver from app
- [ ] Dashboard count updates immediately (no refresh needed)
- [ ] Check logs for: "👤 Driver added: [name]"
- [ ] Check logs for: "📊 New driver count: [count]"

---

## 📊 Expected Behavior

### Before Fix
```
Add Driver → Success message → Return to Dashboard → OLD COUNT (need manual refresh)
```

### After Fix
```
Add Driver → Success message → Return to Dashboard → NEW COUNT (instant update via WebSocket)
```

---

## 🔍 Troubleshooting

### Issue: Count not updating

**Check 1: WebSocket Connected?**
```
Check logs for: "✅ WebSocket connected - Ready for broadcasts"
```

**Check 2: Event received?**
```
Check logs for: "👤 Driver added: [name]"
```

**Check 3: Backend deployed?**
```
aws ecs describe-services --cluster weelocluster --services weelobackendtask-service-joxh3c0r --region ap-south-1 --query 'services[0].deployments'
```

### Issue: WebSocket not connecting

**Check backend logs:**
```
aws logs tail weelobackendtask --since 5m --region ap-south-1 --follow
```

---

## ✅ Success Criteria

1. ✅ Backend built and compiled
2. ⏳ Docker image pushed to ECR
3. ⏳ ECS service updated
4. ⏳ Android APK built
5. ⏳ Driver count updates instantly on dashboard

---

**Status:** Code complete, deployment in progress
