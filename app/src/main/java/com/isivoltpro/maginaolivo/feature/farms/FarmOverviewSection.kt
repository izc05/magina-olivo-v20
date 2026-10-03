package com.isivoltpro.maginaolivo.feature.farms

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.analytics.CurrencyTotal
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.analytics.FarmSeasonFigures
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStat
import com.isivoltpro.maginaolivo.ui.components.MoStatStrip
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.util.UUID

/** #359: weighed kilos, «—» when nothing was weighed (never «0 kg»). */
internal fun overviewKilos(delivery: DeliverySummary): String =
    if (delivery.deliveryCount == 0) "—" else Weight.format(delivery.deliveredGrams)

/** Weighted yield, «—» without any analysis. */
internal fun overviewYield(delivery: DeliverySummary): String =
    delivery.fatYield?.let { Percent.format(it.hundredths) } ?: "—"

/** One total per currency; «—» without posted costs. */
internal fun overviewCost(costs: List<CurrencyTotal>): String =
    if (costs.isEmpty()) "—" else costs.joinToString(" · ") { total ->
        total.amountMinor?.let { Money.format(it, total.currency) } ?: "Importe no disponible (${total.currency})"
    }

/** Total cost over total kilos, in the one currency; «—» otherwise. */
internal fun overviewCostPerKg(costPerKgMinor: Long?, costs: List<CurrencyTotal>): String =
    costPerKgMinor?.let { minor -> costs.singleOrNull()?.let { "${Money.format(minor, it.currency)}/kg" } } ?: "—"

/** «Análisis sobre el 75 % de los kilos · 2 de 3 fincas con campaña · Sin campaña: Los Llanos». */
internal fun overviewNote(overview: FarmOverview): String = listOfNotNull(
    overview.delivery.fatYield?.let { "Análisis sobre el ${overview.yieldCoveragePercent} % de los kilos" },
    overview.costs.mapNotNull { total -> total.labourMinor?.let { Money.format(it, total.currency) } }
        .takeIf { it.isNotEmpty() }?.let { "Jornales ${it.joinToString(" · ")}" },
    "${overview.farms.size} de ${overview.farms.size + overview.farmsWithoutCampaign.size} fincas con campaña ${overview.season}",
    overview.farmsWithoutCampaign.takeIf { it.isNotEmpty() }?.let { "Sin campaña: ${it.joinToString(", ")}" },
    if (overview.costs.size > 1) "Varias monedas: sin coste por kilo conjunto" else null,
).joinToString(" · ")

/** A Farm's line: «3.000 kg · 75 % del total · rend. 20,00 % · 0,10 €/kg». */
internal fun overviewFarmLine(overview: FarmOverview, farm: FarmSeasonFigures): String = listOf(
    overviewKilos(farm.delivery),
    overview.sharePercent(farm)?.let { "$it % del total" } ?: "sin kilos",
    "rend. ${overviewYield(farm.delivery)}",
    overviewCostPerKg(farm.costPerKgMinor, farm.costs).let { if (it == "—") "coste/kg —" else it },
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
    Column(Modifier.fillMaxWidth().testTag("farm-overview"), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        MoSectionHeader("Resumen de la explotación")
        if (overviews.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                overviews.forEach { option ->
                    FilterChip(
                        selected = option.season == overview.season,
                        onClick = { seasonName = option.season },
                        label = { Text("Campaña ${option.season}") },
                        modifier = Modifier.testTag("farm-overview-season"),
                    )
                }
            }
        }
        MoStatStrip(listOf(
            MoStat("Kg pesados", overviewKilos(overview.delivery), MoIcons.Delivery),
            MoStat("Rendimiento", overviewYield(overview.delivery), MoIcons.Percent),
        ), Modifier.testTag("farm-overview-production"))
        MoStatStrip(listOf(
            MoStat("Coste recogida", overviewCost(overview.costs), MoIcons.Euro),
            MoStat("Coste/kg", overviewCostPerKg(overview.costPerKgMinor, overview.costs), MoIcons.Euro),
        ), Modifier.testTag("farm-overview-costs"))
        Text(overviewNote(overview), style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, modifier = Modifier.testTag("farm-overview-note"))
        if (overview.farms.isNotEmpty()) {
            MoTertiaryButton(if (byFarm) "Ocultar por finca" else "Ver por finca", { byFarm = !byFarm }, Modifier.testTag("farm-overview-by-farm"))
        }
        if (byFarm) {
            overview.farms.forEach { farm ->
                MoCompactListItem(
                    title = farm.farmName,
                    subtitle = overviewFarmLine(overview, farm),
                    icon = MoIcons.Tree,
                    onClick = { onFarmSelected(farm.farmId) },
                    modifier = Modifier.testTag("farm-overview-farm"),
                )
            }
        }
    }
}
