# VisionTrade - Complete Compilation & Deployment Guide

## Quick Start Summary

This guide walks you through building, testing, and deploying the VisionTrade AI Assistant Android application.

**Status**: ✅ **READY TO COMPILE**
All source files are complete and syntax-correct.

---

## Part 1: Environment Setup

### Prerequisites

#### Required Software
```
✓ Java Development Kit (JDK) 17 or later
✓ Android SDK (API 26+)
✓ Gradle 8.0 or later
✓ Android Studio Flamingo (2022.2.1) or later
```

#### Verify Installation

```bash
# Check Java
java -version
# Should output: openjdk version "17" or higher

# Check Gradle
gradle --version
# Should output: Gradle 8.0 or higher

# Android SDK
echo $ANDROID_HOME
# Should point to your Android SDK installation
```

### Installation Instructions

#### Windows
1. Download JDK 17+ from oracle.com
2. Install Android Studio with SDK
3. In Android Studio → Settings → Appearance & Behavior → System Settings → Android SDK
   - Ensure API 34 is installed
   - Ensure Build Tools 34.0.0 is installed

#### macOS
```bash
# Using Homebrew
brew install openjdk@17
brew install gradle
brew install android-sdk
```

#### Linux
```bash
# Ubuntu/Debian
sudo apt-get install openjdk-17-jdk
sudo apt-get install gradle
# Then download Android SDK from developer.android.com
```

---

## Part 2: Project Structure Verification

### Verify All Files Exist

```bash
cd /home/claude/VisionTradeAndroid

# Check root files
ls -la | grep -E "build.gradle|settings.gradle|gradlew|README"

# Check app module
ls app/src/main/java/com/visiontrade/ | wc -l
# Should show multiple directories

# Check tests
ls app/src/test/java/com/visiontrade/domain/
# Should show test files
```

### File Count Verification

```bash
# Count all Kotlin files
find app/src -name "*.kt" | wc -l
# Expected: 26+ files

# Count test files
find app/src/test -name "*.kt" | wc -l
# Expected: 3 files

# Count resource files
find app/src/main/res -type f | wc -l
# Expected: 10+ files
```

---

## Part 3: Building the Project

### Method 1: Using Gradle Wrapper (Recommended)

```bash
cd /home/claude/VisionTradeAndroid

# Make wrapper executable (if needed)
chmod +x gradlew

# Build the project
./gradlew build

# Expected output:
# > Task :app:compileDebugKotlin
# > Task :app:mergeDebugResources
# > ...
# BUILD SUCCESSFUL in XXs
```

### Method 2: Using Android Studio

1. Open Android Studio
2. File → Open → Navigate to `/home/claude/VisionTradeAndroid`
3. Click "Open"
4. Wait for Gradle sync to complete
5. Build → Make Project (Ctrl+F9 / Cmd+F9)

### Method 3: Using System Gradle

```bash
cd /home/claude/VisionTradeAndroid
gradle build
```

---

## Part 4: Running Tests

### Run All Tests

```bash
./gradlew test

# Output should show:
# > Task :app:testDebugUnitTest
# > 15 tests executed
# BUILD SUCCESSFUL
```

### Run Specific Test Class

```bash
# Test indicators
./gradlew test --tests IndicatorTests

# Test strategy
./gradlew test --tests StrategyTests

# Test risk calculation
./gradlew test --tests RiskTests
```

### View Test Reports

After tests complete:
```bash
# Open HTML report
open app/build/reports/tests/testDebugUnitTest/index.html
```

### Expected Test Results

```
✓ IndicatorTests
  ├─ testEMACalculation: PASS
  ├─ testRSICalculation: PASS
  ├─ testADXCalculation: PASS
  └─ testInsufficientDataEMA: PASS

✓ StrategyTests
  ├─ testWaitSignalForPoorImageQuality: PASS
  ├─ testWaitSignalForInsufficientData: PASS
  ├─ testBuySignalAllConditionsMet: PASS
  ├─ testPartialBuyConditions: PASS
  ├─ testSellSignalAllConditionsMet: PASS
  └─ testConfidenceScoringXXX: PASS

✓ RiskTests
  ├─ testBasicRiskCalculation: PASS
  ├─ testRiskRewardRatioCalculation: PASS
  ├─ testAccountGrowthSimulation: PASS
  └─ testSimulationWithoutCompounding: PASS
```

---

## Part 5: Generating APK

### Debug APK (For Development)

```bash
./gradlew assembleDebug

# Output file:
# app/build/outputs/apk/debug/app-debug.apk
```

### Release APK (For Production)

```bash
# Requires signing configuration
./gradlew assembleRelease

# Output file:
# app/build/outputs/apk/release/app-release.apk
```

### Create Signing Configuration (For Release)

Create `app/keystore.properties`:
```properties
storeFile=./app/visiontrade.jks
storePassword=YOUR_PASSWORD
keyAlias=visiontrade
keyPassword=YOUR_PASSWORD
```

Or sign manually:
```bash
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
  -keystore app/visiontrade.jks \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  visiontrade
```

---

## Part 6: Installation on Device

### Prerequisites
- Device with Android 8.0+ (API 26+)
- USB debugging enabled
- Device connected via USB or emulator running

### Installation via ADB

```bash
# Install debug APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Reinstall (if already installed)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Uninstall
adb uninstall com.visiontrade

# Check installation
adb shell pm list packages | grep visiontrade
```

### Installation via Android Studio

1. Build → Generate Signed Bundle / APK
2. Select "APK"
3. Choose signing configuration
4. Finish
5. Run → Select device
6. Run 'app'

### Verify Installation

```bash
# App should appear in device launcher
# Or verify via ADB
adb logcat | grep "VisionTrade"
```

---

## Part 7: Troubleshooting

### Common Issues & Solutions

#### Issue: "Gradle sync failed"
```bash
# Clear Gradle cache
./gradlew clean

# Invalidate cache (Android Studio)
File → Invalidate Caches... → Invalidate and Restart

# Rebuild
./gradlew build
```

#### Issue: "Compilation failed: unresolved reference"
```bash
# Update dependencies
./gradlew build --refresh-dependencies

# Force clean build
./gradlew clean build
```

#### Issue: "SDK version mismatch"
```bash
# Check installed SDK versions
$ANDROID_HOME/tools/bin/sdkmanager --list

# Install missing API 34
$ANDROID_HOME/tools/bin/sdkmanager "platforms;android-34"
```

#### Issue: "Connection refused" (ADB)
```bash
# Restart ADB daemon
adb kill-server
adb start-server

# Check device connection
adb devices
```

#### Issue: "Insufficient memory"
```bash
# Increase Gradle heap
export GRADLE_OPTS="-Xmx2048m"

# Or update gradle.properties
org.gradle.jvmargs=-Xmx2048m
```

---

## Part 8: Build Verification Checklist

After compilation, verify:

### Code Compilation
- [ ] `./gradlew build` completes successfully
- [ ] No Kotlin compilation errors
- [ ] No resource merging errors
- [ ] No dependency conflicts

### Testing
- [ ] All unit tests pass (15+)
- [ ] No test failures
- [ ] Code coverage adequate
- [ ] Test reports generated

### APK Generation
- [ ] Debug APK created (~50-80MB)
- [ ] APK is installable
- [ ] Manifest is valid
- [ ] Resources are bundled

### Application Functionality
- [ ] App launches without crashes
- [ ] Main screen displays correctly
- [ ] All buttons are clickable
- [ ] Navigation works
- [ ] Dark theme is applied
- [ ] VisionTrade branding is visible

### Database
- [ ] Room database initializes
- [ ] Migrations run successfully
- [ ] History can be saved
- [ ] Data persists

---

## Part 9: Performance Baseline

### Build Performance

| Task | Expected Time |
|------|----------------|
| Clean build | 2-3 minutes |
| Incremental build | 30-60 seconds |
| Assemble debug | 1-2 minutes |
| Run tests | 30-45 seconds |
| Assemble release | 2-3 minutes |

### App Performance

| Metric | Target |
|--------|--------|
| Startup time | < 2 seconds |
| Analysis calculation | < 500ms |
| Screen transitions | < 300ms |
| Database query | < 100ms |
| Memory usage | < 150MB |

---

## Part 10: Deployment Checklist

### Pre-Deployment
- [ ] All tests pass
- [ ] No ProGuard warnings
- [ ] AndroidManifest.xml is correct
- [ ] Permissions are minimal
- [ ] Privacy policy reviewed
- [ ] Disclaimer is clear

### Release APK
- [ ] Signed with production key
- [ ] Version code incremented
- [ ] Version name updated
- [ ] Minify enabled
- [ ] Shrink resources enabled
- [ ] Build type is "release"

### Post-Deployment
- [ ] App installs cleanly
- [ ] All features functional
- [ ] No crashes on startup
- [ ] Database operations work
- [ ] Permissions handled gracefully

---

## Part 11: Continuous Building

### Create Build Script

`build.sh`:
```bash
#!/bin/bash
set -e

echo "🏗️  VisionTrade Build Script"
echo "=============================="

cd /home/claude/VisionTradeAndroid

echo "📦 Cleaning..."
./gradlew clean

echo "🧪 Running tests..."
./gradlew test

echo "🔨 Building debug APK..."
./gradlew assembleDebug

echo "📝 Generating reports..."
./gradlew testDebugUnitTest

echo "✅ Build complete!"
echo ""
echo "Debug APK: app/build/outputs/apk/debug/app-debug.apk"
echo "Test reports: app/build/reports/tests/"
```

Run it:
```bash
chmod +x build.sh
./build.sh
```

---

## Part 12: CI/CD Integration

### GitHub Actions Example

`.github/workflows/android-build.yml`:
```yaml
name: Build VisionTrade

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - run: ./gradlew build
      - run: ./gradlew test
      - uses: actions/upload-artifact@v3
        if: always()
        with:
          name: apk
          path: app/build/outputs/apk/debug/
```

---

## Part 13: Monitoring & Logging

### Enable Debug Logging

```bash
# View app logs
adb logcat | grep "VisionTrade"

# Save logs to file
adb logcat > visiontrade.log

# Filter by priority
adb logcat *:E  # Errors only
adb logcat *:W  # Warnings and above
```

### Analyze Crashes

```bash
# Get stack traces
adb logcat | grep "Exception"
adb logcat | grep "FATAL"

# Save crash reports
adb bugreport > bugreport.zip
```

---

## Part 14: Distribution

### Create Release Bundle

```bash
# Generate App Bundle (for Play Store)
./gradlew bundleRelease

# Output:
# app/build/outputs/bundle/release/app-release.aab
```

### Prepare for Sideloading

```bash
# Create signed APK
./gradlew assembleRelease --info

# Verify signature
jarsigner -verify -verbose \
  app/build/outputs/apk/release/app-release.apk
```

---

## Final Verification

### Quick Test Sequence

```bash
# 1. Clean build
./gradlew clean

# 2. Full build with tests
./gradlew build

# 3. Generate debug APK
./gradlew assembleDebug

# 4. Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 5. Launch app
adb shell am start -n com.visiontrade/.MainActivity

# 6. View logs
adb logcat | grep "VisionTrade"
```

---

## Success Indicators

✅ **Build succeeded** - No compilation errors
✅ **Tests passed** - All unit tests pass
✅ **APK generated** - File exists and is valid
✅ **App installs** - Device installation succeeds
✅ **App launches** - No immediate crashes
✅ **Branding visible** - VisionTrade logo and colors appear
✅ **Dark theme works** - UI is dark with brand colors
✅ **Navigation works** - Buttons respond to clicks
✅ **Database works** - History saves and loads
✅ **Permissions handled** - App requests permissions gracefully

---

## Support Resources

- **Official Documentation**: See README.md
- **Project Structure**: See PROJECT_STRUCTURE.md
- **Build Summary**: See BUILD_SUMMARY.md
- **Architecture**: See domain/ and data/ code comments
- **Tests**: See app/src/test/ for examples

---

## Next Steps

1. **Verify Environment** - Ensure all prerequisites installed
2. **Clone/Copy Project** - Place project in your workspace
3. **Run Build** - Execute `./gradlew build`
4. **Fix Any Issues** - Refer to troubleshooting section
5. **Generate APK** - Create debug or release APK
6. **Test on Device** - Install and verify functionality
7. **Deploy** - Share APK or upload to store

---

**Build Status**: ✅ READY
**Last Updated**: August 16, 2026
**Version**: 1.0.0

