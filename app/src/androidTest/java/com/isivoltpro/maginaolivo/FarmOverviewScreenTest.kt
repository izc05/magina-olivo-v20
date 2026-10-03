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
        composeRule.onAllNodesWithTag("farm-overview-season").assertCountEquals(2)

        composeRule.onNodeWithTag("farm-overview-by-farm").performScrollTo().performClick()
        composeRule.onNodeWithTag("farm-overview-farm").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf(estacas), opened) }

        // Another season shows its own figures, never the newer ones.
        composeRule.onAllNodesWithTag("farm-overview-season")[1].performScrollTo().performClick()
        composeRule.onNodeWithTag("farm-overview-note").assertTextContains("0 de 2 fincas con campaña 2025/26", substring = true)
        composeRule.onAllNodesWithTag("farm-overview-by-farm").assertCountEquals(0)
    }
}
