package com.isivoltpro.maginaolivo.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.feed.FeedAge
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.activities.icon
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoStat
import com.isivoltpro.maginaolivo.ui.components.MoStatStrip
import com.isivoltpro.maginaolivo.ui.components.MoPhotoBrand
import com.isivoltpro.maginaolivo.ui.components.MoPhotoHeader
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.text.NumberFormat
import java.time.Instant
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
                    weatherFeed = persistence.weatherFeed,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, LocalTime.now(), onOlivar, onCalendar, onHarvest, onDeliveries, onExpenses, onActivitySelected, clock.nowInstant())
}

/**
 * Inicio — the farmer's situation in seconds, from this phone's data only (VISUAL_DESIGN_LOCK
 * "Pantalla Inicio"): greeting, territory hero, olive-grove summary, running campaign,
 * upcoming work and quick access. Weather, oil market and cooperative notices come last
 * (Phase 20), each saying what it knows and from when — never sample figures.
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
    feedNow: Instant = Instant.now(),
) {
    // The navigation shell owns the system-bar insets (visual identity pass); no second inset here.
    Scaffold(
        Modifier.fillMaxSize().testTag("home-reference-root"),
        containerColor = MoCream,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            // Design v3 (CR-004): brand, greeting and place over the olive-grove photograph.
            MoPhotoHeader(
                title = greeting(now),
                location = state.location,
                caption = state.today?.format(TODAY)?.replaceFirstChar { c -> c.titlecase(SPANISH) },
                heightFraction = 0.46f,
                top = { MoPhotoBrand(Modifier.align(Alignment.TopStart).padding(MoSpacing.md)) },
            )
            Column(
                Modifier.padding(horizontal = MoSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).testTag("home-loading"))
            } else if (state.farms.isEmpty()) {
                MoEmptyState(
                    "Empieza por tu primera finca",
                    "Crea una finca con sus parcelas y aquí verás tu campaña, tus kilos y tus próximos trabajos.",
                    actionText = "Crear finca",
                    onAction = onOlivar,
                    icon = MoIcons.Tree,
                    modifier = Modifier.testTag("home-no-farms"),
                )
            } else {
                MoStatStrip(
                    listOf(
                        MoStat("Superficie", state.knownAreaM2?.let(::hectares) ?: "—", MoIcons.Area),
                        MoStat("Parcelas", state.parcelCount.toString(), MoIcons.Parcels),
                        MoStat("Olivos", state.oliveTreesLabel(), MoIcons.Olive),
                    ),
                    Modifier.testTag("home-stats"),
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
                        icon = entry.type.icon(),
                        onClick = { onActivitySelected(entry.activityId) },
                        modifier = Modifier.testTag("home-upcoming"),
                    )
                }
            }
            MoSectionHeader("Accesos rápidos")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                Quick("Mis fincas", MoIcons.Tree, "home-quick-olivar", onOlivar, Modifier.weight(1f))
                Quick("Cosecha", MoIcons.Harvest, "home-quick-harvest", onHarvest, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                Quick("Entregas", MoIcons.Delivery, "home-quick-deliveries", onDeliveries, Modifier.weight(1f))
                Quick("Gastos", MoIcons.Euro, "home-quick-expenses", onExpenses, Modifier.weight(1f))
            }
            // Phase 20: external context after the farm, each with an honest state.
            HomeContext(state, feedNow)
            Spacer(Modifier.height(MoSpacing.lg))
            }
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
        Row(
            Modifier.fillMaxWidth().padding(MoSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoIconBadge(icon)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MoOliveDark, maxLines = 2)
        }
    }
}

/**
 * Phase 20A — weather, oil market and cooperative, below the farm. Every state is said in
 * words: not configured, no place, nothing yet, or the value with its source and age.
 */
@Composable
private fun HomeContext(state: HomeUiState, now: Instant) {
    MoSectionHeader("Tiempo, mercado y cooperativa")
    when (val weather = state.weather) {
        is FeedState.Value -> {
            val value = weather.value
            MoCompactListItem(
                title = "${value.temperatureC} °C · ${value.condition.label}",
                subtitle = listOfNotNull(
                    value.rainProbabilityPercent?.let { "Lluvia $it %" },
                    value.windKmh?.let { "Viento $it km/h" },
                    state.weatherLocation?.label,
                ).joinToString(" · "),
                icon = MoIcons.Weather,
                modifier = Modifier.testTag("home-weather-value"),
                trailing = if (weather.stale) {
                    { MoStatusChip("Desactualizado", tone = MoStatusTone.Warning, modifier = Modifier.testTag("home-weather-stale")) }
                } else {
                    null
                },
            )
            Text(
                "Fuente: ${weather.source} · ${FeedAge.label(weather.fetchedAt, now)}",
                style = MaterialTheme.typography.bodySmall,
                color = MoTextSecondary,
                modifier = Modifier.testTag("home-weather-source"),
            )
        }
        FeedState.NotConfigured -> Quiet("Tiempo", "Sin fuente configurada en esta versión.", MoIcons.Weather, "home-weather-not-configured")
        FeedState.NoLocation -> Quiet(
            "Tiempo",
            "Indica el municipio en la ficha de tu finca para ver su tiempo.",
            MoIcons.Weather,
            "home-weather-no-location",
        )
        FeedState.Unavailable -> Quiet(
            "Tiempo${state.weatherLocation?.let { " · ${it.label}" } ?: ""}",
            "Aún sin datos. Se actualizará cuando haya conexión.",
            MoIcons.Weather,
            "home-weather-unavailable",
        )
    }
    // Owner decision D3: no licensed source yet, so no figure is shown.
    Quiet("Mercado del aceite", "Sin fuente configurada.", MoIcons.Euro, "home-market")
    // Owner decision D4: notices arrive with the private administration panel.
    Quiet("Mi cooperativa", "Los avisos de tu cooperativa llegarán con el panel de administración.", MoIcons.Bell, "home-cooperative")
}

@Composable
private fun Quiet(title: String, body: String, icon: ImageVector, tag: String) {
    MoCompactListItem(
        title = title,
        subtitle = body,
        icon = icon,
        iconTint = MoTextSecondary,
        iconContainer = MoSurfaceSoft,
        modifier = Modifier.testTag(tag),
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

private fun HomeUiState.oliveTreesLabel(): String {
    val count = oliveTrees ?: return "—"
    val number = NumberFormat.getIntegerInstance(SPANISH).format(count)
    return if (oliveTreesComplete) number else "≥ $number"
}
