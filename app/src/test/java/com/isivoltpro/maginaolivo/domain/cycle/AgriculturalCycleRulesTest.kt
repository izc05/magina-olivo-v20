package com.isivoltpro.maginaolivo.domain.cycle

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AgriculturalCycleRulesTest {
    private fun date(value: String) = LocalDate.parse(value)

    @Test fun effectiveClosureIsInclusiveAndNextWorkBelongsToTheNextConfirmedCycle() {
        val first = CycleInterval(date("2026-02-01"), date("2027-01-25"))
        val next = CycleInterval(date("2027-01-26"), null)
        assertTrue(first.contains(date("2027-01-25")))
        assertFalse(first.contains(date("2027-01-26")))
        assertTrue(next.contains(date("2027-01-26")))
        assertFalse(AgriculturalCycleRules.overlaps(first, next))
    }

    @Test fun farmersDateCanDifferFromLastDeliveryAndConfirmationDate() {
        assertNull(AgriculturalCycleRules.closeError(
            CycleInterval(date("2026-02-01"), null), date("2027-01-20"), date("2027-05-04"),
            lastPhysicalDate = date("2027-01-15"),
        ))
        assertEquals("records_after_end", AgriculturalCycleRules.closeError(
            CycleInterval(date("2026-02-01"), null), date("2027-01-14"), date("2027-05-04"),
            lastPhysicalDate = date("2027-01-15"),
        ))
    }

    @Test fun differentFarmBoundariesDoNotDependOnYearLabelsOrSeptember() {
        val a = CycleInterval(date("2026-02-01"), date("2027-01-25"))
        val b = CycleInterval(date("2026-03-01"), date("2027-02-12"))
        assertFalse(a.contains(date("2027-02-10")))
        assertTrue(b.contains(date("2027-02-10")))
        assertTrue(a.contains(date("2026-02-15")))
    }

    @Test fun rejectsOverlapsEmptyIntervalsFutureClosureAndAlreadyClosed() {
        val open = CycleInterval(date("2026-01-01"), null)
        assertTrue(AgriculturalCycleRules.overlaps(open, CycleInterval(date("2026-02-01"), null)))
        assertEquals("before_start", AgriculturalCycleRules.closeError(open, date("2025-12-31"), date("2027-01-01")))
        assertEquals("future_end", AgriculturalCycleRules.closeError(open, date("2027-01-02"), date("2027-01-01")))
        assertEquals("already_closed", AgriculturalCycleRules.closeError(CycleInterval(open.start, date("2026-12-31")), date("2026-12-31"), date("2027-01-01")))
    }

    @Test fun rangeCrossingCycleBoundaryRequiresReviewRatherThanSplittingWork() {
        val cycle = CycleInterval(date("2024-02-01"), date("2024-02-29"))
        assertTrue(cycle.contains(date("2024-02-28"), date("2024-02-29")))
        assertFalse(cycle.contains(date("2024-02-29"), date("2024-03-01")))
        assertFalse(cycle.contains(date("2024-02-29"), date("2024-02-28")))
    }
}
