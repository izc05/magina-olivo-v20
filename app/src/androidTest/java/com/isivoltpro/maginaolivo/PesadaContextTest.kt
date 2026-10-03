package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestParcelOption
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveriesScreen
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveriesUiState
import com.isivoltpro.maginaolivo.feature.deliveries.PESADA_NO_RUNNING_CAMPAIGN
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/**
 * #373/#375: a Pesada opened from a Farm, Campaign or Parcel keeps that context and does not ask
 * for the Farm again; only a global entry offers the Farm picker.
 */
class PesadaContextTest {
    @get:Rule val composeRule = createComposeRule()
    private val today = LocalDate.of(2026, 10, 3)
    private val salinillas = context("Salinillas", "Campaña 2026-2027")
    private val estacas = context("Estacas", "Campaña 2026-2027")

    @Test fun fromAFarmOrCampaignTheFarmIsContextNotAQuestion() {
        show(presetFarmId = salinillas.farmId)
        composeRule.onNodeWithTag("delivery-context").assertTextEquals("Salinillas · Campaña 2026-2027")
        composeRule.onNodeWithTag("delivery-farm").assertDoesNotExist()
    }

    @Test fun eachFarmKeepsItsOwnContext() {
        show(presetFarmId = estacas.farmId)
        composeRule.onNodeWithTag("delivery-context").assertTextEquals("Estacas · Campaña 2026-2027")
        composeRule.onNodeWithTag("delivery-farm").assertDoesNotExist()
    }

    @Test fun aFarmWhoseCampaignIsNoLongerRunningIsSaidNeverSwapped() {
        show(presetFarmId = UUID.randomUUID())
        composeRule.onNodeWithTag("delivery-context").assertTextEquals(PESADA_NO_RUNNING_CAMPAIGN)
        composeRule.onNodeWithTag("delivery-farm").assertDoesNotExist()
    }

    @Test fun aGlobalEntryStillLetsTheFarmerChooseTheFarm() {
        composeRule.setContent {
            MaginaOlivoTheme {
                DeliveriesScreen(
                    state = DeliveriesUiState(isLoading = false, contexts = listOf(salinillas, estacas)),
                    today = today, onCreate = {}, onProblem = {}, onDeliverySelected = {}, onTicketSelected = {},
                )
            }
        }
        composeRule.onNodeWithTag("add-delivery").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { runCatching { composeRule.onNodeWithTag("delivery-farm").assertExists() }.isSuccess }
        composeRule.onNodeWithTag("delivery-context").assertDoesNotExist()
    }

    private fun show(presetFarmId: UUID) {
        composeRule.setContent {
            MaginaOlivoTheme {
                DeliveriesScreen(
                    state = DeliveriesUiState(isLoading = false, contexts = listOf(salinillas, estacas)),
                    today = today, onCreate = {}, onProblem = {}, onDeliverySelected = {}, onTicketSelected = {},
                    presetFarmId = presetFarmId,
                )
            }
        }
        composeRule.waitUntil(5_000) { runCatching { composeRule.onNodeWithTag("delivery-editor").assertExists() }.isSuccess }
    }

    private fun context(farm: String, campaign: String) = HarvestContext(
        UUID.randomUUID(), farm, UUID.randomUUID(), campaign, CampaignStatus.HARVEST, today.minusDays(10),
        listOf(HarvestParcelOption(UUID.randomUUID(), "Pol. 15 · Parc. 596")),
    )
}
