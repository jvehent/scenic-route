package com.scenicroute.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BoundingBoxTest {

    @Test fun fromPoints_empty_returnsNull() {
        assertThat(BoundingBox.fromPoints(emptyList())).isNull()
    }

    @Test fun fromPoints_single_collapses_to_point() {
        val b = BoundingBox.fromPoints(listOf(40.0 to -73.0))!!
        assertThat(b.north).isEqualTo(40.0)
        assertThat(b.south).isEqualTo(40.0)
        assertThat(b.east).isEqualTo(-73.0)
        assertThat(b.west).isEqualTo(-73.0)
    }

    @Test fun fromPoints_many_picks_extremes() {
        val b = BoundingBox.fromPoints(
            listOf(
                10.0 to 20.0,
                40.0 to -10.0,
                -5.0 to 30.0,
                25.0 to 0.0,
            ),
        )!!
        assertThat(b.north).isEqualTo(40.0)
        assertThat(b.south).isEqualTo(-5.0)
        assertThat(b.east).isEqualTo(30.0)
        assertThat(b.west).isEqualTo(-10.0)
    }

    @Test fun contains_inside_returns_true() {
        val b = BoundingBox(north = 50.0, south = 40.0, east = 0.0, west = -10.0)
        assertThat(b.contains(45.0, -5.0)).isTrue()
    }

    @Test fun contains_on_boundary_returns_true() {
        val b = BoundingBox(north = 50.0, south = 40.0, east = 0.0, west = -10.0)
        assertThat(b.contains(50.0, 0.0)).isTrue()
        assertThat(b.contains(40.0, -10.0)).isTrue()
    }

    @Test fun contains_outside_returns_false() {
        val b = BoundingBox(north = 50.0, south = 40.0, east = 0.0, west = -10.0)
        assertThat(b.contains(60.0, -5.0)).isFalse()
        assertThat(b.contains(45.0, 5.0)).isFalse()
    }
}
