package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.feature.catastro.CadastralCandidate
import com.isivoltpro.maginaolivo.feature.catastro.CadastreImportScreen
import com.isivoltpro.maginaolivo.feature.catastro.CadastreImportState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CadastreImportScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun waitsForConfirmationAndUsesPreselectedFarm() {
        val first = farm("Primera finca")
        val selected = farm("Finca elegida")
        var imports = 0
        var importedFarm: UUID? = null
        var importedName: String? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                CadastreImportScreen(
                    state = CadastreImportState(farms = listOf(first, selected), candidate = candidate()),
                    preselectedFarmId = selected.id,
                    onSearch = {},
                    onImport = { id, name -> imports++; importedFarm = id; importedName = name },
                )
            }
        }
        composeRule.runOnIdle { assertEquals(0, imports) }
        composeRule.onNodeWithTag("catastro-import").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(1, imports)
            assertEquals(selected.id, importedFarm)
            assertEquals("Pol. 4 · Parc. 21", importedName)
        }
    }

    @Test fun cannotImportWithoutAFarm() {
        composeRule.setContent {
            MaginaOlivoTheme {
                CadastreImportScreen(
                    state = CadastreImportState(candidate = candidate()),
                    preselectedFarmId = null,
                    onSearch = {},
                    onImport = { _, _ -> throw AssertionError("No farm was selected") },
                )
            }
        }
        composeRule.onNodeWithTag("catastro-import").performScrollTo().assertIsNotEnabled()
    }

    @Test fun multipleMapOrFileCandidatesRequireExplicitSelection() {
        val second = candidate().copy(reference = "23044A00400022")
        var selected: String? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                CadastreImportScreen(
                    state = CadastreImportState(candidates = listOf(candidate(), second)),
                    preselectedFarmId = null,
                    onSearch = {}, onImport = { _, _ -> }, onSelect = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("23044A00400022").performClick()
        composeRule.runOnIdle { assertEquals("23044A00400022", selected) }
    }

    @Test fun duplicateOffersOpeningTheExistingParcel() {
        val existing = UUID.randomUUID()
        var opened: UUID? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                CadastreImportScreen(
                    state = CadastreImportState(duplicateId = existing, error = "Ya está guardada"),
                    preselectedFarmId = null,
                    onSearch = {}, onImport = { _, _ -> }, onOpenExisting = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("Abrir parcela existente").performClick()
        composeRule.runOnIdle { assertEquals(existing, opened) }
    }

    private fun farm(name: String) = Farm(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), name = name,
        description = null, municipality = null, province = null, notes = null,
        coverDocumentId = null, parcelCount = 0, totalAreaM2 = null,
        activeCampaignName = null, archivedAt = null, version = 1,
    )

    // Synthetic shape exercises the screen; only the dedicated live test asserts official data.
    private fun candidate() = CadastralCandidate(
        reference = "23044A00400021", areaM2 = 100.0,
        polygons = listOf(listOf(listOf(-3.0 to 37.0, -2.999 to 37.0, -2.999 to 37.001, -3.0 to 37.0))),
    )
}
