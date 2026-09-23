package com.isivoltpro.maginaolivo

import android.content.Context
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
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Dedicated Gate 17 test: real official lookup, confirmed local import, Room restart. */
@RunWith(AndroidJUnit4::class)
class CatastroLiveImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clear() { context.deleteDatabase(DATABASE) }
    @After fun clean() { context.deleteDatabase(DATABASE) }

    @Test fun realHuelmaParcelIsImportedAndReopenedLocally() = runBlocking {
        val candidate = OfficialCadastreClient().findByReference("23044A00400021")
        assertEquals("23044A00400021", candidate.reference)
        assertTrue(candidate.polygons.isNotEmpty())
        assertTrue(candidate.polygons[0][0][0].first in -10.0..5.0)
        assertTrue(candidate.polygons[0][0][0].second in 35.0..44.0)

        val workspaceId = UUID.randomUUID()
        val database = MaginaOlivoDatabase.create(context, DATABASE)
        val parcelId: UUID
        try {
            val now = Instant.now()
            database.workspaceDao().upsert(
                WorkspaceEntity(
                    id = workspaceId,
                    name = "Mi olivar",
                    ownerUserId = UUID.randomUUID(),
                    countryCode = "ES",
                    timezone = "Europe/Madrid",
                    locale = "es-ES",
                    currency = "EUR",
                    metadata = LocalMetadata(createdAt = now, updatedAt = now),
                ),
            )
            val farms = OfflineFirstFarmRepository(database, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
            val farm = farms.create(NewFarm(workspaceId, "Finca Gate 17")) as AppResult.Success
            val parcels = OfflineFirstParcelRepository(database, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
            val command = NewParcel(
                farmId = farm.value,
                displayName = "Parcela real de Huelma",
                cadastralReference = candidate.reference,
                source = ParcelSource.CATASTRO,
                geometryGeoJson = candidate.geometryGeoJson,
                cadastralAreaM2 = candidate.areaM2,
            )
            parcelId = (parcels.create(command) as AppResult.Success).value
            assertTrue(parcels.create(command) is AppResult.Failure)
        } finally {
            database.close()
        }

        val reopened = MaginaOlivoDatabase.create(context, DATABASE)
        try {
            val parcel = OfflineFirstParcelRepository(reopened, SystemAppClock(), UuidGenerator(), DefaultAppDispatchers())
                .observeById(parcelId).first()!!
            assertEquals(ParcelSource.CATASTRO, parcel.source)
            assertEquals(candidate.reference, parcel.cadastralReference)
            assertEquals(candidate.geometryGeoJson, parcel.geometryGeoJson)
            assertEquals(candidate.areaM2, parcel.cadastralAreaM2)
            assertTrue(parcel.farmId != null)
        } finally {
            reopened.close()
        }
    }

    private companion object { const val DATABASE = "catastro-live-import-test.db" }
}
