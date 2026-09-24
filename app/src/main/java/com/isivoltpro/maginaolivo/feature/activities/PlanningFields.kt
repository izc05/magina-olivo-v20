package com.isivoltpro.maginaolivo.feature.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.agenda.ActivityPlanning
import com.isivoltpro.maginaolivo.domain.agenda.Reminder
import com.isivoltpro.maginaolivo.domain.agenda.ReminderKind
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Phase 16: "Planificación (opcional)". Nothing here is required; a planned Activity is
 * still just the Activity, completed later on the same record.
 */
@Composable
internal fun PlanningFields(
    input: PlanningInput,
    error: PlanningInput.Result.Invalid?,
    onChange: (PlanningInput) -> Unit,
) {
    fun errorOf(field: String) = error?.takeIf { it.field == field }?.message
    MoSectionHeader("Planificación (opcional)")
    MoTextField(
        input.time, { onChange(input.copy(time = it)) }, "Hora prevista (8:30)",
        isError = errorOf(PlanningInput.TIME) != null, supportingText = errorOf(PlanningInput.TIME),
        modifier = Modifier.fillMaxWidth().testTag("planning-time"),
    )
    MoTextField(
        input.durationHours, { onChange(input.copy(durationHours = it)) }, "Duración prevista (horas)",
        isError = errorOf(PlanningInput.DURATION) != null, supportingText = errorOf(PlanningInput.DURATION),
        modifier = Modifier.fillMaxWidth().testTag("planning-duration"),
    )
    MoTextField(
        input.people, { onChange(input.copy(people = it)) }, "Personas previstas",
        isError = errorOf(PlanningInput.PEOPLE) != null, supportingText = errorOf(PlanningInput.PEOPLE),
        modifier = Modifier.fillMaxWidth().testTag("planning-people"),
    )
    MoTextField(
        input.crew, { onChange(input.copy(crew = it)) }, "Cuadrilla, empresa o proveedor",
        modifier = Modifier.fillMaxWidth().testTag("planning-crew"),
    )
    Text("Avisos en este teléfono", color = MoTextSecondary)
    ReminderChoice("La tarde anterior (19:00)", input.previousDay, "planning-reminder-previous-day") {
        onChange(input.copy(previousDay = it))
    }
    ReminderChoice("El mismo día (1 h antes, o a las 7:00)", input.sameDay, "planning-reminder-same-day") {
        onChange(input.copy(sameDay = it))
    }
    MoTextField(
        input.custom, { onChange(input.copy(custom = it)) }, "Otro aviso (AAAA-MM-DD HH:MM)",
        isError = errorOf(PlanningInput.CUSTOM) != null, supportingText = errorOf(PlanningInput.CUSTOM),
        modifier = Modifier.fillMaxWidth().testTag("planning-reminder-custom"),
    )
}

@Composable
private fun ReminderChoice(label: String, checked: Boolean, tag: String, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag(tag).clickable { onChecked(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onChecked)
        Text(label)
    }
}

/** What the detail shows of the planning; nothing when the work was never planned. */
@Composable
internal fun PlanningSummary(planning: ActivityPlanning?, reminders: List<Reminder>) {
    if (planning == null && reminders.isEmpty()) return
    MoSectionHeader("Planificación")
    planningLine(planning)?.let { Text(it, modifier = Modifier.testTag("activity-planning")) }
    reminders.forEach { reminder ->
        Text(
            reminderLabel(reminder),
            color = MoTextSecondary,
            modifier = Modifier.testTag("activity-reminder"),
        )
    }
}

internal fun planningLine(planning: ActivityPlanning?): String? {
    if (planning == null) return null
    return listOfNotNull(
        planning.startTime?.let { "A las ${it.format(HOUR)}" },
        planning.expectedDurationMinutes?.let { "${PlanningInput.hoursText(it)} h" },
        planning.expectedPeopleCount?.let { if (it == 1) "1 persona" else "$it personas" },
        planning.crewText,
    ).joinToString(" · ").ifEmpty { null }
}

internal fun reminderLabel(reminder: Reminder): String {
    val at = reminder.triggerAt.atZone(ZoneId.systemDefault()).toLocalDateTime().format(MOMENT)
    val kind = when (reminder.kind) {
        ReminderKind.PREVIOUS_DAY -> "Aviso la tarde anterior"
        ReminderKind.SAME_DAY -> "Aviso el mismo día"
        ReminderKind.CUSTOM -> "Aviso"
    }
    val fired = if (reminder.firedAt != null) " (ya avisado)" else ""
    return "$kind · $at$fired"
}

private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
private val MOMENT = DateTimeFormatter.ofPattern("dd/MM HH:mm")
