package com.isivoltpro.maginaolivo.domain.notebook

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import java.time.LocalDate

/*
 * UX-E (Issue #246 §4) — the four views of Mi Cuaderno over one CampaignNotebook. They are read
 * projections only: every row wraps its canonical record and every figure is recomputed from
 * those records, so nothing is stored, copied or totalled twice.
 */

/** One Diario row: the canonical record itself. */
sealed class DiaryEntry(val date: LocalDate, val order: Int) {
    class Work(val activity: Activity) : DiaryEntry(activity.activityDate, 0)
    class HarvestEntry(val harvest: Harvest) : DiaryEntry(harvest.harvestDate, 1)
    class DeliveryEntry(val delivery: Delivery) : DiaryEntry(delivery.deliveryDate, 2)
    /**
     * #478: every real Gasto is a row of its own on its own date; [relatedWork] names the work it
     * is tied to, so a Gasto linked to a Tratamiento is never hidden behind it.
     */
    class ExpenseEntry(val expense: Expense, val relatedWork: String? = null) : DiaryEntry(expense.expenseDate, 3)
}

data class DiaryDay(val date: LocalDate, val entries: List<DiaryEntry>)

/** #478: the work a Gasto is tied to, as the Diario names it; null for a Gasto of its own. */
internal fun relatedWorkName(expense: Expense, works: List<Activity>): String? =
    expense.activityId?.let { id -> works.firstOrNull { it.id == id }?.description ?: "un trabajo" }

/**
 * Diario: everything written down in the Campaign, one timeline, newest day first. #478: a Gasto
 * tied to a work is its own row, on its own date, saying which work; only a Jornada's own costs
 * are read inside the Jornada. The Diario shows money, it never sums it.
 */
val CampaignNotebook.diary: List<DiaryDay>
    get() = (
        (works + harvestDays).map { DiaryEntry.Work(it) } +
            harvests.map { DiaryEntry.HarvestEntry(it) } +
            // 254-E: a Jornada's own Pesadas and costs are read inside its row, not repeated.
            deliveries.filter { standsAlone(it.harvestId) }.map { DiaryEntry.DeliveryEntry(it) } +
            expenses.filter { standsAlone(it.harvestId) }
                .map { expense -> DiaryEntry.ExpenseEntry(expense, relatedWorkName(expense, works + harvestDays)) }
        )
        .groupBy { it.date }
        .toSortedMap(compareByDescending { it })
        .map { (date, entries) -> DiaryDay(date, entries.sortedBy { it.order }) }

/** What a phytosanitary record may still lack before it is complete for the notebook. */
enum class PhytoGap(val label: String) {
    PRODUCT("producto"),
    ACTIVE_SUBSTANCE("materia activa"),
    DOSE("dosis"),
    PARCEL("parcela"),
    SURFACE("superficie"),
    REASON("motivo"),
}

/**
 * Fitosanitario: one treatment Activity as the notebook reads it. Only what the farmer wrote is
 * shown; a missing value is listed in [gaps], never filled in. The legal record model is closed
 * separately (Issue #246 §4) — this view only prepares it.
 */
data class PhytoRecord(
    val activity: Activity,
    val productName: String?,
    val activeSubstance: String?,
    /** "2,5 l/ha" as typed: value and unit. */
    val dose: String?,
    /** "40 l" as typed: total quantity and unit. */
    val quantity: String?,
    val parcelNames: List<String>,
    /** Sum of the treated areas, only when every Parcel has one. */
    val surfaceM2: Double?,
    val reason: String?,
    /** The machines linked to the Activity, then the equipment typed on the treatment. */
    val machinery: List<String>,
    /** Who does it, as planned ("Tratamientos López"); no separate applicator field exists yet. */
    val crew: String?,
    val notes: String?,
    val gaps: List<PhytoGap>,
) {
    val date: LocalDate get() = activity.activityDate
    val status: ActivityStatus get() = activity.status

    companion object {
        fun of(activity: Activity): PhytoRecord? {
            if (activity.type != ActivityType.PHYTOSANITARY) return null
            val detail = activity.detail as? ActivityDetail.Phytosanitary
            val targets = activity.targets
            val surface = targets.takeIf { it.isNotEmpty() && it.all { t -> t.areaAffectedM2 != null } }
                ?.sumOf { it.areaAffectedM2 ?: 0.0 }
            val product = detail?.productName?.takeIf { it.isNotBlank() }
            val substance = detail?.activeSubstance?.takeIf { it.isNotBlank() }
            val dose = detail?.let { d -> d.doseValue?.let { amount(it, d.doseUnit) } }
            val reason = detail?.reason?.takeIf { it.isNotBlank() }
            val gaps = buildList {
                if (product == null) add(PhytoGap.PRODUCT)
                if (substance == null) add(PhytoGap.ACTIVE_SUBSTANCE)
                if (dose == null) add(PhytoGap.DOSE)
                if (targets.isEmpty()) add(PhytoGap.PARCEL) else if (surface == null) add(PhytoGap.SURFACE)
                if (reason == null) add(PhytoGap.REASON)
            }
            return PhytoRecord(
                activity = activity,
                productName = product,
                activeSubstance = substance,
                dose = dose,
                quantity = detail?.let { d -> d.totalQuantity?.let { amount(it, d.unit) } },
                parcelNames = targets.map { it.parcelName },
                surfaceM2 = surface,
                reason = reason,
                machinery = activity.machines.map { it.name } + listOfNotNull(detail?.equipmentText?.takeIf { it.isNotBlank() }),
                crew = activity.planning?.crewText?.takeIf { it.isNotBlank() },
                notes = activity.notes?.takeIf { it.isNotBlank() },
                gaps = gaps,
            )
        }

        private fun amount(value: Double, unit: String?): String {
            val number = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')
            return listOfNotNull(number, unit?.takeIf { it.isNotBlank() }).joinToString(" ")
        }
    }
}

/** Fitosanitario: the Campaign's treatments, newest first. */
val CampaignNotebook.phytoRecords: List<PhytoRecord>
    get() = works.mapNotNull { PhytoRecord.of(it) }.sortedByDescending { it.date }

/**
 * Gastos: the one Expense ledger of the Campaign seen by what the farmer asks about — people,
 * machines and papers. Money only ever comes from posted Expenses; jornales and machine use
 * add counts and hours, never a second amount.
 */
data class NotebookCosts(
    /** #450: every amount per currency, never converted nor reduced to one currency. */
    val ledger: List<com.isivoltpro.maginaolivo.domain.expense.RecollectionCurrency>,
    /** Posted LABOR Expenses. */
    val labourMoney: List<com.isivoltpro.maginaolivo.domain.expense.RecollectionCurrency>,
    /** Posted MACHINERY, FUEL and REPAIR Expenses. */
    val machineryMoney: List<com.isivoltpro.maginaolivo.domain.expense.RecollectionCurrency>,
    /** Hours of machine use written on the Campaign's Activities. */
    val machineHours: Double,
    /** Activities with at least one machine. */
    val machineUses: Int,
    /** Expenses that carry a paper: an invoice number or a scanned document. */
    val documents: List<Expense>,
) {
    companion object {
        val MACHINERY_CATEGORIES = setOf(ExpenseCategory.MACHINERY, ExpenseCategory.FUEL, ExpenseCategory.REPAIR)
    }
}

val CampaignNotebook.costs: NotebookCosts
    get() {
        val withMachines = (works + harvestDays).filter { it.machines.isNotEmpty() }
        return NotebookCosts(
            ledger = expensesByCurrency,
            labourMoney = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.posted(expenses.filter { it.category == ExpenseCategory.LABOR }),
            machineryMoney = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.posted(expenses.filter { it.category in NotebookCosts.MACHINERY_CATEGORIES }),
            machineHours = withMachines.sumOf { activity -> activity.machines.sumOf { it.hoursUsed ?: 0.0 } },
            machineUses = withMachines.size,
            documents = expenses.filter { !it.invoiceNumber.isNullOrBlank() || it.origin == ExpenseOrigin.DOCUMENT_OCR },
        )
    }

/**
 * Campaña: kilos picked but not yet delivered. Known only when both sides were written down
 * and the deliveries do not exceed the harvest (otherwise part of the harvest is unrecorded,
 * and a difference would be invented).
 */
val CampaignNotebook.pendingDeliveryGrams: Long?
    get() {
        if (harvestSummary.weighedCount == 0) return null
        val picked = harvestSummary.totalGrams ?: return null
        val delivered = deliverySummary.deliveredGrams ?: return null
        return try {
            Math.subtractExact(picked, delivered).takeIf { it >= 0 }
        } catch (_: ArithmeticException) {
            null
        }
    }

/**
 * CR-010 (Amendment 1, A2): kilograms typed by hand on legacy Jornadas with no Pesada linked.
 * The principal total is always the Pesadas'; these are shown apart as «kg registrados sin pesada
 * (histórico)» — never added to that total, never dropped. A Jornada with Pesadas counts only
 * through them, and one still awaiting its first Pesada has no kilos at all.
 */
fun legacyUnweighedGrams(harvests: List<Harvest>, deliveries: List<Delivery>): Long? {
    val linked = deliveries.mapNotNullTo(HashSet()) { it.harvestId }
    var total = 0L
    for (harvest in harvests.filter { it.id !in linked && !it.automatic && !it.awaitingPesadas }) {
        if (harvest.totalGrams < 0) return null
        total = try {
            Math.addExact(total, harvest.totalGrams)
        } catch (_: ArithmeticException) {
            return null
        }
    }
    return total
}

val CampaignNotebook.legacyUnweighedGrams: Long? get() = legacyUnweighedGrams(harvests, deliveries)
