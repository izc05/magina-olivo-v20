package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.ui.theme.MoColors

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentType
import com.isivoltpro.maginaolivo.domain.expense.DayCostKind
import com.isivoltpro.maginaolivo.domain.expense.DayCostQuestions
import com.isivoltpro.maginaolivo.domain.expense.DayCostRole
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
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
    onPreferCalculated: (DayCostKind) -> Unit = {},
    onEditRates: (() -> Unit)? = null,
    /** CR-010 A3: hand-typed costs of this Farm and date linked to no day. */
    unlinked: List<Expense> = emptyList(),
    onLink: (UUID, DayCostRole) -> Unit = { _, _ -> },
    /** #475: someone of this day has been paid: its jornales can only add to the calculation. */
    labourPaid: Boolean = false,
    loaded: Boolean = true,
    readFailed: Boolean = false,
) {
    MoSectionHeader("Gastos del día")
    if (readFailed || !loaded) {
        Text(
            if (readFailed) "No pudimos leer los gastos de este día." else "Cargando gastos…",
            style = MaterialTheme.typography.bodyMedium,
            color = if (readFailed) MaterialTheme.colorScheme.error else MoColors.current.secondaryText,
            modifier = Modifier.testTag(if (readFailed) "jornada-costs-read-error" else "jornada-costs-loading"),
        )
        return
    }
    val ledger = RecollectionLedger.posted(expenses)
    val draftCount = expenses.count { it.status == ExpenseStatus.DRAFT }
    if (expenses.isEmpty()) {
        Text("Sin gastos anotados.", style = MaterialTheme.typography.bodyMedium, color = MoColors.current.secondaryText, modifier = Modifier.testTag("jornada-no-costs"))
    } else {
        Text(
            listOfNotNull(
                "Coste ${ledger.moneyLabel()}",
                draftCount.takeIf { it > 0 }?.let { if (it == 1) "1 borrador sin contar" else "$it borradores sin contar" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MoColors.current.primaryText,
            modifier = Modifier.testTag("jornada-cost-total"),
        )
        expenses.forEach { expense ->
            MoCompactListItem(
                title = expense.concept,
                subtitle = Money.format(expense.amountMinor, expense.currency),
                icon = MoIcons.Euro,
                onClick = { onExpenseSelected(expense.id) },
                modifier = Modifier.testTag("jornada-cost"),
                trailing = when {
                    expense.status == ExpenseStatus.DRAFT -> { { MoStatusChip("Borrador", tone = MoStatusTone.Neutral) } }
                    expense.calculatedKind() != null -> { { MoStatusChip("Calculado", tone = MoStatusTone.Info) } }
                    else -> null
                },
            )
            // CR-010 A3: a hand-typed cost of the same kind stands; the farmer decides which counts.
            val kind = expense.calculatedKind()
            if (kind != null && expense.status == ExpenseStatus.DRAFT) {
                Text(
                    "Hay ${if (kind == DayCostKind.LABOUR) "jornales" else "maquinaria"} anotados a mano este día: " +
                        "el cálculo (${Money.format(expense.amountMinor, expense.currency)}) no suma. Solo cuenta uno de los dos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoColors.current.secondaryText,
                    modifier = Modifier.testTag("jornada-cost-collision"),
                )
                if (editable) {
                    MoTertiaryButton(
                        "Usar el cálculo",
                        { onPreferCalculated(kind) },
                        Modifier.fillMaxWidth().testTag("jornada-prefer-calculated"),
                    )
                }
            }
        }
    }
    // A3: an unlinked hand-typed cost of the same date may be this day's jornales or machinery.
    // The app never merges or drops it: the farmer links it here, saying how it counts (#475).
    unlinked.forEach { expense ->
        val kind = DayCostKind.of(expense.category)
        val what = if (kind == DayCostKind.LABOUR) "los jornales" else "la maquinaria"
        val canReplace = !(kind == DayCostKind.LABOUR && labourPaid)
        Text(
            "«${expense.concept}» (${Money.format(expense.amountMinor, expense.currency)}) es de este día y no está en " +
                "ningún día de recolección. Puedes enlazarlo: o se añade a $what calculados aquí, o los sustituye. " +
                "Si es otro gasto, déjalo aparte: sigue sumando en la campaña.",
            style = MaterialTheme.typography.bodySmall,
            color = MoColors.current.secondaryText,
            modifier = Modifier.testTag("jornada-unlinked-cost"),
        )
        if (editable) {
            MoTertiaryButton(
                "Enlazar: se añade al cálculo",
                { onLink(expense.id, DayCostRole.ADDITIVE) },
                Modifier.fillMaxWidth().testTag("jornada-link-cost"),
            )
            if (canReplace) {
                MoTertiaryButton(
                    "Enlazar: sustituye el cálculo",
                    { onLink(expense.id, DayCostRole.REPLACEMENT) },
                    Modifier.fillMaxWidth().testTag("jornada-link-cost-replaces"),
                )
            }
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (editable) {
        MoSecondaryButton("Añadir gasto", onAdd, Modifier.fillMaxWidth().testTag("jornada-add-cost"))
        onEditRates?.let { MoTertiaryButton("Precios de recolección", it, Modifier.fillMaxWidth().testTag("jornada-edit-rates")) }
    }
}

private fun Expense.calculatedKind(): DayCostKind? = DayCostKind.entries.firstOrNull { it.origin == origin }

/**
 * CR-010 §8–9 — the Farm's usual prices. Every field is optional: a blank price is unknown and
 * what it would price stays out of the calculation, never counted as zero.
 */
@Composable
internal fun RatesSheet(
    rates: RecollectionRates,
    isSaving: Boolean,
    error: String?,
    onSave: (RecollectionRates) -> Unit,
    onCancel: () -> Unit,
) {
    var fullDay by rememberSaveable { mutableStateOf(Money.editable(rates.fullDayMinor)) }
    var hourly by rememberSaveable { mutableStateOf(Money.editable(rates.hourlyMinor)) }
    var equipment by remember { mutableStateOf(EquipmentType.entries.associateWith { Money.editable(rates.equipmentDayMinor[it]) }) }
    val fields = listOf(fullDay, hourly) + equipment.values
    val valid = fields.all { it.isBlank() || (Money.parseMinor(it) ?: 0) > 0 }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen).testTag("rates-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Precios de recolección", style = MaterialTheme.typography.headlineSmall, color = MoColors.current.primaryText)
        Text(
            "De esta finca. Con ellos se calcula el coste de cada día y se apunta una sola vez en Gastos. " +
                "Deja en blanco lo que no quieras calcular. Las campañas cerradas no cambian.",
            style = MaterialTheme.typography.bodySmall,
            color = MoColors.current.secondaryText,
        )
        MoTextField(fullDay, { fullDay = it }, "Jornada completa (€)", supportingText = "La media jornada es la mitad", modifier = Modifier.fillMaxWidth().testTag("rates-full-day"))
        MoTextField(hourly, { hourly = it }, "Hora (€)", modifier = Modifier.fillMaxWidth().testTag("rates-hourly"))
        MoSectionHeader("Maquinaria, por día")
        EquipmentType.entries.forEach { type ->
            MoTextField(
                equipment[type].orEmpty(),
                { value -> equipment = equipment + (type to value) },
                "${type.singular.replaceFirstChar { it.uppercase() }} (€/día)",
                modifier = Modifier.fillMaxWidth().testTag("rates-equipment-${type.name}"),
            )
        }
        if (!valid) Text("Escribe los importes como 70 o 70,50", color = MaterialTheme.colorScheme.error)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        MoPrimaryButton(
            "Guardar precios",
            {
                onSave(
                    RecollectionRates(
                        fullDayMinor = Money.parseMinor(fullDay),
                        hourlyMinor = Money.parseMinor(hourly),
                        equipmentDayMinor = equipment.mapNotNull { (type, text) -> Money.parseMinor(text)?.let { type to it } }.toMap(),
                        currency = rates.currency,
                    ),
                )
            },
            Modifier.fillMaxWidth().testTag("rates-save"),
            enabled = valid && !isSaving,
        )
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
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
    currency: String? = "EUR",
    currencyError: String? = null,
    /** #475: the calculated costs this day already has, and whether its jornales were paid. */
    calculated: Set<DayCostKind> = emptySet(),
    labourPaid: Boolean = false,
    onSaveWithRole: ((JornadaExpenseKind, Long, String?, Boolean, DayCostRole) -> Unit)? = null,
) {
    var kind by rememberSaveable { mutableStateOf(JornadaExpenseKind.DIESEL) }
    var amount by rememberSaveable { mutableStateOf("") }
    var concept by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf<DayCostRole?>(null) }
    val minor = currency?.let { Money.parseMinor(amount, it) }
    val question = DayCostQuestions.of(kind, calculated, labourPaid)
    // #475: with a calculated cost of that kind, how this one counts is always the farmer's answer.
    val chosen = when {
        question == null -> DayCostRole.ADDITIVE
        !question.canReplace -> DayCostRole.ADDITIVE
        else -> role
    }
    val valid = minor != null && minor > 0 && chosen != null
    val save = { openAfter: Boolean ->
        val text = concept.trim().ifEmpty { null }
        onSaveWithRole?.invoke(kind, minor!!, text, openAfter, chosen!!) ?: onSave(kind, minor!!, text, openAfter)
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen).testTag("cost-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Gasto del día", style = MaterialTheme.typography.headlineSmall, color = MoColors.current.primaryText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            JornadaExpenseKind.entries.forEach { option ->
                FilterChip(kind == option, { kind = option; role = null }, { Text(option.label) }, Modifier.testTag("cost-kind-${option.name}"))
            }
        }
        MoTextField(
            amount, { amount = it }, currency?.let { "Importe ($it)" } ?: "Moneda sin confirmar",
            enabled = currency != null,
            isError = amount.isNotBlank() && !valid,
            supportingText = if (amount.isNotBlank() && !valid) "Escribe un importe como 65 o 65,50" else null,
            modifier = Modifier.fillMaxWidth().testTag("cost-amount"),
        )
        MoTextField(concept, { concept = it }, "Concepto (opcional)", modifier = Modifier.fillMaxWidth().testTag("cost-concept"))
        question?.let { asked ->
            val what = if (asked.calculated == DayCostKind.LABOUR) "jornales" else "maquinaria"
            Text("¿Cómo cuenta este gasto?", style = MaterialTheme.typography.titleSmall, color = MoColors.current.primaryText)
            Text(
                "Este día ya tiene $what calculados.",
                style = MaterialTheme.typography.bodySmall,
                color = MoColors.current.secondaryText,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                FilterChip(chosen == DayCostRole.ADDITIVE, { role = DayCostRole.ADDITIVE },
                    { Text("Se añade al cálculo") }, Modifier.testTag("cost-role-ADDITIVE"))
                if (asked.canReplace) {
                    FilterChip(chosen == DayCostRole.REPLACEMENT, { role = DayCostRole.REPLACEMENT },
                        { Text("Sustituye el cálculo") }, Modifier.testTag("cost-role-REPLACEMENT"))
                }
            }
            if (!asked.canReplace) {
                Text(
                    "Este día tiene pagos por persona: el importe se añade al cálculo de jornales, que no se modifica.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoColors.current.secondaryText,
                    modifier = Modifier.testTag("cost-role-labour-paid"),
                )
            } else if (chosen == DayCostRole.REPLACEMENT && asked.calculated == DayCostKind.LABOUR) {
                Text(
                    "El cálculo de jornales queda guardado sin contar. El importe cuenta en la campaña, sin repartir por persona.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoColors.current.secondaryText,
                    modifier = Modifier.testTag("cost-role-replaces-labour"),
                )
            }
        }
        Text(
            // #475: honest about what this money does to the day's calculation.
            when {
                question == null -> "Se guarda en Gastos y se suma a los costes de este día."
                chosen == DayCostRole.REPLACEMENT -> "Se guarda en Gastos y cuenta en lugar del cálculo; el cálculo queda guardado sin sumar."
                chosen == DayCostRole.ADDITIVE -> "Se guarda en Gastos y se suma al cálculo del día."
                else -> "Se guarda en Gastos. Elige si se añade al cálculo o lo sustituye."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MoColors.current.secondaryText,
            modifier = Modifier.testTag("cost-sheet-ledger-note"),
        )
        currencyError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("day-expense-currency-error")) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        MoPrimaryButton(
            "Guardar gasto",
            { save(false) },
            Modifier.fillMaxWidth().testTag("cost-save"),
            enabled = valid && !isSaving,
        )
        MoSecondaryButton(
            "Guardar y añadir foto del tique",
            { save(true) },
            Modifier.fillMaxWidth().testTag("cost-save-photo"),
            enabled = valid && !isSaving,
        )
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}
