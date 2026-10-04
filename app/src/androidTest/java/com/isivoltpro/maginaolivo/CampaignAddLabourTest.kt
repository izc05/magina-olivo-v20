package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.feature.harvests.LabourPaymentsScreen
import com.isivoltpro.maginaolivo.feature.harvests.LabourPaymentsUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** #365 follow-up: Campaña → Jornales is consultation and entry while the campaign runs. */
class CampaignAddLabourTest {
    @get:Rule val rule = createComposeRule()
    private val campaign = UiPolishFixtures.campaign
    private val entry = LabourEntry(UUID.randomUUID(), UUID.randomUUID(), null, null, 1, LabourUnit.FULL_DAY, null, 1, null)

    private fun show(status: CampaignStatus, withEntries: Boolean, onAdd: (() -> Unit)?) {
        val state = LabourPaymentsUiState(
            isLoading = false,
            campaign = campaign.copy(status = status),
            entries = if (withEntries) listOf(entry) else emptyList(),
        )
        rule.setContent {
            MaginaOlivoTheme { Surface(Modifier.fillMaxSize(), color = MoCream) { LabourPaymentsScreen(state, onAddLabour = onAdd) } }
        }
    }

    @Test fun runningCampaignWithoutJornalesOffersToAddOne() {
        var added = 0
        show(CampaignStatus.HARVEST, withEntries = false) { added++ }
        rule.onNodeWithText("Todavía no has registrado jornales en esta campaña.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("labour-add").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, added) }
    }

    @Test fun runningCampaignWithJornalesStillOffersToAddAnother() {
        show(CampaignStatus.ACTIVE, withEntries = true) {}
        rule.onNodeWithTag("labour-add").performScrollTo().assertIsDisplayed()
    }

    @Test fun closedCampaignIsConsultationOnly() {
        show(CampaignStatus.CLOSED, withEntries = true) {}
        rule.onNodeWithTag("labour-add").assertDoesNotExist()
        rule.onNodeWithTag("labour-add-closed").performScrollTo().assertIsDisplayed()
    }

    @Test fun closedCampaignWithoutJornalesSaysItIsClosed() {
        show(CampaignStatus.CLOSED, withEntries = false) {}
        rule.onNodeWithTag("labour-add").assertDoesNotExist()
        rule.onNodeWithText("Esta campaña está cerrada.").performScrollTo().assertIsDisplayed()
    }

    @Test fun withoutTheCampaignEntryTheCuadernoCopyStays() {
        show(CampaignStatus.HARVEST, withEntries = false, onAdd = null)
        rule.onNodeWithTag("labour-add").assertDoesNotExist()
        rule.onNodeWithText("Registra un jornal desde el Cuaderno o un día de recolección.").performScrollTo().assertIsDisplayed()
    }
}
