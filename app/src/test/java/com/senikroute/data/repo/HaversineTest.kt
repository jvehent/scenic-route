package com.senikroute.data.repo

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

class HaversineTest {

    @Test fun zero_when_same_point() {
        assertThat(haversineMeters(40.0, -73.0, 40.0, -73.0)).isWithin(0.001).of(0.0)
    }

    @Test fun paris_to_nyc_about_5837_km() {
        val paris = 48.8566 to 2.3522
        val nyc = 40.7128 to -74.0060
        val km = haversineMeters(paris.first, paris.second, nyc.first, nyc.second) / 1000.0
        // Reference value ~5837 km. Allow a percent of error for spherical-Earth approximation.
        assertThat(km).isWithin(60.0).of(5837.0)
    }

    @Test fun one_degree_latitude_about_111_km() {
        val km = haversineMeters(0.0, 0.0, 1.0, 0.0) / 1000.0
        assertThat(km).isWithin(0.5).of(111.195)
    }

    @Test fun symmetric() {
        val a = haversineMeters(34.05, -118.25, 51.51, -0.13)
        val b = haversineMeters(51.51, -0.13, 34.05, -118.25)
        assertThat(abs(a - b)).isLessThan(0.001)
    }
}
