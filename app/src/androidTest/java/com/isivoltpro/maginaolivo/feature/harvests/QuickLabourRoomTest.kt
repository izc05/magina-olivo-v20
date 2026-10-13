package com.isivoltpro.maginaolivo.feature.harvests

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.*
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.*
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.repository.*
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.labour.*
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.lang.reflect.Proxy
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Only a unique, disposable fixture database; never the application's operational database. */
class QuickLabourRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "quick-labour-${UUID.randomUUID()}.db"
    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val parcel = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 13)
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private lateinit var db: MaginaOlivoDatabase
    private lateinit var persistence: LocalPersistence
    private lateinit var labour: OfflineFirstLabourRepository
    private lateinit var harvests: OfflineFirstHarvestRepository
    private lateinit var expenses: OfflineFirstExpenseRepository
    private val clock = object : AppClock {
        override fun nowInstant() = now
        override fun today(zoneId: ZoneId) = date
    }
    private val ids = object : IdGenerator { override fun newId() = UUID.randomUUID() }
    private val dispatchers = object : AppDispatchers {
        override val io = Dispatchers.IO
        override val default = Dispatchers.Default
        override val main = Dispatchers.Main
    }
    private val workspaces = object : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspace)
    }
    private fun open() {
        db = MaginaOlivoDatabase.create(context, name)
        harvests = OfflineFirstHarvestRepository(db, clock, ids, dispatchers) { ZoneOffset.UTC }
        labour = OfflineFirstLabourRepository(db, workspaces, clock, ids, dispatchers)
        expenses = OfflineFirstExpenseRepository(db, workspaces, clock, ids, dispatchers)
        persistence = LocalPersistence(db, unused(), unused(), unused(), unused(), unused(), unused(), unused(),
            expenses, unused(), harvests, unused(), unused(), labour, unused(), workspaces)
    }
    @Before fun before() = runBlocking {
        open()
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(WorkspaceEntity(workspace, "Fixture", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farm, workspace, "Fixture", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(parcel, workspace, "Parcela", source = "MANUAL", metadata = meta))
        db.campaignDao().upsert(CampaignEntity(campaign, workspace, farm, "2026/27", date.minusDays(2), null, CampaignStatus.HARVEST, metadata = meta))
        db.campaignDao().upsertSnapshots(listOf(CampaignParcelSnapshotEntity(UUID.randomUUID(), workspace, campaign, parcel, farm, "Fixture", "Parcela", metadata = meta)))
    }
    @After fun after() { db.close(); context.deleteDatabase(name) }
    private suspend fun record(draft: CrewDraft) = quickLabourTransaction(persistence) {
        saveCampaignQuickLabour(farm, campaign, date, draft, harvests.observeContexts().first(),
            harvests::openJornada, { harvests.observe(it).first() }, labour::recordCrew)
    }
    @Test fun rejectedWorkerRollsBackDayAndOutboxAndKeepsGeneralCost() = runBlocking {
        val general = ok(expenses.create(ExpenseDraft(date, "Reparación", ExpenseCategory.LABOR, 6500, farmId = farm)))
        val before = outboxCount()
        val result = record(CrewDraft(UUID.randomUUID(), listOf(UUID.randomUUID()), LabourUnit.FULL_DAY,
            appliedRate = LabourRateSnapshot(7000, "EUR", date, LabourRateBasis.DAY)))
        assertTrue(result is AppResult.Failure)
        assertTrue(harvests.observeAll().first().isEmpty())
        assertEquals(before, outboxCount())
        assertEquals(listOf(general), expenses.observeAll().first().map { it.id })
    }
    @Test fun sameDateBothScopesSurviveRestartWithoutDuplicatingCampaignMoney() = runBlocking {
        val worker = ok(labour.addWorker("Persona sintética"))
        val general = ok(expenses.create(ExpenseDraft(date, "Persona sintética · riego", ExpenseCategory.LABOR, 6500, farmId = farm)))
        val draft = CrewDraft(UUID.randomUUID(), listOf(worker), LabourUnit.HOURS, 180,
            LabourRateSnapshot(1200, "EUR", date, LabourRateBasis.HOUR))
        assertEquals(1, ok(record(draft)))
        assertTrue(record(draft) is AppResult.Failure)
        db.close(); open()
        val day = harvests.observeAll().first().single()
        assertEquals(campaign, day.campaignId); assertEquals(date, day.harvestDate)
        val entries = labour.observeForHarvest(day.id).first()
        assertEquals(1, entries.size); assertEquals(worker, entries.single().workerId); assertEquals(180, entries.single().minutes)
        val rows = expenses.observeAll().first()
        val outside = rows.single { it.id == general }
        assertNull(outside.campaignId); assertNull(outside.harvestId); assertEquals(6500L, outside.amountMinor)
        val inside = rows.filter { it.campaignId == campaign }
        assertEquals(1, inside.size); assertEquals(ExpenseOrigin.DAY_LABOUR, inside.single().origin)
        assertEquals(3600L, ExpenseSummary.of(inside, "EUR").totalMinor)
        assertEquals(10100L, ExpenseSummary.of(rows, "EUR").totalMinor)
        val deliveries = OfflineFirstDeliveryRepository(db, clock, ids, dispatchers) { ZoneOffset.UTC }
        ok(deliveries.create(DeliveryDraft(farmId = farm, deliveryDate = date,
            destinationOrganizationId = null, destinationName = "Cooperativa sintética", netGrams = 1_000_000,
            shares = listOf(DeliveryShareInput(parcel, null)), origin = PesadaOrigin.TREE)))
        val ledger = RecollectionLedger.of(campaign, expenses.observeAll().first(), deliveries.observeAll().first()).single()
        assertEquals(36L, ledger.costPerKgMilli) // 36 EUR / 1000 kg; the general 65 EUR never enter.
    }
    private fun outboxCount() = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); it.getInt(0) }
    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success: ${result.error}")
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Unrelated repository called: ${method.name}")
    } as T
}
