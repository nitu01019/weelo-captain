#!/bin/bash
# Clean build script for Weelo Captain

echo "🧹 Cleaning up..."
rm -rf app/build .gradle build
pkill -9 -f gradle

echo "☕ Setting up Java..."
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

echo "🛑 Stopping Gradle daemon..."
./gradlew --stop

echo "🏗️ Building app..."
./gradlew clean assembleDebug --no-daemon --stacktrace

echo ""
echo "✅ Build complete!"
ls -lh app/build/outputs/apk/debug/app-debug.apk
