package com.scenicroute.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenicroute.data.profile.Profile
import com.scenicroute.data.profile.ProfileRepository
import com.scenicroute.ui.nav.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class OtherProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: ProfileRepository,
) : ViewModel() {

    private val uid: String = checkNotNull(savedStateHandle[Destinations.ARG_UID])

    val profile: StateFlow<Profile?> = repo.observeProfile(uid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
