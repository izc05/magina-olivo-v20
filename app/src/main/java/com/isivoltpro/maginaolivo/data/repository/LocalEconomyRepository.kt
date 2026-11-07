package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.CooperativeSettlementEntity
import com.isivoltpro.maginaolivo.data.local.entity.SettlementAdvanceApplicationEntity
import com.isivoltpro.maginaolivo.data.local.entity.SettlementPaymentEntity
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.domain.economy.*
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/** CR-015 P5 local economy. Owner of the active workspace only (P3 rule). Never queues sync. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalEconomyRepository(
    private val db: MaginaOlivoDatabase,
    private val workspaces: WorkspaceRepository,
    private val clock: AppClock,
    private val account: com.isivoltpro.maginaolivo.feature.account.AccountRepository? = null,
) : EconomyRepository {
    private val dao get() = db.economyDao()

    override fun observe(farmId: UUID?, cycleId: UUID?): Flow<AppResult<EconomySnapshot>> =
        workspaces.observeLocalWorkspace().flatMapLatest { workspace ->
            if (workspace == null) flowOf(AppResult.Failure(AppError.Permission("economy_read")))
            else combine(dao.observeSettlements(workspace), dao.observeApplications(), dao.observePayments(),
                dao.observeExpenses(workspace)) { _, _, _, _ ->
                safe { db.withTransaction { snapshot(farmId, cycleId) } }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun record(input: Settlement): AppResult<Unit> = transaction {
        val settlement = input.copy(cooperative = input.cooperative.trim(),
            reference = input.reference?.trim()?.takeIf { it.isNotEmpty() })
        val workspace = authorize(settlement.farmId, settlement.cycleId, write = true)
        check(settlement.workspaceId == workspace, "workspaceId", "context_mismatch")
        dao.settlement(settlement.id)?.let { existing ->
            if (existing.domain() == settlement.copy(version = existing.version)) return@transaction
            reject(AppError.Conflict("settlement_id_reused"))
        }
        check(settlement.status == SettlementStatus.ACTIVE && settlement.version == 1L, "status", "invalid_status")
        EconomyRules.documentError(settlement)?.let { check(false, "settlement", it) }
        val ledger = ledger(workspace)
        EconomyRules.replacementError(settlement, ledger)?.let { check(false, "replacesId", it) }
        val now = clock.nowInstant()
        dao.insert(settlement.entity(now))
    }

    override suspend fun cancel(settlementId: UUID): AppResult<Unit> = transaction {
        val row = dao.settlement(settlementId) ?: reject(AppError.NotFound("settlement"))
        val workspace = authorize(row.farmId, row.cycleId, write = true)
        check(row.workspaceId == workspace, "workspaceId", "context_mismatch")
        if (row.status == SettlementStatus.CANCELLED.name) return@transaction
        val ledger = ledger(workspace)
        val moved = ledger.payments.any { it.active && it.settlementId == settlementId } ||
            ledger.applications.any { it.active && (it.advanceId == settlementId || it.settlementId == settlementId) }
        check(!moved, "settlementId", "has_movements")
        dao.update(row.copy(status = SettlementStatus.CANCELLED.name, version = Math.addExact(row.version, 1),
            updatedAt = clock.nowInstant()))
    }

    override suspend fun applyAdvance(application: AdvanceApplication): AppResult<Unit> = transaction {
        val advance = dao.settlement(application.advanceId) ?: reject(AppError.NotFound("advance"))
        val workspace = authorize(advance.farmId, advance.cycleId, write = true)
        dao.application(application.id)?.let { existing ->
            if (existing.advanceId == application.advanceId && existing.settlementId == application.settlementId &&
                existing.amountMinor == application.amountMinor && existing.active) return@transaction
            reject(AppError.Conflict("application_id_reused"))
        }
        check(application.active, "application", "invalid_status")
        EconomyRules.applicationError(application, ledger(workspace))?.let { check(false, "amount", it) }
        dao.insert(SettlementAdvanceApplicationEntity(application.id, application.advanceId, application.settlementId,
            application.amountMinor, true, clock.nowInstant()))
    }

    override suspend fun recordPayment(payment: SettlementPayment): AppResult<Unit> = transaction {
        val doc = dao.settlement(payment.settlementId) ?: reject(AppError.NotFound("settlement"))
        val workspace = authorize(doc.farmId, doc.cycleId, write = true)
        dao.payment(payment.id)?.let { existing ->
            if (existing.settlementId == payment.settlementId && existing.amountMinor == payment.amountMinor &&
                existing.paymentDate == payment.date && existing.active) return@transaction
            reject(AppError.Conflict("payment_id_reused"))
        }
        check(payment.active, "payment", "invalid_status")
        EconomyRules.paymentError(payment, ledger(workspace))?.let { check(false, "amount", it) }
        dao.insert(SettlementPaymentEntity(payment.id, payment.settlementId, payment.date, payment.amountMinor, true,
            clock.nowInstant()))
    }

    override suspend fun cancelPayment(paymentId: UUID): AppResult<Unit> = transaction {
        val row = dao.payment(paymentId) ?: reject(AppError.NotFound("payment"))
        val doc = dao.settlement(row.settlementId) ?: reject(AppError.NotFound("settlement"))
        authorize(doc.farmId, doc.cycleId, write = true)
        if (row.active) dao.update(row.copy(active = false))
    }

    private suspend fun snapshot(farmId: UUID?, cycleId: UUID?): EconomySnapshot {
        val workspace = authorize(farmId, cycleId)
        val scope = EconomyScope(workspace, farmId, cycleId)
        val costs = when {
            cycleId != null -> dao.postedCostsOf(db.agriculturalCycleDao().records(farmId!!, workspace)
                .filter { it.kind == "EXPENSE" && it.cycleId == cycleId }.map { it.id })
            else -> dao.postedCosts(workspace, farmId)
        }.associate { it.currency to it.amountMinor }
        val ledger = ledger(workspace)
        return EconomySnapshot(scope, ledger, EconomyRules.balances(scope, ledger, costs))
    }

    private suspend fun ledger(workspace: UUID) = EconomyLedger(
        dao.settlements(workspace).map { it.domain() },
        dao.applications(workspace).map { AdvanceApplication(it.id, it.advanceId, it.settlementId, it.amountMinor, it.active) },
        dao.payments(workspace).map { SettlementPayment(it.id, it.settlementId, it.paymentDate, it.amountMinor, it.active) },
    )

    /** Same identity rule as the P3 cycles: owner of the selected workspace; farm and cycle inside it. */
    private suspend fun authorize(farmId: UUID?, cycleId: UUID?, write: Boolean = false): UUID {
        val owner = workspaces.currentOwnerId()
        val workspace = when (val result = workspaces.activeWorkspace()) {
            is AppResult.Failure -> reject(result.error)
            is AppResult.Success -> result.value
        }
        if (owner != workspaces.currentOwnerId()) reject(AppError.Permission("identity_changed"))
        val ws = db.workspaceDao().findById(workspace) ?: reject(AppError.NotFound("workspace"))
        if (owner != null && owner != ws.ownerUserId || owner == null && ws.metadata.remoteVersion != null)
            reject(AppError.Permission("economy_owner_required"))
        if (farmId != null) {
            val farm = db.farmDao().findById(farmId) ?: reject(AppError.NotFound("farm"))
            check(farm.workspaceId == workspace && farm.metadata.deletedAt == null, "farmId", "context_mismatch")
            if (write) check(farm.status == FarmStatus.ACTIVE, "farmId", "archived_farm")
        }
        if (cycleId != null) {
            val cycle = db.agriculturalCycleDao().find(cycleId) ?: reject(AppError.NotFound("cycle"))
            check(cycle.farmId == farmId && cycle.workspaceId == workspace, "cycleId", "context_mismatch")
        }
        return workspace
    }

    private fun CooperativeSettlementEntity.domain() = Settlement(id, workspaceId, farmId, cycleId,
        SettlementKind.valueOf(kind), cooperative, documentDate, currency, grossMinor, withholdingMinor, feesMinor,
        netMinor, kilograms, reference, replacesId, SettlementStatus.valueOf(status), version)
    private fun Settlement.entity(now: java.time.Instant) = CooperativeSettlementEntity(id, workspaceId, farmId, cycleId,
        kind.name, cooperative.trim(), documentDate, currency, grossMinor, withholdingMinor, feesMinor, netMinor,
        kilograms, reference?.trim()?.takeIf { it.isNotEmpty() }, replacesId, status.name, version, now, now)

    private fun check(ok: Boolean, field: String, code: String) { if (!ok) reject(AppError.Validation(field, code)) }
    private class Rejected(val error: AppError) : RuntimeException()
    private fun reject(error: AppError): Nothing = throw Rejected(error)
    private suspend fun <T> safe(block: suspend () -> T): AppResult<T> = try { AppResult.Success(block()) }
        catch (e: CancellationException) { throw e }
        catch (e: Rejected) { AppResult.Failure(e.error) }
        catch (e: Exception) { AppResult.Failure(AppError.Storage("economy", e)) }
    private suspend fun <T> transaction(block: suspend () -> T): AppResult<T> = withContext(Dispatchers.IO) {
        safe {
            if (account == null) db.withTransaction { block() }
            else account.withLocalWriteIdentity { db.withTransaction { block() } }
        }
    }
}
