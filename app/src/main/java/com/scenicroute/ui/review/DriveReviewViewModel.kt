package com.scenicroute.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenicroute.data.db.entities.DriveEntity
import com.scenicroute.data.model.Visibility
import com.scenicroute.data.repo.DriveRepository
import com.scenicroute.ui.nav.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriveReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: DriveRepository,
) : ViewModel() {

    private val driveId: String = checkNotNull(savedStateHandle[Destinations.ARG_DRIVE_ID])

    val drive: StateFlow<DriveEntity?> = repo.observeDrive(driveId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(
        title: String,
        description: String,
        visibility: Visibility,
        tagsCsv: String,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val tags = tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            repo.updateDriveMeta(
                driveId = driveId,
                title = title.trim(),
                description = description.trim(),
                visibility = visibility,
                tags = tags,
                coverWaypointId = null,
            )
            onDone()
        }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.softDeleteDrive(driveId)
            onDone()
        }
    }
}
