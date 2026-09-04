# Firebase Setup Guide for RCOS 2.0

This guide walks you through setting up a fully functional RCOS 2.0 application with Firebase, Gemini API, and Cloud Functions.

## Table of Contents
1. [Firebase Project Creation](#firebase-project-creation)
2. [Enable Firebase Services](#enable-firebase-services)
3. [Get Credentials](#get-credentials)
4. [Gemini API Setup](#gemini-api-setup)
5. [Deploy Cloud Functions](#deploy-cloud-functions)
6. [Update Firestore Rules](#update-firestore-rules)
7. [Build and Run](#build-and-run)
8. [Testing](#testing)

---

## Firebase Project Creation

### Step 1: Create Firebase Project
1. Go to **https://console.firebase.google.com**
2. Click **"Create Project"** or **"Add Project"**
3. Project name: `RCOS-2.0-Production`
4. Click **"Continue"**
5. Accept Google Analytics prompt (optional)
6. Click **"Create Project"**
7. Wait 1-2 minutes for project to initialize

### Step 2: Add Android App
1. In Firebase Console, click **"Get Started"** or **"Add App"**
2. Select **"Android"** platform
3. Fill in details:
   - Package name: `com.rcsolutions.rcos.app`
   - App nickname: `RCOS Demo`
   - SHA-1 fingerprint: (See below)
4. Click **"Register App"**
5. Download `google-services.json`
6. Copy to `app/google-services.json` in your project

**To get SHA-1 fingerprint:**
```bash
./gradlew signingReport
# Look for "SHA1" under the "debug" variant
# Example: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90
```

---

## Enable Firebase Services

### Authentication
1. In Firebase Console, go to **Build → Authentication**
2. Click **"Get Started"**
3. Click **"Email/Password"**
4. Toggle **"Enable"**
5. Click **"Save"**
6. (Optional) Click **"Google"** to enable Google Sign-In
7. Click **"Enable"**
8. Click **"Save"**

### Firestore Database
1. Go to **Build → Firestore Database**
2. Click **"Create Database"**
3. Security rules: Select **"Production Mode"**
4. Region: **"us-central1"** (or closest to you)
5. Click **"Create"**

### Cloud Functions
1. Go to **Build → Functions**
2. Click **"Get Started"**
3. Note: We'll deploy functions from local machine, not console

---

## Get Credentials

### Download Service Account Key
1. Go to **Project Settings** (⚙️ gear icon)
2. Click **"Service Accounts"** tab
3. Click **"Generate New Private Key"**
4. Save as `service-account-key.json` in project root
5. **IMPORTANT:** Add to `.gitignore`:
   ```bash
   echo "service-account-key.json" >> .gitignore
   ```

---

## Gemini API Setup

### Get Gemini API Key
1. Go to **https://aistudio.google.com/app/apikeys**
2. Click **"Create API key"**
3. Create in existing Firebase project
4. Copy the key
5. **IMPORTANT:** Do NOT commit this key!

### Update .env File
```bash
# In project root, copy .env.example to .env
cp .env.example .env

# Edit .env and add your Gemini API key:
# GEMINI_API_KEY=paste_your_key_here
```

### Enable Gemini in Firebase Project (Optional)
Some regions may require explicit enablement:
1. Go to Firebase Console **Build → APIs**
2. Search for "Generative AI"
3. Click **"Enable"** if available

---

## Deploy Cloud Functions

### Install Firebase CLI
```bash
npm install -g firebase-tools
firebase login
firebase --version
```

### Initialize Functions (if not already done)
```bash
cd RCOS-2.0
firebase init functions

# When prompted:
# - Which features? → Functions
# - Language? → JavaScript
# - ESLint? → No
# - Install dependencies? → Yes
```

### Deploy Functions
```bash
# From project root
cd functions
npm install @google/generative-ai

# Go back to root
cd ..

# Set Gemini API key in Firebase
firebase functions:config:set gemini.api_key="your_gemini_api_key_here"

# Deploy functions
firebase deploy --only functions

# Verify deployment
firebase functions:list
firebase functions:log
```

**Expected output:**
```
✓ functions[spawnAgent(us-central1)]: Successful create operation.
✓ functions[executeWorkflow(us-central1)]: Successful create operation.
✓ functions[getJobStatus(us-central1)]: Successful create operation.
✓ functions[deepReasoning(us-central1)]: Successful create operation.
✓ functions[cleanupOldJobs(us-central1)]: Successful create operation.
✓ functions[archiveCompletedTasks(us-central1)]: Successful create operation.
```

---

## Update Firestore Rules

### Deploy Rules
```bash
firebase deploy --only firestore:rules
```

This deploys the rules from `firestore.rules` file in your project root.

**What these rules do:**
- ✅ Users can only read/write their own data
- ✅ Audit logs are protected
- ✅ Jobs and tasks are user-specific
- ✅ Agent registry is readable by authenticated users

---

## Build and Run

### Build Debug APK
```bash
./gradlew clean build
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Install on Device
```bash
# Enable USB Debugging on your device:
# Settings → Developer Options → USB Debugging → ON

# Connect device via USB
adb devices

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Or use Android Studio: Run → Run 'app'
```

### View Logs
```bash
adb logcat | grep "com.rcsolutions.rcos.app"
```

---

## Testing

### Test Checklist

1. **Authentication**
   - [ ] App launches to Login screen (NOT auto-logged-in)
   - [ ] Can register new account
   - [ ] Can login with email/password
   - [ ] Login persists after app restart
   - [ ] Logout works and returns to Login screen

2. **Firebase Firestore**
   - [ ] User data saved in Firebase Console → Firestore → users collection
   - [ ] User can see their company profile
   - [ ] Chat messages saved to Firestore

3. **Gemini Chat**
   - [ ] Can navigate to Chat screen
   - [ ] Can type and send message
   - [ ] **Real Gemini response appears (not instant fake response)**
   - [ ] Response time is 2-5 seconds (actual API call)
   - [ ] Multiple messages create conversation history

4. **Cloud Functions**
   - [ ] Workflow execution works (if implemented)
   - [ ] Agent spawning creates records in Firestore
   - [ ] Check Firebase Console → Functions → Logs for execution details

5. **Firestore Security Rules**
   - [ ] Cannot access other users' data
   - [ ] Can only read/write own data
   - [ ] Audit logs are protected

### Firebase Console Verification

1. **Check Users**
   - Go to **Authentication → Users**
   - Should see created user email
   - Confirm email is verified

2. **Check Firestore Data**
   - Go to **Firestore Database**
   - Expand **users** collection
   - Expand user document
   - Should see subcollections: chatMessages, jobs, auditLogs

3. **Check Cloud Function Logs**
   - Go to **Build → Functions**
   - Click on function name
   - View **Logs** tab
   - Should show function invocations

---

## Troubleshooting

### APK Build Fails
```bash
# Clear cache
./gradlew clean

# Rebuild with verbose output
./gradlew assembleDebug --stacktrace

# Check Gradle version compatibility
./gradlew --version
```

### App Crashes on Launch
```bash
# View crash logs
adb logcat | grep -E "(AndroidRuntime|FATAL|Exception)"

# Common issues:
# 1. google-services.json missing → app/google-services.json
# 2. Gemini API key not set → .env file
# 3. Firebase services not enabled → Firebase Console
```

### Firebase Connection Fails
- Verify internet permission in `AndroidManifest.xml`
- Check Firebase project ID matches `google-services.json`
- Test internet: `adb shell ping google.com`
- Verify Firestore security rules allow read

### Gemini API Errors
- Verify API key at https://aistudio.google.com/app/apikeys
- Check quota: https://aistudio.google.com/app/usage
- Ensure key has access to Generative AI models
- Check API is enabled in Google Cloud Console

### Cloud Functions Not Deploying
```bash
# Check Node version
node --version  # Should be 16+ (preferably 18)

# Reinstall dependencies
cd functions
rm -rf node_modules package-lock.json
npm install

# Deploy again
firebase deploy --only functions

# View deployment logs
firebase deploy --debug
```

---

## Production Deployment

### Before Going to Production

1. **Update Firestore Rules**
   - Edit `firestore.rules`
   - Deploy: `firebase deploy --only firestore:rules`

2. **Rotate Credentials**
   - Generate new Gemini API keys
   - Never commit `.env` file
   - Use environment variables in Firebase Functions

3. **Enable Authentication Methods**
   - Go to **Authentication → Settings**
   - Configure allowed domains if using custom domains

4. **Set Up Monitoring**
   - Enable Firestore monitoring
   - Enable Cloud Functions error reporting
   - Set up email alerts for critical errors

5. **Load Testing**
   - Test with multiple concurrent users
   - Monitor Firestore read/write quotas
   - Adjust Cloud Functions memory if needed

---

## Next Steps

1. Complete all steps above
2. Build and install debug APK
3. Test in Firebase Console
4. Create release APK for distribution
5. Submit to Google Play Store

For detailed app setup, see `SETUP_GUIDE.md`.
