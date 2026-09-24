package com.isivoltpro.maginaolivo.domain.notebook

import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseSummary
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
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
) {
    val harvestSummary: HarvestSummary = HarvestSummary.of(harvests)
    val deliverySummary: DeliverySummary = DeliverySummary.of(deliveries)
    val expenseSummary: ExpenseSummary = ExpenseSummary.of(expenses)

    /** Expenses the farmer files under recolección (harvest and transport). */
    val recollectionExpenses: List<Expense> = expenses.filter { it.category in RECOLLECTION_CATEGORIES }
    val recollectionExpenseSummary: ExpenseSummary = ExpenseSummary.of(recollectionExpenses)

    val completedWorks: Int = works.count { it.status == ActivityStatus.COMPLETED }
    val plannedWorks: Int = works.count { it.status == ActivityStatus.PLANNED }

    /** Recolección rows, grouped by day, newest day first. */
    val recollectionDays: List<RecollectionDay> =
        (harvests.map { RecollectionItem.HarvestItem(it) } +
            deliveries.map { RecollectionItem.DeliveryItem(it) } +
            harvestDays.map { RecollectionItem.HarvestDayItem(it) } +
            recollectionExpenses.map { RecollectionItem.ExpenseItem(it) })
            .groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (date, items) -> RecollectionDay(date, items.sortedBy { it.order }) }

    val isEmpty: Boolean = works.isEmpty() && recollectionDays.isEmpty()

    /** Phase 19B: how many of this Campaign's Pesadas belong to one Jornada. */
    fun pesadaCount(harvestId: java.util.UUID): Int = deliveries.count { it.harvestId == harvestId }

    companion object {
        val RECOLLECTION_CATEGORIES = setOf(ExpenseCategory.HARVEST, ExpenseCategory.TRANSPORT)

        /**
         * What belongs to [campaign]: records linked to it, plus the Farm's records that were
         * saved without a Campaign but fall inside its dates (they would otherwise be lost
         * between campaigns). Nothing from another Farm or another Campaign is ever pulled in.
         */
        fun project(
            campaign: Campaign,
            activities: List<Activity>,
            harvests: List<Harvest>,
            deliveries: List<Delivery>,
            expenses: List<Expense>,
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
                expenses = expenses.filter { belongs(it.campaignId, it.farmId, it.expenseDate) }
                    .sortedByDescending { it.expenseDate },
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
