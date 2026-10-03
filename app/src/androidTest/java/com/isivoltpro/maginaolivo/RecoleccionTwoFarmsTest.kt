package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShare
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.expenses.DATE_FORMAT
import com.isivoltpro.maginaolivo.feature.harvests.CampaignHarvest
import com.isivoltpro.maginaolivo.feature.harvests.HarvestsScreen
import com.isivoltpro.maginaolivo.feature.harvests.HarvestsUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** #366 — device case: two Farms, one recolección day each on 3 oct 2026. */
class RecoleccionTwoFarmsTest {
    @get:Rule val composeRule = createComposeRule()
    private val oct3 = LocalDate.of(2026, 10, 3)
    private val estacasCampaign = UUID.randomUUID()
    private val salinillasCampaign = UUID.randomUUID()
    private val estacas = day("Estacas", estacasCampaign, 0, emptyList())
    private val salinillas = day(
        "Salinillas", salinillasCampaign, 3_150_000,
        listOf(
            HarvestShare(UUID.randomUUID(), "Pol. 15 · Parc. 596", HarvestAllocation.UNALLOCATED, null),
            HarvestShare(UUID.randomUUID(), "Pol. 15 · Parc. 753", HarvestAllocation.UNALLOCATED, null),
        ),
    )

    @Test fun oneDayInTwoFarmsEachDaySaysItsFarmAndUnsplitKilosReadPlainly() {
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestsScreen(
                    state = HarvestsUiState(
                        isLoading = false,
                        harvests = listOf(estacas, salinillas),
                        campaigns = listOf(
                            CampaignHarvest(estacasCampaign, "Estacas", "Campaña 2026-2027", HarvestSummary.of(listOf(estacas))),
                            CampaignHarvest(salinillasCampaign, "Salinillas", "Campaña 2026-2027", HarvestSummary.of(listOf(salinillas))),
                        ),
                    ),
                    today = oct3,
                    onCreate = {},
                    onHarvestSelected = {},
                )
            }
        }
        // Not «2 días»: two Farms on the same date are one day.
        composeRule.onNodeWithTag("harvest-metric-days").assertTextContains("1").assertTextContains("En 2 fincas")
        // Each day names its Farm.
        val date = DATE_FORMAT.format(oct3)
        val titles = composeRule.onAllNodesWithTag("harvest-row-title", useUnmergedTree = true).fetchSemanticsNodes().size
        assertEquals(2, titles)
        composeRule.onNode(hasText("Estacas · $date")).performScrollTo()
        composeRule.onNode(hasText("Salinillas · $date")).performScrollTo()
        // «registro» is gone; a campaign without Pesadas says so.
        assertEquals(0, composeRule.onAllNodesWithText("registro", substring = true).fetchSemanticsNodes().size)
        composeRule.onNode(hasText("Sin pesadas todavía")).performScrollTo()
        assertEquals(2, composeRule.onAllNodesWithText("1 día de recolección").fetchSemanticsNodes().size)
        // Unsplit kilos are said plainly, never shared out.
        assertEquals(2, composeRule.onAllNodesWithText("Sin kg asignados").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("campaign-harvest-unallocated").performScrollTo()
            .assertTextContains("${Weight.format(3_150_000)} pendientes de repartir entre 2 parcelas")
        assertEquals(0, composeRule.onAllNodesWithText("Solo en kilos sin repartir").fetchSemanticsNodes().size)
    }

    private fun day(farm: String, campaign: UUID, grams: Long, shares: List<HarvestShare>) = Harvest(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), campaignId = campaign,
        harvestDate = oct3, totalGrams = grams, shares = shares, collectionMethod = null, workerCount = null,
        machineryText = null, notes = null, version = 1, farmName = farm, campaignName = "Campaña 2026-2027",
        automatic = true,
    )
}
