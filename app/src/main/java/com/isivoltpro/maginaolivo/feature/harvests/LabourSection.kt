package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.labour.Worker
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
import java.util.UUID

internal fun LabourUnit.label(): String = when (this) {
    LabourUnit.FULL_DAY -> "Jornada completa"
    LabourUnit.HALF_DAY -> "Media jornada"
    LabourUnit.HOURS -> "Horas"
}

internal fun LabourEntry.label(): String = when (unit) {
    LabourUnit.HOURS -> "${LabourSummary.hours(minutes ?: 0)} por persona"
    else -> unit.label()
}

/** "6", "6,5" or "6:30" hours → minutes; null when it is not an amount of hours. */
internal fun parseHours(text: String): Int? {
    val cleaned = text.trim().replace(',', '.')
    if (cleaned.isEmpty()) return null
    val parts = cleaned.split(':')
    val minutes = when (parts.size) {
        1 -> cleaned.toDoubleOrNull()?.let { Math.round(it * 60).toInt() }
        2 -> {
            val h = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (h == null || m == null || m !in 0..59) null else h * 60 + m
        }
        else -> null
    }
    return minutes?.takeIf { it > 0 }
}

/**
 * Phase 19D — the Jornada's jornales: who worked, and how (whole day, half day, hours).
 * Attendance only; a labour cost is recorded as an Expense.
 */
@Composable
internal fun JornadaLabour(
    labour: List<LabourEntry>,
    editable: Boolean,
    message: String?,
    error: String?,
    onRegister: () -> Unit,
    onRemove: (UUID) -> Unit,
) {
    MoSectionHeader("Jornales")
    val summary = LabourSummary.of(labour)
    if (summary.isEmpty) {
        Text(
            "Sin jornales anotados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoTextSecondary,
            modifier = Modifier.testTag("jornada-no-labour"),
        )
    } else {
        Text(
            "${if (summary.people == 1) "1 persona" else "${summary.people} personas"} · ${summary.label()}",
            style = MaterialTheme.typography.bodyLarge,
            color = MoOliveDark,
            modifier = Modifier.testTag("jornada-labour-summary"),
        )
        labour.forEach { entry ->
            MoCompactListItem(
                title = entry.workerName ?: if (entry.quantity == 1) "1 persona" else "${entry.quantity} personas",
                subtitle = entry.label(),
                icon = if (entry.workerId != null) MoIcons.Person else MoIcons.People,
                modifier = Modifier.testTag("jornada-labour"),
                trailing = if (editable) {
                    { MoTertiaryButton("Quitar", { onRemove(entry.id) }, Modifier.testTag("jornada-labour-remove")) }
                } else {
                    null
                },
            )
        }
    }
    message?.let { Text(it, color = MoTextSecondary, modifier = Modifier.testTag("jornada-labour-message")) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("jornada-labour-error")) }
    if (editable) {
        MoSecondaryButton("Registrar jornales", onRegister, Modifier.fillMaxWidth().testTag("jornada-register-labour"))
    }
}

/**
 * Phase 19D — register jornales in one save: select people (or repeat yesterday's crew), or
 * just say how many. Whole day / half day / hours apply to everyone saved together.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabourSheet(
    workers: List<Worker>,
    alreadyRecorded: Set<UUID>,
    previousCrew: List<UUID>,
    isSaving: Boolean,
    error: String?,
    onSaveCrew: (List<UUID>, LabourUnit, Int?) -> Unit,
    onSaveCount: (Int, LabourUnit, Int?) -> Unit,
    onAddWorker: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var byPeople by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf<UUID>()) }
    var unit by rememberSaveable { mutableStateOf(LabourUnit.FULL_DAY) }
    var hours by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableStateOf("") }
    var newName by rememberSaveable { mutableStateOf("") }
    var pendingName by rememberSaveable { mutableStateOf<String?>(null) }

    // A person just added is selected as soon as it is saved.
    LaunchedEffect(workers, pendingName) {
        val name = pendingName ?: return@LaunchedEffect
        workers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { worker ->
            if (worker.id !in alreadyRecorded) selected = selected + worker.id
            pendingName = null
        }
    }
    val minutes = if (unit == LabourUnit.HOURS) parseHours(hours) else null
    val hoursMissing = unit == LabourUnit.HOURS && minutes == null
    val repeatable = previousCrew.filter { it !in alreadyRecorded && workers.any { worker -> worker.id == it } }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen)
            .testTag("labour-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Registrar jornales", style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            FilterChip(byPeople, { byPeople = true }, { Text("Por personas") }, Modifier.testTag("labour-mode-people"))
            FilterChip(!byPeople, { byPeople = false }, { Text("Solo número") }, Modifier.testTag("labour-mode-count"))
        }
        Text("¿Cuánto trabajó cada uno?", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            LabourUnit.entries.forEach { option ->
                FilterChip(unit == option, { unit = option }, { Text(option.label()) }, Modifier.testTag("labour-unit-${option.name}"))
            }
        }
        if (unit == LabourUnit.HOURS) {
            MoTextField(
                hours, { hours = it }, "Horas por persona",
                isError = hours.isNotBlank() && minutes == null,
                supportingText = if (hours.isNotBlank() && minutes == null) "Escribe las horas como 6 o 6,5" else null,
                modifier = Modifier.fillMaxWidth().testTag("labour-hours"),
            )
        }

        if (byPeople) {
            if (repeatable.isNotEmpty()) {
                MoSecondaryButton(
                    "Repetir cuadrilla anterior (${repeatable.size})",
                    { selected = selected + repeatable },
                    Modifier.fillMaxWidth().testTag("labour-repeat-crew"),
                )
            }
            if (workers.isEmpty()) {
                Text("Aún no hay personas. Añade la primera con su nombre o apodo.", color = MoTextSecondary)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                workers.forEach { worker ->
                    val done = worker.id in alreadyRecorded
                    FilterChip(
                        selected = worker.id in selected,
                        onClick = { selected = if (worker.id in selected) selected - worker.id else selected + worker.id },
                        label = { Text(if (done) "${worker.name} ✓" else worker.name) },
                        enabled = !done,
                        modifier = Modifier.testTag("labour-worker"),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                MoTextField(newName, { newName = it }, "+ Persona", modifier = Modifier.weight(1f).testTag("labour-new-name"))
                MoTertiaryButton(
                    "Añadir",
                    {
                        pendingName = newName.trim()
                        onAddWorker(newName)
                        newName = ""
                    },
                    Modifier.testTag("labour-add-worker"),
                    enabled = newName.isNotBlank() && !isSaving,
                )
            }
            Text(
                if (selected.size == 1) "1 seleccionada" else "${selected.size} seleccionadas",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
                modifier = Modifier.testTag("labour-selected-count"),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            MoPrimaryButton(
                if (selected.size == 1) "Guardar 1 jornal" else "Guardar ${selected.size} jornales",
                { onSaveCrew(selected.toList(), unit, minutes) },
                Modifier.fillMaxWidth().testTag("labour-save"),
                enabled = selected.isNotEmpty() && !hoursMissing && !isSaving,
            )
        } else {
            val n = count.trim().toIntOrNull()
            MoTextField(
                count, { count = it.filter(Char::isDigit).take(3) }, "Número de personas",
                modifier = Modifier.fillMaxWidth().testTag("labour-count"),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            MoPrimaryButton(
                if (n == 1) "Guardar 1 jornal" else "Guardar ${n ?: 0} jornales",
                { onSaveCount(n!!, unit, minutes) },
                Modifier.fillMaxWidth().testTag("labour-save"),
                enabled = (n ?: 0) > 0 && !hoursMissing && !isSaving,
            )
        }
        Text(
            "Aquí se anota quién trabajó. Si pagas jornales, anótalo como gasto de recolección: el dinero se cuenta solo allí.",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
        )
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}
