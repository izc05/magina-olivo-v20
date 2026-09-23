package com.isivoltpro.maginaolivo.data.repository

import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity
import com.isivoltpro.maginaolivo.data.local.entity.ActivityParcelTargetEntity
import com.isivoltpro.maginaolivo.data.local.entity.FertilizationDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IncidentDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.IrrigationPriceSnapshotEntity
import com.isivoltpro.maginaolivo.data.local.entity.MaintenanceDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.PhytosanitaryDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.PruningDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.SoilWorkDetailEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.SyncOutboxEntity
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.ActivityWithTargets
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPrice
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import java.time.Instant
import java.util.UUID
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Offline-first Activity engine.
 *
 * One canonical Activity may target many Parcels through `activity_parcels`; a
 * multi-parcel operation never duplicates the Activity header. The Activity and its
 * targets form one aggregate: they mutate in the same transaction, share one version
 * and collapse into a single outbox intent (RC1-NORMATIVE-ADDENDUM D5).
 */
class OfflineFirstActivityRepository(
    private val database: MaginaOlivoDatabase,
    private val clock: AppClock,
    private val idGenerator: IdGenerator,
    private val dispatchers: AppDispatchers,
) : ActivityRepository {
    override fun observeSelectableParcels(farmId: UUID): Flow<List<ActivityParcelOption>> =
        database.parcelDao().observeActive(farmId).map { rows ->
            rows.map { ActivityParcelOption(it.parcel.id, it.parcel.displayName, it.parcel.managedAreaM2) }
        }.flowOn(dispatchers.io)

    override fun observeForFarm(farmId: UUID): Flow<List<Activity>> =
        database.activityDao().observeForFarm(farmId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeForParcel(parcelId: UUID): Flow<List<Activity>> =
        database.activityDao().observeForParcel(parcelId).map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    private val ledger = ExpenseLedgerWriter(database, idGenerator)

    override fun observe(id: UUID): Flow<Activity?> =
        combine(
            database.activityDao().observeWithTargets(id),
            database.expenseDao().observeForActivity(id),
        ) { row, expenses ->
            row?.toDomain()?.copy(
                costMinor = expenses.firstOrNull { it.origin == ExpenseOrigin.ACTIVITY_COST.name }?.amountMinor,
            )
        }.flowOn(dispatchers.io)

    override suspend fun create(command: NewActivity): AppResult<UUID> {
        val description = command.description.trim()
        if (description.isEmpty()) return AppResult.Failure(AppError.Validation("description", "blank"))
        if (!command.asDraft && command.parcelIds.isEmpty()) {
            return AppResult.Failure(AppError.Validation("parcelIds", "empty"))
        }
        validateDetail(command.type, command.detail)?.let { return it }
        notNegative("costMinor", command.costMinor?.toDouble())?.let { return it }
        return withContext(dispatchers.io) {
            safely("create_activity") {
                val farm = database.farmDao().findById(command.farmId)
                    ?: return@safely AppResult.Failure(AppError.NotFound("farm"))
                if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) {
                    return@safely AppResult.Failure(AppError.Conflict("archived_farm"))
                }
                val id = idGenerator.newId()
                val now = clock.nowInstant()
                database.activityDao().upsert(
                    ActivityEntity(
                        id = id,
                        workspaceId = farm.workspaceId,
                        campaignId = command.campaignId,
                        farmId = farm.id,
                        activityDate = command.activityDate,
                        type = command.type.name,
                        status = if (command.asDraft) ActivityStatus.DRAFT else ActivityStatus.PLANNED,
                        description = description,
                        notes = command.notes.normalized(),
                        metadata = pending(now),
                    ),
                )
                replaceDetail(id, farm.workspaceId, command.detail, now)
                replaceTargets(id, command.parcelIds, now)
                enqueue(id, OutboxOperation.CREATE, now)
                syncCost(id, command.costMinor, now)
                AppResult.Success(id)
            }
        }
    }

    override suspend fun update(id: UUID, changes: ActivityChanges): AppResult<Unit> {
        val description = changes.description.trim()
        if (description.isEmpty()) return AppResult.Failure(AppError.Validation("description", "blank"))
        validateDetail(changes.type, changes.detail)?.let { return it }
        notNegative("costMinor", changes.costMinor?.toDouble())?.let { return it }
        return mutate(id, "update_activity") { current, now ->
            if (current.status !in EDITABLE) return@mutate conflict("protected_activity")
            if (current.status == ActivityStatus.PLANNED && changes.parcelIds.isEmpty()) {
                return@mutate AppResult.Failure(AppError.Validation("parcelIds", "empty"))
            }
            database.activityDao().upsert(
                current.copy(
                    type = changes.type.name,
                    activityDate = changes.activityDate,
                    description = description,
                    notes = changes.notes.normalized(),
                    metadata = current.metadata.next(now),
                ),
            )
            replaceDetail(id, current.workspaceId, changes.detail, now)
            replaceTargets(id, changes.parcelIds, now)
            enqueue(id, OutboxOperation.UPDATE, now)
            syncCost(id, changes.costMinor, now)
            AppResult.Success(Unit)
        }
    }

    override suspend fun plan(id: UUID): AppResult<Unit> = transition(id, setOf(ActivityStatus.DRAFT), ActivityStatus.PLANNED, "plan_activity", requireTargets = true)

    override suspend fun complete(id: UUID): AppResult<Unit> = transition(id, setOf(ActivityStatus.PLANNED), ActivityStatus.COMPLETED, "complete_activity", requireTargets = true)

    override suspend fun cancel(id: UUID): AppResult<Unit> = transition(id, setOf(ActivityStatus.DRAFT, ActivityStatus.PLANNED), ActivityStatus.CANCELLED, "cancel_activity", requireTargets = false)

    override suspend fun reopen(id: UUID): AppResult<Unit> = transition(id, setOf(ActivityStatus.COMPLETED, ActivityStatus.CANCELLED), ActivityStatus.PLANNED, "reopen_activity", requireTargets = true)

    override suspend fun archive(id: UUID): AppResult<Unit> = mutate(id, "archive_activity", allowArchived = true) { current, now ->
        if (current.metadata.deletedAt != null) return@mutate AppResult.Success(Unit)
        if (current.status !in ARCHIVABLE) return@mutate conflict("protected_activity")
        database.activityDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
        enqueue(id, OutboxOperation.DELETE, now)
        // Only DRAFT or CANCELLED work can be archived: its convenience cost goes with it.
        syncCost(id, null, now)
        AppResult.Success(Unit)
    }

    private suspend fun transition(
        id: UUID,
        from: Set<ActivityStatus>,
        to: ActivityStatus,
        operation: String,
        requireTargets: Boolean,
    ) = mutate(id, operation) { current, now ->
        if (current.status !in from) return@mutate conflict("illegal_activity_transition")
        if (requireTargets && database.activityDao().countTargets(id) == 0) {
            return@mutate AppResult.Failure(AppError.Validation("parcelIds", "empty"))
        }
        database.activityDao().upsert(current.copy(status = to, metadata = current.metadata.next(now)))
        enqueue(id, OutboxOperation.UPDATE, now)
        AppResult.Success(Unit)
    }

    /**
     * `RC1-NORMATIVE-ADDENDUM` D2: the Activity form's Coste is a convenience for its one
     * linked ACTIVITY_COST Expense, written in this same transaction. Editing it edits that
     * Expense, clearing it deletes it, and it is never a second number on the Activity.
     * Other Expenses a person linked to the Activity are left alone.
     */
    private suspend fun syncCost(activityId: UUID, costMinor: Long?, now: Instant) {
        val activity = database.activityDao().findById(activityId) ?: return
        val existing = database.expenseDao().findActivityCost(activityId)
        if (costMinor == null || costMinor == 0L) {
            existing?.let { ledger.delete(it, now) }
            return
        }
        val draft = ExpenseDraft(
            expenseDate = activity.activityDate,
            concept = activity.description,
            category = costCategory(activity.type),
            amountMinor = costMinor,
            currency = existing?.currency ?: DEFAULT_CURRENCY,
            supplierOrganizationId = existing?.supplierOrganizationId,
            supplierText = existing?.provider,
            farmId = activity.farmId,
            campaignId = activity.campaignId ?: existing?.campaignId,
            activityId = activityId,
            notes = existing?.notes,
        )
        if (existing == null) {
            ledger.insert(activity.workspaceId, draft, ExpenseStatus.POSTED, ExpenseOrigin.ACTIVITY_COST, now)
        } else {
            ledger.rewrite(existing, draft, now)
        }
    }

    private fun costCategory(type: String): ExpenseCategory = when (runCatching { ActivityType.valueOf(type) }.getOrNull()) {
        ActivityType.FERTILIZATION, ActivityType.PHYTOSANITARY -> ExpenseCategory.PRODUCTS
        ActivityType.IRRIGATION -> ExpenseCategory.IRRIGATION
        ActivityType.PRUNING -> ExpenseCategory.LABOR
        ActivityType.SOIL_WORK -> ExpenseCategory.MACHINERY
        ActivityType.MAINTENANCE -> ExpenseCategory.REPAIR
        else -> ExpenseCategory.OTHER
    }

    private suspend fun replaceTargets(activityId: UUID, parcelIds: Set<UUID>, now: Instant) {
        val activity = database.activityDao().findById(activityId) ?: error("activity missing")
        val farmId = activity.farmId ?: throw InvalidSelection("activity_without_farm")
        val rows = parcelIds.sortedBy(UUID::toString).map { parcelId ->
            val parcel = database.parcelDao().findById(parcelId) ?: throw InvalidSelection("parcel_not_found")
            val membership = database.parcelDao().findCurrentMembership(parcelId)
            if (membership?.farmId != farmId || parcel.status != RecordStatus.ACTIVE ||
                parcel.metadata.deletedAt != null || parcel.workspaceId != activity.workspaceId
            ) {
                throw InvalidSelection("parcel_not_in_farm")
            }
            ActivityParcelTargetEntity(
                id = idGenerator.newId(),
                workspaceId = activity.workspaceId,
                activityId = activityId,
                parcelId = parcel.id,
                parcelNameAtTarget = parcel.displayName,
                areaAffectedM2 = parcel.managedAreaM2,
                metadata = pending(now),
            )
        }
        database.activityDao().deleteTargets(activityId)
        if (rows.isNotEmpty()) database.activityDao().upsertTargets(rows)
    }

    private suspend fun enqueue(id: UUID, requested: OutboxOperation, now: Instant) {
        val existing = database.syncOutboxDao().listForEntity(SyncEntityType.ACTIVITY, id)
        val operation =
            if (existing.any { it.operation == OutboxOperation.CREATE } && requested != OutboxOperation.DELETE) {
                OutboxOperation.CREATE
            } else {
                requested
            }
        database.syncOutboxDao().deletePendingForEntity(SyncEntityType.ACTIVITY, id)
        database.syncOutboxDao().insert(
            SyncOutboxEntity(idGenerator.newId(), SyncEntityType.ACTIVITY, id, operation, 1, createdAt = now, updatedAt = now),
        )
    }

    private suspend fun mutate(
        id: UUID,
        operation: String,
        allowArchived: Boolean = false,
        block: suspend (ActivityEntity, Instant) -> AppResult<Unit>,
    ): AppResult<Unit> =
        withContext(dispatchers.io) {
            safely(operation) {
                val current = database.activityDao().findById(id)
                    ?: return@safely AppResult.Failure(AppError.NotFound("activity"))
                if (!allowArchived && current.metadata.deletedAt != null) return@safely conflict("archived_activity")
                block(current, clock.nowInstant())
            }
        }

    private suspend fun <T> safely(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        try {
            database.withTransaction { block() }
        } catch (error: InvalidSelection) {
            AppResult.Failure(AppError.Validation("parcelIds", error.message ?: "invalid"))
        } catch (error: InvalidExpense) {
            AppResult.Failure(AppError.Validation(error.field, error.code))
        } catch (error: Throwable) {
            AppResult.Failure(AppError.Storage(operation, error))
        }

    private fun pending(now: Instant) = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)

    private fun LocalMetadata.next(now: Instant) = copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private fun String?.normalized() = this?.trim()?.ifEmpty { null }

    private fun conflict(code: String): AppResult.Failure = AppResult.Failure(AppError.Conflict(code))

    private class InvalidSelection(message: String) : RuntimeException(message)

    private fun ActivityWithTargets.toDomain() =
        Activity(
            id = activity.id,
            workspaceId = activity.workspaceId,
            farmId = activity.farmId,
            campaignId = activity.campaignId,
            type = runCatching { ActivityType.valueOf(activity.type) }.getOrDefault(ActivityType.OTHER),
            status = activity.status,
            activityDate = activity.activityDate,
            description = activity.description,
            notes = activity.notes,
            targets = targets.map {
                ActivityParcelTarget(it.parcelId, it.parcelNameAtTarget, it.areaAffectedM2, it.notes)
            },
            detail = toDomainDetail(),
            version = activity.metadata.version,
        )

    /**
     * A typed detail belongs to exactly one Activity type.
     *
     * The rule is not that a detail is required — a draft may still be nothing but a
     * header — but that whatever detail is present matches the Activity. An irrigation
     * carrying fertilisation figures is rejected before anything is written, and the
     * numeric fields are checked for the one thing that is always wrong: a negative
     * amount of work, product, water or money.
     */
    private fun validateDetail(type: ActivityType, detail: ActivityDetail?): AppResult.Failure? {
        if (detail == null) return null
        if (detail.type != type) return AppResult.Failure(AppError.Validation("detail", "type_mismatch"))
        return when (detail) {
            is ActivityDetail.Pruning ->
                notNegative("workerCount", detail.workerCount?.toDouble())
                    ?: notNegative("hours", detail.hours)
            is ActivityDetail.Fertilization ->
                notNegative("totalQuantity", detail.totalQuantity)
                    ?: notNegative("doseValue", detail.doseValue)
            is ActivityDetail.Phytosanitary ->
                notNegative("totalQuantity", detail.totalQuantity)
                    ?: notNegative("doseValue", detail.doseValue)
            is ActivityDetail.Irrigation ->
                notNegative("durationMinutes", detail.durationMinutes?.toDouble())
                    ?: notNegative("volumeM3", detail.volumeM3)
                    ?: validatePrice(detail.price)
            // Severity and state are enums, so an invalid value cannot even be built.
            is ActivityDetail.Incident -> null
            is ActivityDetail.SoilWork -> null
            is ActivityDetail.Maintenance -> null
        }
    }

    private fun notNegative(field: String, value: Double?): AppResult.Failure? =
        if (value != null && value < 0) AppResult.Failure(AppError.Validation(field, "negative")) else null

    private fun validatePrice(price: IrrigationPrice?): AppResult.Failure? {
        if (price == null) return null
        if (price.currency.isBlank()) return AppResult.Failure(AppError.Validation("currency", "blank"))
        return notNegative("unitPriceMinor", price.unitPriceMinor?.toDouble())
            ?: notNegative("quantity", price.quantity)
            ?: notNegative("estimatedAmountMinor", price.estimatedAmountMinor?.toDouble())
    }

    /**
     * Writes the Activity's one typed detail, replacing whatever it had.
     *
     * Every detail table is cleared first because retyping an Activity is a legitimate
     * correction and the invariant is one matching detail: leaving the old row behind
     * would hide a record under a type that can no longer read it. This runs inside the
     * caller's transaction, so the header, the Parcel targets and the detail are one
     * mutation and one outbox intent (RC1-NORMATIVE-ADDENDUM D5).
     */
    private suspend fun replaceDetail(
        activityId: UUID,
        workspaceId: UUID,
        detail: ActivityDetail?,
        now: Instant,
    ) {
        val dao = database.activityDao()
        dao.deletePruning(activityId)
        dao.deleteFertilization(activityId)
        dao.deletePhytosanitary(activityId)
        dao.deleteSoilWork(activityId)
        dao.deleteIrrigation(activityId)
        dao.deleteIrrigationPrice(activityId)
        dao.deleteMaintenance(activityId)
        dao.deleteIncident(activityId)
        val metadata = pending(now)
        when (detail) {
            null -> Unit
            is ActivityDetail.Pruning -> dao.upsertPruning(
                PruningDetailEntity(
                    activityId = activityId,
                    workspaceId = workspaceId,
                    pruningType = detail.pruningType.normalized(),
                    workerCount = detail.workerCount,
                    hours = detail.hours,
                    residueManagement = detail.residueManagement.normalized(),
                    metadata = metadata,
                ),
            )
            is ActivityDetail.Fertilization -> dao.upsertFertilization(
                FertilizationDetailEntity(
                    activityId = activityId,
                    workspaceId = workspaceId,
                    productName = detail.productName.normalized(),
                    totalQuantity = detail.totalQuantity,
                    unit = detail.unit.normalized(),
                    doseValue = detail.doseValue,
                    doseUnit = detail.doseUnit.normalized(),
                    applicationMethod = detail.applicationMethod.normalized(),
                    metadata = metadata,
                ),
            )
            is ActivityDetail.Phytosanitary -> dao.upsertPhytosanitary(
                PhytosanitaryDetailEntity(
                    activityId = activityId,
                    workspaceId = workspaceId,
                    productName = detail.productName.normalized(),
                    activeSubstance = detail.activeSubstance.normalized(),
                    totalQuantity = detail.totalQuantity,
                    unit = detail.unit.normalized(),
                    doseValue = detail.doseValue,
                    doseUnit = detail.doseUnit.normalized(),
                    reason = detail.reason.normalized(),
                    equipmentText = detail.equipmentText.normalized(),
                    metadata = metadata,
                ),
            )
            is ActivityDetail.SoilWork -> dao.upsertSoilWork(
                SoilWorkDetailEntity(
                    activityId = activityId,
                    workspaceId = workspaceId,
                    workType = detail.workType.normalized(),
                    method = detail.method.normalized(),
                    metadata = metadata,
                ),
            )
            is ActivityDetail.Irrigation -> {
                dao.upsertIrrigation(
                    IrrigationDetailEntity(
                        activityId = activityId,
                        workspaceId = workspaceId,
                        durationMinutes = detail.durationMinutes,
                        volumeM3 = detail.volumeM3,
                        sectorText = detail.sectorText.normalized(),
                        systemText = detail.systemText.normalized(),
                        metadata = metadata,
                    ),
                )
                detail.price?.let { price ->
                    dao.upsertIrrigationPrice(
                        IrrigationPriceSnapshotEntity(
                            activityId = activityId,
                            workspaceId = workspaceId,
                            pricingBasis = price.basis,
                            unitPriceMinor = price.unitPriceMinor,
                            quantity = price.quantity,
                            estimatedAmountMinor = price.estimatedAmountMinor,
                            currency = price.currency,
                            priceDate = price.priceDate,
                            linkedExpenseId = price.linkedExpenseId,
                            notes = price.notes.normalized(),
                            metadata = metadata,
                        ),
                    )
                }
            }
            is ActivityDetail.Maintenance -> dao.upsertMaintenance(
                MaintenanceDetailEntity(
                    activityId = activityId,
                    workspaceId = workspaceId,
                    maintenanceType = detail.maintenanceType.normalized(),
                    assetText = detail.assetText.normalized(),
                    metadata = metadata,
                ),
            )
            is ActivityDetail.Incident -> dao.upsertIncident(
                IncidentDetailEntity(
                    activityId = activityId,
                    workspaceId = workspaceId,
                    category = detail.category.normalized(),
                    severity = detail.severity,
                    incidentStatus = detail.state,
                    actionTaken = detail.actionTaken.normalized(),
                    resolvedAt = detail.resolvedAt,
                    metadata = metadata,
                ),
            )
        }
    }

    private fun ActivityWithTargets.toDomainDetail(): ActivityDetail? {
        pruning?.let {
            return ActivityDetail.Pruning(it.pruningType, it.workerCount, it.hours, it.residueManagement)
        }
        fertilization?.let {
            return ActivityDetail.Fertilization(
                it.productName, it.totalQuantity, it.unit, it.doseValue, it.doseUnit, it.applicationMethod,
            )
        }
        phytosanitary?.let {
            return ActivityDetail.Phytosanitary(
                it.productName, it.activeSubstance, it.totalQuantity, it.unit,
                it.doseValue, it.doseUnit, it.reason, it.equipmentText,
            )
        }
        soilWork?.let { return ActivityDetail.SoilWork(it.workType, it.method) }
        irrigation?.let { row ->
            val price = irrigationPrice?.let {
                IrrigationPrice(
                    basis = it.pricingBasis,
                    priceDate = it.priceDate,
                    unitPriceMinor = it.unitPriceMinor,
                    quantity = it.quantity,
                    estimatedAmountMinor = it.estimatedAmountMinor,
                    currency = it.currency,
                    linkedExpenseId = it.linkedExpenseId,
                    notes = it.notes,
                )
            }
            return ActivityDetail.Irrigation(
                row.durationMinutes, row.volumeM3, row.sectorText, row.systemText, price,
            )
        }
        maintenance?.let { return ActivityDetail.Maintenance(it.maintenanceType, it.assetText) }
        incident?.let {
            return ActivityDetail.Incident(it.category, it.severity, it.incidentStatus, it.actionTaken, it.resolvedAt)
        }
        return null
    }

    private companion object {
        const val DEFAULT_CURRENCY = "EUR"
        val EDITABLE = setOf(ActivityStatus.DRAFT, ActivityStatus.PLANNED)
        val ARCHIVABLE = setOf(ActivityStatus.DRAFT, ActivityStatus.CANCELLED)
    }
}
