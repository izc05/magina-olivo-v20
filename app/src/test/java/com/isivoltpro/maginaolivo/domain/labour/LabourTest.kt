package com.isivoltpro.maginaolivo.domain.labour

import com.isivoltpro.maginaolivo.feature.harvests.parseHours
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabourTest {
    private val harvest = UUID.randomUUID()

    @Test
    fun daysHalvesAndHoursAreKeptApartAndAddUpTheSameWay() {
        val entries = listOf(
            entry(1, LabourUnit.FULL_DAY), entry(1, LabourUnit.FULL_DAY), entry(3, LabourUnit.FULL_DAY),
            entry(2, LabourUnit.HALF_DAY),
            entry(1, LabourUnit.HOURS, 390),
        )
        val summary = LabourSummary.of(entries)
        assertEquals(8, summary.people)
        assertEquals(5, summary.fullDays)
        assertEquals(2, summary.halfDays)
        assertEquals(390, summary.minutes)
        assertEquals("5 jornadas · 2 medias · 6 h 30 min", summary.label())
        // Same lines in another order: the same total.
        assertEquals(summary, LabourSummary.of(entries.reversed()))
    }

    @Test
    fun rulesRejectWhatCannotBeCounted() {
        assertNull(LabourRules.validate(CrewDraft(harvest, List(5) { UUID.randomUUID() }, LabourUnit.FULL_DAY)))
        assertEquals("empty", LabourRules.validate(CrewDraft(harvest, emptyList(), LabourUnit.FULL_DAY))!!.code)
        val same = UUID.randomUUID()
        assertEquals("duplicate", LabourRules.validate(CrewDraft(harvest, listOf(same, same), LabourUnit.FULL_DAY))!!.code)
        assertEquals("required", LabourRules.validate(CountDraft(harvest, 5, LabourUnit.HOURS))!!.code)
        assertEquals("too_long", LabourRules.validate(CountDraft(harvest, 5, LabourUnit.HOURS, 25 * 60))!!.code)
        assertEquals("only_for_hours", LabourRules.validate(CountDraft(harvest, 5, LabourUnit.FULL_DAY, 60))!!.code)
        assertEquals("not_positive", LabourRules.validate(CountDraft(harvest, 0, LabourUnit.FULL_DAY))!!.code)
    }

    @Test
    fun hoursAreReadAsAFarmerWritesThem() {
        assertEquals(360, parseHours("6"))
        assertEquals(390, parseHours("6,5"))
        assertEquals(390, parseHours("6:30"))
        assertNull(parseHours("seis"))
        assertNull(parseHours("0"))
    }

    private fun entry(quantity: Int, unit: LabourUnit, minutes: Int? = null) =
        LabourEntry(UUID.randomUUID(), harvest, null, null, quantity, unit, minutes, 1)
}
