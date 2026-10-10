package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.feature.catastro.CadastralCandidate
import com.isivoltpro.maginaolivo.feature.maps.FarmMapMode
import com.isivoltpro.maginaolivo.feature.maps.FarmMapScreen
import com.isivoltpro.maginaolivo.feature.maps.FarmMapState
import com.isivoltpro.maginaolivo.feature.maps.GeoPoint
import com.isivoltpro.maginaolivo.feature.maps.LocationProblem
import com.isivoltpro.maginaolivo.feature.maps.ImportReview
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FarmMapScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun reviewNamesEveryParcelBeforeIncorporatingThem() {
        var confirmed: Map<String, String>? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                ImportReview("Cortijo", listOf(candidate("23044A00400021"), candidate("23044A00400022")), saving = false) { confirmed = it }
            }
        }
        composeRule.onNodeWithText("Añadir 2 parcelas a Cortijo").assertExists()
        composeRule.onNodeWithTag("import-name-23044A00400021").performTextReplacement("Olivar de arriba")
        composeRule.onNodeWithTag("farm-map-import-confirm").performClick()
        composeRule.runOnIdle {
            assertEquals(mapOf("23044A00400021" to "Olivar de arriba", "23044A00400022" to "Pol. 4 · Parc. 22"), confirmed)
        }
    }

    @Test fun addModeCountsTheMarkedParcels() {
        composeRule.setContent {
            MaginaOlivoTheme {
                screen(FarmMapState(mode = FarmMapMode.ADD, candidates = listOf(candidate("23044A00400021"), candidate("23044A00400022")), selected = setOf("23044A00400021", "23044A00400022")))
            }
        }
        composeRule.onNodeWithTag("farm-map-add-selected").assertIsEnabled().assertTextContains("Añadir 2 parcelas", substring = true)
        // The marked parcel is named once in the panel; no list of every number under the map.
        composeRule.onNodeWithTag("farm-map-selection").assertTextContains("Pol. 4 · Parc. 22", substring = true)
        composeRule.onNodeWithTag("farm-map-selection").assertTextContains("y 1 más", substring = true)
    }

    @Test fun locatingAHandMadeParcelWaitsForOneTappedParcel() {
        var linked = false
        val manual = Parcel(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "La del camino", null, null, null, null, null,
            ParcelSource.MANUAL, null, null, 9_000.0, null, null, 1,
        )
        val state = mutableStateOfLocate(manual)
        composeRule.setContent { MaginaOlivoTheme { screen(state.value, onLink = { linked = true }) } }
        composeRule.onNodeWithText("Ubicar «La del camino»").assertExists()
        composeRule.onNodeWithTag("farm-map-link").assertIsNotEnabled()

        state.value = state.value.copy(candidates = listOf(candidate("23044A00400021")), selected = setOf("23044A00400021"))
        composeRule.onNodeWithTag("farm-map-link").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(linked) }
    }

    /** #361: with nothing on the map yet, the steps show and «Añadir de Catastro» is the main action. */
    @Test fun anEmptyFarmMapSaysWhatToDoFirst() {
        val modes = mutableListOf<FarmMapMode>()
        composeRule.setContent { MaginaOlivoTheme { screen(FarmMapState(mode = FarmMapMode.VIEW), onMode = { modes += it }) } }
        hasTextUnder("farm-map-guide", "Cómo añadir tus parcelas")
        composeRule.onNodeWithTag("farm-map-mode-add").performClick()
        composeRule.runOnIdle { assertEquals(listOf(FarmMapMode.ADD), modes) }
    }

    /** #361: each way «Mi ubicación» can fail says why and offers its own way out. */
    @Test fun aLocationProblemSaysWhyAndOffersTheWayOut() {
        val actions = mutableListOf<LocationProblem>()
        val state = androidx.compose.runtime.mutableStateOf(FarmMapState(locationProblem = LocationProblem.LOCATION_OFF))
        composeRule.setContent { MaginaOlivoTheme { screen(state.value, onLocationAction = { actions += it }) } }
        hasTextUnder("farm-map-location-problem", LocationProblem.LOCATION_OFF.message)
        composeRule.onNodeWithTag("farm-map-location-action").assertTextContains("Activar ubicación").performClick()
        composeRule.runOnIdle { assertEquals(listOf(LocationProblem.LOCATION_OFF), actions) }

        // No fix: the coordinates search opens so the farmer is never left wondering.
        state.value = FarmMapState(locationProblem = LocationProblem.NO_FIX)
        composeRule.onNodeWithTag("farm-map-location-action").assertTextContains("Escribir coordenadas").performClick()
        composeRule.onNodeWithTag("farm-map-coordinates").assertExists()
    }

    /** #361: once found, the screen says the blue dot is the farmer's position. */
    @Test fun aFoundLocationIsSaidNotOnlyAToast() {
        composeRule.setContent { MaginaOlivoTheme { screen(FarmMapState(myLocation = GeoPoint(37.73, -3.45))) } }
        hasTextUnder("farm-map-my-location-shown", "El punto azul es tu ubicación.")
    }

    /**
     * #711 B3 (owner's order): the map opens on the PNOA photo with the official boundaries
     * already drawn. «Ocultar» is only offered for a layer that is being drawn right now.
     */
    @Test fun theMapOpensOnThePhotoWithTheOfficialBoundariesAlreadyDrawn() {
        composeRule.setContent { MaginaOlivoTheme { screen(FarmMapState(mode = FarmMapMode.VIEW)) } }
        composeRule.onNodeWithTag("farm-map-layer").assertTextContains("Foto a\u00e9rea")
        composeRule.onNodeWithTag("farm-map-layer").performClick()
        composeRule.onNodeWithText("Ocultar linderos de Catastro").assertExists()
        composeRule.onNodeWithText("Ocultar recintos SIGPAC (referencia)").assertExists()
    }

    /**
     * #711 B3 (owner's order): touching a parcel while viewing shows what Catastro says about it
     * — reference, surface and place — and the one action that follows is adding it to the farm.
     */
    @Test fun touchingAnOfficialParcelShowsItsDataAndOffersToAddIt() {
        var added = 0
        var dismissed = 0
        val state = androidx.compose.runtime.mutableStateOf(
            FarmMapState(
                mode = FarmMapMode.VIEW,
                inspected = candidate("23044A00400022"),
                inspectedPlace = com.isivoltpro.maginaolivo.domain.registry.RegistryLocation("Huelma", "Jaén"),
            ),
        )
        composeRule.setContent {
            MaginaOlivoTheme { screen(state.value, onAddInspected = { added++ }, onDismissInspected = { dismissed++ }) }
        }
        composeRule.onNodeWithTag("farm-map-official-card").assertExists()
        hasTextUnder("farm-map-official-card", "23044A00400022")
        hasTextUnder("farm-map-official-card", "1,2 ha")
        hasTextUnder("farm-map-official-card", "Huelma · Jaén")
        // It is said plainly that this is not one of the farmer's parcels, and not a certificate.
        composeRule.onNodeWithTag("farm-map-official-status").assertTextContains("no está en tu olivar", substring = true)
        hasTextUnder("farm-map-official-card", "no un certificado catastral.")
        composeRule.onNodeWithTag("farm-map-official-add").performClick()
        composeRule.onNodeWithTag("farm-map-official-close").performClick()
        composeRule.runOnIdle {
            assertEquals(1, added)
            assertEquals(1, dismissed)
        }

        // Nothing read, nothing selected: the card goes away and no stale data is left on screen.
        state.value = FarmMapState(mode = FarmMapMode.VIEW)
        composeRule.onNodeWithTag("farm-map-official-card").assertDoesNotExist()
    }

    /** The notices are plain containers: their text lives in child nodes. */
    private fun hasTextUnder(tag: String, text: String) {
        composeRule.onNode(
            androidx.compose.ui.test.hasTestTag(tag) and androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasText(text)),
            useUnmergedTree = true,
        ).assertExists()
    }

    @androidx.compose.runtime.Composable
    private fun screen(
        state: FarmMapState,
        onLink: () -> Unit = {},
        onMode: (FarmMapMode) -> Unit = {},
        onLocationAction: (LocationProblem) -> Unit = {},
        onAddInspected: () -> Unit = {},
        onDismissInspected: () -> Unit = {},
    ) = FarmMapScreen(
        state = state, onMode = onMode, onSearchCoordinates = {}, onMyLocation = {}, onSearchPolygonParcel = { _, _, _, _ -> },
        onSearchByReference = {}, onTapMap = { _, _ -> }, onTapParcel = {}, onImport = {}, onLink = onLink,
        onOpenParcel = {}, showMap = false, onLocationProblemAction = onLocationAction,
        onAddInspected = onAddInspected, onDismissInspected = onDismissInspected,
    )

    private fun mutableStateOfLocate(parcel: Parcel) =
        androidx.compose.runtime.mutableStateOf(FarmMapState(mode = FarmMapMode.LOCATE, locateParcel = parcel))

    private fun candidate(reference: String) = CadastralCandidate(
        reference = reference,
        areaM2 = 12_000.0,
        polygons = listOf(listOf(listOf(-3.48 to 37.63, -3.47 to 37.63, -3.47 to 37.64, -3.48 to 37.63))),
    )
}
