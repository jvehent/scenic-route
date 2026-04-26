package com.senikroute.ui.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import com.senikroute.ui.layout.FormMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.senikroute.data.profile.Profile
import com.senikroute.data.profile.ProfileVisibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    vm: MyProfileViewModel = hiltViewModel(),
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val trashedCount by vm.trashedCount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(ProfileVisibility.PRIVATE) }
    var error by remember { mutableStateOf<String?>(null) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        val p = profile
        if (p != null && !initialized) {
            displayName = p.displayName
            bio = p.bio
            visibility = p.visibility
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        val p = profile
        if (p == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = FormMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileHeader(p)
            HorizontalDivider()

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Text("Profile visibility", style = MaterialTheme.typography.titleLarge)
            VisibilityRow(
                value = ProfileVisibility.PRIVATE,
                selected = visibility,
                label = "Private — your real name is hidden",
                sub = "Public drives + comments are attributed to your anon handle (${p.anonHandle}).",
                onSelect = { visibility = it },
            )
            VisibilityRow(
                value = ProfileVisibility.PUBLIC,
                selected = visibility,
                label = "Public — your name is shown",
                sub = "Display name appears as the byline on your public drives and comments.",
                onSelect = { visibility = it },
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            HorizontalDivider()
            androidx.compose.material3.OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenTrash,
            ) {
                Text(if (trashedCount > 0) "Recently deleted ($trashedCount)" else "Recently deleted")
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                onClick = {
                    error = null
                    vm.save(
                        displayName = displayName,
                        bio = bio,
                        visibility = visibility,
                        onSuccess = {
                            Toast.makeText(context, "Profile saved", Toast.LENGTH_SHORT).show()
                            onBack()
                        },
                        onError = { error = it },
                    )
                },
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save")
                }
            }
        }
        }
    }
}

@Composable
private fun ProfileHeader(p: Profile) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = p.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(CircleShape),
        )
        Spacer(Modifier.size(16.dp))
        Column {
            Text(p.displayName.ifBlank { p.anonHandle }, style = MaterialTheme.typography.titleLarge)
            Text(
                "${p.points} points · ${p.drivesPublished} public drives",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VisibilityRow(
    value: ProfileVisibility,
    selected: ProfileVisibility,
    label: String,
    sub: String,
    onSelect: (ProfileVisibility) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Spacer(Modifier.size(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                sub,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
