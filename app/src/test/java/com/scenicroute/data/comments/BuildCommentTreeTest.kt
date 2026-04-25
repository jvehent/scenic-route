package com.scenicroute.data.comments

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildCommentTreeTest {

    private fun comment(
        id: String,
        parent: String? = null,
        createdAt: Long = 0L,
        deleted: Boolean = false,
    ) = Comment(
        id = id,
        driveId = "d",
        authorUid = "u",
        authorAnonHandle = "traveler-a",
        waypointId = null,
        parentCommentId = parent,
        body = "body",
        createdAt = createdAt,
        editedAt = null,
        isHelpfulAnswer = false,
        deleted = deleted,
    )

    @Test fun empty_input_yields_empty_tree() {
        assertThat(buildCommentTree(emptyList())).isEmpty()
    }

    @Test fun top_level_only_grouped_in_chrono_order() {
        val tree = buildCommentTree(listOf(
            comment("c", createdAt = 30),
            comment("a", createdAt = 10),
            comment("b", createdAt = 20),
        ))
        assertThat(tree.map { it.comment.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(tree.flatMap { it.replies }).isEmpty()
    }

    @Test fun replies_attached_to_parents_in_chrono_order() {
        val tree = buildCommentTree(listOf(
            comment("q1", createdAt = 1),
            comment("r1b", parent = "q1", createdAt = 20),
            comment("r1a", parent = "q1", createdAt = 10),
            comment("q2", createdAt = 5),
            comment("r2", parent = "q2", createdAt = 6),
        ))
        assertThat(tree.map { it.comment.id }).containsExactly("q1", "q2").inOrder()
        val q1 = tree.first { it.comment.id == "q1" }
        assertThat(q1.replies.map { it.id }).containsExactly("r1a", "r1b").inOrder()
        val q2 = tree.first { it.comment.id == "q2" }
        assertThat(q2.replies.map { it.id }).containsExactly("r2")
    }

    @Test fun deleted_comments_dropped_entirely() {
        val tree = buildCommentTree(listOf(
            comment("a", createdAt = 10),
            comment("b", createdAt = 20, deleted = true),
            comment("c", parent = "a", createdAt = 30, deleted = true),
        ))
        assertThat(tree.map { it.comment.id }).containsExactly("a")
        assertThat(tree.first().replies).isEmpty()
    }

    @Test fun reply_to_unknown_parent_is_orphaned_and_dropped() {
        // Reply whose parent isn't in the input doesn't promote to top-level.
        val tree = buildCommentTree(listOf(
            comment("a", createdAt = 10),
            comment("orphan", parent = "missing-parent", createdAt = 20),
        ))
        assertThat(tree.map { it.comment.id }).containsExactly("a")
    }
}
