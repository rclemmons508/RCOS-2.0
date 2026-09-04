# Critical Update Instructions for NovaViewModel.kt

## IMPORTANT: Follow These Steps Carefully

This file contains the exact code changes you need to make to NovaViewModel.kt to enable Firebase authentication and Gemini AI integration.

## Step 1: Add Imports

At the top of `NovaViewModel.kt`, add these imports:

```kotlin
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.tasks.await
```

## Step 2: Add Firebase Properties

Inside the `NovaViewModel` class (after line 101), add:

```kotlin
private val firebaseAuthManager = FirebaseAuthManager()
private val firebaseAuth = FirebaseAuth.getInstance()
private val firestore = FirebaseFirestore.getInstance()
```

## Step 3: Replace loginUser() Function

Find the `loginUser()` function (around line 934) and replace it entirely with:

```kotlin
fun loginUser(onSuccess: () -> Unit = {}) {
    val email = _loginEmail.value.trim()
    val password = _loginPassword.value

    if (email.isEmpty()) {
        _statusNotice.value = "Please enter an email address."
        return
    }

    _isAuthLoading.value = true
    viewModelScope.launch {
        try {
            // Use real Firebase Auth
            val result = firebaseAuthManager.loginUser(email, password)
            result.onSuccess { user ->
                _isAuthLoading.value = false
                _currentUser.value = user
                secureManager.saveString("logged_in_email", user.email)
                _statusNotice.value = "Welcome, ${user.fullName}!"
                recordAuditEvent(
                    actionType = "LOGIN",
                    description = "User '${user.email}' authenticated successfully.",
                    result = AuditResultStatus.SUCCESS
                )
                onSuccess()
            }.onFailure { err ->
                _isAuthLoading.value = false
                _statusNotice.value = err.message ?: "Login failed. Please check your credentials."
                recordAuditEvent(
                    actionType = "LOGIN_FAILED",
                    description = "Authentication failed for $email: ${err.message}",
                    result = AuditResultStatus.FAILURE
                )
            }
        } catch (e: Exception) {
            _isAuthLoading.value = false
            _statusNotice.value = e.message ?: "Unexpected error occurred."
        }
    }
}
```

## Step 4: Replace registerUser() Function

Find the `registerUser()` function (around line 889) and replace it with:

```kotlin
fun registerUser(onSuccess: () -> Unit = {}) {
    val email = _registerEmail.value.trim()
    val name = _registerFullName.value.trim()
    val password = _registerPassword.value
    val company = _registerCompanyName.value.trim()
    val industry = _registerIndustry.value.trim()

    if (!SecurityUtils.isValidEmail(email)) {
        _statusNotice.value = "Please enter a valid corporate email address."
        return
    }

    if (name.isEmpty()) {
        _statusNotice.value = "Full Name is required."
        return
    }

    val strength = SecurityUtils.evaluatePasswordStrength(password)
    if (!strength.isValid) {
        _statusNotice.value = "Password must be at least 8 characters with a number and letter."
        return
    }

    _isAuthLoading.value = true
    viewModelScope.launch {
        try {
            // Use real Firebase Auth
            val result = firebaseAuthManager.registerUser(email, password, name)
            result.onSuccess { user ->
                _isAuthLoading.value = false
                _currentUser.value = user
                secureManager.saveString("logged_in_email", user.email)
                _statusNotice.value = "Account created! Welcome to RCOS Platform, ${user.fullName}!"
                onSuccess()
            }.onFailure { err ->
                _isAuthLoading.value = false
                _statusNotice.value = err.message ?: "Registration failed. Please try again."
            }
        } catch (e: Exception) {
            _isAuthLoading.value = false
            _statusNotice.value = e.message ?: "Registration error occurred."
        }
    }
}
```

## Step 5: Replace logout() Function

Find the `logout()` function (around line 992) and replace it with:

```kotlin
fun logout() {
    recordAuditEvent(
        actionType = "LOGOUT",
        description = "User logged out.",
        result = AuditResultStatus.SUCCESS
    )
    firebaseAuthManager.logout()
    _currentUser.value = null
    secureManager.remove("logged_in_email")
    _statusNotice.value = "Logged out from RCOS Multi-Agent System."
}
```

## Step 6: Replace restoreSession() Function

Find the `restoreSession()` function (around line 821) and replace it with:

```kotlin
private fun restoreSession() {
    viewModelScope.launch {
        // Check if user is already authenticated with Firebase
        val currentUser = firebaseAuthManager.getCurrentUser()
        if (currentUser != null) {
            _currentUser.value = currentUser
            secureManager.saveString("logged_in_email", currentUser.email)
        }
        // If not authenticated, user will see login screen
    }
}
```

## Step 7: DELETE the loginAsDemoUser() Call

Find line 832 in the `init` block:
```kotlin
// DELETE THIS LINE:
loginAsDemoUser()
```

Remove it completely. Users must now login manually.

## Step 8: Replace sendChatMessage() Function

Find the `sendChatMessage()` function and replace it with:

```kotlin
fun sendChatMessage() {
    val message = _chatInput.value.trim()
    if (message.isBlank()) return

    // Add user message to chat
    val userMessage = ChatMessageEntity(
        id = UUID.randomUUID().toString(),
        sessionId = _currentSessionId.value,
        role = "user",
        text = message,
        timestamp = System.currentTimeMillis()
    )
    
    _chatMessages.value = _chatMessages.value + userMessage
    _chatInput.value = ""
    _isChatSending.value = true

    viewModelScope.launch {
        try {
            // Convert chat history to proper format for Gemini
            val history = _chatMessages.value.dropLast(1).map { msg ->
                msg.role to msg.text
            }
            
            // Call actual Gemini API
            val result = GeminiClient.generateChat(
                history = history,
                userMessage = message,
                systemInstruction = "You are $_currentPersona, an AI agent helping with business operations. Be concise, professional, and actionable."
            )
            
            result.onSuccess { response ->
                val assistantMessage = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = _currentSessionId.value,
                    role = "assistant",
                    text = response,
                    timestamp = System.currentTimeMillis()
                )
                _chatMessages.value = _chatMessages.value + assistantMessage
                _isChatSending.value = false
                
                // Save to Firestore
                viewModelScope.launch {
                    try {
                        saveChat(userMessage, assistantMessage)
                    } catch (e: Exception) {
                        android.util.Log.e("Chat", "Error saving chat", e)
                    }
                }
            }.onFailure { error ->
                _isChatSending.value = false
                _statusNotice.value = "AI Error: ${error.message}"
                android.util.Log.e("Chat", "Gemini Error", error)
            }
        } catch (e: Exception) {
            _isChatSending.value = false
            _statusNotice.value = "Error: ${e.message}"
            android.util.Log.e("Chat", "Error", e)
        }
    }
}
```

## Step 9: Add Helper Functions

Add these new functions to the end of the NovaViewModel class:

```kotlin
private suspend fun saveChat(userMessage: ChatMessageEntity, assistantMessage: ChatMessageEntity) {
    withContext(Dispatchers.IO) {
        try {
            val userId = _currentUser.value?.email ?: return@withContext
            firestore.collection("users").document(userId)
                .collection("chatMessages").document(userMessage.id)
                .set(userMessage.toMap()).await()
            
            firestore.collection("users").document(userId)
                .collection("chatMessages").document(assistantMessage.id)
                .set(assistantMessage.toMap()).await()
        } catch (e: Exception) {
            android.util.Log.e("Firestore", "Error saving chat", e)
        }
    }
}

private fun ChatMessageEntity.toMap(): Map<String, Any> = mapOf(
    "id" to this.id,
    "sessionId" to this.sessionId,
    "role" to this.role,
    "text" to this.text,
    "timestamp" to this.timestamp
)

fun selectPersona(persona: String) {
    _currentPersona.value = persona
}

fun startNewChatSession() {
    _currentSessionId.value = UUID.randomUUID().toString()
    _chatMessages.value = emptyList()
}
```

## Step 10: Build and Test

```bash
./gradlew clean build
./gradlew assembleDebug
```

If you see any errors, check:
1. All imports are added
2. All old functions are completely replaced
3. Firestore dependencies are in build.gradle

## Summary of Changes

✅ Firebase Auth instead of local database  
✅ Real Gemini API calls instead of mock responses  
✅ No auto-login (users must authenticate)  
✅ Chat messages saved to Firestore  
✅ Proper error handling  
✅ Audit logging maintained  

## Next: Follow FIREBASE_SETUP.md

After making these changes, follow the steps in `FIREBASE_SETUP.md` to:
1. Create Firebase project
2. Enable services
3. Get credentials
4. Deploy Cloud Functions
5. Build and test the app
