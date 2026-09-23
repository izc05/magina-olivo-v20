package com.isivoltpro.maginaolivo.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.ui.brand.MaginaOlivoWordmark
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoFieldArtwork
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoMetricGrid
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoSummaryMetric
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoInfo
import com.isivoltpro.maginaolivo.ui.theme.MoInfoText
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun HomeRoute(
    persistence: LocalPersistence,
    clock: AppClock,
    onOlivar: () -> Unit,
    onCalendar: () -> Unit,
    onHarvest: () -> Unit,
    onDeliveries: () -> Unit,
    onExpenses: () -> Unit,
    onActivitySelected: (UUID) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        key = "home",
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    persistence.workspaceRepository,
                    persistence.farmRepository,
                    persistence.activityRepository,
                    persistence.harvestRepository,
                    persistence.deliveryRepository,
                    clock,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, LocalTime.now(), onOlivar, onCalendar, onHarvest, onDeliveries, onExpenses, onActivitySelected)
}

/**
 * Inicio — the farmer's situation in seconds, from this phone's data only (VISUAL_DESIGN_LOCK
 * "Pantalla Inicio"): greeting, territory hero, olive-grove summary, running campaign,
 * upcoming work and quick access. Weather, oil market and cooperative notices keep their
 * place but say they arrive with their own phase — never sample figures.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    now: LocalTime,
    onOlivar: () -> Unit,
    onCalendar: () -> Unit,
    onHarvest: () -> Unit,
    onDeliveries: () -> Unit,
    onExpenses: () -> Unit,
    onActivitySelected: (UUID) -> Unit,
) {
    Scaffold(Modifier.fillMaxSize().testTag("home-reference-root"), containerColor = MoCream) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.xs))
            MaginaOlivoWordmark(compact = true)
            Column {
                Text(greeting(now), style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
                state.today?.let {
                    Text(it.format(TODAY).replaceFirstChar { c -> c.titlecase(SPANISH) }, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
                }
            }
            Hero()
            if (state.isLoading) {
                CircularProgressIndicator()
                return@Column
            }
            if (state.farms.isEmpty()) {
                MoEmptyState(
                    "Empieza por tu primera finca",
                    "Crea una finca con sus parcelas y aquí verás tu campaña, tus kilos y tus próximos trabajos.",
                    actionText = "Crear finca",
                    onAction = onOlivar,
                    icon = MoIcons.Tree,
                    modifier = Modifier.testTag("home-no-farms"),
                )
            } else {
                MoMetricGrid(
                    columns = 3,
                    content = listOf(
                        { m -> MoSummaryMetric("Fincas", state.farms.size.toString(), m, icon = MoIcons.Tree) },
                        { m -> MoSummaryMetric("Parcelas", state.parcelCount.toString(), m, icon = MoIcons.Parcels) },
                        { m -> MoSummaryMetric("Superficie", state.knownAreaM2?.let(::hectares) ?: "—", m, icon = MoIcons.Area) },
                    ),
                )
                MoSectionHeader("Campaña en marcha")
                if (state.campaigns.isEmpty()) {
                    MoEmptyState(
                        "Sin campaña en marcha",
                        "Activa una campaña en tu finca para registrar cosecha y entregas.",
                        icon = MoIcons.Campaign,
                        modifier = Modifier.testTag("home-no-campaign"),
                    )
                }
                state.campaigns.forEach { campaign ->
                    MoCompactListItem(
                        title = "${campaign.name} · ${campaign.farmName}",
                        subtitle = listOf(
                            campaign.harvestedGrams?.let { "Recogido ${Weight.format(it)}" } ?: "Aún no has registrado cosecha",
                            campaign.deliveredGrams?.let { "Entregado ${Weight.format(it)}" } ?: "sin entregas",
                        ).joinToString(" · "),
                        icon = MoIcons.Harvest,
                        onClick = onHarvest,
                        modifier = Modifier.testTag("home-campaign"),
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MoSectionHeader("Próximos trabajos", Modifier.weight(1f))
                    TextButton(onClick = onCalendar, modifier = Modifier.testTag("home-open-calendar")) { Text("Calendario") }
                }
                if (state.overdueCount > 0) {
                    MoStatusChip(
                        if (state.overdueCount == 1) "1 trabajo pendiente de días pasados" else "${state.overdueCount} trabajos pendientes de días pasados",
                        tone = MoStatusTone.Warning,
                        modifier = Modifier.testTag("home-overdue"),
                    )
                }
                if (state.upcoming.isEmpty()) {
                    MoEmptyState(
                        "Nada planificado",
                        "Planifica una poda, un riego o la cosecha y te avisaremos en este teléfono.",
                        icon = MoIcons.Calendar,
                        modifier = Modifier.testTag("home-no-upcoming"),
                    )
                }
                state.upcoming.forEach { entry ->
                    MoCompactListItem(
                        title = entry.description,
                        subtitle = listOfNotNull(
                            dayLabel(entry.activityDate, state.today) + (entry.planning?.startTime?.let { " · ${it.format(HOUR)}" } ?: ""),
                            entry.type.label(),
                            entry.farmName,
                        ).joinToString(" · "),
                        icon = MoIcons.Calendar,
                        iconTint = MoInfoText,
                        iconContainer = MoInfo.copy(alpha = 0.14f),
                        onClick = { onActivitySelected(entry.activityId) },
                        modifier = Modifier.testTag("home-upcoming"),
                    )
                }
            }
            MoSectionHeader("Accesos rápidos")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                Quick("Mis fincas", MoIcons.Tree, "home-quick-olivar", onOlivar, Modifier.weight(1f))
                Quick("Cosecha", MoIcons.Harvest, "home-quick-harvest", onHarvest, Modifier.weight(1f))
                Quick("Entregas", MoIcons.Delivery, "home-quick-deliveries", onDeliveries, Modifier.weight(1f))
                Quick("Gastos", MoIcons.Document, "home-quick-expenses", onExpenses, Modifier.weight(1f))
            }
            MoSectionHeader("Próximamente en Inicio")
            Later("Tiempo y radar", "Previsión y lluvia de tu zona, con su hora de actualización.", "home-later-weather")
            Later("Mercado del aceite", "AOVE, Virgen y Lampante desde una fuente oficial, con fecha.", "home-later-market")
            Later("Avisos de tu cooperativa", "Cuando elijas tu cooperativa de referencia.", "home-later-news")
            Spacer(Modifier.height(MoSpacing.lg))
        }
    }
}

@Composable
private fun Hero() {
    Box(Modifier.fillMaxWidth().aspectRatio(2.6f).clip(MoShape.hero)) {
        MoFieldArtwork(Modifier.matchParentSize(), seed = 7)
        Column(Modifier.align(Alignment.BottomStart).padding(MoSpacing.md)) {
            Text("Tu olivar, de un vistazo", style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
            Text("Todo se guarda en este teléfono, con o sin cobertura.", style = MaterialTheme.typography.bodySmall, color = MoInk)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Quick(label: String, icon: ImageVector, tag: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag(tag),
        shape = MoShape.card,
        color = MoWarmWhite,
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = MoOliveMid, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MoInk, maxLines = 1)
        }
    }
}

@Composable
private fun Later(title: String, body: String, tag: String) {
    MoCompactListItem(
        title = title,
        subtitle = body,
        icon = MoIcons.Clock,
        iconTint = MoTextSecondary,
        iconContainer = MoSurfaceSoft,
        modifier = Modifier.testTag(tag),
        trailing = { MoStatusChip("Pronto", tone = MoStatusTone.Neutral) },
    )
}

private val SPANISH = Locale.forLanguageTag("es-ES")
private val TODAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)
private val DAY = DateTimeFormatter.ofPattern("EEEE d", SPANISH)
private val HOUR = DateTimeFormatter.ofPattern("HH:mm")

internal fun greeting(now: LocalTime): String = when (now.hour) {
    in 6..13 -> "Buenos días"
    in 14..20 -> "Buenas tardes"
    else -> "Buenas noches"
}

private fun dayLabel(date: LocalDate, today: LocalDate?): String = when (date) {
    today -> "Hoy"
    today?.plusDays(1) -> "Mañana"
    else -> date.format(DAY).replaceFirstChar { it.titlecase(SPANISH) }
}

private fun hectares(areaM2: Double): String {
    val format = NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = 2 }
    return "${format.format(areaM2 / 10_000.0)} ha"
}
