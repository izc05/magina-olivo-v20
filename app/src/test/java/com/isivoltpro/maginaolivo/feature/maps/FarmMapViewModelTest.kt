package com.isivoltpro.maginaolivo.feature.maps

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.domain.parcel.ParcelMembership
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.domain.parcel.RegistryLink
import com.isivoltpro.maginaolivo.feature.catastro.CadastralCandidate
import com.isivoltpro.maginaolivo.feature.catastro.CadastreClient
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FarmMapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val workspace = UUID.fromString("10000000-0000-0000-0000-000000000018")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-000000000018")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun severalTappedParcelsAreIncorporatedInOneGoAndTakenOnesAreNeverOffered() = runTest(dispatcher) {
        val parcels = FakeParcels(takenReference = "23044A00400023")
        val client = FakeClient(listOf(candidate("23044A00400021"), candidate("23044A00400022"), candidate("23044A00400023")))
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), parcels, client)
        advanceUntilIdle()

        viewModel.setMode(FarmMapMode.ADD)
        viewModel.tapMap(37.636, -3.48)
        advanceUntilIdle()
        assertEquals(setOf("23044A00400023"), viewModel.state.value.taken)

        viewModel.tapParcel("23044A00400021")
        viewModel.tapParcel("23044A00400022")
        viewModel.tapParcel("23044A00400023")
        assertEquals(setOf("23044A00400021", "23044A00400022"), viewModel.state.value.selected)

        viewModel.importSelected(mapOf("23044A00400021" to "Olivar de arriba"))
        advanceUntilIdle()

        assertEquals(listOf("Olivar de arriba", "Pol. 4 · Parc. 22"), parcels.created.map { it.displayName })
        assertTrue(parcels.created.all { it.source == ParcelSource.CATASTRO && it.farmId == farmId && it.geometryGeoJson != null })
        assertEquals("004", parcels.created.first().cadastralPolygon)
        assertEquals(FarmMapMode.VIEW, viewModel.state.value.mode)
        assertEquals("2 parcelas incorporadas a la finca.", viewModel.state.value.message)
    }

    @Test
    fun aHandMadeParcelIsLinkedToTheOneCatastroParcelTapped() = runTest(dispatcher) {
        val manual = parcel(UUID.randomUUID())
        val parcels = FakeParcels(existing = manual)
        val client = FakeClient(listOf(candidate("23044A00400021"), candidate("23044A00400022")))
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), parcels, client, locateParcelId = manual.id)
        advanceUntilIdle()

        viewModel.setMode(FarmMapMode.ADD) // ignored while locating
        viewModel.tapMap(37.636, -3.48)
        advanceUntilIdle()
        viewModel.tapParcel("23044A00400021")
        viewModel.tapParcel("23044A00400022") // one parcel only: the second replaces the first
        assertEquals(setOf("23044A00400022"), viewModel.state.value.selected)

        viewModel.linkSelected()
        advanceUntilIdle()

        assertEquals(manual.id, parcels.linked?.first)
        assertEquals("23044A00400022", parcels.linked?.second?.cadastralReference)
        assertEquals(manual.id, viewModel.state.value.linkedParcelId)
        assertTrue(parcels.created.isEmpty())
    }

    @Test
    fun badCoordinatesExplainTheFormatAndMoveNothing() = runTest(dispatcher) {
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(), FakeClient(emptyList()))
        advanceUntilIdle()
        viewModel.searchCoordinates("Huelma")
        assertNull(viewModel.state.value.focus)
        assertTrue(viewModel.state.value.error!!.contains("37.636"))
        viewModel.searchCoordinates("37.636, -3.480")
        assertEquals(37.636, viewModel.state.value.focus!!.point.latitude, 1e-9)
    }

    private fun candidate(reference: String) = CadastralCandidate(
        reference = reference,
        areaM2 = 12_000.0,
        polygons = listOf(listOf(listOf(-3.48 to 37.63, -3.47 to 37.63, -3.47 to 37.64, -3.48 to 37.63))),
    )

    private fun parcel(id: UUID) = Parcel(
        id = id, workspaceId = workspace, farmId = farmId, displayName = "La del camino",
        cadastralReference = null, cadastralPolygon = null, cadastralParcel = null, municipality = null,
        province = null, source = ParcelSource.MANUAL, geometryGeoJson = null, cadastralAreaM2 = null,
        managedAreaM2 = 9_000.0, notes = null, archivedAt = null, version = 1,
    )

    private inner class FakeFarms : FarmRepository {
        private val farm = Farm(farmId, workspace, "Finca Gate 18", null, "Huelma", "Jaén", null, null, 0, null, null, null, 1)
        override fun observeActive(workspaceId: UUID): Flow<List<Farm>> = MutableStateFlow(listOf(farm))
        override fun observeArchived(workspaceId: UUID): Flow<List<Farm>> = MutableStateFlow(emptyList())
        override fun observeById(farmId: UUID): Flow<Farm?> = MutableStateFlow(farm)
        override suspend fun create(command: NewFarm): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(farmId: UUID, changes: FarmChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeClient(private val near: List<CadastralCandidate>) : CadastreClient {
        override suspend fun findByReference(reference: String): CadastralCandidate = near.first { it.reference == reference }
        override suspend fun findNear(latitude: Double, longitude: Double): List<CadastralCandidate> = near
    }

    private class FakeParcels(private val existing: Parcel? = null, private val takenReference: String? = null) : ParcelRepository {
        val created = mutableListOf<NewParcel>()
        var linked: Pair<UUID, RegistryLink>? = null
        override fun observeActive(farmId: UUID): Flow<List<Parcel>> = MutableStateFlow(listOfNotNull(existing))
        override fun observeArchived(farmId: UUID): Flow<List<Parcel>> = MutableStateFlow(emptyList())
        override fun observeById(parcelId: UUID): Flow<Parcel?> = MutableStateFlow(existing?.takeIf { it.id == parcelId })
        override suspend fun findActiveByCadastralReference(workspaceId: UUID, reference: String): UUID? =
            if (reference == takenReference) UUID.randomUUID() else null
        override suspend fun create(command: NewParcel): AppResult<UUID> {
            created += command
            return AppResult.Success(UUID.randomUUID())
        }
        override suspend fun update(parcelId: UUID, changes: ParcelChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun linkToRegistry(parcelId: UUID, link: RegistryLink): AppResult<Unit> {
            linked = parcelId to link
            return AppResult.Success(Unit)
        }
        override suspend fun archive(parcelId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(parcelId: UUID, farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun membershipHistory(parcelId: UUID): List<ParcelMembership> = emptyList()
    }
}
