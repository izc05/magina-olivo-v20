package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelSnapshot
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignDetailScreen
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignDetailUiState
import com.isivoltpro.maginaolivo.feature.campaigns.FarmCampaignsSection
import com.isivoltpro.maginaolivo.feature.campaigns.FarmCampaignsUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CampaignScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyStateAndCreateValidationAreAccessible() {
        var saved = false
        compose.setContent { MaginaOlivoTheme { FarmCampaignsSection(FarmCampaignsUiState(isLoading = false), {}, { saved = true }) } }
        compose.onNodeWithText("Aún no hay campañas").assertIsDisplayed()
        compose.onNodeWithTag("add-campaign").performClick()
        compose.onNodeWithText("Guardar campaña").assertIsDisplayed().performClick()
        assertTrue(saved)
    }

    @Test fun closedDetailRendersFrozenSnapshotAndUnknownMetrics() {
        compose.setContent { MaginaOlivoTheme {
            CampaignDetailScreen(CampaignDetailUiState(isLoading = false, campaign = campaign()), {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Parcela histórica").assertIsDisplayed()
        compose.onNodeWithText("Finca histórica").assertIsDisplayed()
        compose.onNodeWithText("Histórico protegido").assertIsDisplayed()
        compose.onNodeWithText("Sin datos").assertIsDisplayed()
        compose.onNodeWithTag("reopen-campaign").performClick()
        compose.onNodeWithText("Confirmar cambio").assertIsDisplayed()
    }

    private fun campaign() = Campaign(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "2025/26",
        LocalDate.parse("2025-10-01"), LocalDate.parse("2026-02-01"), CampaignStatus.CLOSED, null,
        listOf(CampaignParcelSnapshot(UUID.randomUUID(), "Finca histórica", "Parcela histórica", 1000.0, null, null)), 3)
}
