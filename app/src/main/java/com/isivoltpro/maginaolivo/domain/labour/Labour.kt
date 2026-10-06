package com.isivoltpro.maginaolivo.domain.labour

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Phase 19D: how one person's day is counted. */
enum class LabourUnit { FULL_DAY, HALF_DAY, HOURS }

/** A reusable person or alias. Not a payroll or HR record. */
data class Worker(val id: UUID, val name: String)

/**
 * One labour line of a Jornada: a named person (`quantity` 1) or a quick count of people
 * ("5 jornales") with no names. [minutes] is per person and only for [LabourUnit.HOURS].
 */
data class LabourEntry(
    val id: UUID,
    val harvestId: UUID,
    val workerId: UUID?,
    val workerName: String?,
    val quantity: Int,
    val unit: LabourUnit,
    val minutes: Int?,
    val version: Long,
    /** CR-012 contract: historical applied rate; legacy rows have none. */
    val appliedRate: LabourRateSnapshot? = null,
)

/** Several people, one unit, one save. */
data class CrewDraft(
    val harvestId: UUID, val workerIds: List<UUID>, val unit: LabourUnit, val minutes: Int? = null,
    val appliedRate: LabourRateSnapshot? = null,
    val initialPayments: List<LabourPayment> = emptyList(),
    /**
     * #449: the farmer says the price is not known yet. The line is saved without a price (never
     * 0 € and never the usual rate filled in silently) and nothing can be paid on it yet.
     */
    val priceUnknown: Boolean = false,
)

/** Historical anonymous attendance; new recollection must use named people. */
data class CountDraft(val harvestId: UUID, val count: Int, val unit: LabourUnit, val minutes: Int? = null)

data class LabourChange(val quantity: Int, val unit: LabourUnit, val minutes: Int?, val appliedRate: LabourRateSnapshot? = null)

data class LabourProblem(val field: String, val code: String)

object LabourRules {
    const val MAX_MINUTES = 24 * 60

    fun validate(quantity: Int, unit: LabourUnit, minutes: Int?): LabourProblem? = when {
        quantity <= 0 -> LabourProblem("quantity", "not_positive")
        quantity > 500 -> LabourProblem("quantity", "too_many")
        unit == LabourUnit.HOURS && (minutes == null || minutes <= 0) -> LabourProblem("minutes", "required")
        unit == LabourUnit.HOURS && minutes!! > MAX_MINUTES -> LabourProblem("minutes", "too_long")
        unit != LabourUnit.HOURS && minutes != null -> LabourProblem("minutes", "only_for_hours")
        else -> null
    }

    fun validate(draft: CrewDraft): LabourProblem? = when {
        draft.workerIds.isEmpty() -> LabourProblem("workers", "empty")
        draft.workerIds.toSet().size != draft.workerIds.size -> LabourProblem("workers", "duplicate")
        draft.priceUnknown && (draft.appliedRate != null || draft.initialPayments.isNotEmpty()) -> LabourProblem("appliedRate", "price_unknown")
        else -> validate(1, draft.unit, draft.minutes)
    }

    fun validate(draft: CountDraft): LabourProblem? = validate(draft.count, draft.unit, draft.minutes)
}

/**
 * Deterministic totals of a set of labour lines: whole days, half days and hours are kept
 * apart (never converted into one another by guess), plus how many people they cover.
 */
data class LabourSummary(
    val people: Int,
    val fullDays: Int,
    val halfDays: Int,
    val minutes: Int,
) {
    val isEmpty: Boolean get() = people == 0

    /** "5 jornadas · 2 medias · 6 h 30 min". */
    fun label(): String = listOfNotNull(
        fullDays.takeIf { it > 0 }?.let { if (it == 1) "1 jornada" else "$it jornadas" },
        halfDays.takeIf { it > 0 }?.let { if (it == 1) "1 media" else "$it medias" },
        minutes.takeIf { it > 0 }?.let(::hours),
    ).joinToString(" · ")

    companion object {
        fun of(entries: List<LabourEntry>): LabourSummary = LabourSummary(
            people = entries.sumOf { it.quantity },
            fullDays = entries.filter { it.unit == LabourUnit.FULL_DAY }.sumOf { it.quantity },
            halfDays = entries.filter { it.unit == LabourUnit.HALF_DAY }.sumOf { it.quantity },
            minutes = entries.filter { it.unit == LabourUnit.HOURS }.sumOf { it.quantity * (it.minutes ?: 0) },
        )

        fun hours(minutes: Int): String = when {
            minutes % 60 == 0 -> "${minutes / 60} h"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }
}

/**
 * #254 (254-D): one person's labour over a set of Jornadas, grouped by their stable worker id
 * (so a renamed person stays one row). Lines typed as a bare count have no person and are
 * totalled apart, never shared out among names.
 */
data class WorkerLabour(val workerId: UUID, val name: String, val jornadas: Int, val summary: LabourSummary)

object LabourByWorker {
    /** [entries] in recording order (as the DAO returns them): a person shows their latest name. */
    fun of(entries: List<LabourEntry>): List<WorkerLabour> =
        entries.filter { it.workerId != null }
            .groupBy { it.workerId!! }
            .map { (id, rows) ->
                WorkerLabour(
                    workerId = id,
                    name = rows.lastOrNull { !it.workerName.isNullOrBlank() }?.workerName ?: "Sin nombre",
                    jornadas = rows.map { it.harvestId }.distinct().size,
                    summary = LabourSummary.of(rows),
                )
            }
            .sortedWith(compareByDescending<WorkerLabour> { it.summary.fullDays * 2 + it.summary.halfDays }.thenBy { it.name })

    /** The lines recorded only as "N personas", without names. */
    fun unnamed(entries: List<LabourEntry>): LabourSummary = LabourSummary.of(entries.filter { it.workerId == null })
}

interface LabourRepository {
    fun observePayments(campaignId: UUID): Flow<List<LabourPayment>> = flowOf(emptyList())
    suspend fun recordPayment(payment: LabourPayment): AppResult<UUID> =
        AppResult.Failure(AppError.Conflict("payments_unavailable"))
    suspend fun removePayment(paymentId: UUID): AppResult<Unit> =
        AppResult.Failure(AppError.Conflict("payments_unavailable"))

    fun observeWorkers(): Flow<List<Worker>>

    /** Returns the existing person when the name is already saved (case-insensitive). */
    suspend fun addWorker(name: String): AppResult<UUID>

    fun observeForHarvest(harvestId: UUID): Flow<List<LabourEntry>>

    fun observeForCampaign(campaignId: UUID): Flow<List<LabourEntry>>

    /** One line per person, all in one transaction. */
    suspend fun recordCrew(draft: CrewDraft): AppResult<Int>

    suspend fun recordCount(draft: CountDraft): AppResult<UUID>

    suspend fun update(entryId: UUID, change: LabourChange): AppResult<Unit>

    suspend fun remove(entryId: UUID): AppResult<Unit>

    /** The people of the Farm's previous Jornada with a named crew; empty when there is none. */
    suspend fun previousCrew(harvestId: UUID): List<UUID>
}
