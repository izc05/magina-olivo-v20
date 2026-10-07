package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.HarvestWithParcels
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.harvest.AUTO_DAY
import com.isivoltpro.maginaolivo.domain.harvest.CollectionMethod
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestDraft
import com.isivoltpro.maginaolivo.domain.harvest.HarvestParcelOption
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRules
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShare
import com.isivoltpro.maginaolivo.domain.harvest.HarvestShareInput
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first Harvest.
 *
 * A Harvest and its origin Parcels are one aggregate (`RC1-NORMATIVE-ADDENDUM` D6): they
 * are written in one transaction, share one version and one outbox intent. The split is
 * whatever the farmer knows — every Parcel exact, some, or none — and nothing here ever
 * fills in a Parcel's kilos. A Harvest belongs to its Farm's running Campaign; once that
 * Campaign is closed the Harvest is history and cannot be changed.
 */
class OfflineFirstHarvestRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
    private val workspaceRepository: WorkspaceRepository? = null,
    /** Test override only; production derives the calendar from the persisted Workspace. */
    private val zoneId: (() -> ZoneId)? = null,
) : HarvestRepository {
    private val jornadas = JornadaLedger(database, idGenerator)
    private val workspaceScope = ActiveWorkspaceScope(database, workspaceRepository)

    override fun observeAll(): Flow<List<Harvest>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            emitAll(
                database.harvestDao().observeAllForWorkspace(active)
                    .map { rows -> rows.toDomain() },
            )
        }.flowOn(dispatchers.io)

    override fun observeForCampaign(campaignId: UUID): Flow<List<Harvest>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            val campaign = database.campaignDao().findById(campaignId)
            if (campaign == null || campaign.workspaceId != active) {
                emit(emptyList())
                return@flow
            }
            emitAll(database.harvestDao().observeForCampaign(campaignId).map { rows -> rows.toDomain() })
        }.flowOn(dispatchers.io)

    override fun observe(id: UUID): Flow<Harvest?> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(null)
                is AppResult.Success -> result.value
            }
            emitAll(
                database.harvestDao().observeWithParcels(id).map { row ->
                    row?.takeIf { it.harvest.workspaceId == active }?.let { listOf(it).toDomain().single() }
                },
            )
        }.flowOn(dispatchers.io)

    override fun observeContexts(): Flow<List<HarvestContext>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            emitAll(
                database.harvestDao().observeRunningCampaignsForWorkspace(active).map { rows ->
                    rows.map { row ->
                        HarvestContext(
                            farmId = row.farmId,
                            farmName = row.farmName,
                            campaignId = row.campaignId,
                            campaignName = row.campaignName,
                            campaignStatus = row.campaignStatus,
                            campaignStart = row.campaignStart,
                            parcels = database.harvestDao().listCampaignParcels(row.campaignId)
                                .map { HarvestParcelOption(it.parcelId, it.name) },
                        )
                    }
                },
            )
        }.flowOn(dispatchers.io)

    override suspend fun create(draft: HarvestDraft): AppResult<UUID> =
        inTransaction("create_harvest") {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@inTransaction result
                is AppResult.Success -> result.value
            }
            val farm = database.farmDao().findById(draft.farmId)
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("farm"))
            if (farm.workspaceId != active) return@inTransaction contextMismatch()
            validate(draft, farm.workspaceId)?.let { return@inTransaction it }
            if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) {
                return@inTransaction conflict("archived_farm")
            }
            val campaign = database.campaignDao().findCurrent(farm.id)
                ?: return@inTransaction conflict("no_running_campaign")
            if (draft.harvestDate.isBefore(campaign.startDate)) {
                return@inTransaction AppResult.Failure(AppError.Validation("harvestDate", "before_campaign"))
            }
            if (campaign.endDate != null && draft.harvestDate.isAfter(campaign.endDate)) {
                return@inTransaction AppResult.Failure(AppError.Validation("harvestDate", "after_campaign"))
            }
            val id = idGenerator.newId()
            val now = clock.nowInstant()
            database.harvestDao().upsert(
                HarvestEntity(
                    id = id,
                    workspaceId = farm.workspaceId,
                    campaignId = campaign.id,
                    farmId = farm.id,
                    harvestDate = draft.harvestDate,
                    weightGrams = draft.totalGrams!!,
                    notes = draft.notes.normalized(),
                    collectionMethod = draft.collectionMethod?.name,
                    workerCount = draft.workerCount,
                    machineryText = draft.machineryText.normalized(),
                    metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                ),
            )
            replaceShares(id, farm.workspaceId, campaign.id, draft.shares, now)
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, id, OutboxOperation.CREATE, now)
            AppResult.Success(id)
        }

    override suspend fun clearUnfoundedDayOrigins(): AppResult<Unit> =
        inTransaction("clear_unfounded_day_origins") {
            jornadas.clearUnfoundedOrigins(clock.nowInstant())
            AppResult.Success(Unit)
        }

    override suspend fun openJornada(farmId: UUID, date: LocalDate): AppResult<UUID> =
        inTransaction("open_jornada") {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@inTransaction result
                is AppResult.Success -> result.value
            }
            val farm = database.farmDao().findById(farmId)
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("farm"))
            if (farm.workspaceId != active) return@inTransaction contextMismatch()
            if (date.isAfter(today(farm.workspaceId))) {
                return@inTransaction AppResult.Failure(AppError.Validation("harvestDate", "future"))
            }
            if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) {
                return@inTransaction conflict("archived_farm")
            }
            val campaign = database.campaignDao().findCurrent(farm.id)
                ?: return@inTransaction conflict("no_running_campaign")
            if (date.isBefore(campaign.startDate)) {
                return@inTransaction AppResult.Failure(AppError.Validation("harvestDate", "before_campaign"))
            }
            if (campaign.endDate != null && date.isAfter(campaign.endDate)) {
                return@inTransaction AppResult.Failure(AppError.Validation("harvestDate", "after_campaign"))
            }
            if (database.harvestDao().listCampaignParcels(campaign.id).isEmpty()) {
                return@inTransaction AppResult.Failure(AppError.Validation("parcels", "empty"))
            }
            // CR-010: the same automatic day the Pesadas of that date go to; opening it again
            // returns it. Stored 0 = not weighed yet (Harvest.awaitingPesadas).
            AppResult.Success(jornadas.autoDay(farm.workspaceId, farm.id, campaign.id, date, clock.nowInstant()))
        }

    override suspend fun update(id: UUID, draft: HarvestDraft): AppResult<Unit> =
        inTransaction("update_harvest") {
            val current = live(id) ?: return@inTransaction AppResult.Failure(AppError.NotFound("harvest"))
            workspaceScope.mismatch(current.workspaceId)?.let { return@inTransaction it }
            if (current.farmId != draft.farmId) {
                return@inTransaction AppResult.Failure(AppError.Validation("farmId", "cannot_change"))
            }
            val campaign = current.campaignId?.let { database.campaignDao().findById(it) }
            if (campaign == null || campaign.status !in RUNNING) return@inTransaction conflict("closed_campaign")
            if (draft.harvestDate.isBefore(campaign.startDate)) {
                return@inTransaction AppResult.Failure(AppError.Validation("harvestDate", "before_campaign"))
            }
            if (campaign.endDate != null && draft.harvestDate.isAfter(campaign.endDate)) {
                return@inTransaction AppResult.Failure(AppError.Validation("harvestDate", "after_campaign"))
            }
            if (current.dayOrigin == AUTO_DAY) return@inTransaction updateAutoDay(current, draft)
            validate(draft, current.workspaceId)?.let { return@inTransaction it }
            // Phase 19B: a Jornada with Pesadas takes its kilos from them, never from the form.
            val pesadas = database.deliveryDao().listLiveForHarvest(id)
            val pesadaGrams = pesadas.sumOf { it.netGrams }
            var shares = draft.shares
            if (pesadas.isNotEmpty()) {
                if (shares.size > 1 && shares.any { it.weightGrams != null }) {
                    return@inTransaction AppResult.Failure(AppError.Validation("parcels", "exact_with_pesadas"))
                }
                if (pesadas.any { it.deliveryDate.isBefore(draft.harvestDate) }) {
                    return@inTransaction AppResult.Failure(AppError.Validation("harvestDate", "after_pesadas"))
                }
                shares = shares.map { if (it.weightGrams != null) it.copy(weightGrams = pesadaGrams) else it }
            }
            val now = clock.nowInstant()
            database.harvestDao().upsert(
                current.copy(
                    harvestDate = draft.harvestDate,
                    weightGrams = if (pesadas.isEmpty()) draft.totalGrams!! else pesadaGrams,
                    notes = draft.notes.normalized(),
                    collectionMethod = draft.collectionMethod?.name,
                    workerCount = draft.workerCount,
                    machineryText = draft.machineryText.normalized(),
                    metadata = current.metadata.next(now),
                ),
            )
            replaceShares(id, current.workspaceId, campaign.id, shares, now)
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, id, OutboxOperation.UPDATE, now)
            // CR-010 A3: its calculated costs follow the day's date.
            DayCostLedger(database, idGenerator).sync(id, now)
            AppResult.Success(Unit)
        }

    /**
     * CR-010 (A1, note 2): an automatic day's date, kilos and Parcels are its Pesadas'; only what
     * describes the day itself (method, people, machinery, notes) is the farmer's to edit.
     */
    private suspend fun updateAutoDay(current: HarvestEntity, draft: HarvestDraft): AppResult<Unit> {
        if (draft.harvestDate != current.harvestDate) return AppResult.Failure(AppError.Validation("harvestDate", "automatic_day"))
        if ((draft.workerCount ?: 0) < 0) return AppResult.Failure(AppError.Validation("workerCount", "negative"))
        val now = clock.nowInstant()
        database.harvestDao().upsert(
            current.copy(
                notes = draft.notes.normalized(),
                collectionMethod = draft.collectionMethod?.name,
                workerCount = draft.workerCount,
                machineryText = draft.machineryText.normalized(),
                metadata = current.metadata.next(now),
            ),
        )
        database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, current.id, OutboxOperation.UPDATE, now)
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: UUID): AppResult<Unit> =
        inTransaction("delete_harvest") {
            val current = database.harvestDao().findById(id)
                ?: return@inTransaction AppResult.Failure(AppError.NotFound("harvest"))
            workspaceScope.mismatch(current.workspaceId)?.let { return@inTransaction it }
            if (current.metadata.deletedAt != null) return@inTransaction AppResult.Success(Unit)
            val campaign = current.campaignId?.let { database.campaignDao().findById(it) }
            if (campaign == null || campaign.status !in RUNNING) return@inTransaction conflict("closed_campaign")
            // #457: a live Pesada always has its day; a day with Pesadas is changed by moving or
            // correcting them, never removed from under them.
            if (database.deliveryDao().listLiveForHarvest(id).isNotEmpty()) {
                return@inTransaction conflict(com.isivoltpro.maginaolivo.domain.harvest.HARVEST_HAS_DELIVERIES)
            }
            val now = clock.nowInstant()
            database.harvestDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
            database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST, id, OutboxOperation.DELETE, now)
            // Phase 19D: its jornales only describe this Jornada; they go with it.
            database.labourDao().listForHarvest(id).forEach { line ->
                database.labourDao().upsertLabour(
                    listOf(line.copy(metadata = line.metadata.next(now).copy(deletedAt = now))),
                )
                database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_LABOUR, line.id, OutboxOperation.DELETE, now)
            }
            // CR-010 A3: its calculated costs go with its jornales and equipment (nothing backs them now).
            DayCostLedger(database, idGenerator).removeFor(id, now)
            // Phase 19F: its costs are real money: they stay in the ledger, only unlinked.
            database.expenseDao().listForHarvest(id).forEach { expense ->
                database.expenseDao().upsert(
                    expense.copy(
                        harvestId = null,
                        metadata = expense.metadata.next(now),
                    ),
                )
                database.enqueueCollapsed(idGenerator, SyncEntityType.EXPENSE, expense.id, OutboxOperation.UPDATE, now)
            }
            // Phase 19E: and so does its equipment.
            database.equipmentDao().listForHarvest(id).forEach { line ->
                database.equipmentDao().upsert(listOf(line.copy(metadata = line.metadata.next(now).copy(deletedAt = now))))
                database.enqueueCollapsed(idGenerator, SyncEntityType.HARVEST_EQUIPMENT, line.id, OutboxOperation.DELETE, now)
            }
            AppResult.Success(Unit)
        }

    private suspend fun validate(draft: HarvestDraft, workspaceId: UUID): AppResult.Failure? =
        HarvestRules.validate(draft, today(workspaceId))
            ?.let { AppResult.Failure(AppError.Validation(it.field, it.code)) }

    private suspend fun today(workspaceId: UUID): LocalDate =
        database.todayForWorkspace(workspaceId, clock, zoneId)

    private fun contextMismatch() =
        AppResult.Failure(AppError.Validation("workspaceId", "context_mismatch"))


    /**
     * Writes the origin Parcels with the Harvest, inside its transaction (#458). A Parcel the day
     * already had keeps its row, id and `parcelNameAtHarvest`; only a real change of its kilos
     * touches it. A Parcel added must belong to the Harvest's Campaign and takes its current name;
     * a Parcel taken out loses only its own row. A share without kilos is stored `UNALLOCATED`
     * with no weight, never as zero and never as a computed part of the total.
     */
    private suspend fun replaceShares(
        harvestId: UUID,
        workspaceId: UUID,
        campaignId: UUID,
        shares: List<HarvestShareInput>,
        now: Instant,
    ) {
        val existing = database.harvestDao().listParcels(harvestId).associateBy { it.parcelId }
        val wanted = shares.map { it.parcelId }.toSet()
        val removed = existing.values.filter { it.parcelId !in wanted }.map { it.id }
        val added = shares.filter { it.parcelId !in existing }
        val campaignParcels = if (added.isEmpty()) {
            emptyMap()
        } else {
            database.harvestDao().listCampaignParcels(campaignId).associateBy { it.parcelId }
        }
        val rows = shares.sortedBy { it.parcelId.toString() }.mapNotNull { share ->
            val mode = if (share.weightGrams == null) HarvestAllocation.UNALLOCATED.name else HarvestAllocation.EXACT.name
            val kept = existing[share.parcelId]
            when {
                kept == null -> {
                    val parcel = campaignParcels[share.parcelId] ?: throw InvalidShare("parcel_not_in_campaign")
                    HarvestParcelEntity(
                        id = idGenerator.newId(),
                        workspaceId = workspaceId,
                        harvestId = harvestId,
                        parcelId = parcel.parcelId,
                        campaignParcelId = parcel.campaignParcelId,
                        parcelNameAtHarvest = parcel.name,
                        weightGrams = share.weightGrams,
                        allocationMode = mode,
                        metadata = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING),
                    )
                }
                kept.weightGrams == share.weightGrams && kept.allocationMode == mode -> null
                else -> kept.copy(weightGrams = share.weightGrams, allocationMode = mode, metadata = kept.metadata.next(now))
            }
        }
        if (removed.isNotEmpty()) database.harvestDao().deleteParcelsById(removed)
        if (rows.isNotEmpty()) database.harvestDao().upsertParcels(rows)
    }

    private suspend fun live(id: UUID): HarvestEntity? =
        database.harvestDao().findById(id)?.takeIf { it.metadata.deletedAt == null }

    private suspend fun <T> inTransaction(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        withContext(dispatchers.io) {
            try {
                database.withTransaction { block() }
            } catch (error: InvalidShare) {
                AppResult.Failure(AppError.Validation("parcels", error.message ?: "invalid"))
            } catch (error: LabourFinanceInvalid) {
                AppResult.Failure(AppError.Validation(error.field, error.code))
            } catch (error: Throwable) {
                AppResult.Failure(AppError.Storage(operation, error))
            }
        }

    /** Reads each Farm and Campaign once per emission, for the names a row shows. */
    private suspend fun List<HarvestWithParcels>.toDomain(): List<Harvest> {
        val farms = mapNotNull { it.harvest.farmId }.distinct()
            .associateWith { database.farmDao().findById(it)?.name }
        val campaigns = mapNotNull { it.harvest.campaignId }.distinct()
            .associateWith { database.campaignDao().findById(it) }
        return map { row ->
            val harvest = row.harvest
            val campaign = harvest.campaignId?.let(campaigns::get)
            Harvest(
                id = harvest.id,
                workspaceId = harvest.workspaceId,
                farmId = harvest.farmId,
                campaignId = harvest.campaignId,
                harvestDate = harvest.harvestDate,
                totalGrams = harvest.weightGrams,
                shares = row.parcels
                    .sortedBy { it.parcelNameAtHarvest.lowercase() }
                    .map {
                        HarvestShare(
                            parcelId = it.parcelId,
                            parcelName = it.parcelNameAtHarvest,
                            allocation = if (it.allocationMode == HarvestAllocation.EXACT.name) {
                                HarvestAllocation.EXACT
                            } else {
                                HarvestAllocation.UNALLOCATED
                            },
                            weightGrams = it.weightGrams,
                        )
                    },
                collectionMethod = harvest.collectionMethod
                    ?.let { runCatching { CollectionMethod.valueOf(it) }.getOrNull() },
                workerCount = harvest.workerCount,
                machineryText = harvest.machineryText,
                notes = harvest.notes,
                version = harvest.metadata.version,
                farmName = harvest.farmId?.let(farms::get),
                campaignName = campaign?.name,
                editable = campaign != null && campaign.status in RUNNING,
                automatic = harvest.dayOrigin == AUTO_DAY,
            )
        }
    }

    private fun LocalMetadata.next(now: Instant) =
        copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private fun String?.normalized() = this?.trim()?.ifEmpty { null }

    private fun conflict(code: String): AppResult.Failure = AppResult.Failure(AppError.Conflict(code))

    private class InvalidShare(message: String) : RuntimeException(message)

    private companion object {
        val RUNNING = setOf(CampaignStatus.ACTIVE, CampaignStatus.HARVEST)
    }
}
