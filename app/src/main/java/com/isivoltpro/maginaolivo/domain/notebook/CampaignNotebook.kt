package com.isivoltpro.maginaolivo.domain.notebook

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.delivery.ParcelYield
import com.isivoltpro.maginaolivo.domain.delivery.PesadaSearch
import com.isivoltpro.maginaolivo.domain.delivery.YieldStatus
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseSummary
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentSummary
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourByWorker
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary
import com.isivoltpro.maginaolivo.domain.labour.WorkerLabour
import java.time.LocalDate

/**
 * Phase 19A (CR-005) — the Cuaderno of one Campaign: a read-only projection over the canonical
 * records. It stores nothing and copies nothing; every row is the original Activity, Harvest,
 * Delivery or Expense, and every total is the same summary its own screen computes, so the
 * Cuaderno always reconciles with Cosecha, Entregas and Gastos.
 */
data class CampaignNotebook(
    val campaign: Campaign,
    /** Annual work (everything but harvest days), newest first. */
    val works: List<Activity>,
    /** Harvest-day Activities, shown with the recolección, newest first. */
    val harvestDays: List<Activity>,
    val harvests: List<Harvest>,
    val deliveries: List<Delivery>,
    /** Every Expense of the Campaign; drafts are listed but only posted ones are summed. */
    val expenses: List<Expense>,
    /** Phase 19D: the jornales of this Campaign's Jornadas. */
    val labour: List<LabourEntry> = emptyList(),
    /** Phase 19E: equipment used on this Campaign's Jornadas. */
    val equipment: List<EquipmentLine> = emptyList(),
) {
    /** Equipment-days across the Campaign's Jornadas ("6 vibradoras" = six vibradora-days). */
    val equipmentSummary: EquipmentSummary = EquipmentSummary.of(equipment.filter { line -> harvests.any { it.id == line.harvestId } })

    val labourSummary: LabourSummary = LabourSummary.of(labour.filter { entry -> harvests.any { it.id == entry.harvestId } })

    /** 254-D: the same lines per person (stable worker id), plus those recorded without names. */
    val labourByWorker: List<WorkerLabour> = LabourByWorker.of(labour.filter { entry -> harvests.any { it.id == entry.harvestId } })
    val unnamedLabour: LabourSummary = LabourByWorker.unnamed(labour.filter { entry -> harvests.any { it.id == entry.harvestId } })

    /** Phase 19D: the jornales of one Jornada. */
    fun labourFor(harvestId: java.util.UUID): LabourSummary = LabourSummary.of(labour.filter { it.harvestId == harvestId })

    val harvestSummary: HarvestSummary = HarvestSummary.of(harvests)
    val deliverySummary: DeliverySummary = DeliverySummary.of(deliveries)
    val expenseSummary: ExpenseSummary = ExpenseSummary.of(expenses)

    /**
     * Every explicitly campaign-linked Expense belongs to its recollection ledger, regardless
     * of category. Each Expense is listed once; nothing is copied.
     */
    val recollectionExpenses: List<Expense> = expenses.filter { it.campaignId == campaign.id }

    /** Phase 19F: the cost of one Jornada — its posted ledger Expenses, nothing else. */
    fun jornadaCost(harvestId: java.util.UUID): ExpenseSummary = ExpenseSummary.of(expenses.filter { it.harvestId == harvestId })
    val recollectionExpenseSummary: ExpenseSummary = ExpenseSummary.of(recollectionExpenses)

    val completedWorks: Int = works.count { it.status == ActivityStatus.COMPLETED }
    val plannedWorks: Int = works.count { it.status == ActivityStatus.PLANNED }

    /** 254-E: ids of the Jornadas listed here; their own Pesadas and costs are shown inside them. */
    private val listedJornadas: Set<java.util.UUID> = harvests.map { it.id }.toSet()

    /** Pesadas and Expenses not already shown inside a listed Jornada (no kilo or euro twice). */
    internal fun standsAlone(harvestId: java.util.UUID?): Boolean = harvestId == null || harvestId !in listedJornadas

    /** Recolección rows, grouped by day, newest day first. */
    val recollectionDays: List<RecollectionDay> =
        (harvests.map { RecollectionItem.HarvestItem(it) } +
            deliveries.filter { standsAlone(it.harvestId) }.map { RecollectionItem.DeliveryItem(it) } +
            harvestDays.map { RecollectionItem.HarvestDayItem(it) } +
            recollectionExpenses.filter { standsAlone(it.harvestId) }.map { RecollectionItem.ExpenseItem(it) })
            .groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (date, items) -> RecollectionDay(date, items.sortedBy { it.order }) }

    val isEmpty: Boolean = works.isEmpty() && recollectionDays.isEmpty()

    /** Phase 19C: Pesadas still waiting for their yield analysis. */
    val pendingYieldCount: Int = deliveries.count { PesadaSearch.statusOf(it) == YieldStatus.PENDING }

    /** Phase 19C: per-Parcel yield from single-origin or exactly split Pesadas only. */
    val parcelYields: List<ParcelYield> = ParcelYield.of(deliveries)

    /** Phase 19B: how many of this Campaign's Pesadas belong to one Jornada. */
    fun pesadaCount(harvestId: java.util.UUID): Int = deliveries.count { it.harvestId == harvestId }

    /** The kilo-weighted yield of a Jornada's Pesadas, or "pendiente" while any has none; null without Pesadas. */
    fun jornadaYieldLabel(harvestId: java.util.UUID): String? {
        val own = deliveries.filter { it.harvestId == harvestId }
        if (own.isEmpty()) return null
        val summary = DeliverySummary.of(own)
        val fat = summary.fatYield ?: return "rend. pendiente"
        // Partial whenever the fat yield covers fewer kilos than were weighed (pending or only industrial).
        return "rend. ${Percent.format(fat.hundredths)}" + if (fat.analysedGrams < summary.deliveredGrams) " (parcial)" else ""
    }

    companion object {
        val RECOLLECTION_CATEGORIES = setOf(ExpenseCategory.HARVEST, ExpenseCategory.TRANSPORT)

        /**
         * What belongs to [campaign]: records linked to it, plus the Farm's records that were
         * saved without a Campaign but fall inside its dates. Financial Expenses require an
         * explicit Campaign link: Farm/date never assigns economic context. Original outside
         * Expenses remain in the Farm/Parcel ledger, with no historical rewrite.
         */
        fun project(
            campaign: Campaign,
            activities: List<Activity>,
            harvests: List<Harvest>,
            deliveries: List<Delivery>,
            expenses: List<Expense>,
            labour: List<LabourEntry> = emptyList(),
            equipment: List<EquipmentLine> = emptyList(),
        ): CampaignNotebook {
            fun belongs(campaignId: java.util.UUID?, farmId: java.util.UUID?, date: LocalDate): Boolean =
                campaignId == campaign.id ||
                    (campaignId == null && farmId == campaign.farmId && campaign.contains(date))
            val own = activities.filter { belongs(it.campaignId, it.farmId, it.activityDate) }
                .sortedByDescending { it.activityDate }
            return CampaignNotebook(
                campaign = campaign,
                works = own.filter { it.type != ActivityType.HARVEST_DAY },
                harvestDays = own.filter { it.type == ActivityType.HARVEST_DAY },
                harvests = harvests.filter { belongs(it.campaignId, it.farmId, it.harvestDate) },
                deliveries = deliveries.filter { belongs(it.campaignId, it.farmId, it.deliveryDate) },
                expenses = expenses.filter { it.campaignId == campaign.id }
                    .sortedByDescending { it.expenseDate },
                labour = labour,
                equipment = equipment,
            )
        }

        private fun Campaign.contains(date: LocalDate): Boolean =
            !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate))
    }
}

data class RecollectionDay(val date: LocalDate, val items: List<RecollectionItem>)

/** One recolección row; each wraps its canonical record, never a copy of its figures. */
sealed class RecollectionItem(val date: LocalDate, val order: Int) {
    class HarvestItem(val harvest: Harvest) : RecollectionItem(harvest.harvestDate, 0)
    class DeliveryItem(val delivery: Delivery) : RecollectionItem(delivery.deliveryDate, 1)
    class HarvestDayItem(val activity: Activity) : RecollectionItem(activity.activityDate, 2)
    class ExpenseItem(val expense: Expense) : RecollectionItem(expense.expenseDate, 3)
}
