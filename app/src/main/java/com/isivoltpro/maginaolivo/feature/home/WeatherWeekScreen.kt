package com.isivoltpro.maginaolivo.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.feed.FeedAge
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.profile.ProfileRepository
import com.isivoltpro.maginaolivo.domain.profile.ProfileSettings
import com.isivoltpro.maginaolivo.domain.weather.WeatherFeed
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.domain.weather.WeatherDayForecast
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import com.isivoltpro.maginaolivo.ui.theme.MoInfoText
import com.isivoltpro.maginaolivo.ui.theme.MoInfoTint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

data class WeatherWeekUiState(
    val isLoading: Boolean = false,
    val location: FeedLocation? = null,
    val locationAmbiguous: Boolean = false,
    val weather: FeedState<WeatherNow> = FeedState.Unavailable,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherWeekViewModel(
    workspaces: WorkspaceRepository,
    farms: FarmRepository,
    private val weatherFeed: WeatherFeed?,
    /** Phase 21A: the farmer's municipality (Perfil) when the farms give no single place. */
    profile: ProfileRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WeatherWeekUiState(isLoading = true))
    val state: StateFlow<WeatherWeekUiState> = mutableState.asStateFlow()

    init {
        val locations = flow { emit(workspaces.ensureLocalWorkspace()) }
            .flatMapLatest { result ->
                when (result) {
                    is AppResult.Success -> farms.observeActive(result.value)
                    is AppResult.Failure -> flowOf(emptyList())
                }
            }
            .map(::resolveLocation)
            .combine((profile?.observe() ?: flowOf(ProfileSettings())).catch { emit(ProfileSettings()) }) { resolved, settings ->
                if (resolved.location == null) resolved.copy(location = settings.location) else resolved
            }
            .distinctUntilChanged()
            .onEach { resolved ->
                val place = resolved.location
                if (place != null && weatherFeed != null) viewModelScope.launch { weatherFeed.refreshIfStale(place) }
            }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

        viewModelScope.launch {
            locations.flatMapLatest { resolved ->
                (weatherFeed?.observe(resolved.location) ?: flowOf(FeedState.NotConfigured))
                    .map { WeatherWeekUiState(isLoading = false, location = resolved.location, locationAmbiguous = resolved.ambiguous, weather = it) }
            }.collect(mutableState::emit)
        }
    }

    private fun resolveLocation(farms: List<Farm>): ResolvedWeatherLocation {
        val places = farms.mapNotNull { farm ->
            farm.municipality?.trim()?.takeIf(String::isNotEmpty)?.let { FeedLocation(it, farm.province) }
        }
        return ResolvedWeatherLocation(
            location = FeedLocation.common(farms.map { it.municipality to it.province }),
            ambiguous = places.map(FeedLocation::key).distinct().size > 1,
        )
    }

    private data class ResolvedWeatherLocation(val location: FeedLocation?, val ambiguous: Boolean)
}

@Composable
fun WeatherWeekRoute(
    persistence: LocalPersistence,
    clock: AppClock,
    onRadar: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val viewModel: WeatherWeekViewModel = viewModel(
        key = "weather-week",
        factory = viewModelFactory {
            initializer {
                WeatherWeekViewModel(
                    persistence.workspaceRepository,
                    persistence.farmRepository,
                    persistence.weatherFeed,
                    persistence.profileRepository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsState()
    WeatherWeekScreen(state, clock.nowInstant(), onRadar, onBack)
}

@Composable
fun WeatherWeekScreen(
    state: WeatherWeekUiState,
    now: Instant = Instant.now(),
    onRadar: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        Modifier.fillMaxSize().testTag("weather-week-root"),
        containerColor = MoCream,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen, vertical = MoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("weather-week-back")) {
                    Icon(MoIcons.ChevronLeft, null, modifier = Modifier.size(18.dp))
                    Text("Volver a Inicio")
                }
            }
            Text("El tiempo", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                state.location?.label ?: if (state.locationAmbiguous) "Varias ubicaciones" else "Tu zona",
                style = MaterialTheme.typography.titleMedium,
                color = MoOliveDark,
                modifier = Modifier.testTag("weather-week-location"),
            )
            if (state.isLoading) {
                Text("Cargando el tiempo…", color = MoTextSecondary, modifier = Modifier.testTag("weather-week-loading"))
            } else {
                when (val weather = state.weather) {
                    is FeedState.Value -> {
                        if (weather.stale) MoStatusChip("Datos guardados · pueden estar antiguos", tone = MoStatusTone.Warning, modifier = Modifier.testTag("weather-week-stale"))
                        CurrentWeatherSummary(weather.value, weather.stale)
                        onRadar?.let { MoSecondaryButton("Ver radar de lluvia", it, Modifier.fillMaxWidth().testTag("weather-week-radar")) }
                        Spacer(Modifier.height(MoSpacing.xs))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Próximos días", style = MaterialTheme.typography.titleMedium, color = MoOliveDark, modifier = Modifier.weight(1f))
                            Text("Mín. / Máx.", style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
                        }
                        if (weather.value.daily.isEmpty()) {
                            Text("La fuente disponible aún no ofrece previsión semanal.", color = MoTextSecondary, modifier = Modifier.testTag("weather-week-no-days"))
                        } else {
                            val today = now.atZone(zone).toLocalDate()
                            Surface(shape = MoShape.card, color = MoWarmWhite, modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    weather.value.daily.take(7).forEachIndexed { index, day ->
                                        if (index > 0) HorizontalDivider(color = MoOutline, modifier = Modifier.padding(horizontal = MoSpacing.sm))
                                        WeatherDayRow(day, today, index)
                                    }
                                }
                            }
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
                            modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("weather-week-source"),
                        ) {
                            Text(
                                "Fuente: ${weather.source} · Pronóstico ${FeedAge.label(weather.value.updatedAt ?: weather.fetchedAt, now)} · Consulta ${FeedAge.label(weather.fetchedAt, now)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MoTextSecondary,
                            )
                            weather.value.attribution?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MoTextSecondary) }
                        }
                    }
                    FeedState.NoLocation -> Text(
                        if (state.locationAmbiguous) "Tus fincas están en varios municipios. Elige tu municipio en Perfil para consultar el tiempo."
                        else "Añade el municipio en la ficha de tu finca o en Perfil para consultar el tiempo.",
                        color = MoTextSecondary,
                        modifier = Modifier.testTag("weather-week-no-location"),
                    )
                    FeedState.NotConfigured -> Text("La fuente del tiempo no está configurada.", color = MoTextSecondary, modifier = Modifier.testTag("weather-week-not-configured"))
                    FeedState.Unavailable -> Text("Conéctate para cargar la previsión", color = MoTextSecondary, modifier = Modifier.testTag("weather-week-unavailable"))
                }
            }
            if (state.weather !is FeedState.Value) {
                onRadar?.let { MoSecondaryButton("Ver radar de lluvia", it, Modifier.fillMaxWidth().testTag("weather-week-radar")) }
            }
            Spacer(Modifier.height(MoSpacing.lg))
        }
    }
}

@Composable
private fun CurrentWeatherSummary(weather: WeatherNow, stale: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("weather-week-current"),
        shape = MoShape.cardLarge,
        color = MoInfoTint,
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            Text(if (stale) "Previsión guardada" else "Previsión actual", style = MaterialTheme.typography.labelLarge, color = MoInfoText)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                WeatherTemperature(weather.temperatureC, Modifier.weight(1f))
                WeatherConditionIcon(weather.condition, Modifier.size(64.dp))
            }
            Text(weather.condition.label, style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
        }
    }
    if (weather.rainProbabilityPercent != null || weather.windKmh != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            weather.rainProbabilityPercent?.let {
                WeatherMeasure("Prob. de lluvia", "$it %", MoIcons.Drop, Modifier.weight(1f))
            }
            weather.windKmh?.let {
                WeatherMeasure("Viento", "$it km/h", MoIcons.Wind, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeatherMeasure(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Surface(modifier.semantics(mergeDescendants = true) {}, shape = MoShape.card, color = MoWarmWhite, border = BorderStroke(1.dp, MoOutline)) {
        Column(Modifier.padding(MoSpacing.sm), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
            Icon(icon, null, tint = MoInfoText, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MoTextSecondary)
        }
    }
}

@Composable
private fun WeatherDayRow(day: WeatherDayForecast, today: LocalDate, index: Int) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 88.dp).semantics(mergeDescendants = true) {}.testTag("weather-week-day-$index").padding(MoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            WeatherConditionIcon(day.condition, Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(dateLabel(day.date, today), style = MaterialTheme.typography.titleSmall, color = MoOliveDark)
                Text(day.condition?.label ?: "Estado del cielo no disponible", style = MaterialTheme.typography.bodySmall, color = MoTextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${day.minTemperatureC?.let { "$it°" } ?: "—"} / ${day.maxTemperatureC?.let { "$it°" } ?: "—"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MoOliveDark,
                )
            }
        }
        val details = listOfNotNull(
            day.rainProbabilityPercent?.let { "Lluvia $it %" },
            day.rainMm?.let { "${rainFormat(it)} mm" },
            day.windKmh?.let { "Viento $it km/h" },
        )
        Text(
            if (details.isEmpty()) "Sin valores publicados para este día" else details.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MoInfoText,
        )
    }
}

private fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoy · ${date.format(DAY)}"
    today.plusDays(1) -> "Mañana · ${date.format(DAY)}"
    else -> date.format(DATE)
}.replaceFirstChar { it.titlecase(SPANISH) }

private fun rainFormat(value: Double): String = String.format(SPANISH, "%.1f", value).removeSuffix(",0")

private val SPANISH = Locale.forLanguageTag("es-ES")
private val DAY = DateTimeFormatter.ofPattern("d MMMM", SPANISH)
private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", SPANISH)
