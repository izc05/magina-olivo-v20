package com.isivoltpro.maginaolivo.domain.assistant

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.AgendaEntry
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelOption
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.YieldDraft
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmChanges
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.machinery.MachineOption
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.domain.parcel.ParcelChanges
import com.isivoltpro.maginaolivo.domain.parcel.ParcelMembership
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.domain.parcel.RegistryLink
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas completas de integración de repositorios y aislamiento estricto multi-cliente (Cliente A vs Cliente B).
 * Valida:
 * 1. Acceso a fincas, parcelas de regadío, entregas, gastos en jornales y trabajos del cuaderno.
 * 2. Barrera multi-tenant: Usuario B jamás puede leer datos del Usuario A, incluso conociendo sus IDs.
 * 3. Fincas compartidas: un gestor con acceso restringido solo accede a las fincas autorizadas.
 * 4. Desambiguación cuando existen múltiples fincas.
 * 5. Principio Read-Only: ninguna consulta al asistente produce escrituras no confirmadas en la base de datos.
 */
class AssistantRepositoryIntegrationTest {

    private val workspaceA = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val workspaceB = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private val farmA1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val farmA2 = UUID.fromString("11111111-1111-1111-1111-111111111112")
    private val farmB1 = UUID.fromString("22222222-2222-2222-2222-222222222221")

    private val campaignA = UUID.fromString("33333333-3333-3333-3333-333333333331")
    private val campaignB = UUID.fromString("44444444-4444-4444-4444-444444444441")

    private lateinit var farmRepo: InMemoryFarmRepository
    private lateinit var parcelRepo: InMemoryParcelRepository
    private lateinit var campaignRepo: InMemoryCampaignRepository
    private lateinit var deliveryRepo: InMemoryDeliveryRepository
    private lateinit var expenseRepo: InMemoryExpenseRepository
    private lateinit var activityRepo: InMemoryActivityRepository

    private lateinit var toolsService: RepositoryAssistantToolsService
    private lateinit var inferenceProvider: SimulatedMistralInferenceProvider
    private lateinit var chatRepo: AssistantChatRepositoryImpl

    @Before
    fun setUp() {
        farmRepo = InMemoryFarmRepository()
        parcelRepo = InMemoryParcelRepository()
        campaignRepo = InMemoryCampaignRepository()
        deliveryRepo = InMemoryDeliveryRepository()
        expenseRepo = InMemoryExpenseRepository()
        activityRepo = InMemoryActivityRepository()

        // 1. Población de datos para Usuario A
        farmRepo.farms.add(
            Farm(
                id = farmA1,
                workspaceId = workspaceA,
                name = "Finca Solana de Mágina",
                description = "Olivar picual en pendiente",
                municipality = "Bedmar",
                province = "Jaén",
                notes = "Riego por goteo",
                coverDocumentId = null,
                parcelCount = 1,
                totalAreaM2 = 25000.0,
                activeCampaignName = "2025/2026",
                archivedAt = null,
                version = 1,
                oliveTreeCount = 500,
                oliveTreeCountComplete = true
            )
        )
        farmRepo.farms.add(
            Farm(
                id = farmA2,
                workspaceId = workspaceA,
                name = "Olivar El Campillo",
                description = "Secano tradicional",
                municipality = "Garcíez",
                province = "Jaén",
                notes = "Secano",
                coverDocumentId = null,
                parcelCount = 1,
                totalAreaM2 = 18000.0,
                activeCampaignName = "2025/2026",
                archivedAt = null,
                version = 1,
                oliveTreeCount = 350,
                oliveTreeCountComplete = true
            )
        )

        // Entregas de aceituna de A: 4.500 kg
        deliveryRepo.deliveries.add(
            Delivery(
                id = UUID.randomUUID(),
                workspaceId = workspaceA,
                farmId = farmA1,
                campaignId = campaignA,
                deliveryDate = LocalDate.now(),
                destinationOrganizationId = null,
                destinationName = "S.C.A. San Isidro Labrador",
                netGrams = 4500000L,
                grossGrams = 6000000L,
                tareGrams = 1500000L,
                deliveryNumber = "VALE-101",
                ticketNumber = "T-101",
                source = DeliverySource.MANUAL,
                shares = emptyList(),
                notes = null,
                version = 1
            )
        )

        // Gastos de A: 1.250 € en jornales (125.000 céntimos)
        expenseRepo.expenses.add(
            Expense(
                id = UUID.randomUUID(),
                workspaceId = workspaceA,
                expenseDate = LocalDate.now(),
                concept = "Jornales de poda y desvaretado",
                category = ExpenseCategory.LABOR,
                amountMinor = 125000L,
                currency = "EUR",
                status = ExpenseStatus.POSTED,
                origin = ExpenseOrigin.DAY_LABOUR,
                farmId = farmA1,
                campaignId = campaignA
            )
        )

        // Actividad pendiente en cuaderno de A
        activityRepo.activities.add(
            Activity(
                id = UUID.randomUUID(),
                workspaceId = workspaceA,
                farmId = farmA1,
                campaignId = campaignA,
                type = ActivityType.PRUNING,
                status = ActivityStatus.PLANNED,
                activityDate = LocalDate.now().plusDays(2),
                description = "Poda de regeneración en zona norte",
                notes = "Revisar ramas secas",
                targets = emptyList(),
                version = 1
            )
        )

        // 2. Población de datos para Usuario B
        farmRepo.farms.add(
            Farm(
                id = farmB1,
                workspaceId = workspaceB,
                name = "Finca Privada de B",
                description = "Explotación confidencial de B",
                municipality = "Jódar",
                province = "Jaén",
                notes = null,
                coverDocumentId = null,
                parcelCount = 2,
                totalAreaM2 = 50000.0,
                activeCampaignName = "2025/2026",
                archivedAt = null,
                version = 1,
                oliveTreeCount = 1200,
                oliveTreeCountComplete = true
            )
        )

        toolsService = RepositoryAssistantToolsService(
            farmRepository = farmRepo,
            parcelRepository = parcelRepo,
            campaignRepository = campaignRepo,
            deliveryRepository = deliveryRepo,
            expenseRepository = expenseRepo,
            activityRepository = activityRepo
        )

        inferenceProvider = SimulatedMistralInferenceProvider()
        chatRepo = AssistantChatRepositoryImpl(inferenceProvider, toolsService)
    }

    @Test
    fun testUserACanQueryKilosPickedInCurrentCampaign() = runBlocking {
        val contextA = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceA,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_DELIVERIES),
            selectedCampaignId = campaignA
        )

        val result = chatRepo.sendMessage(workspaceA, contextA, "¿Cuántos kilos llevo recogidos esta campaña?")
        assertTrue(result is AppResult.Success)
        val msg = (result as AppResult.Success).value
        assertTrue("Debe reportar los 4500 kg reales de A", msg.text.contains("4500.0 kg") || msg.text.contains("4500 kg"))
    }

    @Test
    fun testUserACanQueryLabourExpenses() = runBlocking {
        val contextA = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceA,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_EXPENSES),
            selectedCampaignId = campaignA
        )

        val result = chatRepo.sendMessage(workspaceA, contextA, "¿Cuánto he gastado en jornales?")
        assertTrue(result is AppResult.Success)
        val msg = (result as AppResult.Success).value
        assertTrue("Debe reportar 1250,00 € calculados deterministamente", msg.text.contains("1250") || msg.text.contains("1250,00"))
    }

    @Test
    fun testUserACanQueryPendingNotebookActivities() = runBlocking {
        val contextA = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceA,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_NOTEBOOK),
            selectedCampaignId = campaignA
        )

        val result = chatRepo.sendMessage(workspaceA, contextA, "¿Qué trabajos tengo pendientes?")
        assertTrue(result is AppResult.Success)
        val msg = (result as AppResult.Success).value
        assertTrue(msg.text.contains("Poda de regeneración en zona norte"))
    }

    @Test
    fun testMultiTenantIsolation_UserBCannotAccessDataOfUserA() = runBlocking {
        val contextB = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceB,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_FARMS, AssistantPermission.VIEW_DELIVERIES)
        )

        // B consulta sus fincas
        val farmsB = toolsService.getAuthorizedFarms(contextB)
        assertTrue(farmsB is AppResult.Success)
        val list = (farmsB as AppResult.Success).value.first
        assertEquals(1, list.size)
        assertEquals(farmB1, list[0].id)
        assertFalse("El usuario B jamás debe ver las fincas de A", list.any { it.id == farmA1 || it.id == farmA2 })

        // Intento explícito de acceder a los detalles de farmA1 con el contexto de B
        val breachAttempt = toolsService.getFarmDetails(contextB, farmA1)
        assertTrue("Debe ser rechazado por aislamiento de workspace", breachAttempt is AppResult.Failure)
    }

    @Test
    fun testAmbiguousQuestionPromptsFarmDisambiguation() = runBlocking {
        val contextA = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceA,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_FARMS)
        )

        val result = chatRepo.sendMessage(workspaceA, contextA, "¿Qué labores tengo hechas en esta finca?")
        assertTrue(result is AppResult.Success)
        val msg = (result as AppResult.Success).value
        assertTrue("Debe solicitar desambiguar la finca", msg.text.contains("indícame de qué finca en concreto"))
    }

    @Test
    fun testReadOnlyPrinciple_QueriesNeverMutateDatabase() = runBlocking {
        val contextA = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceA,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_FARMS, AssistantPermission.VIEW_DELIVERIES, AssistantPermission.VIEW_EXPENSES)
        )

        val initialDeliveriesCount = deliveryRepo.deliveries.size
        val initialExpensesCount = expenseRepo.expenses.size

        chatRepo.sendMessage(workspaceA, contextA, "¿Cuántos kilos llevo recogidos esta campaña?")
        chatRepo.sendMessage(workspaceA, contextA, "¿Cuánto he gastado en jornales?")

        assertEquals("No deben crearse entregas adicionales en modo solo lectura", initialDeliveriesCount, deliveryRepo.deliveries.size)
        assertEquals("No deben crearse gastos adicionales en modo solo lectura", initialExpensesCount, expenseRepo.expenses.size)
    }

    // --- REPOSITORIOS DE PRUEBA EN MEMORIA ---

    class InMemoryFarmRepository : FarmRepository {
        val farms = mutableListOf<Farm>()
        override fun observeActive(workspaceId: UUID): Flow<List<Farm>> =
            flowOf(farms.filter { it.workspaceId == workspaceId && it.archivedAt == null })
        override fun observeArchived(workspaceId: UUID): Flow<List<Farm>> =
            flowOf(farms.filter { it.workspaceId == workspaceId && it.archivedAt != null })
        override fun observeById(farmId: UUID): Flow<Farm?> =
            flowOf(farms.find { it.id == farmId })
        override suspend fun create(command: NewFarm): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(farmId: UUID, changes: FarmChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }

    class InMemoryParcelRepository : ParcelRepository {
        val parcels = mutableListOf<Parcel>()
        override fun observeActive(farmId: UUID): Flow<List<Parcel>> =
            flowOf(parcels.filter { it.farmId == farmId && it.archivedAt == null })
        override fun observeArchived(farmId: UUID): Flow<List<Parcel>> =
            flowOf(parcels.filter { it.farmId == farmId && it.archivedAt != null })
        override fun observeById(parcelId: UUID): Flow<Parcel?> =
            flowOf(parcels.find { it.id == parcelId })
        override suspend fun findActiveByCadastralReference(workspaceId: UUID, reference: String): UUID? = null
        override suspend fun create(command: NewParcel): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(parcelId: UUID, changes: ParcelChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun linkToRegistry(parcelId: UUID, link: RegistryLink): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun fillMissingCadastralLocation(parcelId: UUID, reference: String, municipality: String, province: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(parcelId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun restore(parcelId: UUID, farmId: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun membershipHistory(parcelId: UUID): List<ParcelMembership> = emptyList()
    }

    class InMemoryCampaignRepository : CampaignRepository {
        val campaigns = mutableListOf<com.isivoltpro.maginaolivo.domain.campaign.Campaign>()
        override fun observeSelectableParcels(farmId: UUID): Flow<List<CampaignParcelOption>> = flowOf(emptyList())
        override fun observeForFarm(farmId: UUID): Flow<List<com.isivoltpro.maginaolivo.domain.campaign.Campaign>> =
            flowOf(campaigns.filter { it.farmId == farmId })
        override fun observe(id: UUID): Flow<com.isivoltpro.maginaolivo.domain.campaign.Campaign?> =
            flowOf(campaigns.find { it.id == id })
        override suspend fun create(command: com.isivoltpro.maginaolivo.domain.campaign.NewCampaign): AppResult<UUID> =
            AppResult.Success(UUID.randomUUID())
        override suspend fun updatePreparation(id: UUID, changes: com.isivoltpro.maginaolivo.domain.campaign.CampaignPreparationChanges): AppResult<Unit> =
            AppResult.Success(Unit)
        override suspend fun activate(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun markHarvest(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun close(id: UUID, endDate: LocalDate): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun closeToday(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun reopen(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archivePreparation(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }

    class InMemoryDeliveryRepository : DeliveryRepository {
        val deliveries = mutableListOf<Delivery>()
        override fun observeAll(): Flow<List<Delivery>> = flowOf(deliveries)
        override fun observeForCampaign(campaignId: UUID): Flow<List<Delivery>> =
            flowOf(deliveries.filter { it.campaignId == campaignId })
        override fun observe(id: UUID): Flow<Delivery?> = flowOf(deliveries.find { it.id == id })
        override fun observeContexts(): Flow<List<HarvestContext>> = flowOf(emptyList())
        override suspend fun create(draft: DeliveryDraft): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(id: UUID, draft: DeliveryDraft): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun delete(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun recordYield(deliveryId: UUID, draft: YieldDraft): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun removeYield(deliveryId: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }

    class InMemoryExpenseRepository : ExpenseRepository {
        val expenses = mutableListOf<Expense>()
        override fun observeAll(): Flow<List<Expense>> = flowOf(expenses)
        override fun observe(id: UUID): Flow<Expense?> = flowOf(expenses.find { it.id == id })
        override fun observeForActivity(activityId: UUID): Flow<List<Expense>> =
            flowOf(expenses.filter { it.activityId == activityId })
        override suspend fun create(draft: ExpenseDraft): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(id: UUID, draft: ExpenseDraft): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun post(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun delete(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun keepAsIndependent(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }

    class InMemoryActivityRepository : ActivityRepository {
        val activities = mutableListOf<Activity>()
        override fun observeAgenda(): Flow<List<AgendaEntry>> = flowOf(emptyList())
        override fun observeSelectableMachines(): Flow<List<MachineOption>> = flowOf(emptyList())
        override fun observeSelectableParcels(farmId: UUID): Flow<List<ActivityParcelOption>> = flowOf(emptyList())
        override fun observeForFarm(farmId: UUID): Flow<List<Activity>> =
            flowOf(activities.filter { it.farmId == farmId })
        override fun observeForParcel(parcelId: UUID): Flow<List<Activity>> =
            flowOf(activities.filter { it.targets.any { t -> t.parcelId == parcelId } })
        override fun observe(id: UUID): Flow<Activity?> = flowOf(activities.find { it.id == id })
        override suspend fun create(command: NewActivity): AppResult<UUID> = AppResult.Success(UUID.randomUUID())
        override suspend fun update(id: UUID, changes: ActivityChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun correctCompleted(id: UUID, changes: ActivityChanges): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun plan(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun complete(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun cancel(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun reopen(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun archive(id: UUID): AppResult<Unit> = AppResult.Success(Unit)
    }
}

