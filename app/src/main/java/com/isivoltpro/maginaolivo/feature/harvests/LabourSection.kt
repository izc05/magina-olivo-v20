package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourSummary
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.labour.LabourRules
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
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
        1 -> runCatching { cleaned.toBigDecimal().multiply(java.math.BigDecimal(60)).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact() }.getOrNull()
        2 -> {
            val h = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (h == null || m == null || h < 0 || m !in 0..59) null else runCatching { Math.addExact(Math.multiplyExact(h, 60), m) }.getOrNull()
        }
        else -> null
    }
    return minutes?.takeIf { it in 1..LabourRules.MAX_MINUTES }
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
    /** False while this day's jornales are still being read: nothing is claimed yet. */
    loaded: Boolean = true,
    /** True when the jornales could not be read: an error, never «Sin jornales». */
    readFailed: Boolean = false,
    onPerson: (UUID) -> Unit = {},
    onEdit: (UUID) -> Unit = {},
) {
    // Device check (build 680): these are this day's jornales; the Cuaderno sums the whole campaign.
    MoSectionHeader("Jornales de este día")
    val summary = LabourSummary.of(labour)
    if (readFailed) {
        Text(
            "No pudimos leer los jornales de este día.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("jornada-labour-read-error"),
        )
    } else if (!loaded) {
        Text(
            "Cargando jornales…",
            style = MaterialTheme.typography.bodyMedium,
            color = MoColors.current.secondaryText,
            modifier = Modifier.testTag("jornada-labour-loading"),
        )
    } else if (summary.isEmpty) {
        Text(
            "Sin jornales anotados.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoColors.current.secondaryText,
            modifier = Modifier.testTag("jornada-no-labour"),
        )
    } else {
        Text(
            "${if (summary.people == 1) "1 persona" else "${summary.people} personas"} · ${summary.label()}",
            style = MaterialTheme.typography.bodyLarge,
            color = MoColors.current.primaryText,
            modifier = Modifier.testTag("jornada-labour-summary"),
        )
        labour.forEach { entry ->
            MoCompactListItem(
                title = entry.workerName ?: "Sin identificar · ${entry.quantity} personas",
                subtitle = entry.label() + if (entry.appliedRate == null) " · Precio sin confirmar" else "",
                onClick = entry.workerId?.let { { onPerson(it) } },
                iconTint = MoColors.current.labourText,
                iconContainer = MoColors.current.labourTint,
                icon = if (entry.workerId != null) MoIcons.Person else MoIcons.People,
                modifier = Modifier.testTag("jornada-labour"),
                trailing = if (editable) {
                    { Column { MoTertiaryButton("Editar", { onEdit(entry.id) }, Modifier.testTag("jornada-labour-edit")); MoTertiaryButton("Quitar", { onRemove(entry.id) }, Modifier.testTag("jornada-labour-remove")) } }
                } else {
                    null
                },
            )
        }
    }
    message?.let { Text(it, color = MoColors.current.secondaryText, modifier = Modifier.testTag("jornada-labour-message")) }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("jornada-labour-error")) }
    if (editable) {
        MoSecondaryButton("Registrar jornales", onRegister, Modifier.fillMaxWidth().testTag("jornada-register-labour"))
    }
}
