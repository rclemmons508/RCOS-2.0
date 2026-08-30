# RCOS 2.0 - Complete Setup Guide for Demo

This guide walks you through setting up RCOS 2.0 as a fully functional demo app.

## Table of Contents
1. [Firebase Setup](#firebase-setup)
2. [Gemini API Configuration](#gemini-api-configuration)
3. [Building the App](#building-the-app)
4. [Cloud Functions Backend](#cloud-functions-backend)
5. [Demo Data Setup](#demo-data-setup)
6. [Testing & Deployment](#testing--deployment)

---

## Firebase Setup

### Step 1: Create Firebase Project
1. Go to https://console.firebase.google.com
2. Click "Add project"
3. Name it "RCOS-2.0"
4. Accept terms and create

### Step 2: Add Android App
1. In Firebase Console, click "Add app" → Android
2. Package name: `com.rcsolutions.rcos.app`
3. App nickname: RCOS Demo
4. SHA-1 fingerprint: Get yours with:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
5. Download `google-services.json`
6. Place it in `app/google-services.json`

### Step 3: Enable Authentication
1. Go to Firebase Console → Authentication
2. Click "Get started"
3. Enable "Email/Password"
4. Click "Add new provider" → Google
   - Add your OAuth credentials (or skip for now)

### Step 4: Create Firestore Database
1. Go to Firebase Console → Firestore Database
2. Click "Create database"
3. Start in test mode (we'll secure it later)
4. Choose region: us-central1

### Step 5: Enable App Check
1. Go to Firebase Console → App Check
2. Click "Manage apps"
3. Register Android app
4. Choose "reCAPTCHA v3"

---

## Gemini API Configuration

### Step 1: Get API Key
1. Visit https://aistudio.google.com/app/apikeys
2. Click "Create API key in new project"
3. Copy the key

### Step 2: Add to .env File
1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
2. Edit `.env` and replace:
   ```
   GEMINI_API_KEY=your_actual_key_here
   ```

### Step 3: Verify Build Configuration
The app uses the Secrets Gradle Plugin to inject .env values:
- Build configuration already reads from `.env`
- No additional setup needed

---

## Building the App

### Step 1: Prerequisites
```bash
# Install Android SDK (via Android Studio or command line)
# Minimum requirements:
# - SDK API 34 (Android 14)
# - Build Tools 35.0.0+
# - NDK (optional)

# Check Java version
java -version
# Should be Java 11+
```

### Step 2: Build Debug APK
```bash
cd RCOS-2.0
./gradlew clean build

# If successful, APK is at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Install on Device
```bash
# Connect Android device (USB debugging enabled)
adb devices

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or use Android Studio to run directly
# Click Run → Run 'app'
```

---

## Cloud Functions Backend

See `BACKEND_SETUP.md` for detailed Cloud Functions configuration.

---

## Demo Data Setup

See `DEMO_DATA_SETUP.md` for loading sample data into Firestore.

---

## Testing & Deployment

### Test Checklist
- [ ] App launches without crashes
- [ ] Login/registration works
- [ ] Onboarding dialog appears
- [ ] Chat with Gemini works
- [ ] Dashboard displays data
- [ ] No permission errors

### Build Release APK
```bash
# Create signing key (one time only)
keytool -genkey -v -keystore ~/rcos-release.keystore \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias rcos-key

# Update app/build.gradle.kts with key location and passwords
# Then build release:
./gradlew assembleRelease

# APK is at: app/build/outputs/apk/release/app-release.apk
```

---

## Troubleshooting

### APK Build Fails
```bash
# Clear cache
./gradlew clean

# Rebuild
./gradlew assembleDebug
```

### App Crashes on Launch
- Check logcat: `adb logcat | grep -i error`
- Verify google-services.json exists in app/
- Check .env file has GEMINI_API_KEY

### Firebase Connection Issues
- Verify internet permission in AndroidManifest.xml
- Check Firebase project ID matches google-services.json
- Ensure device has working internet connection

### Gemini API Errors
- Verify API key is correct
- Check API quota at https://aistudio.google.com/app/usage
- Ensure API key has access to Generative AI API

---

## Next Steps
1. Complete Firebase Setup
2. Add google-services.json to app/
3. Configure Gemini API key
4. Build and test debug APK
5. Deploy Cloud Functions
6. Load demo data
7. Create release APK
