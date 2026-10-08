package com.isivoltpro.maginaolivo.feature.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
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
import com.isivoltpro.maginaolivo.domain.analytics.CampaignHistory
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
    val knownTotal = series.deliveredGrams
    val kgKnown = knownTotal != null && series.days.all { it.deliveredGrams != null && it.cumulativeDeliveredGrams != null }
    val summary = if (kgKnown) {
        "Pesado ${Weight.format(knownTotal!!)} en ${series.days.count { it.deliveryCount > 0 }} días, " +
            "del ${first.format(SHORT_DAY)} al ${last.format(SHORT_DAY)}"
    } else {
        "Kilos no disponibles por un total histórico inconsistente"
    }
    Text("Kilos pesados por día y acumulado", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
    if (kgKnown) {
        val bar = MaterialTheme.colorScheme.primary
        val line = MoOliveDark
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).testTag("chart-kg").semantics { contentDescription = summary },
        ) {
            val maxDay = series.days.maxOf { it.deliveredGrams ?: 0L }.coerceAtLeast(1)
            val maxCumulative = knownTotal!!.coerceAtLeast(1)
            val slot = size.width / series.days.size
            val barWidth = (slot * 0.6f).coerceAtLeast(2f)
            val path = Path()
            series.days.forEachIndexed { index, day ->
                val daily = day.deliveredGrams ?: return@forEachIndexed
                val cumulative = day.cumulativeDeliveredGrams ?: return@forEachIndexed
                val x = slot * index + (slot - barWidth) / 2
                val height = size.height * daily / maxDay
                if (daily > 0) {
                    drawRect(bar.copy(alpha = 0.55f), topLeft = Offset(x, size.height - height), size = Size(barWidth, height))
                }
                val cx = slot * index + slot / 2
                val cy = size.height - size.height * cumulative / maxCumulative
                if (index == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
            }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
        }
    } else {
        Text("La gráfica de kilos se oculta para no dibujar un total incorrecto.", color = MoTextSecondary, modifier = Modifier.testTag("chart-kg-unavailable"))
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
            "${analysed.size} de ${series.days.count { it.deliveryCount > 0 }} días con pesadas analizadas"
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
                        (cooperative.summary.deliveredGrams?.let(Weight::format) ?: "Kilos no disponibles") +
                            (cooperative.summary.fatYield?.let {
                                cooperative.coveragePercent?.let { coverage -> " · análisis sobre el $coverage %" }
                                    ?: " · cobertura no disponible"
                            } ?: " · sin análisis"),
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
internal fun CampaignComparisonList(rows: List<CampaignComparison>, onSelectCampaign: (java.util.UUID) -> Unit = {}) {
    if (rows.size < 2) return
    MoSectionHeader("Comparar campañas")
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        rows.reversed().forEach { row ->
            // #355: a campaign opens here, as from the history above.
            Column(Modifier.fillMaxWidth().clickable { onSelectCampaign(row.campaign.id) }.testTag("comparison-row")) {
                Text(row.campaign.name, style = MaterialTheme.typography.titleSmall, color = MoOliveDark)
                Text(
                    listOf(
                        row.deliveredGrams?.let { kg ->
                            Weight.format(kg) + (row.deliveredChangePercent?.let { if (it >= 0) " (+$it %)" else " ($it %)" } ?: "")
                        } ?: "Pesado: sin datos",
                        row.fatYield?.let {
                            row.yieldCoveragePercent?.let { coverage -> "rend. ${Percent.format(it.hundredths)} sobre el $coverage %" }
                                ?: "rend. ${Percent.format(it.hundredths)} · cobertura no disponible"
                        } ?: "rend. sin datos",
                        if (row.costsByCurrency.isEmpty()) "coste/kg sin datos" else row.costsByCurrency.joinToString(" · ") { cost ->
                            cost.costPerKgMilli?.let { "coste ${com.isivoltpro.maginaolivo.domain.expense.CostPerKg.format(it, cost.currency)}" + if (row.costComplete) "" else " (incompleto)" }
                                ?: "coste/kg sin datos (${cost.currency})"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MoTextSecondary,
                    modifier = Modifier.testTag("comparison-line"),
                )
                val legacy = row.legacyUnweighedGrams
                if (legacy == null || legacy > 0) {
                    // CR-010 (A2): disclosed apart, never added to the weighed kilos above.
                    Text(
                        legacy?.let { "Además, ${Weight.format(it)} registrados sin pesada (histórico)" }
                            ?: "Kilos históricos sin pesada no disponibles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoTextSecondary,
                        modifier = Modifier.testTag("comparison-legacy-kilos"),
                    )
                }
            }
        }
    }
}

/** #355: «2024/25: sin datos · 2025/26: 4.000 kg». The text twin of the kilos chart. */
internal fun historyKilosLine(history: CampaignHistory): String =
    history.points.joinToString(" · ") { point -> "${point.name}: ${point.deliveredGrams?.let(Weight::format) ?: "sin datos"}" }

/** «2025/26: sin análisis · 2026/27: 21,00 % sobre el 100 %». */
internal fun historyYieldLine(history: CampaignHistory): String =
    history.points.joinToString(" · ") { point ->
        "${point.name}: " + (point.yieldHundredths?.let {
            point.yieldCoveragePercent?.let { coverage -> "${Percent.format(it)} sobre el $coverage %" }
                ?: "${Percent.format(it)} · cobertura no disponible"
        } ?: "sin análisis")
    }

/** «2025/26: sin datos · 2026/27: 0,253 €/kg», in the one currency of the series. */
internal fun historyCostLine(history: CampaignHistory): String {
    val currency = history.costCurrency
    val line = history.points.joinToString(" · ") { point ->
        "${point.name}: " + (point.costPerKgMilli?.let { minor ->
            if (currency != null) com.isivoltpro.maginaolivo.domain.expense.CostPerKg.format(minor, currency) + if (point.costIncomplete) " (incompleto)" else "" else null
        } ?: "sin datos")
    }
    return if (history.otherCurrencyCampaigns.isEmpty()) line
    else "$line. En otra moneda, no dibujadas: ${history.otherCurrencyCampaigns.joinToString(", ")}"
}

/**
 * #355 — the Farm's campaigns side by side: weighed kilos, weighted yield and cost per kilo.
 * Drawn from [CampaignHistory] (the comparison as it is); each chart has its text twin, an
 * unknown value is a gap (never a zero) and each campaign under the charts opens it.
 */
@Composable
internal fun CampaignHistoryCharts(history: CampaignHistory, selected: java.util.UUID? = null, onSelectCampaign: (java.util.UUID) -> Unit = {}) {
    if (history.points.size < 2) return
    MoSectionHeader("Histórico de campañas")
    val points = history.points
    val accent = MaterialTheme.colorScheme.primary
    val ink = MoOliveDark

    Text("Kilos pesados por campaña", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
    val kilosLine = historyKilosLine(history)
    if (history.hasKilos) {
        Canvas(Modifier.fillMaxWidth().height(110.dp).testTag("history-kg").semantics { contentDescription = kilosLine }) {
            val max = points.maxOf { it.deliveredGrams ?: 0L }.coerceAtLeast(1)
            val slot = size.width / points.size
            val barWidth = (slot * 0.55f).coerceAtLeast(2f)
            points.forEachIndexed { index, point ->
                val grams = point.deliveredGrams ?: return@forEachIndexed
                val height = size.height * grams / max
                drawRect(accent.copy(alpha = if (point.campaignId == selected) 0.9f else 0.5f),
                    topLeft = Offset(slot * index + (slot - barWidth) / 2, size.height - height), size = Size(barWidth, height))
            }
        }
    }
    Text(kilosLine, style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, modifier = Modifier.testTag("history-kg-summary"))
    if (points.any { (it.legacyUnweighedGrams ?: 0L) > 0 }) {
        Text("Solo pesadas: los kilos registrados sin pesada (histórico) no están en las barras.",
            style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, modifier = Modifier.testTag("history-legacy-note"))
    }

    Text("Rendimiento graso medio", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
    val yieldLine = historyYieldLine(history)
    if (history.hasYield) {
        val analysed = points.mapNotNull { it.yieldHundredths }
        val low = analysed.min()
        val high = analysed.max()
        Canvas(Modifier.fillMaxWidth().height(80.dp).testTag("history-yield").semantics { contentDescription = yieldLine }) {
            drawSeries(points.map { it.yieldHundredths?.toLong() }, low.toLong(), high.toLong(), ink)
        }
    }
    Text(yieldLine, style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, modifier = Modifier.testTag("history-yield-summary"))

    Text("Coste de recogida por kilo", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
    val costLine = historyCostLine(history)
    if (history.hasCost) {
        val costs = points.mapNotNull { it.costPerKgMilli }
        Canvas(Modifier.fillMaxWidth().height(80.dp).testTag("history-cost").semantics { contentDescription = costLine }) {
            drawSeries(points.map { it.costPerKgMilli }, costs.min(), costs.max(), ink)
        }
    }
    Text(costLine, style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, modifier = Modifier.testTag("history-cost-summary"))

    // The campaigns under the charts, in the same order; each opens that campaign. Codex #383:
    // each is a 48 dp target; when they no longer fit under their columns the row scrolls.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fits = maxWidth / points.size >= 48.dp
        val row = if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
        Row(row) {
            points.forEach { point ->
                val slot = if (fits) Modifier.weight(1f) else Modifier.width(72.dp)
                androidx.compose.foundation.layout.Box(
                    slot.heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { onSelectCampaign(point.campaignId) }
                        .testTag("history-campaign"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        point.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (point.campaignId == selected) MoOliveDark else MoTextSecondary,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Dots joined only between known neighbours: a gap stays a gap. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(values: List<Long?>, low: Long, high: Long, color: androidx.compose.ui.graphics.Color) {
    val slot = size.width / values.size
    val span = (high - low).coerceAtLeast(1)
    val pad = 8.dp.toPx()
    fun y(value: Long) = size.height - pad - (size.height - 2 * pad) * (value - low) / span
    values.forEachIndexed { index, value ->
        value ?: return@forEachIndexed
        val center = Offset(slot * index + slot / 2, y(value))
        drawCircle(color, radius = 5.dp.toPx(), center = center)
        val next = values.getOrNull(index + 1) ?: return@forEachIndexed
        drawLine(color, center, Offset(slot * (index + 1) + slot / 2, y(next)), strokeWidth = 2.dp.toPx())
    }
}
