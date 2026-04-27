package com.senikroute.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import com.senikroute.ui.layout.FormMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.senikroute.R
import com.senikroute.auth.AuthViewModel
import com.senikroute.ui.theme.SenikBrandTitle

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onBack: () -> Unit,
    vm: AuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Gate the Google sign-in flow behind privacy acceptance. Anonymous users
    // (the "Browse without signing in" path) are not asked to accept.
    var showPrivacy by remember { mutableStateOf(false) }

    fun startGoogleSignIn() {
        busy = true
        vm.signIn(context) { result ->
            busy = false
            result.onSuccess { state ->
                if (!state.isEmailVerified) {
                    error = context.getString(R.string.signin_verify_email)
                } else {
                    onSignedIn()
                }
            }.onFailure { error = it.message ?: "Sign-in failed" }
        }
    }

    if (showPrivacy) {
        PrivacyAcceptanceScreen(
            onAccept = {
                showPrivacy = false
                startGoogleSignIn()
            },
            onDecline = { showPrivacy = false },
        )
        return
    }

    // Brand mark fixed in the top-left; main content centered in the remaining space.
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) { SenikBrandTitle() }
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = FormMaxWidth)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.signin_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.signin_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            enabled = !busy,
            onClick = { showPrivacy = true },
        ) {
            Text(stringResource(R.string.signin_google))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.signin_skip))
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
    }
    }
}
