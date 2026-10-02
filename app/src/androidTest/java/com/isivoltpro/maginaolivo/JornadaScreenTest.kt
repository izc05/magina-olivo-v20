package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailScreen
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Phase 19B — a Jornada lists its Pesadas, each with its own cooperative, and adds the next one. */
class JornadaScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val day = LocalDate.of(2026, 11, 24)
    private val harvest = Harvest(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), campaignId = UUID.randomUUID(),
        harvestDate = day, totalGrams = 5_430_000, shares = emptyList(), collectionMethod = null,
        workerCount = null, machineryText = null, notes = null, version = 4, farmName = "El Cortijo",
    )

    @Test fun threePesadasToTwoCooperativesReadAsOneDay() {
        var added = 0
        val pesadas = listOf(
            pesada(2_100_000, "Coop. San Isidro", "V-101", LocalTime.of(9, 40)),
            pesada(1_850_500, "Almazara El Molino", "A-77", LocalTime.of(13, 5)),
            pesada(1_479_500, "Coop. San Isidro", "V-102", LocalTime.of(17, 20)),
        )
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest, pesadas = pesadas),
                    onUpdate = {},
                    onDelete = {},
                    onAddPesada = { added++ },
                )
            }
        }
        composeRule.onNodeWithText("Suma de sus 3 pesadas").assertExists()
        // CR-012: the count/total are in production; cooperative and kg stay on their own Pesada rows.
        composeRule.onNodeWithTag("jornada-pesadas-summary").assertDoesNotExist()
        composeRule.onAllNodesWithTag("jornada-pesada").assertCountEquals(3)
        composeRule.onNodeWithTag("jornada-add-pesada").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, added) }
    }

    @Test fun aJornadaWithoutPesadasInvitesTheFirstOne() {
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest),
                    onUpdate = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("jornada-no-pesadas").assertTextContains("Aún no hay pesadas", substring = true)
        composeRule.onAllNodesWithTag("jornada-pesada").assertCountEquals(0)
    }

    @Test fun aJornadaOpenedBeforeItsPesadasShowsPendingKilosNeverZero() {
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest.copy(totalGrams = 0)),
                    onUpdate = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("Kg pendientes de pesada").assertExists()
        composeRule.onNodeWithText("Serán la suma de sus pesadas").assertExists()
        composeRule.onNodeWithText("0 kg").assertDoesNotExist()
    }

    @Test fun eachPesadaSaysItsOriginAndYieldAndTheJornadaWeighsThem() {
        val analysed = pesada(2_000_000, "Coop. Bedmarense", "V-1", LocalTime.of(10, 0)).let {
            it.copy(origin = PesadaOrigin.TREE, analysis = YieldAnalysis(UUID.randomUUID(), it.id, day.plusDays(4), 2_000, null, null, 1))
        }
        val second = pesada(1_000_000, "Coop. Bedmarense", "V-2", LocalTime.of(12, 0)).let {
            it.copy(origin = PesadaOrigin.GROUND, analysis = YieldAnalysis(UUID.randomUUID(), it.id, day.plusDays(4), 2_300, null, null, 1))
        }
        val pending = pesada(500_000, "Coop. Bedmarense", "V-3", LocalTime.of(14, 0)).copy(origin = PesadaOrigin.TREE)
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest, pesadas = listOf(analysed, second, pending)),
                    onUpdate = {},
                    onDelete = {},
                )
            }
        }
        // (2.000 kg × 20 % + 1.000 kg × 23 %) / 3.000 kg analysed = 21 %; the pending one never counts as 0 %.
        composeRule.onNodeWithTag("jornada-pesadas-summary").assertTextContains("Rendimiento del día ${Percent.format(2_100)}", substring = true)
        val rows = composeRule.onAllNodesWithTag("jornada-pesada")
        rows[0].assertTextContains("Árbol / vuelo · Vale V-1 · Rend. ${Percent.format(2_000)}", substring = true)
        rows[1].assertTextContains("Suelo · Vale V-2 · Rend. ${Percent.format(2_300)}", substring = true)
        rows[2].assertTextContains("Rend. pendiente", substring = true)
    }

    private fun pesada(net: Long, cooperative: String, ticket: String, time: LocalTime) = Delivery(
        id = UUID.randomUUID(), workspaceId = harvest.workspaceId, farmId = harvest.farmId!!, campaignId = harvest.campaignId!!,
        deliveryDate = day, destinationOrganizationId = null, destinationName = cooperative, netGrams = net,
        grossGrams = null, tareGrams = null, deliveryNumber = null, ticketNumber = ticket,
        source = DeliverySource.MANUAL, shares = emptyList(), notes = null, version = 1,
        harvestId = harvest.id, deliveryTime = time,
    )
}
