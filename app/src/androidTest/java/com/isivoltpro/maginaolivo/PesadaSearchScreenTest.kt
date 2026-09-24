package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.YieldAnalysis
import com.isivoltpro.maginaolivo.domain.delivery.YieldStatus
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveriesScreen
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveriesUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Phase 19C — a Pesada is found by its ticket and gets its yield in one tap. */
class PesadaSearchScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 28)
    private val withYield = pesada("V-45872", "Coop. San Isidro", fat = 2_150)
    private val pendingA = pesada("V-45873", "Coop. San Isidro")
    private val pendingB = pesada("A-77", "Almazara El Molino")

    @Test fun pendingFilterAndTicketSearchNarrowTheList() {
        var yieldFor: UUID? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                DeliveriesScreen(
                    state = DeliveriesUiState(isLoading = false, deliveries = listOf(withYield, pendingA, pendingB)),
                    today = today,
                    onCreate = {},
                    onTicketPicked = {},
                    onProblem = {},
                    onDeliverySelected = {},
                    onTicketSelected = {},
                    initialStatus = YieldStatus.PENDING,
                    onAddYield = { yieldFor = it },
                )
            }
        }
        composeRule.onAllNodesWithTag("delivery-row").assertCountEquals(2)
        composeRule.onNodeWithTag("pesada-search").performScrollTo().performTextInput("77")
        composeRule.onAllNodesWithTag("delivery-row").assertCountEquals(1)
        composeRule.onAllNodesWithTag("pesada-add-yield").onFirst().performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(pendingB.id, yieldFor) }
    }

    private fun pesada(ticket: String, cooperative: String, fat: Int? = null): Delivery {
        val id = UUID.randomUUID()
        return Delivery(
            id = id, workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), campaignId = UUID.randomUUID(),
            deliveryDate = today.minusDays(3), destinationOrganizationId = null, destinationName = cooperative,
            netGrams = 1_000_000, grossGrams = null, tareGrams = null, deliveryNumber = null, ticketNumber = ticket,
            source = DeliverySource.MANUAL, shares = emptyList(), notes = null, version = 1,
            analysis = fat?.let { YieldAnalysis(UUID.randomUUID(), id, null, it, null, null, 1) },
        )
    }
}
