# RCOS 2.0 Installation Checklist

Follow this checklist to get RCOS 2.0 fully functional with Firebase integration.

## Phase 1: Prerequisites (5 minutes)

- [ ] Android Studio installed
- [ ] Git installed
- [ ] Node.js 18+ installed
- [ ] Firebase CLI installed: `npm install -g firebase-tools`
- [ ] Gradle 8.5+ available

## Phase 2: Firebase Console Setup (15 minutes)

### Create Firebase Project
- [ ] Go to https://console.firebase.google.com
- [ ] Create new project: `RCOS-2.0-Production`
- [ ] Enable Google Analytics (optional)
- [ ] Wait for project initialization

### Add Android App
- [ ] Click "Add App" → Android
- [ ] Package name: `com.rcsolutions.rcos.app`
- [ ] Get SHA-1: Run `./gradlew signingReport`
- [ ] Download `google-services.json`
- [ ] Place in `app/google-services.json`

### Enable Firebase Services

**Authentication:**
- [ ] Go to Build → Authentication
- [ ] Click "Get Started"
- [ ] Enable "Email/Password"
- [ ] Save

**Firestore Database:**
- [ ] Go to Build → Firestore Database
- [ ] Click "Create Database"
- [ ] Security rules: "Production Mode"
- [ ] Region: "us-central1"
- [ ] Create

**Cloud Functions:**
- [ ] Go to Build → Functions
- [ ] Click "Get Started" (we'll deploy from command line)

### Get Service Account Key
- [ ] Go to Project Settings (⚙️)
- [ ] Click "Service Accounts"
- [ ] Click "Generate New Private Key"
- [ ] Save as `service-account-key.json` in project root
- [ ] Add to `.gitignore`: `echo "service-account-key.json" >> .gitignore`

## Phase 3: Gemini API Setup (5 minutes)

- [ ] Go to https://aistudio.google.com/app/apikeys
- [ ] Click "Create API key"
- [ ] Copy the API key
- [ ] Create `.env` file in project root:
  ```bash
  cp .env.example .env
  ```
- [ ] Edit `.env` and add your key:
  ```
  GEMINI_API_KEY=your_key_here
  ```
- [ ] Add `.env` to `.gitignore` (already done)

## Phase 4: Deploy Cloud Functions (10 minutes)

```bash
# Login to Firebase
firebase login

# Initialize functions (if needed)
firebase init functions

# Install dependencies
cd functions
npm install @google/generative-ai
cd ..

# Set API key in Firebase environment
firebase functions:config:set gemini.api_key="your_key_here"

# Deploy functions
firebase deploy --only functions

# Verify deployment
firebase functions:list
```

- [ ] All 6 functions deployed successfully
- [ ] No deployment errors in console

## Phase 5: Deploy Firestore Rules (2 minutes)

```bash
firebase deploy --only firestore:rules
```

- [ ] Rules deployed successfully

## Phase 6: Build Android App (10 minutes)

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug
```

- [ ] Build completes without errors
- [ ] APK created at: `app/build/outputs/apk/debug/app-debug.apk`

## Phase 7: Install on Device (5 minutes)

```bash
# Enable USB Debugging on device
# Settings → Developer Options → USB Debugging → ON

# Connect device via USB
adb devices

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

- [ ] Device listed by `adb devices`
- [ ] APK installed successfully
- [ ] App appears on device home screen

## Phase 8: Test Application (10 minutes)

### Login Test
- [ ] App opens to Login screen (NOT auto-logged-in)
- [ ] Can type email and password
- [ ] Click "Sign In to RCOS" button
- [ ] Login succeeds with valid credentials
- [ ] Successfully logged in and see Dashboard

### Register Test
- [ ] Click "Register Account" on Login screen
- [ ] Fill in registration form
- [ ] Click "Create Account & Provision RCOS"
- [ ] New account created successfully
- [ ] Can login with new credentials

### Chat Test (Most Important)
- [ ] Navigate to Chat screen
- [ ] Type a message: "Hello, what can you do?"
- [ ] Click send button
- [ ] **WAIT 2-5 seconds** (real API call)
- [ ] **Response appears from Gemini AI** (NOT instant fake response)
- [ ] Response is unique and relevant
- [ ] Send multiple messages and build conversation

### Firestore Test
- [ ] Open Firebase Console
- [ ] Go to Firestore → Data
- [ ] Expand `users` collection
- [ ] See your user document
- [ ] See subcollections: `chatMessages`, `jobs`, `auditLogs`
- [ ] Chat messages are saved

### Cloud Functions Test
- [ ] Open Firebase Console
- [ ] Go to Build → Functions
- [ ] View function execution logs
- [ ] See successful invocations

## Phase 9: Verify Security (5 minutes)

- [ ] Cannot access other users' data
- [ ] Logout and login as different user
- [ ] Each user only sees their own data
- [ ] Audit logs are protected

## Phase 10: Cleanup (2 minutes)

- [ ] Remove `.env` from version control
- [ ] Remove `service-account-key.json` from version control
- [ ] Verify `.gitignore` contains both files
- [ ] Never commit secrets to git

## Troubleshooting

If something fails:

1. **App won't build:**
   - [ ] Run `./gradlew clean` and try again
   - [ ] Check `google-services.json` exists in `app/`
   - [ ] Verify gradle version: `./gradlew --version`

2. **App crashes on launch:**
   - [ ] Check logs: `adb logcat | grep "com.rcsolutions.rcos.app"`
   - [ ] Verify `google-services.json` is valid
   - [ ] Verify Firebase services are enabled

3. **Chat doesn't work:**
   - [ ] Check `.env` file has GEMINI_API_KEY
   - [ ] Verify API key is valid
   - [ ] Check API quota at https://aistudio.google.com/app/usage
   - [ ] View Cloud Function logs: `firebase functions:log`

4. **Login fails:**
   - [ ] Verify internet connection: `adb shell ping google.com`
   - [ ] Check Firestore security rules: `firebase deploy --only firestore:rules`
   - [ ] Verify authentication is enabled in Firebase Console

5. **Firebase connection fails:**
   - [ ] Check `AndroidManifest.xml` has internet permission
   - [ ] Verify Firebase project ID in `google-services.json`
   - [ ] Test Firestore access in Firebase Console

## Success Criteria

✅ **You're done when:**
- [ ] App launches to Login screen (no auto-login)
- [ ] Can register and login with real Firebase Auth
- [ ] Chat sends message and gets real Gemini response (2-5 second delay)
- [ ] User data appears in Firestore
- [ ] Cloud Functions execute successfully
- [ ] Firestore security rules protect user data
- [ ] No mock/fake data in use

## Next Steps

1. Build release APK:
   ```bash
   ./gradlew assembleRelease
   ```

2. Submit to Google Play Store
   - Create Google Play Developer account
   - Upload release APK
   - Fill in app details
   - Submit for review

3. Set up monitoring:
   - Enable Firestore monitoring
   - Set up Cloud Functions alerts
   - Monitor API quota usage

Congratulations! RCOS 2.0 is now fully functional! 🎉
