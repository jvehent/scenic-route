package com.scenicroute.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.scenicroute.ui.layout.FormMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenicroute.data.model.Visibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveReviewScreen(
    onSaved: () -> Unit,
    onDiscarded: () -> Unit,
    vm: DriveReviewViewModel = hiltViewModel(),
) {
    val drive by vm.drive.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(Visibility.PRIVATE) }
    var tagsCsv by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(drive) {
        val d = drive
        if (d != null && !initialized) {
            title = d.title
            description = d.description
            visibility = Visibility.fromStored(d.visibility)
            tagsCsv = d.tags.joinToString(", ")
            initialized = true
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Review drive") }) },
    ) { padding ->
        val d = drive
        if (d == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Loading…")
            }
            return@Scaffold
        }

        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = FormMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "%.1f km · %d min".format(
                    (d.distanceM ?: 0) / 1000.0,
                    (d.durationS ?: 0) / 60,
                ),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            OutlinedTextField(
                value = tagsCsv,
                onValueChange = { tagsCsv = it },
                label = { Text("Tags (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Visibility", style = MaterialTheme.typography.labelLarge)
            VisibilityRow(Visibility.PRIVATE, visibility, "Private — only you", "No one else can see this drive.") { visibility = it }
            VisibilityRow(Visibility.UNLISTED, visibility, "Unlisted — share-by-link", "Anyone with the link can view, but it's not in discovery.") { visibility = it }
            VisibilityRow(Visibility.PUBLIC, visibility, "Public — appears in discovery", "Visible in Explore; signed-in users can comment.") { visibility = it }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { vm.discard(onDiscarded) },
                ) { Text("Discard") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { vm.save(title, description, visibility, tagsCsv, onSaved) },
                ) { Text("Save") }
            }
        }
        }
    }
}

@Composable
private fun VisibilityRow(
    value: Visibility,
    selected: Visibility,
    label: String,
    sub: String,
    onSelect: (Visibility) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Spacer(Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
