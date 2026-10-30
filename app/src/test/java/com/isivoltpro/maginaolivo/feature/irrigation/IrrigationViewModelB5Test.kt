package com.isivoltpro.maginaolivo.feature.irrigation

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.AgendaEntry
import com.isivoltpro.maginaolivo.domain.machinery.MachineOption
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.irrigation.AforoConfig
import com.isivoltpro.maginaolivo.domain.irrigation.AndroidFarmerIrrigationStore
import com.isivoltpro.maginaolivo.domain.irrigation.FlowMeasurementMode
import com.isivoltpro.maginaolivo.domain.irrigation.InMemoryFarmerIrrigationStore
import com.isivoltpro.maginaolivo.domain.irrigation.ParcelIrrigationProfile
import com.isivoltpro.maginaolivo.domain.irrigation.PunctualTurn
import com.isivoltpro.maginaolivo.domain.irrigation.ScheduledTimeSlot
import com.isivoltpro.maginaolivo.domain.irrigation.WaterSourceType
import com.isivoltpro.maginaolivo.domain.irrigation.WaterSupplyDetails
import com.isivoltpro.maginaolivo.domain.irrigation.WaterTariff
import com.isivoltpro.maginaolivo.domain.irrigation.WaterTariffType
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.domain.parcel.ParcelMembership
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.domain.parcel.RegistryLink
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class IrrigationViewModelB5Test {

    private val testDispatcher = StandardTestDispatcher()
    private val farmId = UUID.randomUUID()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeParcelRepository(
        private val activeFlow: MutableStateFlow<List<Parcel>>,
    ) : ParcelRepository {
        override fun observeActive(farmId: UUID): Flow<List<Parcel>> = activeFlow
        override fun observeArchived(farmId: UUID): Flow<List<Parcel>> = flowOf(emptyList())
        override fun observeById(parcelId: UUID): Flow<Parcel?> = flowOf(null)
        override suspend fun findActiveByCadastralReference(workspaceId: UUID, reference: String): UUID? = null
        override suspend fun create(command: NewParcel): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(parcelId: UUID, changes: ParcelChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun linkToRegistry(parcelId: UUID, link: RegistryLink): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(parcelId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(parcelId: UUID, farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun membershipHistory(parcelId: UUID): List<ParcelMembership> = emptyList()
    }

    private class FakeActivityRepository(
        private val farmActivitiesFlow: MutableStateFlow<List<Activity>>,
    ) : ActivityRepository {
        override fun observeForFarm(farmId: UUID): Flow<List<Activity>> = farmActivitiesFlow
        override fun observeForParcel(parcelId: UUID): Flow<List<Activity>> = flowOf(emptyList())
        override fun observe(id: UUID): Flow<Activity?> = flowOf(null)
        override fun observeAgenda(): Flow<List<AgendaEntry>> = flowOf(emptyList())
        override fun observeSelectableMachines(): Flow<List<MachineOption>> = flowOf(emptyList())
        override fun observeSelectableParcels(farmId: UUID): Flow<List<ActivityParcelOption>> = flowOf(emptyList())
        override suspend fun create(command: NewActivity): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(id: UUID, changes: ActivityChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun correctCompleted(id: UUID, changes: ActivityChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun plan(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun complete(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun cancel(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun reopen(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeFarmRepository(
        private val farmFlow: MutableStateFlow<Farm?>,
    ) : FarmRepository {
        override fun observeActive(workspaceId: UUID): Flow<List<Farm>> = flowOf(emptyList())
        override fun observeArchived(workspaceId: UUID): Flow<List<Farm>> = flowOf(emptyList())
        override fun observeById(farmId: UUID): Flow<Farm?> = farmFlow
        override suspend fun create(command: NewFarm): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(farmId: UUID, changes: FarmChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }

    private fun testFarm(id: UUID, name: String) = Farm(
        id = id,
        workspaceId = UUID.randomUUID(),
        name = name,
        description = "Finca con sistema de riego",
        municipality = "Bedmar",
        province = "Jaén",
        notes = null,
        coverDocumentId = null,
        parcelCount = 1L,
        totalAreaM2 = 20000.0,
        activeCampaignName = null,
        archivedAt = null,
        version = 1L,
    )

    private fun testParcel(id: UUID, name: String, days: Set<DayOfWeek> = emptySet()) = Parcel(
        id = id,
        workspaceId = UUID.randomUUID(),
        farmId = farmId,
        displayName = name,
        cadastralReference = null,
        cadastralPolygon = null,
        cadastralParcel = null,
        municipality = null,
        province = null,
        source = ParcelSource.MANUAL,
        geometryGeoJson = null,
        cadastralAreaM2 = null,
        managedAreaM2 = null,
        notes = null,
        archivedAt = null,
        version = 1L,
        agronomy = ParcelAgronomy(
            irrigationSystem = IrrigationSystem.DRIP,
            irrigationDays = days,
            oliveTreeCount = 400,
        ),
    )

    @Test
    fun saveProfile_persistsToStoreAndUpdatesCalendarAndSummary() = runTest {
        val parcelId = UUID.randomUUID()
        val parcel = testParcel(parcelId, "El Olivar")

        val farmFlow = MutableStateFlow<Farm?>(testFarm(farmId, "Finca Sierra"))
        val parcelsFlow = MutableStateFlow(listOf(parcel))
        val activitiesFlow = MutableStateFlow<List<Activity>>(emptyList())
        val irrigationStore = InMemoryFarmerIrrigationStore()

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            today = LocalDate.of(2026, 6, 1), // Monday
        )
        advanceUntilIdle()

        val profile = ParcelIrrigationProfile(
            parcelId = parcelId,
            supply = WaterSupplyDetails(
                sourceType = WaterSourceType.IRRIGATION_COMMUNITY,
                communityName = "Comunidad San Isidro",
                sector = "Sector 1",
                hydrant = "H-05",
                intakeOrValve = "Boca 2",
            ),
            aforo = AforoConfig(
                measurementMode = FlowMeasurementMode.CALCULATED_FROM_EMITTERS,
                oliveTreeCount = 400,
                emittersPerTree = 2,
                emitterFlowLph = 4.0, // 3.2 m³/h
            ),
            weeklySlots = mapOf(
                DayOfWeek.MONDAY to ScheduledTimeSlot(
                    startTime = LocalTime.of(22, 0),
                    endTime = LocalTime.of(6, 0), // 8h = 25.6 m³
                )
            ),
            tariff = WaterTariff(
                tariffType = WaterTariffType.PER_M3,
                variablePricePerUnitMinor = 20L, // 0.20 €/m³ -> 25.6 * 20 = 512 cents
                annualCommunityQuotaMinor = 18000L, // 180.00 € cuota fija anual
            ),
        )

        viewModel.saveProfile(profile)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.profiles.size)
        assertEquals("Comunidad San Isidro", state.profiles[parcelId]?.supply?.communityName)

        // Verify weekly plan projected metrics
        assertEquals(25.6, state.weeklyPlan.totalEstimatedWeeklyVolumeM3, 0.01)
        assertEquals(512L, state.weeklyPlan.totalEstimatedWeeklyCostMinor)

        // Verify summary captures annual community quota separated from variable costs
        assertEquals(1, state.summary.annualCommunityFees.size)
        assertEquals("Comunidad San Isidro", state.summary.annualCommunityFees.first().communityName)
        assertEquals(18000L, state.summary.annualCommunityFees.first().annualFixedFeeMinor)
    }

    @Test
    fun addPunctualTurnAndToggleCancellation_updatesProjectionsCorrectly() = runTest {
        val parcelId = UUID.randomUUID()
        val parcel = testParcel(parcelId, "La Solana")

        val farmFlow = MutableStateFlow<Farm?>(testFarm(farmId, "Finca Sierra"))
        val parcelsFlow = MutableStateFlow(listOf(parcel))
        val activitiesFlow = MutableStateFlow<List<Activity>>(emptyList())
        val irrigationStore = InMemoryFarmerIrrigationStore()

        val referenceMonday = LocalDate.of(2026, 6, 1)
        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            today = referenceMonday,
        )
        advanceUntilIdle()

        // Set base profile with direct hydrant flow
        val profile = ParcelIrrigationProfile(
            parcelId = parcelId,
            aforo = AforoConfig(
                measurementMode = FlowMeasurementMode.DIRECT_HYDRANT_FLOW,
                directFlowRate = 10.0, // 10 m³/h
            ),
            tariff = WaterTariff(variablePricePerUnitMinor = 25L), // 0.25 €/m³
        )
        viewModel.saveProfile(profile)
        advanceUntilIdle()

        // Add extraordinary turn on Wednesday June 3 (2h: 18:00 to 20:00 -> 20 m³ -> 500 cents)
        val punctualTurnId = UUID.randomUUID()
        val punctualTurn = PunctualTurn(
            id = punctualTurnId,
            parcelId = parcelId,
            date = referenceMonday.plusDays(2),
            timeSlot = ScheduledTimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0)),
            reason = "Aporte de socorro",
        )

        viewModel.addPunctualTurn(punctualTurn)
        advanceUntilIdle()

        var state = viewModel.state.value
        assertEquals(20.0, state.weeklyPlan.totalEstimatedWeeklyVolumeM3, 0.01)
        assertEquals(500L, state.weeklyPlan.totalEstimatedWeeklyCostMinor)

        // Toggle cancellation
        viewModel.togglePunctualTurnCancellation(parcelId, punctualTurnId)
        advanceUntilIdle()

        state = viewModel.state.value
        val turnItem = state.weeklyPlan.days.first { it.dayOfWeek == DayOfWeek.WEDNESDAY }.turns.first()
        assertTrue(turnItem.isCancelled)
        assertEquals(0.0, state.weeklyPlan.totalEstimatedWeeklyVolumeM3, 0.01)
        assertEquals(0L, state.weeklyPlan.totalEstimatedWeeklyCostMinor)
    }

    @Test
    fun foreignParcelsAndQuotasAreNotLeakedIntoHoldingSummary() = runTest {
        val farm = testFarm(farmId, "Finca Activa")
        val parcelA = testParcel(id = UUID.randomUUID(), name = "Parcela Finca Activa")
        val foreignParcelId = UUID.randomUUID()

        val farmFlow = MutableStateFlow<Farm?>(farm)
        val parcelsFlow = MutableStateFlow(listOf(parcelA))
        val activitiesFlow = MutableStateFlow<List<Activity>>(emptyList())
        val irrigationStore = InMemoryFarmerIrrigationStore()
        val scope = AndroidFarmerIrrigationStore.scopeKey(null, farm.workspaceId)
        irrigationStore.setScope(scope)

        // Persist profile for active parcel
        irrigationStore.saveProfile(
            ParcelIrrigationProfile(
                parcelId = parcelA.id,
                supply = WaterSupplyDetails(communityName = "Comunidad Activa", sector = "1"),
                tariff = WaterTariff(annualCommunityQuotaMinor = 12000L), // 120 €
            )
        )

        // Persist profile for foreign parcel (e.g. from another farm in same workspace or foreign collection)
        irrigationStore.saveProfile(
            ParcelIrrigationProfile(
                parcelId = foreignParcelId,
                supply = WaterSupplyDetails(communityName = "Comunidad Ajena", sector = "99"),
                tariff = WaterTariff(annualCommunityQuotaMinor = 50000L), // 500 €
            )
        )

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        // Only parcel A is in profiles
        assertEquals(1, state.profiles.size)
        assertTrue(state.profiles.containsKey(parcelA.id))
        assertFalse(state.profiles.containsKey(foreignParcelId))

        // Quotas: only Comunidad Activa is present, foreign quota is completely excluded
        assertEquals(1, state.summary.annualCommunityFees.size)
        assertEquals("Comunidad Activa", state.summary.annualCommunityFees.first().communityName)
        assertEquals(12000L, state.summary.totalAnnualFixedFeesMinor)
    }

    @Test
    fun annualFeesDoNotDuplicateWhenMultipleParcelsShareSameCommunityAndSector() = runTest {
        val farm = testFarm(farmId, "Finca Dos Parcelas")
        val parcel1 = testParcel(id = UUID.randomUUID(), name = "Parcela Arriba")
        val parcel2 = testParcel(id = UUID.randomUUID(), name = "Parcela Abajo")

        val farmFlow = MutableStateFlow<Farm?>(farm)
        val parcelsFlow = MutableStateFlow(listOf(parcel1, parcel2))
        val activitiesFlow = MutableStateFlow<List<Activity>>(emptyList())
        val irrigationStore = InMemoryFarmerIrrigationStore()
        val scope = AndroidFarmerIrrigationStore.scopeKey(null, farm.workspaceId)
        irrigationStore.setScope(scope)

        // Both parcels share the SAME Community and Sector
        val sharedSupply = WaterSupplyDetails(communityName = "Comunidad San Isidro", sector = "Sector 2")
        val sharedTariff = WaterTariff(annualCommunityQuotaMinor = 15000L) // 150 € per holding/sector quota

        irrigationStore.saveProfile(ParcelIrrigationProfile(parcelId = parcel1.id, supply = sharedSupply, tariff = sharedTariff))
        irrigationStore.saveProfile(ParcelIrrigationProfile(parcelId = parcel2.id, supply = sharedSupply, tariff = sharedTariff))

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        // Exactly one shared quota, no duplicate 300 € charge
        assertEquals(1, state.summary.annualCommunityFees.size)
        assertEquals(15000L, state.summary.totalAnnualFixedFeesMinor)
        assertEquals("Comunidad San Isidro", state.summary.annualCommunityFees.first().communityName)
    }

    @Test
    fun annualFeesPreserveDistinctSectorsAndDistinctCommunities() = runTest {
        val farm = testFarm(farmId, "Finca Multi Sector")
        val parcelSector1 = testParcel(id = UUID.randomUUID(), name = "Parcela Sector 1")
        val parcelSector2 = testParcel(id = UUID.randomUUID(), name = "Parcela Sector 2")
        val parcelOtherComm = testParcel(id = UUID.randomUUID(), name = "Parcela Cuenca")

        val farmFlow = MutableStateFlow<Farm?>(farm)
        val parcelsFlow = MutableStateFlow(listOf(parcelSector1, parcelSector2, parcelOtherComm))
        val activitiesFlow = MutableStateFlow<List<Activity>>(emptyList())
        val irrigationStore = InMemoryFarmerIrrigationStore()
        val scope = AndroidFarmerIrrigationStore.scopeKey(null, farm.workspaceId)
        irrigationStore.setScope(scope)

        // Same community name but DIFFERENT sectors -> legitimate distinct quotas
        irrigationStore.saveProfile(
            ParcelIrrigationProfile(
                parcelId = parcelSector1.id,
                supply = WaterSupplyDetails(communityName = "Comunidad San Isidro", sector = "1"),
                tariff = WaterTariff(annualCommunityQuotaMinor = 10000L), // 100 €
            )
        )
        irrigationStore.saveProfile(
            ParcelIrrigationProfile(
                parcelId = parcelSector2.id,
                supply = WaterSupplyDetails(communityName = "Comunidad San Isidro", sector = "2"),
                tariff = WaterTariff(annualCommunityQuotaMinor = 15000L), // 150 €
            )
        )
        // Different community
        irrigationStore.saveProfile(
            ParcelIrrigationProfile(
                parcelId = parcelOtherComm.id,
                supply = WaterSupplyDetails(communityName = "Comunidad San Marcos", sector = "A"),
                tariff = WaterTariff(annualCommunityQuotaMinor = 8000L), // 80 €
            )
        )

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        // All 3 distinct quotas preserved: 100 + 150 + 80 = 330 €
        assertEquals(3, state.summary.annualCommunityFees.size)
        assertEquals(33000L, state.summary.totalAnnualFixedFeesMinor)
    }

    @Test
    fun scopeIsolation_preservesProfilesWhenSwitchingWorkspacesA_to_B_to_A() = runTest {
        val store = InMemoryFarmerIrrigationStore()
        val parcelA = UUID.randomUUID()
        val parcelB = UUID.randomUUID()

        val scopeA = "user1:::workspaceA"
        val scopeB = "user1:::workspaceB"

        // 1. Save in Scope A
        store.setScope(scopeA)
        store.saveProfile(ParcelIrrigationProfile(parcelId = parcelA, supply = WaterSupplyDetails(communityName = "Comunidad A")))
        assertEquals(1, store.getAllProfiles().size)
        assertTrue(store.getAllProfiles().containsKey(parcelA))

        // 2. Switch to Scope B
        store.setScope(scopeB)
        // Scope B starts clean, does not see A's profiles
        assertEquals(0, store.getAllProfiles().size)
        store.saveProfile(ParcelIrrigationProfile(parcelId = parcelB, supply = WaterSupplyDetails(communityName = "Comunidad B")))
        assertEquals(1, store.getAllProfiles().size)
        assertTrue(store.getAllProfiles().containsKey(parcelB))
        assertFalse(store.getAllProfiles().containsKey(parcelA))

        // 3. Switch back to Scope A: A's profiles are intact and recovered (no data loss)
        store.setScope(scopeA)
        assertEquals(1, store.getAllProfiles().size)
        assertTrue(store.getAllProfiles().containsKey(parcelA))
        assertFalse(store.getAllProfiles().containsKey(parcelB))
        assertEquals("Comunidad A", store.getProfile(parcelA)?.supply?.communityName)
    }

    @Test
    fun accountIsolation_differentUsersInSameWorkspace_haveCompletelyIndependentPartitions() = runTest {
        val farm = testFarm(farmId, "Finca Compartida")
        val parcelA = testParcel(id = UUID.randomUUID(), name = "Parcela Comun")
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()

        val farmFlow = MutableStateFlow<Farm?>(farm)
        val parcelsFlow = MutableStateFlow(listOf(parcelA))
        val activitiesFlow = MutableStateFlow<List<Activity>>(emptyList())
        val irrigationStore = InMemoryFarmerIrrigationStore()

        var currentUser = user1

        // User 1 creates ViewModel and saves a profile for parcelA
        val vmUser1 = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            currentUserIdProvider = { currentUser },
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        vmUser1.saveProfile(
            ParcelIrrigationProfile(
                parcelId = parcelA.id,
                supply = WaterSupplyDetails(communityName = "Comunidad User 1"),
                tariff = WaterTariff(annualCommunityQuotaMinor = 10000L),
            )
        )
        advanceUntilIdle()

        assertEquals("Comunidad User 1", vmUser1.state.value.profiles[parcelA.id]?.supply?.communityName)

        // Switch to User 2 in the same workspace
        currentUser = user2
        val vmUser2 = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            currentUserIdProvider = { currentUser },
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        // User 2 has clean initial state for parcelA (does not inherit User 1's profile)
        assertFalse(vmUser2.state.value.profiles.containsKey(parcelA.id))

        // User 2 saves their own distinct configuration for parcelA
        vmUser2.saveProfile(
            ParcelIrrigationProfile(
                parcelId = parcelA.id,
                supply = WaterSupplyDetails(communityName = "Comunidad User 2"),
                tariff = WaterTariff(annualCommunityQuotaMinor = 25000L),
            )
        )
        advanceUntilIdle()
        assertEquals("Comunidad User 2", vmUser2.state.value.profiles[parcelA.id]?.supply?.communityName)

        // Switch back to User 1: User 1's profile is intact (no data loss, no cross-contamination)
        currentUser = user1
        val vmUser1Reloaded = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            currentUserIdProvider = { currentUser },
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        assertEquals("Comunidad User 1", vmUser1Reloaded.state.value.profiles[parcelA.id]?.supply?.communityName)
    }

    @Test
    fun annualFees_sameSectorWithDifferentQuotas_arePreservedAsDistinctObligations() = runTest {
        val farm = testFarm(farmId, "Finca Dos Tomas Mismo Sector")
        val parcelToma1 = testParcel(id = UUID.randomUUID(), name = "Parcela Toma 1")
        val parcelToma2 = testParcel(id = UUID.randomUUID(), name = "Parcela Toma 2")

        val farmFlow = MutableStateFlow<Farm?>(farm)
        val parcelsFlow = MutableStateFlow(listOf(parcelToma1, parcelToma2))
        val activitiesFlow = MutableStateFlow<List<Activity>>(emptyList())
        val irrigationStore = InMemoryFarmerIrrigationStore()
        val scope = AndroidFarmerIrrigationStore.scopeKey(null, farm.workspaceId)
        irrigationStore.setScope(scope)

        // Both in Sector 2, but DIFFERENT quota amounts (e.g. 150 € regular + 80 € well canon)
        irrigationStore.saveProfile(
            ParcelIrrigationProfile(
                parcelId = parcelToma1.id,
                supply = WaterSupplyDetails(communityName = "Comunidad San Isidro", sector = "Sector 2"),
                tariff = WaterTariff(annualCommunityQuotaMinor = 15000L), // 150 €
            )
        )
        irrigationStore.saveProfile(
            ParcelIrrigationProfile(
                parcelId = parcelToma2.id,
                supply = WaterSupplyDetails(communityName = "Comunidad San Isidro", sector = "Sector 2"),
                tariff = WaterTariff(annualCommunityQuotaMinor = 8000L), // 80 €
            )
        )

        val viewModel = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        // Both distinct obligations must be preserved: 150 € + 80 € = 230 €
        assertEquals(2, state.summary.annualCommunityFees.size)
        assertEquals(23000L, state.summary.totalAnnualFixedFeesMinor)
    }

    @Test
    fun accountLogin_doesNotAutomaticallyClaimLocalConfigurations_andPreservesLocalData() = runTest {
        val farm = testFarm(farmId, "Finca Protegida Sin Reclamo Automatico")
        val parcel = testParcel(id = UUID.randomUUID(), name = "Parcela Local")
        val userBob = UUID.randomUUID()
        val userAlice = UUID.randomUUID()

        val farmFlow = MutableStateFlow<Farm?>(farm)
        val parcelsFlow = MutableStateFlow(listOf(parcel))
        val activitiesFlow = MutableStateFlow<List<Activity>>(emptyList())
        val irrigationStore = InMemoryFarmerIrrigationStore()

        // 1. Initial state: unlinked local device has a profile configured in local mode
        val localScope = AndroidFarmerIrrigationStore.scopeKey(null, farm.workspaceId)
        irrigationStore.setScope(localScope)
        irrigationStore.saveProfile(
            ParcelIrrigationProfile(
                parcelId = parcel.id,
                supply = WaterSupplyDetails(communityName = "Comunidad Local Original"),
            )
        )

        var currentUser: UUID? = null

        // 2. Open ViewModel in unauthenticated local session -> sees local profile
        val vmGuest = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            currentUserIdProvider = { currentUser },
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        assertEquals("Comunidad Local Original", vmGuest.state.value.profiles[parcel.id]?.supply?.communityName)

        // 3. User Bob signs in FIRST (e.g. unauthorized or different person on shared phone)
        currentUser = userBob
        val vmBob = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            currentUserIdProvider = { currentUser },
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        // User Bob must NOT automatically claim, see, or delete local data
        assertTrue("Bob's profile map must be empty (fail-closed, no auto-claim)", vmBob.state.value.profiles.isEmpty())

        // 4. User Alice signs in next: Alice also does NOT automatically steal local data
        currentUser = userAlice
        val vmAlice = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            currentUserIdProvider = { currentUser },
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        assertTrue("Alice's profile map is initially empty before explicit link", vmAlice.state.value.profiles.isEmpty())

        // 5. Explicit authorized linking: Alice affirmatively links her authorized parcel
        val linkedCount = irrigationStore.linkLocalProfilesToAccount(
            targetUserId = userAlice.toString(),
            targetWorkspaceId = farm.workspaceId,
            parcelIdsToLink = setOf(parcel.id),
        )
        assertEquals(1, linkedCount)

        // Reload Alice's ViewModel: Alice now safely has the linked profile in her partition
        val vmAliceLinked = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            currentUserIdProvider = { currentUser },
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()
        assertEquals("Comunidad Local Original", vmAliceLinked.state.value.profiles[parcel.id]?.supply?.communityName)

        // 6. User Bob logs in again: Bob still has 0 profiles (no cross-contamination from Alice's link)
        currentUser = userBob
        val vmBobReloaded = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            currentUserIdProvider = { currentUser },
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()
        assertTrue("Bob still sees no profiles", vmBobReloaded.state.value.profiles.isEmpty())

        // 7. Return to local/unauthenticated mode: local profile remains 100% intact (non-destructive)
        currentUser = null
        val vmGuestReturned = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            currentUserIdProvider = { currentUser },
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()
        assertEquals("Comunidad Local Original", vmGuestReturned.state.value.profiles[parcel.id]?.supply?.communityName)
    }

    @Test
    fun linkLocalProfilesToAccount_negativeTest_sameParcelInTwoWorkspaces_neverCopiesForeignWorkspaceData() = runTest {
        val sharedParcel = testParcel(id = UUID.randomUUID(), name = "Parcela Comun Multiexplotacion")
        val foreignWorkspaceId = UUID.randomUUID()
        val authorizedFarm = testFarm(farmId, "Finca Explotacion Propia")

        val farmFlow = MutableStateFlow<Farm?>(authorizedFarm)
        val parcelsFlow = MutableStateFlow(listOf(sharedParcel))
        val activitiesFlow = MutableStateFlow<List<Activity>>(emptyList())
        val irrigationStore = InMemoryFarmerIrrigationStore()

        // Configure parcel locally in a foreign exploitation workspace
        val foreignLocalScope = "local:::$foreignWorkspaceId"
        irrigationStore.setScope(foreignLocalScope)
        irrigationStore.saveProfile(
            ParcelIrrigationProfile(
                parcelId = sharedParcel.id,
                supply = WaterSupplyDetails(communityName = "Comunidad Ajena Finca Vecina"),
            )
        )

        // Switch to authorized user on own exploitation farm
        val userOwner = UUID.randomUUID()
        val vmOwner = IrrigationViewModel(
            farmId = farmId,
            parcelRepository = FakeParcelRepository(parcelsFlow),
            activityRepository = FakeActivityRepository(activitiesFlow),
            farmRepository = FakeFarmRepository(farmFlow),
            farmerIrrigationStore = irrigationStore,
            currentUserIdProvider = { userOwner },
            today = LocalDate.of(2026, 6, 1),
        )
        advanceUntilIdle()

        // Attempt linking for sharedParcel targeting authorizedFarm.workspaceId with authorizedFarm.workspaceId as source
        val linkedCount = irrigationStore.linkLocalProfilesToAccount(
            targetUserId = userOwner.toString(),
            targetWorkspaceId = authorizedFarm.workspaceId,
            parcelIdsToLink = setOf(sharedParcel.id),
            sourceWorkspaceId = authorizedFarm.workspaceId,
        )

        // Zero profiles linked; must never pull local configs from foreignWorkspaceId
        assertEquals(0, linkedCount)
        assertTrue("Owner profile map must remain empty (fail-closed, no leak from foreign workspace)", vmOwner.state.value.profiles.isEmpty())
        assertNull(irrigationStore.getProfile(sharedParcel.id))

        // Foreign workspace local profile remains intact
        irrigationStore.setScope(foreignLocalScope)
        assertEquals("Comunidad Ajena Finca Vecina", irrigationStore.getProfile(sharedParcel.id)?.supply?.communityName)
    }
}
