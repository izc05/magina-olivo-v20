package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.*
import com.isivoltpro.maginaolivo.data.repository.LocalAgriculturalCycleRepository
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.domain.cycle.CycleRecordKind
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstActivityRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstExpenseRepository
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgriculturalCycleRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "cr015-cycles-isolated-test.db"
    private lateinit var db: MaginaOlivoDatabase
    private val workspace = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val otherFarm = UUID.randomUUID()
    private val selected = MutableStateFlow<UUID?>(workspace)
    private var user: UUID? = owner
    private var provisioningCalls = 0
    private val clock = object : AppClock {
        override fun nowInstant() = Instant.parse("2027-05-04T12:00:00Z")
        override fun today(zoneId: ZoneId) = LocalDate.of(2027, 5, 4)
    }
    private val scope = object : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> { provisioningCalls++; return activeWorkspace() }
        override suspend fun activeWorkspace(): AppResult<UUID> = selected.value?.let { AppResult.Success(it) }
            ?: AppResult.Failure(com.isivoltpro.maginaolivo.core.common.AppError.Permission("signed_out"))
        override fun currentOwnerId() = user
        override fun observeLocalWorkspace() = selected
    }
    private fun repository() = LocalAgriculturalCycleRepository(db, scope, clock)
    private fun date(value: String) = LocalDate.parse(value)
    private fun <T> AppResult<T>.value(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }

    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        val meta = LocalMetadata(clock.nowInstant(), clock.nowInstant())
        db.workspaceDao().upsert(WorkspaceEntity(workspace,"QA","$owner".let(UUID::fromString),"ES","Europe/Madrid","es-ES","EUR",meta))
        db.farmDao().upsert(FarmEntity(farm, workspace,"Finca A", metadata=meta))
        db.farmDao().upsert(FarmEntity(otherFarm, workspace,"Finca B", metadata=meta))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun createCloseAndRestartKeepEffectiveDateSeparateAndDoNotCreateOutbox() = runBlocking {
        val repo = repository()
        val initial = repo.observe(farm).first().value()
        assertTrue(initial.cycles.isEmpty())
        assertEquals(0,provisioningCalls)
        val operation = UUID.randomUUID()
        val cycle = repo.create(initial.scope,"2026/27",date("2026-02-01"),operation).value()
        assertEquals(cycle, repo.create(initial.scope,"2026/27",date("2026-02-01"),operation).value())
        val preview = repo.previewClose(cycle,date("2027-01-25"),true).value()
        assertTrue(preview.blockers.isEmpty())
        repo.close(preview,UUID.randomUUID()).value()
        val closed = repo.observe(farm).first().value().cycles.single()
        assertEquals(date("2027-01-25"),closed.effectiveEndDate)
        assertEquals(clock.nowInstant(),closed.closedAt)
        db.close(); db = MaginaOlivoDatabase.create(context,name)
        assertEquals(closed,repository().observe(farm).first().value().cycles.single())
        val state = repository().observe(farm).first().value()
        repository().create(state.scope,"2027/28",date("2027-01-26"),UUID.randomUUID()).value()
        assertEquals(2,repository().observe(farm).first().value().cycles.size)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { c -> c.moveToFirst(); assertEquals(0,c.getInt(0)) }
    }

    @Test fun farmDatesAreIndependentAndStaleCommandsConflictWithoutChangingCycles() = runBlocking {
        val repo=repository()
        val initial=repo.observe(farm).first().value()
        repo.create(initial.scope,"A",date("2026-02-01"),UUID.randomUUID()).value()
        assertTrue(repo.create(initial.scope,"stale",date("2026-02-02"),UUID.randomUUID()) is AppResult.Failure)
        val current=repo.observe(farm).first().value()
        assertTrue(repo.create(current.scope,"overlap",date("2026-03-01"),UUID.randomUUID()) is AppResult.Failure)
        val b=repo.observe(otherFarm).first().value()
        repo.create(b.scope,"B",date("2026-03-01"),UUID.randomUUID()).value()
        assertEquals(1,repo.observe(farm).first().value().cycles.size)
        assertEquals(1,repo.observe(otherFarm).first().value().cycles.size)
    }

    @Test fun logoutAndAccountSwitchInvalidateThePreviouslyCapturedCommand() = runBlocking {
        val repo=repository()
        val before=repo.observe(farm).first().value()
        user=UUID.randomUUID()
        assertTrue(repo.observe(farm).first() is AppResult.Failure)
        assertTrue(repo.create(before.scope,"foreign",date("2026-01-01"),UUID.randomUUID()) is AppResult.Failure)
        user=owner; selected.value=null
        assertTrue(repo.create(before.scope,"logout",date("2026-01-01"),UUID.randomUUID()) is AppResult.Failure)
        selected.value=workspace
        assertTrue(repo.observe(farm).first().value().cycles.isEmpty())
    }

    @Test fun explicitHistoricAssociationChangesOnlySidecarAndIsNeverInferredFromMonth() = runBlocking {
        val meta=LocalMetadata(clock.nowInstant(),clock.nowInstant(),version=9)
        val activity=ActivityEntity(UUID.randomUUID(),workspace,farmId=farm,activityDate=date("2026-02-15"),
            type="PRUNING",status=ActivityStatus.COMPLETED,description="Histórica",metadata=meta)
        db.activityDao().upsert(activity)
        val repo=repository()
        val before=repo.observe(farm).first().value()
        val cycle=repo.create(before.scope,"Etiqueta libre",date("2026-02-01"),UUID.randomUUID()).value()
        val state=repo.observe(farm).first().value()
        assertNull(state.records.single().cycleId)
        repo.assign(state.scope,cycle,CycleRecordKind.ACTIVITY,activity.id,"Confirmo esta poda histórica",UUID.randomUUID()).value()
        assertEquals(activity,db.activityDao().findById(activity.id))
        assertEquals(cycle,repo.observe(farm).first().value().records.single().cycleId)
    }

    @Test fun lastDeliverySuggestsButDoesNotImposeEffectiveClosureAndPreviewCanBecomeStale() = runBlocking {
        val repo=repository()
        val cycle=repo.create(repo.observe(farm).first().value().scope,"2026/27",date("2026-02-01"),UUID.randomUUID()).value()
        val meta=LocalMetadata(clock.nowInstant(),clock.nowInstant())
        val campaign=CampaignEntity(UUID.randomUUID(),workspace,farm,"Recogida",date("2026-12-01"),date("2027-01-15"),CampaignStatus.CLOSED,metadata=meta)
        db.campaignDao().upsert(campaign)
        val delivery=DeliveryEntity(UUID.randomUUID(),workspace,farm,campaign.id,date("2027-01-15"),
            destinationName="Cooperativa QA",netGrams=45000000,source="MANUAL",metadata=meta)
        db.deliveryDao().upsert(delivery)
        repo.assign(repo.observe(farm).first().value().scope,cycle,CycleRecordKind.RECOLLECTION,campaign.id,
            "Confirmo la recolección",UUID.randomUUID()).value()
        val preview=repo.previewClose(cycle,date("2027-01-20"),false).value()
        assertEquals(date("2027-01-15"),preview.lastDeliveryDate)
        assertTrue(preview.blockers.isEmpty())
        db.campaignDao().upsert(campaign.copy(metadata=meta.copy(version=2)))
        assertTrue(repo.close(preview,UUID.randomUUID()) is AppResult.Failure)
        assertNull(repo.observe(farm).first().value().cycles.single().effectiveEndDate)
        repo.close(repo.previewClose(cycle,date("2027-01-20"),false).value(),UUID.randomUUID()).value()
        assertEquals(date("2027-01-20"),repo.observe(farm).first().value().cycles.single().effectiveEndDate)
        assertEquals(delivery,db.deliveryDao().findById(delivery.id))
    }

    @Test fun newWorkUsesConfirmedCycleUuidAndRejectsTheClosedHistoricDestinationWithoutWriting() = runBlocking {
        val repo=repository()
        val previous=repo.create(repo.observe(farm).first().value().scope,"2026/27",date("2026-02-01"),UUID.randomUUID()).value()
        repo.close(repo.previewClose(previous,date("2027-01-25"),true).value(),UUID.randomUUID()).value()
        val next=repo.create(repo.observe(farm).first().value().scope,"2027/28",date("2027-01-26"),UUID.randomUUID()).value()
        val parcel=UUID.randomUUID()
        val meta=LocalMetadata(clock.nowInstant(),clock.nowInstant())
        db.parcelDao().upsert(ParcelEntity(parcel,workspace,"QA parcela",source="MANUAL",metadata=meta))
        db.parcelDao().upsertMembership(FarmParcelMembershipEntity(UUID.randomUUID(),workspace,farm,parcel,
            Instant.parse("2020-01-01T00:00:00Z"),metadata=meta))
        val activities=OfflineFirstActivityRepository(db,clock,UuidGenerator(),DefaultAppDispatchers(),workspaceRepository=scope)
        val command=NewActivity(farm,null,ActivityType.PRUNING,date("2027-01-26"),"Poda del siguiente ciclo",setOf(parcel),
            completeImmediately=true,costMinor=500,agriculturalCycleId=next)
        val id=activities.create(command).value()
        assertTrue(activities.create(command.copy(agriculturalCycleId=previous)) is AppResult.Failure)
        assertTrue(activities.create(command.copy(agriculturalCycleId=null)) is AppResult.Failure)
        val state=repo.observe(farm).first().value()
        assertEquals(next,state.records.single { it.id==id }.cycleId)
        assertEquals(1,state.records.count { it.kind=="ACTIVITY" })
        assertEquals(1,state.records.count { it.kind=="EXPENSE" })
        assertEquals(next,state.records.single { it.kind=="EXPENSE" }.cycleId)
        assertEquals(date("2027-01-25"),state.cycles.single { it.id==previous }.effectiveEndDate)
    }

    @Test fun newRecollectionAndGeneralExpenseKeepExplicitLinksButUnassignedExpensesStayUnassigned() = runBlocking {
        val cycles=repository()
        val cycle=cycles.create(cycles.observe(farm).first().value().scope,"Flexible",date("2026-02-01"),UUID.randomUUID()).value()
        val campaigns=OfflineFirstCampaignRepository(db,clock,UuidGenerator(),DefaultAppDispatchers(),scope)
        assertTrue(campaigns.create(NewCampaign(farm,"Sin selección",date("2026-12-01"))) is AppResult.Failure)
        val campaign=campaigns.create(NewCampaign(farm,"Recogida QA",date("2026-12-01"),agriculturalCycleId=cycle)).value()
        val expenses=OfflineFirstExpenseRepository(db,scope,clock,UuidGenerator(),DefaultAppDispatchers())
        val expense=expenses.create(ExpenseDraft(date("2026-03-15"),"Abonado QA",ExpenseCategory.OTHER,1234,
            farmId=farm,agriculturalCycleId=cycle)).value()
        val unassigned=expenses.create(ExpenseDraft(date("2026-03-15"),"Compartido sin finca",ExpenseCategory.OTHER,567)).value()
        assertNull(db.expenseDao().findById(unassigned)!!.farmId)
        assertTrue(expenses.create(ExpenseDraft(date("2026-03-15"),"Otra finca",ExpenseCategory.OTHER,100,
            farmId=otherFarm,agriculturalCycleId=cycle)) is AppResult.Failure)
        val snapshot=cycles.observe(farm).first().value()
        assertEquals(cycle,snapshot.records.single { it.id==campaign }.cycleId)
        assertEquals(cycle,snapshot.records.single { it.id==expense }.cycleId)
        assertEquals(1234L,db.expenseDao().findById(expense)!!.amountMinor)
    }

    @Test fun closedAgriculturalCycleCannotBeBypassedByReopeningItsRecollection() = runBlocking {
        val repo=repository()
        val cycle=repo.create(repo.observe(farm).first().value().scope,"Cerrada",date("2026-02-01"),UUID.randomUUID()).value()
        val meta=LocalMetadata(clock.nowInstant(),clock.nowInstant())
        val campaign=CampaignEntity(UUID.randomUUID(),workspace,farm,"Recogida",date("2026-12-01"),date("2027-01-15"),CampaignStatus.CLOSED,metadata=meta)
        db.campaignDao().upsert(campaign)
        repo.assign(repo.observe(farm).first().value().scope,cycle,CycleRecordKind.RECOLLECTION,campaign.id,"Confirmo",UUID.randomUUID()).value()
        repo.close(repo.previewClose(cycle,date("2027-01-20"),false).value(),UUID.randomUUID()).value()
        val campaigns=OfflineFirstCampaignRepository(db,clock,UuidGenerator(),DefaultAppDispatchers(),scope)
        assertTrue(campaigns.reopen(campaign.id) is AppResult.Failure)
        assertEquals(campaign,db.campaignDao().findById(campaign.id))
    }

    @Test fun existingUnassignedRecollectionCannotSilentlyReceiveNewWeighingsAfterCyclesAreEnabled() = runBlocking {
        val meta=LocalMetadata(clock.nowInstant(),clock.nowInstant())
        val campaign=CampaignEntity(UUID.randomUUID(),workspace,farm,"Anterior sin vínculo",date("2026-12-01"),
            status=CampaignStatus.ACTIVE,metadata=meta)
        db.campaignDao().upsert(campaign)
        val parcel=UUID.randomUUID()
        db.parcelDao().upsert(ParcelEntity(parcel,workspace,"QA",source="MANUAL",metadata=meta))
        db.campaignDao().upsertSnapshots(listOf(CampaignParcelSnapshotEntity(UUID.randomUUID(),workspace,
            campaign.id,parcel,farm,"Finca A","QA",metadata=meta)))
        val repo=repository()
        val deliveries=OfflineFirstDeliveryRepository(db,clock,UuidGenerator(),DefaultAppDispatchers(),scope)
        val draft=DeliveryDraft(farm,date("2027-01-15"),destinationOrganizationId=null,netGrams=1000,
            destinationName="QA",shares=listOf(com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput(parcel,1000)),
            origin=com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin.TREE)
        deliveries.create(draft.copy(deliveryDate=date("2027-01-14"))).value()
        repo.create(repo.observe(farm).first().value().scope,"Flexible",date("2026-02-01"),UUID.randomUUID()).value()
        assertTrue(deliveries.create(draft) is AppResult.Failure)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM deliveries").use {c->c.moveToFirst();assertEquals(1,c.getInt(0))}
    }

    @Test fun openAgriculturalCyclePreventsArchivingItsFarm() = runBlocking {
        val repo=repository()
        repo.create(repo.observe(farm).first().value().scope,"Abierta",date("2026-02-01"),UUID.randomUUID()).value()
        val farms=com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository(db,clock,UuidGenerator(),DefaultAppDispatchers(),scope)
        val before=db.farmDao().findById(farm)
        assertTrue(farms.archive(farm) is AppResult.Failure)
        assertEquals(before,db.farmDao().findById(farm))
    }

    @Test fun preparationDateCannotMoveBeforeItsExplicitCycle() = runBlocking {
        val repo=repository()
        val cycle=repo.create(repo.observe(farm).first().value().scope,"Abierta",date("2026-02-01"),UUID.randomUUID()).value()
        val campaigns=OfflineFirstCampaignRepository(db,clock,UuidGenerator(),DefaultAppDispatchers(),workspaceRepository=scope)
        val id=campaigns.create(NewCampaign(farm,"QA",date("2026-03-01"),emptySet(),agriculturalCycleId=cycle)).value()
        val before=db.campaignDao().findById(id)
        assertTrue(campaigns.updatePreparation(id,com.isivoltpro.maginaolivo.domain.campaign.CampaignPreparationChanges(
            "QA",date("2026-01-31"),emptySet())) is AppResult.Failure)
        assertEquals(before,db.campaignDao().findById(id))
    }

    @Test fun aClosedCycleDeclaredWithoutRecollectionCannotAcquireOneLater() = runBlocking {
        val repo=repository()
        val cycle=repo.create(repo.observe(farm).first().value().scope,"Sin recolección",date("2026-02-01"),UUID.randomUUID()).value()
        repo.close(repo.previewClose(cycle,date("2027-01-25"),true).value(),UUID.randomUUID()).value()
        val meta=LocalMetadata(clock.nowInstant(),clock.nowInstant())
        val campaign=CampaignEntity(UUID.randomUUID(),workspace,farm,"Histórica",date("2026-12-01"),
            endDate=date("2027-01-15"),status=CampaignStatus.CLOSED,metadata=meta)
        db.campaignDao().upsert(campaign)
        assertTrue(repo.assign(repo.observe(farm).first().value().scope,cycle,CycleRecordKind.RECOLLECTION,
            campaign.id,"Revisión",UUID.randomUUID()) is AppResult.Failure)
        assertNull(repo.observe(farm).first().value().records.single {it.id==campaign.id}.cycleId)
    }

    private fun operationCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM agricultural_cycle_operations").use { c -> c.moveToFirst(); c.getInt(0) }

    @Test fun expenseLinkedToAClosedCycleCannotBeMovedToAnotherCycle() = runBlocking {
        val repo=repository()
        val closed=repo.create(repo.observe(farm).first().value().scope,"2026/27",date("2026-02-01"),UUID.randomUUID()).value()
        val expenses=OfflineFirstExpenseRepository(db,scope,clock,UuidGenerator(),DefaultAppDispatchers())
        val expense=expenses.create(ExpenseDraft(date("2026-03-15"),"Abonado cerrado",ExpenseCategory.OTHER,1234,
            farmId=farm,agriculturalCycleId=closed)).value()
        repo.close(repo.previewClose(closed,date("2027-01-20"),true).value(),UUID.randomUUID()).value()
        val next=repo.create(repo.observe(farm).first().value().scope,"2027/28",date("2027-01-21"),UUID.randomUUID()).value()
        val operations=operationCount()
        val before=db.expenseDao().findById(expense)

        val result=repo.assign(repo.observe(farm).first().value().scope,next,CycleRecordKind.EXPENSE,expense,
            "Mover gasto",UUID.randomUUID())

        assertTrue(result.toString(),result is AppResult.Failure)
        assertEquals("source_cycle_closed",((result as AppResult.Failure).error as com.isivoltpro.maginaolivo.core.common.AppError.Validation).code)
        assertEquals(closed,repo.observe(farm).first().value().records.single { it.id==expense }.cycleId)
        assertEquals(before,db.expenseDao().findById(expense))
        assertEquals(operations,operationCount())
    }

    @Test fun unassignedExpenseCannotBeAssignedToAClosedCycle() = runBlocking {
        val repo=repository()
        val closed=repo.create(repo.observe(farm).first().value().scope,"2026/27",date("2026-02-01"),UUID.randomUUID()).value()
        repo.close(repo.previewClose(closed,date("2027-01-20"),true).value(),UUID.randomUUID()).value()
        val meta=LocalMetadata(clock.nowInstant(),clock.nowInstant())
        val expense=ExpenseEntity(UUID.randomUUID(),workspace,farmId=farm,expenseDate=date("2026-06-01"),
            concept="Factura tardía",category="OTHER",amountMinor=500,currency="EUR",status="POSTED",origin="MANUAL",metadata=meta)
        db.expenseDao().upsert(expense)
        val operations=operationCount()

        val result=repo.assign(repo.observe(farm).first().value().scope,closed,CycleRecordKind.EXPENSE,expense.id,
            "Asignar factura tardía",UUID.randomUUID())

        assertTrue(result.toString(),result is AppResult.Failure)
        assertEquals("cycle_closed",((result as AppResult.Failure).error as com.isivoltpro.maginaolivo.core.common.AppError.Validation).code)
        assertNull(repo.observe(farm).first().value().records.single { it.id==expense.id }.cycleId)
        assertEquals(expense,db.expenseDao().findById(expense.id))
        assertEquals(operations,operationCount())
    }

    @Test fun unassignedExpenseCanStillBeAssignedToTheOpenCycleWithATrace() = runBlocking {
        val repo=repository()
        val open=repo.create(repo.observe(farm).first().value().scope,"2026/27",date("2026-02-01"),UUID.randomUUID()).value()
        val meta=LocalMetadata(clock.nowInstant(),clock.nowInstant())
        val expense=ExpenseEntity(UUID.randomUUID(),workspace,farmId=farm,expenseDate=date("2026-06-01"),
            concept="Factura",category="OTHER",amountMinor=500,currency="EUR",status="POSTED",origin="MANUAL",metadata=meta)
        db.expenseDao().upsert(expense)
        val operations=operationCount()

        repo.assign(repo.observe(farm).first().value().scope,open,CycleRecordKind.EXPENSE,expense.id,
            "Confirmo la factura",UUID.randomUUID()).value()

        assertEquals(open,repo.observe(farm).first().value().records.single { it.id==expense.id }.cycleId)
        assertEquals(expense,db.expenseDao().findById(expense.id))
        assertEquals(operations+1,operationCount())
    }

    @Test fun assignedExpenseCannotChangeFarmAndLoseItsCycleContext() = runBlocking {
        val repo=repository()
        val cycle=repo.create(repo.observe(farm).first().value().scope,"Abierta",date("2026-02-01"),UUID.randomUUID()).value()
        val expenses=OfflineFirstExpenseRepository(db,scope,clock,UuidGenerator(),DefaultAppDispatchers())
        val draft=ExpenseDraft(date("2026-03-01"),"QA",ExpenseCategory.OTHER,1000,farmId=farm,agriculturalCycleId=cycle)
        val id=expenses.create(draft).value()
        val before=db.expenseDao().findById(id)
        assertTrue(expenses.update(id,draft.copy(farmId=otherFarm,agriculturalCycleId=null)) is AppResult.Failure)
        assertEquals(before,db.expenseDao().findById(id))
    }

    @Test fun editingAnAssignedWorkDateCannotSilentlyMoveItOutsideItsCycle() = runBlocking {
        val repo=repository()
        val cycle=repo.create(repo.observe(farm).first().value().scope,"Flexible",date("2026-02-01"),UUID.randomUUID()).value()
        val activities=OfflineFirstActivityRepository(db,clock,UuidGenerator(),DefaultAppDispatchers(),workspaceRepository=scope)
        val id=activities.create(NewActivity(farm,null,ActivityType.OTHER,date("2026-02-15"),"Borrador QA",
            asDraft=true,agriculturalCycleId=cycle)).value()
        val before=db.activityDao().findById(id)
        assertTrue(activities.update(id,ActivityChanges(type=ActivityType.OTHER,activityDate=date("2026-01-31"),
            description="Fecha fuera del ciclo",parcelIds=emptySet())) is AppResult.Failure)
        assertEquals(before,db.activityDao().findById(id))
        assertEquals(cycle,repo.observe(farm).first().value().records.single {it.id==id}.cycleId)
    }
}
