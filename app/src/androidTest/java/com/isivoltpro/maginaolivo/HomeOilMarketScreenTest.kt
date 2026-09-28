package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.market.OilCategory
import com.isivoltpro.maginaolivo.domain.market.OilMarketSeries
import com.isivoltpro.maginaolivo.domain.market.OilObservation
import com.isivoltpro.maginaolivo.feature.home.OilMarketCard
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Home presents a compact official graph; the daily AOVE.net pulse remains on Market detail. */
class HomeOilMarketScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val week37 = LocalDate.of(2026, 9, 7)
    private val week38 = LocalDate.of(2026, 9, 14)

    private fun obs(category: OilCategory, start: LocalDate, value: String) =
        OilObservation(category, start, start.plusDays(6), BigDecimal(value), BigDecimal(value), "EUR_PER_KG")

    private val junta = OilMarketSeries(
        "junta-andalucia-observatorio", "Junta de Andalucía", "ES-AN", "Andalucía", "ALMAZARA_OR_BODEGA",
        listOf(
            obs(OilCategory.AOVE, week37, "3.67"), obs(OilCategory.AOV, week37, "3.30"), obs(OilCategory.AOL, week37, "3.19"),
            obs(OilCategory.AOVE, week38, "3.46"), obs(OilCategory.AOV, week38, "3.31"), obs(OilCategory.AOL, week38, "3.15"),
        ),
    )

    @Test fun officialGraphShowsThreePricesSourceAndOpensMarketDetail() {
        var opened = 0
        composeRule.setContent {
            MaginaOlivoTheme {
                Column {
                    OilMarketCard(
                        FeedState.Value(junta, "Junta de Andalucía", Instant.parse("2026-09-27T09:00:00Z"), stale = false),
                        onOpen = { opened++ },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("oil-market-chart").assertIsDisplayed()
        composeRule.onNodeWithTag("oil-market-legend-aove").assertTextContains("3,46 €/kg", substring = true)
        composeRule.onNodeWithTag("oil-market-legend-aov").assertTextContains("3,31 €/kg", substring = true)
        composeRule.onNodeWithTag("oil-market-legend-aol").assertTextContains("3,15 €/kg", substring = true)
        composeRule.onNodeWithTag("home-market-official-source").assertTextContains("Semana 38", substring = true)
            .assertTextContains("Junta de Andalucía", substring = true)
        composeRule.onNodeWithTag("home-market-pulse").assertDoesNotExist()
        composeRule.onNodeWithText("Pulso diario").assertDoesNotExist()
        composeRule.onNodeWithTag("home-market-open").performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun staleAndUnavailableMarketStatesStayClear() {
        composeRule.setContent {
            MaginaOlivoTheme {
                Column {
                    OilMarketCard(FeedState.Value(junta, "Junta de Andalucía", Instant.parse("2026-09-27T09:00:00Z"), stale = true), onOpen = {})
                    OilMarketCard(FeedState.NotConfigured)
                }
            }
        }
        composeRule.onNodeWithTag("home-market-official-stale").assertIsDisplayed()
        composeRule.onNodeWithTag("home-market-official-not-configured").assertTextContains("Sin fuente configurada.")
    }
}
