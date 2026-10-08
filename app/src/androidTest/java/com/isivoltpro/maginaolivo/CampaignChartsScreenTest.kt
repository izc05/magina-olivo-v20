package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.analytics.CampaignComparison
import com.isivoltpro.maginaolivo.domain.analytics.CampaignSeries
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.notebook.CampaignCharts
import com.isivoltpro.maginaolivo.feature.notebook.CampaignComparisonList
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/** Phase 19G — what the charts say in words equals the records; unknowns read "sin datos". */
class CampaignChartsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val last = campaign("2025/26", LocalDate.of(2025, 10, 1), CampaignStatus.CLOSED)
    private val current = campaign("2026/27", LocalDate.of(2026, 10, 1), CampaignStatus.HARVEST)

    /** #355: the history shows every campaign; tapping one opens it; a missing value is no bar. */
    @Test fun theHistoryNamesEachCampaignAndOpensIt() {
        val opened = mutableListOf<UUID>()
        val history = com.isivoltpro.maginaolivo.domain.analytics.CampaignHistory(
            listOf(
                com.isivoltpro.maginaolivo.domain.analytics.CampaignHistoryPoint(last.id, last.name, null, 0, null, 0, null),
                com.isivoltpro.maginaolivo.domain.analytics.CampaignHistoryPoint(current.id, current.name, 2_000_000, 0, 2_100, 100, null),
            ),
            costCurrency = null,
        )
        composeRule.setContent {
            MaginaOlivoTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    com.isivoltpro.maginaolivo.feature.notebook.CampaignHistoryCharts(history, current.id) { opened += it }
                }
            }
        }
        composeRule.onNodeWithTag("history-kg-summary").assertTextContains("2025/26: sin datos", substring = true)
        composeRule.onNodeWithTag("history-yield-summary").assertTextContains("2025/26: sin análisis", substring = true)
        // No campaign has a cost per kilo: no line is drawn, the text says so.
        composeRule.onAllNodesWithTag("history-cost").assertCountEquals(0)
        composeRule.onNodeWithTag("history-cost-summary").assertTextContains("2026/27: sin datos", substring = true)
        composeRule.onAllNodesWithTag("history-campaign").assertCountEquals(2)
        // Codex #383: each campaign is a 48 dp target.
        composeRule.onAllNodesWithTag("history-campaign")[0].assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        composeRule.onAllNodesWithTag("history-campaign")[0].performClick()
        composeRule.runOnIdle { org.junit.Assert.assertEquals(listOf(last.id), opened) }
    }

    @Test fun oneCampaignHasNoHistory() {
        composeRule.setContent {
            MaginaOlivoTheme {
                com.isivoltpro.maginaolivo.feature.notebook.CampaignHistoryCharts(
                    com.isivoltpro.maginaolivo.domain.analytics.CampaignHistory(
                        listOf(com.isivoltpro.maginaolivo.domain.analytics.CampaignHistoryPoint(current.id, current.name, 1, 0, null, 0, null)), null,
                    ),
                )
            }
        }
        composeRule.onAllNodesWithTag("history-kg-summary").assertCountEquals(0)
    }

    @Test fun theSummaryLinesAreTheRawTotals() {
        val notebook = CampaignNotebook.project(
            current, emptyList(), emptyList(),
            listOf(
                delivery(current, 2_000_000, LocalDate.of(2026, 11, 24), "Coop. San Isidro", 2_200),
                delivery(current, 1_500_000, LocalDate.of(2026, 11, 26), "Coop. San Isidro", null),
            ),
            emptyList(),
        )
        show(CampaignSeries.of(notebook), emptyList())
        composeRule.onNodeWithTag("chart-kg-summary")
            .assertTextContains("Pesado ${Weight.format(requireNotNull(notebook.deliverySummary.deliveredGrams))} en 2 días", substring = true)
        composeRule.onNodeWithTag("chart-yield-summary").assertTextContains("1 de 2 días con pesadas analizadas", substring = true)
        composeRule.onAllNodesWithTag("chart-cooperative").assertCountEquals(1)
    }

    @Test fun anOverflowingTotalIsUnavailableAndNeverDrawnAsZero() {
        val notebook = CampaignNotebook.project(
            current, emptyList(), emptyList(),
            listOf(
                delivery(current, Long.MAX_VALUE, LocalDate.of(2026, 11, 24), "Coop", null),
                delivery(current, 1, LocalDate.of(2026, 11, 25), "Coop", null),
            ),
            emptyList(),
        )
        show(CampaignSeries.of(notebook), emptyList())
        composeRule.onNodeWithTag("chart-kg-summary")
            .assertTextContains("Kilos no disponibles", substring = true)
        composeRule.onNodeWithTag("chart-kg-unavailable").assertIsDisplayed()
        composeRule.onAllNodesWithTag("chart-kg").assertCountEquals(0)
        composeRule.onAllNodesWithTag("chart-empty").assertCountEquals(0)
    }

    @Test fun anOverflowingDayStillCountsAsADayWithPesadas() {
        val notebook = CampaignNotebook.project(
            current, emptyList(), emptyList(),
            listOf(
                delivery(current, Long.MAX_VALUE, LocalDate.of(2026, 11, 24), "Coop", null),
                delivery(current, 1, LocalDate.of(2026, 11, 24), "Coop", 2_000),
            ),
            emptyList(),
        )
        show(CampaignSeries.of(notebook), emptyList())
        composeRule.onNodeWithTag("chart-yield-summary")
            .assertTextContains("1 de 1 días con pesadas analizadas", substring = true)
    }

    @Test fun unknownLegacyKilosRemainVisibleInTheComparison() {
        val comparison = CampaignComparison(current, null, null, null, null, null, legacyUnweighedGrams = null)
        val previous = CampaignComparison(last, null, null, null, null, null)
        show(CampaignSeries(emptyList(), emptyList()), listOf(previous, comparison))
        composeRule.onNodeWithTag("comparison-legacy-kilos", useUnmergedTree = true)
            .assertTextContains("Kilos históricos sin pesada no disponibles", substring = true)
    }

    @Test fun noRecordsIsAnExplicitEmptyState() {
        show(CampaignSeries.of(CampaignNotebook.project(current, emptyList(), emptyList(), emptyList(), emptyList())), emptyList())
        composeRule.onNodeWithTag("chart-empty").assertIsDisplayed()
        composeRule.onAllNodesWithTag("chart-kg").assertCountEquals(0)
        composeRule.onAllNodesWithTag("comparison-row").assertCountEquals(0)
    }

    @Test fun theComparisonNeverShowsAnInventedCost() {
        val deliveries = listOf(
            delivery(last, 10_000_000, LocalDate.of(2025, 11, 20), "Coop", null),
            delivery(current, 12_000_000, LocalDate.of(2026, 11, 24), "Coop", null),
        )
        val rows = CampaignComparison.of(
            listOf(last, current).map { CampaignNotebook.project(it, emptyList(), emptyList(), deliveries, emptyList()) },
        )
        show(CampaignSeries(emptyList(), emptyList()), rows)
        composeRule.onAllNodesWithTag("comparison-row").assertCountEquals(2)
        composeRule.onAllNodesWithTag("comparison-line", useUnmergedTree = true)[0].assertTextContains("(+20 %)", substring = true)
        composeRule.onAllNodesWithTag("comparison-line", useUnmergedTree = true)[0].assertTextContains("coste/kg sin datos", substring = true)
        composeRule.onAllNodesWithTag("comparison-line", useUnmergedTree = true)[0].assertTextContains("rend. sin datos", substring = true)
    }

    private fun show(series: CampaignSeries, rows: List<CampaignComparison>) {
        composeRule.setContent {
            MaginaOlivoTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CampaignCharts(series)
                    CampaignComparisonList(rows)
                }
            }
        }
    }

    private fun campaign(name: String, start: LocalDate, status: CampaignStatus) = Campaign(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, name = name, startDate = start,
        endDate = if (status == CampaignStatus.CLOSED) start.plusMonths(9) else null, status = status,
        notes = null, snapshots = emptyList(), version = 1,
    )

    private fun delivery(campaign: Campaign, grams: Long, date: LocalDate, destination: String, fat: Int?): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = workspace, farmId = farm, campaignId = campaign.id, deliveryDate = date,
            destinationOrganizationId = null, destinationName = destination, netGrams = grams, grossGrams = null,
            tareGrams = null, deliveryNumber = null, ticketNumber = null, source = DeliverySource.MANUAL,
            shares = emptyList(), notes = null, version = 1,
            analysis = fat?.let { YieldAnalysis(UUID.randomUUID(), id, date, it, null, null, 1) },
        )
    }
}
