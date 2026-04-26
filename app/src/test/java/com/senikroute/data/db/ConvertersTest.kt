package com.senikroute.data.db

import com.google.common.truth.Truth.assertThat
import com.senikroute.data.model.BoundingBox
import com.senikroute.data.model.VehicleReqs
import com.senikroute.data.model.VehicleSummary
import org.junit.Test

class ConvertersTest {

    private val c = Converters()

    @Test fun boundingBox_roundtrip() {
        val b = BoundingBox(north = 50.0, south = 40.0, east = 0.5, west = -10.25)
        val s = c.boundingBoxToString(b)!!
        assertThat(c.stringToBoundingBox(s)).isEqualTo(b)
    }

    @Test fun boundingBox_null_roundtrip() {
        assertThat(c.boundingBoxToString(null)).isNull()
        assertThat(c.stringToBoundingBox(null)).isNull()
    }

    @Test fun vehicleReqs_roundtrip_full() {
        val v = VehicleReqs(
            requires4wd = true,
            maxWidthM = 2.1,
            maxHeightM = 3.0,
            rvFriendly = false,
            tags = listOf("steep_grade", "narrow"),
            notes = "tight switchbacks",
        )
        val s = c.vehicleReqsToString(v)!!
        assertThat(c.stringToVehicleReqs(s)).isEqualTo(v)
    }

    @Test fun vehicleReqs_roundtrip_minimal() {
        val v = VehicleReqs(tags = listOf("toll"))
        val s = c.vehicleReqsToString(v)!!
        assertThat(c.stringToVehicleReqs(s)).isEqualTo(v)
    }

    @Test fun vehicleSummary_roundtrip() {
        val v = VehicleSummary(
            requires4wd = true,
            maxRecommendedWidthM = 1.8,
            rvFriendly = false,
            tagSet = listOf("unpaved"),
            notes = listOf("rocky"),
        )
        val s = c.vehicleSummaryToString(v)!!
        assertThat(c.stringToVehicleSummary(s)).isEqualTo(v)
    }

    @Test fun stringList_roundtrip() {
        val list = listOf("foo", "bar baz", "qux,quux")
        val s = c.stringListToString(list)
        assertThat(c.stringToStringList(s)).containsExactlyElementsIn(list).inOrder()
    }

    @Test fun stringList_empty_roundtrip() {
        assertThat(c.stringToStringList(c.stringListToString(emptyList()))).isEmpty()
        // The DAO may also pass back an empty string from a legacy row.
        assertThat(c.stringToStringList("")).isEmpty()
    }
}
