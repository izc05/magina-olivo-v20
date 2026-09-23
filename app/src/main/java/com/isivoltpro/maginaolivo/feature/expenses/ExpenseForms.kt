package com.isivoltpro.maginaolivo.feature.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.ui.components.MoDateInputField
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoSelectField
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.util.UUID

/** One option of a [ChoiceSheet]. A null [key] is the explicit "none" choice. */
internal data class Choice(val key: String?, val label: String)

/** A short list to pick one value from, in the same sheet language as the rest of the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoiceSheet(
    title: String,
    choices: List<Choice>,
    selected: String?,
    onSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
    testTag: String,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen)
                .testTag(testTag),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
            choices.forEach { choice ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable { onSelected(choice.key); onDismiss() }
                        .testTag("choice-${choice.key ?: "none"}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = choice.key == selected, onClick = { onSelected(choice.key); onDismiss() })
                    Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(MoSpacing.lg))
        }
    }
}

/**
 * S61 — one expense form for new, edited and document-reviewed expenses. It shows only
 * locally known Farms, Parcels, Activities and suppliers; nothing waits for a network.
 */
@Composable
internal fun ExpenseEditor(
    title: String,
    initial: ExpenseForm,
    options: RelationOptions,
    errors: ExpenseFormErrors,
    isSaving: Boolean,
    saveText: String,
    onFarmSelected: (UUID?) -> Unit,
    onSave: (ExpenseForm) -> Unit,
    onCancel: () -> Unit,
    subtitle: String = "Se guardará primero en este dispositivo.",
    amountLabel: String = "Importe (€)",
    extraActions: @Composable () -> Unit = {},
    /** False when the form is placed inside a screen that already scrolls. */
    scrollable: Boolean = true,
) {
    var form by remember(initial) { mutableStateOf(initial) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }

    val scrolling = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        Modifier.fillMaxWidth().then(scrolling).padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
        MoTextField(
            form.concept, { form = form.copy(concept = it) }, "Concepto",
            isError = errors.concept != null, supportingText = errors.concept,
            modifier = Modifier.fillMaxWidth().testTag("expense-concept"),
        )
        MoTextField(
            form.amount, { form = form.copy(amount = it) }, amountLabel,
            isError = errors.amount != null, supportingText = errors.amount,
            modifier = Modifier.fillMaxWidth().testTag("expense-amount"),
        )
        MoDateInputField(
            form.date, { form = form.copy(date = it) }, "Fecha",
            isError = errors.date != null, supportingText = errors.date,
            modifier = Modifier.fillMaxWidth().testTag("expense-date"),
        )
        MoSelectField(
            "Categoría", form.category.label(), { picker = "category" },
            Modifier.testTag("expense-category"),
        )
        val supplier = options.suppliers.firstOrNull { it.id == form.supplierOrganizationId }
        MoSelectField(
            "Proveedor guardado", supplier?.name ?: "Ninguno", { picker = "supplier" },
            Modifier.testTag("expense-supplier"),
        )
        if (supplier == null) {
            MoTextField(
                form.supplierText, { form = form.copy(supplierText = it) }, "Proveedor (texto libre)",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MoTextField(
            form.invoiceNumber, { form = form.copy(invoiceNumber = it) }, "Nº de factura o ticket (opcional)",
            modifier = Modifier.fillMaxWidth(),
        )

        MoSectionHeader("Relación")
        val farm = options.farms.firstOrNull { it.id == form.farmId }
        MoSelectField("Finca", farm?.name ?: "Sin finca", { picker = "farm" }, Modifier.testTag("expense-farm"))
        if (farm != null) {
            val parcel = options.parcels.firstOrNull { it.id == form.parcelId }
            MoSelectField("Parcela", parcel?.displayName ?: "Toda la finca", { picker = "parcel" })
            val activity = options.activities.firstOrNull { it.id == form.activityId }
            MoSelectField("Actuación", activity?.description ?: "Ninguna", { picker = "activity" })
        }

        MoSectionHeader(
            "Qué se compró (opcional)",
            action = {
                TextButton(
                    onClick = { form = form.copy(lines = form.lines + LineForm()) },
                    modifier = Modifier.testTag("add-purchase-line"),
                ) { Text("Añadir línea") }
            },
        )
        Text(
            "Las líneas describen la compra; el importe que cuenta es el total del gasto.",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
        )
        errors.lines?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        form.lines.forEachIndexed { index, line ->
            Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                MoTextField(
                    line.product, { value -> form = form.withLine(index, line.copy(product = value)) }, "Producto",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                    MoTextField(
                        line.quantity, { value -> form = form.withLine(index, line.copy(quantity = value)) }, "Cantidad",
                        modifier = Modifier.weight(1f),
                    )
                    MoTextField(
                        line.unit, { value -> form = form.withLine(index, line.copy(unit = value)) }, "Unidad",
                        modifier = Modifier.weight(1f),
                    )
                    MoTextField(
                        line.total, { value -> form = form.withLine(index, line.copy(total = value)) }, "Importe",
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = { form = form.copy(lines = form.lines.filterIndexed { position, _ -> position != index }) }) {
                    Text("Quitar línea")
                }
            }
        }
        MoTextField(form.notes, { form = form.copy(notes = it) }, "Notas", singleLine = false, modifier = Modifier.fillMaxWidth())

        MoPrimaryButton(
            saveText,
            { onSave(form) },
            modifier = Modifier.fillMaxWidth().testTag("save-expense"),
            enabled = !isSaving,
        )
        extraActions()
        MoSecondaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }

    when (picker) {
        "category" -> ChoiceSheet(
            "Categoría",
            ExpenseCategory.entries.map { Choice(it.name, it.label()) },
            form.category.name,
            { key -> form = form.copy(category = ExpenseCategory.entries.firstOrNull { it.name == key } ?: form.category) },
            { picker = null },
            "expense-category-sheet",
        )
        "supplier" -> ChoiceSheet(
            "Proveedor",
            listOf(Choice(null, "Ninguno (escribir a mano)")) + options.suppliers.map { Choice(it.id.toString(), it.name) },
            form.supplierOrganizationId?.toString(),
            { key -> form = form.copy(supplierOrganizationId = key?.let(UUID::fromString)) },
            { picker = null },
            "expense-supplier-sheet",
        )
        "farm" -> ChoiceSheet(
            "Finca",
            listOf(Choice(null, "Sin finca")) + options.farms.map { Choice(it.id.toString(), it.name) },
            form.farmId?.toString(),
            { key ->
                val farmId = key?.let(UUID::fromString)
                if (farmId != form.farmId) {
                    form = form.copy(farmId = farmId, parcelId = null, activityId = null)
                    onFarmSelected(farmId)
                }
            },
            { picker = null },
            "expense-farm-sheet",
        )
        "parcel" -> ChoiceSheet(
            "Parcela",
            listOf(Choice(null, "Toda la finca")) + options.parcels.map { Choice(it.id.toString(), it.displayName) },
            form.parcelId?.toString(),
            { key -> form = form.copy(parcelId = key?.let(UUID::fromString)) },
            { picker = null },
            "expense-parcel-sheet",
        )
        "activity" -> ChoiceSheet(
            "Actuación",
            listOf(Choice(null, "Ninguna")) + options.activities.map {
                Choice(it.id.toString(), "${it.description} · ${it.activityDate}")
            },
            form.activityId?.toString(),
            { key -> form = form.copy(activityId = key?.let(UUID::fromString)) },
            { picker = null },
            "expense-activity-sheet",
        )
    }
}

private fun ExpenseForm.withLine(index: Int, line: LineForm) =
    copy(lines = lines.mapIndexed { position, current -> if (position == index) line else current })
