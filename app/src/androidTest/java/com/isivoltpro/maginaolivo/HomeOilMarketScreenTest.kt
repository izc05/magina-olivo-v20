package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.market.OilCategory
import com.isivoltpro.maginaolivo.domain.market.OilMarketSeries
import com.isivoltpro.maginaolivo.domain.market.OilObservation
import com.isivoltpro.maginaolivo.feature.home.OilMarketCard
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/**
 * Phase 20D — Inicio's market card keeps the daily pulse and the official weekly trend apart,
 * names source and week, and never calls a weekly change "hoy".
 */
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

    @Test fun theOfficialWeeksShowPriceChangeWeekAndSourceNextToThePulse() {
        composeRule.setContent {
            MaginaOlivoTheme {
                Column {
                    OilMarketCard(
                        FeedState.Value(junta, "Junta de Andalucía", Instant.parse("2026-09-27T09:00:00Z"), stale = false),
                        pulse = { Text("widget") },
                    )
                }
            }
        }
        composeRule.onNodeWithText("Pulso diario").assertExists()
        composeRule.onNodeWithText("Tendencia oficial semanal").assertExists()
        val rows = composeRule.onAllNodesWithTag("home-market-trend")
        rows.assertCountEquals(3)
        rows[0].assertTextContains("3,46 €/kg", substring = true).assertTextContains("↓ 5,7 % esta semana", substring = true)
        rows[1].assertTextContains("↑ 0,3 % esta semana", substring = true)
        rows[2].assertTextContains("↓ 1,3 % esta semana", substring = true)
        composeRule.onNodeWithTag("home-market-official-source").assertTextContains("Semana 38", substring = true)
            .assertTextContains("Junta de Andalucía", substring = true)
        composeRule.onAllNodesWithTag("home-market-official-stale").assertCountEquals(0)
        composeRule.onAllNodesWithText("hoy", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test fun aCategoryMissingTheNewestWeekShowsItsOwnWeek() {
        val withoutLampante38 = junta.copy(
            observations = junta.observations.filterNot { it.category == OilCategory.AOL && it.periodStart == week38 } +
                obs(OilCategory.AOL, week37.minusDays(7), "3.23"),
        )
        composeRule.setContent {
            MaginaOlivoTheme {
                Column {
                    OilMarketCard(
                        FeedState.Value(withoutLampante38, "Junta de Andalucía", Instant.parse("2026-09-27T09:00:00Z"), stale = false),
                        pulse = null,
                    )
                }
            }
        }
        val rows = composeRule.onAllNodesWithTag("home-market-trend")
        rows.assertCountEquals(3)
        rows[0].assertTextContains("↓ 5,7 % esta semana", substring = true)
        rows[2].assertTextContains("3,19 €/kg", substring = true).assertTextContains("↓ 1,2 % · semana 37", substring = true)
        composeRule.onAllNodesWithText("↓ 1,2 % esta semana", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("home-market-official-source").assertTextContains("Semana 38", substring = true)
    }

    @Test fun anOldWeekIsMarkedAndWithoutAnySourceTheCardSaysSo() {
        composeRule.setContent {
            MaginaOlivoTheme {
                Column {
                    OilMarketCard(FeedState.Value(junta, "Junta de Andalucía", Instant.parse("2026-09-27T09:00:00Z"), stale = true), pulse = null)
                    OilMarketCard(FeedState.NotConfigured, pulse = null)
                }
            }
        }
        composeRule.onNodeWithTag("home-market-official-stale").assertExists()
        composeRule.onNodeWithTag("home-market-official-not-configured").assertTextContains("Sin fuente configurada.")
        composeRule.onAllNodesWithText("Pulso diario").assertCountEquals(0)
    }
}
