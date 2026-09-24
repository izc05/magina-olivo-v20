package com.isivoltpro.maginaolivo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.feature.catastro.OfficialCadastreClient
import com.isivoltpro.maginaolivo.feature.maps.MapParcel
import com.isivoltpro.maginaolivo.feature.maps.ParcelMap
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase18OnlineImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clearPreviousEvidence() { context.deleteDatabase(DATABASE) }

    @Test fun importsOfficialGeometryWithProvenanceForOfflineUse() = runBlocking {
        // Same bounded query issued when the farmer taps the live map near this real olive parcel.
        val candidate = requireNotNull(
            OfficialCadastreClient().findNear(37.6360, -3.4800)
                .firstOrNull { it.reference == REFERENCE },
        ) { "Visual discovery did not return the known Huelma parcel" }
        val now = Instant.now()
        val database = MaginaOlivoDatabase.create(context, DATABASE)
        try {
            database.workspaceDao().upsert(WorkspaceEntity(
                id = WORKSPACE_ID, name = "Gate 18", ownerUserId = UUID.randomUUID(),
                countryCode = "ES", timezone = "Europe/Madrid", locale = "es-ES", currency = "EUR",
                metadata = LocalMetadata(now, now),
            ))
            val farm = OfflineFirstFarmRepository(database, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
                .create(NewFarm(WORKSPACE_ID, "Finca offline")) as AppResult.Success
            val result = OfflineFirstParcelRepository(database, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
                .create(NewParcel(
                    farmId = farm.value, displayName = "Parcela Gate 18",
                    cadastralReference = candidate.reference, source = ParcelSource.CATASTRO,
                    geometryGeoJson = candidate.geometryGeoJson, cadastralAreaM2 = candidate.areaM2,
                    sourceProvider = candidate.provider, sourceImportedAt = candidate.importedAt,
                ))
            assertTrue(result is AppResult.Success)
        } finally { database.close() }
    }
}

@RunWith(AndroidJUnit4::class)
class Phase18OfflineReopenTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun reopensAndRendersImportedGeometryWithoutNetwork() = runBlocking {
        val database = MaginaOlivoDatabase.create(context, DATABASE)
        val parcel = try {
            val repository = OfflineFirstParcelRepository(database, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
            val id = repository.findActiveByCadastralReference(WORKSPACE_ID, REFERENCE)
            assertNotNull("Online setup did not persist the official parcel", id)
            repository.observeById(id!!).first()!!
        } finally { database.close() }

        assertEquals("ES_CATASTRO", parcel.sourceProvider)
        assertNotNull(parcel.sourceImportedAt)
        assertNotNull(parcel.geometryGeoJson)
        val snapshot = AtomicReference<Bitmap>()
        composeRule.setContent {
            MaginaOlivoTheme {
                ParcelMap(
                    parcels = listOf(MapParcel(parcel.id.toString(), parcel.displayName, parcel.geometryGeoJson!!)),
                    imagery = false,
                    onMapSnapshot = snapshot::set,
                )
            }
        }
        composeRule.waitUntil(15_000) { snapshot.get() != null }
        composeRule.onNodeWithTag("parcel-map-view").assertIsDisplayed()
        composeRule.waitForIdle()

        val renderedMap = requireNotNull(snapshot.get())
        val output = File(context.getExternalFilesDir(null), "phase18-offline-map.png")
        FileOutputStream(output).use { stream ->
            renderedMap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertTrue(output.length() > 10_000)
        val boundaryPixels = renderedMap.countBoundaryPixels()
        assertTrue(
            "Map snapshot must contain the saved parcel boundary; matched $boundaryPixels pixels",
            boundaryPixels > 50,
        )
    }
}

private fun Bitmap.countBoundaryPixels(): Int {
    var matches = 0
    for (y in 0 until height step 2) {
        for (x in 0 until width step 2) {
            val pixel = getPixel(x, y)
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            if (kotlin.math.abs(red - 37) <= 16 && kotlin.math.abs(green - 55) <= 16 &&
                kotlin.math.abs(blue - 28) <= 16
            ) matches++
        }
    }
    return matches
}

private const val DATABASE = "phase18-offline-map.db"
private const val REFERENCE = "23044A00400021"
private val WORKSPACE_ID: UUID = UUID.fromString("18181818-1818-1818-1818-181818181818")
