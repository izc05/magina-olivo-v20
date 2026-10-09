package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.market.OilCategory
import com.isivoltpro.maginaolivo.domain.market.OilMarketSeries
import com.isivoltpro.maginaolivo.domain.market.OilTrends
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.flowOf

@Composable
fun OilMarketRoute(persistence: LocalPersistence) {
    val feed = persistence.oilMarketFeed
    val flow = remember(feed) { feed?.observe() ?: flowOf<FeedState<OilMarketSeries>>(FeedState.NotConfigured) }
    val state: FeedState<OilMarketSeries> by flow.collectAsStateWithLifecycle(FeedState.Unavailable)
    LaunchedEffect(feed) { feed?.refreshIfStale() }
    OilMarketScreen(state, pulse = { AoveNetPulse() })
}

/**
 * Phase 20D-3 — «Mercado del aceite»: AOVE.net's daily pulse (publisher-hosted, online only) and
 * the official Junta weeks: this week's change per category and the last 12 weeks drawn as one
 * line per category. A week the source did not publish is a gap in the line, never a guess, and
 * the chart always comes with the same numbers in words, the source and when it was fetched.
 */
@Composable
fun OilMarketScreen(
    official: FeedState<OilMarketSeries>,
    pulse: (@Composable () -> Unit)?,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        Modifier.fillMaxSize().testTag("oil-market-root"),
        containerColor = MoSurfaceTokens.appBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Text("Mercado del aceite", style = MaterialTheme.typography.headlineLarge, color = MoColors.current.primaryText)
            pulse?.let {
                MoSectionHeader("Pulso diario")
                it()
            }
            MoSectionHeader("Tendencia oficial semanal")
            when (official) {
                is FeedState.Value -> {
                    OfficialTrend(official)
                    MoSectionHeader("Últimas 12 semanas")
                    OilMarketChart(official.value)
                    Text(
                        "Fuente: ${official.value.sourceName} · precios en almazara o bodega, €/kg · " +
                            "consultado ${FETCHED.format(official.fetchedAt.atZone(zone))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoColors.current.secondaryText,
                        modifier = Modifier.testTag("oil-market-source"),
                    )
                }
                FeedState.NotConfigured -> Note("Sin fuente configurada.", "oil-market-not-configured")
                FeedState.NoLocation, FeedState.Unavailable ->
                    Note("Aún sin datos oficiales. Se actualizará cuando haya conexión.", "oil-market-unavailable")
            }
            Spacer(Modifier.height(MoSpacing.lg))
        }
    }
}



@Composable
internal fun OilMarketChart(series: OilMarketSeries, compact: Boolean = false) {
    val lines = listOf(
        OilCategory.AOVE to MoColors.current.primaryText,
        OilCategory.AOV to MoColors.current.warningAccent,
        OilCategory.AOL to MoColors.current.earthText,
    )
    val weeks = OilTrends.chartWeeks(series)
    if (weeks.isEmpty()) {
        Note("Sin semanas publicadas todavía.", "oil-market-chart-empty")
        return
    }
    val values = lines.associate { (category, _) -> category to weeks.map { OilTrends.valueAt(series, category, it) } }
    val all = values.values.flatten().filterNotNull()
    val summary = chartSummary(weeks.first(), weeks.last(), values)
    val low = all.minOf { it }.toFloat()
    val high = all.maxOf { it }.toFloat()
    // A little room above and below so the lines never touch the edges; a flat market still draws.
    val span = (high - low).takeIf { it > 0f } ?: 0.1f
    val bottom = low - span * 0.15f
    val top = high + span * 0.15f
    val grid = MoSurfaceTokens.cardStroke
    Canvas(
        Modifier.fillMaxWidth().height(if (compact) 118.dp else 180.dp).testTag("oil-market-chart").semantics { contentDescription = summary },
    ) {
        val slot = if (weeks.size > 1) size.width / (weeks.size - 1) else 0f
        fun x(index: Int) = if (weeks.size > 1) slot * index else size.width / 2
        fun y(value: BigDecimal) = size.height - size.height * (value.toFloat() - bottom) / (top - bottom)
        listOf(0f, 0.5f, 1f).forEach { f ->
            val gy = size.height * f
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }
        lines.forEach { (category, color) ->
            val points = values.getValue(category)
            val path = Path()
            var drawing = false
            points.forEachIndexed { index, value ->
                if (value == null) {
                    drawing = false // a missing week breaks the line
                    return@forEachIndexed
                }
                val point = Offset(x(index), y(value))
                if (drawing) path.lineTo(point.x, point.y) else path.moveTo(point.x, point.y)
                drawing = true
                drawCircle(color, radius = 3.dp.toPx(), center = point)
            }
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Semana ${OilTrends.weekOf(weeks.first())}", style = MaterialTheme.typography.labelSmall, color = MoColors.current.secondaryText)
        Text("Semana ${OilTrends.weekOf(weeks.last())}", style = MaterialTheme.typography.labelSmall, color = MoColors.current.secondaryText)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        lines.forEach { (category, color) ->
            val latest = OilTrends.of(series, category)?.latest?.valueEurPerKg
            Row(
                Modifier.weight(1f).semantics(mergeDescendants = true) {}.testTag("oil-market-legend-${category.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
            ) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Text(
                    "${category.label}\n${latest?.let { OilTrends.euros(it) } ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoColors.current.primaryText,
                    maxLines = 2,
                )
            }
        }
    }
    if (!compact) Text(summary, style = MaterialTheme.typography.bodySmall, color = MoColors.current.secondaryText, modifier = Modifier.testTag("oil-market-chart-summary"))
}

/** The chart in words: range per category and how many weeks had no published price. */
internal fun chartSummary(
    first: LocalDate,
    last: LocalDate,
    values: Map<OilCategory, List<BigDecimal?>>,
): String {
    val weeks = values.values.firstOrNull()?.size ?: 0
    val parts = listOf(OilCategory.AOVE, OilCategory.AOV, OilCategory.AOL).mapNotNull { category ->
        val own = values[category].orEmpty()
        val known = own.filterNotNull()
        if (known.isEmpty()) return@mapNotNull "${category.label} sin datos"
        val missing = own.count { it == null }
        "${category.label} de ${OilTrends.euros(known.min())} a ${OilTrends.euros(known.max())}" +
            if (missing > 0) " ($missing ${if (missing == 1) "semana" else "semanas"} sin dato)" else ""
    }
    return "$weeks semanas, del ${first.format(DAY)} al ${last.plusDays(6).format(DAY)}: " + parts.joinToString("; ")
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-ES"))
private val FETCHED: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es-ES"))
