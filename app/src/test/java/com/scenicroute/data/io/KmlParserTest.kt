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
class KmlParserTest {

    private fun parse(kml: String) = parseKml(ByteArrayInputStream(kml.toByteArray()))

    @Test fun multiple_point_placemarks_each_become_their_own_drive() {
        val drives = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>My Places</name>
                <Placemark>
                  <name>Lookout A</name>
                  <description>Sweeping view</description>
                  <Point><coordinates>-73.0,40.0,0</coordinates></Point>
                </Placemark>
                <Placemark>
                  <name>Lookout B</name>
                  <Point><coordinates>-74.0,41.0,0</coordinates></Point>
                </Placemark>
              </Document>
            </kml>
            """.trimIndent(),
        )
        assertThat(drives).hasSize(2)
        assertThat(drives[0].title).isEqualTo("Lookout A")
        assertThat(drives[0].description).isEqualTo("Sweeping view")
        assertThat(drives[0].trackPoints).hasSize(1)
        assertThat(drives[0].trackPoints[0].lat).isEqualTo(40.0)
        assertThat(drives[1].title).isEqualTo("Lookout B")
        assertThat(drives[1].trackPoints[0].lng).isEqualTo(-74.0)
    }

    @Test fun linestring_placemark_becomes_a_drive_with_full_track() {
        val drives = parse(
            """
            <?xml version="1.0"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>Coastal Route</name>
                  <description>Highway 1</description>
                  <LineString><coordinates>
                    -122.5,37.7,0
                    -122.4,37.6,0
                    -122.3,37.5,0
                  </coordinates></LineString>
                </Placemark>
              </Document>
            </kml>
            """.trimIndent(),
        )
        assertThat(drives).hasSize(1)
        assertThat(drives[0].title).isEqualTo("Coastal Route")
        assertThat(drives[0].trackPoints).hasSize(3)
        // KML coords are lng,lat,alt; verify we mapped them correctly.
        assertThat(drives[0].trackPoints[0].lat).isEqualTo(37.7)
        assertThat(drives[0].trackPoints[0].lng).isEqualTo(-122.5)
    }

    @Test fun mixed_kml_yields_one_drive_per_placemark() {
        val drives = parse(
            """
            <?xml version="1.0"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <Placemark>
                  <name>Pin</name>
                  <Point><coordinates>10,20,0</coordinates></Point>
                </Placemark>
                <Placemark>
                  <name>Route</name>
                  <LineString><coordinates>0,0,0 1,1,0</coordinates></LineString>
                </Placemark>
              </Document>
            </kml>
            """.trimIndent(),
        )
        assertThat(drives.map { it.title }).containsExactly("Pin", "Route").inOrder()
        assertThat(drives[0].trackPoints).hasSize(1)
        assertThat(drives[1].trackPoints).hasSize(2)
    }

    @Test fun empty_kml_yields_empty_list() {
        val drives = parse(
            """
            <?xml version="1.0"?>
            <kml xmlns="http://www.opengis.net/kml/2.2"><Document/></kml>
            """.trimIndent(),
        )
        assertThat(drives).isEmpty()
    }
}
