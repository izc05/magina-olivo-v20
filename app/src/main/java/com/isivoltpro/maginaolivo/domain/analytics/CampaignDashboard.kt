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
    /** Calendar days from activation to today, or to the close date once closed (both counted). */
    val calendarDays: Long?,
    val pesadaDays: Int,
    val labourDays: Int,
    val firstPesada: LocalDate?,
    val lastPesada: LocalDate?,
    val closedOn: LocalDate?,
    val postedCostMinor: Long,
    /** Posted `DAY_LABOUR` / `DAY_EQUIPMENT` entries: already inside [postedCostMinor], never added again. */
    val calculatedLabourMinor: Long,
    val calculatedMachineryMinor: Long,
    val currency: String,
    /** Cents per weighed kilo; null without posted costs or without weighed kilos. */
    val costPerKgMinor: Long?,
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
            val summary = notebook.expenseSummary
            val posted = notebook.expenses.filter { it.status == ExpenseStatus.POSTED && it.currency == summary.currency }
            val grams = notebook.deliverySummary.deliveredGrams
            return CampaignDashboard(
                calendarDays = days,
                pesadaDays = pesadaDates.size,
                labourDays = labourDays,
                firstPesada = pesadaDates.firstOrNull(),
                lastPesada = pesadaDates.lastOrNull(),
                closedOn = closedOn,
                postedCostMinor = summary.totalMinor,
                calculatedLabourMinor = posted.filter { it.origin == ExpenseOrigin.DAY_LABOUR }.sumOf { it.amountMinor },
                calculatedMachineryMinor = posted.filter { it.origin == ExpenseOrigin.DAY_EQUIPMENT }.sumOf { it.amountMinor },
                currency = summary.currency,
                // cents / (grams / 1000), rounded half up — the same rule as the year-over-year row.
                costPerKgMinor = if (grams > 0 && summary.totalMinor > 0) (summary.totalMinor * 1000 * 2 + grams) / (grams * 2) else null,
            )
        }
    }
}
