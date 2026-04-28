package com.senikroute.data.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Google Identity's AuthorizationClient so the rest of the app can ask "give me a
 * Drive-API access token" without juggling the consent flow plumbing.
 *
 * The Sign-In flow (Credential Manager → Firebase Auth) only returns an ID token. To call
 * the Drive REST API we need an OAuth2 access token with the `drive.file` scope, which is
 * a separate authorization step. AuthorizationClient handles the user-consent prompt and
 * cached-grant short-circuit on subsequent calls.
 *
 * `drive.file` scope (vs. `drive`) restricts our access to files the app itself created —
 * we cannot read or modify any of the user's other Drive files. This is the principle of
 * least privilege and what the Google review process expects for non-storage apps.
 */
@Singleton
class GoogleDriveAuth @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    /**
     * Try to authorize without showing UI. Succeeds if the user previously granted the
     * scope to this app + this device. Returns the access token, or null if the user
     * needs to be prompted (the caller then runs the [requestConsent] flow).
     */
    suspend fun trySilent(): String? {
        val client = Identity.getAuthorizationClient(appContext)
        val req = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE)))
            .build()
        return runCatching {
            val result = client.authorize(req).await()
            if (result.hasResolution()) null else result.accessToken
        }.getOrNull()
    }

    /**
     * Build the AuthorizationRequest the Activity should launch via
     * [Activity.startIntentSenderForResult] when [trySilent] returned null. The result's
     * intent comes back to onActivityResult, where the caller passes it to
     * [parseConsentResult].
     */
    suspend fun consentIntent(): android.content.IntentSender? {
        val client = Identity.getAuthorizationClient(appContext)
        val req = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE)))
            .build()
        val result = runCatching { client.authorize(req).await() }.getOrNull() ?: return null
        return result.pendingIntent?.intentSender
    }

    /** Extract the access token from the consent-flow result. */
    fun parseConsentResult(data: Intent?): String? {
        val client = Identity.getAuthorizationClient(appContext)
        return runCatching { client.getAuthorizationResultFromIntent(data) }.getOrNull()
            ?.let { it.accessToken }
    }

    @Suppress("unused")
    fun emptyResult(): AuthorizationResult? = null

    companion object {
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    }
}
