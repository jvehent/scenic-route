package com.scenicroute.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenicroute.auth.AuthRepository
import com.scenicroute.auth.AuthState
import com.scenicroute.data.db.entities.DriveEntity
import com.scenicroute.data.repo.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val PURGE_AFTER_DAYS = 30

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrashViewModel @Inject constructor(
    authRepo: AuthRepository,
    private val driveRepo: DriveRepository,
) : ViewModel() {

    val drives: StateFlow<List<DriveEntity>> = authRepo.authState
        .flatMapLatest { state ->
            if (state is AuthState.SignedIn) driveRepo.observeTrashed(state.uid)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(id: String) {
        viewModelScope.launch { driveRepo.restoreDrive(id) }
    }

    fun deleteNow(id: String) {
        viewModelScope.launch { driveRepo.hardDeleteDrive(id) }
    }
}
