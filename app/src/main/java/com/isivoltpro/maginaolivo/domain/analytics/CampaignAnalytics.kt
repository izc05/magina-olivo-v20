package com.isivoltpro.maginaolivo.domain.analytics

import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.WeightedYield
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.domain.notebook.legacyUnweighedGrams
import java.time.LocalDate
import java.util.UUID

/**
 * Phase 19G — one day of a Campaign, derived from its canonical rows. A day with deliveries
 * but no analysis has `fatYield == null`: unknown, never zero, never interpolated.
 */
data class DayPoint(
    val date: LocalDate,
    val harvestedGrams: Long,
    val deliveredGrams: Long,
    val cumulativeDeliveredGrams: Long,
    val fatYield: WeightedYield?,
)

/** A cooperative's weighted yield and how many of its kilos carry an analysis. */
data class CooperativeYield(val name: String, val summary: DeliverySummary) {
    val coveragePercent: Int get() = summary.coveragePercent(summary.fatYield)
}

/**
 * The Campaign's series for its charts. Every total here is recomputed from the same rows the
 * Cuaderno lists, so the charts reconcile with `HarvestSummary`/`DeliverySummary` exactly.
 */
data class CampaignSeries(val days: List<DayPoint>, val cooperatives: List<CooperativeYield>) {
    val deliveredGrams: Long get() = days.lastOrNull()?.cumulativeDeliveredGrams ?: 0
    val harvestedGrams: Long get() = days.sumOf { it.harvestedGrams }
    val isEmpty: Boolean get() = days.isEmpty()

    companion object {
        fun of(notebook: CampaignNotebook): CampaignSeries {
            val harvestByDay = notebook.harvests.filterNot { it.awaitingPesadas }.groupBy { it.harvestDate }
            val deliveryByDay = notebook.deliveries.groupBy { it.deliveryDate }
            var cumulative = 0L
            val days = (harvestByDay.keys + deliveryByDay.keys).sorted().map { date ->
                val delivered = deliveryByDay[date].orEmpty()
                val deliveredGrams = delivered.sumOf { it.netGrams }
                cumulative += deliveredGrams
                DayPoint(
                    date = date,
                    harvestedGrams = harvestByDay[date].orEmpty().sumOf { it.totalGrams },
                    deliveredGrams = deliveredGrams,
                    cumulativeDeliveredGrams = cumulative,
                    fatYield = if (delivered.isEmpty()) null else DeliverySummary.of(delivered).fatYield,
                )
            }
            val cooperatives = notebook.deliveries
                .groupBy { delivery ->
                    delivery.destinationOrganizationId
                        ?.let { CooperativeYieldKey.Organization(it) }
                        ?: CooperativeYieldKey.Manual(delivery.destinationName.normalizedDestination())
                }
                .map { (key, rows) ->
                    val name = when (key) {
                        is CooperativeYieldKey.Organization -> rows.latestDestinationName()
                        is CooperativeYieldKey.Manual -> rows.first().destinationName.trim()
                    }
                    CooperativeYield(name, DeliverySummary.of(rows))
                }
                .sortedByDescending { it.summary.deliveredGrams }
            return CampaignSeries(days, cooperatives)
        }
    }
}

private sealed interface CooperativeYieldKey {
    data class Organization(val id: UUID) : CooperativeYieldKey
    data class Manual(val normalizedName: String) : CooperativeYieldKey
}

private fun String.normalizedDestination(): String =
    trim().lowercase().replace(Regex("""\s+"""), " ")

private fun List<Delivery>.latestDestinationName(): String =
    maxWithOrNull(compareBy<Delivery>({ it.deliveryDate }, { it.id.toString() }))
        ?.destinationName
        ?.trim()
        .orEmpty()

/**
 * One Campaign in the year-over-year comparison. Cost per kilo exists only when both sides
 * are known — posted money and delivered kilos; otherwise it is `null` ("sin datos").
 */
data class CampaignComparison(
    val campaign: Campaign,
    val harvestedGrams: Long?,
    val deliveredGrams: Long?,
    val fatYield: WeightedYield?,
    val yieldCoveragePercent: Int,
    /** Change in delivered kilos against the previous Campaign, in whole percent; null when unknown. */
    val deliveredChangePercent: Int?,
    /** CR-010 (A2): hand-typed kilos with no Pesada, disclosed apart and never in [deliveredGrams]. */
    val legacyUnweighedGrams: Long = 0,
    val canonicalCost: com.isivoltpro.maginaolivo.domain.expense.RecollectionCurrency? = null,
    val costsByCurrency: List<com.isivoltpro.maginaolivo.domain.expense.RecollectionCurrency> = listOfNotNull(canonicalCost),
    /** #449: false while jornales, machinery or costs of the Campaign are still unconfirmed. */
    val costComplete: Boolean = true,
) {
    /** Minor units (cents) per delivered kilo. */
    val costPerKgMilli: Long?
        get() = canonicalCost?.costPerKgMilli

    companion object {
        /** Oldest first; each Campaign compared with the one before it. */
        fun of(notebooks: List<CampaignNotebook>): List<CampaignComparison> {
            var previousDelivered: Long? = null
            return notebooks.sortedBy { it.campaign.startDate }.map { notebook ->
                val deliveries = notebook.deliverySummary
                val delivered = deliveries.deliveredGrams.takeIf { deliveries.deliveryCount > 0 }
                val change = if (delivered != null && previousDelivered != null && previousDelivered!! > 0) {
                    Math.round((delivered - previousDelivered!!) * 100.0 / previousDelivered!!).toInt()
                } else {
                    null
                }
                previousDelivered = delivered
                val costs = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.of(notebook.campaign.id, notebook.expenses, notebook.deliveries)
                val cost = costs.singleOrNull()
                CampaignComparison(
                    campaign = notebook.campaign,
                    harvestedGrams = notebook.harvestSummary.totalGrams.takeIf { notebook.harvestSummary.weighedCount > 0 },
                    deliveredGrams = delivered,
                    fatYield = deliveries.fatYield,
                    yieldCoveragePercent = deliveries.coveragePercent(deliveries.fatYield),
                    deliveredChangePercent = change,
                    legacyUnweighedGrams = notebook.legacyUnweighedGrams,
                    canonicalCost = cost,
                    costsByCurrency = costs,
                    costComplete = notebook.costCompleteness.complete,
                )
            }
        }
    }
}
