package com.isivoltpro.maginaolivo.domain.analytics

import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket
import com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger
import com.isivoltpro.maginaolivo.domain.farm.Farm
import java.time.LocalDate
import java.util.UUID

/**
 * #359 — the olive season a Campaign belongs to, from its start date: a season runs from
 * September to August and reads «2026/27». Seasons are never mixed in one overview.
 */
object OliveSeason {
    fun of(date: LocalDate): String {
        val start = if (date.monthValue >= 9) date.year else date.year - 1
        return String.format(java.util.Locale.ROOT, "%d/%02d", start, (start + 1) % 100)
    }

    /** The dates a season covers: 1 September to 31 August. */
    fun range(season: String): ClosedRange<LocalDate> {
        val start = season.substringBefore('/').toInt()
        return LocalDate.of(start, 9, 1)..LocalDate.of(start + 1, 8, 31)
    }

    /** Seasons with at least one Campaign, newest first. */
    fun available(campaigns: List<Campaign>): List<String> =
        campaigns.map { of(it.startDate) }.distinct().sortedDescending()
}

/** Money of one currency, in minor units; [amountMinor] is null when the sum overflows. */
data class CurrencyTotal(val currency: String, val amountMinor: Long?, val labourMinor: Long?)

/** One Farm's figures in a season, from its Pesadas and its posted ledger only. */
data class FarmSeasonFigures(
    val farmId: UUID,
    val farmName: String,
    val campaignCount: Int,
    val delivery: DeliverySummary,
    val costs: List<CurrencyTotal>,
    /** #449: false while a campaign of this Farm in the season has unconfirmed costs; null while unknown. */
    val costComplete: Boolean? = true,
    /** #616: historical rows can belong to a Farm no longer operational today. */
    val archived: Boolean = false,
) {
    val yieldCoveragePercent: Int get() = delivery.coveragePercent(delivery.fatYield)
    val costPerKgMilli: Long? get() = costPerKg(costs, delivery)
}

/**
 * #359 — the whole holding in one season: the sum of each Farm's own figures, never an
 * average of averages. Yield is weighted by analysed kilos; cost per kilo is total cost over
 * total kilos, and only with one currency. Farms with no Campaign in the season are named,
 * not counted as zero.
 */
data class FarmOverview(
    val season: String,
    val farms: List<FarmSeasonFigures>,
    val farmsWithoutCampaign: List<String>,
    val delivery: DeliverySummary,
    val costs: List<CurrencyTotal>,
    /**
     * #359 follow-up: posted costs of these Farms dated in the season and linked to no Campaign
     * (pruning, treatments, irrigation, fuel…). Never part of [costs] nor of the recollection cost/kg.
     */
    val generalCosts: List<CurrencyTotal> = emptyList(),
    /**
     * #449: false while any campaign of the season has unconfirmed costs; null while that is still
     * being read (never claimed either way). The money stays the posted ledger.
     */
    val costComplete: Boolean? = true,
) {
    val yieldCoveragePercent: Int get() = delivery.coveragePercent(delivery.fatYield)
    val costPerKgMilli: Long? get() = costPerKg(costs, delivery)

    /** Recollection plus general costs, one total per currency, nothing converted. */
    val totalCosts: List<CurrencyTotal> get() = (costs + generalCosts).groupBy { it.currency }.toSortedMap()
        .map { (currency, parts) ->
            val amounts = parts.map { it.amountMinor }
            CurrencyTotal(currency, if (amounts.any { it == null }) null else sum(amounts), labourMinor = null)
        }

    /** Total cost over weighed kilos, only with one currency. */
    val totalCostPerKgMilli: Long? get() = costPerKg(totalCosts, delivery)

    /** A Farm's share of the season's weighed kilos, in whole percent; null without kilos. */
    fun sharePercent(figures: FarmSeasonFigures): Int? =
        if (delivery.deliveredGrams <= 0) null
        else Math.round(figures.delivery.deliveredGrams * 100.0 / delivery.deliveredGrams).toInt()

    companion object {
        fun of(
            season: String,
            farms: List<Farm>,
            campaigns: List<Campaign>,
            deliveries: List<Delivery>,
            expenses: List<Expense>,
            /** #449: Campaigns whose jornales, machinery or costs are still unconfirmed; null while unknown. */
            incompleteCampaigns: Set<UUID>? = emptySet(),
        ): FarmOverview {
            val inSeason = campaigns.filter { OliveSeason.of(it.startDate) == season }
            val figures = farms.mapNotNull { farm ->
                val own = inSeason.filter { it.farmId == farm.id }
                if (own.isEmpty()) return@mapNotNull null
                val ids = own.map { it.id }.toSet()
                val weighed = deliveries.filter { it.campaignId in ids }
                FarmSeasonFigures(
                    farm.id,
                    seasonName(farm, own),
                    own.size,
                    DeliverySummary.of(weighed),
                    totals(ids, expenses, weighed),
                    costComplete = incompleteCampaigns?.let { pending -> ids.none { it in pending } },
                    archived = farm.archivedAt != null,
                )
            }
            val ids = inSeason.filter { campaign -> farms.any { it.id == campaign.farmId } }.map { it.id }.toSet()
            val weighed = deliveries.filter { it.campaignId in ids }
            return FarmOverview(
                season = season,
                farms = figures.sortedByDescending { it.delivery.deliveredGrams },
                // Archived Farms are historical participants, not missing current work.
                farmsWithoutCampaign = farms.filter { farm ->
                    farm.archivedAt == null && figures.none { it.farmId == farm.id }
                }.map { it.name },
                delivery = DeliverySummary.of(weighed),
                costs = totals(ids, expenses, weighed),
                generalCosts = general(season, farms, expenses),
                costComplete = incompleteCampaigns?.let { pending -> ids.none { it in pending } },
            )
        }

        /**
         * Posted expenses of these Farms (or of the same holding, with no Farm) dated in the
         * season and assigned to no Campaign. Drafts never count.
         */
        private fun general(season: String, farms: List<Farm>, expenses: List<Expense>): List<CurrencyTotal> {
            val range = OliveSeason.range(season)
            val farmIds = farms.map { it.id }.toSet()
            // Codex #402: the local store may hold other workspaces; only this holding's expenses count.
            val workspaces = farms.map { it.workspaceId }.toSet()
            return expenses.filter { expense ->
                expense.workspaceId in workspaces &&
                    expense.status == com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus.POSTED &&
                    expense.campaignId == null &&
                    (expense.farmId == null || expense.farmId in farmIds) &&
                    expense.expenseDate in range
            }.groupBy { it.currency }.toSortedMap().map { (currency, rows) ->
                CurrencyTotal(currency, sum(rows.map { it.amountMinor }), labourMinor = null)
            }
        }

        /**
         * Codex #384: a season whose campaigns are all closed keeps the Farm's name as frozen in
         * their snapshots, so renaming the Farm later never rewrites that history.
         */
        private fun seasonName(farm: Farm, campaigns: List<Campaign>): String =
            if (campaigns.all { it.status == com.isivoltpro.maginaolivo.data.local.model.CampaignStatus.CLOSED }) {
                campaigns.sortedByDescending { it.startDate }.flatMap { it.snapshots }.firstNotNullOfOrNull { it.farmName.takeIf(String::isNotBlank) } ?: farm.name
            } else {
                farm.name
            }

        /** Posted recollection costs of these Campaigns, one total per currency, nothing converted. */
        private fun totals(campaignIds: Set<UUID>, expenses: List<Expense>, deliveries: List<Delivery>): List<CurrencyTotal> =
            campaignIds.flatMap { RecollectionLedger.of(it, expenses, deliveries) }
                .groupBy { it.currency }.toSortedMap()
                .map { (currency, ledgers) ->
                    // A ledger too large to add up makes the total unknown, never a smaller number.
                    val all = ledgers.map { it.amount() }
                    val labour = ledgers.filter { it.hasPosted(RecollectionBucket.LABOUR) }.map { it.amount(RecollectionBucket.LABOUR) }
                    CurrencyTotal(currency, if (all.any { it == null }) null else sum(all),
                        if (labour.any { it == null }) null else sum(labour))
                }

    }
}

private fun sum(values: List<Long?>): Long? = runCatching {
    values.filterNotNull().takeIf { it.isNotEmpty() }?.fold(0L) { total, value -> Math.addExact(total, value) }
}.getOrNull()

/**
 * #486: thousandths of the currency unit per kilo (0,253 €/kg = 253): total cost over total
 * weighed kilos, only with one currency. A ratio, never rounded to the currency's cents.
 */
private fun costPerKg(costs: List<CurrencyTotal>, delivery: DeliverySummary): Long? {
    val total = costs.singleOrNull() ?: return null
    val cost = total.amountMinor ?: return null
    return com.isivoltpro.maginaolivo.domain.expense.CostPerKg.milli(cost, total.currency, delivery.deliveredGrams)
}
