package com.senikroute.data.repo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeohashTest {

    @Test fun encode_returns_requested_precision() {
        assertThat(encodeGeohash(40.0, -73.0, precision = 5)).hasLength(5)
        assertThat(encodeGeohash(40.0, -73.0, precision = 9)).hasLength(9)
        assertThat(encodeGeohash(0.0, 0.0, precision = 1)).hasLength(1)
    }

    @Test fun encode_known_value_paris() {
        // Paris (48.8566, 2.3522) at precision 5 → "u09tv"
        // Standard geohash; spot-check rather than exhaustive.
        val g = encodeGeohash(48.8566, 2.3522, precision = 5)
        assertThat(g).isEqualTo("u09tv")
    }

    @Test fun encode_known_value_nyc() {
        // NYC (40.7128, -74.0060) at precision 5 → "dr5re"
        val g = encodeGeohash(40.7128, -74.0060, precision = 5)
        assertThat(g).isEqualTo("dr5re")
    }

    @Test fun encode_uses_only_base32_alphabet() {
        val allowed = "0123456789bcdefghjkmnpqrstuvwxyz".toSet()
        repeat(50) {
            val lat = (-90.0..90.0).random()
            val lng = (-180.0..180.0).random()
            val g = encodeGeohash(lat, lng, precision = 9)
            for (c in g) assertThat(allowed).contains(c)
        }
    }

    @Test fun encode_prefixes_consistent_for_nearby_points() {
        // Two points within a small box should share at least the first 4 chars.
        val a = encodeGeohash(40.7128, -74.0060, precision = 9)
        val b = encodeGeohash(40.7130, -74.0061, precision = 9)
        assertThat(a.take(4)).isEqualTo(b.take(4))
    }

    private fun ClosedFloatingPointRange<Double>.random(): Double {
        val r = java.util.Random(this.start.toRawBits().toLong() xor this.endInclusive.toRawBits().toLong())
        return this.start + (this.endInclusive - this.start) * r.nextDouble()
    }
}
