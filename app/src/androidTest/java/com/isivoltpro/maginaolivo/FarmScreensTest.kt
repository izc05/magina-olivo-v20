package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
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
        assertEquals(1, composeRule.onAllNodesWithTag("add-farm").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Crear mi primera finca").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Maquinaria").fetchSemanticsNodes().size)
    }

    /** A second Farm in a row brings the same message as the first; the editor still closes. */
    @Test
    fun theEditorClosesOnEverySaveEvenWithTheSameMessage() {
        val saved = "Finca guardada en este dispositivo"
        var state by mutableStateOf(FarmListUiState(isLoading = false, farms = listOf(farm(1)), message = saved, saveCount = 1))
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmListScreen(state = state, onFarmSelected = {}, onCreate = {}, onRestore = {}, onRetry = {})
            }
        }
        composeRule.onNodeWithTag("add-farm").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("farm-name").fetchSemanticsNodes().isNotEmpty() }

        state = state.copy(farms = state.farms + farm(2), saveCount = 2)

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("farm-name").fetchSemanticsNodes().isEmpty() }
        assertEquals(saved, state.message)
    }

    /** After process death the new ViewModel counts from zero; that is not a save. */
    @Test
    fun aCountStartingAgainDoesNotCloseTheEditor() {
        var state by mutableStateOf(FarmListUiState(isLoading = false, farms = listOf(farm(1)), saveCount = 3))
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmListScreen(state = state, onFarmSelected = {}, onCreate = {}, onRestore = {}, onRetry = {})
            }
        }
        composeRule.onNodeWithTag("add-farm").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("farm-name").fetchSemanticsNodes().isNotEmpty() }

        state = state.copy(saveCount = 0)
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithTag("farm-name").fetchSemanticsNodes().size)

        // The next real save still closes it.
        state = state.copy(saveCount = 1)
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("farm-name").fetchSemanticsNodes().isEmpty() }
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

        composeRule.onNodeWithText("Crear mi primera finca").performClick()
        composeRule.onNodeWithText("Más detalles").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Municipio (opcional)").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Más detalles").performClick()
        composeRule.onNodeWithTag("farm-name").performTextInput("La Solana")
        composeRule.onNodeWithText("Municipio (opcional)").performTextInput("Huelma")
        composeRule.onNodeWithText("Provincia (opcional)").performTextInput("Jaén")
        composeRule.onNodeWithTag("save-farm").performScrollTo().performClick()

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

        composeRule.onNodeWithTag("farm-list").performScrollToIndex(52)
        composeRule.onNodeWithText("Finca 50").assertIsDisplayed()
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

        composeRule.onNodeWithTag("farm-list").performScrollToIndex(22)
        composeRule.onNodeWithText("Finca 20").assertIsDisplayed()
    }

    /** #359/#363/#364: Olivos in the header and a 2×2 on each card, at 360 dp with large text. */
    @Test
    fun headerAndCardsShowOlivesAndCampaignKilosWithoutInventingZeros() {
        val estacas = farm(1).copy(name = "Estacas", oliveTreeCount = 82, oliveTreeCountComplete = true)
        val salinillas = farm(2).copy(name = "Salinillas", oliveTreeCount = null, oliveTreeCountComplete = false)
        val sinCampana = farm(3).copy(name = "La Loma", oliveTreeCount = 40, oliveTreeCountComplete = true)
        val kilos = mapOf(estacas.id to 0L, salinillas.id to 3_150_000L)
        composeRule.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale = 1.3f),
            ) {
                MaginaOlivoTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(360.dp)) {
                        FarmListScreen(
                            state = FarmListUiState(isLoading = false, farms = listOf(estacas, salinillas, sinCampana)),
                            onFarmSelected = {},
                            onCreate = {},
                            onRestore = {},
                            onRetry = {},
                            campaignKilos = { id -> kilos[id] },
                        )
                    }
                }
            }
        }
        // Header: Salinillas has no count, so the sum is partial.
        composeRule.onNode(
            hasTestTag("farm-totals") and hasAnyDescendant(hasText("Olivos")) and hasAnyDescendant(hasText("≥ 122")),
            useUnmergedTree = true,
        ).assertExists()
        // Cards: running campaign without Pesadas, with Pesadas, and no running campaign («—», not 0).
        composeRule.onNodeWithTag("farm-list").performScrollToNode(hasTestTag("farm-${estacas.id}"))
        composeRule.onNodeWithTag("farm-${estacas.id}").assertTextContains("82").assertTextContains("Sin pesadas")
        composeRule.onNodeWithTag("farm-list").performScrollToNode(hasTestTag("farm-${salinillas.id}"))
        composeRule.onNodeWithTag("farm-${salinillas.id}").assertTextContains(Weight.format(3_150_000)).assertTextContains("Kg campaña")
        composeRule.onNodeWithTag("farm-list").performScrollToNode(hasTestTag("farm-${sinCampana.id}"))
        composeRule.onNodeWithTag("farm-${sinCampana.id}").assertTextContains("40").assertTextContains("—")
        assertEquals(0, composeRule.onAllNodesWithText("0 kg").fetchSemanticsNodes().size)
    }

    /** Codex #371: while the Pesadas load, «Kg campaña» is unknown — never «Sin pesadas» or «—». */
    @Test
    fun campaignKilosAreUnknownWhileLoading() {
        val estacas = farm(1).copy(name = "Estacas", oliveTreeCount = 82, oliveTreeCountComplete = true)
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmListScreen(
                    state = FarmListUiState(isLoading = false, farms = listOf(estacas)),
                    onFarmSelected = {}, onCreate = {}, onRestore = {}, onRetry = {},
                    campaignKilos = null,
                )
            }
        }
        composeRule.onNodeWithTag("farm-list").performScrollToNode(hasTestTag("farm-${estacas.id}"))
        composeRule.onNodeWithTag("farm-${estacas.id}").assertTextContains("…")
        assertEquals(0, composeRule.onAllNodesWithText("Sin pesadas").fetchSemanticsNodes().size)
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
