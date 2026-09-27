package com.isivoltpro.maginaolivo.domain.market

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 20D — the official weekly trend, checked against the owner's verified Junta de Andalucía
 * weeks 37 → 38 (docs/06-testing/evidence/phase20d/free-market-sources.json).
 */
class OilTrendsTest {
    private val week37 = LocalDate.of(2026, 9, 7)
    private val week38 = LocalDate.of(2026, 9, 14)

    private fun obs(category: OilCategory, start: LocalDate, value: String) =
        OilObservation(category, start, start.plusDays(6), BigDecimal(value), BigDecimal(value), "EUR_PER_KG")

    private fun junta(vararg observations: OilObservation) =
        OilMarketSeries("junta-andalucia-observatorio", "Junta de Andalucía", "ES-AN", "Andalucía", "ALMAZARA_OR_BODEGA", observations.toList())

    private val weeks = junta(
        obs(OilCategory.AOVE, week37, "3.67"), obs(OilCategory.AOV, week37, "3.30"), obs(OilCategory.AOL, week37, "3.19"),
        obs(OilCategory.AOVE, week38, "3.46"), obs(OilCategory.AOV, week38, "3.31"), obs(OilCategory.AOL, week38, "3.15"),
    )

    @Test fun theVerifiedWeek37To38ChangesAndTheirWords() {
        val aove = OilTrends.of(weeks, OilCategory.AOVE)!!
        assertEquals(BigDecimal("-0.21"), aove.absolute)
        assertEquals(TrendDirection.DOWN, aove.direction)
        assertEquals("AOVE ↓ 5,7 % esta semana", OilTrends.label(aove))
        assertEquals("Virgen ↑ 0,3 % esta semana", OilTrends.label(OilTrends.of(weeks, OilCategory.AOV)!!))
        assertEquals("Lampante ↓ 1,3 % esta semana", OilTrends.label(OilTrends.of(weeks, OilCategory.AOL)!!))
        assertEquals(38, OilTrends.week(aove.latest))
        assertEquals("3,46 €/kg", OilTrends.euros(aove.latest.valueEurPerKg))
    }

    @Test fun aMissingWeekIsNeverBridgedAndAMissingCategoryStaysMissing() {
        // Week 36 then week 38: the latest value shows, without a trend across the gap.
        val gap = junta(obs(OilCategory.AOVE, week37.minusDays(7), "3.80"), obs(OilCategory.AOVE, week38, "3.46"))
        val trend = OilTrends.of(gap, OilCategory.AOVE)!!
        assertNull(trend.direction)
        assertEquals("AOVE 3,46 €/kg", OilTrends.label(trend))
        assertNull(OilTrends.of(gap, OilCategory.AOL))
        assertEquals(listOf(OilCategory.AOVE), OilTrends.all(gap).map { it.category })
    }

    @Test fun anUnchangedPriceIsFlatAndAWeeklyValueAgesHonestly() {
        val same = junta(obs(OilCategory.AOV, week37, "3.30"), obs(OilCategory.AOV, week38, "3.30"))
        val trend = OilTrends.of(same, OilCategory.AOV)!!
        assertEquals(TrendDirection.FLAT, trend.direction)
        assertEquals("Virgen = sin cambio esta semana", OilTrends.label(trend))
        val latest = trend.latest // period ends 2026-09-20
        assertFalse(OilTrends.isOutdated(latest, LocalDate.of(2026, 9, 27)))
        assertFalse(OilTrends.isOutdated(latest, LocalDate.of(2026, 10, 4)))
        assertTrue(OilTrends.isOutdated(latest, LocalDate.of(2026, 10, 5)))
    }
}
