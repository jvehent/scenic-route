package com.senikroute.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senikroute.auth.AuthRepository
import com.senikroute.auth.AuthState
import com.senikroute.data.profile.Profile
import com.senikroute.data.profile.ProfileRepository
import com.senikroute.data.profile.ProfileVisibility
import com.senikroute.data.repo.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyProfileViewModel @Inject constructor(
    private val repo: ProfileRepository,
    authRepo: AuthRepository,
    driveRepo: DriveRepository,
) : ViewModel() {

    val profile: StateFlow<Profile?> = repo.myProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trashedCount: StateFlow<Int> = authRepo.authState
        .flatMapLatest { state ->
            if (state is AuthState.SignedIn) driveRepo.observeTrashedCount(state.uid)
            else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    fun save(
        displayName: String,
        bio: String,
        visibility: ProfileVisibility,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            runCatching { repo.updateMyProfile(displayName, bio, visibility) }
                .onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Failed to save") }
            _saving.value = false
        }
    }
}
