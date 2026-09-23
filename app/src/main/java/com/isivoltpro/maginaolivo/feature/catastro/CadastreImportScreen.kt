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
    CadastreImportScreen(state, preselectedFarmId, model::search, model::import)
}

@Composable
fun CadastreImportScreen(
    state: CadastreImportState,
    preselectedFarmId: UUID?,
    onSearch: (String) -> Unit,
    onImport: (UUID?, String) -> Unit,
) {
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
                        Text("Parcela encontrada", style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
                        Text("Referencia ${candidate.reference}", style = MaterialTheme.typography.bodyLarge)
                        candidate.areaM2?.let {
                            Text("Superficie catastral: ${formatHectares(it)} ha", style = MaterialTheme.typography.bodyLarge)
                        }
                        CandidateGeometryPreview(candidate, Modifier.fillMaxWidth().height(180.dp))
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
                    enabled = !state.saving && !state.searching && state.farms.isNotEmpty(),
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
    val lonSpan = (maxLon - minLon).takeIf { it > 0 } ?: 1.0
    val latSpan = (maxLat - minLat).takeIf { it > 0 } ?: 1.0
    Canvas(modifier.testTag("catastro-geometry-preview")) {
        val scale = minOf(size.width.toDouble() * 0.84 / lonSpan, size.height.toDouble() * 0.84 / latSpan).toFloat()
        val usedWidth = (lonSpan * scale).toFloat()
        val usedHeight = (latSpan * scale).toFloat()
        val left = (size.width - usedWidth) / 2
        val top = (size.height - usedHeight) / 2
        candidate.polygons.forEach { polygon ->
            polygon.forEachIndexed { ringIndex, ring ->
                val path = Path()
                ring.forEachIndexed { index, point ->
                    val x = left + ((point.first - minLon) * scale).toFloat()
                    val y = top + usedHeight - ((point.second - minLat) * scale).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                if (ringIndex == 0) drawPath(path, MoOlivePrimary.copy(alpha = 0.20f))
                drawPath(path, MoOliveDark, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}

private fun formatHectares(areaM2: Double): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).apply { maximumFractionDigits = 2 }
        .format(areaM2 / 10_000)
