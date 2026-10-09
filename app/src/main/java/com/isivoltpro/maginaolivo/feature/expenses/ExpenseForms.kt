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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.expense.DayCostRole
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
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
    amountLabel: String = "Importe (${initial.currency})",
    extraActions: @Composable () -> Unit = {},
    /** False when the form is placed inside a screen that already scrolls. */
    scrollable: Boolean = true,
    /** Legacy/explicit flows may still preselect a running recolección. */
    preselectRecollection: Boolean = false,
    /** #411: a Farm-level new expense with a running Campaign must choose where it belongs. */
    requireCampaignChoice: Boolean = false,
    /** #375: opened on a Farm already chosen; it is shown as context, never as a selector. */
    farmLocked: Boolean = false,
    /**
     * #416: «Añadir gasto relacionado» — the work is context, not a choice; its Parcel is one of the
     * work's own, and the category must be chosen on purpose rather than left on «Otro».
     */
    activityLocked: Boolean = false,
    /** #416: a new related expense asks its category on purpose; an edit keeps the one it has. */
    askCategory: Boolean = activityLocked,
    /** #415: «Guardar y añadir foto» — saves like [onSave], then the Gasto opens for its photo. */
    onSaveWithPhoto: ((ExpenseForm) -> Unit)? = null,
    /** #411: a new expense opened from a concrete Campaign keeps that Campaign as context. */
    campaignLocked: Boolean = false,
    /**
     * #475: the question a day's jornales/maquinaria cost gets, read from the day as it is (a
     * calculation of that kind exists; with jornales paid it can only add). Null asks nothing.
     */
    dayCostQuestion: suspend (UUID, ExpenseCategory) -> com.isivoltpro.maginaolivo.domain.expense.DayCostQuestion? = { _, _ -> null },
) {
    var form by remember(initial) { mutableStateOf(initial) }
    val lockedCampaignId = initial.campaignId.takeIf { campaignLocked }
    var categoryChosen by rememberSaveable(initial, askCategory) { mutableStateOf(!askCategory) }
    // #411: a new Farm-level expense may start deliberately undecided; existing/explicit
    // Campaign expenses and ordinary editors keep their current classification.
    var campaignChoiceMade by rememberSaveable(initial, requireCampaignChoice) {
        // #433: an expense tied to a work takes that work's Campaign; there is nothing left to choose.
        mutableStateOf(initial.campaignId != null || initial.activityId != null || !requireCampaignChoice)
    }
    LaunchedEffect(preselectRecollection, options.campaigns, form.farmId) {
        // Codex #480: a related expense takes its work's Campaign; nothing is preselected over it.
        if (preselectRecollection && !campaignChoiceMade && !activityLocked && form.activityId == null) {
            val preselected = form.withRecollectionPreselected(options)
            if (preselected.campaignId != null) {
                form = preselected
                campaignChoiceMade = true
            }
        }
    }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    // #415 (owner #524): a Parcel the expense was opened on (Cuaderno of a Parcel) is the origin;
    // it is said, never asked again, and the works offered are those done on it.
    val lockedParcelId = if (!activityLocked && farmLocked) initial.parcelId else null

    val scrolling = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        Modifier.fillMaxWidth().then(scrolling).padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
        // Codex #480 / #433: once the expense is tied to a work (locked or chosen), its Campaign — or
        // none, for general work — is context, not a choice.
        val workBound = activityLocked || form.activityId != null
        val recollection = if (workBound) null else options.recollectionCampaignFor(form, initial.campaignId)
        if (workBound) {
            val work = options.activities.firstOrNull { it.id == form.activityId }
            val workCampaign = work?.campaignId?.let { id -> options.campaigns.firstOrNull { it.id == id } }
            if (work != null) {
                Text(
                    when {
                        workCampaign != null -> "Campaña del trabajo · ${workCampaign.choiceLabel()}"
                        work.campaignId != null -> "En la campaña del trabajo"
                        else -> "Fuera de campaña · trabajo general de la finca"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                    modifier = Modifier.testTag("expense-work-campaign"),
                )
            }
        } else if (lockedCampaignId != null) {
            val campaign = options.campaigns.firstOrNull { it.id == lockedCampaignId }
            Text(
                "Recogida · ${campaign?.choiceLabel() ?: "Campaña seleccionada"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
                modifier = Modifier.testTag("expense-campaign-context"),
            )
        } else if (recollection != null) {
            Column(Modifier.fillMaxWidth().selectableGroup().testTag("expense-kind")) {
                Text("¿Dónde pertenece este gasto?", style = MaterialTheme.typography.titleSmall, color = MoOliveDark)
                ExpenseKindRow("Recogida · ${recollection.choiceLabel()}", campaignChoiceMade && form.campaignId == recollection.id, "expense-kind-recollection") {
                    form = form.copy(campaignId = recollection.id)
                    campaignChoiceMade = true
                }
                ExpenseKindRow("Finca/parcela · Fuera de campaña", campaignChoiceMade && form.campaignId == null, "expense-kind-general") {
                    form = form.copy(campaignId = null)
                    campaignChoiceMade = true
                }
            }
            if (!campaignChoiceMade) {
                Text(
                    "Elige Recogida o Fuera de campaña antes de guardar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoTextSecondary,
                    modifier = Modifier.testTag("expense-kind-required"),
                )
            }
        } else if (form.campaignId != null) {
            Text("Gasto vinculado explícitamente a la campaña", color = MoTextSecondary)
        } else if (!campaignChoiceMade && !options.campaignsKnownFor(form.farmId)) {
            // #411: an empty list while the Farm's campaigns load is not «no running campaign».
            Text(
                "Comprobando las campañas de la finca…",
                style = MaterialTheme.typography.bodySmall,
                color = MoTextSecondary,
                modifier = Modifier.testTag("expense-kind-loading"),
            )
        }
        if (!runCatching { java.util.Currency.getInstance(form.currency).defaultFractionDigits >= 0 }.getOrDefault(false))
            Text("La moneda histórica ${form.currency} no admite edición. Se conserva el importe original.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("expense-currency-error"))
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
            "Categoría", if (categoryChosen) form.category.label() else "Elige la categoría", { picker = "category" },
            Modifier.testTag("expense-category"),
        )
        if (!categoryChosen) {
            Text(
                "Indica qué tipo de gasto es (producto, mano de obra, maquinaria…) antes de guardar.",
                style = MaterialTheme.typography.bodySmall, color = MoTextSecondary,
                modifier = Modifier.testTag("expense-category-required"),
            )
        }
        // #475: a day's jornales or machinery are asked how they count only when that day has a
        // calculation of the kind; with jornales paid they can only add. A cost that already
        // replaces it, unchanged, keeps that choice and may be taken back to «Se añade».
        var question by remember { mutableStateOf<com.isivoltpro.maginaolivo.domain.expense.DayCostQuestion?>(null) }
        val dayId = form.harvestId
        LaunchedEffect(dayId, form.category, categoryChosen) {
            question = if (dayId != null && categoryChosen) dayCostQuestion(dayId, form.category) else null
        }
        val keepsReplacement = initial.dayCostRole == DayCostRole.REPLACEMENT &&
            form.harvestId == initial.harvestId && form.category == initial.category
        val canReplace = question?.canReplace == true || keepsReplacement
        LaunchedEffect(question, keepsReplacement) {
            if (form.dayCostRole == DayCostRole.REPLACEMENT && !canReplace) form = form.copy(dayCostRole = DayCostRole.ADDITIVE)
        }
        if (question != null || keepsReplacement) {
            Text("¿Cómo cuenta en su día de recolección?", style = MaterialTheme.typography.titleSmall)
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(MoSpacing.xs),
            ) {
                listOfNotNull(
                    DayCostRole.ADDITIVE to "Se añade al cálculo",
                    (DayCostRole.REPLACEMENT to "Sustituye el cálculo").takeIf { canReplace },
                ).forEach { (role, label) ->
                    androidx.compose.material3.FilterChip(
                        form.dayCostRole == role,
                        { form = form.copy(dayCostRole = role) },
                        { Text(label) },
                        Modifier.testTag("expense-role-${role.name}"),
                    )
                }
            }
            if (!canReplace) {
                Text(
                    "Ya hay pagos anotados a personas de este día: este gasto se suma al cálculo y no lo sustituye.",
                    style = MaterialTheme.typography.bodySmall, color = MoTextSecondary,
                    modifier = Modifier.testTag("expense-role-labour-paid"),
                )
            }
        }
        MoSectionHeader("Relación")
        val farm = options.farms.firstOrNull { it.id == form.farmId }
        if (farmLocked) {
            // Codex #405: never a selector while locked, not even before the Farms have loaded.
            Text(farm?.name ?: "Cargando la finca…", style = MaterialTheme.typography.titleMedium,
                color = if (farm != null) MoOliveDark else MoTextSecondary, modifier = Modifier.testTag("expense-farm-context"))
        } else {
            MoSelectField("Finca", farm?.name ?: "Sin finca", { picker = "farm" }, Modifier.testTag("expense-farm"))
        }
        val activity = options.activities.firstOrNull { it.id == form.activityId }
        // #433: with a work chosen, only the Parcels it was done on (or the whole work).
        val parcelField: @Composable () -> Unit = {
            if (activity != null) {
                MoSelectField(
                    "Parcela",
                    activity.targets.firstOrNull { it.parcelId == form.parcelId }?.parcelName ?: "Todo el trabajo",
                    { picker = "work-parcel" },
                    Modifier.testTag("expense-parcel"),
                )
            } else {
                MoSelectField("Parcela", options.parcelLabel(form.parcelId, form.farmId) ?: "Toda la finca", { picker = "parcel" },
                    Modifier.testTag("expense-parcel"))
            }
        }
        // #415: a Parcel the expense was opened on is context and stays in sight; otherwise it waits.
        val parcelInSight = farm != null && lockedParcelId != null
        if (farm != null && activityLocked) {
            // #416: the work this expense belongs to; changing it means opening another work.
            Text(
                activity?.let { "Relacionado con · ${it.description} · ${it.activityDate}" } ?: "Cargando el trabajo…",
                style = MaterialTheme.typography.titleMedium,
                color = if (activity != null) MoOliveDark else MoTextSecondary,
                modifier = Modifier.testTag("expense-activity-context"),
            )
            val targets = activity?.targets.orEmpty()
            if (targets.size == 1) {
                Text("Parcela · ${targets.single().parcelName}", style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary, modifier = Modifier.testTag("expense-parcel-context"))
            } else if (targets.size > 1) {
                parcelField()
            }
        } else if (parcelInSight) {
            Text(
                "Parcela · " + (options.parcelLabel(lockedParcelId, form.farmId) ?: "…"),
                style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary,
                modifier = Modifier.testTag("expense-parcel-context"),
            )
        }

        // CR-011 §24 / #415: supplier, the work, a Parcel not given by context, invoice number,
        // purchase lines and notes wait under «Relacionar y más detalles»; they open by themselves
        // when they already hold something (OCR, editing, a chosen work) or have an error.
        val relationsFolded = farm != null && !activityLocked
        val hasDetails = form.invoiceNumber.isNotBlank() || form.lines.isNotEmpty() || form.notes.isNotBlank() ||
            errors.lines != null || form.supplierOrganizationId != null || form.supplierText.isNotBlank() ||
            (relationsFolded && (form.activityId != null || (!parcelInSight && form.parcelId != null)))
        var showDetails by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(hasDetails) { if (hasDetails) showDetails = true }
        if (showDetails || hasDetails) {
            val supplier = form.supplierShown(options.suppliers)
            MoSelectField(
                "Proveedor guardado", supplier.name ?: "Ninguno", { picker = "supplier" },
                Modifier.testTag("expense-supplier"),
            )
            supplier.currentName?.let { now ->
                Text(
                    "Ahora: $now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoTextSecondary,
                    modifier = Modifier.testTag("expense-supplier-now"),
                )
            }
            if (supplier.name == null) {
                MoTextField(
                    form.supplierText, { form = form.copy(supplierText = it) }, "Proveedor (texto libre)",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (relationsFolded) {
                // #415 (owner #524): «Relacionado con»; the domain keeps calling it Activity.
                MoSelectField("Relacionado con", activity?.let { "${it.description} · ${it.activityDate}" } ?: "Ninguno",
                    { picker = "activity" }, Modifier.testTag("expense-activity"))
                if (!parcelInSight) parcelField()
            }
            MoTextField(
                form.invoiceNumber, { form = form.copy(invoiceNumber = it) }, "Nº de factura o ticket (opcional)",
                modifier = Modifier.fillMaxWidth(),
            )
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
        } else {
            MoTertiaryButton("Relacionar y más detalles", { showDetails = true }, Modifier.testTag("expense-more-details"))
        }

        val canSave = !isSaving && categoryChosen &&
            (campaignChoiceMade || (recollection == null && options.campaignsKnownFor(form.farmId))) &&
            runCatching { java.util.Currency.getInstance(form.currency).defaultFractionDigits >= 0 }.getOrDefault(false)
        MoPrimaryButton(
            saveText,
            { onSave(form) },
            modifier = Modifier.fillMaxWidth().testTag("save-expense"),
            enabled = canSave,
        )
        onSaveWithPhoto?.let { saveWithPhoto ->
            MoSecondaryButton(
                "Guardar y añadir foto",
                { saveWithPhoto(form) },
                modifier = Modifier.fillMaxWidth().testTag("save-expense-photo"),
                enabled = canSave,
            )
        }
        extraActions()
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }

    when (picker) {
        "category" -> ChoiceSheet(
            "Categoría",
            ExpenseCategory.entries.map { Choice(it.name, it.label()) },
            form.category.name,
            { key ->
                form = form.copy(category = ExpenseCategory.entries.firstOrNull { it.name == key } ?: form.category)
                categoryChosen = true
            },
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
                    form = form.copy(farmId = farmId, parcelId = null, activityId = null, harvestId = null, campaignId = null)
                    campaignChoiceMade = !requireCampaignChoice
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
        "work-parcel" -> {
            // #416: only the Parcels the work was done on, or the whole work (#433).
            val targets = options.activities.firstOrNull { it.id == form.activityId }?.targets.orEmpty()
            ChoiceSheet(
                "Parcela",
                listOf(Choice(null, "Todo el trabajo")) + targets.map { Choice(it.parcelId.toString(), it.parcelName) },
                form.parcelId?.toString(),
                { key -> form = form.copy(parcelId = key?.let(UUID::fromString)) },
                { picker = null },
                "expense-work-parcel-sheet",
            )
        }
        "activity" -> ChoiceSheet(
            "Relacionado con",
            listOf(Choice(null, "Ninguno")) + options.activities
                .filter { work -> lockedParcelId == null || work.targets.any { it.parcelId == lockedParcelId } }
                .filter { work -> lockedCampaignId == null || work.campaignId == lockedCampaignId }
                .map { Choice(it.id.toString(), "${it.description} · ${it.activityDate}") },
            form.activityId?.toString(),
            { key ->
                // #433: choosing or dropping a work clears what no longer fits it.
                val chosen = key?.let(UUID::fromString)?.let { id -> options.activities.firstOrNull { it.id == id } }
                if (chosen?.id != form.activityId) {
                    form = form.withActivity(chosen)
                    // The Campaign entry remains context when its optional work link is removed.
                    if (lockedCampaignId != null) form = form.copy(campaignId = lockedCampaignId)
                    // Codex #522: dropping the work asks Recogida / Fuera de campaña again, in every
                    // editor, unless a recolección day still gives the Campaign.
                    campaignChoiceMade = chosen != null || form.harvestId != null || lockedCampaignId != null
                }
            },
            { picker = null },
            "expense-activity-sheet",
        )
    }
}

/** One explicit, full-width choice; the label wraps at 360 dp with large text. */
@Composable
private fun ExpenseKindRow(label: String, selected: Boolean, tag: String, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = MoSpacing.xs))
    }
}

private fun ExpenseForm.withLine(index: Int, line: LineForm) =
    copy(lines = lines.mapIndexed { position, current -> if (position == index) line else current })
