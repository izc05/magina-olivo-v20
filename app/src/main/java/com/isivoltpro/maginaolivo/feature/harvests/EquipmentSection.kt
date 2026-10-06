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
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentPriceSnapshot
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRules
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentSummary
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
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
import java.time.LocalDate
import java.util.UUID

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
internal fun JornadaEquipment(
    lines: List<EquipmentLine>, editable: Boolean, error: String?, onEdit: () -> Unit,
    loaded: Boolean = true, readFailed: Boolean = false,
) {
    MoSectionHeader("Uso de maquinaria")
    if (readFailed || !loaded) {
        Text(
            if (readFailed) "No pudimos leer la maquinaria de este día." else "Cargando maquinaria…",
            style = MaterialTheme.typography.bodyMedium,
            color = if (readFailed) MaterialTheme.colorScheme.error else MoTextSecondary,
            modifier = Modifier.testTag(if (readFailed) "jornada-equipment-read-error" else "jornada-equipment-loading"),
        )
        return
    }
    val summary = EquipmentSummary.of(lines)
    if (summary.isEmpty) {
        Text("Sin maquinaria anotada.", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary, modifier = Modifier.testTag("jornada-no-equipment"))
    } else {
        Text(summary.label(), style = MaterialTheme.typography.bodyLarge, color = MoOliveDark, modifier = Modifier.testTag("jornada-equipment-summary"))
        lines.forEach { line ->
            val total = line.appliedPrice?.let { runCatching { Math.multiplyExact(line.quantity.toLong(), it.unitPriceMinor) }.getOrNull() }
            val subtitle = if (total == null) "Coste sin confirmar" else
                "${Money.format(line.appliedPrice.unitPriceMinor, line.appliedPrice.currency)} por uso · ${Money.format(total, line.appliedPrice.currency)} total"
            MoCompactListItem(title = line.text(), subtitle = subtitle, icon = line.type.icon(), iconTint = com.isivoltpro.maginaolivo.ui.theme.MoEarthText, iconContainer = com.isivoltpro.maginaolivo.ui.theme.MoEarthTint, modifier = Modifier.testTag("jornada-equipment-line"))
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
    rates: RecollectionRates?,
    currency: String,
    currencyError: String?,
    priceDate: LocalDate,
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
    var prices by remember(current) { mutableStateOf(current.associate { row ->
        EquipmentRules.key(EquipmentDraftLine(row.type, row.quantity, row.label, row.machineId)) to
            Money.editable(row.appliedPrice?.unitPriceMinor, row.appliedPrice?.currency ?: currency)
    }) }
    fun priceText(line: EquipmentDraftLine): String {
        val key = EquipmentRules.key(line)
        return prices[key] ?: if (current.none { EquipmentRules.key(EquipmentDraftLine(it.type, it.quantity, it.label, it.machineId)) == key } && rates?.currency == currency)
            Money.editable(rates.equipmentDayMinor[line.type], currency) else ""
    }
    // #446: a machine archived since still belongs to the days it worked: its line is part of the
    // form with the type, name, quantity and price it was recorded with.
    val historicalMachines = remember(current, machines) {
        current.filter { line -> line.machineId != null && machines.none { it.id == line.machineId } }
    }
    fun machineLine(id: UUID): EquipmentDraftLine? =
        machines.firstOrNull { it.id == id }?.let { EquipmentDraftLine(it.category.toEquipment(), 1, machineId = it.id) }
            ?: historicalMachines.firstOrNull { it.machineId == id }?.let { EquipmentDraftLine(it.type, it.quantity, it.label, machineId = id) }
    val selected = counts.map { (type, n) -> EquipmentDraftLine(type, n) } +
        others.map { (name, n) -> EquipmentDraftLine(EquipmentType.OTHER, n, label = name) } +
        chosenMachines.mapNotNull(::machineLine)
    val malformed = selected.any { line -> priceText(line).isNotBlank() && Money.parseMinor(priceText(line), currency) == null }
    val totalOverflow = runCatching {
        selected.fold(0L) { total, line ->
            val unit = Money.parseMinor(priceText(line), currency) ?: 0L
            Math.addExact(total, Math.multiplyExact(line.quantity.toLong(), unit))
        }
    }.isFailure
    val lines = selected.map { line ->
        val price = Money.parseMinor(priceText(line), currency)
        val existing = current.firstOrNull { EquipmentRules.key(EquipmentDraftLine(it.type, it.quantity, it.label, it.machineId)) == EquipmentRules.key(line) }
        line.copy(appliedPrice = price?.takeIf { existing?.appliedPrice?.unitPriceMinor != it || existing.appliedPrice.currency != currency }
            ?.let { EquipmentPriceSnapshot(it, currency, priceDate) },
            captureUsualPriceWhenMissing = priceText(line).isNotBlank())
    }
    val invalid = EquipmentRules.validate(lines)
    val legacyMissing = selected.any { line ->
        current.any { EquipmentRules.key(EquipmentDraftLine(it.type, it.quantity, it.label, it.machineId)) == EquipmentRules.key(line) && it.appliedPrice == null } &&
            priceText(line).isBlank()
    }
    val pricedAppend = lines.any { line ->
        current.none { EquipmentRules.key(EquipmentDraftLine(it.type, it.quantity, it.label, it.machineId)) == EquipmentRules.key(line) } && line.appliedPrice != null
    }
    val clearedConfirmedPrice = selected.any { line ->
        current.any { EquipmentRules.key(EquipmentDraftLine(it.type, it.quantity, it.label, it.machineId)) == EquipmentRules.key(line) && it.appliedPrice != null } &&
            priceText(line).isBlank()
    }
    val pricingError = when {
        currencyError != null -> currencyError
        malformed -> "El precio no es válido o es demasiado grande. Revisa el importe."
        totalOverflow -> "El total es demasiado grande. Reduce el precio o la cantidad."
        invalid?.code == "overflow" -> "El total es demasiado grande. Reduce el precio o la cantidad."
        clearedConfirmedPrice -> "El coste ya confirmado necesita un precio. Introduce el importe o cancela el cambio."
        legacyMissing && pricedAppend -> "Confirma primero los precios que faltan en la maquinaria histórica de este día."
        else -> null
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen).testTag("equipment-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Uso de maquinaria del día", style = MaterialTheme.typography.headlineSmall, color = com.isivoltpro.maginaolivo.ui.theme.MoEarthText)
        Text(
            "Indica cuántas se usaron. No hace falta registrar cada máquina.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoTextSecondary,
        )
        EquipmentType.entries.filter { it != EquipmentType.OTHER }.forEach { type ->
            Stepper(type.title(), counts[type] ?: 0, "equipment-${type.name}") { value ->
                counts = if (value == 0) counts - type else counts + (type to value)
            }
            counts[type]?.let { quantity ->
                EquipmentPriceField(type.title(), quantity, currency, priceText(EquipmentDraftLine(type, quantity)), "equipment-${type.name}") {
                    prices = prices + ("type:${type.name}" to it)
                }
            }
        }
        others.forEachIndexed { index, (name, quantity) ->
            Stepper(name, quantity, "equipment-other") { value ->
                others = if (value == 0) others.filterIndexed { i, _ -> i != index } else others.mapIndexed { i, item -> if (i == index) item.first to value else item }
            }
            EquipmentPriceField(name, quantity, currency, priceText(EquipmentDraftLine(EquipmentType.OTHER, quantity, name)), "equipment-other-$index") {
                prices = prices + ("other:${name.trim().lowercase()}" to it)
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
        if (machines.isNotEmpty() || historicalMachines.isNotEmpty()) {
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
                historicalMachines.forEach { line ->
                    val id = line.machineId!!
                    FilterChip(
                        selected = id in chosenMachines,
                        onClick = { chosenMachines = if (id in chosenMachines) chosenMachines - id else chosenMachines + id },
                        label = { Text("${line.label ?: "Máquina"} · Archivada") },
                        modifier = Modifier.testTag("equipment-machine-archived"),
                    )
                }
            }
            chosenMachines.forEach { id ->
                val line = machineLine(id) ?: return@forEach
                val name = machines.firstOrNull { it.id == id }?.name ?: line.label ?: "Máquina"
                EquipmentPriceField(name, line.quantity, currency, priceText(line), "equipment-machine-$id") {
                    prices = prices + ("machine:$id" to it)
                }
            }
        }
        pricingError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("equipment-price-error")) }
        if (selected.any { priceText(it).isBlank() }) Text("Sin precio confirmado: el coste de esas máquinas queda pendiente.",
            style = MaterialTheme.typography.bodySmall, color = MoTextSecondary)
        MoPrimaryButton(
            "Guardar maquinaria",
            { onSave(lines) },
            Modifier.fillMaxWidth().testTag("equipment-save"),
            enabled = !isSaving && invalid == null && pricingError == null,
        )
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun EquipmentPriceField(name: String, quantity: Int, currency: String, text: String, tag: String, onChange: (String) -> Unit) {
    MoTextField(text, onChange, "$name · coste unitario del día/uso ($currency)",
        modifier = Modifier.fillMaxWidth().testTag("$tag-price"))
    val minor = Money.parseMinor(text, currency)
    val total = minor?.let { runCatching { Math.multiplyExact(quantity.toLong(), it) }.getOrNull() }
    Text(if (total == null) "Total de esta línea: pendiente" else "Total de esta línea: ${Money.format(total, currency)}",
        style = MaterialTheme.typography.bodySmall, color = MoTextSecondary,
        modifier = Modifier.testTag("$tag-total"))
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

/**
 * A registered Machine's category as a recollection equipment type. #521: only unambiguous
 * categories map to a specific type; «Recolección» covers shakers, umbrellas and combs alike, so it
 * stays OTHER with the machine's own name and never receives the usual Vibradora rate.
 */
internal fun com.isivoltpro.maginaolivo.domain.machinery.MachineCategory.toEquipment(): EquipmentType = when (this) {
    com.isivoltpro.maginaolivo.domain.machinery.MachineCategory.TRACTOR -> EquipmentType.TRACTOR
    com.isivoltpro.maginaolivo.domain.machinery.MachineCategory.TRAILER -> EquipmentType.TRAILER
    else -> EquipmentType.OTHER
}
