package com.isivoltpro.maginaolivo.feature.notebook

import com.isivoltpro.maginaolivo.domain.analytics.CampaignHistory
import com.isivoltpro.maginaolivo.domain.analytics.CampaignHistoryPoint
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #355: each history chart's text twin says every campaign, with gaps in words. */
class CampaignHistoryLinesTest {
    private fun point(name: String, grams: Long?, yieldHundredths: Int? = null, coverage: Int = 0, cost: Long? = null) =
        CampaignHistoryPoint(UUID.randomUUID(), name, grams, 0, yieldHundredths, coverage, cost)

    private val history = CampaignHistory(
        listOf(point("2024/25", null), point("2025/26", 4_000_000), point("2026/27", 2_000_000, 2_100, 100, 25)),
        costCurrency = "EUR",
    )

    @Test fun kilosSayEveryCampaignAndAGapInWords() {
        val line = historyKilosLine(history)
        assertTrue(line, line.startsWith("2024/25: sin datos · 2025/26: "))
        assertEquals(3, line.split(" · ").size)
    }

    @Test fun yieldSaysItsCoverageAndNoAnalysisIsNotZero() {
        val line = historyYieldLine(history)
        assertTrue(line, line.contains("2025/26: sin análisis"))
        assertTrue(line, line.contains("sobre el 100 %"))
        assertTrue(line, !line.contains("0,00 %"))
    }

    /** #449: a cost per kilo built on unconfirmed costs says so beside the figure. */
    @Test fun anIncompleteCostPerKiloIsMarked() {
        val incomplete = history.copy(points = history.points.map { if (it.name == "2026/27") it.copy(costIncomplete = true) else it })
        assertTrue(historyCostLine(incomplete), historyCostLine(incomplete).endsWith("/kg (incompleto)"))
        assertTrue(historyCostLine(history), !historyCostLine(history).contains("incompleto"))
    }

    @Test fun costNamesCampaignsInAnotherCurrency() {
        val line = historyCostLine(history.copy(otherCurrencyCampaigns = listOf("2024/25")))
        assertTrue(line, line.contains("2025/26: sin datos"))
        assertTrue(line, line.contains("/kg"))
        assertTrue(line, line.endsWith("En otra moneda, no dibujadas: 2024/25"))
    }
}
