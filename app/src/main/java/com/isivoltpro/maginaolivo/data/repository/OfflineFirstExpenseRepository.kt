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
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.PurchaseLine
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val writer = ExpenseLedgerWriter(database, idGenerator)

    override fun observeAll(): Flow<List<Expense>> =
        database.expenseDao().observeAll().map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeForActivity(activityId: UUID): Flow<List<Expense>> =
        database.expenseDao().observeForActivity(activityId).map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.io)

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
            AppResult.Success(writer.insert(workspaceId, draft, ExpenseStatus.POSTED, ExpenseOrigin.MANUAL, clock.nowInstant()))
        }
    }

    override suspend fun update(id: UUID, draft: ExpenseDraft): AppResult<Unit> =
        inTransaction("update_expense") {
            val current = live(id) ?: return@inTransaction AppResult.Failure(AppError.NotFound("expense"))
            writer.rewrite(current, draft, clock.nowInstant())
            AppResult.Success(Unit)
        }

    override suspend fun post(id: UUID): AppResult<Unit> =
        inTransaction("post_expense") {
            val current = live(id) ?: return@inTransaction AppResult.Failure(AppError.NotFound("expense"))
            if (current.status == ExpenseStatus.POSTED.name) return@inTransaction AppResult.Success(Unit)
            writer.post(current, clock.nowInstant())
            AppResult.Success(Unit)
        }

    override suspend fun delete(id: UUID): AppResult<Unit> =
        inTransaction("delete_expense") {
            val current = database.expenseDao().findById(id)
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("expense"))
            if (current.metadata.deletedAt != null) return@inTransaction AppResult.Success(Unit)
            writer.delete(current, clock.nowInstant())
            AppResult.Success(Unit)
        }

    private suspend fun live(id: UUID): ExpenseEntity? =
        database.expenseDao().findById(id)?.takeIf { it.metadata.deletedAt == null }

    private suspend fun <T> inTransaction(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: InvalidExpense) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }
}

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
    invoiceNumber = purchase?.invoiceNumber,
    lines = items.map {
        PurchaseLine(it.productName, it.quantity, it.unit, it.unitPriceMinor, it.lineTotalMinor)
    },
    notes = notes,
)
