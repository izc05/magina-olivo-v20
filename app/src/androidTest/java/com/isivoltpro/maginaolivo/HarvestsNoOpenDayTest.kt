package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestParcelOption
import com.isivoltpro.maginaolivo.feature.harvests.HarvestsScreen
import com.isivoltpro.maginaolivo.feature.harvests.HarvestsUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/**
 * CR-011 A2: nobody opens a «jornada» by hand. Recolección keeps «+ Nueva pesada»; the day of
 * recolección is created by its first Pesada or by Cuaderno → Jornal.
 */
class HarvestsNoOpenDayTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun recoleccionHasNoOpenTodayButton() {
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestsScreen(
                    state = HarvestsUiState(isLoading = false, contexts = listOf(context())),
                    today = LocalDate.of(2026, 11, 24),
                    onCreate = {},
                    onHarvestSelected = {},
                )
            }
        }
        composeRule.onNodeWithTag("open-jornada").assertDoesNotExist()
        composeRule.onNodeWithText("Abrir jornada de hoy").assertDoesNotExist()
        composeRule.onNodeWithTag("add-pesada").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithText("Aún no hay días de recolección").assertExists()
    }

    private fun context() = HarvestContext(
        farmId = UUID.randomUUID(),
        farmName = "El Cortijo",
        campaignId = UUID.randomUUID(),
        campaignName = "2026/27",
        campaignStatus = CampaignStatus.HARVEST,
        campaignStart = LocalDate.of(2026, 10, 1),
        parcels = listOf(HarvestParcelOption(UUID.randomUUID(), "Norte")),
    )
}
