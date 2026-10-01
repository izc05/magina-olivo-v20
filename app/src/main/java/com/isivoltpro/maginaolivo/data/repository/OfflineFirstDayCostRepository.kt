package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.RecollectionRatesEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.repository.DayCostLedger.Companion.toDomain
import com.isivoltpro.maginaolivo.domain.expense.DayCostKind
import com.isivoltpro.maginaolivo.domain.expense.DayCostRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.UnlinkedDayCosts
import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** CR-010 A3: the Farm's recollection prices and the farmer's choice on a cost collision. */
class OfflineFirstDayCostRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : DayCostRepository {
    private val costs = DayCostLedger(database, idGenerator)

    override fun observeRates(farmId: UUID): Flow<RecollectionRates> =
        database.recollectionRatesDao().observeForFarm(farmId)
            .map { it?.toDomain() ?: RecollectionRates() }
            .flowOn(dispatchers.io)

    override suspend fun saveRates(farmId: UUID, rates: RecollectionRates): AppResult<Unit> {
        val prices = listOfNotNull(rates.fullDayMinor, rates.hourlyMinor) + rates.equipmentDayMinor.values
        if (prices.any { it <= 0 }) return AppResult.Failure(AppError.Validation("rates", "not_positive"))
        return inTransaction("save_rates") {
            val farm = database.farmDao().findById(farmId)?.takeIf { it.metadata.deletedAt == null }
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("farm"))
            val now = clock.nowInstant()
            val current = database.recollectionRatesDao().findForFarm(farmId)
            val row = RecollectionRatesEntity(
                id = current?.id ?: idGenerator.newId(),
                workspaceId = farm.workspaceId,
                farmId = farmId,
                currency = rates.currency,
                fullDayMinor = rates.fullDayMinor,
                hourlyMinor = rates.hourlyMinor,
                equipmentDayJson = DayCostLedger.equipmentJson(rates),
                metadata = current?.metadata?.copy(
                    updatedAt = now,
                    version = current.metadata.version + 1,
                    syncStatus = SyncStatus.PENDING,
                ) ?: LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
            )
            database.recollectionRatesDao().upsert(row)
            database.enqueueCollapsed(
                idGenerator, SyncEntityType.RECOLLECTION_RATES, row.id,
                if (current == null) OutboxOperation.CREATE else OutboxOperation.UPDATE, now,
            )
            // A3: the running Campaign's days follow the new prices; a closed Campaign keeps its own.
            costs.syncFarm(farmId, now)
            AppResult.Success(Unit)
        }
    }

    override suspend fun preferCalculated(harvestId: UUID, kind: DayCostKind): AppResult<Unit> =
        inTransaction("prefer_calculated") {
            val day = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null }
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("harvest"))
            // A closed Campaign is history: neither its hand-typed nor its calculated costs change.
            val campaign = day.campaignId?.let { database.campaignDao().findById(it) }
            if (campaign == null || (campaign.status != CampaignStatus.ACTIVE && campaign.status != CampaignStatus.HARVEST)) {
                return@inTransaction AppResult.Failure(AppError.Conflict("campaign_closed"))
            }
            costs.preferCalculated(day.id, kind, clock.nowInstant())
            AppResult.Success(Unit)
        }

    override suspend fun linkToDay(expenseId: UUID, harvestId: UUID): AppResult<Unit> =
        inTransaction("link_to_day") {
            val day = database.harvestDao().findById(harvestId)?.takeIf { it.metadata.deletedAt == null }
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("harvest"))
            val expense = database.expenseDao().findById(expenseId)?.takeIf { it.metadata.deletedAt == null }
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("expense"))
            val origin = runCatching { ExpenseOrigin.valueOf(expense.origin) }.getOrNull()
            if (origin !in UnlinkedDayCosts.LINKABLE || expense.harvestId != null) {
                return@inTransaction AppResult.Failure(AppError.Conflict("not_unlinked"))
            }
            if (expense.farmId != day.farmId || expense.expenseDate != day.harvestDate) {
                return@inTransaction AppResult.Failure(AppError.Validation("expense", "other_day"))
            }
            val campaign = day.campaignId?.let { database.campaignDao().findById(it) }
            if (campaign == null || (campaign.status != CampaignStatus.ACTIVE && campaign.status != CampaignStatus.HARVEST)) {
                return@inTransaction AppResult.Failure(AppError.Conflict("campaign_closed"))
            }
            val now = clock.nowInstant()
            database.expenseDao().upsert(
                expense.copy(
                    harvestId = day.id,
                    campaignId = day.campaignId,
                    metadata = expense.metadata.copy(
                        updatedAt = now,
                        version = expense.metadata.version + 1,
                        syncStatus = SyncStatus.PENDING,
                    ),
                ),
            )
            database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, expense.id, OutboxOperation.UPDATE, now)
            costs.sync(day.id, now)
            AppResult.Success(Unit)
        }

    private suspend fun <T> inTransaction(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: LabourFinanceInvalid) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }
}
