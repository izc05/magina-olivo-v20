package com.isivoltpro.maginaolivo.feature.maps

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
import androidx.compose.material3.FilterChip
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
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoErrorText
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSuccessText
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
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
    val context = LocalContext.current
    val locate = {
        requestCurrentLocation(context) { point -> if (point != null) viewModel.goTo(point) else viewModel.locationUnavailable() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) locate() else viewModel.locationUnavailable()
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
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Aerial photo by default: without it an empty base shows nothing to find the land by.
    var imagery by rememberSaveable { mutableStateOf(true) }
    var polygonSheet by rememberSaveable { mutableStateOf(false) }
    var reviewSheet by rememberSaveable { mutableStateOf(false) }
    val mapped = remember(state.parcels, state.candidates, state.taken) {
        state.parcels.mapNotNull { p -> p.geometryGeoJson?.let { MapParcel(p.id.toString(), p.displayName, it) } } +
            state.candidates.filter { it.reference !in state.taken }
                .map { MapParcel(it.reference, it.reference, it.geometryGeoJson, MapParcelKind.CANDIDATE) }
    }
    val withBoundary = state.parcels.count { it.geometryGeoJson != null }

    Surface(color = MoCream, modifier = Modifier.fillMaxSize().testTag("farm-map-root")) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Spacer(Modifier.height(MoSpacing.xs))
            Text(
                if (state.mode == FarmMapMode.LOCATE) "Ubicar parcela" else "Mapa de la finca",
                style = MaterialTheme.typography.headlineMedium,
                color = MoOliveDark,
            )
            Text(
                when (state.mode) {
                    FarmMapMode.LOCATE -> "Toca en el mapa donde está «${state.locateParcel?.displayName.orEmpty()}» y elige su parcela de Catastro."
                    FarmMapMode.ADD -> "Toca en el mapa donde están tus parcelas y marca todas las que quieras añadir."
                    FarmMapMode.VIEW -> listOfNotNull(
                        state.farm?.name,
                        "$withBoundary con límites",
                        (state.parcels.size - withBoundary).takeIf { it > 0 }?.let { "$it sin ubicar" },
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
                modifier = Modifier.testTag("farm-map-hint"),
            )
            if (state.mode != FarmMapMode.LOCATE) {
                Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                    FilterChip(
                        selected = state.mode == FarmMapMode.VIEW,
                        onClick = { onMode(FarmMapMode.VIEW) },
                        label = { Text("Mis parcelas") },
                        modifier = Modifier.testTag("farm-map-mode-view"),
                    )
                    FilterChip(
                        selected = state.mode == FarmMapMode.ADD,
                        onClick = { onMode(FarmMapMode.ADD) },
                        label = { Text("Añadir de Catastro") },
                        modifier = Modifier.testTag("farm-map-mode-add"),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Coordenadas (37.636, -3.480)",
                    modifier = Modifier.weight(1f).testTag("farm-map-coordinates"),
                )
                TextButton(onClick = { onSearchCoordinates(query) }, modifier = Modifier.testTag("farm-map-go")) { Text("Ir") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MoTertiaryButton("Mi ubicación", onMyLocation, Modifier.testTag("farm-map-my-location"))
                MoTertiaryButton("Polígono y parcela", { polygonSheet = true }, Modifier.testTag("farm-map-polygon"))
                MoTertiaryButton(if (imagery) "Sin foto" else "Foto aérea", { imagery = !imagery })
            }
            if (showMap) {
                ParcelMap(
                    parcels = mapped,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    imagery = imagery,
                    selectedId = state.selectedSavedId?.toString(),
                    selectedIds = state.selected,
                    onSelected = onTapParcel,
                    onTap = { latitude, longitude -> onTapMap(latitude, longitude) },
                    focus = state.focus,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (state.searching) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            state.error?.let { Text(it, color = MoErrorText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("farm-map-error")) }
            state.message?.let { Text(it, color = MoSuccessText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("farm-map-message")) }
            BottomPanel(state, onOpenParcel, onSearchByReference, onReview = { reviewSheet = true }, onLink = onLink)
            Spacer(Modifier.height(MoSpacing.xs))
        }
    }

    if (polygonSheet) {
        ModalBottomSheet(onDismissRequest = { polygonSheet = false }) {
            PolygonParcelForm(
                province = state.farm?.province.orEmpty(),
                municipality = state.farm?.municipality.orEmpty(),
                onSearch = { province, municipality, polygon, parcel ->
                    polygonSheet = false
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                        MoLabeledValue("Superficie catastral", parcel.cadastralAreaM2?.let(::hectares), Modifier.weight(1f))
                        MoLabeledValue("Superficie gestionada", parcel.managedAreaM2?.let(::hectares), Modifier.weight(1f))
                    }
                    MoPrimaryButton("Abrir parcela", { onOpenParcel(parcel.id) }, Modifier.fillMaxWidth())
                }
            } else if (state.parcels.none { it.geometryGeoJson != null }) {
                Text(
                    "Pulsa «Añadir de Catastro» para marcar tus parcelas en el mapa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                )
            }
        }
        FarmMapMode.ADD -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MoPrimaryButton(
                text = when (state.selected.size) {
                    0 -> "Marca tus parcelas en el mapa"
                    1 -> "Añadir 1 parcela"
                    else -> "Añadir ${state.selected.size} parcelas"
                },
                onClick = onReview,
                enabled = state.selected.isNotEmpty() && !state.saving,
                modifier = Modifier.fillMaxWidth().testTag("farm-map-add-selected"),
            )
            MoTertiaryButton("Tengo la referencia catastral", onSearchByReference, Modifier.align(Alignment.CenterHorizontally))
        }
        FarmMapMode.LOCATE -> {
            val candidate = state.selectedCandidates.singleOrNull()
            MoPrimaryButton(
                text = when {
                    state.saving -> "Guardando…"
                    candidate == null -> "Toca tu parcela en el mapa"
                    else -> "Vincular con ${candidate.reference}" + (candidate.areaM2?.let { " · ${hectares(it)}" } ?: "")
                },
                onClick = onLink,
                enabled = candidate != null && !state.saving,
                modifier = Modifier.fillMaxWidth().testTag("farm-map-link"),
            )
        }
    }
}

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
        Text("Buscar por polígono y parcela", style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
        Text(
            "Son los datos que aparecen en la PAC, la cooperativa o las escrituras.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoTextSecondary,
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
            color = MoOliveDark,
        )
        Text(
            "Ponles el nombre con el que las conoces. El contorno y la superficie vienen de Catastro; la copia guardada no es un certificado catastral.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoTextSecondary,
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
