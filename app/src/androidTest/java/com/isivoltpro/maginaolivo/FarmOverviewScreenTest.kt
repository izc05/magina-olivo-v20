package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.analytics.CurrencyTotal
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.analytics.FarmSeasonFigures
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.WeightedYield
import com.isivoltpro.maginaolivo.feature.farms.FarmOverviewSection
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** #359: Mi Campo's holding summary — season chosen, totals, and each Farm opening its detail. */
class FarmOverviewScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val estacas = UUID.randomUUID()
    private val weighed = DeliverySummary(2, 3_000_000, 0, WeightedYield(2_000, 3_000_000), null)
    private val now = FarmOverview(
        "2026/27",
        listOf(FarmSeasonFigures(estacas, "Estacas", 1, weighed, listOf(CurrencyTotal("EUR", 30_000, 6_500)))),
        listOf("Los Llanos"), weighed, listOf(CurrencyTotal("EUR", 30_000, 6_500)),
    )
    private val before = FarmOverview("2025/26", emptyList(), listOf("Estacas", "Los Llanos"), DeliverySummary(0, 0, 0, null, null), emptyList())

    @Test fun seasonsAreChosenAndEachFarmOpensItsDetail() {
        val opened = mutableListOf<UUID>()
        composeRule.setContent {
            MaginaOlivoTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { FarmOverviewSection(listOf(now, before)) { opened += it } }
            }
        }
        composeRule.onNodeWithTag("farm-overview-note").assertTextContains("1 de 2 fincas con campaña 2026/27", substring = true)
        composeRule.onNodeWithTag("farm-overview-note").assertTextContains("Sin campaña: Los Llanos", substring = true)
        // #359 follow-up: the period is always shown; the other seasons are in its menu.
        composeRule.onNodeWithTag("farm-overview-period").assertTextContains("Campaña 2026/27", substring = true)
        composeRule.onAllNodesWithTag("farm-overview-season").assertCountEquals(0)

        composeRule.onNodeWithTag("farm-overview-by-farm").performScrollTo().performClick()
        composeRule.onNodeWithTag("farm-overview-farm").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(estacas), opened) }

        // Another season shows its own figures, never the newer ones.
        composeRule.onNodeWithTag("farm-overview-period").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("farm-overview-season").fetchSemanticsNodes().size == 2 }
        composeRule.onAllNodesWithTag("farm-overview-season")[1].performClick()
        composeRule.onNodeWithTag("farm-overview-note").assertTextContains("0 de 2 fincas con campaña 2025/26", substring = true)
        composeRule.onAllNodesWithTag("farm-overview-by-farm").assertCountEquals(0)
    }

    /** #359 follow-up: recollection cost/kg apart from general costs, and the total of both. */
    @Test fun generalCostsAreShownApartWithTheTotal() {
        val kilos = DeliverySummary(3, 5_700_000, 0, null, null)
        val season = FarmOverview("2026/27", emptyList(), emptyList(), kilos,
            listOf(CurrencyTotal("EUR", 144_000, 65_000)), listOf(CurrencyTotal("EUR", 118_500, null)))
        composeRule.setContent {
            MaginaOlivoTheme { Column(Modifier.verticalScroll(rememberScrollState())) { FarmOverviewSection(listOf(season)) {} } }
        }
        composeRule.onNodeWithTag("farm-overview-period").assertTextContains("Campaña 2026/27")
        val money = { minor: Long -> com.isivoltpro.maginaolivo.domain.expense.Money.format(minor, "EUR") }
        val perKg = { milli: Long -> com.isivoltpro.maginaolivo.domain.expense.CostPerKg.format(milli, "EUR") }
        // Recollection cost/kg stays 1.440 € / 5.700 kg = 0,253 €/kg (#486, never 0,25); general costs apart.
        composeRule.onNodeWithText(perKg(253), useUnmergedTree = true).performScrollTo()
        composeRule.onNodeWithTag("farm-overview-general").performScrollTo()
        composeRule.onNodeWithText(money(118_500), useUnmergedTree = true).performScrollTo()
        composeRule.onNodeWithText(money(262_500), useUnmergedTree = true).performScrollTo()
        composeRule.onNodeWithText(perKg(461), useUnmergedTree = true).performScrollTo()
    }
}
