package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.repository.LocalAgriculturalCycleRepository
import com.isivoltpro.maginaolivo.data.repository.LocalEconomyRepository
import com.isivoltpro.maginaolivo.domain.cycle.CycleScope
import com.isivoltpro.maginaolivo.domain.economy.*
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
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

/** CR-015 P5: liquidations, advances, payments and margin persisted locally under the P3 owner rule. */
@RunWith(AndroidJUnit4::class)
class EconomyRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "cr015-p5-economy-test.db"
    private lateinit var db: MaginaOlivoDatabase
    private val workspace = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val selected = MutableStateFlow<UUID?>(workspace)
    private var user: UUID? = owner
    private val day = LocalDate.parse("2027-03-15")
    private val clock = object : AppClock {
        override fun nowInstant() = Instant.parse("2027-03-15T12:00:00Z")
        override fun today(zoneId: ZoneId) = LocalDate.of(2027, 3, 15)
    }
    private val scope = object : WorkspaceRepository {
        override suspend fun ensureLocalWorkspace(): AppResult<UUID> = activeWorkspace()
        override suspend fun activeWorkspace(): AppResult<UUID> = selected.value?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.Permission("signed_out"))
        override fun currentOwnerId() = user
        override fun observeLocalWorkspace() = selected
    }
    private fun repo() = LocalEconomyRepository(db, scope, clock)
    private fun <T> AppResult<T>.ok(): T { assertTrue(toString(), this is AppResult.Success); return (this as AppResult.Success).value }
    private fun AppResult<*>.code() = ((this as AppResult.Failure).error as? AppError.Validation)?.code
    private fun doc(kind: SettlementKind, gross: Long, withholding: Long = 0, fees: Long = 0, cycle: UUID? = null,
        replaces: UUID? = null) = Settlement(UUID.randomUUID(), workspace, farm, cycle, kind, " Cooperativa Mágina ", day, "EUR",
        gross, withholding, fees, gross - withholding - fees, replacesId = replaces)

    @Before fun setup() = runBlocking {
        context.deleteDatabase(name)
        db = MaginaOlivoDatabase.create(context, name)
        val meta = LocalMetadata(clock.nowInstant(), clock.nowInstant())
        db.workspaceDao().upsert(WorkspaceEntity(workspace, "QA", owner, "ES", "Europe/Madrid", "es-ES", "EUR", meta))
        db.farmDao().upsert(FarmEntity(farm, workspace, "Finca A", metadata = meta))
        db.openHelper.writableDatabase.execSQL("INSERT INTO expenses(id,workspace_id,farm_id,expense_date,concept,category,amount_minor,currency,status,origin,created_at,updated_at,version,sync_status) VALUES ('${UUID.randomUUID()}','$workspace','$farm','2027-01-10','Abono','OTHER',30000,'EUR','POSTED','MANUAL',1,1,1,'LOCAL_ONLY')")
        db.openHelper.writableDatabase.execSQL("INSERT INTO expenses(id,workspace_id,farm_id,expense_date,concept,category,amount_minor,currency,status,origin,created_at,updated_at,version,sync_status) VALUES ('${UUID.randomUUID()}','$workspace','$farm','2027-01-11','Borrador','OTHER',99999,'EUR','DRAFT','MANUAL',1,1,1,'LOCAL_ONLY')")
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun fullSeasonFlowSurvivesRestartWithoutDoubleCountingAndWithoutOutbox() = runBlocking {
        val advance = doc(SettlementKind.ADVANCE, 20_000)
        val provisional = doc(SettlementKind.PROVISIONAL, 50_000, 1_000, 2_000)
        repo().record(advance).ok(); repo().record(provisional).ok()
        repo().applyAdvance(AdvanceApplication(UUID.randomUUID(), advance.id, provisional.id, 20_000)).ok()
        repo().recordPayment(SettlementPayment(UUID.randomUUID(), advance.id, day, 20_000)).ok()
        repo().recordPayment(SettlementPayment(UUID.randomUUID(), provisional.id, day, 10_000)).ok()
        val final = doc(SettlementKind.FINAL, 60_000, 1_200, 2_400, replaces = provisional.id)
        repo().record(final).ok()
        assertEquals(true, repo().record(final) is AppResult.Success)   // same id and content: no-op

        db.close(); db = MaginaOlivoDatabase.create(context, name)
        val b = repo().observe(farm).first().ok().balances.single()
        assertEquals(60_000, b.recognisedGrossMinor)
        assertEquals(1_200, b.withholdingMinor)
        assertEquals(30_000, b.costsMinor)                       // DRAFT expense excluded
        assertEquals(60_000 - 2_400 - 30_000L, b.marginMinor)
        assertEquals(56_400 - 20_000 - 10_000L, b.pendingMinor)
        assertEquals(30_000, b.collectedMinor)
        assertFalse(b.provisional)
        assertEquals("Cooperativa Mágina", db.economyDao().settlement(final.id)?.cooperative)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    @Test fun invalidDocumentsAndExcessesAreRejectedBeforeAnyWrite() = runBlocking {
        val provisional = doc(SettlementKind.PROVISIONAL, 10_000)
        repo().record(provisional).ok()
        assertEquals("net_mismatch", repo().record(doc(SettlementKind.FINAL, 10_000).copy(netMinor = 1)).code())
        assertEquals("payment_exceeds_pending", repo().recordPayment(SettlementPayment(UUID.randomUUID(), provisional.id, day, 10_001)).code())
        repo().recordPayment(SettlementPayment(UUID.randomUUID(), provisional.id, day, 8_000)).ok()
        assertEquals("replacement_below_collected", repo().record(doc(SettlementKind.FINAL, 7_000, replaces = provisional.id)).code())
        assertEquals("has_movements", repo().cancel(provisional.id).code())
        assertTrue(repo().record(provisional.copy(grossMinor = 11_000, netMinor = 11_000)) is AppResult.Failure)
        assertEquals(1, db.economyDao().settlements(workspace).size)
    }

    @Test fun cancelKeepsTheDocumentAndRestoresTheReplacedProvisional() = runBlocking {
        val provisional = doc(SettlementKind.PROVISIONAL, 10_000)
        val final = doc(SettlementKind.FINAL, 12_000, replaces = provisional.id)
        repo().record(provisional).ok(); repo().record(final).ok()
        repo().cancel(final.id).ok()
        val b = repo().observe(farm).first().ok().balances.single()
        assertEquals(10_000, b.recognisedGrossMinor)
        assertTrue(b.provisional)
        assertEquals("CANCELLED", db.economyDao().settlement(final.id)?.status)
    }

    @Test fun cycleScopeUsesOnlyItsOwnSettlementsAndLinkedExpenses() = runBlocking {
        val cycles = LocalAgriculturalCycleRepository(db, scope, clock)
        val cycleId = cycles.create(CycleScope(workspace, farm, owner, 0), "2026/27", LocalDate.parse("2026-11-01"), UUID.randomUUID()).ok()
        repo().record(doc(SettlementKind.FINAL, 40_000, cycle = cycleId)).ok()
        repo().record(doc(SettlementKind.FINAL, 5_000)).ok()
        val inCycle = repo().observe(farm, cycleId).first().ok().balances.single()
        assertEquals(40_000, inCycle.recognisedGrossMinor)
        assertEquals(0, inCycle.costsMinor)                       // the expense is not linked to the cycle
        assertEquals(45_000, repo().observe(farm).first().ok().balances.single().recognisedGrossMinor)
    }

    @Test fun anotherAccountCannotReadOrWrite() = runBlocking {
        user = UUID.randomUUID()
        assertTrue(repo().observe(farm).first() is AppResult.Failure)
        assertTrue(repo().record(doc(SettlementKind.FINAL, 1_000)) is AppResult.Failure)
        assertEquals(0, db.economyDao().settlements(workspace).size)
    }
}
