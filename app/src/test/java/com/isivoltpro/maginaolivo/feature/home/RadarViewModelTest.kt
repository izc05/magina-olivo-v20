package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.domain.weather.RadarFrame
import com.isivoltpro.maginaolivo.domain.weather.RadarFrames
import com.isivoltpro.maginaolivo.domain.weather.RadarSource
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Phase 20B-radar: live only; offline says so; the latest picture first; times said plainly. */
@OptIn(ExperimentalCoroutinesApi::class)
class RadarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val radar = RadarFrames(
        provider = "RainViewer",
        attribution = "Radar: RainViewer (rainviewer.com)",
        updatedAt = Instant.parse("2026-09-25T11:05:26Z"),
        frames = listOf(
            RadarFrame(Instant.parse("2026-09-25T10:40:00Z"), "https://tilecache.rainviewer.com/v2/radar/a/256/{z}/{x}/{y}/2/1_1.png"),
            RadarFrame(Instant.parse("2026-09-25T10:50:00Z"), "https://tilecache.rainviewer.com/v2/radar/b/256/{z}/{x}/{y}/2/1_1.png"),
            RadarFrame(Instant.parse("2026-09-25T11:00:00Z"), "https://tilecache.rainviewer.com/v2/radar/c/256/{z}/{x}/{y}/2/1_1.png"),
        ),
    )

    private fun source(answer: () -> RadarFrames) = object : RadarSource {
        override suspend fun frames(): RadarFrames = answer()
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun withoutASourceTheRadarSaysItIsNotConfigured() {
        assertEquals(RadarUiState.NotConfigured, RadarViewModel(null).state.value)
    }

    @Test fun theLatestPictureIsShownFirstAndFramesCanBeBrowsed() = runTest(dispatcher) {
        val viewModel = RadarViewModel(source { radar })
        advanceUntilIdle()
        val ready = viewModel.state.value as RadarUiState.Ready
        assertEquals(2, ready.index)
        assertEquals(radar.latest, ready.frame)
        viewModel.showFrame(0)
        assertEquals(0, (viewModel.state.value as RadarUiState.Ready).index)
        viewModel.showFrame(99)
        assertEquals(2, (viewModel.state.value as RadarUiState.Ready).index)
    }

    @Test fun offlineSaysSoAndRetryLoadsAgain() = runTest(dispatcher) {
        var online = false
        val viewModel = RadarViewModel(source { if (online) radar else throw IOException("no connection") })
        advanceUntilIdle()
        assertEquals(RadarUiState.Unavailable, viewModel.state.value)
        online = true
        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.state.value is RadarUiState.Ready)
    }

    @Test fun thePictureTimeIsSaidInLocalTime() {
        val madrid = ZoneId.of("Europe/Madrid")
        val latest = Instant.parse("2026-09-25T11:00:00Z")
        assertEquals("Radar de las 13:00", radarTimeLabel(latest, latest, madrid))
        assertEquals(
            "Radar de las 12:40 · 20 min antes del último",
            radarTimeLabel(Instant.parse("2026-09-25T10:40:00Z"), latest, madrid),
        )
    }
}
