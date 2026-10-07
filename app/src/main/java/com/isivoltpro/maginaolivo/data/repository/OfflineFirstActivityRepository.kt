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
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.local.model.ActivityWithTargets
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.OutboxOperation
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.data.local.model.SyncStatus
import com.isivoltpro.maginaolivo.data.reminder.allocateReminderRequestCode
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityChanges
import com.isivoltpro.maginaolivo.domain.activity.ActivityCostRules
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetailPatch
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPrice
import com.isivoltpro.maginaolivo.domain.activity.NewActivity
import com.isivoltpro.maginaolivo.domain.activity.AgendaEntry
import com.isivoltpro.maginaolivo.data.local.entity.ActivityPlanningEntity
import com.isivoltpro.maginaolivo.data.local.entity.ReminderEntity
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferences
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferencesSource
import com.isivoltpro.maginaolivo.domain.agenda.ActivityPlanning
import com.isivoltpro.maginaolivo.domain.agenda.Reminder
import com.isivoltpro.maginaolivo.domain.agenda.ReminderKind
import com.isivoltpro.maginaolivo.domain.agenda.ReminderReconciler
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRequest
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRules
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import java.util.UUID
import com.isivoltpro.maginaolivo.data.local.entity.ActivityMachineEntity
import com.isivoltpro.maginaolivo.domain.machinery.ActivityMachine
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import com.isivoltpro.maginaolivo.domain.machinery.MachineOption
import com.isivoltpro.maginaolivo.domain.machinery.MachineRules
import com.isivoltpro.maginaolivo.domain.machinery.MachineUseInput
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
    /** Rebuilds device alarms after planned work changes. Null where no alarms exist (tests). */
    private val reminderReconciler: ReminderReconciler? = null,
    /** Phase 21B: Perfil → Avisos (the day-before hour). Without one, the rules' default hour. */
    private val reminderPreferences: ReminderPreferencesSource = ReminderPreferencesSource {
        ReminderPreferences(previousDayTime = ReminderRules.PREVIOUS_DAY_TIME)
    },
    /** The wall clock reminders are read in: the phone's, because the phone rings them. */
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val workspaceRepository: WorkspaceRepository? = null,
) : ActivityRepository {
    private val workspaceScope = ActiveWorkspaceScope(database, workspaceRepository)

    override fun observeSelectableParcels(farmId: UUID): Flow<List<ActivityParcelOption>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            val farm = database.farmDao().findById(farmId)
            if (farm == null || farm.workspaceId != active || farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) {
                emit(emptyList())
                return@flow
            }
            emitAll(
                database.parcelDao().observeActive(farmId).map { rows ->
                    rows.map { ActivityParcelOption(it.parcel.id, it.parcel.displayName, it.parcel.managedAreaM2) }
                },
            )
        }.flowOn(dispatchers.io)

    override fun observeForFarm(farmId: UUID): Flow<List<Activity>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            val farm = database.farmDao().findById(farmId)
            if (farm == null || farm.workspaceId != active) {
                emit(emptyList())
                return@flow
            }
            emitAll(database.activityDao().observeForFarm(farmId).map { rows -> rows.map { it.toDomain() } })
        }.flowOn(dispatchers.io)

    override fun observeForParcel(parcelId: UUID): Flow<List<Activity>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            val parcel = database.parcelDao().findById(parcelId)
            if (parcel == null || parcel.workspaceId != active) {
                emit(emptyList())
                return@flow
            }
            emitAll(database.activityDao().observeForParcel(parcelId).map { rows -> rows.map { it.toDomain() } })
        }.flowOn(dispatchers.io)

    private val ledger = ExpenseLedgerWriter(database, idGenerator)

    override fun observe(id: UUID): Flow<Activity?> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(null)
                is AppResult.Success -> result.value
            }
            emitAll(
                combine(
                    database.activityDao().observeWithTargets(id),
                    database.expenseDao().observeForActivity(id),
                    database.machineDao().observeForActivity(id),
                ) { row, expenses, uses ->
                    row?.takeIf { it.activity.workspaceId == active }?.toDomain()?.copy(
                        costMinor = expenses.firstOrNull { it.origin == ExpenseOrigin.ACTIVITY_COST.name }?.amountMinor,
                        machines = machinesOf(uses),
                    )
                },
            )
        }.flowOn(dispatchers.io)

    override fun observeAgenda(): Flow<List<AgendaEntry>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            emitAll(
                combine(
                    database.agendaDao().observePlannedForWorkspace(active),
                    database.agendaDao().observeFarmNamesForWorkspace(active),
                ) { rows, farms ->
                    val names = farms.associate { it.id to it.name }
                    rows.map { row ->
                        val activity = row.toDomain()
                        AgendaEntry(
                            activityId = activity.id,
                            farmId = activity.farmId,
                            farmName = activity.farmId?.let(names::get),
                            type = activity.type,
                            activityDate = activity.activityDate,
                            description = activity.description,
                            parcelNames = activity.targets.map { it.parcelName },
                            planning = activity.planning,
                            reminders = activity.reminders,
                            activityEndDate = activity.activityEndDate,
                        )
                    }
                },
            )
        }.flowOn(dispatchers.io)

    override fun observeSelectableMachines(): Flow<List<MachineOption>> =
        flow {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@flow emit(emptyList())
                is AppResult.Success -> result.value
            }
            emitAll(
                database.machineDao().observeByStatus(active, MACHINE_ACTIVE).map { rows ->
                    rows.map { MachineOption(it.id, it.name, it.category.toMachineCategory()) }
                },
            )
        }.flowOn(dispatchers.io)

    private suspend fun machinesOf(uses: List<ActivityMachineEntity>): List<ActivityMachine> {
        if (uses.isEmpty()) return emptyList()
        val machines = database.machineDao().findAll(uses.map { it.machineId }).associateBy { it.id }
        return uses.mapNotNull { use ->
            val machine = machines[use.machineId] ?: return@mapNotNull null
            ActivityMachine(
                machineId = machine.id,
                name = machine.name,
                category = machine.category.toMachineCategory(),
                startHours = use.startHours,
                endHours = use.endHours,
                usageHours = use.usageHours,
                archived = machine.status != MACHINE_ACTIVE,
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun String.toMachineCategory() =
        MachineCategory.entries.firstOrNull { it.name == this } ?: MachineCategory.OTHER

    /**
     * Rewrites the Activity's machines inside its transaction (D5: children of the
     * Activity aggregate, no intent of their own). A retired machine can no longer be
     * chosen, but one the Activity already named stays, so editing old work never drops it.
     */
    private suspend fun replaceMachines(activityId: UUID, workspaceId: UUID, uses: List<MachineUseInput>) {
        val kept = database.machineDao().listForActivity(activityId).map { it.machineId }.toSet()
        val rows = uses.map { use ->
            val machine = database.machineDao().findById(use.machineId)
            if (machine == null || machine.metadata.deletedAt != null || machine.workspaceId != workspaceId) {
                throw InvalidMachine("machine_not_found")
            }
            if (machine.status != MACHINE_ACTIVE && machine.id !in kept) throw InvalidMachine("archived_machine")
            ActivityMachineEntity(activityId, machine.id, use.startHours, use.endHours, use.usageHours)
        }
        database.machineDao().deleteForActivity(activityId)
        if (rows.isNotEmpty()) database.machineDao().insertUses(rows)
    }

    override suspend fun create(command: NewActivity): AppResult<UUID> {
        val description = command.description.trim()
        if (description.isEmpty()) return AppResult.Failure(AppError.Validation("description", "blank"))
        if (!command.asDraft && command.parcelIds.isEmpty()) {
            return AppResult.Failure(AppError.Validation("parcelIds", "empty"))
        }
        validateDateRange(command.activityDate, command.activityEndDate)?.let { return it }
        validateDetail(command.type, command.detail)?.let { return it }
        notNegative("costMinor", command.costMinor?.toDouble())?.let { return it }
        MachineRules.validateUses(command.machines)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        ReminderRules.validate(command.planning, command.reminders)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        return withContext(dispatchers.io) {
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@withContext result
                is AppResult.Success -> result.value
            }
            safely("create_activity") {
                val farm = database.farmDao().findById(command.farmId)
                    ?: return@safely AppResult.Failure(AppError.NotFound("farm"))
                if (farm.workspaceId != active) {
                    return@safely AppResult.Failure(AppError.Validation("workspaceId", "context_mismatch"))
                }
                if (farm.status != FarmStatus.ACTIVE || farm.metadata.deletedAt != null) {
                    return@safely AppResult.Failure(AppError.Conflict("archived_farm"))
                }
                // #619: a completed agricultural fact uses the Workspace calendar, never the
                // device's temporary timezone. Pending work may still be planned in the future.
                if (
                    command.completeImmediately && !command.asDraft &&
                    command.type != ActivityType.HARVEST_DAY
                ) {
                    val today = database.todayForWorkspace(farm.workspaceId, clock)
                    if (command.activityDate.isAfter(today)) {
                        return@safely AppResult.Failure(AppError.Validation("activityDate", "future_completed_work"))
                    }
                    if (command.activityEndDate?.isAfter(today) == true) {
                        return@safely AppResult.Failure(AppError.Validation("activityEndDate", "future_completed_work"))
                    }
                }
                if (command.type == ActivityType.HARVEST_DAY) {
                    validateHarvestDayContext(
                        workspaceId = farm.workspaceId,
                        farmId = farm.id,
                        campaignId = command.campaignId,
                        date = command.activityDate,
                        parcelIds = command.parcelIds,
                    )?.let { return@safely it }
                }
                command.campaignId?.let { campaignId ->
                    val campaign = database.campaignDao().findById(campaignId)
                        ?: return@safely AppResult.Failure(AppError.Validation("campaignId", "not_found"))
                    if (campaign.metadata.deletedAt != null) {
                        return@safely AppResult.Failure(AppError.Validation("campaignId", "archived"))
                    }
                    if (campaign.workspaceId != active || campaign.farmId != farm.id) {
                        return@safely AppResult.Failure(AppError.Validation("campaignId", "context_mismatch"))
                    }
                }
                val status = when {
                    command.asDraft -> ActivityStatus.DRAFT
                    // "Registrar hoy" records work already done (a date ahead was refused
                    // above). A reminder asked for, or a harvest-day appointment (read by
                    // the agenda only while planned) stays planned, whatever route saved it.
                    command.completeImmediately &&
                        command.type != ActivityType.HARVEST_DAY &&
                        command.reminders.isEmpty() -> ActivityStatus.COMPLETED
                    else -> ActivityStatus.PLANNED
                }
                // #429: work not done yet never counts money as spent, whichever caller sends a cost.
                if (status != ActivityStatus.COMPLETED && command.costMinor.counts()) {
                    return@safely AppResult.Failure(AppError.Validation("costMinor", ActivityCostRules.NOT_DONE_WORK))
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
                        status = status,
                        description = description,
                        notes = command.notes.normalized(),
                        metadata = pending(now),
                        activityEndDate = command.activityEndDate,
                    ),
                )
                replaceDetail(id, farm.workspaceId, command.detail, now)
                replaceTargets(id, command.parcelIds, command.parcelAreasM2, now)
                replaceMachines(id, farm.workspaceId, command.machines)
                replacePlanning(id, farm.workspaceId, command.planning, now)
                replaceReminders(id, command.reminders, now)
                enqueue(id, OutboxOperation.CREATE, now)
                if (status == ActivityStatus.COMPLETED) postCost(id, command.costMinor, now)
                AppResult.Success(id)
            }
        }.alsoReconcile()
    }

    override suspend fun update(id: UUID, changes: ActivityChanges): AppResult<Unit> {
        val description = changes.description.trim()
        if (description.isEmpty()) return AppResult.Failure(AppError.Validation("description", "blank"))
        validateDateRange(changes.activityDate, changes.activityEndDate)?.let { return it }
        validateDetail(changes.type, changes.detail)?.let { return it }
        notNegative("costMinor", changes.costMinor?.toDouble())?.let { return it }
        // #429: only DRAFT and PLANNED work is editable, and neither carries money; a cost typed
        // before 1.0 is corrected on its own Gasto, so a null here leaves it exactly as it is.
        if (changes.costMinor.counts()) return AppResult.Failure(AppError.Validation("costMinor", ActivityCostRules.NOT_DONE_WORK))
        MachineRules.validateUses(changes.machines)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        ReminderRules.validate(changes.planning, changes.reminders)?.let { return AppResult.Failure(AppError.Validation(it.field, it.code)) }
        return mutate(id, "update_activity") { current, now ->
            if (current.status !in EDITABLE) return@mutate conflict("protected_activity")
            if (current.status == ActivityStatus.PLANNED && changes.parcelIds.isEmpty()) {
                return@mutate AppResult.Failure(AppError.Validation("parcelIds", "empty"))
            }
            if (changes.type == ActivityType.HARVEST_DAY) {
                val farmId = current.farmId
                    ?: return@mutate AppResult.Failure(AppError.Validation("farmId", "not_found"))
                validateHarvestDayContext(
                    workspaceId = current.workspaceId,
                    farmId = farmId,
                    campaignId = current.campaignId,
                    date = changes.activityDate,
                    parcelIds = changes.parcelIds,
                )?.let { return@mutate it }
            }
            // #441: a Parcel a Gasto of this work names is not dropped from the work (also when it
            // becomes «Toda la finca»). Adding Parcels, changing surface, retyping or redating never
            // touches Gastos.
            val currentTargets = database.activityDao().listTargets(id)
            val dropped = currentTargets.map { it.parcelId }.toSet() - changes.parcelIds
            if (dropped.isNotEmpty() && database.expenseDao().listForActivity(id).any { it.parcelId in dropped }) {
                return@mutate conflict(ActivityCostRules.PARCEL_HAS_EXPENSES)
            }
            database.activityDao().upsert(
                current.copy(
                    type = changes.type.name,
                    activityDate = changes.activityDate,
                    activityEndDate = changes.activityEndDate,
                    description = description,
                    notes = changes.notes.normalized(),
                    metadata = current.metadata.next(now),
                ),
            )
            // #453: the same type keeps what its form never carries; a new type replaces it all.
            val previous = database.activityDao().findWithTargets(id)?.toDomainDetail()
            replaceDetail(id, current.workspaceId, ActivityDetailPatch.keepingHidden(previous, changes.detail), now)
            val areas = changes.parcelAreasM2 ?: currentTargets
                .filter { it.parcelId in changes.parcelIds }
                .associate { it.parcelId to it.areaAffectedM2 }
            replaceTargets(id, changes.parcelIds, areas, now)
            replaceMachines(id, current.workspaceId, changes.machines)
            replacePlanning(id, current.workspaceId, changes.planning, now)
            replaceReminders(id, changes.reminders, now)
            enqueue(id, OutboxOperation.UPDATE, now)
            AppResult.Success(Unit)
        }.alsoReconcile()
    }

    override suspend fun plan(id: UUID): AppResult<Unit> =
        transition(id, setOf(ActivityStatus.DRAFT), ActivityStatus.PLANNED, "plan_activity", requireTargets = true, costMustBeReviewed = true)

    // Completing makes a legacy cost coherent again, so it is the one move a counted cost never blocks.
    override suspend fun complete(id: UUID): AppResult<Unit> =
        transition(id, setOf(ActivityStatus.PLANNED), ActivityStatus.COMPLETED, "complete_activity", requireTargets = true, costMustBeReviewed = false)

    override suspend fun cancel(id: UUID): AppResult<Unit> =
        transition(id, setOf(ActivityStatus.DRAFT, ActivityStatus.PLANNED), ActivityStatus.CANCELLED, "cancel_activity", requireTargets = false, costMustBeReviewed = true)

    override suspend fun reopen(id: UUID): AppResult<Unit> =
        transition(id, setOf(ActivityStatus.COMPLETED, ActivityStatus.CANCELLED), ActivityStatus.PLANNED, "reopen_activity", requireTargets = true, costMustBeReviewed = true)

    override suspend fun archive(id: UUID): AppResult<Unit> = mutate(id, "archive_activity", allowArchived = true) { current, now ->
        if (current.metadata.deletedAt != null) return@mutate AppResult.Success(Unit)
        if (current.status !in ARCHIVABLE) return@mutate conflict("protected_activity")
        // #429: a counted cost is never dropped in silence with the work; the person decides on its Gasto.
        if (database.expenseDao().findActivityCost(id) != null) return@mutate conflict(ActivityCostRules.COST_TO_REVIEW)
        // #437: money of its own never loses the work it points at, nor goes with it.
        if (database.expenseDao().countLinkedToActivity(id) > 0) return@mutate conflict(ActivityCostRules.LINKED_EXPENSES)
        database.activityDao().upsert(current.copy(metadata = current.metadata.next(now).copy(deletedAt = now)))
        enqueue(id, OutboxOperation.DELETE, now)
        AppResult.Success(Unit)
    }.alsoReconcile()

    private suspend fun transition(
        id: UUID,
        from: Set<ActivityStatus>,
        to: ActivityStatus,
        operation: String,
        requireTargets: Boolean,
        costMustBeReviewed: Boolean,
    ) = mutate(id, operation) { current, now ->
        if (current.status !in from) return@mutate conflict("illegal_activity_transition")
        // #429: work that would end up not done cannot keep a counted cost. It is not undone or
        // converted here: the person keeps it as its own Gasto or stops counting it, then moves on.
        if (costMustBeReviewed && database.expenseDao().findActivityCost(id) != null) {
            return@mutate conflict(ActivityCostRules.COST_TO_REVIEW)
        }
        if (requireTargets && database.activityDao().countTargets(id) == 0) {
            return@mutate AppResult.Failure(AppError.Validation("parcelIds", "empty"))
        }
        if (to == ActivityStatus.PLANNED && current.type == ActivityType.HARVEST_DAY.name) {
            val farmId = current.farmId
                ?: return@mutate AppResult.Failure(AppError.Validation("farmId", "not_found"))
            validateHarvestDayContext(
                workspaceId = current.workspaceId,
                farmId = farmId,
                campaignId = current.campaignId,
                date = current.activityDate,
                parcelIds = database.activityDao().listTargets(id).map { it.parcelId }.toSet(),
            )?.let { return@mutate it }
        }
        if (
            to == ActivityStatus.COMPLETED &&
            current.type != ActivityType.HARVEST_DAY.name &&
            current.activityDate.isAfter(database.todayForWorkspace(current.workspaceId, clock))
        ) {
            return@mutate AppResult.Failure(AppError.Validation("activityDate", "future_completed_work"))
        }
        database.activityDao().upsert(current.copy(status = to, metadata = current.metadata.next(now)))
        enqueue(id, OutboxOperation.UPDATE, now)
        AppResult.Success(Unit)
    }.alsoReconcile()

    /**
     * Alarms follow the committed rows, never the other way round: a failed reconcile
     * leaves the write intact and is repeated at the next start.
     */
    private suspend fun <T> AppResult<T>.alsoReconcile(): AppResult<T> {
        if (this is AppResult.Success) runCatching { reminderReconciler?.reconcile() }
        return this
    }

    /**
     * #482 block A: a harvest-day appointment always belongs to one explicit Campaign.
     * Dates never infer identity, and its Parcel targets must be Campaign snapshots.
     */
    private suspend fun validateHarvestDayContext(
        workspaceId: UUID,
        farmId: UUID,
        campaignId: UUID?,
        date: java.time.LocalDate,
        parcelIds: Set<UUID>,
    ): AppResult.Failure? {
        if (campaignId == null) {
            return AppResult.Failure(AppError.Validation("campaignId", "required_for_harvest_day"))
        }
        val campaign = database.campaignDao().findById(campaignId)
            ?: return AppResult.Failure(AppError.Validation("campaignId", "not_found"))
        if (campaign.metadata.deletedAt != null) {
            return AppResult.Failure(AppError.Validation("campaignId", "archived"))
        }
        if (campaign.workspaceId != workspaceId || campaign.farmId != farmId) {
            return AppResult.Failure(AppError.Validation("campaignId", "context_mismatch"))
        }
        if (campaign.status == CampaignStatus.CLOSED) {
            return AppResult.Failure(AppError.Validation("campaignId", "closed"))
        }
        if (date.isBefore(campaign.startDate)) {
            return AppResult.Failure(AppError.Validation("activityDate", "before_campaign"))
        }
        if (campaign.endDate != null && date.isAfter(campaign.endDate)) {
            return AppResult.Failure(AppError.Validation("activityDate", "after_campaign"))
        }
        val campaignParcels = database.campaignDao().listSnapshots(campaignId).map { it.parcelId }.toSet()
        if (!campaignParcels.containsAll(parcelIds)) {
            return AppResult.Failure(AppError.Validation("parcelIds", "not_in_campaign"))
        }
        return null
    }

    /** Planning is a child of the Activity aggregate (D5): same transaction, same intent. */
    private suspend fun replacePlanning(activityId: UUID, workspaceId: UUID, planning: ActivityPlanning?, now: Instant) {
        val dao = database.agendaDao()
        if (planning == null || planning.isEmpty) {
            dao.deletePlanning(activityId)
            return
        }
        dao.upsertPlanning(
            ActivityPlanningEntity(
                activityId = activityId,
                workspaceId = workspaceId,
                plannedStartTime = planning.startTime?.format(TIME),
                expectedDurationMinutes = planning.expectedDurationMinutes,
                expectedPeopleCount = planning.expectedPeopleCount,
                crewText = planning.crewText.normalized(),
                metadata = pending(now),
            ),
        )
    }

    /**
     * Reminders are children of the Activity aggregate too. A reminder of the same kind
     * keeps its row — and so its alarm slot — when the date or hour moves; one the farmer
     * dropped is switched off rather than deleted, so the next reconcile can still find
     * and cancel its alarm.
     */
    private suspend fun replaceReminders(activityId: UUID, requests: List<ReminderRequest>, now: Instant) {
        val dao = database.agendaDao()
        val activity = database.activityDao().findById(activityId) ?: error("activity missing")
        val zoneId = zone()
        val startTime = dao.findPlannedStartTime(activityId)?.let(LocalTime::parse)
        val remaining = dao.listForOwner(OWNER_ACTIVITY, activityId).toMutableList()
        // #573: a requestCode identifies a PendingIntent on this device. It must be unique across
        // every Reminder row, including disabled/fired rows whose notification may still exist.
        val usedNotificationIds = dao.listAllReminders().mapTo(mutableSetOf()) { it.localNotificationId }
        val previousDayTime = reminderPreferences.current().previousDayTime
        val rows = requests.map { request ->
            val trigger = ReminderRules.triggerAt(activity.activityDate, startTime, request, zoneId, previousDayTime)
            val match = remaining.firstOrNull {
                it.enabled && it.kind == request.kind.name && (request.kind != ReminderKind.CUSTOM || it.triggerAt == trigger)
            } ?: remaining.firstOrNull { it.enabled && it.kind == request.kind.name }
            if (match != null) {
                remaining.remove(match)
                if (match.triggerAt == trigger) {
                    match
                } else {
                    match.copy(triggerAt = trigger, firedAt = passed(trigger, now), metadata = match.metadata.next(now))
                }
            } else {
                val id = idGenerator.newId()
                val requestCode = allocateReminderRequestCode(id, usedNotificationIds)
                usedNotificationIds += requestCode
                ReminderEntity(
                    id = id,
                    workspaceId = activity.workspaceId,
                    ownerType = OWNER_ACTIVITY,
                    ownerId = activityId,
                    triggerAt = trigger,
                    kind = request.kind.name,
                    localNotificationId = requestCode,
                    firedAt = passed(trigger, now),
                    metadata = pending(now),
                )
            }
        }
        val dropped = remaining.filter { it.enabled }.map { it.copy(enabled = false, metadata = it.metadata.next(now)) }
        if (rows.isNotEmpty() || dropped.isNotEmpty()) dao.upsertReminders(rows + dropped)
    }

    /** A moment that has already gone by when saved is never rung late: it counts as spent. */
    private fun passed(trigger: Instant, now: Instant): Instant? = if (trigger.isAfter(now)) null else now

    /**
     * `RC1-NORMATIVE-ADDENDUM` D2 as narrowed by #416/#429: a cost sent with work recorded as
     * done becomes its one linked ACTIVITY_COST Expense, in this same transaction. Nothing else
     * writes one: planned or draft work holds no money, and an existing cost is corrected on its
     * own Gasto, never from the work.
     */
    private suspend fun postCost(activityId: UUID, costMinor: Long?, now: Instant) {
        if (!costMinor.counts()) return
        val activity = database.activityDao().findById(activityId) ?: return
        check(activity.status == ActivityStatus.COMPLETED) { "cost on work not done" }
        val draft = ExpenseDraft(
            expenseDate = activity.activityDate,
            concept = activity.description,
            category = costCategory(activity.type),
            amountMinor = costMinor!!,
            currency = DEFAULT_CURRENCY,
            farmId = activity.farmId,
            // #433: the cost of a work follows the work's Campaign; general work stays general.
            campaignId = activity.campaignId,
            activityId = activityId,
        )
        ledger.insert(activity.workspaceId, draft, ExpenseStatus.POSTED, ExpenseOrigin.ACTIVITY_COST, now)
    }

    private fun Long?.counts() = this != null && this != 0L

    private fun costCategory(type: String): ExpenseCategory = when (runCatching { ActivityType.valueOf(type) }.getOrNull()) {
        ActivityType.FERTILIZATION, ActivityType.PHYTOSANITARY -> ExpenseCategory.PRODUCTS
        ActivityType.IRRIGATION -> ExpenseCategory.IRRIGATION
        ActivityType.PRUNING -> ExpenseCategory.LABOR
        ActivityType.SOIL_WORK -> ExpenseCategory.MACHINERY
        ActivityType.MAINTENANCE -> ExpenseCategory.REPAIR
        else -> ExpenseCategory.OTHER
    }

    private suspend fun replaceTargets(
        activityId: UUID,
        parcelIds: Set<UUID>,
        parcelAreasM2: Map<UUID, Double?>,
        now: Instant,
    ) {
        val activity = database.activityDao().findById(activityId) ?: error("activity missing")
        val farmId = activity.farmId ?: throw InvalidSelection("activity_without_farm")
        if (parcelAreasM2.keys.any { it !in parcelIds }) throw InvalidParcelArea("area_for_unselected_parcel")

        val dao = database.activityDao()
        val existingByParcel = dao.listTargets(activityId).associateBy { it.parcelId }
        val keptOrAdded = parcelIds.sortedBy(UUID::toString).map { parcelId ->
            val existing = existingByParcel[parcelId]
            val parcel = database.parcelDao().findById(parcelId) ?: throw InvalidSelection("parcel_not_found")
            // #440: a target the work already has is history. Its Parcel may since have been
            // archived or moved; correcting another field of the work never re-checks it against
            // today's catalogue. Only a Parcel being added must be an active Parcel of the Farm.
            if (existing == null) {
                val membership = database.parcelDao().findCurrentMembership(parcelId)
                if (membership?.farmId != farmId || parcel.status != RecordStatus.ACTIVE ||
                    parcel.metadata.deletedAt != null || parcel.workspaceId != activity.workspaceId
                ) {
                    throw InvalidSelection("parcel_not_in_farm")
                }
            }

            val affected = parcelAreasM2[parcelId]
            if (affected != null && (!affected.isFinite() || affected <= 0.0)) {
                throw InvalidParcelArea("invalid_area")
            }

            val changesHistoricalArea = existing == null || !sameArea(existing.areaAffectedM2, affected)
            if (affected != null && parcel.managedAreaM2 != null &&
                affected > parcel.managedAreaM2 + AREA_EPSILON_M2 && changesHistoricalArea
            ) {
                throw InvalidParcelArea("area_exceeds_parcel")
            }

            if (existing == null) {
                ActivityParcelTargetEntity(
                    id = idGenerator.newId(),
                    workspaceId = activity.workspaceId,
                    activityId = activityId,
                    parcelId = parcel.id,
                    parcelNameAtTarget = parcel.displayName,
                    // #546: selecting a Parcel never asserts that all of it was affected.
                    areaAffectedM2 = affected,
                    metadata = pending(now),
                )
            } else if (sameArea(existing.areaAffectedM2, affected)) {
                // #444: editing another field never refreshes a historical Parcel snapshot or child id.
                existing
            } else {
                existing.copy(
                    areaAffectedM2 = affected,
                    metadata = existing.metadata.next(now),
                )
            }
        }

        // Remove only relations the farmer actually dropped. Every surviving target keeps its
        // stable child id, snapshot name and notes.
        (existingByParcel.keys - parcelIds).forEach { parcelId ->
            dao.deleteTarget(activityId, parcelId)
        }
        if (keptOrAdded.isNotEmpty()) dao.upsertTargets(keptOrAdded)
    }

    private fun sameArea(left: Double?, right: Double?): Boolean =
        when {
            left == null || right == null -> left == right
            else -> kotlin.math.abs(left - right) <= AREA_EPSILON_M2
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
            val active = when (val result = workspaceScope.resolve()) {
                is AppResult.Failure -> return@withContext result
                is AppResult.Success -> result.value
            }
            safely(operation) {
                val current = database.activityDao().findById(id)
                    ?: return@safely AppResult.Failure(AppError.NotFound("activity"))
                if (current.workspaceId != active) {
                    return@safely AppResult.Failure(AppError.Validation("workspaceId", "context_mismatch"))
                }
                if (!allowArchived && current.metadata.deletedAt != null) return@safely conflict("archived_activity")
                block(current, clock.nowInstant())
            }
        }

    private suspend fun <T> safely(operation: String, block: suspend () -> AppResult<T>): AppResult<T> =
        try {
            database.withTransaction { block() }
        } catch (error: InvalidSelection) {
            AppResult.Failure(AppError.Validation("parcelIds", error.message ?: "invalid"))
        } catch (error: InvalidParcelArea) {
            AppResult.Failure(AppError.Validation("parcelAreasM2", error.message ?: "invalid"))
        } catch (error: InvalidExpense) {
            AppResult.Failure(AppError.Validation(error.field, error.code))
        } catch (error: InvalidMachine) {
            AppResult.Failure(AppError.Validation("machines", error.message ?: "invalid"))
        } catch (error: Throwable) {
            AppResult.Failure(AppError.Storage(operation, error))
        }

    private fun pending(now: Instant) = LocalMetadata(now, now, syncStatus = SyncStatus.PENDING)

    private fun LocalMetadata.next(now: Instant) = copy(updatedAt = now, version = version + 1, syncStatus = SyncStatus.PENDING)

    private fun String?.normalized() = this?.trim()?.ifEmpty { null }

    private fun conflict(code: String): AppResult.Failure = AppResult.Failure(AppError.Conflict(code))

    private class InvalidSelection(message: String) : RuntimeException(message)

    private class InvalidParcelArea(message: String) : RuntimeException(message)

    private class InvalidMachine(message: String) : RuntimeException(message)

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
            planning = planning?.let {
                ActivityPlanning(
                    startTime = it.plannedStartTime?.let { time -> runCatching { LocalTime.parse(time) }.getOrNull() },
                    expectedDurationMinutes = it.expectedDurationMinutes,
                    expectedPeopleCount = it.expectedPeopleCount,
                    crewText = it.crewText,
                )
            },
            reminders = reminders.filter { it.enabled }.sortedBy { it.triggerAt }.mapNotNull { row ->
                val kind = runCatching { ReminderKind.valueOf(row.kind) }.getOrNull() ?: return@mapNotNull null
                Reminder(
                    id = row.id,
                    kind = kind,
                    triggerAt = row.triggerAt,
                    enabled = row.enabled,
                    firedAt = row.firedAt,
                    customAt = if (kind == ReminderKind.CUSTOM) row.triggerAt.atZone(zone()).toLocalDateTime() else null,
                )
            },
            activityEndDate = activity.activityEndDate,
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

    private fun validateDateRange(start: LocalDate, end: LocalDate?): AppResult.Failure? =
        if (end != null && end.isBefore(start)) {
            AppResult.Failure(AppError.Validation("activityEndDate", "before_start"))
        } else {
            null
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
        const val AREA_EPSILON_M2 = 0.01
        const val DEFAULT_CURRENCY = "EUR"
        val EDITABLE = setOf(ActivityStatus.DRAFT, ActivityStatus.PLANNED)
        val ARCHIVABLE = setOf(ActivityStatus.DRAFT, ActivityStatus.CANCELLED)
        const val MACHINE_ACTIVE = "ACTIVE"
        const val OWNER_ACTIVITY = "ACTIVITY"
        val TIME: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    }
}
