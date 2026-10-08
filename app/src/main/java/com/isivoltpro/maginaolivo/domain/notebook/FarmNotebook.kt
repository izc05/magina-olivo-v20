package com.isivoltpro.maginaolivo.domain.notebook

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentSummary
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary
import java.util.UUID

/**
 * #417 — the Cuaderno of a Farm: everything done on it through the agricultural year, with or
 * without a Campaign. A read-only projection over the canonical records (nothing stored, nothing
 * copied). Diario, Fitosanitario and Gastos read it; only the Campaña view reads [CampaignNotebook].
 * A Campaign never absorbs a record here: each row is listed once, as the record it is.
 */
data class FarmNotebook(
    val farmId: UUID?,
    /** Every Activity of the Farm, done and planned, newest first. */
    val activities: List<Activity>,
    val harvests: List<Harvest>,
    val deliveries: List<Delivery>,
    /** Every Expense of the Farm, with or without a Campaign, newest first. */
    val expenses: List<Expense>,
    val labour: List<LabourEntry> = emptyList(),
    val equipment: List<EquipmentLine> = emptyList(),
) {
    /**
     * The Activities that were actually done (#424/#438): the only ones the Diario, Fitosanitario
     * and machinery use read. Planned work stays in Avisos until confirmed; drafts and cancelled
     * work are never facts.
     */
    val realizedActivities: List<Activity> get() = activities.filter { it.status == ActivityStatus.COMPLETED }

    /** Machinery actually used: only on done work, never machinery merely planned. */
    val machineWork: List<Activity> get() = realizedActivities.filter { it.machines.isNotEmpty() }

    private val listedJornadas: Set<UUID> = harvests.map { it.id }.toSet()

    /** Equipment recorded on the Farm's recolección Jornadas (canonical, never typed twice). */
    val equipmentSummary: EquipmentSummary get() = EquipmentSummary.of(equipment.filter { it.harvestId in listedJornadas })

    /** Pesadas and Expenses not already shown inside a listed Jornada (no kilo or euro twice). */
    private fun standsAlone(harvestId: UUID?): Boolean = harvestId == null || harvestId !in listedJornadas

    /**
     * Diario: the Farm's timeline of what was done, newest day first. #478: every Gasto is a row of its own on
     * its own date, saying the work it is tied to; only a Jornada's own Pesadas and costs are read inside it.
     */
    val diary: List<DiaryDay>
        get() = (
            realizedActivities.map { DiaryEntry.Work(it) } +
                harvests.map { DiaryEntry.HarvestEntry(it) } +
                deliveries.filter { standsAlone(it.harvestId) }.map { DiaryEntry.DeliveryEntry(it) } +
                expenses.filter { standsAlone(it.harvestId) }
                    .map { expense -> DiaryEntry.ExpenseEntry(expense, relatedWorkName(expense, activities)) }
            )
            .groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (date, entries) -> DiaryDay(date, entries.sortedBy { it.order }) }

    /** Fitosanitario: every treatment applied on the Farm, newest first, whatever its Campaign. */
    val phytoRecords: List<PhytoRecord>
        get() = realizedActivities.mapNotNull { PhytoRecord.of(it) }.sortedByDescending { it.date }

    /** Expenses linked to no Campaign: the Farm's general costs. */
    val generalExpenses: List<Expense> get() = expenses.filter { it.campaignId == null }

    /** Expenses explicitly linked to a Campaign: recollection costs, never decided by date. */
    val recollectionExpenses: List<Expense> get() = expenses.filter { it.campaignId != null }

    fun pesadaCount(harvestId: UUID): Int = deliveries.count { it.harvestId == harvestId }

    fun labourFor(harvestId: UUID): LabourSummary = LabourSummary.of(labour.filter { it.harvestId == harvestId })

    /** #450: the day's posted money per currency. */
    fun jornadaCost(harvestId: UUID): List<com.isivoltpro.maginaolivo.domain.expense.RecollectionCurrency> = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.posted(expenses.filter { it.harvestId == harvestId })

    /** The kilo-weighted yield of a Jornada's Pesadas, «pendiente» while any has none; null without Pesadas. */
    fun jornadaYieldLabel(harvestId: UUID): String? {
        val own = deliveries.filter { it.harvestId == harvestId }
        if (own.isEmpty()) return null
        val summary = DeliverySummary.of(own)
        val fat = summary.fatYield ?: return "rend. pendiente"
        val delivered = summary.deliveredGrams
            ?: return "rend. ${Percent.format(fat.hundredths)} (cobertura no disponible)"
        return "rend. ${Percent.format(fat.hundredths)}" + if (fat.analysedGrams < delivered) " (parcial)" else ""
    }

    companion object {
        val EMPTY = FarmNotebook(null, emptyList(), emptyList(), emptyList(), emptyList())

        /** The Farm's records, taken from all the local lists. */
        fun of(
            farmId: UUID,
            activities: List<Activity>,
            harvests: List<Harvest>,
            deliveries: List<Delivery>,
            expenses: List<Expense>,
            labour: List<LabourEntry> = emptyList(),
            equipment: List<EquipmentLine> = emptyList(),
        ): FarmNotebook = FarmNotebook(
            farmId = farmId,
            activities = activities.filter { it.farmId == farmId }.sortedByDescending { it.activityDate },
            harvests = harvests.filter { it.farmId == farmId },
            deliveries = deliveries.filter { it.farmId == farmId },
            expenses = expenses.filter { it.farmId == farmId }.sortedByDescending { it.expenseDate },
            labour = labour,
            equipment = equipment,
        )

        /** The records one Campaign view already holds, read as its Farm's (screens built from a Campaign). */
        fun of(notebook: CampaignNotebook): FarmNotebook = FarmNotebook(
            farmId = notebook.campaign.farmId,
            activities = (notebook.works + notebook.harvestDays).sortedByDescending { it.activityDate },
            harvests = notebook.harvests,
            deliveries = notebook.deliveries,
            expenses = notebook.expenses,
            labour = notebook.labour,
            equipment = notebook.equipment,
        )
    }
}
