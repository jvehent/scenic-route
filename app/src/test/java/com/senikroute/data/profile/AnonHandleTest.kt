package com.senikroute.data.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnonHandleTest {

    @Test fun starts_with_traveler_prefix() {
        assertThat(anonHandleFor("any-uid")).startsWith("traveler-")
    }

    @Test fun has_six_hex_suffix() {
        val h = anonHandleFor("some-uid-here")
        val suffix = h.removePrefix("traveler-")
        assertThat(suffix).hasLength(6)
        assertThat(suffix).matches("[0-9a-f]+")
    }

    @Test fun deterministic_for_same_uid() {
        val a = anonHandleFor("ABC123")
        val b = anonHandleFor("ABC123")
        assertThat(a).isEqualTo(b)
    }

    @Test fun is_not_a_constant_function() {
        // The audit flagged the 24-bit space + Int.hashCode clustering on similar inputs;
        // so we don't assert "all unique", we assert the function isn't degenerate.
        // The Cloud Function (profileBootstrap.ts) is the real anti-collision via
        // crypto.randomBytes — this client-side helper is only a fallback.
        val handles = setOf(
            anonHandleFor("alpha"),
            anonHandleFor("zebra"),
            anonHandleFor("middlename-xyz"),
            anonHandleFor("a-very-long-uid-string-with-mixed-content"),
        )
        assertThat(handles.size).isAtLeast(2)
    }
}
