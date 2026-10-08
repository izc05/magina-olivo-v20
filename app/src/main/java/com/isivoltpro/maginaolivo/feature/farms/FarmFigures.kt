package com.isivoltpro.maginaolivo.feature.farms

import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/*
 * #359/#363/#364 — the figures Mis fincas shows, derived from canonical records only: nothing
 * is stored twice and an unknown figure reads «—», never 0.
 */

/** Olive trees across Farms: the known sum, «≥ N» when some count is missing, «—» with none. */
internal fun oliveTreesLabel(farms: List<Farm>): String {
    val known = farms.mapNotNull { it.oliveTreeCount }
    if (known.isEmpty()) return "—"
    val number = NumberFormat.getIntegerInstance(SPANISH).format(known.sum())
    return if (farms.all { it.oliveTreeCountComplete }) number else "≥ $number"
}

/**
 * Pesada kilos of each Farm's running Campaign, only for Farms that have one. A Farm with a
 * running Campaign and no Pesadas maps to 0 («Sin pesadas»); a Farm without one is absent.
 */
internal fun runningCampaignKilos(contexts: List<HarvestContext>, deliveries: List<Delivery>): Map<UUID, Long?> =
    contexts.associate { context ->
        val own = deliveries.filter { it.farmId == context.farmId && it.campaignId == context.campaignId }
        context.farmId to if (own.isEmpty()) 0L else DeliverySummary.of(own).deliveredGrams
    }

/** The card's «Kg campaña»: weighed kilos, «Sin pesadas», or «—» without a running Campaign. */
internal fun campaignKilosLabel(grams: Long?): String = when (grams) {
    null -> "—"
    0L -> "Sin pesadas"
    else -> Weight.format(grams)
}

private val SPANISH = Locale.forLanguageTag("es-ES")
