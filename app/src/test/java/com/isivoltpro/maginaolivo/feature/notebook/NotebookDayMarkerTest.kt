package com.isivoltpro.maginaolivo.feature.notebook

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Cuaderno's day line tells today from yesterday at a glance. */
class NotebookDayMarkerTest {
    private val today = LocalDate.of(2026, 9, 27) // domingo

    @Test fun todayYesterdayAndTomorrowAreNamed() {
        assertTrue(dayMarkerLabel(today, today), dayMarkerLabel(today, today).startsWith("Hoy · domingo 27 "))
        assertTrue(dayMarkerLabel(today.minusDays(1), today).startsWith("Ayer · sábado 26 "))
        assertTrue(dayMarkerLabel(today.plusDays(1), today).startsWith("Mañana · lunes 28 "))
    }

    @Test fun otherDaysStartWithTheirWeekdayAndOtherYearsSayTheYear() {
        assertTrue(dayMarkerLabel(LocalDate.of(2026, 9, 24), today).startsWith("Jueves 24 "))
        val lastYear = dayMarkerLabel(LocalDate.of(2025, 11, 20), today)
        assertTrue(lastYear, lastYear.startsWith("Jueves 20 ") && lastYear.endsWith("2025"))
        assertEquals(false, dayMarkerLabel(LocalDate.of(2026, 9, 24), today).contains("2026"))
    }
}
