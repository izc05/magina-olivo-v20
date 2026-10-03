package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.domain.harvest.ParcelHarvestTotal
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.expenses.DATE_FORMAT

/*
 * #366 — Recolección reads plainly with several Farms. A «día» is a calendar date: two Farms
 * harvesting on 3 oct are one day, not two. Every day row says which Farm it belongs to, and
 * kilos not split between Parcels are said as such, never shared out proportionally.
 */

/** Calendar days with harvest work, whatever the number of Farms that worked on each. */
internal fun harvestDayCount(harvests: List<Harvest>): Int = harvests.map { it.harvestDate }.distinct().size

internal fun harvestDays(count: Int): String = if (count == 1) "1 día" else "$count días"

/** Under the day count, only when it adds something: «En 2 fincas». */
internal fun harvestDaysSpread(harvests: List<Harvest>): String? {
    val farms = harvests.mapNotNull { it.farmId }.distinct().size
    return if (farms > 1) "En $farms fincas" else null
}

/** «Estacas · 3 oct 2026»; without a Farm name, the date and what it is. */
internal fun harvestRowTitle(harvest: Harvest): String {
    val date = DATE_FORMAT.format(harvest.harvestDate)
    return harvest.farmName?.takeIf { it.isNotBlank() }?.let { "$it · $date" } ?: "$date · Día de recolección"
}

/** A Farm/Campaign card's headline: its weighed kilos, or that nothing is weighed yet. */
internal fun campaignHarvestHeadline(summary: HarvestSummary): String =
    if (summary.weighedCount == 0) NO_PESADAS_YET else Weight.format(summary.totalGrams)

/** A Parcel's kilos on the card: exact, exact plus an unsplit part, or none assigned. */
internal fun parcelHarvestText(parcel: ParcelHarvestTotal): String = when {
    parcel.exactGrams > 0 && parcel.sharesUnallocated -> "${Weight.format(parcel.exactGrams)} + parte sin repartir"
    parcel.exactGrams > 0 -> Weight.format(parcel.exactGrams)
    else -> "Sin kg asignados"
}

/** «3.150 kg pendientes de repartir entre 2 parcelas», or null when everything is split. */
internal fun unallocatedLine(summary: HarvestSummary): String? {
    if (summary.unallocatedGrams <= 0) return null
    val parcels = summary.parcels.count { it.sharesUnallocated }
    val among = if (parcels > 1) " entre $parcels parcelas" else ""
    return "${Weight.format(summary.unallocatedGrams)} pendientes de repartir$among"
}

internal const val NO_PESADAS_YET = "Sin pesadas todavía"
