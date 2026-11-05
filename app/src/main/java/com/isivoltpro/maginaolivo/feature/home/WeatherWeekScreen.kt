package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.ui.theme.MoColors

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.isivoltpro.maginaolivo.domain.feed.IneMunicipality
import com.isivoltpro.maginaolivo.domain.weather.WeatherLocationPreferenceStore
import com.isivoltpro.maginaolivo.domain.weather.WeatherLocationResolver
import com.isivoltpro.maginaolivo.domain.weather.WeatherPreferenceIdentity
import kotlinx.coroutines.flow.Flow
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
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
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
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

data class WeatherWeekUiState(
    val isLoading: Boolean = false,
    val location: FeedLocation? = null,
    val locationAmbiguous: Boolean = false,
    val weather: FeedState<WeatherNow> = FeedState.Unavailable,
    /** #315: the farmer's «Actualizar» is running. */
    val refreshing: Boolean = false,
    /** #315: the last «Actualizar» failed; the saved value stays on screen with its age. */
    val refreshFailed: Boolean = false,
    /** Selector 1.0: the place comes from the farmer's explicit weather preference. */
    val hasExplicitLocation: Boolean = false,
    /** Selector 1.0: the farms' one municipality, offered as a 1-tap suggestion. */
    val farmSuggestion: FeedLocation? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherWeekViewModel(
    private val workspaces: WorkspaceRepository,
    farms: FarmRepository,
    private val weatherFeed: WeatherFeed?,
    /** Phase 21A: the farmer's municipality (Perfil), only as a local-mode fallback. */
    profile: ProfileRepository? = null,
    /** Selector 1.0: personal weather municipality (identity + workspace). */
    private val weatherLocationStore: WeatherLocationPreferenceStore? = null,
    weatherIdentity: Flow<WeatherPreferenceIdentity> = flowOf(WeatherPreferenceIdentity.Local),
) : ViewModel() {
    private val mutableState = MutableStateFlow(WeatherWeekUiState(isLoading = true))
    val state: StateFlow<WeatherWeekUiState> = mutableState.asStateFlow()
    private val currentIdentity = MutableStateFlow<WeatherPreferenceIdentity>(WeatherPreferenceIdentity.Local)

    init {
        val workspace = workspaces.observeActiveWorkspace()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
        val farmList = workspace.flatMapLatest { result ->
            when (result) {
                is AppResult.Success -> farms.observeActive(result.value)
                is AppResult.Failure -> flowOf(emptyList())
            }
        }
        val explicit = combine(weatherIdentity.onEach { currentIdentity.value = it }, workspace) { identity, ws ->
            identity to (ws as? AppResult.Success)?.value
        }.flatMapLatest { (identity, wsId) ->
            if (wsId == null || weatherLocationStore == null) flowOf(identity to null)
            else weatherLocationStore.observe(identity, wsId).map { identity to it }
        }
        val locations = combine(
            farmList,
            explicit,
            (profile?.observe() ?: flowOf(ProfileSettings())).catch { emit(ProfileSettings()) },
        ) { list, (identity, pref), settings ->
            WeatherLocationResolver.resolve(list, pref, settings, identity)
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
                    .map {
                        WeatherWeekUiState(
                            isLoading = false,
                            location = resolved.location,
                            locationAmbiguous = resolved.ambiguous,
                            weather = it,
                            hasExplicitLocation = resolved.hasExplicitPreference,
                            farmSuggestion = resolved.farmSuggestion,
                        )
                    }
            }.collect { next ->
                mutableState.value = next.copy(refreshing = mutableState.value.refreshing, refreshFailed = mutableState.value.refreshFailed)
            }
        }
    }

    /** #315: returning to El tiempo after > 1 hour refreshes the cached forecast in the background. */
    fun onResumed() {
        val feed = weatherFeed ?: return
        val place = mutableState.value.location ?: return
        viewModelScope.launch { feed.refreshIfStale(place) }
    }

    /** #315: «Actualizar» asks the source now; a failure keeps the saved value and says so. */
    fun refresh() {
        val feed = weatherFeed ?: return
        val place = mutableState.value.location ?: return
        if (mutableState.value.refreshing) return
        mutableState.value = mutableState.value.copy(refreshing = true, refreshFailed = false)
        viewModelScope.launch {
            val ok = feed.refresh(place)
            mutableState.value = mutableState.value.copy(refreshing = false, refreshFailed = !ok)
        }
    }

    /** Selector 1.0: saves the chosen municipality; farm records are never touched. */
    fun selectMunicipality(municipality: IneMunicipality) {
        val store = weatherLocationStore ?: return
        viewModelScope.launch {
            val wsId = (workspaces.ensureLocalWorkspace() as? AppResult.Success)?.value ?: return@launch
            mutableState.value = mutableState.value.copy(refreshFailed = false)
            store.set(currentIdentity.value, wsId, municipality)
        }
    }

    fun clearMunicipality() {
        val store = weatherLocationStore ?: return
        viewModelScope.launch {
            val wsId = (workspaces.ensureLocalWorkspace() as? AppResult.Success)?.value ?: return@launch
            store.clear(currentIdentity.value, wsId)
        }
    }
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
                    persistence.weatherLocationStore,
                    persistence.weatherIdentity,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }
    WeatherWeekScreen(
        state, clock.nowInstant(), onRadar, onBack, onRefresh = viewModel::refresh,
        onSelectMunicipality = viewModel::selectMunicipality.takeIf { persistence.weatherLocationStore != null },
        onClearMunicipality = viewModel::clearMunicipality,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherWeekScreen(
    state: WeatherWeekUiState,
    now: Instant = Instant.now(),
    onRadar: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    onRefresh: (() -> Unit)? = null,
    /** Selector 1.0: null hides «Cambiar municipio» (no store wired, e.g. previews). */
    onSelectMunicipality: ((IneMunicipality) -> Unit)? = null,
    onClearMunicipality: () -> Unit = {},
) {
    var selectorOpen by rememberSaveable { mutableStateOf(false) }
    if (selectorOpen && onSelectMunicipality != null) {
        ModalBottomSheet(onDismissRequest = { selectorOpen = false }) {
            WeatherLocationSelectorContent(
                currentLocation = state.location.takeIf { state.hasExplicitLocation },
                farmSuggestion = state.farmSuggestion,
                onSelectMunicipality = { onSelectMunicipality(it); selectorOpen = false },
                onClearLocation = { onClearMunicipality(); selectorOpen = false },
                onCancel = { selectorOpen = false },
            )
        }
    }
    Scaffold(
        Modifier.fillMaxSize().testTag("weather-week-root"),
        containerColor = MoSurfaceTokens.appBackground,
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
            Text("El tiempo", style = MaterialTheme.typography.headlineLarge, color = MoColors.current.primaryText)
            Text(
                state.location?.label ?: if (state.locationAmbiguous) "Varias ubicaciones" else "Tu zona",
                style = MaterialTheme.typography.titleMedium,
                color = MoColors.current.primaryText,
                modifier = Modifier.testTag("weather-week-location"),
            )
            if (state.location != null) {
                Text(
                    if (state.hasExplicitLocation) "Ubicación elegida por ti" else "Municipio de tus fincas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MoSurfaceTokens.secondaryText,
                    modifier = Modifier.testTag("weather-week-location-origin"),
                )
            }
            if (onSelectMunicipality != null) {
                TextButton(
                    onClick = { selectorOpen = true },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("weather-week-location-action"),
                ) {
                    Icon(MoIcons.Location, null, modifier = Modifier.size(18.dp))
                    Text(if (state.location == null) " Elegir municipio" else " Cambiar municipio")
                }
            }
            if (state.isLoading) {
                Text("Cargando el tiempo…", color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("weather-week-loading"))
            } else {
                when (val weather = state.weather) {
                    is FeedState.Value -> {
                        val today = now.atZone(zone).toLocalDate()
                        if (weather.stale) {
                            MoStatusChip(
                                "Datos guardados · pueden estar antiguos",
                                tone = MoStatusTone.Warning,
                                modifier = Modifier.testTag("weather-week-stale"),
                            )
                        }
                        CurrentWeatherSummary(weather.value, weather.stale, now, zone)
                        weatherAdvisories(weather.value, weather.value.daily, today, stale = weather.stale)
                            .takeIf { it.isNotEmpty() }
                            ?.let { WeatherAdvisoriesCard(it, weather.source) }
                        onRadar?.let { RadarAccess(it) }
                        Spacer(Modifier.height(MoSpacing.xs))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Próximos días", style = MaterialTheme.typography.titleMedium, color = MoColors.current.primaryText, modifier = Modifier.weight(1f))
                            Text("Máx. · mín.", style = MaterialTheme.typography.labelMedium, color = MoSurfaceTokens.secondaryText)
                        }
                        val week = visibleForecastDays(weather.value.daily, today)
                        if (weather.value.daily.isEmpty()) {
                            Text("La fuente disponible aún no ofrece previsión semanal.", color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("weather-week-no-days"))
                        } else if (week.isEmpty()) {
                            Text(
                                "Los días guardados en el teléfono ya han pasado. Conéctate y pulsa Actualizar para consultar los próximos días.",
                                color = MoSurfaceTokens.secondaryText,
                                modifier = Modifier.testTag("weather-week-expired-days"),
                            )
                        } else {
                            // #362: the week in one line, from published values only.
                            weekSummary(week, today)?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MoColors.current.bodyText, modifier = Modifier.testTag("weather-week-summary"))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                week.forEachIndexed { index, day -> WeatherDayRow(day, today, index) }
                            }
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
                            modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("weather-week-source"),
                        ) {
                            // #362: the attribution stays, but as a quiet footnote under the forecast.
                            Text(
                                "Fuente: ${weather.source} · Pronóstico ${FeedAge.label(weather.value.updatedAt ?: weather.fetchedAt, now)} · Consulta ${FeedAge.label(weather.fetchedAt, now)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MoSurfaceTokens.secondaryText,
                            )
                            weather.value.attribution?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MoSurfaceTokens.secondaryText) }
                        }
                        WeatherRefresh(state, onRefresh)
                    }
                    FeedState.NoLocation -> Text(
                        if (state.locationAmbiguous) "Tus fincas están en varios municipios. Elige tu municipio para consultar el tiempo."
                        else "Elige tu municipio o añádelo en la ficha de tu finca para consultar el tiempo.",
                        color = MoSurfaceTokens.secondaryText,
                        modifier = Modifier.testTag("weather-week-no-location"),
                    )
                    FeedState.NotConfigured -> Text("La fuente del tiempo no está configurada.", color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("weather-week-not-configured"))
                    FeedState.Unavailable -> {
                        Text("Conéctate para cargar la previsión", color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("weather-week-unavailable"))
                        WeatherRefresh(state, onRefresh)
                    }
                }
            }
            if (state.weather !is FeedState.Value) {
                onRadar?.let { RadarAccess(it) }
            }
            Spacer(Modifier.height(MoSpacing.lg))
        }
    }
}

/** #315: manual retry; never hides the saved value nor claims it is fresh. */
@Composable
private fun WeatherRefresh(state: WeatherWeekUiState, onRefresh: (() -> Unit)?) {
    if (onRefresh == null || state.location == null) return
    TextButton(onClick = onRefresh, enabled = !state.refreshing, modifier = Modifier.heightIn(min = 48.dp).testTag("weather-week-refresh")) {
        Text(if (state.refreshing) "Actualizando…" else "Actualizar")
    }
    if (state.refreshFailed) {
        Text(
            "No se pudo actualizar. Se muestran los datos guardados con su antigüedad.",
            style = MaterialTheme.typography.bodySmall,
            color = MoSurfaceTokens.secondaryText,
            modifier = Modifier.testTag("weather-week-refresh-failed"),
        )
    }
}

/** #345: the rain radar has its own water-blue identity (icon + text + tint), not just colour. */
@Composable
private fun RadarAccess(onRadar: () -> Unit) {
    Surface(
        onClick = onRadar,
        modifier = Modifier.fillMaxWidth().testTag("weather-week-radar"),
        shape = MoShape.card,
        color = MoColors.current.rainTint,
        border = BorderStroke(1.dp, MoColors.current.rainText),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(MoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Icon(MoIcons.Rain, null, tint = MoColors.current.rainText, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("Radar de lluvia", style = MaterialTheme.typography.titleMedium, color = MoColors.current.rainText)
                Text("Dónde llueve ahora y en las últimas horas", style = MaterialTheme.typography.bodySmall, color = MoColors.current.bodyText)
            }
            Icon(MoIcons.ChevronRight, null, tint = MoColors.current.rainText, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CurrentWeatherSummary(weather: WeatherNow, stale: Boolean, now: Instant, zone: ZoneId) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("weather-week-current"),
        shape = MoShape.cardLarge,
        color = MoColors.current.infoTint,
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (stale) "Previsión guardada" else "Previsión actual", style = MaterialTheme.typography.labelLarge, color = MoColors.current.infoText)
                Text("Tramo ${HOUR_FORMAT.format(weather.validAt.atZone(zone))}", style = MaterialTheme.typography.labelMedium, color = MoColors.current.secondaryText)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                WeatherTemperature(weather.temperatureC, Modifier.weight(1f))
                WeatherConditionIcon(weather.condition, Modifier.size(64.dp))
            }
            Text(weather.condition.label, style = MaterialTheme.typography.titleLarge, color = MoColors.current.primaryText)
            weather.solarLine(now)?.let { solar ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
                    Icon(MoIcons.Sun, null, tint = MoColors.current.valueText, modifier = Modifier.size(18.dp))
                    Text(solar, style = MaterialTheme.typography.bodySmall, color = MoColors.current.bodyText)
                }
            }
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
private fun WeatherAdvisoriesCard(advisories: List<String>, source: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("weather-week-advisories"),
        shape = MoShape.card,
        color = MoColors.current.warningTint,
        border = BorderStroke(1.dp, MoColors.current.warningText),
    ) {
        Column(Modifier.padding(MoSpacing.sm), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                Icon(MoIcons.Warning, null, tint = MoColors.current.warningText, modifier = Modifier.size(20.dp))
                Text("Condiciones a vigilar en tu olivar", style = MaterialTheme.typography.titleSmall, color = MoColors.current.warningText)
            }
            advisories.forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodySmall, color = MoColors.current.primaryText)
            }
            Text(
                "Orientación para el campo según los datos previstos por $source · No sustituye a los avisos oficiales de AEMET.",
                style = MaterialTheme.typography.labelSmall,
                color = MoSurfaceTokens.secondaryText,
            )
        }
    }
}

@Composable
private fun WeatherMeasure(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Surface(modifier.semantics(mergeDescendants = true) {}, shape = MoShape.card, color = MoSurfaceTokens.cardSurface, border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke)) {
        Column(Modifier.padding(MoSpacing.sm), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
            Icon(icon, null, tint = MoColors.current.infoText, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = MoColors.current.primaryText)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
        }
    }
}

@Composable
private fun WeatherDayRow(day: WeatherDayForecast, today: LocalDate, index: Int) {
    val isToday = day.date == today
    val (tint, accent) = when (day.condition.tone()) {
        WeatherTone.RAIN -> MoColors.current.rainTint to MoColors.current.rainText
        WeatherTone.CLOUD -> MoColors.current.cloudTint to MoColors.current.cloudText
        WeatherTone.PARTLY -> MoColors.current.skyTint to MoColors.current.infoText
        WeatherTone.SUN -> MoColors.current.valueTint to MoColors.current.valueText
        WeatherTone.NEUTRAL -> MoSurfaceTokens.cardSurface to MoSurfaceTokens.secondaryText
    }
    Surface(
        shape = MoShape.card,
        color = tint,
        // «Hoy» stands out by its border and larger icon, not by a different layout.
        border = if (isToday) BorderStroke(2.dp, accent) else BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics(mergeDescendants = true) {}.testTag("weather-week-day-$index").padding(MoSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                WeatherConditionIcon(day.condition, Modifier.size(if (isToday) 36.dp else 28.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        dateLabel(day.date, today),
                        style = if (isToday) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                        color = MoColors.current.primaryText,
                    )
                    Text(day.condition?.label ?: "Estado del cielo no disponible", style = MaterialTheme.typography.bodySmall, color = accent)
                }
                // The maximum leads; the minimum is secondary.
                Column(horizontalAlignment = Alignment.End) {
                    Text(day.maxTemperatureC?.let { "$it°" } ?: "—", style = MaterialTheme.typography.titleLarge, color = MoColors.current.primaryText)
                    Text(day.minTemperatureC?.let { "$it°" } ?: "—", style = MaterialTheme.typography.bodyMedium, color = MoSurfaceTokens.secondaryText)
                }
            }
            val rain = dayRainLine(day)
            val wind = day.windKmh?.let { "Viento $it km/h" }
            if (rain != null && dayHasRain(day)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
                    Icon(MoIcons.Drop, null, tint = MoColors.current.rainText, modifier = Modifier.size(16.dp))
                    Text(rain, style = MaterialTheme.typography.labelLarge, color = MoColors.current.rainText)
                }
            } else if (rain != null) {
                Text(rain, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
            }
            if (wind != null) {
                Text(wind, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
            } else if (rain == null) {
                Text("Sin valores publicados para este día", style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
            }
        }
    }
}

private fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoy · ${date.format(DAY)}"
    today.plusDays(1) -> "Mañana · ${date.format(DAY)}"
    else -> date.format(DATE)
}.replaceFirstChar { it.titlecase(SPANISH) }

internal fun rainFormat(value: Double): String = String.format(SPANISH, "%.1f", value).removeSuffix(",0")

private val SPANISH = Locale.forLanguageTag("es-ES")
private val DAY = DateTimeFormatter.ofPattern("d MMMM", SPANISH)
private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", SPANISH)
private val HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
