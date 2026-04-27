package com.senikroute.ui.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private const val PRIVACY_POLICY_URL = "https://senikroute.com/privacy-policy.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAcceptanceScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val blocks = remember {
        val raw = context.assets.open("privacy.md").bufferedReader().use { it.readText() }
        parsePrivacyMarkdown(raw)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                actions = {
                    TextButton(onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) }) {
                        Text("View online")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDecline) { Text("Decline") }
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f))
                    Button(onClick = onAccept) { Text("Accept and continue") }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(blocks) { block ->
                when (block) {
                    is PrivacyBlock.Heading -> {
                        Spacer(Modifier.padding(top = 8.dp))
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    }
                    is PrivacyBlock.Paragraph -> {
                        Text(
                            text = block.annotated,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is PrivacyBlock.Bullet -> {
                        Row {
                            Text("• ", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = block.annotated,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal sealed interface PrivacyBlock {
    data class Heading(val text: String) : PrivacyBlock
    data class Paragraph(val annotated: AnnotatedString) : PrivacyBlock
    data class Bullet(val annotated: AnnotatedString) : PrivacyBlock
}

// Parses the small markdown subset PRIVACY.md uses:
//   - lines that are entirely **Bold** become section headings
//   - lines starting with "*   " become bullets
//   - non-empty other lines become paragraphs
//   - inline **bold** and [text](url) are parsed within text
internal fun parsePrivacyMarkdown(raw: String): List<PrivacyBlock> {
    val blocks = mutableListOf<PrivacyBlock>()
    val headingOnly = Regex("^\\*\\*(.+)\\*\\*\\.?$")
    val bulletPrefix = Regex("^\\*\\s+")
    raw.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@forEach
        val headingMatch = headingOnly.matchEntire(trimmed)
        when {
            headingMatch != null -> blocks += PrivacyBlock.Heading(headingMatch.groupValues[1])
            bulletPrefix.containsMatchIn(trimmed) -> {
                val body = trimmed.replaceFirst(bulletPrefix, "")
                blocks += PrivacyBlock.Bullet(parseInline(body))
            }
            else -> blocks += PrivacyBlock.Paragraph(parseInline(trimmed))
        }
    }
    return blocks
}

// Inline parser: handles **bold** and [text](url) only. Everything else is plain text.
private fun parseInline(input: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < input.length) {
        when {
            input.startsWith("**", i) -> {
                val end = input.indexOf("**", i + 2)
                if (end < 0) { append(input.substring(i)); i = input.length }
                else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(input.substring(i + 2, end))
                    }
                    i = end + 2
                }
            }
            input[i] == '[' -> {
                val closeBracket = input.indexOf(']', i + 1)
                val openParen = if (closeBracket >= 0) closeBracket + 1 else -1
                val isLink = openParen in input.indices && input[openParen] == '('
                val closeParen = if (isLink) input.indexOf(')', openParen + 1) else -1
                if (closeParen > 0) {
                    val label = input.substring(i + 1, closeBracket)
                    val url = input.substring(openParen + 1, closeParen)
                    val link = LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = androidx.compose.ui.graphics.Color(0xFF1565C0),
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                            ),
                        ),
                    )
                    withLink(link) { append(label) }
                    i = closeParen + 1
                } else {
                    append(input[i]); i++
                }
            }
            else -> { append(input[i]); i++ }
        }
    }
}
