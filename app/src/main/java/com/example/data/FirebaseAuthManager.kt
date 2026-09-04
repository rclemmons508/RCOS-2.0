package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase Authentication for the RCOS application.
 * Handles user registration, login, logout, and profile management.
 */
class FirebaseAuthManager {
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val tag = "FirebaseAuthManager"

    /**
     * Register a new user with email and password
     */
    suspend fun registerUser(
        email: String,
        password: String,
        fullName: String
    ): Result<UserEntity> = try {
        val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        val user = authResult.user

        if (user != null) {
            // Update profile with display name
            val profileUpdate = UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
                .build()
            user.updateProfile(profileUpdate).await()

            // Create UserEntity
            val userEntity = UserEntity(
                id = user.uid,
                email = user.email ?: email,
                fullName = fullName,
                passwordHash = "", // Not stored locally
                salt = "",
                companyName = "",
                industry = "",
                createdAt = System.currentTimeMillis()
            )

            Log.i(tag, "User registered successfully: $email")
            Result.success(userEntity)
        } else {
            Result.failure(Exception("User creation failed"))
        }
    } catch (e: Exception) {
        Log.e(tag, "Registration error: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Login user with email and password
     */
    suspend fun loginUser(email: String, password: String): Result<UserEntity> = try {
        val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        val user = authResult.user

        if (user != null) {
            val userEntity = UserEntity(
                id = user.uid,
                email = user.email ?: email,
                fullName = user.displayName ?: "User",
                passwordHash = "",
                salt = "",
                companyName = "",
                industry = "",
                createdAt = System.currentTimeMillis()
            )

            Log.i(tag, "User logged in successfully: $email")
            Result.success(userEntity)
        } else {
            Result.failure(Exception("Login failed"))
        }
    } catch (e: Exception) {
        Log.e(tag, "Login error: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Logout the current user
     */
    fun logout() {
        firebaseAuth.signOut()
        Log.i(tag, "User logged out")
    }

    /**
     * Get the current authenticated user
     */
    fun getCurrentUser(): UserEntity? {
        val user = firebaseAuth.currentUser
        return if (user != null) {
            UserEntity(
                id = user.uid,
                email = user.email ?: "",
                fullName = user.displayName ?: "User",
                passwordHash = "",
                salt = "",
                companyName = "",
                industry = "",
                createdAt = System.currentTimeMillis()
            )
        } else {
            null
        }
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return firebaseAuth.currentUser != null
    }

    /**
     * Send password reset email
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = try {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Log.i(tag, "Password reset email sent to: $email")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(tag, "Password reset error: ${e.message}", e)
        Result.failure(e)
    }

    /**
     * Update user profile
     */
    suspend fun updateProfile(fullName: String): Result<Unit> = try {
        val user = firebaseAuth.currentUser
        if (user != null) {
            val profileUpdate = UserProfileChangeRequest.Builder()
                .setDisplayName(fullName)
                .build()
            user.updateProfile(profileUpdate).await()
            Log.i(tag, "Profile updated for: ${user.email}")
            Result.success(Unit)
        } else {
            Result.failure(Exception("No authenticated user"))
        }
    } catch (e: Exception) {
        Log.e(tag, "Profile update error: ${e.message}", e)
        Result.failure(e)
    }
}
