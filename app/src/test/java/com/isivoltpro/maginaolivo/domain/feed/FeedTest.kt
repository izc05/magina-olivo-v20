package com.isivoltpro.maginaolivo.domain.feed

import com.isivoltpro.maginaolivo.domain.weather.WeatherCodec
import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTest {
    private val fetched = Instant.parse("2026-11-26T09:00:00Z")

    @Test
    fun weatherIsStaleOnlyPastItsWindow() {
        assertFalse(FeedAge.isStale(FeedKind.WEATHER, fetched, fetched.plusSeconds(3 * 3600)))
        assertTrue(FeedAge.isStale(FeedKind.WEATHER, fetched, fetched.plusSeconds(3 * 3600 + 1)))
    }

    @Test
    fun theAgeIsSaidInWords() {
        assertEquals("Actualizado ahora", FeedAge.label(fetched, fetched.plusSeconds(60)))
        assertEquals("Actualizado hace 5 min", FeedAge.label(fetched, fetched.plusSeconds(5 * 60)))
        assertEquals("Actualizado hace 3 h", FeedAge.label(fetched, fetched.plusSeconds(3 * 3600 + 120)))
        assertEquals("Actualizado hace 2 días", FeedAge.label(fetched, fetched.plusSeconds(49 * 3600)))
        // A clock moved backwards never shows a negative age.
        assertEquals("Actualizado ahora", FeedAge.label(fetched, fetched.minusSeconds(600)))
    }

    @Test
    fun oneSharedPlaceOrNone() {
        val bedmar = FeedLocation.common(listOf("Bedmar" to "Jaén", " bedmar " to "Jaen"))
        assertEquals("Bedmar, Jaén", bedmar?.label)
        assertEquals("bedmar|jaen", bedmar?.key)
        assertNull(FeedLocation.common(listOf("Bedmar" to "Jaén", "Jódar" to "Jaén")))
        assertNull(FeedLocation.common(listOf(null to "Jaén", "  " to null)))
        assertEquals("Úbeda", FeedLocation.common(listOf("Úbeda" to null, null to null))?.label)
    }

    @Test
    fun theCacheCodecRoundTripsAndRefusesPartialValues() {
        val weather = WeatherNow(22, WeatherCondition.PARTLY_CLOUDY, 15, null, Instant.parse("2026-11-26T10:00:00Z"))
        assertEquals(weather, WeatherCodec.decode(WeatherCodec.encode(weather)))
        val credited = weather.copy(
            condition = WeatherCondition.HAZE,
            updatedAt = Instant.parse("2026-11-26T09:40:00Z"),
            attribution = "© AEMET. Información elaborada por la Agencia Estatal de Meteorología.",
        )
        assertEquals(credited, WeatherCodec.decode(WeatherCodec.encode(credited)))
        assertNull(WeatherCodec.decode("t=22\nc=CLEAR"))
        assertNull(WeatherCodec.decode("t=22\nc=HAIL\nat=1"))
        assertNull(WeatherCodec.decode(""))
    }
}
