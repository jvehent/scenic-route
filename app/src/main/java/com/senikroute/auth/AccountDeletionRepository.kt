package com.senikroute.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permanent, irreversible deletion of the signed-in user's account and all data attributed
 * to them. Steps:
 *
 *  1. Hard-delete every drive doc owned by the user in Firestore. The `onDriveDeleted`
 *     Cloud Function fires per drive and cascades subcollections (waypoints, comments) +
 *     Cloud Storage objects (track GeoJSON, photos).
 *  2. Delete the user's profile doc at /users/{uid}.
 *  3. Wipe local Room state + on-disk photos via [SignOutCleaner].
 *  4. Delete the Firebase Auth account (also signs the user out as a side effect).
 *
 * Comments the user posted on other people's public drives are not located by uid here —
 * Firestore would need a collection-group query and an index for that. The privacy policy
 * documents that those may persist and points to the support email for manual removal.
 *
 * Throws [FirebaseAuthRecentLoginRequiredException] if Google's recent-login window has
 * expired; the caller should surface a "sign out + sign back in" message in that case.
 */
@Singleton
class AccountDeletionRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val cleaner: SignOutCleaner,
) {

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val user = firebaseAuth.currentUser ?: error("Not signed in")
        val uid = user.uid

        // 1. Hard-delete every owned drive. Each delete fires the server cascade.
        val owned = firestore.collection("drives")
            .whereEqualTo("ownerUid", uid)
            .get()
            .await()
        owned.documents.forEach { doc ->
            runCatching { doc.reference.delete().await() }
        }

        // 2. Delete the user's profile doc. Best-effort — if rules reject for any reason
        // we still proceed to local + auth deletion so the user is fully signed out.
        runCatching { firestore.collection("users").document(uid).delete().await() }

        // 3. Wipe local-only state.
        cleaner.wipeAll()

        // 4. Delete the Firebase Auth user. This will throw
        // FirebaseAuthRecentLoginRequiredException if the recent-login window has expired,
        // which we let propagate so the UI can prompt for re-auth.
        user.delete().await()
    }
}
