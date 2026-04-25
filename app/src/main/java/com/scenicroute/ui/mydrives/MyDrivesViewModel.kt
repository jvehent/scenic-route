package com.scenicroute.ui.mydrives

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenicroute.auth.AuthRepository
import com.scenicroute.auth.AuthState
import com.scenicroute.data.db.entities.DriveEntity
import com.scenicroute.data.repo.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class DriveSort(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    DISTANCE_DESC("Longest first"),
    DURATION_DESC("Longest duration"),
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyDrivesViewModel @Inject constructor(
    authRepo: AuthRepository,
    driveRepo: DriveRepository,
) : ViewModel() {

    private val _sort = MutableStateFlow(DriveSort.DATE_DESC)
    val sort: StateFlow<DriveSort> = _sort.asStateFlow()

    private val rawDrives = authRepo.authState
        .flatMapLatest { state ->
            if (state is AuthState.SignedIn) driveRepo.observeDrives(state.uid)
            else flowOf(emptyList())
        }

    val drives: StateFlow<List<DriveEntity>> = combine(rawDrives, _sort) { list, s ->
        when (s) {
            DriveSort.DATE_DESC -> list.sortedByDescending { it.startedAt }
            DriveSort.DATE_ASC -> list.sortedBy { it.startedAt }
            DriveSort.DISTANCE_DESC -> list.sortedByDescending { it.distanceM ?: 0 }
            DriveSort.DURATION_DESC -> list.sortedByDescending { it.durationS ?: 0 }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trashedCount: StateFlow<Int> = authRepo.authState
        .flatMapLatest { state ->
            if (state is AuthState.SignedIn) driveRepo.observeTrashedCount(state.uid)
            else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setSort(s: DriveSort) { _sort.value = s }
}
