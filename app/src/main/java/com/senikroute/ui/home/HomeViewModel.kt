package com.senikroute.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senikroute.auth.AuthRepository
import com.senikroute.auth.AuthState
import com.senikroute.data.db.entities.DriveEntity
import com.senikroute.data.io.DriveImporter
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
class HomeViewModel @Inject constructor(
    authRepo: AuthRepository,
    driveRepo: DriveRepository,
    private val importer: DriveImporter,
) : ViewModel() {

    val drives: StateFlow<List<DriveEntity>> = authRepo.authState
        .flatMapLatest { state ->
            when (state) {
                is AuthState.SignedIn -> driveRepo.observeDrives(state.uid)
                else -> flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    fun import(uri: Uri, onSuccess: (List<String>) -> Unit, onError: (String) -> Unit) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            importer.importFromUri(uri)
                .onSuccess(onSuccess)
                .onFailure { onError(it.message ?: "Import failed") }
            _importing.value = false
        }
    }
}
