package com.senikroute.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Always-on brand mark for the top-left of every screen: the Senik glyph next to
 * the word "Senik", with an optional small subtitle for screen-specific context.
 *
 * The glyph is drawn with a Compose Canvas (matching icon.svg's path data) instead
 * of loading R.mipmap.ic_launcher, because that resource is an <adaptive-icon> XML
 * and painterResource throws on AdaptiveIconDrawable in some Compose versions —
 * which is what crashed the app at startup.
 */
@Composable
fun SenikBrandTitle(subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SenikGlyph(modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Senik",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val BrandGreen = Color(0xFF2E6E4B)

/** The Senik logo: green rounded square with a white winding road and a small arrow tip. */
@Composable
fun SenikGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // The viewBox is 108x108 in icon.svg; scale every coordinate by canvasSize / 108.
        val s = size.minDimension / 108f
        // Rounded green square background.
        drawRoundRect(
            color = BrandGreen,
            topLeft = Offset.Zero,
            size = Size(size.minDimension, size.minDimension),
            cornerRadius = CornerRadius(22f * s, 22f * s),
        )
        // Winding road: M34,74 C38,62 46,58 54,58 C62,58 70,54 74,42
        val road = Path().apply {
            moveTo(34f * s, 74f * s)
            cubicTo(38f * s, 62f * s, 46f * s, 58f * s, 54f * s, 58f * s)
            cubicTo(62f * s, 58f * s, 70f * s, 54f * s, 74f * s, 42f * s)
        }
        drawPath(
            path = road,
            color = Color.White,
            style = Stroke(
                width = 4f * s,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        // Arrow tip: M72,38 l6,0 l-3,6 z
        val arrow = Path().apply {
            moveTo(72f * s, 38f * s)
            lineTo(78f * s, 38f * s)
            lineTo(75f * s, 44f * s)
            close()
        }
        drawPath(path = arrow, color = Color.White)
    }
}
