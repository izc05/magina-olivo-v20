package com.isivoltpro.maginaolivo.domain.analytics

import java.util.UUID

/**
 * #355 — one Campaign in the Farm's history charts, read straight from [CampaignComparison]:
 * nothing is totalled again here. Unknown values are null and drawn as gaps, never as zeros.
 */
data class CampaignHistoryPoint(
    val campaignId: UUID,
    val name: String,
    /** Canonical Pesadas only; legacy kilos without a Pesada stay in [legacyUnweighedGrams]. */
    val deliveredGrams: Long?,
    val legacyUnweighedGrams: Long,
    val yieldHundredths: Int?,
    val yieldCoveragePercent: Int?,
    /** Cost per kilo in [CampaignHistory.costCurrency] only; another currency is a gap. */
    val costPerKgMilli: Long?,
    /** #449: the cost per kilo is only what is confirmed so far; said beside it, never hidden. */
    val costIncomplete: Boolean = false,
)

/**
 * The Farm's campaigns, oldest first, as three series: weighed kilos, weighted fat yield and
 * recollection cost per kilo. The cost series uses one currency only — the latest campaign's
 * with a cost per kilo — so currencies are never mixed in one line.
 */
data class CampaignHistory(
    val points: List<CampaignHistoryPoint>,
    val costCurrency: String?,
    /** Campaigns whose cost per kilo is in another currency, or in several: said, not drawn. */
    val otherCurrencyCampaigns: List<String> = emptyList(),
) {
    val hasKilos: Boolean get() = points.any { it.deliveredGrams != null }
    val hasYield: Boolean get() = points.any { it.yieldHundredths != null }
    val hasCost: Boolean get() = points.any { it.costPerKgMilli != null }

    companion object {
        fun of(rows: List<CampaignComparison>): CampaignHistory {
            val sorted = rows.sortedBy { it.campaign.startDate }
            val currency = sorted.lastOrNull { it.singleCostPerKg() != null }?.costsByCurrency?.single()?.currency
            val points = sorted.map { row ->
                CampaignHistoryPoint(
                    campaignId = row.campaign.id,
                    name = row.campaign.name,
                    deliveredGrams = row.deliveredGrams,
                    legacyUnweighedGrams = row.legacyUnweighedGrams,
                    yieldHundredths = row.fatYield?.hundredths,
                    yieldCoveragePercent = row.yieldCoveragePercent,
                    costPerKgMilli = row.singleCostPerKg()?.takeIf { row.costsByCurrency.single().currency == currency },
                    costIncomplete = !row.costComplete,
                )
            }
            val other = sorted.filter { row ->
                row.costsByCurrency.any { it.costPerKgMilli != null } &&
                    (row.costsByCurrency.size > 1 || row.costsByCurrency.single().currency != currency)
            }.map { it.campaign.name }
            return CampaignHistory(points, currency, other)
        }

        /** A cost per kilo only when the campaign has exactly one currency. */
        private fun CampaignComparison.singleCostPerKg(): Long? = costsByCurrency.singleOrNull()?.costPerKgMilli
    }
}
