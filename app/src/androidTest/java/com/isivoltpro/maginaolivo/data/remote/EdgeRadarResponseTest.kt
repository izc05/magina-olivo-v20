package com.isivoltpro.maginaolivo.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.remote.weather.EdgeRadarResponse
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 20B-radar — the app reads the `weather-radar` contract exactly. The sample is cut from
 * the real answer stored in docs/06-testing/evidence/phase20b/weather-radar-frames.json.
 */
@RunWith(AndroidJUnit4::class)
class EdgeRadarResponseTest {
    private val real = """
        {"provider":"RAINVIEWER","providerName":"RainViewer","attribution":"Radar: RainViewer (rainviewer.com)",
         "updatedAt":"2026-09-25T11:05:26.000Z","fetchedAt":"2026-09-25T11:09:59.380Z",
         "frames":[
          {"time":"2026-09-25T11:00:00.000Z","tileUrlTemplate":"https://tilecache.rainviewer.com/v2/radar/5415fe0e827c/256/{z}/{x}/{y}/2/1_1.png"},
          {"time":"2026-09-25T10:40:00.000Z","tileUrlTemplate":"https://tilecache.rainviewer.com/v2/radar/7d610414bf04/256/{z}/{x}/{y}/2/1_1.png"},
          {"time":"2026-09-25T10:50:00.000Z","tileUrlTemplate":"https://tilecache.rainviewer.com/v2/radar/5cdb09f96f18/256/{z}/{x}/{y}/2/1_1.png"}]}
    """.trimIndent()

    @Test
    fun theRealAnswerGivesFramesOldestFirstWithTheirCredit() {
        val radar = EdgeRadarResponse.parse(real)
        assertEquals("RainViewer", radar.provider)
        assertEquals("Radar: RainViewer (rainviewer.com)", radar.attribution)
        assertEquals(Instant.parse("2026-09-25T11:05:26Z"), radar.updatedAt)
        assertEquals(
            listOf("2026-09-25T10:40:00Z", "2026-09-25T10:50:00Z", "2026-09-25T11:00:00Z").map(Instant::parse),
            radar.frames.map { it.time },
        )
        assertEquals("https://tilecache.rainviewer.com/v2/radar/5415fe0e827c/256/{z}/{x}/{y}/2/1_1.png", radar.latest.tileUrlTemplate)
    }

    @Test
    fun anythingUnusableIsNoRadarNeverAGuess() {
        // No frames.
        assertThrows(Exception::class.java) {
            EdgeRadarResponse.parse("""{"provider":"RAINVIEWER","attribution":"Radar: RainViewer","frames":[]}""")
        }
        // Plain http or a template without tile coordinates.
        assertThrows(Exception::class.java) {
            EdgeRadarResponse.parse(
                """{"provider":"RAINVIEWER","attribution":"Radar: RainViewer","frames":[{"time":"2026-09-25T11:00:00Z","tileUrlTemplate":"http://x/{z}/{x}/{y}.png"}]}""",
            )
        }
        assertThrows(Exception::class.java) {
            EdgeRadarResponse.parse(
                """{"provider":"RAINVIEWER","attribution":"Radar: RainViewer","frames":[{"time":"2026-09-25T11:00:00Z","tileUrlTemplate":"https://x/tile.png"}]}""",
            )
        }
        // No credit for the provider.
        assertThrows(Exception::class.java) {
            EdgeRadarResponse.parse(
                """{"provider":"RAINVIEWER","frames":[{"time":"2026-09-25T11:00:00Z","tileUrlTemplate":"https://x/{z}/{x}/{y}.png"}]}""",
            )
        }
    }
}
