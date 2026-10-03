package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.domain.weather.WeatherCondition
import com.isivoltpro.maginaolivo.domain.weather.WeatherDayForecast
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun day(offset: Long, max: Int? = null, prob: Int? = null, mm: Double? = null) =
        WeatherDayForecast(today.plusDays(offset), null, max, null, prob, mm, null)
}
