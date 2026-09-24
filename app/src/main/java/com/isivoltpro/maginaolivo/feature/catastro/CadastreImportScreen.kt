package com.isivoltpro.maginaolivo.feature.catastro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@Composable
fun CadastreImportRoute(
    persistence: LocalPersistence,
    preselectedFarmId: UUID?,
    onParcelImported: (UUID) -> Unit,
) {
    val model: CadastreImportViewModel = viewModel(key = "catastro-$preselectedFarmId", factory = viewModelFactory {
        initializer { CadastreImportViewModel(persistence, OfficialCadastreClient()) }
    })
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedParcelId) { state.savedParcelId?.let(onParcelImported) }
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val file = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { model.importFile(resolver, it) } }
    CadastreImportScreen(state, preselectedFarmId, model::search, model::import,
        onNear = model::searchNear, onSelect = model::selectCandidate,
        onFile = { file.launch(arrayOf("*/*")) }, onOpenExisting = onParcelImported)

}

@Composable
fun CadastreImportScreen(
    state: CadastreImportState,
    preselectedFarmId: UUID?,
    onSearch: (String) -> Unit,
    onImport: (UUID?, String) -> Unit,
    onNear: ((Double, Double) -> Unit)? = null,
    onSelect: (String) -> Unit = {},
    onFile: (() -> Unit)? = null,
    onOpenExisting: (UUID) -> Unit = {},
) {
    var showMap by rememberSaveable { mutableStateOf(false) }
    var base by rememberSaveable { mutableStateOf(com.isivoltpro.maginaolivo.feature.maps.MapBase.MAP) }
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    var reference by rememberSaveable { mutableStateOf("") }
    var alias by rememberSaveable { mutableStateOf("") }
    var selectedFarm by rememberSaveable { mutableStateOf(preselectedFarmId?.toString()) }
    // With a single farm there is nothing to choose.
    LaunchedEffect(state.farms) {
        if (selectedFarm == null) state.farms.singleOrNull()?.let { selectedFarm = it.id.toString() }
    }
    LaunchedEffect(state.candidate?.reference) {
        state.candidate?.let { alias = "Parcela ${it.reference.takeLast(5)}" }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("catastro-root"),
        containerColor = MoCream,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen, vertical = MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            Text("Buscar en Catastro", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "Consulta una parcela real por su referencia catastral. Revísala antes de incorporarla a una finca.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )
            Row {
                onNear?.let { androidx.compose.material3.TextButton(onClick = { showMap = !showMap }) { Text(if (showMap) "Cerrar mapa" else "Elegir en mapa") } }
                onFile?.let { androidx.compose.material3.TextButton(onClick = it, enabled = !state.saving && !state.searching) { Text("Abrir GML") } }
            }
            if (showMap) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Toca la zona y luego el número de tu parcela.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoTextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    com.isivoltpro.maginaolivo.feature.maps.MapBase.entries.forEach { option ->
                        androidx.compose.material3.FilterChip(
                            selected = base == option,
                            onClick = { base = option },
                            label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                // A tall map, numbers drawn on each parcel: no list of every number underneath.
                com.isivoltpro.maginaolivo.feature.maps.ParcelMap(
                    parcels = state.candidates.map {
                        com.isivoltpro.maginaolivo.feature.maps.MapParcel(
                            it.reference, it.reference, it.geometryGeoJson,
                            com.isivoltpro.maginaolivo.feature.maps.MapParcelKind.CANDIDATE,
                            com.isivoltpro.maginaolivo.feature.maps.parcelNumber(it.reference),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height((screenHeight * 0.62f).dp).testTag("catastro-map"),
                    base = base,
                    selectedId = state.candidate?.reference, onSelected = onSelect,
                    onTap = { latitude, longitude -> if (!state.saving) onNear?.invoke(latitude, longitude) },
                )
            }
            // Several parcels from a GML file and no map open: choose by number here instead.
            if (!showMap && state.candidates.size > 1) {
                Text("Elige una de las ${state.candidates.size} parcelas del archivo", color = MoTextSecondary)
                state.candidates.forEach { option ->
                    androidx.compose.material3.TextButton(onClick = { onSelect(option.reference) }, enabled = !state.saving) { Text(option.reference) }
                }
            }
            state.duplicateId?.let { id ->
                androidx.compose.material3.TextButton(onClick = { onOpenExisting(id) }) { Text("Abrir parcela existente") }
            }
            MoTextField(
                value = reference,
                onValueChange = { reference = it.uppercase().filter(Char::isLetterOrDigit).take(14) },
                label = "Referencia catastral (14 caracteres)",
                modifier = Modifier.fillMaxWidth().testTag("catastro-reference"),
            )
            MoPrimaryButton(
                text = if (state.searching) "Consultando…" else "Buscar parcela",
                onClick = { onSearch(reference) },
                modifier = Modifier.fillMaxWidth().testTag("catastro-search"),
                enabled = !state.searching && !state.saving,
            )
            if (state.searching) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("catastro-error")) }

            state.candidate?.let { candidate ->
                Card(
                    shape = MoShape.card,
                    colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
                    modifier = Modifier.fillMaxWidth().testTag("catastro-candidate"),
                ) {
                    Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                        Text(
                            com.isivoltpro.maginaolivo.feature.maps.defaultParcelName(candidate.reference),
                            style = MaterialTheme.typography.titleLarge,
                            color = MoOliveDark,
                        )
                        Text(
                            listOfNotNull(candidate.reference, candidate.areaM2?.let { "${formatHectares(it)} ha" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        // The map already shows it; the small drawing is only for a reference search.
                        if (!showMap) CandidateGeometryPreview(candidate, Modifier.fillMaxWidth().height(140.dp))
                        Text(
                            "Contorno recibido del servicio INSPIRE de la Dirección General del Catastro. La copia guardada no es un certificado catastral actualizado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MoTextSecondary,
                        )
                    }
                }
                Text("Incorporar a una finca", style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                if (state.farms.isEmpty()) {
                    Text("Crea primero una finca en Mi Olivar.", color = MoTextSecondary)
                } else {
                    state.farms.forEach { farm ->
                        Card(
                            onClick = { selectedFarm = farm.id.toString() },
                            modifier = Modifier.fillMaxWidth().testTag("catastro-farm-${farm.id}"),
                            colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = MoSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selectedFarm == farm.id.toString(), onClick = { selectedFarm = farm.id.toString() })
                                Text(farm.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                MoTextField(alias, { alias = it }, "Nombre para esta parcela", Modifier.fillMaxWidth())
                MoPrimaryButton(
                    text = if (state.saving) "Guardando…" else "Confirmar e incorporar",
                    onClick = { onImport(selectedFarm?.let(UUID::fromString), alias) },
                    modifier = Modifier.fillMaxWidth().testTag("catastro-import"),
                    enabled = !state.saving && !state.searching && state.farms.any { it.id.toString() == selectedFarm } && alias.isNotBlank(),
                )
            }
            Spacer(Modifier.height(MoSpacing.lg))
        }
    }
}

@Composable
private fun CandidateGeometryPreview(candidate: CadastralCandidate, modifier: Modifier = Modifier) {
    val points = candidate.polygons.flatten().flatten()
    val minLon = points.minOf { it.first }
    val maxLon = points.maxOf { it.first }
    val minLat = points.minOf { it.second }
    val maxLat = points.maxOf { it.second }
    val longitudeScale = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))
    val lonSpan = ((maxLon - minLon) * longitudeScale).takeIf { it > 0 } ?: 1.0
    val latSpan = (maxLat - minLat).takeIf { it > 0 } ?: 1.0
    Canvas(modifier.testTag("catastro-geometry-preview")) {
        val scale = minOf(size.width.toDouble() * 0.84 / lonSpan, size.height.toDouble() * 0.84 / latSpan).toFloat()
        val usedWidth = (lonSpan * scale).toFloat()
        val usedHeight = (latSpan * scale).toFloat()
        val left = (size.width - usedWidth) / 2
        val top = (size.height - usedHeight) / 2
        candidate.polygons.forEach { polygon ->
            val path = Path().apply { fillType = PathFillType.EvenOdd }
            polygon.forEach { ring ->
                ring.forEachIndexed { index, point ->
                    val x = left + ((point.first - minLon) * longitudeScale * scale).toFloat()
                    val y = top + usedHeight - ((point.second - minLat) * scale).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            drawPath(path, MoOlivePrimary.copy(alpha = 0.20f))
            drawPath(path, MoOliveDark, style = Stroke(width = 3.dp.toPx()))
        }
    }
}

private fun formatHectares(areaM2: Double): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).apply { maximumFractionDigits = 2 }
        .format(areaM2 / 10_000)
