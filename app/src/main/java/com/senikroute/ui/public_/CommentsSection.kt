package com.senikroute.ui.public_

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.senikroute.data.comments.Comment
import com.senikroute.data.comments.CommentNode
import com.senikroute.data.comments.buildCommentTree
import java.text.DateFormat
import java.util.Date

@Composable
fun CommentsSection(
    comments: List<Comment>,
    isOwner: Boolean,
    isSignedIn: Boolean,
    isEmailVerified: Boolean,
    currentUid: String?,
    onPost: (body: String, parentId: String?) -> Unit,
    onMarkHelpful: (commentId: String, helpful: Boolean, parentId: String?) -> Unit,
    onDelete: (commentId: String) -> Unit,
    onAuthorClick: (uid: String) -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tree = remember(comments) { buildCommentTree(comments) }

    Column(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Discussion (${tree.size})",
            style = MaterialTheme.typography.titleLarge,
        )

        when {
            !isSignedIn -> {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onSignInClick) {
                    Text("Sign in to ask a question")
                }
            }
            !isEmailVerified -> {
                Text(
                    "Verify your email to participate in discussions.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                CommentInput(
                    placeholder = "Ask a question",
                    submitLabel = "Post",
                    onSubmit = { body -> onPost(body, null) },
                )
            }
        }

        if (tree.isEmpty()) {
            Text(
                "No questions yet. Be the first to ask.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            tree.forEach { node ->
                ThreadView(
                    node = node,
                    isOwner = isOwner,
                    canReply = isSignedIn && isEmailVerified,
                    currentUid = currentUid,
                    onPost = onPost,
                    onMarkHelpful = onMarkHelpful,
                    onDelete = onDelete,
                    onAuthorClick = onAuthorClick,
                )
            }
        }
    }
}

@Composable
private fun ThreadView(
    node: CommentNode,
    isOwner: Boolean,
    canReply: Boolean,
    currentUid: String?,
    onPost: (body: String, parentId: String?) -> Unit,
    onMarkHelpful: (commentId: String, helpful: Boolean, parentId: String?) -> Unit,
    onDelete: (commentId: String) -> Unit,
    onAuthorClick: (uid: String) -> Unit,
) {
    var replying by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CommentCard(
            comment = node.comment,
            isOwnerOfDrive = isOwner,
            isAuthor = currentUid == node.comment.authorUid,
            isTopLevel = true,
            parentId = null,
            onMarkHelpful = onMarkHelpful,
            onDelete = onDelete,
            onAuthorClick = onAuthorClick,
        )
        node.replies.forEach { reply ->
            Row {
                Spacer(Modifier.width(24.dp))
                CommentCard(
                    comment = reply,
                    isOwnerOfDrive = isOwner,
                    isAuthor = currentUid == reply.authorUid,
                    isTopLevel = false,
                    parentId = node.comment.id,
                    onMarkHelpful = onMarkHelpful,
                    onDelete = onDelete,
                    onAuthorClick = onAuthorClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (canReply) {
            if (replying) {
                Row {
                    Spacer(Modifier.width(24.dp))
                    CommentInput(
                        placeholder = "Reply",
                        submitLabel = "Reply",
                        onSubmit = { body ->
                            onPost(body, node.comment.id)
                            replying = false
                        },
                        onCancel = { replying = false },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                TextButton(onClick = { replying = true }) {
                    Text("Reply")
                }
            }
        }
    }
}

@Composable
private fun CommentCard(
    comment: Comment,
    isOwnerOfDrive: Boolean,
    isAuthor: Boolean,
    isTopLevel: Boolean,
    parentId: String?,
    onMarkHelpful: (commentId: String, helpful: Boolean, parentId: String?) -> Unit,
    onDelete: (commentId: String) -> Unit,
    onAuthorClick: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canMarkHelpful = isOwnerOfDrive && !isTopLevel
    val canDelete = isAuthor || isOwnerOfDrive
    val highlight = if (comment.isHelpfulAnswer) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    Card(modifier = modifier.fillMaxWidth(), colors = highlight) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorAnonHandle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                )
                Text(
                    " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(comment.createdAt)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (comment.isHelpfulAnswer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "helpful",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(comment.body, style = MaterialTheme.typography.bodyLarge)
            Row {
                Spacer(Modifier.weight(1f))
                if (canMarkHelpful) {
                    TextButton(
                        onClick = { onMarkHelpful(comment.id, !comment.isHelpfulAnswer, parentId) },
                    ) {
                        Icon(
                            if (comment.isHelpfulAnswer) Icons.Filled.Check else Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (comment.isHelpfulAnswer) "Unmark helpful" else "Mark helpful")
                    }
                }
                if (canDelete) {
                    IconButton(onClick = { onDelete(comment.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentInput(
    placeholder: String,
    submitLabel: String,
    onSubmit: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (onCancel != null) {
                TextButton(onClick = { onCancel(); text = "" }) { Text("Cancel") }
            }
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    onSubmit(text)
                    text = ""
                },
            ) { Text(submitLabel) }
        }
    }
}

