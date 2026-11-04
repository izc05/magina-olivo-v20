package com.isivoltpro.maginaolivo.feature.maps

import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import com.isivoltpro.maginaolivo.ui.theme.MoShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.feature.catastro.CadastralCandidate
import com.isivoltpro.maginaolivo.feature.catastro.OfficialCadastreClient
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoLabeledValue
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionCard
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Phase 18 — the farm's parcels on a map, and the place to add them: tap several Catastro
 * parcels and incorporate them in one go, or give a hand-made parcel its location.
 * Boundaries are drawn from the phone's copy; aerial imagery and the official reference
 * boundaries need a connection, and their absence never hides the saved parcels.
 */
@Composable
fun FarmMapRoute(
    farmId: UUID,
    persistence: LocalPersistence,
    onOpenParcel: (UUID) -> Unit,
    onSearchByReference: () -> Unit,
    locateParcelId: UUID? = null,
    onLocated: (UUID) -> Unit = onOpenParcel,
    onImported: () -> Unit = {},
) {
    val ownerScope = persistence.workspaceRepository.currentOwnerId()?.toString() ?: "default"
    val viewModel: FarmMapViewModel = viewModel(
        key = "farm-map-$farmId-$locateParcelId-$ownerScope",
        factory = viewModelFactory {
            initializer {
                FarmMapViewModel(farmId, persistence.farmRepository, persistence.parcelRepository, OfficialCadastreClient(), locateParcelId)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.linkedParcelId) { state.linkedParcelId?.let(onLocated) }
    LaunchedEffect(state.importCompleted) { if (state.importCompleted) onImported() }
    val context = LocalContext.current
    val locate = {
        if (!isLocationEnabled(context)) {
            viewModel.locationUnavailable(LocationProblem.LOCATION_OFF)
        } else {
            requestCurrentLocation(context) { fix ->
                if (fix != null) viewModel.myLocationFound(fix.point, fix.approximate)
                else viewModel.locationUnavailable(LocationProblem.NO_FIX)
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locate() else viewModel.locationUnavailable(LocationProblem.PERMISSION)
    }
    FarmMapScreen(
        state = state,
        onMode = viewModel::setMode,
        onSearchCoordinates = viewModel::searchCoordinates,
        onMyLocation = {
            viewModel.cancelPlaceSearch()
            if (hasLocationPermission(context)) locate() else permission.launch(LOCATION_PERMISSIONS)
        },
        onSearchPolygonParcel = viewModel::searchPolygonParcel,
        onSearchByReference = { viewModel.cancelPlaceSearch(); onSearchByReference() },
        onTapMap = viewModel::tapMap,
        onTapParcel = viewModel::tapParcel,
        onImport = viewModel::importSelected,
        onLink = viewModel::linkSelected,
        onAddInspected = viewModel::addInspected,
        onDismissInspected = viewModel::dismissInspected,
        onOpenParcel = onOpenParcel,
        onLocationProblemAction = { problem ->
            viewModel.dismissLocationProblem()
            when (problem) {
                // Codex #379: asked again while Android can still ask; only once it no longer
                // shows the prompt (permanently denied) do its app settings become the way.
                LocationProblem.PERMISSION -> when {
                    hasLocationPermission(context) -> locate()
                    canAskLocationAgain(context) -> permission.launch(LOCATION_PERMISSIONS)
                    else -> runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(android.net.Uri.fromParts("package", context.packageName, null)),
                        )
                    }
                }
                LocationProblem.LOCATION_OFF -> runCatching {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                LocationProblem.NO_FIX -> Unit // the screen opens the coordinate search itself
            }
        },
        onDismissLocationProblem = viewModel::dismissLocationProblem,
        onSearchPlace = viewModel::searchPlace,
        onSelectPlace = viewModel::selectPlace,
        onPlaceQueryChanged = viewModel::cancelPlaceSearch,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmMapScreen(
    state: FarmMapState,
    onMode: (FarmMapMode) -> Unit,
    onSearchCoordinates: (String) -> Unit,
    onMyLocation: () -> Unit,
    onSearchPolygonParcel: (String, String, String, String) -> Unit,
    onSearchByReference: () -> Unit,
    onTapMap: (Double, Double) -> Unit,
    onTapParcel: (String) -> Unit,
    onImport: (Map<String, String>) -> Unit,
    onLink: () -> Unit,
    onOpenParcel: (UUID) -> Unit,
    showMap: Boolean = true,
    onLocationProblemAction: (LocationProblem) -> Unit = {},
    onDismissLocationProblem: () -> Unit = {},
    onAddInspected: () -> Unit = {},
    onDismissInspected: () -> Unit = {},
    onSearchPlace: (String) -> Unit = {},
    onSelectPlace: (MapPlace) -> Unit = {},
    onPlaceQueryChanged: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var placeQuery by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // #711 B3 (owner's order): the map opens on the PNOA photo with the official boundaries
    // already drawn, so the parcels are there to be seen instead of hunted for one by one. Each
    // layer stays one tap away in the layer menu, and a failing source only shows its notice:
    // the saved boundaries are local and never depend on these remote sources.
    var base by rememberSaveable { mutableStateOf(MapBase.AERIAL) }
    var cadastreLines by rememberSaveable { mutableStateOf(true) }
    var sigpacLines by rememberSaveable { mutableStateOf(true) }
    var cadastreOpacity by rememberSaveable { mutableStateOf(0.65f) }
    var sigpacOpacity by rememberSaveable { mutableStateOf(0.5f) }
    var tileError by remember { mutableStateOf<String?>(null) }
    var tileRetryToken by remember { mutableStateOf(0L) }
    LaunchedEffect(base, cadastreLines, sigpacLines) { tileError = null }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.focus?.token) {
        if (state.focus != null) {
            searchOpen = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    var layerMenu by remember { mutableStateOf(false) }
    var polygonSheet by rememberSaveable { mutableStateOf(false) }
    var reviewSheet by rememberSaveable { mutableStateOf(false) }
    var topOverlayHeightPx by remember { mutableStateOf(0) }
    var bottomOverlayHeightPx by remember { mutableStateOf(0) }
    val mapped = remember(state.parcels, state.candidates, state.taken, state.inspected) {
        state.parcels.mapNotNull { p -> p.geometryGeoJson?.let { MapParcel(p.id.toString(), p.displayName, it) } } +
            state.candidates.filter { it.reference !in state.taken }
                .map { MapParcel(it.reference, it.reference, it.geometryGeoJson, MapParcelKind.CANDIDATE, parcelNumber(it.reference)) } +
            // #711 B3: the parcel just read keeps its own outline drawn while its card is open.
            listOfNotNull(
                state.inspected
                    ?.takeIf { read -> state.candidates.none { it.reference == read.reference } }
                    ?.let { MapParcel(it.reference, it.reference, it.geometryGeoJson, MapParcelKind.CANDIDATE, parcelNumber(it.reference)) },
            )
    }
    val withBoundary = state.parcels.count { it.geometryGeoJson != null }

    BoxWithConstraints(Modifier.fillMaxSize().background(MoSurfaceTokens.appBackground).imePadding().testTag("farm-map-root")) {
        val searchPanelMaxHeight = maxHeight * 0.6f
        val compactHeader = maxWidth >= 600.dp && maxHeight < 400.dp
        if (showMap) {
            ParcelMap(
                parcels = mapped,
                modifier = Modifier.fillMaxSize(),
                base = base,
                cadastreLines = cadastreLines,
                sigpacLines = sigpacLines,
                tileRetryToken = tileRetryToken,
                cadastreOpacity = cadastreOpacity,
                sigpacOpacity = sigpacOpacity,
                selectedId = state.selectedSavedId?.toString(),
                selectedIds = state.selected + setOfNotNull(state.inspected?.reference),
                onSelected = onTapParcel,
                onTap = { latitude, longitude -> onTapMap(latitude, longitude) },
                focus = state.focus,
                cameraInsets = MapCameraInsets(
                    topPx = topOverlayHeightPx,
                    bottomPx = bottomOverlayHeightPx,
                ),
                myLocation = state.myLocation,
                onTileError = { source ->
                    // Ignore late errors from layers the farmer has deliberately turned off.
                    if (base != MapBase.NONE && (source == "base" || source == "cadastre" && cadastreLines || source == "sigpac" && sigpacLines)) {
                        tileError = source
                    }
                },
            )
        }
        // Everything floats over the map, so the map takes the whole screen.
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .onSizeChanged { topOverlayHeightPx = it.height }
                .padding(horizontal = 8.dp, vertical = if (compactHeader) 4.dp else 8.dp).testTag("farm-map-header"),
            shape = MoShape.card,
            color = MoSurfaceTokens.cardSurface.copy(alpha = 0.97f),
            shadowElevation = 3.dp,
        ) {
            Column(Modifier.heightIn(max = searchPanelMaxHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = if (compactHeader) 2.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(if (compactHeader) 2.dp else 4.dp)) {
                Text(
                    if (state.mode == FarmMapMode.LOCATE) "Ubicar «${state.locateParcel?.displayName.orEmpty()}»" else state.farm?.name ?: "Mapa de la finca",
                    style = MaterialTheme.typography.titleMedium,
                    color = MoColors.current.primaryText,
                    maxLines = 1,
                )
                if (state.mode != FarmMapMode.VIEW || searchOpen) Text(
                    when (state.mode) {
                        FarmMapMode.LOCATE -> "Toca donde está tu parcela y elige su número."
                        FarmMapMode.ADD -> "Toca donde están tus parcelas y marca sus números."
                        FarmMapMode.VIEW -> listOfNotNull(
                            "$withBoundary con límites",
                            (state.parcels.size - withBoundary).takeIf { it > 0 }?.let { "$it sin ubicar" },
                            "Toca una parcela para ver sus datos",
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MoColors.current.secondaryText,
                    maxLines = 2,
                    modifier = Modifier.testTag("farm-map-hint"),
                )
                // #722: direct inspection is primary; batch selection stays a quiet alternative.
                when (state.mode) {
                    FarmMapMode.VIEW -> Unit
                    FarmMapMode.ADD -> if (!compactHeader) TextButton(onClick = { onMode(FarmMapMode.VIEW) }, modifier = Modifier.testTag("farm-map-mode-view")) {
                        Text("← Volver a mis parcelas")
                    }
                    FarmMapMode.LOCATE -> Unit
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (compactHeader && state.mode == FarmMapMode.ADD) TextButton(
                        onClick = { onMode(FarmMapMode.VIEW) }, modifier = Modifier.testTag("farm-map-mode-view"),
                    ) { Text("← Volver a mis parcelas") }
                    TextButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) { focusManager.clearFocus(force = true); keyboard?.hide(); onPlaceQueryChanged() }
                    }, modifier = Modifier.testTag("farm-map-search-toggle")
                        .semantics { contentDescription = if (searchOpen) "Cerrar búsqueda" else "Abrir búsqueda" }) {
                        Text(if (searchOpen) "Cerrar" else "Buscar")
                    }
                    TextButton(onClick = onMyLocation, modifier = Modifier.testTag("farm-map-my-location")) { Text("Mi ubicación") }
                    Box {
                        TextButton(onClick = { layerMenu = true }, modifier = Modifier.testTag("farm-map-layer")
                            .semantics { contentDescription = base.label }) {
                            Text(if (base == MapBase.NONE) "Parcelas" else base.label)
                        }
                        DropdownMenu(expanded = layerMenu, onDismissRequest = { layerMenu = false }) {
                            if (state.mode == FarmMapMode.VIEW) DropdownMenuItem(
                                text = { Text("Seleccionar varias parcelas") },
                                onClick = { layerMenu = false; onMode(FarmMapMode.ADD) },
                                modifier = Modifier.testTag("farm-map-mode-add"),
                            )
                            MapBase.entries.forEach { option ->
                                DropdownMenuItem(
                                    modifier = Modifier.testTag("farm-map-base-${option.name}"),
                                    text = { Text(if (option == base) "✓ ${option.label}" else option.label) },
                                    onClick = { base = option; layerMenu = false },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (cadastreLines) "Ocultar linderos de Catastro" else "Ver linderos de Catastro") },
                                onClick = { cadastreLines = !cadastreLines; layerMenu = false },
                                enabled = base != MapBase.NONE,
                            )
                            DropdownMenuItem(
                                text = { Text(if (sigpacLines) "Ocultar recintos SIGPAC (referencia)" else "Ver recintos SIGPAC (referencia)") },
                                onClick = { sigpacLines = !sigpacLines; layerMenu = false },
                                enabled = base != MapBase.NONE,
                                modifier = Modifier.testTag("farm-map-sigpac"),
                            )
                            MapLayerIntensity("Intensidad de Catastro", cadastreOpacity,
                                enabled = cadastreLines && base != MapBase.NONE,
                                tag = "farm-map-cadastre-intensity", onChange = { cadastreOpacity = it })
                            MapLayerIntensity("Intensidad de SIGPAC", sigpacOpacity,
                                enabled = sigpacLines && base != MapBase.NONE,
                                tag = "farm-map-sigpac-intensity", onChange = { sigpacOpacity = it })
                        }
                    }
                    if (compactHeader && base == MapBase.NONE && state.mode == FarmMapMode.VIEW) TextButton(
                        onClick = onSearchByReference, modifier = Modifier.testTag("farm-map-reference-fallback"),
                    ) { Text("Añadir por referencia") }
                }
                if (!compactHeader && base == MapBase.NONE && state.mode == FarmMapMode.VIEW) TextButton(
                    onClick = onSearchByReference, modifier = Modifier.testTag("farm-map-reference-fallback"),
                ) { Text("Añadir por referencia") }
                if (searchOpen) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoTextField(value = placeQuery, onValueChange = { placeQuery = it; onPlaceQueryChanged() },
                            label = "Pueblo o paraje", modifier = Modifier.weight(1f).testTag("farm-map-place-query"),
                            isError = state.placeError != null,
                        )
                        TextButton(onClick = { onSearchPlace(placeQuery) }, enabled = !state.placeSearching,
                            modifier = Modifier.testTag("farm-map-place-go")) { Text("Ir") }
                    }
                    Row {
                        localMapPlaces(placeQuery).forEach { place ->
                            TextButton(onClick = { onSelectPlace(place) }, modifier = Modifier.testTag("farm-map-place-${place.id}")) {
                                Text(place.label.substringBefore(" · "))
                            }
                        }
                    }
                    if (state.placeSearching) Text("Buscando lugar…", style = MaterialTheme.typography.bodySmall)
                    state.placeError?.let {
                        Text(it, color = MoColors.current.errorText, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("farm-map-place-error"))
                    }
                    state.placeCandidates.forEach { place ->
                        TextButton(onClick = { onSelectPlace(place) }, modifier = Modifier.testTag("farm-map-place-${place.id}")) {
                            Text(place.label, maxLines = 2)
                        }
                    }
                    Text("CartoCiudad · IGN", style = MaterialTheme.typography.labelSmall, color = MoColors.current.secondaryText)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = "Coordenadas",
                            supportingText = "Ejemplo: 37.636, -3.480",
                            modifier = Modifier.weight(1f).testTag("farm-map-coordinates"),
                        )
                        TextButton(onClick = { onSearchCoordinates(query) }, modifier = Modifier.testTag("farm-map-go")) { Text("Ir") }
                    }
                    TextButton(onClick = { polygonSheet = true }, modifier = Modifier.testTag("farm-map-polygon")) {
                        Text("Buscar por polígono y parcela")
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .onSizeChanged { bottomOverlayHeightPx = it.height }
                .padding(horizontal = 8.dp, vertical = if (compactHeader) 4.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.searching) Notice("Consultando Catastro…", MoColors.current.secondaryText, progress = true)
            tileError?.let { source ->
                MapTileProblemNotice(
                    when {
                        source == "sigpac" -> "Algunos recintos SIGPAC no se han podido cargar."
                        source == "cadastre" -> "Algunos límites de Catastro no se han podido cargar."
                        base == MapBase.AERIAL -> "Algunas imágenes aéreas no están disponibles."
                        else -> "Parte del mapa no se ha podido cargar."
                    },
                    onOffline = { tileError = null; base = MapBase.NONE },
                    onRetry = { tileError = null; tileRetryToken = System.nanoTime() },
                )
            }
            state.locationProblem?.let { problem ->
                LocationProblemNotice(
                    problem,
                    onAction = {
                        if (problem == LocationProblem.NO_FIX) searchOpen = true
                        onLocationProblemAction(problem)
                    },
                    onDismiss = onDismissLocationProblem,
                )
            }
            if (state.myLocation != null && state.locationProblem == null && state.mode == FarmMapMode.VIEW && state.selectedSavedId == null) {
                Notice("El punto azul es tu ubicación.", MoColors.current.secondaryText, Modifier.testTag("farm-map-my-location-shown"))
            }
            if (state.mode != FarmMapMode.LOCATE && state.parcels.none { it.geometryGeoJson != null } && state.selected.isEmpty()
                && !searchOpen && state.locationProblem == null && tileError == null) {
                FarmMapGuide(Modifier.testTag("farm-map-guide"))
            }
            state.error?.let { Notice(it, MoColors.current.errorText, Modifier.testTag("farm-map-error")) }
            state.message?.let { Notice(it, MoColors.current.successText, Modifier.testTag("farm-map-message")) }
            BottomPanel(
                state, onOpenParcel, onSearchByReference, onReview = { reviewSheet = true }, onLink = onLink,
                onAddInspected = onAddInspected, onDismissInspected = onDismissInspected,
                compact = compactHeader,
            )
        }
    }

    if (polygonSheet) {
        ModalBottomSheet(onDismissRequest = { polygonSheet = false }) {
            PolygonParcelForm(
                province = state.farm?.province.orEmpty(),
                municipality = state.farm?.municipality.orEmpty(),
                onSearch = { province, municipality, polygon, parcel ->
                    polygonSheet = false
                    searchOpen = false
                    onSearchPolygonParcel(province, municipality, polygon, parcel)
                },
            )
        }
    }
    if (reviewSheet && state.selected.isNotEmpty() && state.mode == FarmMapMode.ADD) {
        ModalBottomSheet(onDismissRequest = { reviewSheet = false }) {
            ImportReview(
                farmName = state.farm?.name.orEmpty(),
                candidates = state.selectedCandidates,
                saving = state.saving,
                onConfirm = { names -> reviewSheet = false; onImport(names) },
            )
        }
    }
}

/** #722: official reference layers can be read independently over the photo. */
@Composable
private fun MapLayerIntensity(label: String, value: Float, enabled: Boolean, tag: String, onChange: (Float) -> Unit) {
    Column(Modifier.width(240.dp).padding(horizontal = 16.dp)) {
        Text("$label · ${(value * 100).roundToInt()} %", style = MaterialTheme.typography.labelMedium,
            color = MoColors.current.secondaryText)
        Slider(value = value, onValueChange = onChange, enabled = enabled, valueRange = 0.3f..1f,
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics {
                contentDescription = label
                stateDescription = "${(value * 100).roundToInt()} % de intensidad"
            })
    }
}

/** #711 B3: a remote raster failure must not prevent use of the local parcels. */
@Composable
private fun MapTileProblemNotice(message: String, onOffline: () -> Unit, onRetry: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().testTag("farm-map-tile-error"),
        shape = MoShape.card, color = MoSurfaceTokens.cardSurface.copy(alpha = 0.97f), shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, color = MoColors.current.errorText, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("farm-map-tile-retry")) {
                    Text("Reintentar")
                }
                TextButton(onClick = onOffline, modifier = Modifier.testTag("farm-map-offline-fallback")) {
                    Text("Solo parcelas")
                }
            }
        }
    }
}

/** #361: «Mi ubicación» could not answer — say why and offer the one useful way out. */
@Composable
private fun LocationProblemNotice(problem: LocationProblem, onAction: () -> Unit, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("farm-map-location-problem"), shape = MoShape.card, color = MoSurfaceTokens.cardSurface.copy(alpha = 0.97f), shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(problem.message, color = MoColors.current.errorText, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                TextButton(onClick = onAction, modifier = Modifier.testTag("farm-map-location-action")) { Text(problem.action) }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("farm-map-location-dismiss")) { Text("Cancelar") }
            }
        }
    }
}

/** #361: what to do first, in four short steps, until the farm has a parcel on the map. */
@Composable
private fun FarmMapGuide(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MoShape.card, color = MoSurfaceTokens.cardSurface.copy(alpha = 0.97f), shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Cómo añadir tus parcelas", style = MaterialTheme.typography.titleSmall, color = MoColors.current.primaryText)
            FARM_MAP_STEPS.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall, color = MoColors.current.secondaryText)
            }
        }
    }
}

internal val FARM_MAP_STEPS = listOf(
    "Busca el pueblo, coordenadas o polígono.",
    "Usa «Mi ubicación» si estás en la finca.",
    "Toca dentro de tu parcela y revisa sus datos.",
    "Revisa y pulsa «Añadir»: se guardan con su municipio.",
)

@Composable
private fun Notice(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier, progress: Boolean = false) {
    Surface(modifier, shape = MoShape.card, color = MoSurfaceTokens.cardSurface.copy(alpha = 0.97f), shadowElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (progress) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BottomPanel(
    state: FarmMapState,
    onOpenParcel: (UUID) -> Unit,
    onSearchByReference: () -> Unit,
    onReview: () -> Unit,
    onLink: () -> Unit,
    onAddInspected: () -> Unit = {},
    onDismissInspected: () -> Unit = {},
    compact: Boolean = false,
) {
    when (state.mode) {
        FarmMapMode.VIEW -> {
            val parcel = state.parcels.firstOrNull { it.id == state.selectedSavedId }
            val read = state.inspected
            if (read != null) {
                OfficialParcelCard(read, state.inspectedPlace, onAdd = onAddInspected, onDismiss = onDismissInspected)
            } else if (parcel != null) {
                MoSectionCard(title = parcel.displayName, icon = MoIcons.Parcels, modifier = Modifier.testTag("farm-map-parcel-card")) {
                    // #361: say it is the selected, saved parcel, and where it is.
                    Text(
                        listOfNotNull("Seleccionada · Guardada", parcel.municipality?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MoColors.current.successText,
                        modifier = Modifier.testTag("farm-map-parcel-status"),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                        MoLabeledValue("Superficie catastral", parcel.cadastralAreaM2?.let(::hectares), Modifier.weight(1f))
                        MoLabeledValue("Superficie gestionada", parcel.managedAreaM2?.let(::hectares), Modifier.weight(1f))
                    }
                    MoPrimaryButton("Abrir parcela", { onOpenParcel(parcel.id) }, Modifier.fillMaxWidth())
                }
            }
        }
        FarmMapMode.ADD -> Surface(shape = MoShape.card, color = MoSurfaceTokens.cardSurface.copy(alpha = 0.97f), shadowElevation = 3.dp) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = if (compact) 0.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // One short line per marked parcel instead of a list of every number under the map.
                state.selectedCandidates.lastOrNull()?.let { last ->
                    Text(
                        selectionLine(last) + if (state.selected.size > 1) " · y ${state.selected.size - 1} más" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoColors.current.primaryText,
                        maxLines = 1,
                        modifier = Modifier.testTag("farm-map-selection"),
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MoPrimaryButton(
                        text = when (state.selected.size) {
                            0 -> "Toca los números de tus parcelas"
                            1 -> "Añadir 1 parcela"
                            else -> "Añadir ${state.selected.size} parcelas"
                        },
                        onClick = onReview,
                        enabled = state.selected.isNotEmpty() && !state.saving,
                        modifier = (if (compact) Modifier.weight(1f) else Modifier.fillMaxWidth()).testTag("farm-map-add-selected"),
                    )
                    if (compact) MoTertiaryButton("Tengo la referencia catastral", onSearchByReference)
                }
                if (!compact)
                MoTertiaryButton("Tengo la referencia catastral", onSearchByReference, Modifier.align(Alignment.CenterHorizontally))
            }
        }
        FarmMapMode.LOCATE -> Surface(shape = MoShape.card, color = MoSurfaceTokens.cardSurface.copy(alpha = 0.97f), shadowElevation = 3.dp) {
            val candidate = state.selectedCandidates.singleOrNull()
            Column(Modifier.padding(horizontal = 10.dp, vertical = if (compact) 0.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                candidate?.let { Text(selectionLine(it), style = MaterialTheme.typography.bodyMedium, color = MoColors.current.primaryText, maxLines = 1) }
                MoPrimaryButton(
                    text = when {
                        state.saving -> "Guardando…"
                        candidate == null -> "Toca el número de tu parcela"
                        else -> "Vincular con ${candidate.reference}" + (candidate.areaM2?.let { " · ${hectares(it)}" } ?: "")
                    },
                    onClick = onLink,
                    enabled = candidate != null && !state.saving,
                    modifier = Modifier.fillMaxWidth().testTag("farm-map-link"),
                )
            }
        }
    }
}

/**
 * #711 B3 (owner's order): what Catastro says about the parcel the farmer just touched —
 * reference, polygon and parcel, surface and place — and the one action that follows. The copy
 * the app would keep is a reference boundary, never a cadastral certificate, and that is said.
 */
@Composable
private fun OfficialParcelCard(
    candidate: CadastralCandidate,
    place: com.isivoltpro.maginaolivo.domain.registry.RegistryLocation?,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    MoSectionCard(
        title = defaultParcelName(candidate.reference),
        icon = MoIcons.Parcels,
        modifier = Modifier.testTag("farm-map-official-card"),
    ) {
        Text(
            "Parcela de Catastro · no está en tu olivar",
            style = MaterialTheme.typography.bodySmall,
            color = MoColors.current.secondaryText,
            modifier = Modifier.testTag("farm-map-official-status"),
        )
        MoLabeledValue("Referencia catastral", candidate.reference, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
            MoLabeledValue("Superficie catastral", candidate.areaM2?.let(::hectares), Modifier.weight(1f))
            MoLabeledValue(
                "Municipio",
                place?.let { listOfNotNull(it.municipality, it.province).joinToString(" · ") },
                Modifier.weight(1f),
            )
        }
        Text(
            "Datos de Catastro. El contorno guardado es una referencia, no un certificado catastral.",
            style = MaterialTheme.typography.bodySmall,
            color = MoColors.current.secondaryText,
        )
        MoPrimaryButton("Añadir a la finca", onAdd, Modifier.fillMaxWidth().testTag("farm-map-official-add"))
        MoTertiaryButton("Cerrar", onDismiss, Modifier.align(Alignment.CenterHorizontally).testTag("farm-map-official-close"))
    }
}

/** "Pol. 4 · Parc. 120 · 23044A00400120 · 1,2 ha" — what the farmer checks before confirming. */
private fun selectionLine(candidate: CadastralCandidate): String =
    listOfNotNull(defaultParcelName(candidate.reference), candidate.reference, candidate.areaM2?.let(::hectares)).joinToString(" · ")

@Composable
private fun PolygonParcelForm(
    province: String,
    municipality: String,
    onSearch: (String, String, String, String) -> Unit,
) {
    var provinceValue by rememberSaveable { mutableStateOf(province) }
    var municipalityValue by rememberSaveable { mutableStateOf(municipality) }
    var polygon by rememberSaveable { mutableStateOf("") }
    var parcel by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.padding(horizontal = MoSpacing.screen).padding(bottom = MoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Buscar por polígono y parcela", style = MaterialTheme.typography.titleLarge, color = MoColors.current.primaryText)
        Text(
            "Son los datos que aparecen en la PAC, la cooperativa o las escrituras.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoColors.current.secondaryText,
        )
        MoTextField(provinceValue, { provinceValue = it }, "Provincia", Modifier.fillMaxWidth().testTag("polygon-province"))
        MoTextField(municipalityValue, { municipalityValue = it }, "Municipio", Modifier.fillMaxWidth().testTag("polygon-municipality"))
        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
            MoTextField(polygon, { polygon = it.filter(Char::isDigit).take(5) }, "Polígono", Modifier.weight(1f).testTag("polygon-number"))
            MoTextField(parcel, { parcel = it.filter(Char::isDigit).take(5) }, "Parcela", Modifier.weight(1f).testTag("polygon-parcel"))
        }
        MoPrimaryButton(
            "Buscar en Catastro",
            { onSearch(provinceValue, municipalityValue, polygon, parcel) },
            Modifier.fillMaxWidth().testTag("polygon-search"),
            enabled = provinceValue.isNotBlank() && municipalityValue.isNotBlank() && polygon.isNotBlank() && parcel.isNotBlank(),
        )
    }
}

/** Names each selected parcel before they are incorporated; nothing is saved without it. */
@Composable
fun ImportReview(
    farmName: String,
    candidates: List<CadastralCandidate>,
    saving: Boolean,
    onConfirm: (Map<String, String>) -> Unit,
) {
    val names = remember(candidates) {
        mutableStateMapOf<String, String>().apply { candidates.forEach { put(it.reference, defaultParcelName(it.reference)) } }
    }
    Column(
        Modifier.padding(horizontal = MoSpacing.screen).padding(bottom = MoSpacing.lg).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(
            if (candidates.size == 1) "Añadir 1 parcela a $farmName" else "Añadir ${candidates.size} parcelas a $farmName",
            style = MaterialTheme.typography.titleLarge,
            color = MoColors.current.primaryText,
        )
        Text(
            "Ponles el nombre con el que las conoces. El contorno y la superficie vienen de Catastro; la copia guardada no es un certificado catastral.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoColors.current.secondaryText,
        )
        candidates.forEach { candidate ->
            MoTextField(
                value = names[candidate.reference].orEmpty(),
                onValueChange = { names[candidate.reference] = it.take(80) },
                label = "Nombre",
                supportingText = listOfNotNull(candidate.reference, candidate.areaM2?.let(::hectares)).joinToString(" · "),
                modifier = Modifier.fillMaxWidth().testTag("import-name-${candidate.reference}"),
            )
        }
        MoPrimaryButton(
            text = if (saving) "Guardando…" else "Incorporar a la finca",
            onClick = { onConfirm(names.toMap()) },
            enabled = !saving && names.values.all { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth().testTag("farm-map-import-confirm"),
        )
    }
}

private fun hectares(areaM2: Double): String =
    "${NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).apply { maximumFractionDigits = 2 }.format(areaM2 / 10_000)} ha"
