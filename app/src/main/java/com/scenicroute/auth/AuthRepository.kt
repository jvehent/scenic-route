package com.scenicroute.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    @Named("webClientId") private val webClientId: String,
) {
    val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toState() ?: AuthState.Anonymous)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithGoogle(context: Context): Result<AuthState.SignedIn> = runCatching {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = credentialManager.getCredential(context, request)
        val cred = response.credential

        val idToken = when {
            cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                GoogleIdTokenCredential.createFrom(cred.data).idToken
            else -> error("Unexpected credential type: ${cred.type}")
        }

        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(firebaseCred).await()
        val user = result.user ?: error("Sign-in succeeded but user is null")
        user.toState()
    }

    /**
     * Sign out + wipe local-only state. The cleaner is passed in to break a potential
     * dep cycle (cleaner needs DB + photo storage which sit downstream of auth).
     */
    suspend fun signOut(cleaner: SignOutCleaner) {
        runCatching { cleaner.wipeAll() }
        firebaseAuth.signOut()
    }

    private fun com.google.firebase.auth.FirebaseUser.toState() = AuthState.SignedIn(
        uid = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString(),
        isEmailVerified = isEmailVerified,
    )
}
