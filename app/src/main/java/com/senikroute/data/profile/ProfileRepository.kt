package com.senikroute.data.profile

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.senikroute.auth.AuthRepository
import com.senikroute.auth.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ProfileRepo"

@Singleton
class ProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepo: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myProfile: Flow<Profile?> = authRepo.authState
        .flatMapLatest { state ->
            if (state is AuthState.SignedIn) observeProfile(state.uid)
            else flowOf(null)
        }

    fun observeProfile(uid: String): Flow<Profile?> = callbackFlow {
        val reg = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "snapshot error for $uid", err)
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snap?.toProfile())
            }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    suspend fun updateMyProfile(
        displayName: String,
        bio: String,
        visibility: ProfileVisibility,
    ) {
        val uid = currentUid() ?: error("not signed in")
        firestore.collection("users").document(uid)
            .set(
                mapOf(
                    "displayName" to displayName.trim(),
                    "bio" to bio.trim(),
                    "visibility" to visibility.name.lowercase(),
                    "updatedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    /**
     * Idempotent: creates the doc on first sign-in with only the field set the
     * security rules allow at create time. The Cloud Function `onUserDocCreated`
     * fills in the privileged fields (anonHandle, points, stats) server-side so
     * the client can't inject them.
     */
    suspend fun ensureProfile(
        uid: String,
        defaultDisplayName: String?,
        defaultAvatarUrl: String?,
    ) {
        val ref = firestore.collection("users").document(uid)
        val snap = runCatching { ref.get().await() }.getOrNull() ?: return
        if (snap.exists()) return
        // Only the field set the create rule whitelists. createdAt + updatedAt are
        // stamped server-side by onUserDocCreated; sending them here is rejected.
        runCatching {
            ref.set(
                mapOf(
                    "displayName" to (defaultDisplayName?.takeIf { it.isNotBlank() } ?: "Traveler"),
                    "avatarUrl" to defaultAvatarUrl,
                    "bio" to "",
                    "visibility" to "private",
                ),
            ).await()
        }.onFailure { Log.w(TAG, "ensureProfile failed") } // no uid in production logs
    }

    /** Wires the repository to listen on auth state and bootstrap a profile doc on first sign-in. */
    fun observeAuthAndBootstrap() {
        scope.launch {
            authRepo.authState.distinctUntilChanged().collect { state ->
                if (state is AuthState.SignedIn && state.isEmailVerified) {
                    ensureProfile(state.uid, state.displayName, state.photoUrl)
                }
            }
        }
    }

    private suspend fun currentUid(): String? =
        (authRepo.authState.first() as? AuthState.SignedIn)?.uid
}

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot?.toProfile(): Profile? {
    if (this == null || !exists()) return null
    val stats = (get("stats") as? Map<String, Any?>).orEmpty()
    return Profile(
        uid = id,
        displayName = getString("displayName").orEmpty(),
        avatarUrl = getString("avatarUrl"),
        bio = getString("bio").orEmpty(),
        visibility = ProfileVisibility.fromStored(getString("visibility")),
        anonHandle = getString("anonHandle") ?: anonHandleFor(id),
        points = (getLong("points") ?: 0L).toInt(),
        drivesPublished = ((stats["drivesPublished"] as? Number)?.toInt()) ?: 0,
        helpfulAnswers = ((stats["helpfulAnswers"] as? Number)?.toInt()) ?: 0,
        createdAt = getLong("createdAt") ?: 0L,
        updatedAt = getLong("updatedAt") ?: 0L,
    )
}
