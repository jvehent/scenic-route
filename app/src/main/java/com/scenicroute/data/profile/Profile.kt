package com.scenicroute.data.profile

data class Profile(
    val uid: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String,
    val visibility: ProfileVisibility,
    val anonHandle: String,
    val points: Int,
    val drivesPublished: Int,
    val helpfulAnswers: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ProfileVisibility {
    PRIVATE,
    PUBLIC;

    companion object {
        fun fromStored(s: String?): ProfileVisibility =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: PRIVATE
    }
}

/**
 * Pseudonym fallback used ONLY when the server's stored anonHandle hasn't been read
 * yet (e.g., immediately after first sign-in, before onUserDocCreated has populated
 * the profile). Derived from `uid.hashCode()`, so it's reversible from a uid — that
 * means it MUST NOT appear on any public surface. The Cloud Function generates a
 * cryptographic-random handle and stores it in /users/{uid}.anonHandle; that's the
 * value that should be displayed.
 */
fun anonHandleFor(uid: String): String {
    val h = uid.hashCode().toUInt().toString(16).padStart(8, '0').take(6)
    return "traveler-$h"
}
