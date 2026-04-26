package com.senikroute.ui.public_

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senikroute.auth.AuthRepository
import com.senikroute.auth.AuthState
import com.senikroute.data.comments.Comment
import com.senikroute.data.comments.CommentsRepository
import com.senikroute.data.remote.PublicDriveRepository
import com.senikroute.data.remote.RemoteDrive
import com.senikroute.data.remote.RemoteTrackPoint
import com.senikroute.data.remote.RemoteWaypoint
import com.senikroute.ui.nav.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PublicDriveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: PublicDriveRepository,
    private val commentsRepo: CommentsRepository,
    authRepo: AuthRepository,
) : ViewModel() {

    private val driveId: String = checkNotNull(savedStateHandle[Destinations.ARG_DRIVE_ID])

    private val _state = MutableStateFlow(PublicDriveUiState(loading = true))
    val state: StateFlow<PublicDriveUiState> = _state.asStateFlow()

    val comments: StateFlow<List<Comment>> = commentsRepo.observe(driveId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val authState: StateFlow<AuthState> = authRepo.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Unknown)

    val isOwner: StateFlow<Boolean> = combine2(state, authState) { s, a ->
        val ownerUid = s.drive?.ownerUid
        val signedIn = a as? AuthState.SignedIn
        ownerUid != null && signedIn?.uid == ownerUid
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        load()
    }

    fun reload() = load()

    fun postComment(body: String, parentId: String?, onError: (String) -> Unit) {
        viewModelScope.launch {
            commentsRepo.post(driveId, body, parentCommentId = parentId)
                .onFailure { onError(it.message ?: "Failed to post") }
        }
    }

    fun setHelpful(commentId: String, helpful: Boolean, parentId: String?, onError: (String) -> Unit) {
        viewModelScope.launch {
            // Enforce one helpful per question-thread: unmark siblings first.
            if (helpful && parentId != null) {
                comments.value
                    .filter { it.parentCommentId == parentId && it.id != commentId && it.isHelpfulAnswer }
                    .forEach { commentsRepo.setHelpful(driveId, it.id, false) }
            }
            commentsRepo.setHelpful(driveId, commentId, helpful)
                .onFailure { onError(it.message ?: "Failed to update") }
        }
    }

    fun deleteComment(commentId: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            commentsRepo.softDelete(driveId, commentId)
                .onFailure { onError(it.message ?: "Failed to delete") }
        }
    }

    private fun load() {
        _state.value = PublicDriveUiState(loading = true)
        viewModelScope.launch {
            runCatching {
                val drive = repo.fetchDrive(driveId) ?: error("Drive not found or not accessible")
                _state.value = PublicDriveUiState(loading = true, drive = drive)
                val waypoints = runCatching { repo.fetchWaypoints(driveId) }.getOrDefault(emptyList())
                val track = drive.trackUrl?.let {
                    runCatching { repo.fetchTrack(it) }.getOrDefault(emptyList())
                } ?: emptyList()
                _state.value = PublicDriveUiState(
                    loading = false,
                    drive = drive,
                    waypoints = waypoints,
                    track = track,
                )
            }.onFailure { e ->
                _state.value = PublicDriveUiState(loading = false, error = e.message)
            }
        }
    }
}

data class PublicDriveUiState(
    val loading: Boolean = false,
    val drive: RemoteDrive? = null,
    val waypoints: List<RemoteWaypoint> = emptyList(),
    val track: List<RemoteTrackPoint> = emptyList(),
    val error: String? = null,
)

private fun <A, B, R> combine2(
    a: StateFlow<A>,
    b: StateFlow<B>,
    transform: (A, B) -> R,
) = kotlinx.coroutines.flow.combine(a, b, transform)
