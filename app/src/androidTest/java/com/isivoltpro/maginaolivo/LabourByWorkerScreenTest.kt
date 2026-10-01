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
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.harvests.LabourPaymentsScreen
import com.isivoltpro.maginaolivo.feature.harvests.LabourPaymentsUiState
import com.isivoltpro.maginaolivo.feature.harvests.labourAccounts
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/** 254-D: the campaign summary says who worked how much, by name, and keeps bare counts apart. */
class LabourByWorkerScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val campaign = UiPolishFixtures.campaign
    private val harvest = Harvest(
        id = UUID.randomUUID(), workspaceId = campaign.workspaceId, farmId = campaign.farmId, campaignId = campaign.id,
        harvestDate = UiPolishFixtures.today, totalGrams = 2_390_000, shares = emptyList(), collectionMethod = null,
        workerCount = null, machineryText = null, notes = null, version = 1,
    )
    private val juan = UUID.randomUUID()
    private val maria = UUID.randomUUID()

    @Test fun oneRowPerPersonAndTheUnnamedApart() {
        val labour = listOf(
            line(juan, "Juan Pérez", LabourUnit.FULL_DAY),
            line(maria, "María López", LabourUnit.HALF_DAY),
            line(null, null, LabourUnit.FULL_DAY, quantity = 3),
        )
        val notebook = CampaignNotebook.project(campaign, emptyList(), listOf(harvest), emptyList(), emptyList(), labour = labour)
        composeRule.setContent {
            MaginaOlivoTheme { LabourPaymentsScreen(LabourPaymentsUiState(isLoading = false, campaign = campaign, entries = notebook.labour, days = notebook.harvests, accounts = labourAccounts(campaign.id, notebook.labour, emptyList(), emptyList()))) }
        }
        // 1 + 3 whole days and 1 half day: never "5 jornales" mixing both.
        composeRule.onNodeWithText("4 jornadas · 1 media").performScrollTo()
        composeRule.onNodeWithText("2 personas con nombre").assertExists()
        composeRule.onAllNodesWithTag("notebook-worker-labour").assertCountEquals(2)
        composeRule.onNodeWithText("Juan Pérez").assertExists()
        composeRule.onNodeWithTag("notebook-worker-labour-unnamed").performScrollTo().assertTextContains("3 jornadas", substring = true)
    }

    @Test fun onlyCountsStillSayTheyHaveNoName() {
        val notebook = CampaignNotebook.project(
            campaign, emptyList(), listOf(harvest), emptyList(), emptyList(),
            labour = listOf(line(null, null, LabourUnit.FULL_DAY, quantity = 5)),
        )
        composeRule.setContent {
            MaginaOlivoTheme { LabourPaymentsScreen(LabourPaymentsUiState(isLoading = false, campaign = campaign, entries = notebook.labour, days = notebook.harvests, accounts = labourAccounts(campaign.id, notebook.labour, emptyList(), emptyList()))) }
        }
        composeRule.onAllNodesWithTag("notebook-worker-labour").assertCountEquals(0)
        composeRule.onNodeWithTag("notebook-worker-labour-unnamed").performScrollTo().assertTextContains("5 jornadas", substring = true)
    }

    private fun line(worker: UUID?, name: String?, unit: LabourUnit, quantity: Int = 1) =
        LabourEntry(UUID.randomUUID(), harvest.id, worker, name, quantity, unit, null, 1)
}
