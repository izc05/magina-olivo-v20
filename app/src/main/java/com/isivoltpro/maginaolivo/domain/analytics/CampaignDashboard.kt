package com.isivoltpro.maginaolivo.domain.analytics

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * CR-010 §12 — what the active Campaign screen answers in seconds. Every figure is derived from
 * the same rows the Cuaderno lists: kilos from Pesadas, days from their dates, money only from
 * the posted Expense ledger (calculated day costs included, once). Unknown stays null.
 */
data class CampaignDashboard(
    /**
     * Calendar days from the Campaign's start date ([countedFrom]) to today, or to the close date
     * once closed (both counted). The activation moment itself is not stored, so the screen names
     * the date it counts from instead of claiming «since activation».
     */
    val calendarDays: Long?,
    val countedFrom: LocalDate?,
    val pesadaDays: Int,
    val labourDays: Int,
    val firstPesada: LocalDate?,
    val lastPesada: LocalDate?,
    val closedOn: LocalDate?,
    /**
     * #450: posted money of the Campaign, one entry per currency (never converted, never one
     * currency standing for the whole ledger). Empty when nothing is posted.
     */
    val costs: List<CampaignCurrencyCost>,
    /** Currency minor units per weighed kilo; null without a unique currency or weighed kilos. */
    val costPerKgMilli: Long?,
) {
    companion object {
        fun of(notebook: CampaignNotebook, today: LocalDate): CampaignDashboard {
            val campaign = notebook.campaign
            val closedOn = campaign.endDate?.takeIf { campaign.status == CampaignStatus.CLOSED }
            val until = closedOn ?: today
            val days = if (campaign.status == CampaignStatus.PREPARATION || until.isBefore(campaign.startDate)) {
                null
            } else {
                ChronoUnit.DAYS.between(campaign.startDate, until) + 1
            }
            val pesadaDates = notebook.deliveries.map { it.deliveryDate }.distinct().sorted()
            val labourHarvests = notebook.labour.map { it.harvestId }.toSet()
            val labourDays = notebook.harvests.filter { it.id in labourHarvests }.map { it.harvestDate }.distinct().size
            val currencies = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.of(campaign.id, notebook.expenses, notebook.deliveries)
            val canonical = currencies.singleOrNull()
            val costs = notebook.expenses.filter { it.status == ExpenseStatus.POSTED }.groupBy { it.currency }.toSortedMap()
                .map { (currency, posted) ->
                    CampaignCurrencyCost(
                        currency = currency,
                        postedMinor = posted.sumOf { it.amountMinor },
                        calculatedLabourMinor = posted.filter { it.origin == ExpenseOrigin.DAY_LABOUR }.sumOf { it.amountMinor },
                        calculatedMachineryMinor = posted.filter { it.origin == ExpenseOrigin.DAY_EQUIPMENT }.sumOf { it.amountMinor },
                    )
                }
            return CampaignDashboard(
                calendarDays = days,
                countedFrom = campaign.startDate.takeIf { days != null },
                pesadaDays = pesadaDates.size,
                labourDays = labourDays,
                firstPesada = pesadaDates.firstOrNull(),
                lastPesada = pesadaDates.lastOrNull(),
                closedOn = closedOn,
                costs = costs,
                // Slice 1 decimal ratio, rounded only for display in the currency's minor units.
                costPerKgMilli = canonical?.costPerKgMilli,
            )
        }
    }
}

/** #450: one currency's posted money of a Campaign; the calculated day costs are already inside [postedMinor]. */
data class CampaignCurrencyCost(
    val currency: String,
    val postedMinor: Long,
    val calculatedLabourMinor: Long,
    val calculatedMachineryMinor: Long,
)
