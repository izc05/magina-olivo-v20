package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRules
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentSummary
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.machinery.Machine
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary

internal fun EquipmentType.title(): String = when (this) {
    EquipmentType.TRACTOR -> "Tractor"
    EquipmentType.SHAKER -> "Vibradora"
    EquipmentType.COMB -> "Peine eléctrico"
    EquipmentType.TRAILER -> "Remolque"
    EquipmentType.BLOWER -> "Sopladora"
    EquipmentType.OTHER -> "Otra"
}

internal fun EquipmentType.icon() = when (this) {
    EquipmentType.TRACTOR, EquipmentType.TRAILER -> MoIcons.Tractor
    EquipmentType.COMB -> MoIcons.Shears
    EquipmentType.BLOWER -> MoIcons.Spray
    else -> MoIcons.Wrench
}

/** Phase 19E — what equipment the Jornada used, by kind and quantity. */
@Composable
internal fun JornadaEquipment(lines: List<EquipmentLine>, editable: Boolean, error: String?, onEdit: () -> Unit) {
    MoSectionHeader("Maquinaria")
    val summary = EquipmentSummary.of(lines)
    if (summary.isEmpty) {
        Text("Sin maquinaria anotada.", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary, modifier = Modifier.testTag("jornada-no-equipment"))
    } else {
        Text(summary.label(), style = MaterialTheme.typography.bodyLarge, color = MoOliveDark, modifier = Modifier.testTag("jornada-equipment-summary"))
        lines.filter { it.machineId != null }.forEach { line ->
            MoCompactListItem(title = line.text(), subtitle = "Máquina registrada", icon = line.type.icon(), modifier = Modifier.testTag("jornada-equipment-machine"))
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (editable) {
        MoSecondaryButton(
            if (summary.isEmpty) "Anotar maquinaria" else "Cambiar maquinaria",
            onEdit,
            Modifier.fillMaxWidth().testTag("jornada-edit-equipment"),
        )
    }
}

/**
 * Phase 19E — the equipment sheet: `Vibradora  −  2  +` for each preset, "Otra" with its
 * name, and registered machines for their own history. Saved all at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EquipmentSheet(
    current: List<EquipmentLine>,
    machines: List<Machine>,
    isSaving: Boolean,
    onSave: (List<EquipmentDraftLine>) -> Unit,
    onCancel: () -> Unit,
) {
    val initialCounts = remember(current) {
        current.filter { it.machineId == null && it.type != EquipmentType.OTHER }.associate { it.type to it.quantity }
    }
    var counts by remember(current) { mutableStateOf(initialCounts) }
    var others by remember(current) {
        mutableStateOf(current.filter { it.machineId == null && it.type == EquipmentType.OTHER }.map { (it.label ?: "") to it.quantity })
    }
    var chosenMachines by remember(current) { mutableStateOf(current.mapNotNull { it.machineId }.toSet()) }
    var otherName by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen).testTag("equipment-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Maquinaria de la jornada", style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        Text(
            "Indica cuántas se usaron. No hace falta registrar cada máquina.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoTextSecondary,
        )
        EquipmentType.entries.filter { it != EquipmentType.OTHER }.forEach { type ->
            Stepper(type.title(), counts[type] ?: 0, "equipment-${type.name}") { value ->
                counts = if (value == 0) counts - type else counts + (type to value)
            }
        }
        others.forEachIndexed { index, (name, quantity) ->
            Stepper(name, quantity, "equipment-other") { value ->
                others = if (value == 0) others.filterIndexed { i, _ -> i != index } else others.mapIndexed { i, item -> if (i == index) item.first to value else item }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            MoTextField(otherName, { otherName = it.take(40) }, "Otra (nombre)", modifier = Modifier.weight(1f).testTag("equipment-other-name"))
            MoTertiaryButton(
                "Añadir",
                {
                    val name = otherName.trim()
                    if (others.none { it.first.equals(name, ignoreCase = true) }) others = others + (name to 1)
                    otherName = ""
                },
                Modifier.testTag("equipment-add-other"),
                enabled = otherName.isNotBlank(),
            )
        }
        if (machines.isNotEmpty()) {
            Text("Máquinas registradas (opcional, para su historial)", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                machines.forEach { machine ->
                    FilterChip(
                        selected = machine.id in chosenMachines,
                        onClick = { chosenMachines = if (machine.id in chosenMachines) chosenMachines - machine.id else chosenMachines + machine.id },
                        label = { Text(machine.name) },
                        modifier = Modifier.testTag("equipment-machine"),
                    )
                }
            }
        }
        val lines = counts.map { (type, n) -> EquipmentDraftLine(type, n) } +
            others.map { (name, n) -> EquipmentDraftLine(EquipmentType.OTHER, n, label = name) } +
            chosenMachines.mapNotNull { id -> machines.firstOrNull { it.id == id } }.map { EquipmentDraftLine(it.category.toEquipment(), 1, machineId = it.id) }
        MoPrimaryButton(
            "Guardar maquinaria",
            { onSave(lines) },
            Modifier.fillMaxWidth().testTag("equipment-save"),
            enabled = !isSaving && EquipmentRules.validate(lines) == null,
        )
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun Stepper(title: String, value: Int, tag: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton({ onChange((value - 1).coerceAtLeast(0)) }, enabled = value > 0, modifier = Modifier.testTag("$tag-minus")) { Text("−") }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp).testTag("$tag-value"),
        )
        OutlinedButton({ onChange((value + 1).coerceAtMost(EquipmentRules.MAX_QUANTITY)) }, modifier = Modifier.testTag("$tag-plus")) { Text("+") }
    }
}

/** A registered Machine's category as a recollection equipment type. */
internal fun com.isivoltpro.maginaolivo.domain.machinery.MachineCategory.toEquipment(): EquipmentType = when (this) {
    com.isivoltpro.maginaolivo.domain.machinery.MachineCategory.TRACTOR -> EquipmentType.TRACTOR
    com.isivoltpro.maginaolivo.domain.machinery.MachineCategory.TRAILER -> EquipmentType.TRAILER
    com.isivoltpro.maginaolivo.domain.machinery.MachineCategory.HARVEST -> EquipmentType.SHAKER
    else -> EquipmentType.OTHER
}
