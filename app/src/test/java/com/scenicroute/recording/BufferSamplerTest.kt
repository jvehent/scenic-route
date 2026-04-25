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
class BufferSamplerTest {

    private fun fix(lat: Double, lng: Double, t: Long, accuracy: Float? = 10f): Location =
        Location("test").apply {
            latitude = lat
            longitude = lng
            time = t
            accuracy?.let { this.accuracy = it }
        }

    @Test fun first_fix_accepted_and_recorded() {
        val s = BufferSampler()
        val out = s.accept(fix(0.0, 0.0, 1_000L))
        assertThat(out).isNotNull()
        assertThat(out!!.lat).isEqualTo(0.0)
    }

    @Test fun rejects_low_accuracy() {
        val s = BufferSampler(maxAccuracyM = 50f)
        val out = s.accept(fix(0.0, 0.0, 1_000L, accuracy = 75f))
        assertThat(out).isNull()
    }

    @Test fun rejects_below_min_displacement() {
        val s = BufferSampler(minDisplacementM = 30.0)
        s.accept(fix(0.0, 0.0, 1_000L))
        // 0.0001° ≈ 11 m, below 30 m threshold.
        val out = s.accept(fix(0.0001, 0.0, 2_000L))
        assertThat(out).isNull()
    }

    @Test fun accepts_above_min_displacement() {
        val s = BufferSampler(minDisplacementM = 30.0)
        s.accept(fix(0.0, 0.0, 1_000L))
        // 0.001° lat ≈ 111 m, comfortably over 30 m.
        val out = s.accept(fix(0.001, 0.0, 2_000L))
        assertThat(out).isNotNull()
    }

    @Test fun reset_clears_state() {
        val s = BufferSampler(minDisplacementM = 30.0)
        s.accept(fix(0.0, 0.0, 1_000L))
        s.reset()
        // After reset, even a tiny displacement is accepted (first fix again).
        val out = s.accept(fix(0.00001, 0.0, 1_500L))
        assertThat(out).isNotNull()
    }
}
