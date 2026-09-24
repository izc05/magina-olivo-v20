package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.JornadaCost
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.util.UUID

/**
 * Phase 19F — the Jornada's costs, read straight from the Expense ledger: the total is the
 * posted Expenses linked to it, nothing else, so it always matches Gastos.
 */
@Composable
internal fun JornadaCosts(
    expenses: List<Expense>,
    editable: Boolean,
    error: String?,
    onAdd: () -> Unit,
    onExpenseSelected: (UUID) -> Unit,
) {
    MoSectionHeader("Gastos de la jornada")
    val cost = JornadaCost.of(expenses)
    if (expenses.isEmpty()) {
        Text("Sin gastos anotados.", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary, modifier = Modifier.testTag("jornada-no-costs"))
    } else {
        Text(
            listOfNotNull(
                "Coste ${Money.format(cost.postedMinor, cost.summary.currency)}",
                cost.draftCount.takeIf { it > 0 }?.let { if (it == 1) "1 borrador sin contar" else "$it borradores sin contar" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MoOliveDark,
            modifier = Modifier.testTag("jornada-cost-total"),
        )
        expenses.forEach { expense ->
            MoCompactListItem(
                title = expense.concept,
                subtitle = Money.format(expense.amountMinor, expense.currency),
                icon = MoIcons.Euro,
                onClick = { onExpenseSelected(expense.id) },
                modifier = Modifier.testTag("jornada-cost"),
                trailing = if (expense.status == ExpenseStatus.DRAFT) {
                    { MoStatusChip("Borrador", tone = MoStatusTone.Neutral) }
                } else {
                    null
                },
            )
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (editable) {
        MoSecondaryButton("Añadir gasto", onAdd, Modifier.fillMaxWidth().testTag("jornada-add-cost"))
    }
}

/**
 * Phase 19F — a recollection cost in three taps: kind, amount, save. It becomes an ordinary
 * posted Expense of the Jornada's Farm and Campaign; a ticket photo can be added right after.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CostSheet(
    isSaving: Boolean,
    error: String?,
    onSave: (JornadaExpenseKind, Long, String?, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(JornadaExpenseKind.DIESEL) }
    var amount by rememberSaveable { mutableStateOf("") }
    var concept by rememberSaveable { mutableStateOf("") }
    val minor = Money.parseMinor(amount)
    val valid = minor != null && minor > 0

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen).testTag("cost-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Gasto de la jornada", style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            JornadaExpenseKind.entries.forEach { option ->
                FilterChip(kind == option, { kind = option }, { Text(option.label) }, Modifier.testTag("cost-kind-${option.name}"))
            }
        }
        MoTextField(
            amount, { amount = it }, "Importe (€)",
            isError = amount.isNotBlank() && !valid,
            supportingText = if (amount.isNotBlank() && !valid) "Escribe un importe como 65 o 65,50" else null,
            modifier = Modifier.fillMaxWidth().testTag("cost-amount"),
        )
        MoTextField(concept, { concept = it }, "Concepto (opcional)", modifier = Modifier.fillMaxWidth().testTag("cost-concept"))
        Text(
            "Se guarda en Gastos, el único registro del dinero. No se cuenta dos veces.",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        MoPrimaryButton(
            "Guardar gasto",
            { onSave(kind, minor!!, concept.trim().ifEmpty { null }, false) },
            Modifier.fillMaxWidth().testTag("cost-save"),
            enabled = valid && !isSaving,
        )
        MoSecondaryButton(
            "Guardar y añadir foto del tique",
            { onSave(kind, minor!!, concept.trim().ifEmpty { null }, true) },
            Modifier.fillMaxWidth().testTag("cost-save-photo"),
            enabled = valid && !isSaving,
        )
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}
