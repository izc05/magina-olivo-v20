package com.isivoltpro.maginaolivo.feature.sigpac

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoLabeledValue
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionCard
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")
private val CONSULTED: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", SPANISH)

internal fun sigpacNumber(value: Double, decimals: Int = 4): String =
    NumberFormat.getNumberInstance(SPANISH).apply { maximumFractionDigits = decimals }.format(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigpacCard(state: SigpacUiState, hasReference: Boolean, onLoad: () -> Unit, modifier: Modifier = Modifier) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    MoSectionCard(title = "SIGPAC — Datos oficiales", icon = MoIcons.Map, modifier = modifier.testTag("parcel-sigpac")) {
        @Composable
        fun line(text: String, tag: String? = null, error: Boolean = false) {
            Text(text, style = MaterialTheme.typography.bodyMedium,
                color = if (error) MoColors.current.errorText else MoSurfaceTokens.secondaryText,
                modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
        }
        @Composable
        fun available(data: SigpacUiState.Available) {
            val assessment = data.assessment
            MoLabeledValue("Recintos", assessment.matched.size.toString(), Modifier.testTag("parcel-sigpac-count"))
            MoLabeledValue("Superficie de los recintos · Según SIGPAC",
                assessment.totalAreaHa?.let { "${sigpacNumber(it)} ha" } ?: "No disponible", Modifier.testTag("parcel-sigpac-area"))
            MoLabeledValue("Usos SIGPAC", assessment.useCodes.joinToString(", ").ifEmpty { "Sin dato" })
            MoLabeledValue("Consultado", data.consultedAt.atZone(ZoneId.systemDefault()).format(CONSULTED))
            line("Campaña no indicada por el servicio.")
            if (assessment.unverified) line("Sin contorno guardado con el que comprobar que estos recintos son de la parcela.", "parcel-sigpac-unverified")
            if (assessment.outside.isNotEmpty()) line("${assessment.outside.size} recintos no coinciden con el contorno guardado y no se cuentan.", "parcel-sigpac-outside")
            line("El uso SIGPAC no acredita el cultivo real ni la titularidad.")
            Text("Fuente: SIGPAC · FEGA · CC BY 4.0", style = MaterialTheme.typography.labelSmall, color = MoSurfaceTokens.secondaryText)
            MoSecondaryButton("Ver recintos", { sheet = true }, Modifier.fillMaxWidth().testTag("parcel-sigpac-open"), assessment.matched.isNotEmpty())
        }
        when (state) {
            SigpacUiState.NotRequested -> if (hasReference) {
                MoTertiaryButton("Consultar SIGPAC", onLoad, Modifier.fillMaxWidth().testTag("parcel-sigpac-load"))
            } else line("Añade la referencia catastral para consultar sus recintos.", "parcel-sigpac-no-reference")
            SigpacUiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                CircularProgressIndicator(Modifier.padding(2.dp).testTag("parcel-sigpac-loading"))
                line("Consultando SIGPAC…")
            }
            is SigpacUiState.Available -> available(state)
            SigpacUiState.NoRecintos -> {
                line("SIGPAC no ha localizado recintos para esta parcela.", "parcel-sigpac-empty")
                MoTertiaryButton("Volver a consultar", onLoad, Modifier.fillMaxWidth())
            }
            SigpacUiState.Unsupported -> line("SIGPAC solo se puede consultar con referencias rústicas.", "parcel-sigpac-unsupported")
            is SigpacUiState.Unavailable -> {
                line(when (state.error) {
                    SigpacError.NETWORK -> "Sin conexión con SIGPAC. Tus datos siguen guardados."
                    SigpacError.SERVICE -> "SIGPAC no está disponible ahora. Tus datos siguen guardados."
                    SigpacError.RATE_LIMITED -> "SIGPAC limita las consultas. Inténtalo más tarde."
                    SigpacError.RESPONSE -> "SIGPAC ha enviado una respuesta que no se puede comprobar. No se muestra."
                }, "parcel-sigpac-error", error = true)
                state.previous?.let { available(it) }
                MoTertiaryButton("Reintentar", onLoad, Modifier.fillMaxWidth().testTag("parcel-sigpac-retry"))
            }
        }
    }
    val data = (state as? SigpacUiState.Available) ?: (state as? SigpacUiState.Unavailable)?.previous
    if (sheet && data != null) ModalBottomSheet(onDismissRequest = { sheet = false }) {
        SigpacRecintosSheet(data.assessment.matched)
    }
}

@Composable
private fun SigpacRecintosSheet(recintos: List<SigpacRecinto>) {
    var selected by remember { mutableStateOf(emptySet<Int>()) }
    var detail by remember { mutableStateOf<Int?>(null) }
    Column(
        Modifier.padding(horizontal = MoSpacing.screen).padding(bottom = MoSpacing.lg).verticalScroll(rememberScrollState()).testTag("parcel-sigpac-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Text("Recintos SIGPAC", style = MaterialTheme.typography.titleLarge, color = MoColors.current.primaryText)
        if (selected.isNotEmpty()) {
            val sum = sigpacAreaSum(recintos.filter { it.recinto in selected })
            Text("Seleccionados: ${selected.size} · ${sum?.let { "${sigpacNumber(it)} ha" } ?: "superficie no disponible"}",
                style = MaterialTheme.typography.titleSmall, color = MoColors.current.bodyText, modifier = Modifier.testTag("parcel-sigpac-selected"))
        }
        recintos.forEach { recinto ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = recinto.recinto in selected,
                    onCheckedChange = { selected = if (it) selected + recinto.recinto else selected - recinto.recinto },
                    modifier = Modifier.testTag("parcel-sigpac-check-${recinto.recinto}"),
                )
                Text(
                    "Recinto ${recinto.recinto} — ${recinto.useCode ?: "Sin uso"} — ${recinto.areaHa?.let { "${sigpacNumber(it)} ha" } ?: "Sin superficie"}",
                    Modifier.weight(1f).clickable(role = Role.Button) { detail = recinto.recinto.takeIf { it != detail } }
                        .padding(vertical = MoSpacing.sm).testTag("parcel-sigpac-recinto-${recinto.recinto}"),
                    style = MaterialTheme.typography.bodyLarge, color = MoColors.current.bodyText,
                )
            }
            if (detail == recinto.recinto) SigpacRecintoDetail(recinto)
        }
        Text("Fuente: SIGPAC · FEGA · CC BY 4.0", style = MaterialTheme.typography.labelSmall, color = MoSurfaceTokens.secondaryText)
    }
}

/** A value FEGA did not send reads «Sin dato»; a zero is shown as zero. */
@Composable
private fun SigpacRecintoDetail(recinto: SigpacRecinto) {
    Column(Modifier.padding(start = MoSpacing.lg).testTag("parcel-sigpac-detail"), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        MoLabeledValue("Pendiente media (valor de FEGA, sin unidad indicada)", recinto.slope?.let { sigpacNumber(it, 2) } ?: "Sin dato")
        MoLabeledValue("Coeficiente de regadío (valor de FEGA)", recinto.irrigationCoefficient?.let { sigpacNumber(it, 2) } ?: "Sin dato")
        MoLabeledValue("Admisibilidad en pastos", recinto.admissibilityPercent?.let { "${sigpacNumber(it, 2)} %" } ?: "Sin dato")
        MoLabeledValue("Incidencias", recinto.incidents ?: "Ninguna indicada")
        MoLabeledValue("Región", recinto.region ?: "Sin dato")
    }
}
