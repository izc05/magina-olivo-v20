package com.isivoltpro.maginaolivo.feature.maps

import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import com.isivoltpro.maginaolivo.ui.theme.MoShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
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

/**
 * Phase 18 — the farm's parcels on a map, and the place to add them: tap several Catastro
 * parcels and incorporate them in one go, or give a hand-made parcel its location.
 * Boundaries are drawn from the phone's copy; aerial imagery needs a connection.
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
    val viewModel: FarmMapViewModel = viewModel(
        key = "farm-map-$farmId-$locateParcelId",
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
        onMyLocation = { if (hasLocationPermission(context)) locate() else permission.launch(LOCATION_PERMISSIONS) },
        onSearchPolygonParcel = viewModel::searchPolygonParcel,
        onSearchByReference = onSearchByReference,
        onTapMap = viewModel::tapMap,
        onTapParcel = viewModel::tapParcel,
        onImport = viewModel::importSelected,
        onLink = viewModel::linkSelected,
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
) {
    var query by rememberSaveable { mutableStateOf("") }
    // The light IGN map by default: the aerial photo is heavier on the phone and is one tap away.
    var base by rememberSaveable { mutableStateOf(MapBase.MAP) }
    var cadastreLines by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var layerMenu by remember { mutableStateOf(false) }
    var polygonSheet by rememberSaveable { mutableStateOf(false) }
    var reviewSheet by rememberSaveable { mutableStateOf(false) }
    var topOverlayHeightPx by remember { mutableStateOf(0) }
    var bottomOverlayHeightPx by remember { mutableStateOf(0) }
    val mapped = remember(state.parcels, state.candidates, state.taken) {
        state.parcels.mapNotNull { p -> p.geometryGeoJson?.let { MapParcel(p.id.toString(), p.displayName, it) } } +
            state.candidates.filter { it.reference !in state.taken }
                .map { MapParcel(it.reference, it.reference, it.geometryGeoJson, MapParcelKind.CANDIDATE, parcelNumber(it.reference)) }
    }
    val withBoundary = state.parcels.count { it.geometryGeoJson != null }

    Box(Modifier.fillMaxSize().background(MoSurfaceTokens.appBackground).testTag("farm-map-root")) {
        if (showMap) {
            ParcelMap(
                parcels = mapped,
                modifier = Modifier.fillMaxSize(),
                base = base,
                cadastreLines = cadastreLines,
                selectedId = state.selectedSavedId?.toString(),
                selectedIds = state.selected,
                onSelected = onTapParcel,
                onTap = { latitude, longitude -> onTapMap(latitude, longitude) },
                focus = state.focus,
                cameraInsets = MapCameraInsets(
                    topPx = topOverlayHeightPx,
                    bottomPx = bottomOverlayHeightPx,
                ),
                myLocation = state.myLocation,
            )
        }
        // Everything floats over the map, so the map takes the whole screen.
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .onSizeChanged { topOverlayHeightPx = it.height }
                .padding(8.dp),
            shape = MoShape.card,
            color = MoSurfaceTokens.cardSurface.copy(alpha = 0.97f),
            shadowElevation = 3.dp,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (state.mode == FarmMapMode.LOCATE) "Ubicar «${state.locateParcel?.displayName.orEmpty()}»" else state.farm?.name ?: "Mapa de la finca",
                    style = MaterialTheme.typography.titleMedium,
                    color = MoColors.current.primaryText,
                    maxLines = 1,
                )
                Text(
                    when (state.mode) {
                        FarmMapMode.LOCATE -> "Toca donde está tu parcela y elige su número."
                        FarmMapMode.ADD -> "Toca donde están tus parcelas y marca sus números."
                        FarmMapMode.VIEW -> listOfNotNull(
                            "$withBoundary con límites",
                            (state.parcels.size - withBoundary).takeIf { it > 0 }?.let { "$it sin ubicar" },
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MoColors.current.secondaryText,
                    maxLines = 2,
                    modifier = Modifier.testTag("farm-map-hint"),
                )
                // #361: one clear main action. Viewing, «Añadir de Catastro» leads; adding, the way
                // back to «Mis parcelas» is a quiet secondary link, not a competing button.
                when (state.mode) {
                    FarmMapMode.VIEW -> MoPrimaryButton(
                        "Añadir de Catastro",
                        { onMode(FarmMapMode.ADD) },
                        Modifier.fillMaxWidth().testTag("farm-map-mode-add"),
                    )
                    FarmMapMode.ADD -> TextButton(onClick = { onMode(FarmMapMode.VIEW) }, modifier = Modifier.testTag("farm-map-mode-view")) {
                        Text("← Volver a mis parcelas")
                    }
                    FarmMapMode.LOCATE -> Unit
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.testTag("farm-map-search-toggle")) {
                        Text(if (searchOpen) "Cerrar búsqueda" else "Buscar")
                    }
                    TextButton(onClick = onMyLocation, modifier = Modifier.testTag("farm-map-my-location")) { Text("Mi ubicación") }
                    Box {
                        TextButton(onClick = { layerMenu = true }, modifier = Modifier.testTag("farm-map-layer")) { Text(base.label) }
                        DropdownMenu(expanded = layerMenu, onDismissRequest = { layerMenu = false }) {
                            MapBase.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(if (option == base) "✓ ${option.label}" else option.label) },
                                    onClick = { base = option; layerMenu = false },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(if (cadastreLines) "Ocultar linderos de Catastro" else "Ver linderos de Catastro") },
                                onClick = { cadastreLines = !cadastreLines; layerMenu = false },
                                enabled = base != MapBase.NONE,
                            )
                        }
                    }
                }
                if (searchOpen) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = "Coordenadas (37.636, -3.480)",
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
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.searching) Notice("Consultando Catastro…", MoColors.current.secondaryText, progress = true)
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
            if (state.mode != FarmMapMode.LOCATE && state.parcels.none { it.geometryGeoJson != null } && state.selected.isEmpty()) {
                FarmMapGuide(Modifier.testTag("farm-map-guide"))
            }
            state.error?.let { Notice(it, MoColors.current.errorText, Modifier.testTag("farm-map-error")) }
            state.message?.let { Notice(it, MoColors.current.successText, Modifier.testTag("farm-map-message")) }
            BottomPanel(state, onOpenParcel, onSearchByReference, onReview = { reviewSheet = true }, onLink = onLink)
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
    "Pulsa «Añadir de Catastro» o busca por coordenadas o polígono.",
    "Usa «Mi ubicación» si estás en la finca.",
    "Toca los números de tus parcelas en el mapa.",
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
) {
    when (state.mode) {
        FarmMapMode.VIEW -> {
            val parcel = state.parcels.firstOrNull { it.id == state.selectedSavedId }
            if (parcel != null) {
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
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                MoPrimaryButton(
                    text = when (state.selected.size) {
                        0 -> "Toca los números de tus parcelas"
                        1 -> "Añadir 1 parcela"
                        else -> "Añadir ${state.selected.size} parcelas"
                    },
                    onClick = onReview,
                    enabled = state.selected.isNotEmpty() && !state.saving,
                    modifier = Modifier.fillMaxWidth().testTag("farm-map-add-selected"),
                )
                MoTertiaryButton("Tengo la referencia catastral", onSearchByReference, Modifier.align(Alignment.CenterHorizontally))
            }
        }
        FarmMapMode.LOCATE -> Surface(shape = MoShape.card, color = MoSurfaceTokens.cardSurface.copy(alpha = 0.97f), shadowElevation = 3.dp) {
            val candidate = state.selectedCandidates.singleOrNull()
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
