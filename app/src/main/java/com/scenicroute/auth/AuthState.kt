package com.scenicroute.auth

sealed interface AuthState {
    data object Unknown : AuthState
    data object Anonymous : AuthState
    data class SignedIn(
        val uid: String,
        val email: String?,
        val displayName: String?,
        val photoUrl: String?,
        val isEmailVerified: Boolean,
    ) : AuthState
}
