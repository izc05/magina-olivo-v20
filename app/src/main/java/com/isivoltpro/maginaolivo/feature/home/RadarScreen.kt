package com.isivoltpro.maginaolivo.feature.home

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.weather.RadarFrames
import com.isivoltpro.maginaolivo.domain.weather.RadarSource
import com.isivoltpro.maginaolivo.feature.maps.GeoPoint
import com.isivoltpro.maginaolivo.feature.maps.MapBase
import com.isivoltpro.maginaolivo.feature.maps.MapFocus
import com.isivoltpro.maginaolivo.feature.maps.MapParcel
import com.isivoltpro.maginaolivo.feature.maps.ParcelMap
import com.isivoltpro.maginaolivo.feature.maps.labelPoint
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Phase 20B-radar — what the radar screen can honestly say. */
sealed interface RadarUiState {
    data object Loading : RadarUiState
    /** This build has no weather functions configured. */
    data object NotConfigured : RadarUiState
    /** No connection or no answer: radar is live-only, so nothing older is shown instead. */
    data object Unavailable : RadarUiState
    /** [index] is the frame on screen; the latest by default. */
    data class Ready(val radar: RadarFrames, val index: Int) : RadarUiState {
        val frame get() = radar.frames[index]
    }
}

class RadarViewModel(private val source: RadarSource?) : ViewModel() {
    private val mutableState = MutableStateFlow<RadarUiState>(if (source == null) RadarUiState.NotConfigured else RadarUiState.Loading)
    val state: StateFlow<RadarUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val radar = source ?: return
        mutableState.value = RadarUiState.Loading
        viewModelScope.launch {
            mutableState.value = runCatching { radar.frames() }
                .fold({ RadarUiState.Ready(it, it.frames.lastIndex) }, { RadarUiState.Unavailable })
        }
    }

    fun showFrame(index: Int) {
        mutableState.update { current ->
            if (current is RadarUiState.Ready) current.copy(index = index.coerceIn(0, current.radar.frames.lastIndex)) else current
        }
    }
}

@Composable
fun RadarRoute(persistence: LocalPersistence, farmId: UUID?) {
    val viewModel: RadarViewModel = viewModel(
        key = "radar",
        factory = viewModelFactory { initializer { RadarViewModel(persistence.radarSource) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The active Farm's boundaries, from this phone, so the farmer sees where the rain is.
    val parcelsFlow: Flow<List<MapParcel>> = remember(farmId) {
        farmId?.let { id ->
            persistence.parcelRepository.observeActive(id).map { parcels ->
                parcels.mapNotNull { p -> p.geometryGeoJson?.let { MapParcel(p.id.toString(), p.displayName, it) } }
            }
        } ?: flowOf(emptyList())
    }
    val parcels by parcelsFlow.collectAsStateWithLifecycle(emptyList())
    RadarScreen(state, parcels, onRetry = viewModel::load, onFrame = viewModel::showFrame)
}

/**
 * Phase 20B-radar (spec §10) — rain radar over the farm. Always says the picture's time and
 * its source; offline it says the radar needs a connection instead of showing an old picture.
 */
@Composable
fun RadarScreen(
    state: RadarUiState,
    parcels: List<MapParcel>,
    onRetry: () -> Unit,
    onFrame: (Int) -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        Modifier.fillMaxSize().testTag("radar-root"),
        containerColor = MoSurfaceTokens.appBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            // #345: the radar keeps its water-blue identity (icon + title), distinct from «El tiempo».
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                modifier = Modifier.testTag("radar-title"),
            ) {
                androidx.compose.material3.Icon(MoIcons.Rain, null, tint = MoColors.current.rainText, modifier = Modifier.size(32.dp))
                Text("Radar de lluvia", style = MaterialTheme.typography.headlineLarge, color = MoColors.current.rainText)
            }
            when (state) {
                RadarUiState.Loading -> CircularProgressIndicator(Modifier.testTag("radar-loading"))
                RadarUiState.NotConfigured -> MoEmptyState(
                    "Radar sin configurar",
                    "Esta versión de la app no tiene el servicio del tiempo configurado.",
                    icon = MoIcons.Weather,
                    modifier = Modifier.testTag("radar-not-configured"),
                )
                RadarUiState.Unavailable -> MoEmptyState(
                    "El radar necesita conexión",
                    "No hemos podido cargar el radar. Comprueba la cobertura y vuelve a intentarlo. " +
                        "El radar no se guarda en el teléfono: siempre muestra la imagen real del momento.",
                    actionText = "Reintentar",
                    onAction = onRetry,
                    icon = MoIcons.Weather,
                    modifier = Modifier.testTag("radar-offline"),
                )
                is RadarUiState.Ready -> RadarReady(state, parcels, onRetry, onFrame, zone, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RadarReady(
    state: RadarUiState.Ready,
    parcels: List<MapParcel>,
    onRetry: () -> Unit,
    onFrame: (Int) -> Unit,
    zone: ZoneId,
    mapModifier: Modifier,
) {
    val frames = state.radar.frames
    val focus = remember(parcels.map { it.id }) { MapFocus(radarCenter(parcels), zoom = RADAR_ZOOM) }
    Text(
        radarTimeLabel(state.frame.time, frames.last().time, zone),
        style = MaterialTheme.typography.titleMedium,
        color = MoColors.current.primaryText,
        modifier = Modifier.testTag("radar-time"),
    )
    ParcelMap(
        parcels = parcels,
        modifier = mapModifier.fillMaxWidth().testTag("radar-map"),
        base = MapBase.MAP,
        focus = focus,
        overlayTiles = sharpRadarTiles(state.frame.tileUrlTemplate),
        overlayAttribution = state.radar.attribution,
    )
    if (frames.size > 1) {
        Slider(
            value = state.index.toFloat(),
            onValueChange = { onFrame(it.roundToInt()) },
            valueRange = 0f..frames.lastIndex.toFloat(),
            steps = (frames.size - 2).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth().testTag("radar-frame-slider")
                .semantics { contentDescription = "Elegir la hora del radar" },
        )
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            state.radar.attribution,
            style = MaterialTheme.typography.labelSmall,
            color = MoColors.current.secondaryText,
            modifier = Modifier.weight(1f).testTag("radar-attribution"),
        )
        MoSecondaryButton("Actualizar", onRetry, Modifier.testTag("radar-refresh"))
    }
}

/** "Radar de las 11:00" for the latest picture; older ones also say how much older. */
internal fun radarTimeLabel(time: Instant, latest: Instant, zone: ZoneId): String {
    val clock = HOUR.format(time.atZone(zone))
    val behind = java.time.Duration.between(time, latest).toMinutes()
    return if (behind <= 0) "Radar de las $clock" else "Radar de las $clock · $behind min antes del último"
}

/** The middle of the farm's parcels; without boundaries, Sierra Mágina (the app's home area). */
internal fun radarCenter(parcels: List<MapParcel>): GeoPoint {
    val points = parcels.mapNotNull { labelPoint(it.geometry) }
    if (points.isEmpty()) return GeoPoint(37.73, -3.45)
    return GeoPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average())
}

/**
 * #360: RainViewer publishes every radar picture at 256 and at 512 px. On a phone screen the
 * 256 px picture is enlarged about three times and looks pixelated; the 512 px one is the same
 * picture with twice the detail, so the radar asks for it. Other providers are left untouched.
 */
internal fun sharpRadarTiles(template: String): String =
    if ("rainviewer.com" in template) template.replace("/256/", "/512/") else template

private const val RADAR_ZOOM = 7.5
private val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
