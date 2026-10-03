package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherDayForecast
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** #345: Inicio says what the source published about rain, and never turns null into 0 %. */
class RainLineTest {
    private val today = LocalDate.of(2026, 11, 26)
    private fun weather(probability: Int?, todayMm: Double? = null, otherDayMm: Double? = null) = WeatherNow(
        18, WeatherCondition.CLOUDY, probability, 10, Instant.parse("2026-11-26T09:00:00Z"),
        daily = listOf(
            WeatherDayForecast(today, 9, 18, WeatherCondition.CLOUDY, null, todayMm, 10),
            WeatherDayForecast(today.plusDays(1), 8, 16, WeatherCondition.RAIN, null, otherDayMm, 12),
        ),
    )

    @Test fun aemetProbabilitiesAreShownAsPublishedIncludingZeroAndHundred() {
        assertEquals("Prob. lluvia 0 %", weather(0).rainLine(today))
        assertEquals("Prob. lluvia 35 %", weather(35).rainLine(today))
        assertEquals("Prob. lluvia 100 %", weather(100).rainLine(today))
    }

    @Test fun probabilityWinsOverMillimetres() {
        assertEquals("Prob. lluvia 35 %", weather(35, todayMm = 2.5).rainLine(today))
    }

    @Test fun metNorwayWithoutProbabilityShowsTodaysMillimetres() {
        assertEquals("Lluvia prevista 2,5 mm", weather(null, todayMm = 2.5).rainLine(today))
        assertEquals("Lluvia prevista 0 mm", weather(null, todayMm = 0.0).rainLine(today))
    }

    @Test fun nothingPublishedForTodayShowsNothingNeverZeroPercent() {
        assertNull(weather(null).rainLine(today))
        assertNull(weather(null, otherDayMm = 4.0).rainLine(today))
        assertNull(weather(null, todayMm = 2.5).rainLine(null))
        assertNull(weather(null).copy(daily = emptyList()).rainLine(today))
    }
}
