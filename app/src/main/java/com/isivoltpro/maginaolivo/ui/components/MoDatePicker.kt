package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.isivoltpro.maginaolivo.ui.theme.MoError
import com.isivoltpro.maginaolivo.ui.theme.MoErrorText
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOliveMid
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSize
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")
private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", SPANISH)
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH)

fun LocalDate.spanishLong(): String = format(LONG_DATE).replaceFirstChar { it.titlecase(SPANISH) }

/**
 * A date field that opens a large month picker instead of asking for AAAA-MM-DD.
 * It reads and writes the same ISO text (`2026-11-20`) the forms already validate, so no
 * form or rule changes. [modifier] carries the field's test tag.
 */
@Composable
fun MoDateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    optional: Boolean = false,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(value.trim()) }.getOrNull()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = MoSize.fieldMinHeight)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Elegir fecha") { open = true },
            shape = MoShape.field,
            color = MoSurfaceSoft,
            border = BorderStroke(1.dp, if (isError) MoError else MoOutline),
        ) {
            Row(
                Modifier.padding(horizontal = MoSpacing.md, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (isError) MoErrorText else MoTextSecondary)
                    Text(
                        date?.format(SHORT_DATE) ?: if (optional) "Sin fecha" else "Elegir fecha",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (date == null) MoTextSecondary else MoInk,
                    )
                }
                Icon(MoIcons.Calendar, contentDescription = null, tint = MoOliveMid, modifier = Modifier.size(22.dp))
            }
        }
        if (supportingText != null) {
            Text(supportingText, style = MaterialTheme.typography.bodySmall, color = if (isError) MoErrorText else MoTextSecondary, modifier = Modifier.padding(start = MoSpacing.md))
        }
    }
    if (open) {
        MoDatePickerSheet(
            initial = date,
            title = label,
            onConfirm = { picked -> onValueChange(picked.toString()); open = false },
            onClear = if (optional) ({ onValueChange(""); open = false }) else null,
            onDismiss = { open = false },
        )
    }
}

/**
 * The date picker (UI polish v2): a bottom sheet opened fully, a whole month with large
 * targets, the chosen date spelt out, quick "Hoy" / "Ayer", and an explicit Confirmar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoDatePickerSheet(
    initial: LocalDate?,
    title: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
    today: LocalDate = LocalDate.now(),
) {
    var selected by remember { mutableStateOf(initial ?: today) }
    var month by remember { mutableStateOf(YearMonth.from(initial ?: today)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen)
                .padding(bottom = MoSpacing.sm)
                .testTag("date-picker-sheet"),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
            Text(selected.spanishLong(), style = MaterialTheme.typography.headlineMedium, color = MoInk, modifier = Modifier.testTag("date-picker-selected"))
            Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                QuickDay("Hoy", "date-picker-today") { selected = today; month = YearMonth.from(today) }
                QuickDay("Ayer", "date-picker-yesterday") { selected = today.minusDays(1); month = YearMonth.from(today.minusDays(1)) }
            }
            MoMonthCalendar(
                month = month,
                selected = selected,
                today = today,
                onMonthChange = { month = it },
                onDaySelected = { selected = it },
                tagPrefix = "date-picker",
            )
            MoPrimaryButton("Confirmar", { onConfirm(selected) }, modifier = Modifier.fillMaxWidth().testTag("date-picker-confirm"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (onClear != null) MoTertiaryButton("Sin fecha", onClear, modifier = Modifier.testTag("date-picker-clear"))
                MoTertiaryButton("Cancelar", onDismiss, modifier = Modifier.testTag("date-picker-cancel"))
            }
        }
    }
}

@Composable
private fun QuickDay(text: String, tag: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onClick).testTag(tag),
        shape = MoShape.pill,
        color = MoSurfaceSoft,
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, color = MoInk)
    }
}
