package com.scenicroute.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val signOutCleaner: SignOutCleaner,
) : ViewModel() {

    val state: StateFlow<AuthState> = repo.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Unknown)

    fun signIn(context: Context, onResult: (Result<AuthState.SignedIn>) -> Unit) {
        viewModelScope.launch {
            onResult(repo.signInWithGoogle(context))
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repo.signOut(signOutCleaner)
        }
    }
}
