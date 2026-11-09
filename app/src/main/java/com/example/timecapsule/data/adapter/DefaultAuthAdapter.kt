package com.example.timecapsule.data.adapter

import com.example.timecapsule.domain.result.OperationResult
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

/**
 * Production implementation of [AuthAdapter] using Firebase Authentication.
 */
class DefaultAuthAdapter(
    private val authSupplier: () -> FirebaseAuth = { FirebaseAuth.getInstance() }
) : AuthAdapter {

    override fun activeUid(): String? = authSupplier().currentUser?.uid

    override fun activeEmail(): String? = authSupplier().currentUser?.email

    override fun activeDisplayName(): String? = authSupplier().currentUser?.displayName

    override suspend fun signIn(email: String, password: String): OperationResult<String> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            return OperationResult.Invalid("Email and password must not be blank.")
        }

        return try {
            val result = authSupplier().signInWithEmailAndPassword(trimmedEmail, password).await()
            val uid = result.user?.uid
            if (uid != null) {
                OperationResult.Success(uid)
            } else {
                OperationResult.Unavailable("Authentication succeeded but returned a null user ID.")
            }
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): OperationResult<String> {
        val trimmedEmail = email.trim()
        val trimmedName = displayName.trim()
        if (trimmedEmail.isBlank() || password.isBlank()) {
            return OperationResult.Invalid("Email and password must not be blank for registration.")
        }

        return try {
            val result = authSupplier().createUserWithEmailAndPassword(trimmedEmail, password).await()
            val user = result.user
            if (user != null) {
                if (trimmedName.isNotBlank()) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(trimmedName)
                        .build()
                    user.updateProfile(profileUpdates).await()
                }
                OperationResult.Success(user.uid)
            } else {
                OperationResult.Unavailable("Account creation succeeded but returned a null user.")
            }
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    override suspend fun updateDisplayName(displayName: String): OperationResult<Unit> {
        val trimmedName = displayName.trim()
        if (trimmedName.isBlank()) {
            return OperationResult.Invalid("Display name must not be blank.")
        }

        val user = authSupplier().currentUser
            ?: return OperationResult.Denied("No active authenticated session available.")

        return try {
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(trimmedName)
                .build()
            user.updateProfile(profileUpdates).await()
            OperationResult.Success(Unit)
        } catch (e: Exception) {
            mapAuthException(e)
        }
    }

    override fun signOut() {
        try {
            authSupplier().signOut()
        } catch (_: Exception) {
            // Ignore sign-out exceptions for clean session termination
        }
    }

    private fun <T> mapAuthException(e: Exception): OperationResult<T> {
        return when (e) {
            is FirebaseNetworkException -> {
                OperationResult.Unavailable("Network unavailable. Please check your connection.")
            }
            is FirebaseAuthWeakPasswordException -> {
                OperationResult.Invalid("Password is too weak.")
            }
            is FirebaseAuthUserCollisionException -> {
                OperationResult.Invalid("User email is already registered.")
            }
            is FirebaseAuthInvalidCredentialsException -> {
                OperationResult.Invalid("Invalid credentials provided.")
            }
            is FirebaseAuthInvalidUserException -> {
                OperationResult.Denied("User account disabled or not found.")
            }
            else -> {
                OperationResult.Denied("Authentication operation failed: ${e.localizedMessage ?: e.message}")
            }
        }
    }
}
