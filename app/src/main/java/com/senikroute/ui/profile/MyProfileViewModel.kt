package com.senikroute.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.senikroute.auth.AccountDeletionRepository
import com.senikroute.auth.AuthRepository
import com.senikroute.auth.AuthState
import com.senikroute.data.drive.DriveTakeoutManager
import com.senikroute.data.drive.GoogleDriveAuth
import com.senikroute.data.prefs.SettingsStore
import com.senikroute.data.prefs.UserSettings
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyProfileViewModel @Inject constructor(
    private val repo: ProfileRepository,
    authRepo: AuthRepository,
    driveRepo: DriveRepository,
    private val accountDeletion: AccountDeletionRepository,
    private val driveAuth: GoogleDriveAuth,
    private val takeoutManager: DriveTakeoutManager,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsStore.settings
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            UserSettings(
                bufferEnabled = true, bufferMinutes = 30, discoveryRadiusKm = 25,
                wifiOnlyUploads = false,
                gpsSamplingSeconds = UserSettings.DEFAULT_GPS_SAMPLING_SECONDS,
                driveAutoSave = false,
                driveFolderName = UserSettings.DEFAULT_DRIVE_FOLDER,
            ),
        )

    private val _takeoutState = MutableStateFlow<TakeoutState>(TakeoutState.Idle)
    val takeoutState: StateFlow<TakeoutState> = _takeoutState.asStateFlow()

    val profile: StateFlow<Profile?> = repo.myProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Email the user signed in with — used as the confirmation phrase for account deletion. */
    val signedInEmail: StateFlow<String?> = authRepo.authState
        .map { (it as? AuthState.SignedIn)?.email }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trashedCount: StateFlow<Int> = authRepo.authState
        .flatMapLatest { state ->
            if (state is AuthState.SignedIn) driveRepo.observeTrashedCount(state.uid)
            else flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

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

    /**
     * Permanently delete the account + all data attributed to it. The caller has already
     * confirmed the user typed their full email; this method does not re-validate that
     * gate. Reports a friendly message on the recent-login error so the user knows to
     * re-authenticate before retrying.
     */
    fun deleteAccount(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            accountDeletion.deleteAccount()
                .onSuccess { onSuccess() }
                .onFailure { e ->
                    if (e is FirebaseAuthRecentLoginRequiredException) {
                        onError("For security, this requires a recent sign-in. Sign out, sign back in, then try again.")
                    } else {
                        onError(e.message ?: "Failed to delete account")
                    }
                }
            _deleting.value = false
        }
    }

    fun setDriveAutoSave(on: Boolean) = viewModelScope.launch {
        settingsStore.setDriveAutoSave(on)
    }

    fun setDriveFolderName(name: String) = viewModelScope.launch {
        settingsStore.setDriveFolderName(name)
    }

    /**
     * Step 1 of the takeout flow. Tries to get a Drive access token without showing UI.
     * Returns the token if the user previously granted Drive access; null if the caller
     * needs to launch the consent intent and then call [continueTakeoutWithConsent].
     */
    suspend fun beginTakeoutSilent(): String? = driveAuth.trySilent()

    /** Build the consent intent the Activity should launch with startIntentSenderForResult. */
    suspend fun consentIntentSender() = driveAuth.consentIntent()

    /** After the consent flow returns, parse the access token and run the export. */
    fun parseConsentAndExport(data: android.content.Intent?) {
        val token = driveAuth.parseConsentResult(data)
        if (token == null) {
            _takeoutState.value = TakeoutState.Error("Drive access wasn't granted")
            return
        }
        runTakeout(token)
    }

    fun runTakeout(accessToken: String) {
        if (_takeoutState.value is TakeoutState.Running) return
        _takeoutState.value = TakeoutState.Running(0, 0, "")
        viewModelScope.launch {
            takeoutManager.exportAll(accessToken) { p ->
                _takeoutState.value = when (p) {
                    is DriveTakeoutManager.Progress.Started -> TakeoutState.Running(0, p.total, "")
                    is DriveTakeoutManager.Progress.Item -> TakeoutState.Running(p.index, p.total, p.title)
                    is DriveTakeoutManager.Progress.Failed -> _takeoutState.value
                    is DriveTakeoutManager.Progress.Done -> TakeoutState.Done(p.uploaded, p.failed, p.folderName)
                }
            }.onFailure { _takeoutState.value = TakeoutState.Error(it.message ?: "takeout failed") }
        }
    }

    fun dismissTakeout() { _takeoutState.value = TakeoutState.Idle }

    sealed interface TakeoutState {
        data object Idle : TakeoutState
        data class Running(val current: Int, val total: Int, val currentTitle: String) : TakeoutState
        data class Done(val uploaded: Int, val failed: Int, val folder: String) : TakeoutState
        data class Error(val message: String) : TakeoutState
    }
}
