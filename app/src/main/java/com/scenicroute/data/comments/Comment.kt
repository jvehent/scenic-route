package com.scenicroute.data.comments

data class Comment(
    val id: String,
    val driveId: String,
    val authorUid: String,
    val authorAnonHandle: String,
    val waypointId: String?,
    val parentCommentId: String?,
    val body: String,
    val createdAt: Long,
    val editedAt: Long?,
    val isHelpfulAnswer: Boolean,
    val deleted: Boolean,
)

data class CommentNode(
    val comment: Comment,
    val replies: List<Comment>,
)

fun buildCommentTree(comments: List<Comment>): List<CommentNode> {
    val visible = comments.filter { !it.deleted }
    val byParent = visible.groupBy { it.parentCommentId }
    val topLevel = byParent[null].orEmpty().sortedBy { it.createdAt }
    return topLevel.map { top ->
        CommentNode(
            comment = top,
            replies = byParent[top.id].orEmpty().sortedBy { it.createdAt },
        )
    }
}
