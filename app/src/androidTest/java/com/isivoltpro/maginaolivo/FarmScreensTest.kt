package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.feature.farms.FarmDraft
import com.isivoltpro.maginaolivo.feature.farms.FarmListScreen
import com.isivoltpro.maginaolivo.feature.farms.FarmListUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FarmScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateRemainsActionable() {
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmListScreen(
                    state = FarmListUiState(isLoading = false),
                    onFarmSelected = {},
                    onCreate = {},
                    onRestore = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Aún no tienes fincas").assertIsDisplayed()
        composeRule.onNodeWithTag("add-farm").assertIsDisplayed()
    }

    @Test
    fun errorStateExplainsLocalFailure() {
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmListScreen(
                    state = FarmListUiState(isLoading = false, error = "Error local verificable"),
                    onFarmSelected = {},
                    onCreate = {},
                    onRestore = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("No pudimos abrir tus fincas").assertIsDisplayed()
        composeRule.onNodeWithText("Error local verificable").assertIsDisplayed()
    }

    @Test
    fun editorReturnsAllUserEnteredFields() {
        var captured: FarmDraft? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmListScreen(
                    state = FarmListUiState(isLoading = false),
                    onFarmSelected = {},
                    onCreate = { captured = it },
                    onRestore = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithTag("add-farm").performClick()
        composeRule.onNodeWithTag("farm-name").performTextInput("La Solana")
        composeRule.onNodeWithText("Municipio (opcional)").performTextInput("Huelma")
        composeRule.onNodeWithText("Provincia (opcional)").performTextInput("Jaén")
        composeRule.onNodeWithTag("save-farm").performClick()

        assertEquals("La Solana", captured?.name)
        assertEquals("Huelma", captured?.municipality)
        assertEquals("Jaén", captured?.province)
    }

    @Test
    fun fiftyFarmsStayReachableInLazyList() {
        val farms = (1..50).map { index -> farm(index) }
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmListScreen(
                    state = FarmListUiState(isLoading = false, farms = farms),
                    onFarmSelected = {},
                    onCreate = {},
                    onRestore = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Finca 50").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun twentyFarmsStayReachableInLazyList() {
        val farms = (1..20).map { index -> farm(index) }
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmListScreen(
                    state = FarmListUiState(isLoading = false, farms = farms),
                    onFarmSelected = {},
                    onCreate = {},
                    onRestore = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Finca 20").performScrollTo().assertIsDisplayed()
    }

    private fun farm(index: Int) = Farm(
        id = UUID.nameUUIDFromBytes("farm-$index".toByteArray()),
        workspaceId = UUID.fromString("10000000-0000-0000-0000-000000000060"),
        name = "Finca $index",
        description = null,
        municipality = if (index % 2 == 0) "Huelma" else null,
        province = "Jaén",
        notes = null,
        coverDocumentId = null,
        parcelCount = index.toLong(),
        totalAreaM2 = index * 10_000.0,
        activeCampaignName = null,
        archivedAt = null,
        version = 1,
    )
}
