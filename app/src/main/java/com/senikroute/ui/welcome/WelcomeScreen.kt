package com.senikroute.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.senikroute.R
import com.senikroute.data.discovery.DiscoveryDrive
import com.senikroute.ui.layout.FormMaxWidth
import com.senikroute.ui.theme.SenikBrandTitle
import kotlin.math.absoluteValue

@Composable
fun WelcomeScreen(
    onSignIn: () -> Unit,
    onBrowseAsGuest: () -> Unit,
    onDriveClick: (String) -> Unit,
    vm: WelcomeViewModel = hiltViewModel(),
) {
    val featured by vm.featured.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // Brand mark pinned to the top-left, fixed across orientation/scroll changes.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) { SenikBrandTitle() }

        // Main content centered vertically in the remaining viewport (offset down so it
        // doesn't collide with the brand mark). When the column overflows, the inner
        // verticalScroll lets the user scroll through it without losing the centered feel.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = FormMaxWidth * 1.5f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HeaderBlock()

            Text(
                stringResource(R.string.welcome_featured_label),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            when {
                loading && featured.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                featured.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.welcome_featured_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> FeaturedCarousel(featured, onDriveClick)
            }

            ActionsBlock(onSignIn = onSignIn, onBrowseAsGuest = onBrowseAsGuest)
        }
        }
    }
}

@Composable
private fun HeaderBlock() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.welcome_tagline),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** LazyRow so we only compose & load images for visible cards. */
@Composable
private fun FeaturedCarousel(drives: List<DiscoveryDrive>, onDriveClick: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(drives, key = { it.driveId }) { d ->
            FeaturedCard(d) { onDriveClick(d.driveId) }
        }
    }
}

@Composable
private fun FeaturedCard(drive: DiscoveryDrive, onClick: () -> Unit) {
    val gradient = remember(drive.driveId) { fallbackGradientFor(drive.driveId) }
    Card(
        modifier = Modifier
            .width(280.dp)
            .height(220.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 1: cover photo if we have one, otherwise the colored gradient.
            if (!drive.coverPhotoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = drive.coverPhotoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(brush = gradient),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(brush = gradient))
            }
            // Layer 2: bottom-up dark gradient so the text always reads against any photo.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        ),
                    ),
            )
            // Layer 3: text content.
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                drive.tags.firstOrNull()?.let {
                    Text(
                        "#$it",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        drive.title.ifBlank { "Untitled drive" },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    Text(
                        "%.0f km · %d min".format(drive.distanceM / 1000.0, drive.durationS / 60),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                    drive.ownerAnonHandle?.let {
                        Text(
                            "by $it",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsBlock(onSignIn: () -> Unit, onBrowseAsGuest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSignIn,
        ) { Text(stringResource(R.string.welcome_signin_cta)) }
        TextButton(onClick = onBrowseAsGuest) {
            Text(stringResource(R.string.welcome_browse_cta))
        }
    }
}

/** Used as a placeholder backdrop while the cover photo loads, and as a fallback if absent. */
private fun fallbackGradientFor(driveId: String): Brush {
    val palettes = listOf(
        listOf(Color(0xFF2E6E4B), Color(0xFF1B4332)),
        listOf(Color(0xFFB55A21), Color(0xFF7A2E0E)),
        listOf(Color(0xFF1E3A8A), Color(0xFF0C1E4A)),
        listOf(Color(0xFF6E6A5E), Color(0xFF3B382F)),
        listOf(Color(0xFF065F46), Color(0xFF022C20)),
        listOf(Color(0xFF7C2D12), Color(0xFF431407)),
        listOf(Color(0xFF1F2937), Color(0xFF030712)),
        listOf(Color(0xFF0E7490), Color(0xFF083344)),
    )
    val idx = (driveId.hashCode().absoluteValue) % palettes.size
    return Brush.linearGradient(colors = palettes[idx])
}
