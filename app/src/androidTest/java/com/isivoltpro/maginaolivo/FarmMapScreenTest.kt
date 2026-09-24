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

    @androidx.compose.runtime.Composable
    private fun screen(state: FarmMapState, onLink: () -> Unit = {}) = FarmMapScreen(
        state = state, onMode = {}, onSearchCoordinates = {}, onMyLocation = {}, onSearchPolygonParcel = { _, _, _, _ -> },
        onSearchByReference = {}, onTapMap = { _, _ -> }, onTapParcel = {}, onImport = {}, onLink = onLink,
        onOpenParcel = {}, showMap = false,
    )

    private fun mutableStateOfLocate(parcel: Parcel) =
        androidx.compose.runtime.mutableStateOf(FarmMapState(mode = FarmMapMode.LOCATE, locateParcel = parcel))

    private fun candidate(reference: String) = CadastralCandidate(
        reference = reference,
        areaM2 = 12_000.0,
        polygons = listOf(listOf(listOf(-3.48 to 37.63, -3.47 to 37.63, -3.47 to 37.64, -3.48 to 37.63))),
    )
}
