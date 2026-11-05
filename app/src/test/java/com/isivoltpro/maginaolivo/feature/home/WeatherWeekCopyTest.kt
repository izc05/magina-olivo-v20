package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherDayForecast
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** #362: the week's rain and summary say only what the source published. */
class WeatherWeekCopyTest {
    private val today = LocalDate.of(2026, 10, 3)

    @Test fun rainLineNeverInventsAProbability() {
        // AEMET: probability and millimetres.
        assertEquals("Lluvia 30 % · 1,2 mm", dayRainLine(day(0, prob = 30, mm = 1.2)))
        // MET Norway: millimetres only, and no 0 % is made up.
        assertEquals("7,9 mm", dayRainLine(day(0, mm = 7.9)))
        // A published 0 is «Sin lluvia», not a prominent «0 mm».
        assertEquals("Sin lluvia", dayRainLine(day(0, mm = 0.0)))
        assertEquals("Sin lluvia", dayRainLine(day(0, prob = 0)))
        // Nothing published: nothing claimed.
        assertNull(dayRainLine(day(0)))
    }

    @Test fun summaryCountsRainyDaysTheWarmestAndTheWettest() {
        val week = listOf(
            day(0, max = 22, mm = 0.0),
            day(1, max = 19, mm = 7.9),
            day(2, max = 25, mm = 2.0),
            day(3, max = 18, prob = 40),
        )
        assertEquals("3 días con lluvia · Máx. 25° · Mayor lluvia: mañana, 7,9 mm", weekSummary(week, today))
    }

    @Test fun summarySaysOnlyWhatIsKnown() {
        assertEquals("Sin lluvia prevista · Máx. 24°", weekSummary(listOf(day(0, max = 24, mm = 0.0)), today))
        // Codex #374: one dry day and one silent day is not a dry week.
        assertEquals("Máx. 24°", weekSummary(listOf(day(0, max = 24, mm = 0.0), day(1, max = 20)), today))
        // No rain data at all: no rain claim either way.
        assertEquals("Máx. 24°", weekSummary(listOf(day(0, max = 24)), today))
        assertNull(weekSummary(listOf(day(0)), today))
        assertNull(weekSummary(emptyList(), today))
    }

    @Test fun conditionsFallIntoTonesThatTheLabelAlsoNames() {
        assertEquals(WeatherTone.RAIN, WeatherCondition.STORM.tone())
        assertEquals(WeatherTone.CLOUD, WeatherCondition.CLOUDY.tone())
        assertEquals(WeatherTone.PARTLY, WeatherCondition.PARTLY_CLOUDY.tone())
        assertEquals(WeatherTone.SUN, WeatherCondition.CLEAR.tone())
        assertEquals(WeatherTone.NEUTRAL, (null as WeatherCondition?).tone())
    }

    @Test fun visibleForecastDaysFiltersPastDatesAndReturnsEmptyWhenAllStoredDaysHavePassed() {
        val cachedDays = listOf(day(-2, max = 20), day(-1, max = 21), day(0, max = 22), day(1, max = 23))
        assertEquals(listOf(today, today.plusDays(1)), visibleForecastDays(cachedDays, today).map { it.date })
        // When every stored day is already in the past, returns emptyList so past days are never shown as upcoming.
        val allPast = listOf(day(-3, max = 18), day(-2, max = 19))
        assertTrue(visibleForecastDays(allPast, today).isEmpty())
    }

    @Test fun advisoriesAreDerivedDeterministicallyFromPublishedFiguresOnly() {
        val calmNow = WeatherNow(18, WeatherCondition.CLEAR, 5, 10, Instant.parse("2026-10-03T10:00:00Z"))
        assertTrue(weatherAdvisories(calmNow, listOf(day(0, max = 24, mm = 0.0)), today).isEmpty())

        val adverseDays = listOf(
            WeatherDayForecast(today, 4, 18, WeatherCondition.RAIN, 80, 18.5, 20),
            WeatherDayForecast(today.plusDays(1), -1, 14, WeatherCondition.STORM, 90, 8.0, 38),
        )
        val warnings = weatherAdvisories(calmNow, adverseDays, today)
        assertEquals(
            listOf(
                "Tormenta en la previsión (mañana)",
                "Viento fuerte mañana (38 km/h) · precaución al tratar el olivar",
                "Riesgo de helada mañana (mín. -1°)",
                "Lluvia intensa hoy (18,5 mm)",
            ),
            warnings,
        )

        // When all stored days have passed and the reading is stale, no outdated advisories are claimed for today.
        val staleStorm = WeatherNow(-2, WeatherCondition.STORM, 90, 42, Instant.parse("2026-09-29T10:00:00Z"))
        val expiredDays = listOf(
            WeatherDayForecast(today.minusDays(3), -2, 12, WeatherCondition.STORM, 90, 25.0, 40),
        )
        assertTrue(weatherAdvisories(staleStorm, expiredDays, today, stale = true).isEmpty())
    }

    private fun day(offset: Long, max: Int? = null, prob: Int? = null, mm: Double? = null) =
        WeatherDayForecast(today.plusDays(offset), null, max, null, prob, mm, null)
}
