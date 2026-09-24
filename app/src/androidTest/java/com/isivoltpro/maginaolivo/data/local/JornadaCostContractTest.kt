package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.CampaignParcelSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstHarvestRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstExpenseRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.JornadaCost
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 19F — Recollection costs.
 *
 * Gate 19F: the Jornada cost equals the authoritative posted Expense rows exactly; no double
 * counting.
 */
@RunWith(AndroidJUnit4::class)
class JornadaCostContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val workspaceId = UUID.fromString("10000000-0000-0000-0000-0000000019f1")
    private val farmId = UUID.fromString("20000000-0000-0000-0000-0000000019f1")
    private val otherFarmId = UUID.fromString("20000000-0000-0000-0000-0000000019f2")
    private val campaignId = UUID.fromString("40000000-0000-0000-0000-0000000019f1")
    private val north = UUID.fromString("30000000-0000-0000-0000-0000000019f1")
    private val now = Instant.parse("2026-11-27T19:00:00Z")
    private val day = LocalDate.parse("2026-11-27")

    private lateinit var db: MaginaOlivoDatabase
    private lateinit var expenses: OfflineFirstExpenseRepository
    private lateinit var harvests: OfflineFirstHarvestRepository

    @Before
    fun before() = runBlocking {
        context.deleteDatabase(DB)
        open()
        seed()
        db.farmDao().upsert(FarmEntity(otherFarmId, workspaceId, "La Vega", metadata = LocalMetadata(now, now)))
    }

    @After
    fun after() {
        db.close()
        context.deleteDatabase(DB)
    }

    @Test
    fun theJornadaCostIsExactlyItsPostedLedgerRows() = runBlocking {
        val jornada = jornada()
        val diesel = ok(expenses.create(cost(jornada, JornadaExpenseKind.DIESEL, 4_550)))
        ok(expenses.create(cost(jornada, JornadaExpenseKind.RENTAL, 12_000)))
        // A cost of another day on the same Farm: not this Jornada's.
        ok(expenses.create(cost(null, JornadaExpenseKind.TRANSPORT, 3_000)))
        db.close()
        open()

        val linked = expenses.observeForHarvest(jornada).first()
        assertEquals(2, linked.size)
        assertTrue(linked.all { it.farmId == farmId && it.campaignId == campaignId && it.status == ExpenseStatus.POSTED })
        val cost = JornadaCost.of(linked)
        assertEquals(16_550L, cost.postedMinor)
        // Exactly the ledger rows: summed straight from the expenses table.
        assertEquals(16_550L, sumPostedFor(jornada))

        // Counted once in the Campaign: 165,50 € + 30 € of the other cost, never twice.
        val all = expenses.observeAll().first().filter { it.campaignId == campaignId }
        assertEquals(19_550L, all.sumOf { if (it.status == ExpenseStatus.POSTED) it.amountMinor else 0L })

        // Editing the cost through the ordinary Expense form keeps it on its Jornada.
        ok(expenses.update(diesel, cost(jornada, JornadaExpenseKind.DIESEL, 5_000)))
        assertEquals(17_000L, JornadaCost.of(expenses.observeForHarvest(jornada).first()).postedMinor)
        ok(expenses.delete(diesel))
        assertEquals(12_000L, JornadaCost.of(expenses.observeForHarvest(jornada).first()).postedMinor)
        assertEquals(12_000L, sumPostedFor(jornada))
    }

    @Test
    fun aCostCannotJoinAnotherFarmsJornadaAndARemovedJornadaKeepsItsMoneyInTheLedger() = runBlocking {
        val jornada = jornada()
        val result = expenses.create(cost(jornada, JornadaExpenseKind.DIESEL, 1_000).copy(farmId = otherFarmId))
        assertTrue((result as AppResult.Failure).error is AppError.Validation)
        assertValidation("harvestId", expenses.create(cost(UUID.randomUUID(), JornadaExpenseKind.DIESEL, 1_000)))

        val id = ok(expenses.create(cost(jornada, JornadaExpenseKind.LABOUR, 25_000)))
        ok(harvests.delete(jornada))
        val kept = expenses.observe(id).first()!!
        assertNull(kept.harvestId)
        assertEquals(25_000L, kept.amountMinor)
        assertEquals(ExpenseStatus.POSTED, kept.status)
    }

    private fun cost(harvestId: UUID?, kind: JornadaExpenseKind, amountMinor: Long) = ExpenseDraft(
        expenseDate = day,
        concept = kind.label,
        category = kind.category,
        amountMinor = amountMinor,
        farmId = farmId,
        harvestId = harvestId,
    )

    private fun sumPostedFor(harvestId: UUID): Long =
        db.openHelper.readableDatabase.query(
            "SELECT COALESCE(SUM(amount_minor), 0) FROM expenses WHERE harvest_id = ? AND status = 'POSTED' AND deleted_at IS NULL",
            arrayOf<Any>(harvestId.toString()),
        ).use { it.moveToFirst(); it.getLong(0) }

    private suspend fun jornada(): UUID =
        ok(harvests.create(HarvestDraft(farmId, day, 1_000_000, listOf(HarvestShareInput(north, 1_000_000)))))

    private fun open() {
        db = MaginaOlivoDatabase.create(context, DB)
        val clock = FixedClock(now)
        val workspaces = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspaceId)
        }
        expenses = OfflineFirstExpenseRepository(db, workspaces, clock, RandomIds, TestDispatchers)
        harvests = OfflineFirstHarvestRepository(db, clock, RandomIds, TestDispatchers) { ZoneOffset.UTC }
    }

    private suspend fun seed() {
        val meta = LocalMetadata(now, now)
        db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Olivar", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farmId, workspaceId, "El Cortijo", metadata = meta))
        db.parcelDao().upsert(ParcelEntity(north, workspaceId, "Norte", source = "MANUAL", metadata = meta))
        db.campaignDao().upsert(
            CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.parse("2026-10-01"), null, CampaignStatus.HARVEST, metadata = meta),
        )
        db.campaignDao().upsertSnapshots(
            listOf(CampaignParcelSnapshotEntity(UUID.randomUUID(), workspaceId, campaignId, north, farmId, "El Cortijo", "Norte", metadata = meta)),
        )
    }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table WHERE deleted_at IS NULL").use { it.moveToFirst(); it.getInt(0) }

    private fun <T> ok(result: AppResult<T>): T = when (result) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
    }

    private fun assertValidation(field: String, result: AppResult<*>) {
        val error = (result as? AppResult.Failure)?.error
        assertTrue("Expected validation on $field but was $result", error is AppError.Validation && error.field == field)
    }

    private data class FixedClock(val value: Instant) : AppClock {
        override fun nowInstant() = value
        override fun today(zoneId: ZoneId) = LocalDate.ofInstant(value, zoneId)
    }

    private object RandomIds : IdGenerator {
        override fun newId(): UUID = UUID.randomUUID()
    }

    private object TestDispatchers : AppDispatchers {
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val DB = "jornada-cost-contract-test.db"
    }
}
