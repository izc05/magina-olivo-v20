package com.isivoltpro.maginaolivo.feature.home

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.activities.icon
import com.isivoltpro.maginaolivo.feature.activities.label
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoStat
import com.isivoltpro.maginaolivo.ui.components.MoStatStrip
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun HomeRoute(
    persistence: LocalPersistence,
    clock: AppClock,
    onCalendar: () -> Unit,
    /** CR-011 §17: a running campaign opens its Farm's Cuaderno (Campaña). */
    onCampaign: (UUID?) -> Unit,
    onWeatherWeek: () -> Unit,
    onActivitySelected: (UUID) -> Unit,
    /** Phase 20D-3: the oil market screen (12-week official chart). */
    onOilMarket: () -> Unit = {},
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
                    oilMarketFeed = persistence.oilMarketFeed,
                    profile = persistence.profileRepository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    // #315: entering Inicio or bringing the app back refreshes the weather if it is over an hour old.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }
    HomeScreen(
        state, LocalTime.now(), onCalendar, onWeatherWeek, onCampaign, onActivitySelected, clock.nowInstant(),
        onOilMarket = onOilMarket,
        onRetryLocalData = viewModel::retryLocalData,
    )
}

/**
 * Inicio — the farmer's situation in seconds, from this phone's data only (VISUAL_DESIGN_LOCK
 * "Pantalla Inicio"): greeting and territory hero, farm summary, running campaign and upcoming
 * work. Weather is integrated into the hero; market and cooperative notices come last
 * (Phase 20), each saying what it knows and from when — never sample figures.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    now: LocalTime,
    onCalendar: () -> Unit,
    onWeatherWeek: () -> Unit,
    onCampaign: (UUID?) -> Unit,
    onActivitySelected: (UUID) -> Unit,
    feedNow: Instant = Instant.now(),
    /** Phase 20C: null follows the phone (reduced motion, low memory); tests pass false. */
    weatherMotion: Boolean? = null,
    /** Phase 20D-3: opens the oil market screen; the card offers it once there are official weeks. */
    onOilMarket: (() -> Unit)? = null,
    onRetryLocalData: () -> Unit = {},
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
            HomeWeatherHero(
                state = state,
                greeting = greeting(now),
                date = state.today?.format(TODAY)?.replaceFirstChar { c -> c.titlecase(SPANISH) },
                now = feedNow,
                weatherMotion = weatherMotion,
                onForecast = onWeatherWeek,
            )
            Column(
                Modifier.padding(horizontal = MoSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).testTag("home-loading"))
            } else if (state.localReadError != null) {
                Text(
                    state.localReadError,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("home-local-error"),
                )
                TextButton(onClick = onRetryLocalData, modifier = Modifier.testTag("home-local-retry")) {
                    Text("Reintentar")
                }
            } else if (state.farms.isNotEmpty()) {
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
                        "Activa una campaña en tu finca para registrar pesadas y días de recolección.",
                        icon = MoIcons.Campaign,
                        modifier = Modifier.testTag("home-no-campaign"),
                    )
                }
                state.campaigns.forEach { campaign ->
                    MoCompactListItem(
                        title = "${campaign.name} · ${campaign.farmName}",
                        subtitle = campaign.deliveredGrams?.let { "Pesado ${Weight.format(it)}" }
                            ?: "Aún no hay pesadas",
                        icon = MoIcons.Delivery,
                        onClick = { onCampaign(campaign.farmId) },
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
                        "Planifica una poda, un riego o la recolección y te avisaremos en este teléfono.",
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
            // Phase 20: external context after the farm, each with an honest state.
            HomeContext(state, onOilMarket)
            Spacer(Modifier.height(MoSpacing.lg))
            }
        }
    }
}

/**
 * Market and cooperative stay below farm operations. Weather lives in the hero card.
 */
@Composable
private fun HomeContext(
    state: HomeUiState,
    onOilMarket: (() -> Unit)? = null,
) {
    MoSectionHeader("Mercado y cooperativa")
    OilMarketCard(state.oilMarket, onOpen = onOilMarket)
    // Owner decision D4: notices arrive with the private administration panel.
    // Phase 21A: the cooperative chosen in Perfil is named; still no notices until then.
    Quiet(
        state.cooperativeName?.let { "Mi cooperativa · $it" } ?: "Mi cooperativa",
        if (state.cooperativeName != null) {
            "Sus avisos llegarán con el panel de administración."
        } else {
            "Elígela en Perfil. Sus avisos llegarán con el panel de administración."
        },
        MoIcons.Bell,
        "home-cooperative",
    )
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
