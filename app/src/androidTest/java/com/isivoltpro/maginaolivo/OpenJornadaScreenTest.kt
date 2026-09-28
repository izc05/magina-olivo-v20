package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestParcelOption
import com.isivoltpro.maginaolivo.feature.harvests.HarvestsScreen
import com.isivoltpro.maginaolivo.feature.harvests.HarvestsUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Gate 20 (emulator, build 575): a Jornada can be opened before its first Pesada. */
class OpenJornadaScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 24)
    private val cortijo = context("El Cortijo")
    private val solana = context("La Solana")

    @Test fun withOneFarmTodaysJornadaOpensDirectly() {
        val opened = mutableListOf<UUID>()
        show(HarvestsUiState(isLoading = false, contexts = listOf(cortijo)), opened)

        composeRule.onNodeWithText("Aún no hay jornadas").assertExists()
        composeRule.onNodeWithTag("open-jornada").performScrollTo().assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(listOf(cortijo.farmId), opened) }
    }

    @Test fun withSeveralFarmsTheFarmIsChosenFirst() {
        val opened = mutableListOf<UUID>()
        show(HarvestsUiState(isLoading = false, contexts = listOf(cortijo, solana)), opened)

        composeRule.onNodeWithTag("open-jornada").performScrollTo().performClick()
        composeRule.onNodeWithTag("open-jornada-farms").assertExists()
        composeRule.onNodeWithText("La Solana · 2026/27").performClick()
        composeRule.runOnIdle { assertEquals(listOf(solana.farmId), opened) }
    }

    @Test fun withoutARunningCampaignNoJornadaCanBeOpened() {
        show(HarvestsUiState(isLoading = false), mutableListOf())
        composeRule.onNodeWithTag("open-jornada").performScrollTo().assertIsNotEnabled()
    }

    private fun show(state: HarvestsUiState, opened: MutableList<UUID>) {
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestsScreen(
                    state = state,
                    today = today,
                    onCreate = {},
                    onHarvestSelected = {},
                    onOpenJornada = { opened += it },
                )
            }
        }
    }

    private fun context(farm: String) = HarvestContext(
        farmId = UUID.randomUUID(),
        farmName = farm,
        campaignId = UUID.randomUUID(),
        campaignName = "2026/27",
        campaignStatus = CampaignStatus.HARVEST,
        campaignStart = LocalDate.of(2026, 10, 1),
        parcels = listOf(HarvestParcelOption(UUID.randomUUID(), "Norte")),
    )
}
