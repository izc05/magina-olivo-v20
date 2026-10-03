package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextReplacement
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

    /** Owner 2026-10-03: Catastro's municipality/province arrive filled and stay editable. */
    @Test fun catastroFillsMunicipalityAndProvinceAndTheFarmerCanChangeThem() {
        var typed: Pair<String, String>? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                CadastreImportScreen(
                    state = CadastreImportState(
                        farms = listOf(farm("Finca")), candidate = candidate(),
                        municipality = "Bedmar y Garciez", province = "Jaén", placeFromCatastro = true,
                    ),
                    preselectedFarmId = null,
                    onSearch = {},
                    onImport = { _, _ -> },
                    onPlaceChanged = { municipality, province -> typed = municipality to province },
                )
            }
        }
        composeRule.onNodeWithTag("catastro-municipality").performScrollTo().assertTextContains("Bedmar y Garciez")
        composeRule.onNodeWithText("Según Catastro", substring = true).assertExists()
        composeRule.onNodeWithTag("catastro-province").performScrollTo().assertTextContains("Jaén")
        composeRule.onNodeWithTag("catastro-municipality").performTextReplacement("Bedmar")
        composeRule.runOnIdle { assertEquals("Bedmar" to "Jaén", typed) }
    }

    @Test fun withoutCatastrosPlaceTheFarmerIsInvitedToTypeIt() {
        composeRule.setContent {
            MaginaOlivoTheme {
                CadastreImportScreen(
                    state = CadastreImportState(farms = listOf(farm("Finca")), candidate = candidate()),
                    preselectedFarmId = null, onSearch = {}, onImport = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithTag("catastro-municipality").performScrollTo()
        composeRule.onNodeWithText("Catastro no lo ha indicado", substring = true).assertExists()
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
