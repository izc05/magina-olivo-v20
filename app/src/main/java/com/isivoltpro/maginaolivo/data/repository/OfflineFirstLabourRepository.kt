package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestLabourEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkerEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.labour.CountDraft
import com.isivoltpro.maginaolivo.domain.labour.CrewDraft
import com.isivoltpro.maginaolivo.domain.labour.LabourChange
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourRepository
import com.isivoltpro.maginaolivo.domain.labour.LabourRules
import com.isivoltpro.maginaolivo.domain.labour.LabourPayment
import com.isivoltpro.maginaolivo.domain.labour.LabourPaymentRules
import com.isivoltpro.maginaolivo.domain.labour.LabourPricing
import com.isivoltpro.maginaolivo.domain.labour.LabourSettlement
import com.isivoltpro.maginaolivo.data.local.entity.LabourPaymentEntity
import com.isivoltpro.maginaolivo.data.repository.DayCostLedger.Companion.toDomain
import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import com.isivoltpro.maginaolivo.domain.labour.Worker
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private class LabourInvalid(val field: String, val code: String) : RuntimeException("$field:$code")

private class LabourConflict(val code: String) : RuntimeException(code)

/**
 * Phase 19D — offline-first jornales. Each labour line and each person is its own record with
 * its own outbox intent; a Jornada of a closed Campaign is history and takes no new labour.
 * Snapshots and the single day Expense are saved atomically; payments only settle that debt.
 */
class OfflineFirstLabourRepository(
    private val database: MaginaOlivoDatabase,
    private val workspaceRepository: WorkspaceRepository,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : LabourRepository {
    private val costs = DayCostLedger(database, idGenerator)

    override fun observeWorkers(): Flow<List<Worker>> =
        flow {
            val active = when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> workspace.value
            }
            emitAll(
                database.labourDao().observeWorkers(active)
                    .map { rows -> rows.map { Worker(it.id, it.name) } },
            )
        }.flowOn(dispatchers.io)

    override suspend fun addWorker(name: String): AppResult<UUID> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AppResult.Failure(AppError.Validation("name", "required"))
        if (trimmed.length > 60) return AppResult.Failure(AppError.Validation("name", "too_long"))
        val workspaceId = when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
            is AppResult.Failure -> return workspace
            is AppResult.Success -> workspace.value
        }
        // #442: Worker.id is the identity; a name is only what is shown. Two people may share it,
        // so the same name never silently returns an existing person — the form asks first.
        return inTransaction("add_worker") {
            val now = clock.nowInstant()
            val id = idGenerator.newId()
            database.labourDao().upsertWorker(WorkerEntity(id, workspaceId, trimmed, LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.WORKER, id, OutboxOperation.CREATE, now)
            AppResult.Success(id)
        }
    }

    override fun observeForHarvest(harvestId: UUID): Flow<List<LabourEntry>> =
        database.labourDao().observeForHarvest(harvestId).map { rows -> rows.map { it.toLabourEntry() } }.flowOn(dispatchers.io)

    override fun observeForCampaign(campaignId: UUID): Flow<List<LabourEntry>> =
        database.labourDao().observeForCampaign(campaignId).map { rows -> rows.map { it.toLabourEntry() } }.flowOn(dispatchers.io)

    override suspend fun recordCrew(draft: CrewDraft): AppResult<Int> {
        LabourRules.validate(draft)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        return inTransaction("record_crew") {
            val harvest = runningJornada(draft.harvestId)
            val rates = harvest.farmId?.let { database.recollectionRatesDao().findForFarm(it)?.toDomain() } ?: RecollectionRates()
            val already = database.labourDao().listForHarvest(harvest.id).mapNotNull { it.workerId }.toSet()
            if (draft.workerIds.any { it in already }) throw LabourInvalid("workers", "already_recorded")
            val now = clock.nowInstant()
            val rows = draft.workerIds.map { workerId ->
                val worker = database.labourDao().findWorker(workerId)
                    ?.takeIf { it.metadata.deletedAt == null && it.workspaceId == harvest.workspaceId }
                    ?: throw LabourInvalid("workers", "not_found")
                HarvestLabourEntity(
                    id = idGenerator.newId(),
                    workspaceId = harvest.workspaceId,
                    harvestId = harvest.id,
                    workerId = worker.id,
                    workerName = worker.name,
                    quantity = 1,
                    unit = draft.unit.name,
                    minutes = draft.minutes,
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                ).let { row ->
                    val entry = if (draft.priceUnknown) row.toLabourEntry()
                        else LabourPricing.capture(row.toLabourEntry().copy(appliedRate = draft.appliedRate), rates, harvest.harvestDate)
                    LabourPricing.amountMinor(entry)
                    row.withRate(entry.appliedRate)
                }
            }
            // #449: a person whose price is not known yet is recorded as such (never as 0 €); the
            // day's cost is shown as incomplete until the last price is confirmed. What stays
            // blocked is adding priced money to a day that already has a posted calculation while
            // another price is still missing: the posted amount would no longer be its subtotal.
            val combined = database.labourDao().listForHarvest(harvest.id) + rows
            val postedCalculation = database.expenseDao().listForHarvest(harvest.id).any {
                it.origin == ExpenseOrigin.DAY_LABOUR.name && it.status == ExpenseStatus.POSTED.name
            }
            if (postedCalculation && rows.any { it.appliedPriceMinor != null } && combined.any { it.appliedPriceMinor == null }) {
                throw LabourFinanceInvalid("appliedRate", "confirm_missing_prices")
            }
            database.labourDao().upsertLabour(rows)
            rows.forEach { database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_LABOUR, it.id, OutboxOperation.CREATE, now) }
            costs.sync(harvest.id, now)
            draft.initialPayments.forEach { payment ->
                if (payment.workerId !in draft.workerIds || payment.campaignId != harvest.campaignId) {
                    throw LabourInvalid("payment", "context_mismatch")
                }
                savePayment(payment)
            }
            AppResult.Success(rows.size)
        }
    }

    override suspend fun recordCount(draft: CountDraft): AppResult<UUID> =
        inTransaction("record_count") {
            runningJornada(draft.harvestId)
            throw LabourInvalid("worker", "required")
        }

    override suspend fun update(entryId: UUID, change: LabourChange): AppResult<Unit> {
        LabourRules.validate(change.quantity, change.unit, change.minutes)
            ?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        return inTransaction("update_labour") {
            val current = liveEntry(entryId) ?: return@inTransaction AppResult.Failure(AppError.NotFound("labour"))
            runningJornada(current.harvestId)
            // A named line is one person: only its unit and hours change.
            if (current.workerId != null && change.quantity != 1) throw LabourInvalid("quantity", "one_person")
            val rate = change.appliedRate ?: current.toLabourEntry().appliedRate
            val savedCurrency = current.toLabourEntry().appliedRate?.currency
            if (savedCurrency != null && rate?.currency != savedCurrency) {
                throw LabourFinanceInvalid("currency", "currency_mismatch")
            }
            val candidate = current.copy(quantity = change.quantity, unit = change.unit.name, minutes = change.minutes).withRate(rate)
            LabourPricing.amountMinor(candidate.toLabourEntry())
            val now = clock.nowInstant()
            database.labourDao().upsertLabour(
                listOf(
                    candidate.copy(metadata = current.metadata.next(now)),
                ),
            )
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_LABOUR, entryId, OutboxOperation.UPDATE, now)
            costs.sync(current.harvestId, now)
            AppResult.Success(Unit)
        }
    }

    override suspend fun remove(entryId: UUID): AppResult<Unit> =
        inTransaction("remove_labour") {
            val current = liveEntry(entryId) ?: return@inTransaction AppResult.Success(Unit)
            runningJornada(current.harvestId)
            val now = clock.nowInstant()
            database.labourDao().upsertLabour(listOf(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now))))
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_LABOUR, entryId, OutboxOperation.DELETE, now)
            costs.sync(current.harvestId, now)
            // #502: an automatic day left with nothing goes with its last jornal.
            JornadaLedger(database, idGenerator).reconcileAutomatic(current.harvestId, now)
            AppResult.Success(Unit)
        }

    override fun observePayments(campaignId: UUID): Flow<List<LabourPayment>> =
        database.labourPaymentDao().observeForCampaign(campaignId).map { rows -> rows.map { it.toPayment() } }.flowOn(dispatchers.io)

    override suspend fun recordPayment(payment: LabourPayment): AppResult<UUID> =
        inTransaction("record_payment") { savePayment(payment); AppResult.Success(payment.id) }

    private suspend fun workspaceId(): UUID = when (val result = workspaceRepository.ensureLocalWorkspace()) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw LabourInvalid("workspace", "unavailable")
    }

    private suspend fun savePayment(payment: LabourPayment) {
        val workspaceId = workspaceId()
        val campaign = database.campaignDao().findById(payment.campaignId)
            ?.takeIf { it.metadata.deletedAt == null && it.workspaceId == workspaceId }
            ?: throw LabourInvalid("campaign", "not_found")
        // #481: archiving a person stops new jornales, never settling what they are owed. The
        // balance check below still refuses paying anyone more than their pending debt.
        database.labourDao().findWorker(payment.workerId)
            ?.takeIf { it.workspaceId == workspaceId }
            ?: throw LabourInvalid("worker", "not_found")
        val existing = database.labourPaymentDao().find(payment.id)
        if (existing != null) {
            if (existing.workspaceId == workspaceId && existing.metadata.deletedAt == null && existing.toPayment() == payment) return
            throw LabourConflict("payment_id_conflict")
        }
        if (runCatching { java.util.Currency.getInstance(payment.currency).defaultFractionDigits >= 0 }.getOrDefault(false).not()) {
            throw LabourInvalid("currency", "invalid")
        }
        val workspace = database.workspaceDao().findById(workspaceId)
            ?: throw LabourInvalid("workspace", "not_found")
        val zone = runCatching { ZoneId.of(workspace.timezone) }
            .getOrElse { throw LabourInvalid("workspace", "invalid_timezone") }
        LabourPaymentRules.validateDate(payment.paymentDate, clock.today(zone))
            ?.let { throw LabourInvalid(it.field, it.code) }
        val balance = LabourSettlement.of(payment.workerId, payment.campaignId, payment.currency,
            LabourFinance(database).costs(payment.campaignId),
            database.labourPaymentDao().listForCampaign(payment.campaignId).map { it.toPayment() })
        LabourPaymentRules.validate(payment, balance, campaign.status)?.let { throw LabourInvalid(it.field, it.code) }
        val now = clock.nowInstant()
        database.labourPaymentDao().upsert(LabourPaymentEntity(payment.id, workspaceId, payment.workerId, payment.campaignId,
            payment.paymentDate, payment.amountMinor, payment.currency, payment.note, LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)))
        database.enqueueCollapsed(idGenerator, SyncEntityType.LABOUR_PAYMENT, payment.id, OutboxOperation.CREATE, now)
    }

    override suspend fun removePayment(paymentId: UUID): AppResult<Unit> = inTransaction("remove_payment") {
        val current = database.labourPaymentDao().find(paymentId) ?: return@inTransaction AppResult.Success(Unit)
        if (current.workspaceId != workspaceId()) throw LabourInvalid("payment", "context_mismatch")
        if (current.metadata.deletedAt == null) {
            val now = clock.nowInstant()
            database.labourPaymentDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.LABOUR_PAYMENT, paymentId, OutboxOperation.DELETE, now)
        }
        AppResult.Success(Unit)
    }

    override suspend fun previousCrew(harvestId: UUID): List<UUID> = withContext(dispatchers.io) {
        val active = when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
            is AppResult.Failure -> return@withContext emptyList()
            is AppResult.Success -> workspace.value
        }
        val harvest = database.harvestDao().findById(harvestId)
            ?.takeIf { it.metadata.deletedAt == null && it.workspaceId == active }
            ?: return@withContext emptyList()
        val farmId = harvest.farmId ?: return@withContext emptyList()
        val rows = database.labourDao().listNamedCrewsBefore(active, farmId, harvestId, harvest.harvestDate)
        val latest = rows.firstOrNull()?.harvestId ?: return@withContext emptyList()
        rows.filter { it.harvestId == latest }.mapNotNull { it.workerId }.distinct()
            .filter { id ->
                database.labourDao().findWorker(id)
                    ?.takeIf { it.metadata.deletedAt == null && it.workspaceId == active } != null
            }
    }

    private suspend fun runningJornada(harvestId: UUID): HarvestEntity {
        val harvest = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null }
            ?: throw LabourInvalid("harvestId", "not_found")
        val campaign = harvest.campaignId?.let { database.campaignDao().findById(it) }
        if (campaign == null || campaign.metadata.deletedAt != null || campaign.status !in RUNNING) throw LabourConflict("closed_campaign")
        val workspaceId = workspaceId()
        if (harvest.workspaceId != workspaceId || campaign.workspaceId != workspaceId || campaign.farmId != harvest.farmId) {
            throw LabourInvalid("harvestId", "context_mismatch")
        }
        return harvest
    }

    private suspend fun liveEntry(id: UUID): HarvestLabourEntity? =
        database.labourDao().findLabour(id)?.takeIf { it.metadata.deletedAt == null }

    private suspend fun <T> inTransaction(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: LabourFinanceInvalid) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: IllegalArgumentException) {
                AppResult.Failure(AppError.Validation("appliedRate", error.message ?: "invalid"))
            } catch (error: ArithmeticException) {
                AppResult.Failure(AppError.Validation("amount", "overflow"))
            } catch (error: LabourInvalid) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: LabourConflict) {
                AppResult.Failure(AppError.Conflict(error.code))
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private companion object {
        val RUNNING = setOf(CampaignStatus.ACTIVE, CampaignStatus.HARVEST)
    }
}
