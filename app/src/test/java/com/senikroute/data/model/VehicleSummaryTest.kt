package com.senikroute.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VehicleSummaryTest {

    @Test fun summarize_empty_returns_defaults() {
        val s = VehicleSummary.summarize(emptyList())
        assertThat(s.requires4wd).isFalse()
        assertThat(s.rvFriendly).isTrue()
        assertThat(s.maxRecommendedWidthM).isNull()
        assertThat(s.maxRecommendedHeightM).isNull()
        assertThat(s.tagSet).isEmpty()
    }

    @Test fun summarize_promotes_4wd_if_any_requires() {
        val s = VehicleSummary.summarize(listOf(
            VehicleReqs(),
            VehicleReqs(requires4wd = true),
            VehicleReqs(requires4wd = false),
        ))
        assertThat(s.requires4wd).isTrue()
    }

    @Test fun summarize_picks_smallest_width_height() {
        val s = VehicleSummary.summarize(listOf(
            VehicleReqs(maxWidthM = 2.5, maxHeightM = 3.0),
            VehicleReqs(maxWidthM = 1.8, maxHeightM = 2.2),
            VehicleReqs(maxWidthM = 2.0),
        ))
        assertThat(s.maxRecommendedWidthM).isEqualTo(1.8)
        assertThat(s.maxRecommendedHeightM).isEqualTo(2.2)
    }

    @Test fun summarize_marks_not_rvFriendly_if_any_says_no() {
        val s = VehicleSummary.summarize(listOf(
            VehicleReqs(),
            VehicleReqs(rvFriendly = false),
        ))
        assertThat(s.rvFriendly).isFalse()
    }

    @Test fun summarize_dedupes_tags_across_waypoints() {
        val s = VehicleSummary.summarize(listOf(
            VehicleReqs(tags = listOf("steep_grade", "narrow")),
            VehicleReqs(tags = listOf("narrow", "unpaved")),
        ))
        assertThat(s.tagSet).containsExactly("steep_grade", "narrow", "unpaved")
    }

    @Test fun summarize_collects_nonblank_notes() {
        val s = VehicleSummary.summarize(listOf(
            VehicleReqs(notes = "tight switchbacks"),
            VehicleReqs(notes = ""),
            VehicleReqs(notes = "   "),
            VehicleReqs(notes = "river crossing"),
        ))
        assertThat(s.notes).containsExactly("tight switchbacks", "river crossing")
    }

    @Test fun isEmpty_true_for_default() {
        assertThat(VehicleReqs().isEmpty).isTrue()
    }

    @Test fun isEmpty_false_when_any_field_set() {
        assertThat(VehicleReqs(requires4wd = true).isEmpty).isFalse()
        assertThat(VehicleReqs(tags = listOf("x")).isEmpty).isFalse()
        assertThat(VehicleReqs(notes = "hi").isEmpty).isFalse()
    }
}
