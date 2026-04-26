package com.senikroute.data.comments

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.senikroute.auth.AuthRepository
import com.senikroute.auth.AuthState
import com.senikroute.data.profile.anonHandleFor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CommentsRepo"

@Singleton
class CommentsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepo: AuthRepository,
) {
    fun observe(driveId: String): Flow<List<Comment>> = callbackFlow {
        val reg = firestore.collection("drives")
            .document(driveId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "snapshot error for $driveId", err)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toComment(driveId) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    suspend fun post(
        driveId: String,
        body: String,
        parentCommentId: String? = null,
        waypointId: String? = null,
    ): Result<String> = runCatching {
        val signedIn = authRepo.authState.first() as? AuthState.SignedIn
            ?: error("not signed in")
        require(signedIn.isEmailVerified) { "email not verified" }
        require(body.isNotBlank()) { "empty comment" }
        require(body.length <= 4000) { "comment too long" }

        // Read the server-assigned anon handle from the user's profile. Avoid the
        // uid-derived fallback as the canonical byline so handles aren't reversible.
        val handle = runCatching {
            firestore.collection("users").document(signedIn.uid).get().await()
                .getString("anonHandle")
        }.getOrNull() ?: "traveler-pending"

        val id = UUID.randomUUID().toString()
        firestore.collection("drives").document(driveId).collection("comments").document(id)
            .set(
                mapOf(
                    "authorUid" to signedIn.uid,
                    "authorAnonHandle" to handle,
                    "waypointId" to waypointId,
                    "parentCommentId" to parentCommentId,
                    "body" to body.trim(),
                    "createdAt" to System.currentTimeMillis(),
                    "editedAt" to null,
                    "isHelpfulAnswer" to false,
                    "deleted" to false,
                ),
            )
            .await()
        id
    }

    suspend fun setHelpful(driveId: String, commentId: String, helpful: Boolean): Result<Unit> = runCatching {
        firestore.collection("drives").document(driveId)
            .collection("comments").document(commentId)
            .set(mapOf("isHelpfulAnswer" to helpful), SetOptions.merge())
            .await()
    }

    /** Soft-delete (tombstone). Author or drive owner per security rules. */
    suspend fun softDelete(driveId: String, commentId: String): Result<Unit> = runCatching {
        firestore.collection("drives").document(driveId)
            .collection("comments").document(commentId)
            .set(mapOf("deleted" to true), SetOptions.merge())
            .await()
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toComment(driveId: String): Comment? {
    val authorUid = getString("authorUid") ?: return null
    return Comment(
        id = id,
        driveId = driveId,
        authorUid = authorUid,
        authorAnonHandle = getString("authorAnonHandle") ?: anonHandleFor(authorUid),
        waypointId = getString("waypointId"),
        parentCommentId = getString("parentCommentId"),
        body = getString("body").orEmpty(),
        createdAt = getLong("createdAt") ?: 0L,
        editedAt = getLong("editedAt"),
        isHelpfulAnswer = getBoolean("isHelpfulAnswer") ?: false,
        deleted = getBoolean("deleted") ?: false,
    )
}
