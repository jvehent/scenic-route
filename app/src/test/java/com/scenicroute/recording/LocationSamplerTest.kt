package com.scenicroute.recording

import android.app.Application
import android.location.Location
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocationSamplerTest {

    private fun fix(
        lat: Double,
        lng: Double,
        timeMs: Long,
        accuracy: Float? = 5f,
        bearing: Float? = null,
        altitude: Double? = null,
        speed: Float? = null,
    ): Location = Location("test").apply {
        latitude = lat
        longitude = lng
        time = timeMs
        accuracy?.let { this.accuracy = it }
        bearing?.let { this.bearing = it }
        altitude?.let { this.altitude = it }
        speed?.let { this.speed = it }
    }

    @Test fun first_fix_always_accepted() {
        val s = LocationSampler()
        val p = s.accept(fix(40.0, -73.0, 1000L))
        assertThat(p).isNotNull()
        assertThat(p!!.lat).isEqualTo(40.0)
    }

    @Test fun rejects_low_accuracy_when_better_already_seen() {
        val s = LocationSampler(maxAccuracyM = 30f)
        s.accept(fix(40.0, -73.0, 1000L, accuracy = 5f))
        // 6 m of movement would otherwise pass the displacement filter were it not for accuracy.
        val noisy = s.accept(fix(40.00006, -73.0, 5000L, accuracy = 80f))
        assertThat(noisy).isNull()
    }

    @Test fun rejects_below_min_interval() {
        val s = LocationSampler(minIntervalMs = 2_000L, minDisplacementM = 10.0)
        s.accept(fix(0.0, 0.0, 1_000L))
        // 1 second later, 50 m further: still rejected by the time gate.
        val out = s.accept(fix(0.00045, 0.0, 1_500L))
        assertThat(out).isNull()
    }

    @Test fun rejects_below_min_displacement_without_heading_change() {
        val s = LocationSampler(minIntervalMs = 1_000L, minDisplacementM = 50.0)
        s.accept(fix(0.0, 0.0, 1_000L, bearing = 90f))
        // 5 m later, same heading, well past the time gate: still rejected.
        val out = s.accept(fix(0.000045, 0.0, 5_000L, bearing = 90f))
        assertThat(out).isNull()
    }

    @Test fun accepts_on_heading_change_even_below_displacement() {
        val s = LocationSampler(minIntervalMs = 1_000L, minDisplacementM = 50.0, headingChangeDeg = 20f)
        s.accept(fix(0.0, 0.0, 1_000L, bearing = 90f))
        // 5 m further but bearing pivoted 90° — heading filter triggers.
        val out = s.accept(fix(0.000045, 0.0, 5_000L, bearing = 0f))
        assertThat(out).isNotNull()
    }

    @Test fun accepts_when_displacement_threshold_met() {
        val s = LocationSampler(minIntervalMs = 1_000L, minDisplacementM = 10.0)
        s.accept(fix(0.0, 0.0, 1_000L))
        // 1° lat ≈ 111 km, so 0.001° ≈ 111 m — well over the 10 m threshold.
        val out = s.accept(fix(0.001, 0.0, 5_000L))
        assertThat(out).isNotNull()
        assertThat(out!!.lat).isWithin(1e-6).of(0.001)
    }

    @Test fun reset_clears_state() {
        val s = LocationSampler(minIntervalMs = 2_000L)
        s.accept(fix(0.0, 0.0, 1_000L))
        s.reset()
        // After reset, the next fix is "first" again — should be accepted with no time gate.
        val out = s.accept(fix(0.0, 0.0, 1_500L))
        assertThat(out).isNotNull()
    }
}
