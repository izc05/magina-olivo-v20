package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.ExpenseEntity
import com.isivoltpro.maginaolivo.data.local.entity.PurchaseEntity
import com.isivoltpro.maginaolivo.data.local.entity.PurchaseItemEntity
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.DayCostRole
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.PurchaseLine
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OfflineFirstExpenseRepository(
    private val database: MaginaOlivoDatabase,
    private val workspaceRepository: WorkspaceRepository,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : ExpenseRepository {
    private val costs = DayCostLedger(database, idGenerator)

    private val writer = ExpenseLedgerWriter(database, idGenerator)

    override fun observeAll(): Flow<List<Expense>> =
        flow {
            when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
                is AppResult.Failure -> throw IllegalStateException("active_workspace_unavailable")
                is AppResult.Success -> emitAll(
                    database.expenseDao().observeForWorkspace(workspace.value)
                        .map { rows -> rows.map { it.toDomain() } },
                )
            }
        }.flowOn(dispatchers.io)

    override fun observeForActivity(activityId: UUID): Flow<List<Expense>> =
        database.expenseDao().observeForActivity(activityId).map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)

    override fun observeForHarvest(harvestId: UUID): Flow<List<Expense>> =
        database.expenseDao().observeForHarvest(harvestId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observe(id: UUID): Flow<Expense?> =
        combine(
            database.expenseDao().observeById(id),
            database.expenseDao().observePurchaseForExpense(id),
            database.expenseDao().observeItemsForExpense(id),
        ) { expense, purchase, items -> expense?.toDomain(purchase, items) }.flowOn(dispatchers.io)

    override suspend fun create(draft: ExpenseDraft): AppResult<UUID> {
        val workspaceId = draft.farmId?.let { farmId ->
            withContext(dispatchers.io) { database.farmDao().findById(farmId)?.workspaceId }
                ?: return AppResult.Failure(AppError.Validation("farmId", "not_found"))
        } ?: when (val workspace = workspaceRepository.ensureLocalWorkspace()) {
            is AppResult.Failure -> return workspace
            is AppResult.Success -> workspace.value
        }
        return inTransaction("create_expense") {
            val now = clock.nowInstant()
            // #475: a day's cost replaces its calculation only when the farmer said so.
            val origin = if (draft.dayCostRole == DayCostRole.REPLACEMENT) {
                costs.requireReplaceable(draft.harvestId, draft.category.name)
                ExpenseOrigin.DAY_REPLACEMENT
            } else {
                ExpenseOrigin.MANUAL
            }
            val id = writer.insert(workspaceId, draft, ExpenseStatus.POSTED, origin, now)
            // CR-010 A3: a hand-typed cost on a day decides whether its calculated one counts.
            costs.sync(draft.harvestId, now)
            AppResult.Success(id)
        }
    }

    override suspend fun update(id: UUID, draft: ExpenseDraft): AppResult<Unit> =
        inTransaction("update_expense") {
            val current = live(id) ?: return@inTransaction AppResult.Failure(AppError.NotFound("expense"))
            if (current.origin in DayCostLedger.CALCULATED) return@inTransaction AppResult.Failure(AppError.Conflict("calculated_cost"))
            // Codex #520: a work's cost never moves to another work or loses it in a plain edit.
            if (current.origin == ExpenseOrigin.ACTIVITY_COST.name && draft.activityId != current.activityId) {
                return@inTransaction AppResult.Failure(AppError.Validation("activityId", "activity_cost_locked"))
            }
            val now = clock.nowInstant()
            val origin = roleOrigin(current, draft)
            writer.rewrite(current, draft, now, origin)
            costs.sync(current.harvestId, now)
            if (draft.harvestId != current.harvestId) {
                costs.sync(draft.harvestId, now)
                // #502: the day it left may now hold nothing.
                JornadaLedger(database, idGenerator).reconcileAutomatic(current.harvestId, now)
            }
            AppResult.Success(Unit)
        }

    override suspend fun post(id: UUID): AppResult<Unit> =
        inTransaction("post_expense") {
            val current = live(id) ?: return@inTransaction AppResult.Failure(AppError.NotFound("expense"))
            if (current.status == ExpenseStatus.POSTED.name) return@inTransaction AppResult.Success(Unit)
            if (current.origin in DayCostLedger.CALCULATED) return@inTransaction AppResult.Failure(AppError.Conflict("calculated_cost"))
            // #475: a replacement taken back into use replaces again: never over paid jornales.
            if (current.origin == ExpenseOrigin.DAY_REPLACEMENT.name) {
                costs.requireReplaceable(current.harvestId, current.category, requireCalculated = false)
            }
            val now = clock.nowInstant()
            writer.post(current, now, clock.today(java.time.ZoneId.systemDefault()))
            costs.sync(current.harvestId, now)
            AppResult.Success(Unit)
        }

    override suspend fun delete(id: UUID): AppResult<Unit> =
        inTransaction("delete_expense") {
            val current = database.expenseDao().findById(id)
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("expense"))
            if (current.metadata.deletedAt != null) return@inTransaction AppResult.Success(Unit)
            if (current.origin in DayCostLedger.CALCULATED) return@inTransaction AppResult.Failure(AppError.Conflict("calculated_cost"))
            val now = clock.nowInstant()
            writer.delete(current, now)
            costs.sync(current.harvestId, now)
            // #502: an automatic day left with nothing goes with its last Gasto.
            JornadaLedger(database, idGenerator).reconcileAutomatic(current.harvestId, now)
            AppResult.Success(Unit)
        }

    override suspend fun keepAsIndependent(id: UUID): AppResult<Unit> =
        inTransaction("keep_expense_independent") {
            val current = live(id) ?: return@inTransaction AppResult.Failure(AppError.NotFound("expense"))
            if (current.origin != ExpenseOrigin.ACTIVITY_COST.name) {
                return@inTransaction AppResult.Failure(AppError.Conflict("not_activity_cost"))
            }
            writer.detachFromActivity(current, clock.nowInstant())
            AppResult.Success(Unit)
        }

    /**
     * #475: the origin an edit keeps. A hand-typed or document cost becomes a replacement only by
     * the farmer's explicit choice (checked like a new one); choosing «Se añade» again makes it an
     * ordinary hand-typed cost. Any other origin is kept as it is.
     */
    private suspend fun roleOrigin(current: ExpenseEntity, draft: ExpenseDraft): ExpenseOrigin {
        val stored = ExpenseOrigin.entries.firstOrNull { it.name == current.origin } ?: ExpenseOrigin.MANUAL
        if (stored !in ROLE_ORIGINS) {
            if (draft.dayCostRole == DayCostRole.REPLACEMENT) throw InvalidExpense("dayCostRole", "not_replaceable")
            return stored
        }
        return when (draft.dayCostRole) {
            DayCostRole.ADDITIVE -> if (stored == ExpenseOrigin.DAY_REPLACEMENT) ExpenseOrigin.MANUAL else stored
            DayCostRole.REPLACEMENT -> {
                val unchanged = stored == ExpenseOrigin.DAY_REPLACEMENT && draft.harvestId == current.harvestId &&
                    draft.category.name == current.category
                if (!unchanged) costs.requireReplaceable(draft.harvestId, draft.category.name)
                ExpenseOrigin.DAY_REPLACEMENT
            }
        }
    }

    private suspend fun live(id: UUID): ExpenseEntity? =
        database.expenseDao().findById(id)?.takeIf { it.metadata.deletedAt == null }

    private suspend fun <T> inTransaction(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: InvalidExpense) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: LabourFinanceInvalid) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }
}

private val ROLE_ORIGINS = setOf(ExpenseOrigin.MANUAL, ExpenseOrigin.DOCUMENT_OCR, ExpenseOrigin.DAY_REPLACEMENT)

internal fun ExpenseEntity.toDomain(
    purchase: PurchaseEntity? = null,
    items: List<PurchaseItemEntity> = emptyList(),
) = Expense(
    id = id,
    workspaceId = workspaceId,
    expenseDate = expenseDate,
    concept = concept,
    category = ExpenseCategory.entries.firstOrNull { it.name == category } ?: ExpenseCategory.OTHER,
    amountMinor = amountMinor,
    currency = currency,
    // Anything unreadable is treated as unconfirmed, never as counted money.
    status = ExpenseStatus.entries.firstOrNull { it.name == status } ?: ExpenseStatus.DRAFT,
    origin = ExpenseOrigin.entries.firstOrNull { it.name == origin } ?: ExpenseOrigin.MANUAL,
    supplierName = provider,
    supplierOrganizationId = supplierOrganizationId,
    farmId = farmId,
    parcelId = parcelId,
    campaignId = campaignId,
    activityId = activityId,
    harvestId = harvestId,
    invoiceNumber = purchase?.invoiceNumber,
    lines = items.map {
        PurchaseLine(it.productName, it.quantity, it.unit, it.unitPriceMinor, it.lineTotalMinor)
    },
    notes = notes,
)
