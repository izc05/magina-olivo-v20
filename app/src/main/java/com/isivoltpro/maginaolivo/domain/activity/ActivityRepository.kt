package com.isivoltpro.maginaolivo.domain.activity

import com.isivoltpro.maginaolivo.domain.agenda.ActivityPlanning
import com.isivoltpro.maginaolivo.domain.agenda.Reminder
import com.isivoltpro.maginaolivo.domain.agenda.ReminderRequest
import com.isivoltpro.maginaolivo.domain.machinery.ActivityMachine
import com.isivoltpro.maginaolivo.domain.machinery.MachineOption
import com.isivoltpro.maginaolivo.domain.machinery.MachineUseInput
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * RC1 activity types. Harvest is intentionally absent: it owns separate
 * quantity/distribution/delivery semantics (DATA-MODEL-RC1-FUTURE section 8).
 *
 * [HARVEST_DAY] is only an appointment in the agenda (Phase 16): it says when the crew
 * picks, never how many kilos — those always live in a Harvest record.
 */
enum class ActivityType {
    OBSERVATION,
    PRUNING,
    SOIL_WORK,
    FERTILIZATION,
    PHYTOSANITARY,
    IRRIGATION,
    MAINTENANCE,
    INCIDENT,
    OTHER,
    HARVEST_DAY,
}

/** One Parcel targeted by a single canonical Activity. Never a duplicated Activity. */
data class ActivityParcelTarget(
    val parcelId: UUID,
    val parcelName: String,
    val areaAffectedM2: Double? = null,
    val notes: String? = null,
)

data class ActivityParcelOption(val id: UUID, val name: String, val managedAreaM2: Double?)

data class Activity(
    val id: UUID,
    val workspaceId: UUID,
    val farmId: UUID?,
    val campaignId: UUID?,
    val type: ActivityType,
    val status: ActivityStatus,
    val activityDate: LocalDate,
    val description: String,
    val notes: String?,
    val targets: List<ActivityParcelTarget>,
    /** At most one, and always of this Activity's own type. Null for OBSERVATION and OTHER. */
    val detail: ActivityDetail? = null,
    val version: Long,
    /**
     * The convenience cost, read from its linked Expense (`RC1-NORMATIVE-ADDENDUM` D2).
     * It is never stored on the Activity and never summed next to the Expense ledger.
     */
    val costMinor: Long? = null,
    /** Machines used, if the farmer said so. Always optional (Phase 15). */
    val machines: List<ActivityMachine> = emptyList(),
    /** How the work is expected to go, if planned (Phase 16). */
    val planning: ActivityPlanning? = null,
    /** Its enabled local reminders (Phase 16). */
    val reminders: List<Reminder> = emptyList(),
    /** Inclusive end; null means this Activity happened only on [activityDate]. */
    val activityEndDate: LocalDate? = null,
)

/** One planned Activity as the Calendar lists it. */
data class AgendaEntry(
    val activityId: UUID,
    val farmId: UUID?,
    val farmName: String?,
    val type: ActivityType,
    val activityDate: LocalDate,
    val description: String,
    val parcelNames: List<String>,
    val planning: ActivityPlanning?,
    val reminders: List<Reminder>,
    val activityEndDate: LocalDate? = null,
)

data class NewActivity(
    val farmId: UUID,
    val campaignId: UUID? = null,
    val type: ActivityType,
    val activityDate: LocalDate,
    val description: String,
    val parcelIds: Set<UUID> = emptySet(),
    val notes: String? = null,
    /** A draft is resumable and may be saved with no Parcel selected yet. */
    val asDraft: Boolean = false,
    /**
     * Records made through Cuaderno's "Registrar hoy" are already performed: saved as Completed
     * unless the date is still ahead, a reminder is asked for, or it is a [ActivityType.HARVEST_DAY]
     * appointment (then it is planned work).
     */
    val completeImmediately: Boolean = false,
    /** The typed agronomic detail. It must match [type], and may be absent. */
    val detail: ActivityDetail? = null,
    /**
     * Optional cost, accepted only with work saved as done: it writes the one linked Expense, never
     * the Activity. With draft or planned work it is refused (#429). The app's forms send none (#416).
     */
    val costMinor: Long? = null,
    /** Optional machines; a child of the Activity aggregate, never required. */
    val machines: List<MachineUseInput> = emptyList(),
    /** Optional planning: hour, duration, people, crew (Phase 16). */
    val planning: ActivityPlanning? = null,
    /** Optional local reminders (Phase 16). */
    val reminders: List<ReminderRequest> = emptyList(),
    /**
     * Explicit affected surface per selected Parcel, in square metres.
     * Missing/null means “not confirmed yet”; the repository never infers the full Parcel silently.
     */
    val parcelAreasM2: Map<UUID, Double?> = emptyMap(),
    val activityEndDate: LocalDate? = null,
)

data class ActivityChanges(
    val type: ActivityType,
    val activityDate: LocalDate,
    val description: String,
    val parcelIds: Set<UUID>,
    val notes: String? = null,
    /**
     * The typed agronomic detail after the change.
     *
     * Retyping an Activity replaces its detail: the previous one is removed in the same
     * transaction, because an Activity carries exactly one detail and it always matches
     * the type. Passing null clears the detail.
     */
    val detail: ActivityDetail? = null,
    /**
     * Must stay null (#429): only draft or planned work is edited and it holds no money. A cost
     * linked before is left as it is and corrected on its own Expense.
     */
    val costMinor: Long? = null,
    /** The machines after the change; an empty list removes them. */
    val machines: List<MachineUseInput> = emptyList(),
    /** The planning after the change; null removes it. */
    val planning: ActivityPlanning? = null,
    /** The reminders after the change; an empty list turns them all off. */
    val reminders: List<ReminderRequest> = emptyList(),
    /**
     * Explicit affected surface per selected Parcel, in square metres.
     * Null means this caller is legacy/unaware and existing areas must be preserved.
     * A map containing parcelId -> null explicitly clears that Parcel's confirmed area.
     */
    val parcelAreasM2: Map<UUID, Double?>? = null,
    val activityEndDate: LocalDate? = null,
)

/**
 * Common engine for agricultural activities.
 *
 * Aggregate: `activities` is the root, `activity_parcels` are its children
 * (RC1-NORMATIVE-ADDENDUM D5). Every mutation commits locally first, bumps the
 * aggregate version and collapses into a single Activity outbox intent.
 *
 * Phase 10 adds the typed agronomic details as further children of the same aggregate:
 * they are written in the same transaction, share the Activity version and queue no
 * synchronization intent of their own.
 *
 * No cost field is exposed: RC1-NORMATIVE-ADDENDUM D2 supersedes `activities.cost_cents`
 * / `activities.currency` in favour of linked expenses, which remain the only
 * authoritative financial source.
 */
interface ActivityRepository {
    /** Planned work across every Farm, for the Calendar (Phase 16). */
    fun observeAgenda(): Flow<List<AgendaEntry>>

    /** Machines in use that an Activity can name. Empty is fine: machinery is optional. */
    fun observeSelectableMachines(): Flow<List<MachineOption>>

    fun observeSelectableParcels(farmId: UUID): Flow<List<ActivityParcelOption>>
    fun observeForFarm(farmId: UUID): Flow<List<Activity>>
    fun observeForParcel(parcelId: UUID): Flow<List<Activity>>
    fun observe(id: UUID): Flow<Activity?>
    suspend fun create(command: NewActivity): AppResult<UUID>
    suspend fun update(id: UUID, changes: ActivityChanges): AppResult<Unit>
    /**
     * #426: corrects a historical COMPLETED record without turning it back into planning.
     * Planning/reminders are preserved as history and money remains in the Expense ledger.
     */
    suspend fun correctCompleted(id: UUID, changes: ActivityChanges): AppResult<Unit>
    /** Promotes a resumable DRAFT to PLANNED once it targets at least one Parcel. */
    suspend fun plan(id: UUID): AppResult<Unit>
    suspend fun complete(id: UUID): AppResult<Unit>
    suspend fun cancel(id: UUID): AppResult<Unit>
    /** Reopens a COMPLETED or CANCELLED activity back to PLANNED, explicitly and audited. */
    suspend fun reopen(id: UUID): AppResult<Unit>
    suspend fun archive(id: UUID): AppResult<Unit>
}

/**
 * #429: money and work that is not done. Planned or draft work never counts a cost; a cost
 * typed before 1.0 on work that would stop being done is reviewed on its own Gasto first.
 */
object ActivityCostRules {
    /** Validation code on `costMinor`: a cost sent with work that is not done. */
    const val NOT_DONE_WORK = "not_done_work"

    /** Conflict code: the work holds a counted cost the person has to review before this move. */
    const val COST_TO_REVIEW = "activity_cost_posted"

    /**
     * #437 conflict code: Gastos of their own still point at the work, so it cannot be archived;
     * it stays (cancelled) and reachable, and they are never removed or unlinked by the archive.
     */
    const val LINKED_EXPENSES = "activity_has_expenses"

    /**
     * #441 conflict code: a Parcel the edit would drop from the work still carries a Gasto tied to
     * that work. Gastos never move with a correction of the work; the person reviews them first.
     */
    const val PARCEL_HAS_EXPENSES = "activity_parcel_has_expenses"
}
