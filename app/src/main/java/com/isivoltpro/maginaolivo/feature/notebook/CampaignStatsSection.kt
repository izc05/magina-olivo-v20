package com.isivoltpro.maginaolivo.feature.notebook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.analytics.CampaignCostSplit
import com.isivoltpro.maginaolivo.domain.analytics.CampaignSeries
import com.isivoltpro.maginaolivo.domain.analytics.ParcelKiloSlice
import com.isivoltpro.maginaolivo.domain.analytics.ParcelKiloSplit
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.ui.charts.MoChartPalette
import com.isivoltpro.maginaolivo.ui.charts.MoDonut
import com.isivoltpro.maginaolivo.ui.charts.MoDonutSlice
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing

/**
 * #355 Fase 2, bloque D — «Estadísticas de campaña».
 *
 * **Folded by default, on purpose.** Someone standing in the olive grove with a full trailer wants
 * to write down a Pesada; four charts between them and that button would be four charts in the way.
 * Whoever came to look at the figures opens it, and the choice is remembered while the screen lives.
 *
 * Four charts, each a different shape because each answers a different kind of question: two rings
 * for shares (which parcel, which cost), bars for an amount per day, a line for a trend. The
 * campaign's history and the year-on-year comparison stay where they were, outside this section:
 * they are about other campaigns, not this one.
 */
@Composable
internal fun CampaignStatsSection(notebook: CampaignNotebook, series: CampaignSeries) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button) { open = !open }
                .testTag("campaign-stats-toggle"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // El titulo se queda con el ancho sobrante; sin esto, «Ocultar» se escribia en vertical.
            Box(Modifier.weight(1f)) { MoSectionHeader("Estadísticas de campaña") }
            Text(
                if (open) "Ocultar" else "Ver",
                style = MaterialTheme.typography.labelLarge,
                color = MoColors.current.actionText,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = MoSpacing.xs),
            )
        }
        AnimatedVisibility(open) {
            Column(
                Modifier.fillMaxWidth().testTag("campaign-stats"),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
            ) {
                ParcelKiloDonut(notebook)
                CostDonuts(notebook)
                // Bars for the kilos of each day and a line for the fat yield: already drawn from
                // these same records, with their dates, units and legend.
                CampaignCharts(series)
            }
        }
    }
}

/** Ring 1 — which parcel the kilos came from, with the mixed Pesadas as their own slice. */
@Composable
private fun ParcelKiloDonut(notebook: CampaignNotebook) {
    val split = ParcelKiloSplit.of(
        deliveredGrams = notebook.deliverySummary.deliveredGrams,
        attributed = notebook.parcelYields.map { ParcelKiloSlice(it.parcelId, it.parcelName, it.attributedGrams) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text("Kilos por parcela", style = MaterialTheme.typography.labelLarge, color = MoColors.current.secondaryText)
        if (split.isEmpty) {
            Text(
                "Todavía no se puede repartir por parcela.",
                style = MaterialTheme.typography.bodySmall,
                color = MoColors.current.secondaryText,
                modifier = Modifier.testTag("campaign-stats-parcels-empty"),
            )
            return@Column
        }
        var named = -1
        MoDonut(
            slices = split.slices.map { slice ->
                MoDonutSlice(
                    label = slice.label,
                    value = slice.grams,
                    formatted = Weight.format(slice.grams),
                    color = if (slice.isMixed) MoChartPalette.unassigned() else MoChartPalette.parcel(++named),
                    muted = slice.isMixed,
                )
            },
            centreValue = split.deliveredGrams?.let(Weight::format) ?: "—",
            centreLabel = "pesados",
            tag = "campaign-stats-parcels",
        )
    }
}

/** Ring 2 — where the money went, one ring per currency, never added together. */
@Composable
private fun CostDonuts(notebook: CampaignNotebook) {
    val splits = CampaignCostSplit.of(notebook.campaign.id, notebook.expenses, notebook.deliveries)
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text("Gastos por categoría", style = MaterialTheme.typography.labelLarge, color = MoColors.current.secondaryText)
        if (splits.isEmpty()) {
            Text(
                "Todavía no hay gastos contabilizados en esta campaña.",
                style = MaterialTheme.typography.bodySmall,
                color = MoColors.current.secondaryText,
                modifier = Modifier.testTag("campaign-stats-costs-empty"),
            )
            return@Column
        }
        splits.forEach { split ->
            val total = split.totalMinor ?: return@forEach
            MoDonut(
                slices = split.slices.map { (slice, amount) ->
                    MoDonutSlice(slice.label, amount, Money.format(amount, split.currency), MoChartPalette.cost(slice))
                },
                centreValue = Money.format(total, split.currency),
                centreLabel = "recogida",
                tag = "campaign-stats-costs",
            )
        }
    }
}
