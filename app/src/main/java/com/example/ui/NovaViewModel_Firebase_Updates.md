// Add these new imports to the top of NovaViewModel.kt
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Add this property to NovaViewModel class (around line 101)
private val firebaseAuthManager = FirebaseAuthManager()
private val firebaseAuth = FirebaseAuth.getInstance()
private val firestore = FirebaseFirestore.getInstance()

// REPLACE the loginUser() function (starting around line 934) with this:
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

// REPLACE the registerUser() function (starting around line 889) with this:
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

// REPLACE the logout() function (around line 992) with this:
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

// REPLACE the restoreSession() function (around line 821) with this:
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

// REPLACE the sendChatMessage() function with this:
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

// Add this helper function to NovaViewModel
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

// Add this function to convert ChatMessageEntity to Map for Firestore
private fun ChatMessageEntity.toMap(): Map<String, Any> = mapOf(
    "id" to this.id,
    "sessionId" to this.sessionId,
    "role" to this.role,
    "text" to this.text,
    "timestamp" to this.timestamp
)

// Add these helper functions
fun selectPersona(persona: String) {
    _currentPersona.value = persona
}

fun startNewChatSession() {
    _currentSessionId.value = UUID.randomUUID().toString()
    _chatMessages.value = emptyList()
}
