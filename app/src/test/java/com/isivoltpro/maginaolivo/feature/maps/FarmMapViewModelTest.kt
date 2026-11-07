package com.isivoltpro.maginaolivo.feature.maps

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.common.AppError
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
import com.isivoltpro.maginaolivo.domain.registry.RegistryLocation
import com.isivoltpro.maginaolivo.feature.catastro.CadastralCandidate
import com.isivoltpro.maginaolivo.feature.catastro.CadastreClient
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        val client = FakeClient(
            listOf(candidate("23044A00400021"), candidate("23044A00400022"), candidate("23044A00400023")),
            places = mapOf("23044A00400021" to RegistryLocation("Huelma", "Jaén")),
        )
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
        // Service metadata wins; the second parcel uses the official offline code concordance.
        assertEquals("Huelma", parcels.created.first().municipality)
        assertEquals("Jaén", parcels.created.first().province)
        assertEquals("Huelma", parcels.created[1].municipality)
        assertEquals("Jaén", parcels.created[1].province)
        assertEquals(FarmMapMode.VIEW, viewModel.state.value.mode)
        assertEquals("2 parcelas incorporadas a la finca.", viewModel.state.value.message)
        assertTrue(viewModel.state.value.importCompleted)
    }

    @Test
    fun partialImportRetainsOnlyFailedReferenceForRetryWithoutDuplicatingSuccess() = runTest(dispatcher) {
        val first = "23044A00400021"
        val second = "23044A00400022"
        val parcels = FakeParcels(failOnceReference = second)
        val model = FarmMapViewModel(farmId, FakeFarms(), parcels,
            FakeClient(listOf(candidate(first), candidate(second))), initialMode = FarmMapMode.ADD)
        advanceUntilIdle()
        model.tapMap(37.636, -3.48)
        advanceUntilIdle()
        model.tapParcel(first)
        model.tapParcel(second)
        model.importSelected(emptyMap())
        advanceUntilIdle()
        assertEquals(setOf(second), model.state.value.selected)
        assertEquals(setOf(first), model.state.value.taken)
        assertFalse(model.state.value.importCompleted)
        assertEquals(listOf(first), parcels.created.mapNotNull { it.cadastralReference })
        model.importSelected(emptyMap())
        advanceUntilIdle()
        assertTrue(model.state.value.importCompleted)
        assertEquals(listOf(first, second), parcels.created.mapNotNull { it.cadastralReference })
        assertEquals(listOf(first, second, second), parcels.attempts)
    }

    @Test
    fun aHandMadeParcelIsLinkedToTheOneCatastroParcelTapped() = runTest(dispatcher) {
        val manual = parcel(UUID.randomUUID())
        val parcels = FakeParcels(existing = manual)
        val client = FakeClient(
            listOf(candidate("23044A00400021"), candidate("23044A00400022")),
            places = mapOf("23044A00400022" to RegistryLocation("Cabra del Santo Cristo", "Jaén")),
        )
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
        assertEquals("Cabra del Santo Cristo", parcels.linked?.second?.municipality)
        assertEquals("Jaén", parcels.linked?.second?.province)
        assertEquals(manual.id, viewModel.state.value.linkedParcelId)
        assertTrue(parcels.created.isEmpty())
    }

    /** #361: «Mi ubicación» keeps its dot; a later failure removes it instead of showing an old one. */
    @Test
    fun aFailedLocationLookupRemovesTheOldDot() = runTest(dispatcher) {
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(), FakeClient(emptyList()))
        advanceUntilIdle()
        viewModel.myLocationFound(GeoPoint(37.73, -3.45))
        assertEquals(GeoPoint(37.73, -3.45), viewModel.state.value.myLocation)
        assertNull(viewModel.state.value.locationProblem)
        viewModel.locationUnavailable(LocationProblem.LOCATION_OFF)
        assertNull(viewModel.state.value.myLocation)
        assertEquals(LocationProblem.LOCATION_OFF, viewModel.state.value.locationProblem)
    }

    @Test
    fun approximateMyLocationCentresMapButDoesNotQueryCatastro() = runTest(dispatcher) {
        val client = FakeClient(listOf(candidate("23044A00400021")))
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(), client)
        advanceUntilIdle()

        viewModel.setMode(FarmMapMode.ADD)
        viewModel.myLocationFound(GeoPoint(37.73, -3.45), approximate = true)
        advanceUntilIdle()

        assertEquals(GeoPoint(37.73, -3.45), viewModel.state.value.myLocation)
        assertEquals(GeoPoint(37.73, -3.45), viewModel.state.value.focus?.point)
        assertTrue(viewModel.state.value.message!!.contains("aproximada"))
        assertEquals(0, client.nearCalls)
        assertTrue(viewModel.state.value.candidates.isEmpty())
    }

    @Test
    fun preciseMyLocationCanQueryNearbyParcelsInAddMode() = runTest(dispatcher) {
        val client = FakeClient(listOf(candidate("23044A00400021")))
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(), client)
        advanceUntilIdle()

        viewModel.setMode(FarmMapMode.ADD)
        viewModel.myLocationFound(GeoPoint(37.73, -3.45))
        advanceUntilIdle()

        assertEquals(1, client.nearCalls)
        assertEquals(listOf("23044A00400021"), viewModel.state.value.candidates.map { it.reference })
    }

    @Test
    fun aCatastroThatCannotSayWhereNeverBlocksTheImport() = runTest(dispatcher) {
        val parcels = FakeParcels()
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), parcels, FakeClient(listOf(candidate("23044A00400021")), placeFails = true))
        advanceUntilIdle()
        viewModel.setMode(FarmMapMode.ADD)
        viewModel.tapMap(37.636, -3.48)
        advanceUntilIdle()
        viewModel.tapParcel("23044A00400021")
        viewModel.importSelected(emptyMap())
        advanceUntilIdle()
        assertEquals(1, parcels.created.size)
        assertEquals("Huelma", parcels.created.single().municipality)
        assertTrue(viewModel.state.value.importCompleted)
    }

    @Test
    fun aStalledCatastroDoesNotHoldTheLocalSaves() = runTest(dispatcher) {
        val parcels = FakeParcels()
        val slow = FakeClient(
            listOf(candidate("23044A00400021"), candidate("23044A00400022")),
            places = mapOf("23044A00400021" to RegistryLocation("Huelma", "Jaén")),
            placeDelayMillis = 60_000,
        )
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), parcels, slow)
        advanceUntilIdle()
        viewModel.setMode(FarmMapMode.ADD)
        viewModel.tapMap(37.636, -3.48)
        advanceUntilIdle()
        viewModel.tapParcel("23044A00400021")
        viewModel.tapParcel("23044A00400022")
        val start = testScheduler.currentTime
        viewModel.importSelected(emptyMap())
        advanceUntilIdle()
        assertEquals(2, parcels.created.size)
        assertTrue(parcels.created.all { it.municipality == "Huelma" && it.province == "Jaén" })
        // Both lookups ran together and were cut at the bound, not 2 × 60 s.
        assertTrue(testScheduler.currentTime - start <= com.isivoltpro.maginaolivo.feature.catastro.PLACE_TIMEOUT_MS)
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

    /**
     * #711 B3 (owner's order): viewing the map, a tap reads the official parcel under the finger
     * and offers it; the farmer no longer has to switch to «Añadir de Catastro» first to see it.
     */
    @Test
    fun aTapWhileViewingReadsTheParcelUnderTheFingerAndOffersToAddIt() = runTest(dispatcher) {
        val parcels = FakeParcels()
        val client = FakeClient(
            listOf(square("23044A00400021", -3.48, 37.63), square("23044A00400022", -3.46, 37.63)),
            places = mapOf("23044A00400022" to RegistryLocation("Huelma", "Jaén")),
        )
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), parcels, client)
        advanceUntilIdle()

        viewModel.tapMap(37.635, -3.455) // inside the second parcel, not the first one returned
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("23044A00400022", state.inspected?.reference)
        assertEquals(12_000.0, state.inspected?.areaM2!!, 0.5)
        assertEquals("Huelma", state.inspectedPlace?.municipality)
        assertEquals(FarmMapMode.VIEW, state.mode)      // reading never switches the screen
        assertTrue(state.selected.isEmpty())            // nor marks anything for saving
        assertTrue(parcels.created.isEmpty())

        viewModel.addInspected()
        assertEquals(FarmMapMode.ADD, viewModel.state.value.mode)
        assertEquals(setOf("23044A00400022"), viewModel.state.value.selected)
        assertNull(viewModel.state.value.inspected)

        viewModel.importSelected(emptyMap())
        advanceUntilIdle()
        assertEquals(listOf("23044A00400022"), parcels.created.map { it.cadastralReference })
    }

    /** A parcel of this farm opens its own card: the saved record wins over the official copy. */
    @Test
    fun aTapOnAParcelAlreadyInTheFarmOpensItInsteadOfOfferingItAgain() = runTest(dispatcher) {
        val saved = parcel(UUID.randomUUID(), reference = "23044A00400021")
        val viewModel = FarmMapViewModel(
            farmId, FakeFarms(), FakeParcels(existing = saved),
            FakeClient(listOf(square("23044A00400021", -3.48, 37.63))),
        )
        advanceUntilIdle()

        viewModel.tapMap(37.635, -3.475)
        advanceUntilIdle()

        assertNull(viewModel.state.value.inspected)
        assertEquals(saved.id, viewModel.state.value.selectedSavedId)
    }

    /** One saved in another farm is named, never offered a second time. */
    @Test
    fun aTapOnAParcelSavedInAnotherFarmSaysSoAndOffersNothing() = runTest(dispatcher) {
        val viewModel = FarmMapViewModel(
            farmId, FakeFarms(), FakeParcels(takenReference = "23044A00400021"),
            FakeClient(listOf(square("23044A00400021", -3.48, 37.63))),
        )
        advanceUntilIdle()

        viewModel.tapMap(37.635, -3.475)
        advanceUntilIdle()

        assertNull(viewModel.state.value.inspected)
        assertTrue(viewModel.state.value.message!!.contains("otra finca"))
        assertEquals(setOf("23044A00400021"), viewModel.state.value.taken)
        viewModel.addInspected()
        assertEquals(FarmMapMode.VIEW, viewModel.state.value.mode)
    }

    @Test
    fun aTapOnThePathBetweenParcelsDoesNotOfferTheFirstNearbyParcel() = runTest(dispatcher) {
        val parcels = FakeParcels()
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), parcels,
            FakeClient(listOf(square("23044A00400021", -3.48, 37.63), square("23044A00400022", -3.46, 37.63))))
        advanceUntilIdle()
        viewModel.tapMap(37.635, -3.465)
        advanceUntilIdle()
        assertNull(viewModel.state.value.inspected)
        assertNull(viewModel.state.value.selectedSavedId)
        assertNotNull(viewModel.state.value.error)
        viewModel.addInspected()
        assertTrue(viewModel.state.value.selected.isEmpty())
        assertTrue(parcels.created.isEmpty())
    }

    @Test
    fun overlappingCandidatesRequireAnotherTapInsteadOfChoosingByServiceOrder() = runTest(dispatcher) {
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(),
            FakeClient(listOf(square("23044A00400021", -3.48, 37.63), square("23044A00400022", -3.48, 37.63))))
        advanceUntilIdle()
        viewModel.tapMap(37.635, -3.475)
        advanceUntilIdle()
        assertNull(viewModel.state.value.inspected)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun aTapExactlyOnAParcelBoundaryDoesNotAssumeOwnership() = runTest(dispatcher) {
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(),
            FakeClient(listOf(square("23044A00400021", -3.48, 37.63))))
        advanceUntilIdle()
        viewModel.tapMap(37.635, -3.48)
        advanceUntilIdle()
        assertNull(viewModel.state.value.inspected)
        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun anInteriorHoleIsNotPartOfTheParcel() {
        val exterior = square("23044A00400021", -3.48, 37.63).polygons.single().single()
        val hole = square("hole", -3.478, 37.632).polygons.single().single()
            .map { (x, y) -> -3.478 + (x + 3.478) / 2 to 37.632 + (y - 37.632) / 2 }
        val withHole = candidate("23044A00400021").copy(polygons = listOf(listOf(exterior, hole)))
        assertFalse(withHole.covers(-3.475, 37.635))
        assertTrue(withHole.covers(-3.479, 37.631))
        assertFalse(withHole.covers(-3.478, 37.635)) // hole boundary
    }

    @Test
    fun multipartParcelAcceptsEitherInteriorButNotTheGap() {
        val west = square("west", -3.48, 37.63).polygons.single()
        val east = square("east", -3.46, 37.63).polygons.single()
        val multipart = candidate("23044A00400021").copy(polygons = listOf(west, east))
        assertTrue(multipart.covers(-3.475, 37.635))
        assertTrue(multipart.covers(-3.455, 37.635))
        assertFalse(multipart.covers(-3.465, 37.635))
    }

    @Test
    fun bedmarTextSearchUsesTheVerifiedOfflinePlaceWithoutCallingCadastre() = runTest(dispatcher) {
        val client = FakeClient(emptyList())
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(), client)
        advanceUntilIdle()
        viewModel.searchPlace("BEDMAR")
        advanceUntilIdle()
        assertEquals(37.82283439581455, viewModel.state.value.focus!!.point.latitude, 0.0000001)
        assertEquals(FarmMapMode.VIEW, viewModel.state.value.mode)
        assertEquals(0, client.nearCalls)
        assertNull(viewModel.state.value.placeError)
    }

    @Test
    fun remotePlaceSearchWaitsForAnExplicitChoiceAndResolvesItsId() = runTest(dispatcher) {
        val choices = listOf(MapPlace("a", "poblacion", "Ambiguous · Jaén"), MapPlace("b", "poblacion", "Ambiguous · Córdoba"))
        val resolved = GeoPoint(37.9, -4.1)
        var selected = ""
        val places = object : PlaceSearchClient {
            override suspend fun candidates(query: String) = choices
            override suspend fun resolve(place: MapPlace): GeoPoint { selected = place.id; return resolved }
        }
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(), FakeClient(emptyList()), placeClient = places)
        advanceUntilIdle()
        viewModel.searchPlace("Ambiguous")
        advanceUntilIdle()
        assertNull(viewModel.state.value.focus)
        assertEquals(choices, viewModel.state.value.placeCandidates)
        viewModel.selectPlace(choices[1])
        advanceUntilIdle()
        assertEquals("b", selected)
        assertEquals(resolved, viewModel.state.value.focus!!.point)
    }

    @Test
    fun unavailablePlaceSearchKeepsTheCurrentMapAndReportsAnError() = runTest(dispatcher) {
        val places = object : PlaceSearchClient {
            override suspend fun candidates(query: String): List<MapPlace> = throw java.io.IOException("Offline fixture")
            override suspend fun resolve(place: MapPlace): GeoPoint = error("Not called")
        }
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(), FakeClient(emptyList()), placeClient = places)
        advanceUntilIdle()
        viewModel.goTo(GeoPoint(37.6, -3.4))
        val before = viewModel.state.value.focus
        viewModel.searchPlace("Unknown location")
        advanceUntilIdle()
        assertEquals(before, viewModel.state.value.focus)
        assertNotNull(viewModel.state.value.placeError)
        assertTrue(viewModel.state.value.placeCandidates.isEmpty())
    }

    private fun delayedPlaceClient() = object : PlaceSearchClient {
        override suspend fun candidates(query: String) = listOf(MapPlace("remote", "poblacion", "Remote place"))
        override suspend fun resolve(place: MapPlace): GeoPoint {
            kotlinx.coroutines.delay(500)
            return GeoPoint(37.9, -4.1)
        }
    }

    @Test fun aPolygonSearchCancelsPendingPlaceResolution() = runTest(dispatcher) {
        val client = FakeClient(emptyList())
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(), client, placeClient = delayedPlaceClient())
        advanceUntilIdle()
        viewModel.searchPlace("Remote place")
        advanceUntilIdle()
        viewModel.selectPlace(viewModel.state.value.placeCandidates.single())
        advanceTimeBy(1)
        viewModel.searchPolygonParcel("Jaén", "Huelma", "4", "21")
        advanceUntilIdle()
        assertNull(viewModel.state.value.focus)
        assertEquals(0, client.nearCalls)
    }

    @Test fun aManualMapTapCancelsPendingPlaceResolution() = runTest(dispatcher) {
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(),
            FakeClient(listOf(square("23044A00400021", -3.48, 37.63))), placeClient = delayedPlaceClient())
        advanceUntilIdle()
        viewModel.searchPlace("Remote place")
        advanceUntilIdle()
        viewModel.selectPlace(viewModel.state.value.placeCandidates.single())
        advanceTimeBy(1)
        viewModel.tapMap(37.635, -3.475)
        advanceUntilIdle()
        assertNull(viewModel.state.value.focus)
        assertEquals("23044A00400021", viewModel.state.value.inspected?.reference)
    }

    @Test fun manualCoordinatesCancelPendingPlaceResolution() = runTest(dispatcher) {
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(), FakeClient(emptyList()), placeClient = delayedPlaceClient())
        advanceUntilIdle()
        viewModel.searchPlace("Remote place")
        advanceUntilIdle()
        viewModel.selectPlace(viewModel.state.value.placeCandidates.single())
        advanceTimeBy(1)
        viewModel.searchCoordinates("37.636, -3.480")
        advanceUntilIdle()
        assertEquals(GeoPoint(37.636, -3.480), viewModel.state.value.focus!!.point)
    }

    @Test fun unavailableGpsCancelsPendingPlaceResolution() = runTest(dispatcher) {
        val viewModel = FarmMapViewModel(farmId, FakeFarms(), FakeParcels(), FakeClient(emptyList()), placeClient = delayedPlaceClient())
        advanceUntilIdle()
        viewModel.searchPlace("Remote place")
        advanceUntilIdle()
        viewModel.selectPlace(viewModel.state.value.placeCandidates.single())
        advanceTimeBy(1)
        viewModel.locationUnavailable(LocationProblem.LOCATION_OFF)
        advanceUntilIdle()
        assertEquals(LocationProblem.LOCATION_OFF, viewModel.state.value.locationProblem)
        assertNull(viewModel.state.value.focus)
        assertFalse(viewModel.state.value.placeSearching)
    }

    /** A closed square of about 0,01° of side, so a coordinate is unambiguously inside or outside. */
    private fun square(reference: String, west: Double, south: Double) = CadastralCandidate(
        reference = reference,
        areaM2 = 12_000.0,
        polygons = listOf(
            listOf(
                listOf(
                    west to south,
                    west + 0.01 to south,
                    west + 0.01 to south + 0.01,
                    west to south + 0.01,
                    west to south,
                ),
            ),
        ),
    )

    private fun candidate(reference: String) = CadastralCandidate(
        reference = reference,
        areaM2 = 12_000.0,
        polygons = listOf(listOf(listOf(-3.48 to 37.63, -3.47 to 37.63, -3.47 to 37.64, -3.48 to 37.63))),
    )

    private fun parcel(id: UUID, reference: String? = null) = Parcel(
        id = id, workspaceId = workspace, farmId = farmId, displayName = "La del camino",
        cadastralReference = reference, cadastralPolygon = null, cadastralParcel = null, municipality = null,
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

    private class FakeClient(
        private val near: List<CadastralCandidate>,
        private val places: Map<String, RegistryLocation> = emptyMap(),
        private val placeFails: Boolean = false,
        private val placeDelayMillis: Long = 0,
    ) : CadastreClient {
        var nearCalls: Int = 0
        override suspend fun findByReference(reference: String): CadastralCandidate = near.first { it.reference == reference }
        override suspend fun findNear(latitude: Double, longitude: Double): List<CadastralCandidate> {
            nearCalls++
            return near
        }
        override suspend fun locate(reference: String): RegistryLocation? {
            if (placeDelayMillis > 0) kotlinx.coroutines.delay(placeDelayMillis)
            return if (placeFails) throw java.io.IOException("offline") else places[reference]
        }
    }

    private class FakeParcels(private val existing: Parcel? = null, private val takenReference: String? = null,
        private val failOnceReference: String? = null) : ParcelRepository {
        val created = mutableListOf<NewParcel>()
        val attempts = mutableListOf<String>()
        var linked: Pair<UUID, RegistryLink>? = null
        override fun observeActive(farmId: UUID): Flow<List<Parcel>> = MutableStateFlow(listOfNotNull(existing))
        override fun observeArchived(farmId: UUID): Flow<List<Parcel>> = MutableStateFlow(emptyList())
        override fun observeById(parcelId: UUID): Flow<Parcel?> = MutableStateFlow(existing?.takeIf { it.id == parcelId })
        override suspend fun findActiveByCadastralReference(workspaceId: UUID, reference: String): UUID? =
            if (reference == takenReference) UUID.randomUUID() else null
        override suspend fun create(command: NewParcel): AppResult<UUID> {
            val reference = command.cadastralReference.orEmpty()
            attempts += reference
            if (reference == failOnceReference && attempts.count { it == reference } == 1)
                return AppResult.Failure(AppError.Conflict("duplicate_cadastral_reference"))
            created += command
            return AppResult.Success(UUID.randomUUID())
        }
        override suspend fun update(parcelId: UUID, changes: ParcelChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun linkToRegistry(parcelId: UUID, link: RegistryLink): AppResult<Unit> {
            linked = parcelId to link
            return AppResult.Success(Unit)
        }
        override suspend fun fillMissingCadastralLocation(parcelId: UUID, reference: String, municipality: String, province: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(parcelId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(parcelId: UUID, farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun membershipHistory(parcelId: UUID): List<ParcelMembership> = emptyList()
    }
}
