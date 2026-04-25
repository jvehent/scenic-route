package com.scenicroute.ui.recording

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.scenicroute.data.model.VehicleReqs
import java.io.File

data class WaypointDraft(
    val note: String,
    val vehicleReqs: VehicleReqs?,
    val photoPaths: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointSheet(
    onDismiss: () -> Unit,
    onSave: (WaypointDraft) -> Unit,
    photosVm: WaypointPhotosViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var note by remember { mutableStateOf("") }
    var requires4wd by remember { mutableStateOf(false) }
    var rvFriendly by remember { mutableStateOf(true) }
    var maxWidthText by remember { mutableStateOf("") }
    var maxHeightText by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var customTagsText by remember { mutableStateOf("") }
    var vehicleNotes by remember { mutableStateOf("") }
    var vehiclePanelExpanded by remember { mutableStateOf(false) }
    var photoPaths by remember { mutableStateOf(listOf<String>()) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { ok ->
        val path = pendingPhotoPath
        if (ok && path != null && photosVm.finalizeCaptured(path)) {
            photoPaths = photoPaths + path
        } else if (path != null) {
            photosVm.discard(path)
        }
        pendingPhotoPath = null
    }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            val path = photosVm.importFromUri(uri)
            if (path != null) photoPaths = photoPaths + path
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val (path, uri) = photosVm.newPhotoTarget()
            pendingPhotoPath = path
            takePicture.launch(uri)
        }
    }

    fun launchCapture() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val (path, uri) = photosVm.newPhotoTarget()
        pendingPhotoPath = path
        takePicture.launch(uri)
    }

    ModalBottomSheet(
        onDismissRequest = {
            photoPaths.forEach(photosVm::discard)
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add waypoint", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            PhotoStrip(
                photoPaths = photoPaths,
                onCapture = { launchCapture() },
                onPickFromGallery = {
                    pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemove = { path ->
                    photoPaths = photoPaths - path
                    photosVm.discard(path)
                },
            )

            OutlinedButton(
                onClick = { vehiclePanelExpanded = !vehiclePanelExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vehiclePanelExpanded) "Hide vehicle annotations" else "Add vehicle annotations")
            }

            if (vehiclePanelExpanded) {
                VehicleAnnotationsPanel(
                    requires4wd = requires4wd,
                    onRequires4wdChange = { requires4wd = it },
                    rvFriendly = rvFriendly,
                    onRvFriendlyChange = { rvFriendly = it },
                    maxWidthText = maxWidthText,
                    onMaxWidthChange = { maxWidthText = it },
                    maxHeightText = maxHeightText,
                    onMaxHeightChange = { maxHeightText = it },
                    selectedTags = selectedTags,
                    onToggleTag = { t ->
                        selectedTags = if (t in selectedTags) selectedTags - t else selectedTags + t
                    },
                    customTagsText = customTagsText,
                    onCustomTagsChange = { customTagsText = it },
                    vehicleNotes = vehicleNotes,
                    onVehicleNotesChange = { vehicleNotes = it },
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        photoPaths.forEach(photosVm::discard)
                        onDismiss()
                    },
                ) { Text("Cancel") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val customTags = customTagsText
                            .split(',')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        val allTags = (selectedTags + customTags).toList()
                        val reqs = VehicleReqs(
                            requires4wd = requires4wd.takeIf { vehiclePanelExpanded && requires4wd },
                            rvFriendly = if (vehiclePanelExpanded && !rvFriendly) false else null,
                            maxWidthM = maxWidthText.toDoubleOrNull(),
                            maxHeightM = maxHeightText.toDoubleOrNull(),
                            tags = allTags,
                            notes = vehicleNotes.takeIf { it.isNotBlank() },
                        )
                        val maybeReqs = if (vehiclePanelExpanded && !reqs.isEmpty) reqs else null
                        onSave(
                            WaypointDraft(
                                note = note.trim(),
                                vehicleReqs = maybeReqs,
                                photoPaths = photoPaths,
                            ),
                        )
                    },
                ) { Text("Save waypoint") }
            }
        }
    }
}

@Composable
private fun PhotoStrip(
    photoPaths: List<String>,
    onCapture: () -> Unit,
    onPickFromGallery: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onCapture, modifier = Modifier.size(80.dp)) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = "Take photo")
        }
        OutlinedButton(onClick = onPickFromGallery, modifier = Modifier.size(80.dp)) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = "Pick from gallery")
        }
        photoPaths.forEach { path ->
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.LightGray),
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                )
                IconButton(
                    onClick = { onRemove(path) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .background(Color(0x99000000), RoundedCornerShape(50)),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VehicleAnnotationsPanel(
    requires4wd: Boolean,
    onRequires4wdChange: (Boolean) -> Unit,
    rvFriendly: Boolean,
    onRvFriendlyChange: (Boolean) -> Unit,
    maxWidthText: String,
    onMaxWidthChange: (String) -> Unit,
    maxHeightText: String,
    onMaxHeightChange: (String) -> Unit,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit,
    customTagsText: String,
    onCustomTagsChange: (String) -> Unit,
    vehicleNotes: String,
    onVehicleNotesChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow("Requires 4WD", requires4wd, onRequires4wdChange)
        SwitchRow("RV-friendly", rvFriendly, onRvFriendlyChange)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = maxWidthText,
                onValueChange = onMaxWidthChange,
                label = { Text("Max width (m)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxHeightText,
                onValueChange = onMaxHeightChange,
                label = { Text("Max height (m)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }

        Text("Tags", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VehicleReqs.PREDEFINED_TAGS.forEach { tag ->
                FilterChip(
                    selected = tag in selectedTags,
                    onClick = { onToggleTag(tag) },
                    label = { Text(tag.replace('_', ' ')) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }

        OutlinedTextField(
            value = customTagsText,
            onValueChange = onCustomTagsChange,
            label = { Text("Custom tags (comma-separated)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = vehicleNotes,
            onValueChange = onVehicleNotesChange,
            label = { Text("Vehicle notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
