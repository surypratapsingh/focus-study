package com.focusstudy.app.core.auth

import com.focusstudy.app.core.security.SafeLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class AuthUser(
    val uid: String,
    val email: String? = null,
    val isAnonymous: Boolean = true
)

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val user: AuthUser) : AuthState()
}

interface AuthManager {
    val authState: StateFlow<AuthState>
    val currentUser: AuthUser?
    suspend fun signInAnonymously(): Result<AuthUser>
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signOut()
}

/**
 * Local-first AuthManager implementation (DEC-003, FSTUDY-014-02).
 * Enables seamless offline study without requiring cloud accounts,
 * with zero-friction upgrade path to Firebase Authentication for cloud sync.
 */
class LocalAuthManager : AuthManager {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override val currentUser: AuthUser?
        get() = (_authState.value as? AuthState.Authenticated)?.user

    init {
        // By default, initialize with a local guest account for zero-barrier onboarding
        val guestUser = AuthUser(
            uid = "local_guest_" + UUID.randomUUID().toString().take(8),
            email = null,
            isAnonymous = true
        )
        _authState.value = AuthState.Authenticated(guestUser)
        SafeLogger.i("LocalAuthManager", "Initialized default local guest profile: ${guestUser.uid}")
    }

    override suspend fun signInAnonymously(): Result<AuthUser> {
        val user = AuthUser(
            uid = "anon_" + UUID.randomUUID().toString(),
            email = null,
            isAnonymous = true
        )
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> {
        if (!email.contains("@") || password.length < 6) {
            return Result.failure(IllegalArgumentException("Invalid email or password length (min 6 characters required)."))
        }
        val user = AuthUser(
            uid = "user_" + UUID.randomUUID().toString().take(12),
            email = email,
            isAnonymous = false
        )
        _authState.value = AuthState.Authenticated(user)
        SafeLogger.i("LocalAuthManager", "User authenticated with email")
        return Result.success(user)
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<AuthUser> {
        return signInWithEmail(email, password)
    }

    override suspend fun signOut() {
        _authState.value = AuthState.Unauthenticated
        SafeLogger.i("LocalAuthManager", "User signed out")
    }
}
