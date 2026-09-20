package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.feature.parcels.FarmParcelsSection
import com.isivoltpro.maginaolivo.feature.parcels.FarmParcelsUiState
import com.isivoltpro.maginaolivo.feature.parcels.ParcelDetailScreen
import com.isivoltpro.maginaolivo.feature.parcels.ParcelDetailUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ParcelScreensTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun farmSectionShowsTruthfulEmptyState() {
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmParcelsSection(
                    state = FarmParcelsUiState(isLoading = false),
                    onParcelSelected = {},
                    onCreate = {},
                    onRestore = {},
                )
            }
        }

        composeRule.onNodeWithText("Aún no hay parcelas").assertIsDisplayed()
        composeRule.onNodeWithText("Añadir").assertIsDisplayed()
    }

    @Test
    fun activeParcelOpensPersistedIdentifier() {
        val parcel = parcel()
        var selected: UUID? = null
        composeRule.setContent {
            MaginaOlivoTheme {
                FarmParcelsSection(
                    state = FarmParcelsUiState(isLoading = false, active = listOf(parcel)),
                    onParcelSelected = { selected = it },
                    onCreate = {},
                    onRestore = {},
                )
            }
        }

        composeRule.onNodeWithTag("parcel-row").performClick()
        assertEquals(parcel.id, selected)
    }

    @Test
    fun detailLabelsManualProvenanceAndUnknownData() {
        composeRule.setContent {
            MaginaOlivoTheme {
                ParcelDetailScreen(
                    state = ParcelDetailUiState(isLoading = false, parcel = parcel()),
                    onUpdate = {},
                    onArchive = {},
                    onArchived = {},
                )
            }
        }

        composeRule.onNodeWithTag("parcel-detail-root").assertIsDisplayed()
        composeRule.onNodeWithText("Entrada manual").assertIsDisplayed()
        composeRule.onNodeWithText("Parcela Norte").assertIsDisplayed()
    }

    private fun parcel() = Parcel(
        id = UUID.fromString("30000000-0000-0000-0000-000000000080"),
        workspaceId = UUID.fromString("10000000-0000-0000-0000-000000000080"),
        farmId = UUID.fromString("20000000-0000-0000-0000-000000000080"),
        displayName = "Parcela Norte",
        cadastralReference = null,
        cadastralPolygon = null,
        cadastralParcel = null,
        municipality = null,
        province = "Jaén",
        source = ParcelSource.MANUAL,
        geometryGeoJson = null,
        cadastralAreaM2 = null,
        managedAreaM2 = null,
        notes = null,
        archivedAt = null,
        version = 1,
    )
}
