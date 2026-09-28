package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.market.OilCategory
import com.isivoltpro.maginaolivo.domain.market.OilMarketSeries
import com.isivoltpro.maginaolivo.domain.market.OilObservation
import com.isivoltpro.maginaolivo.feature.home.OilMarketCard
import com.isivoltpro.maginaolivo.feature.home.OilMarketScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Phase 20D-3 — the oil market screen: the 12 official weeks drawn per category, the same numbers
 * in words, missing weeks said as missing, and source + fetch time always visible.
 */
class OilMarketScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val week31 = LocalDate.of(2026, 7, 27)

    private fun obs(category: OilCategory, start: LocalDate, value: String) =
        OilObservation(category, start, start.plusDays(6), BigDecimal(value), BigDecimal(value), "EUR_PER_KG")

    // The owner's verified Junta weeks 31–38 (junta-live-table-2026-09-27.json).
    private val aove = listOf("3.68", "3.63", "3.42", "3.50", "3.58", "3.47", "3.67", "3.46")
    private val aov = listOf("3.21", "3.16", "3.28", "3.24", "3.27", "3.28", "3.30", "3.31")
    private val aol = listOf("3.00", "3.01", "3.04", "3.08", "3.13", "3.16", "3.19", "3.15")

    private fun series(skipVirgenWeek: Int? = null) = OilMarketSeries(
        "junta-andalucia-observatorio", "Observatorio de Precios y Mercados - Junta de Andalucía", "ES-AN", "Andalucía",
        "ALMAZARA_OR_BODEGA",
        (0 until 8).flatMap { i ->
            val start = week31.plusWeeks(i.toLong())
            listOfNotNull(
                obs(OilCategory.AOVE, start, aove[i]),
                obs(OilCategory.AOV, start, aov[i]).takeIf { i != skipVirgenWeek },
                obs(OilCategory.AOL, start, aol[i]),
            )
        },
    )

    private val fetched = Instant.parse("2026-09-28T07:30:00Z")

    @Test fun theOfficialWeeksAreDrawnWithTheSameNumbersInWordsAndTheirSource() {
        composeRule.setContent {
            MaginaOlivoTheme {
                OilMarketScreen(FeedState.Value(series(), "Junta", fetched, stale = false), pulse = null, zone = ZoneOffset.UTC)
            }
        }
        composeRule.onNodeWithText("Mercado del aceite").assertIsDisplayed()
        composeRule.onAllNodesWithTag("home-market-trend").assertCountEquals(3)
        composeRule.onNodeWithTag("oil-market-chart").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("oil-market-chart-summary")
            .assertTextContains("8 semanas, del 27 ", substring = true) // month abbreviations follow the phone's locale data
            .assertTextContains("AOVE de 3,42 €/kg a 3,68 €/kg", substring = true)
            .assertTextContains("Virgen de 3,16 €/kg a 3,31 €/kg", substring = true)
            .assertTextContains("Lampante de 3,00 €/kg a 3,19 €/kg", substring = true)
        composeRule.onNodeWithText("Semana 31").assertExists()
        composeRule.onNodeWithText("Semana 38").assertExists()
        composeRule.onNodeWithTag("oil-market-source").performScrollTo()
            .assertTextContains("Observatorio de Precios y Mercados - Junta de Andalucía", substring = true)
            .assertTextContains("consultado 28 ", substring = true).assertTextContains(", 07:30", substring = true)
        composeRule.onAllNodesWithText("hoy", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test fun aWeekTheSourceDidNotPublishIsSaidAsMissingNeverFilledIn() {
        composeRule.setContent {
            MaginaOlivoTheme {
                OilMarketScreen(FeedState.Value(series(skipVirgenWeek = 3), "Junta", fetched, stale = false), pulse = null, zone = ZoneOffset.UTC)
            }
        }
        composeRule.onNodeWithTag("oil-market-chart-summary").performScrollTo()
            .assertTextContains("Virgen de 3,16 €/kg a 3,31 €/kg (1 semana sin dato)", substring = true)
            .assertTextContains("AOVE de 3,42 €/kg a 3,68 €/kg;", substring = true)
    }

    @Test fun withoutOfficialDataTheScreenSaysSo() {
        composeRule.setContent {
            MaginaOlivoTheme {
                androidx.compose.foundation.layout.Column {
                    OilMarketScreen(FeedState.Unavailable, pulse = null)
                }
            }
        }
        composeRule.onNodeWithTag("oil-market-unavailable").assertIsDisplayed()
        composeRule.onAllNodesWithTag("oil-market-chart").assertCountEquals(0)
    }

    @Test fun theHomeCardOpensTheMarketOnlyWhenThereAreOfficialWeeks() {
        var opened = false
        composeRule.setContent {
            MaginaOlivoTheme {
                androidx.compose.foundation.layout.Column {
                    OilMarketCard(FeedState.Value(series(), "Junta", fetched, stale = false), onOpen = { opened = true })
                    OilMarketCard(FeedState.NotConfigured, onOpen = { opened = true })
                }
            }
        }
        composeRule.onAllNodesWithTag("home-market-open").assertCountEquals(1)
        composeRule.onNodeWithTag("home-market-open").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }
}
