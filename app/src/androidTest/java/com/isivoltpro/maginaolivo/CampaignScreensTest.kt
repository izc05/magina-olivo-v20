package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelSnapshot
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignDetailScreen
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignDetailUiState
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignEditor
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CampaignScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyStateAndCreateValidationAreAccessible() {
        var saved = false
        compose.setContent { MaginaOlivoTheme {
            CampaignEditor(emptyList(), null, null, false, { saved = true }, {})
        } }
        compose.onNodeWithTag("campaign-name").assertIsDisplayed()
        compose.onNodeWithTag("save-campaign").performClick()
        assertTrue(saved)
    }

    @Test fun closedDetailRendersFrozenSnapshotAndUnknownMetrics() {
        compose.setContent { MaginaOlivoTheme {
            CampaignDetailScreen(CampaignDetailUiState(isLoading = false, campaign = campaign()), {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Parcela histórica").assertIsDisplayed()
        compose.onNodeWithText("Finca histórica").assertIsDisplayed()
        compose.onNodeWithText("Histórico protegido").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aún no hay kilos pesados").assertIsDisplayed()
        compose.onNodeWithText("Aún no hay pesadas").assertIsDisplayed()
        compose.onAllNodesWithText("Sin datos").assertCountEquals(0)
        compose.onNodeWithTag("reopen-campaign").performScrollTo().performClick()
        compose.onNodeWithText("Confirmar cambio").assertIsDisplayed()
    }

    /** #365: Jornales sits under Pesadas with its summary and opens the campaign's Jornales detail. */
    @Test fun jornalesSitsUnderPesadasAndOpensItsDetail() {
        var opened = 0
        compose.setContent { MaginaOlivoTheme {
            CampaignDetailScreen(
                CampaignDetailUiState(isLoading = false, campaign = campaign()), {}, {}, {}, {}, {}, {},
                summary = com.isivoltpro.maginaolivo.feature.campaigns.CampaignSummaryUi(labourLine = "1 persona · 1 jornada · 65,00 €"),
                onLabour = { opened++ },
            )
        } }
        compose.onNodeWithTag("campaign-open-labour").performScrollTo()
        compose.onNodeWithText("1 persona · 1 jornada · 65,00 €").assertIsDisplayed()
        val deliveries = compose.onNodeWithTag("campaign-open-deliveries").fetchSemanticsNode().boundsInRoot.top
        val labour = compose.onNodeWithTag("campaign-open-labour").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Jornales goes under Pesadas", labour > deliveries)
        compose.onNodeWithTag("campaign-open-labour").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    private fun campaign() = Campaign(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "2025/26",
        LocalDate.parse("2025-10-01"), LocalDate.parse("2026-02-01"), CampaignStatus.CLOSED, null,
        listOf(CampaignParcelSnapshot(UUID.randomUUID(), "Finca histórica", "Parcela histórica", 1000.0, null, null)), 3)
}
