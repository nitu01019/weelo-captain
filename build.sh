#!/bin/bash

###############################################################################
# Weelo Captain App - Build Script
# 
# This script builds the Android app using Gradle command line
# No need to open Android Studio - just run this script!
#
# REQUIREMENTS:
# - Android Studio installed (for JDK)
# - Android SDK installed at: ~/Library/Android/sdk
#
# USAGE:
#   ./build.sh            # Build debug APK
#   ./build.sh release    # Build release APK (requires signing)
#   ./build.sh clean      # Clean build
###############################################################################

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ANDROID_SDK="$HOME/Library/Android/sdk"

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   Weelo Captain - Build Script        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo ""

# Check Java/JDK
if [ ! -d "$JAVA_HOME" ]; then
    echo -e "${RED}❌ Error: Android Studio JDK not found!${NC}"
    echo -e "${YELLOW}Please install Android Studio first.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ JDK found: $JAVA_HOME${NC}"

# Check Android SDK
if [ ! -d "$ANDROID_SDK" ]; then
    echo -e "${RED}❌ Error: Android SDK not found!${NC}"
    echo -e "${YELLOW}Expected location: $ANDROID_SDK${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Android SDK found: $ANDROID_SDK${NC}"

# Export JAVA_HOME
export JAVA_HOME="$JAVA_HOME"
export ANDROID_HOME="$ANDROID_SDK"

echo ""
echo -e "${BLUE}Starting build...${NC}"
echo ""

# Determine build type
BUILD_TYPE=${1:-debug}

case "$BUILD_TYPE" in
    clean)
        echo -e "${YELLOW}🧹 Cleaning build...${NC}"
        ./gradlew clean
        echo -e "${GREEN}✓ Clean complete!${NC}"
        ;;
    debug)
        echo -e "${YELLOW}🔨 Building DEBUG APK...${NC}"
        ./gradlew assembleDebug
        
        if [ $? -eq 0 ]; then
            echo ""
            echo -e "${GREEN}╔════════════════════════════════════════╗${NC}"
            echo -e "${GREEN}║   ✓ BUILD SUCCESSFUL!                  ║${NC}"
            echo -e "${GREEN}╚════════════════════════════════════════╝${NC}"
            echo ""
            echo -e "${BLUE}📦 APK Location:${NC}"
            echo -e "   app/build/outputs/apk/debug/app-debug.apk"
            echo ""
            
            # Show APK size
            APK_SIZE=$(du -h app/build/outputs/apk/debug/app-debug.apk | cut -f1)
            echo -e "${BLUE}📊 APK Size: ${GREEN}$APK_SIZE${NC}"
            echo ""
            
            # Show installation command
            echo -e "${YELLOW}To install on device:${NC}"
            echo -e "   adb install app/build/outputs/apk/debug/app-debug.apk"
            echo ""
        else
            echo -e "${RED}❌ Build failed!${NC}"
            exit 1
        fi
        ;;
    release)
        echo -e "${YELLOW}🔨 Building RELEASE APK...${NC}"
        echo -e "${YELLOW}⚠️  Note: Release builds require signing configuration${NC}"
        ./gradlew assembleRelease
        
        if [ $? -eq 0 ]; then
            echo ""
            echo -e "${GREEN}✓ Release build successful!${NC}"
            echo -e "${BLUE}📦 APK Location:${NC}"
            echo -e "   app/build/outputs/apk/release/app-release.apk"
            echo ""
        else
            echo -e "${RED}❌ Build failed!${NC}"
            exit 1
        fi
        ;;
    *)
        echo -e "${RED}❌ Invalid build type: $BUILD_TYPE${NC}"
        echo -e "${YELLOW}Usage: ./build.sh [clean|debug|release]${NC}"
        exit 1
        ;;
esac

echo -e "${BLUE}═══════════════════════════════════════${NC}"
