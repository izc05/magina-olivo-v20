package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.feature.catastro.CadastralCandidate
import com.isivoltpro.maginaolivo.feature.catastro.CadastreImportScreen
import com.isivoltpro.maginaolivo.feature.catastro.CadastreImportState
import com.isivoltpro.maginaolivo.feature.maps.FarmMapMode
import com.isivoltpro.maginaolivo.feature.maps.FarmMapScreen
import com.isivoltpro.maginaolivo.feature.maps.FarmMapState
import com.isivoltpro.maginaolivo.feature.maps.GeoPoint
import com.isivoltpro.maginaolivo.feature.maps.MapFocus
import com.isivoltpro.maginaolivo.feature.parcels.ParcelDetailScreen
import com.isivoltpro.maginaolivo.feature.parcels.ParcelDetailUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walkthrough screenshots (review tooling, never shipped): whole screens as the farmer sees
 * them, with sample data, so agents can review the app without a phone. Maps need network.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val farm = UiPolishFixtures.farms.first()

    @Test fun farmMapAddMode() = shot("w-01-mapa-anadir", mapWait = true) {
        FarmMapScreen(
            state = FarmMapState(
                farm = farm, mode = FarmMapMode.ADD, candidates = grid(), selected = setOf(ref(121), ref(122)),
                focus = MapFocus(GeoPoint(37.6362, -3.4796), zoom = 17.0),
            ),
            onMode = {}, onSearchCoordinates = {}, onMyLocation = {}, onSearchPolygonParcel = { _, _, _, _ -> },
            onSearchByReference = {}, onTapMap = { _, _ -> }, onTapParcel = {}, onImport = {}, onLink = {}, onOpenParcel = {},
        )
    }

    @Test fun farmMapViewMode() = shot("w-02-mapa-mis-parcelas", mapWait = true) {
        FarmMapScreen(
            state = FarmMapState(farm = farm, parcels = listOf(parcel(true), parcel(false)), selectedSavedId = SAVED),
            onMode = {}, onSearchCoordinates = {}, onMyLocation = {}, onSearchPolygonParcel = { _, _, _, _ -> },
            onSearchByReference = {}, onTapMap = { _, _ -> }, onTapParcel = {}, onImport = {}, onLink = {}, onOpenParcel = {},
        )
    }

    @Test fun parcelDetailImported() = shot("w-03-parcela-catastro") {
        ParcelDetailScreen(ParcelDetailUiState(isLoading = false, parcel = parcel(true), farmName = farm.name), {}, {}, {})
    }

    @Test fun parcelDetailManual() = shot("w-04-parcela-manual") {
        ParcelDetailScreen(ParcelDetailUiState(isLoading = false, parcel = parcel(false), farmName = farm.name), {}, {}, {}, onLocate = {})
    }

    @Test fun cadastreCandidate() = shot("w-05-catastro-referencia") {
        CadastreImportScreen(
            state = CadastreImportState(farms = UiPolishFixtures.farms, candidate = grid().first(), candidates = grid().take(1)),
            preselectedFarmId = farm.id, onSearch = {}, onImport = { _, _ -> },
        )
    }

    private fun shot(name: String, mapWait: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { MaginaOlivoTheme { content() } }
        composeRule.waitForIdle()
        if (mapWait) {
            Thread.sleep(8_000)
            composeRule.waitForIdle()
        }
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(File(context.filesDir, "gate3").apply { mkdirs() }, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun ref(number: Int) = "23044A004" + number.toString().padStart(5, '0')

    /** Six neighbouring sample squares near Huelma, labelled 120–125. */
    private fun grid(): List<CadastralCandidate> = (0 until 6).map { i ->
        val lon = -3.4815 + (i % 3) * 0.0012
        val lat = 37.6355 + (i / 3) * 0.0010
        CadastralCandidate(
            reference = ref(120 + i),
            areaM2 = 9_000.0 + i * 1_300,
            polygons = listOf(listOf(listOf(lon to lat, lon + 0.0011 to lat, lon + 0.0011 to lat + 0.0009, lon to lat + 0.0009, lon to lat))),
        )
    }

    private fun parcel(imported: Boolean) = Parcel(
        id = if (imported) SAVED else UUID.fromString("30000000-0000-0000-0000-0000000000b2"),
        workspaceId = farm.workspaceId, farmId = farm.id,
        displayName = if (imported) "Olivar de arriba" else "La del camino",
        cadastralReference = if (imported) ref(121) else null,
        cadastralPolygon = if (imported) "004" else null, cadastralParcel = if (imported) "00121" else null,
        municipality = "Huelma", province = "Jaén",
        source = if (imported) ParcelSource.CATASTRO else ParcelSource.MANUAL,
        geometryGeoJson = if (imported) grid()[1].geometryGeoJson else null,
        cadastralAreaM2 = if (imported) 10_300.0 else null, managedAreaM2 = 9_800.0,
        notes = null, archivedAt = null, version = 2,
        agronomy = ParcelAgronomy(oliveTreeCount = 140, variety = "Picual", irrigationSystem = IrrigationSystem.DRIP),
        sourceProvider = if (imported) "ES_CATASTRO" else null,
        sourceImportedAt = if (imported) Instant.parse("2026-09-24T10:00:00Z") else null,
    )

    private companion object {
        val SAVED: UUID = UUID.fromString("30000000-0000-0000-0000-0000000000b1")
    }
}
