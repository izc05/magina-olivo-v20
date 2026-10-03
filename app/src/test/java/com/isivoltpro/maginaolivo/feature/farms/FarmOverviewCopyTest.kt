package com.isivoltpro.maginaolivo.feature.farms

import com.isivoltpro.maginaolivo.domain.analytics.CurrencyTotal
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #359: unknown is «—», currencies stay apart, and the note says which Farms have no campaign. */
class FarmOverviewCopyTest {
    private val nothing = DeliverySummary(0, 0, 0, null, null)

    @Test fun unknownFiguresAreADash() {
        assertEquals("—", overviewKilos(nothing))
        assertEquals("—", overviewYield(nothing))
        assertEquals("—", overviewCost(emptyList()))
        assertEquals("—", overviewCostPerKg(null, emptyList()))
    }

    @Test fun twoCurrenciesAreTwoTotalsAndNoCostPerKg() {
        val costs = listOf(CurrencyTotal("EUR", 10_000, null), CurrencyTotal("USD", 5_000, null))
        val line = overviewCost(costs)
        assertTrue(line, line.contains(" · "))
        assertEquals("—", overviewCostPerKg(20, costs))
    }

    @Test fun theNoteSaysTheConfirmedLabourCost() {
        val overview = FarmOverview("2026/27", emptyList(), emptyList(), nothing, listOf(CurrencyTotal("EUR", 30_000, 6_500)))
        assertTrue(overviewNote(overview), overviewNote(overview).startsWith("Jornales 65,00"))
    }

    @Test fun theNoteNamesFarmsWithoutCampaign() {
        val overview = FarmOverview("2026/27", emptyList(), listOf("Los Llanos"), nothing, emptyList())
        assertEquals("0 de 1 fincas con campaña 2026/27 · Sin campaña: Los Llanos", overviewNote(overview))
    }
}
