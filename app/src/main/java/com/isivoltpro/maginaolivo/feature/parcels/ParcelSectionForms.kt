package com.isivoltpro.maginaolivo.feature.parcels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** One part of the parcel, edited alone: a form never touches fields it does not show. */
internal enum class ParcelForm { CROP, IRRIGATION, GENERAL }

private val SPANISH = Locale.forLanguageTag("es-ES")

/** Asks for the keyboard as soon as a field inside a sheet takes focus. */
@Composable
internal fun Modifier.imeOnFocus(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    return onFocusChanged { if (it.isFocused) keyboard?.show() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParcelFormSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            content = content,
        )
    }
}

@Composable
private fun FormButtons(saving: Boolean, onSave: () -> Unit, onCancel: () -> Unit, tag: String) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    MoPrimaryButton("Guardar", { focus.clearFocus(force = true); keyboard?.hide(); onSave() },
        Modifier.fillMaxWidth().testTag("$tag-save"), enabled = !saving)
    MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth().testTag("$tag-cancel"), enabled = !saving)
    Spacer(Modifier.height(MoSpacing.lg))
}

@Composable
private fun FormTitle(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, color = MoColors.current.primaryText)

@Composable
internal fun ParcelCropForm(
    parcel: Parcel,
    saving: Boolean,
    oliveError: String?,
    onSave: (olives: String, variety: String) -> Unit,
    onCancel: () -> Unit,
) {
    var olives by rememberSaveable(parcel.id) { mutableStateOf(parcel.agronomy.oliveTreeCount?.toString().orEmpty()) }
    var variety by rememberSaveable(parcel.id) { mutableStateOf(parcel.agronomy.variety.orEmpty()) }
    Column(Modifier.testTag("form-crop"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        FormTitle("Editar cultivo")
        MoTextField(olives, { olives = it.filter(Char::isDigit).take(7) }, "Número de olivos", Modifier.fillMaxWidth().imeOnFocus().testTag("form-crop-trees"),
            isError = oliveError != null, supportingText = oliveError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        MoTextField(variety, { variety = it.take(80) }, "Variedad principal", Modifier.fillMaxWidth().imeOnFocus().testTag("form-crop-variety"))
        FormButtons(saving, { onSave(olives, variety) }, onCancel, "form-crop")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ParcelIrrigationForm(
    parcel: Parcel,
    saving: Boolean,
    onSave: (IrrigationSystem?, network: String, sector: String, Set<DayOfWeek>) -> Unit,
    onCancel: () -> Unit,
) {
    val current = parcel.agronomy
    var systemName by rememberSaveable(parcel.id) { mutableStateOf(current.irrigationSystem?.name) }
    var network by rememberSaveable(parcel.id) { mutableStateOf(current.irrigationNetwork.orEmpty()) }
    var sector by rememberSaveable(parcel.id) { mutableStateOf(current.irrigationSector.orEmpty()) }
    var days by rememberSaveable(parcel.id) { mutableStateOf(current.irrigationDays.map { it.name }) }
    val system = systemName?.let(IrrigationSystem::valueOf)
    Column(Modifier.testTag("form-irrigation"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        FormTitle("Configurar riego")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            IrrigationSystem.entries.forEach { option ->
                FilterChip(
                    selected = system == option,
                    // Tapping again clears it: "not told" is different from "secano".
                    onClick = { systemName = option.name.takeIf { it != systemName } },
                    label = { Text(option.label()) },
                    modifier = Modifier.testTag("form-irrigation-${option.name.lowercase()}"),
                )
            }
        }
        IrrigationSystemDiagram(system)
        if (system != null && system != IrrigationSystem.DRYLAND) {
            MoTextField(network, { network = it.take(80) }, "Comunidad de regantes o red", Modifier.fillMaxWidth().imeOnFocus().testTag("form-irrigation-network"))
            MoTextField(sector, { sector = it.take(80) }, "Sector", Modifier.fillMaxWidth().imeOnFocus().testTag("form-irrigation-sector"))
            Text("Días habituales", style = MaterialTheme.typography.labelLarge, color = MoSurfaceTokens.secondaryText)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                DayOfWeek.entries.forEach { day ->
                    val selected = day.name in days
                    FilterChip(selected = selected, onClick = { days = if (selected) days - day.name else days + day.name },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, SPANISH).replaceFirstChar { it.titlecase(SPANISH) }) })
                }
            }
        }
        // Secano hides these fields but does not erase what was written before.
        FormButtons(saving, { onSave(system, network, sector, days.map(DayOfWeek::valueOf).toSet()) }, onCancel, "form-irrigation")
    }
}

/** Identifies the system with a simple drawing of how the water reaches the tree. */
@Composable
internal fun IrrigationSystemDiagram(system: IrrigationSystem?) {
    val description = when (system) {
        IrrigationSystem.DRIP -> "Esquema de goteo: tubería junto a los olivos con un gotero en cada uno"
        IrrigationSystem.SPRINKLER -> "Esquema de aspersión: un aspersor reparte agua en círculo"
        else -> return
    }
    val line = MoColors.current.primaryText
    val water = MoColors.current.actionText
    Canvas(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = description }.testTag("irrigation-diagram")) {
        val ground = size.height * 0.82f
        drawLine(line, Offset(0f, ground), Offset(size.width, ground), strokeWidth = 2.dp.toPx())
        val stroke = Stroke(width = 2.dp.toPx())
        if (system == IrrigationSystem.DRIP) {
            val pipe = ground - 6.dp.toPx()
            drawLine(water, Offset(0f, pipe), Offset(size.width, pipe), strokeWidth = 3.dp.toPx())
            listOf(0.2f, 0.5f, 0.8f).forEach { x ->
                val cx = size.width * x
                drawLine(line, Offset(cx, pipe), Offset(cx, size.height * 0.34f), strokeWidth = 3.dp.toPx())
                drawCircle(line, 14.dp.toPx(), Offset(cx, size.height * 0.26f), style = stroke)
                drawCircle(water, 3.dp.toPx(), Offset(cx + 12.dp.toPx(), pipe + 7.dp.toPx()))
            }
        } else {
            val head = Offset(size.width / 2, size.height * 0.5f)
            drawLine(line, Offset(head.x, ground), head, strokeWidth = 3.dp.toPx())
            val radius = size.height * 0.42f
            drawArc(water, 200f, 140f, false, Offset(head.x - radius * 2, head.y - radius), Size(radius * 4, radius * 2), style = stroke)
            drawArc(water, 210f, 120f, false, Offset(head.x - radius * 3, head.y - radius * 0.7f), Size(radius * 6, radius * 2), style = stroke)
        }
    }
}

@Composable
internal fun ParcelGeneralForm(
    parcel: Parcel,
    saving: Boolean,
    nameError: String?,
    areaError: String?,
    onSave: (name: String, areaHectares: String, municipality: String, province: String, notes: String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable(parcel.id) { mutableStateOf(parcel.displayName) }
    var area by rememberSaveable(parcel.id) { mutableStateOf(parcel.managedAreaM2?.let { (it / 10_000).toBigDecimal().stripTrailingZeros().toPlainString().replace('.', ',') }.orEmpty()) }
    var municipality by rememberSaveable(parcel.id) { mutableStateOf(parcel.municipality.orEmpty()) }
    var province by rememberSaveable(parcel.id) { mutableStateOf(parcel.province.orEmpty()) }
    var notes by rememberSaveable(parcel.id) { mutableStateOf(parcel.notes.orEmpty()) }
    val manual = parcel.source == ParcelSource.MANUAL
    Column(Modifier.testTag("form-general"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        FormTitle("Datos generales")
        MoTextField(name, { name = it }, "Nombre de la parcela", Modifier.fillMaxWidth().imeOnFocus().testTag("form-general-name"),
            isError = nameError != null, supportingText = nameError)
        MoTextField(area, { area = it.take(12) }, "Superficie trabajada (ha)", Modifier.fillMaxWidth().imeOnFocus().testTag("form-general-area"),
            isError = areaError != null, supportingText = areaError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (manual) {
            MoTextField(municipality, { municipality = it }, "Municipio", Modifier.fillMaxWidth().imeOnFocus().testTag("form-general-municipality"))
            MoTextField(province, { province = it }, "Provincia", Modifier.fillMaxWidth().imeOnFocus().testTag("form-general-province"))
        }
        MoTextField(notes, { notes = it }, "Observaciones", Modifier.fillMaxWidth().imeOnFocus().testTag("form-general-notes"), singleLine = false)
        FormButtons(saving, { onSave(name, area, municipality, province, notes) }, onCancel, "form-general")
    }
}
