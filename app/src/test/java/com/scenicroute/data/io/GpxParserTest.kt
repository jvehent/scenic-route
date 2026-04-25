package com.scenicroute.data.io

import android.app.Application
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GpxParserTest {

    private fun parse(gpx: String) = parseGpx(ByteArrayInputStream(gpx.toByteArray()))

    @Test fun parses_metadata_and_trkpts() {
        val drive = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <metadata><name>My drive</name><desc>A scenic loop</desc></metadata>
              <trk>
                <name>Track</name>
                <trkseg>
                  <trkpt lat="40.0" lon="-73.0"><ele>10.0</ele><time>2024-06-01T10:00:00Z</time></trkpt>
                  <trkpt lat="40.001" lon="-73.001"><time>2024-06-01T10:00:30Z</time></trkpt>
                </trkseg>
              </trk>
            </gpx>
            """.trimIndent(),
        )
        assertThat(drive.title).isEqualTo("My drive")
        assertThat(drive.description).isEqualTo("A scenic loop")
        assertThat(drive.trackPoints).hasSize(2)
        assertThat(drive.trackPoints[0].lat).isEqualTo(40.0)
        assertThat(drive.trackPoints[0].lng).isEqualTo(-73.0)
        assertThat(drive.trackPoints[0].alt).isEqualTo(10.0)
        assertThat(drive.trackPoints[0].timeMs).isNotNull()
    }

    @Test fun parses_waypoints_with_name_and_desc() {
        val drive = parse(
            """
            <?xml version="1.0"?>
            <gpx version="1.1">
              <wpt lat="40.5" lon="-73.5">
                <name>Lookout</name>
                <desc>Great view</desc>
              </wpt>
              <trk><trkseg><trkpt lat="40.0" lon="-73.0"/></trkseg></trk>
            </gpx>
            """.trimIndent(),
        )
        assertThat(drive.waypoints).hasSize(1)
        val wp = drive.waypoints[0]
        assertThat(wp.lat).isEqualTo(40.5)
        assertThat(wp.lng).isEqualTo(-73.5)
        assertThat(wp.name).isEqualTo("Lookout")
        assertThat(wp.description).isEqualTo("Great view")
    }

    @Test fun handles_missing_metadata_gracefully() {
        val drive = parse(
            """
            <?xml version="1.0"?>
            <gpx version="1.1">
              <trk><trkseg>
                <trkpt lat="1.0" lon="2.0"/>
              </trkseg></trk>
            </gpx>
            """.trimIndent(),
        )
        assertThat(drive.title).isEmpty()
        assertThat(drive.description).isEmpty()
        assertThat(drive.trackPoints).hasSize(1)
    }
}
