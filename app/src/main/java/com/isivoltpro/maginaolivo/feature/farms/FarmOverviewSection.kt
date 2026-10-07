package com.isivoltpro.maginaolivo.feature.farms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.analytics.CurrencyTotal
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.analytics.FarmSeasonFigures
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.CostPerKg
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoStat
import com.isivoltpro.maginaolivo.ui.components.MoStatStrip
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.util.UUID

/** #359: weighed kilos, «—» when nothing was weighed (never «0 kg»). */
internal fun overviewKilos(delivery: DeliverySummary): String =
    if (delivery.deliveryCount == 0) "—" else delivery.deliveredGrams?.let(Weight::format) ?: "No disponible"

/** Weighted yield, «—» without any analysis. */
internal fun overviewYield(delivery: DeliverySummary): String =
    delivery.fatYield?.let { Percent.format(it.hundredths) } ?: "—"

/** One total per currency; «—» without posted costs. */
internal fun overviewCost(costs: List<CurrencyTotal>): String =
    if (costs.isEmpty()) "—" else costs.joinToString(" · ") { total ->
        total.amountMinor?.let { Money.format(it, total.currency) } ?: "Importe no disponible (${total.currency})"
    }

/**
 * Total cost over total kilos, in the one currency; «—» otherwise. #449: built on unconfirmed
 * costs it says «(incompleto)» beside the figure.
 */
internal fun overviewCostPerKg(costPerKgMilli: Long?, costs: List<CurrencyTotal>, complete: Boolean? = true): String =
    costPerKgMilli?.let { minor ->
        costs.singleOrNull()?.let { CostPerKg.format(minor, it.currency) + if (complete == false) " (incompleto)" else "" }
    } ?: "—"

/** #359 follow-up: recollection plus general cost over the weighed kilos; «—» otherwise. */
internal fun overviewTotalCostPerKg(overview: FarmOverview): String =
    overviewCostPerKg(overview.totalCostPerKgMilli, overview.totalCosts, overview.costComplete)

/** Farms whose name or municipality contains [query], ignoring case and accents. */
internal fun matchesFarmSearch(query: String, name: String, municipality: String?): Boolean {
    val needle = query.trim().fold()
    if (needle.isEmpty()) return true
    return name.fold().contains(needle) || municipality?.fold()?.contains(needle) == true
}

private fun String.fold(): String =
    java.text.Normalizer.normalize(lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

/** «Análisis sobre el 75 % de los kilos · 2 de 3 fincas con campaña · Sin campaña: Los Llanos». */
internal fun overviewNote(overview: FarmOverview): String = listOfNotNull(
    overview.delivery.fatYield?.let { "Análisis sobre el ${overview.yieldCoveragePercent} % de los kilos" },
    overview.costs.mapNotNull { total -> total.labourMinor?.let { Money.format(it, total.currency) } }
        .takeIf { it.isNotEmpty() }?.let { "Jornales ${it.joinToString(" · ")}" },
    when (overview.costComplete) {
        false -> "Costes sin confirmar"
        null -> "Comprobando si faltan costes…"
        true -> null
    },
    "${overview.farms.size} de ${overview.farms.size + overview.farmsWithoutCampaign.size} fincas con campaña ${overview.season}",
    overview.farmsWithoutCampaign.takeIf { it.isNotEmpty() }?.let { "Sin campaña: ${it.joinToString(", ")}" },
    if (overview.costs.size > 1) "Varias monedas: sin coste por kilo conjunto" else null,
).joinToString(" · ")

/** A Farm's line: «3.000 kg · 75 % del total · rend. 20,00 % · 0,100 €/kg». */
internal fun overviewFarmLine(overview: FarmOverview, farm: FarmSeasonFigures): String = listOf(
    overviewKilos(farm.delivery),
    overview.sharePercent(farm)?.let { "$it % del total" } ?: "sin kilos",
    "rend. ${overviewYield(farm.delivery)}",
    overviewCostPerKg(farm.costPerKgMilli, farm.costs, farm.costComplete).let { if (it == "—") "coste/kg —" else it },
).joinToString(" · ")

/**
 * #359 — Mi Campo's «Resumen de la explotación»: the selected season of all the Farms at once,
 * four figures and a line of context; «Ver por finca» lists how each Farm adds to the total.
 * Everything comes from [FarmOverview]: the same Pesadas and posted ledger as each Farm.
 */
@Composable
internal fun FarmOverviewSection(overviews: List<FarmOverview>, onFarmSelected: (UUID) -> Unit) {
    if (overviews.isEmpty()) return
    var seasonName by rememberSaveable { mutableStateOf(overviews.first().season) }
    var byFarm by rememberSaveable { mutableStateOf(false) }
    val overview = overviews.firstOrNull { it.season == seasonName } ?: overviews.first()
    var periodMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("farm-overview"), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        // #359 follow-up: the period is always visible, so the kilos never read as «of all time».
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Resumen de la explotación", style = MaterialTheme.typography.titleMedium, color = MoOliveDark, modifier = Modifier.weight(1f))
            Box {
                if (overviews.size > 1) {
                    TextButton(onClick = { periodMenu = true }, modifier = Modifier.testTag("farm-overview-period")) {
                        Text("Campaña ${overview.season} ▾")
                    }
                    DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                        overviews.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("Campaña ${option.season}") },
                                onClick = { seasonName = option.season; periodMenu = false },
                                modifier = Modifier.testTag("farm-overview-season"),
                            )
                        }
                    }
                } else {
                    Text("Campaña ${overview.season}", color = MoTextSecondary, modifier = Modifier.testTag("farm-overview-period"))
                }
            }
        }
        MoStatStrip(listOf(
            MoStat("Kg pesados", overviewKilos(overview.delivery), MoIcons.Delivery),
            MoStat("Rendimiento", overviewYield(overview.delivery), MoIcons.Percent),
        ), Modifier.testTag("farm-overview-production"))
        MoStatStrip(listOf(
            MoStat("Coste recogida", overviewCost(overview.costs), MoIcons.Euro),
            MoStat("Coste recogida/kg", overviewCostPerKg(overview.costPerKgMilli, overview.costs, overview.costComplete), MoIcons.Euro),
        ), Modifier.testTag("farm-overview-costs"))
        Text(overviewNote(overview), style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, modifier = Modifier.testTag("farm-overview-note"))
        // #359 follow-up: costs linked to no campaign are shown apart, never inside the recollection cost/kg.
        Text("Gastos generales del periodo", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
        MoStatStrip(listOf(
            MoStat("Fuera de campaña", overviewCost(overview.generalCosts), MoIcons.Euro),
            MoStat("Coste total", overviewCost(overview.totalCosts), MoIcons.Euro),
            MoStat("Coste total/kg", overviewTotalCostPerKg(overview), MoIcons.Euro),
        ), Modifier.testTag("farm-overview-general"))
        if (overview.farms.isNotEmpty()) {
            MoTertiaryButton(if (byFarm) "Ocultar por finca" else "Ver por finca", { byFarm = !byFarm }, Modifier.testTag("farm-overview-by-farm"))
        }
        if (byFarm) {
            overview.farms.forEach { farm ->
                MoCompactListItem(
                    title = if (farm.archived) "${farm.farmName} · Archivada" else farm.farmName,
                    subtitle = overviewFarmLine(overview, farm),
                    icon = MoIcons.Tree,
                    onClick = { onFarmSelected(farm.farmId) },
                    modifier = Modifier.testTag("farm-overview-farm"),
                )
            }
        }
    }
}
