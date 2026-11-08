package com.isivoltpro.maginaolivo.feature.campaignhub

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CycleOptionLabelTest {
    private val day = LocalDate.of(2026, 11, 8)

    @Test fun anOpenCycleSaysItsStateAndStart() =
        assertEquals("2026/27 · Abierta · desde 09/11/2026", cycleOptionLabel("2026/27", day.plusDays(1), null))

    @Test fun aClosedCycleSaysItsStateAndBothDates() =
        assertEquals("2026/27 · Cerrada · 08/11/2026–08/11/2026", cycleOptionLabel("2026/27", day, day))

    @Test fun twoClosedCyclesWithTheSameNameStayDistinct() =
        assertNotEquals(cycleOptionLabel("2026/27", day, day), cycleOptionLabel("2026/27", day.plusDays(1), day.plusDays(2)))
}
