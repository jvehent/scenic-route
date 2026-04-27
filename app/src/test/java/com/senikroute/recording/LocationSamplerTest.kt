package com.senikroute.recording

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
        val noisy = s.accept(fix(40.00006, -73.0, 5000L, accuracy = 80f))
        assertThat(noisy).isNull()
    }

    @Test fun rejects_below_min_interval() {
        val s = LocationSampler(minIntervalMs = 2_000L)
        s.accept(fix(0.0, 0.0, 1_000L))
        // 500 ms later: rejected by the time gate.
        val out = s.accept(fix(0.001, 0.0, 1_500L))
        assertThat(out).isNull()
    }

    @Test fun accepts_after_time_gate_regardless_of_displacement() {
        val s = LocationSampler(minIntervalMs = 2_000L)
        s.accept(fix(0.0, 0.0, 1_000L))
        // ~5 m of movement, well past the 2 s gate — accepted because sampling is now
        // purely time-based (the old "displacement OR heading" gate was removed so users
        // can configure dense GPS sampling without losing points to a stationary car).
        val out = s.accept(fix(0.000045, 0.0, 5_000L))
        assertThat(out).isNotNull()
    }

    @Test fun heading_change_bypasses_time_gate() {
        val s = LocationSampler(minIntervalMs = 10_000L, headingChangeDeg = 20f)
        s.accept(fix(0.0, 0.0, 1_000L, bearing = 90f))
        // 1 s later, 90° pivot — heading change opens the gate so we capture the turn
        // even when the user picked a long sampling interval.
        val out = s.accept(fix(0.000045, 0.0, 2_000L, bearing = 0f))
        assertThat(out).isNotNull()
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
