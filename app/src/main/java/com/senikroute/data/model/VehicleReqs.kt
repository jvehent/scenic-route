package com.senikroute.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VehicleReqs(
    val requires4wd: Boolean? = null,
    val maxWidthM: Double? = null,
    val maxHeightM: Double? = null,
    val rvFriendly: Boolean? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
) {
    val isEmpty: Boolean
        get() = requires4wd == null &&
                maxWidthM == null &&
                maxHeightM == null &&
                rvFriendly == null &&
                tags.isEmpty() &&
                notes.isNullOrBlank()

    companion object {
        const val TAG_STEEP_GRADE = "steep_grade"
        const val TAG_NARROW = "narrow"
        const val TAG_UNPAVED = "unpaved"
        const val TAG_TIGHT_SWITCHBACKS = "tight_switchbacks"
        const val TAG_ROUGH_SURFACE = "rough_surface"
        const val TAG_RIVER_CROSSING = "river_crossing"
        const val TAG_TOLL = "toll"

        val PREDEFINED_TAGS = listOf(
            TAG_STEEP_GRADE,
            TAG_NARROW,
            TAG_UNPAVED,
            TAG_TIGHT_SWITCHBACKS,
            TAG_ROUGH_SURFACE,
            TAG_RIVER_CROSSING,
            TAG_TOLL,
        )
    }
}

@Serializable
data class VehicleSummary(
    val requires4wd: Boolean = false,
    val maxRecommendedWidthM: Double? = null,
    val maxRecommendedHeightM: Double? = null,
    val rvFriendly: Boolean = true,
    val tagSet: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
) {
    companion object {
        fun summarize(reqs: List<VehicleReqs>): VehicleSummary {
            if (reqs.isEmpty()) return VehicleSummary()
            val anyRequires4wd = reqs.any { it.requires4wd == true }
            val widths = reqs.mapNotNull { it.maxWidthM }
            val heights = reqs.mapNotNull { it.maxHeightM }
            val anyNotRvFriendly = reqs.any { it.rvFriendly == false }
            val allTags = reqs.flatMap { it.tags }.toSet().toList()
            val allNotes = reqs.mapNotNull { it.notes?.takeIf { n -> n.isNotBlank() } }
            return VehicleSummary(
                requires4wd = anyRequires4wd,
                maxRecommendedWidthM = widths.minOrNull(),
                maxRecommendedHeightM = heights.minOrNull(),
                rvFriendly = !anyNotRvFriendly,
                tagSet = allTags,
                notes = allNotes,
            )
        }
    }
}
