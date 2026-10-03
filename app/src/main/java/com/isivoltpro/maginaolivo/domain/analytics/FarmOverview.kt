package com.isivoltpro.maginaolivo.domain.analytics

import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket
import com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger
import com.isivoltpro.maginaolivo.domain.farm.Farm
import java.math.BigDecimal
import java.math.RoundingMode
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
) {
    val yieldCoveragePercent: Int get() = delivery.coveragePercent(delivery.fatYield)
    val costPerKgMinor: Long? get() = costPerKg(costs, delivery)
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
) {
    val yieldCoveragePercent: Int get() = delivery.coveragePercent(delivery.fatYield)
    val costPerKgMinor: Long? get() = costPerKg(costs, delivery)

    /** A Farm's share of the season's weighed kilos, in whole percent; null without kilos. */
    fun sharePercent(figures: FarmSeasonFigures): Int? =
        if (delivery.deliveredGrams <= 0) null
        else Math.round(figures.delivery.deliveredGrams * 100.0 / delivery.deliveredGrams).toInt()

    companion object {
        fun of(season: String, farms: List<Farm>, campaigns: List<Campaign>, deliveries: List<Delivery>, expenses: List<Expense>): FarmOverview {
            val inSeason = campaigns.filter { OliveSeason.of(it.startDate) == season }
            val figures = farms.mapNotNull { farm ->
                val own = inSeason.filter { it.farmId == farm.id }
                if (own.isEmpty()) return@mapNotNull null
                val ids = own.map { it.id }.toSet()
                val weighed = deliveries.filter { it.campaignId in ids }
                FarmSeasonFigures(farm.id, seasonName(farm, own), own.size, DeliverySummary.of(weighed), totals(ids, expenses, weighed))
            }
            val ids = inSeason.filter { campaign -> farms.any { it.id == campaign.farmId } }.map { it.id }.toSet()
            val weighed = deliveries.filter { it.campaignId in ids }
            return FarmOverview(
                season = season,
                farms = figures.sortedByDescending { it.delivery.deliveredGrams },
                farmsWithoutCampaign = farms.filter { farm -> figures.none { it.farmId == farm.id } }.map { it.name },
                delivery = DeliverySummary.of(weighed),
                costs = totals(ids, expenses, weighed),
            )
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

        private fun sum(values: List<Long?>): Long? = runCatching {
            values.filterNotNull().takeIf { it.isNotEmpty() }?.fold(0L) { total, value -> Math.addExact(total, value) }
        }.getOrNull()
    }
}

/** Minor units per kilo: total cost over total weighed kilos, only with one currency. */
private fun costPerKg(costs: List<CurrencyTotal>, delivery: DeliverySummary): Long? {
    val cost = costs.singleOrNull()?.amountMinor ?: return null
    if (delivery.deliveredGrams <= 0) return null
    return BigDecimal.valueOf(cost).multiply(BigDecimal.valueOf(1000))
        .divide(BigDecimal.valueOf(delivery.deliveredGrams), 0, RoundingMode.HALF_UP).longValueExact()
}
