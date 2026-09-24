package com.isivoltpro.maginaolivo.feature.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.analytics.CampaignComparison
import com.isivoltpro.maginaolivo.domain.analytics.CampaignSeries
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES"))

/**
 * Phase 19G — the Campaign's charts. Each chart is drawn from [CampaignSeries] and carries a
 * text line with the same numbers, so what is drawn always reconciles with the records and
 * can be read without looking at the drawing. Unknown values are gaps, never zeros.
 */
@Composable
internal fun CampaignCharts(series: CampaignSeries) {
    MoSectionHeader("Gráficas")
    if (series.isEmpty) {
        Text("Sin datos de recolección todavía.", color = MoTextSecondary, modifier = Modifier.testTag("chart-empty"))
        return
    }
    val first = series.days.first().date
    val last = series.days.last().date
    val summary = "Entregado ${Weight.format(series.deliveredGrams)} en ${series.days.count { it.deliveredGrams > 0 }} días, " +
        "del ${first.format(SHORT_DAY)} al ${last.format(SHORT_DAY)}"
    Text("Kilos entregados por día y acumulado", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
    val bar = MaterialTheme.colorScheme.primary
    val line = MoOliveDark
    Canvas(
        Modifier.fillMaxWidth().height(140.dp).testTag("chart-kg").semantics { contentDescription = summary },
    ) {
        val maxDay = series.days.maxOf { it.deliveredGrams }.coerceAtLeast(1)
        val maxCumulative = series.deliveredGrams.coerceAtLeast(1)
        val slot = size.width / series.days.size
        val barWidth = (slot * 0.6f).coerceAtLeast(2f)
        val path = Path()
        series.days.forEachIndexed { index, day ->
            val x = slot * index + (slot - barWidth) / 2
            val height = size.height * day.deliveredGrams / maxDay
            if (day.deliveredGrams > 0) {
                drawRect(bar.copy(alpha = 0.55f), topLeft = Offset(x, size.height - height), size = Size(barWidth, height))
            }
            val cx = slot * index + slot / 2
            val cy = size.height - size.height * day.cumulativeDeliveredGrams / maxCumulative
            if (index == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
        }
        drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
    }
    Text(summary, style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, modifier = Modifier.testTag("chart-kg-summary"))

    val analysed = series.days.filter { it.fatYield != null }
    Text("Rendimiento graso por día", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
    if (analysed.isEmpty()) {
        Text("Aún no hay análisis de rendimiento.", color = MoTextSecondary, modifier = Modifier.testTag("chart-yield-empty"))
    } else {
        val low = analysed.minOf { it.fatYield!!.hundredths }
        val high = analysed.maxOf { it.fatYield!!.hundredths }
        val yieldSummary = "De ${Percent.format(low)} a ${Percent.format(high)}; " +
            "${analysed.size} de ${series.days.count { it.deliveredGrams > 0 }} días de entrega con análisis"
        val dot = MoOliveDark
        Canvas(
            Modifier.fillMaxWidth().height(90.dp).testTag("chart-yield").semantics { contentDescription = yieldSummary },
        ) {
            val slot = size.width / series.days.size
            val span = (high - low).coerceAtLeast(1)
            series.days.forEachIndexed { index, day ->
                val value = day.fatYield?.hundredths ?: return@forEachIndexed
                val cx = slot * index + slot / 2
                val cy = size.height - 8.dp.toPx() - (size.height - 16.dp.toPx()) * (value - low) / span
                drawCircle(dot, radius = 5.dp.toPx(), center = Offset(cx, cy))
            }
        }
        Text(yieldSummary, style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, modifier = Modifier.testTag("chart-yield-summary"))
    }

    if (series.cooperatives.isNotEmpty()) {
        MoSectionHeader("Por cooperativa")
        series.cooperatives.forEach { cooperative ->
            Row(Modifier.fillMaxWidth().testTag("chart-cooperative"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cooperative.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        Weight.format(cooperative.summary.deliveredGrams) +
                            (cooperative.summary.fatYield?.let { " · análisis sobre el ${cooperative.coveragePercent} %" } ?: " · sin análisis"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MoTextSecondary,
                    )
                }
                Text(
                    cooperative.summary.fatYield?.let { Percent.format(it.hundredths) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MoOliveDark,
                )
            }
        }
    }
}

/**
 * Phase 19G — year over year for the Farm. Cost per kilo appears only when there are both
 * posted costs and delivered kilos; anything unknown reads "sin datos".
 */
@Composable
internal fun CampaignComparisonList(rows: List<CampaignComparison>) {
    if (rows.size < 2) return
    MoSectionHeader("Comparar campañas")
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        rows.reversed().forEach { row ->
            Column(Modifier.fillMaxWidth().testTag("comparison-row")) {
                Text(row.campaign.name, style = MaterialTheme.typography.titleSmall, color = MoOliveDark)
                Text(
                    listOf(
                        row.deliveredGrams?.let { kg ->
                            Weight.format(kg) + (row.deliveredChangePercent?.let { if (it >= 0) " (+$it %)" else " ($it %)" } ?: "")
                        } ?: "Entregado: sin datos",
                        row.fatYield?.let { "rend. ${Percent.format(it.hundredths)} sobre el ${row.yieldCoveragePercent} %" } ?: "rend. sin datos",
                        row.costPerKgMinor?.let { "coste ${Money.format(it, row.currency)}/kg" } ?: "coste/kg sin datos",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MoTextSecondary,
                    modifier = Modifier.testTag("comparison-line"),
                )
            }
        }
    }
}
