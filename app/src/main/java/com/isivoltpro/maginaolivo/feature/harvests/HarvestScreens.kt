package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.harvest.CollectionMethod
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocationMode
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsRoute
import com.isivoltpro.maginaolivo.feature.expenses.Choice
import com.isivoltpro.maginaolivo.feature.expenses.ChoiceSheet
import com.isivoltpro.maginaolivo.feature.expenses.DATE_FORMAT
import com.isivoltpro.maginaolivo.ui.components.MoConfirmationSheet
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoSelectField
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun HarvestsRoute(
    persistence: LocalPersistence,
    clock: AppClock,
    onHarvestSelected: (UUID) -> Unit,
    onDeliveries: () -> Unit = {},
) {
    val viewModel: HarvestsViewModel = viewModel(
        key = "harvests",
        factory = viewModelFactory { initializer { HarvestsViewModel(persistence.harvestRepository, clock) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    HarvestsScreen(
        state = state,
        today = clock.today(ZoneId.systemDefault()),
        onCreate = viewModel::create,
        onHarvestSelected = onHarvestSelected,
        onEditorClosed = viewModel::clearFormErrors,
        onDeliveries = onDeliveries,
    )
}

/**
 * S70 — Cosecha. Collected kilos only: deliveries to the cooperative are a separate record.
 * Per-Parcel figures are only what was typed; the rest is shown as "sin repartir".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarvestsScreen(
    state: HarvestsUiState,
    today: LocalDate,
    onCreate: (HarvestForm) -> Unit,
    onHarvestSelected: (UUID) -> Unit,
    onEditorClosed: () -> Unit = {},
    onDeliveries: () -> Unit = {},
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) { if (state.message != null) editorVisible = false }

    Scaffold(Modifier.fillMaxSize().testTag("harvests-root"), containerColor = MoCream) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.md))
            Text("Cosecha", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "Kilos recogidos en el campo. Las entregas a la cooperativa o almazara se registran aparte.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )
            MoPrimaryButton(
                "Registrar cosecha",
                { editorVisible = true },
                Modifier.fillMaxWidth().testTag("add-harvest"),
                enabled = state.contexts.isNotEmpty() && !state.isSaving,
            )
            MoSecondaryButton("Entregas a la cooperativa", onDeliveries, Modifier.fillMaxWidth().testTag("open-deliveries"))
            if (!state.isLoading && state.contexts.isEmpty()) {
                Text(
                    "Para registrar cosecha, una finca necesita una campaña activa o en recolección.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                    modifier = Modifier.testTag("harvest-no-campaign"),
                )
            }
            if (state.isSaving) Text("Guardando…", color = MoTextSecondary)
            state.message?.let { Text(it, color = MoTextSecondary, modifier = Modifier.testTag("harvests-message")) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("harvests-error")) }

            when {
                state.isLoading -> CircularProgressIndicator()
                state.harvests.isEmpty() -> MoEmptyState(
                    "Aún no hay cosecha",
                    "Anota cada día de recogida con sus kilos y las parcelas de origen. Si no sabes cuánto salió de cada parcela, no hace falta inventarlo.",
                )
                else -> {
                    state.campaigns.forEach { campaign -> CampaignHarvestCard(campaign) }
                    MoSectionHeader("Registros")
                    state.harvests.forEach { harvest -> HarvestRow(harvest) { onHarvestSelected(harvest.id) } }
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    if (editorVisible) {
        ModalBottomSheet(onDismissRequest = { editorVisible = false; onEditorClosed() }) {
            HarvestEditor(
                title = "Registrar cosecha",
                initial = HarvestForm(
                    farmId = state.contexts.singleOrNull()?.farmId,
                    date = today.toString(),
                ),
                contexts = state.contexts,
                errors = state.formErrors,
                isSaving = state.isSaving,
                saveText = "Guardar cosecha",
                onSave = onCreate,
                onCancel = { editorVisible = false; onEditorClosed() },
            )
        }
    }
}

@Composable
private fun CampaignHarvestCard(campaign: CampaignHarvest) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("campaign-harvest"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            Text(
                listOfNotNull(campaign.farmName, campaign.campaignName).joinToString(" · ").ifEmpty { "Sin campaña" },
                style = MaterialTheme.typography.titleMedium,
                color = MoOliveDark,
            )
            Text(
                Weight.format(campaign.summary.totalGrams),
                style = MaterialTheme.typography.headlineSmall,
                color = MoOlivePrimary,
                modifier = Modifier.testTag("campaign-harvest-total"),
            )
            Text(
                "${campaign.summary.harvestCount} ${if (campaign.summary.harvestCount == 1) "registro" else "registros"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )
            campaign.summary.parcels.forEach { parcel ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(parcel.parcelName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            parcel.exactGrams > 0 && parcel.sharesUnallocated ->
                                "${Weight.format(parcel.exactGrams)} + parte sin repartir"
                            parcel.exactGrams > 0 -> Weight.format(parcel.exactGrams)
                            else -> "Solo en kilos sin repartir"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoTextSecondary,
                    )
                }
            }
            if (campaign.summary.unallocatedGrams > 0) {
                Text(
                    "Sin repartir entre parcelas: ${Weight.format(campaign.summary.unallocatedGrams)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                    modifier = Modifier.testTag("campaign-harvest-unallocated"),
                )
            }
        }
    }
}

@Composable
private fun HarvestRow(harvest: Harvest, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("harvest-row"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(MoSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
                Text(DATE_FORMAT.format(harvest.harvestDate), style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text(
                    harvest.shares.joinToString(", ") { it.parcelName },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                )
                MoStatusChip(harvest.allocationMode.label(), tone = harvest.allocationMode.tone())
            }
            Text(Weight.format(harvest.totalGrams), style = MaterialTheme.typography.titleMedium, color = MoOlivePrimary)
        }
    }
}

private fun HarvestAllocationMode.tone(): MoStatusTone = when (this) {
    HarvestAllocationMode.EXACT -> MoStatusTone.Success
    HarvestAllocationMode.PARTIAL -> MoStatusTone.Info
    HarvestAllocationMode.UNALLOCATED -> MoStatusTone.Neutral
}

/**
 * S71 — Nueva/Editar cosecha. The origin Parcels come from the Farm's running Campaign.
 * With several Parcels the split is an explicit choice, and "No conozco el reparto
 * exacto" is the default: nothing is attributed to a Parcel unless it was typed.
 */
@Composable
internal fun HarvestEditor(
    title: String,
    initial: HarvestForm,
    contexts: List<HarvestContext>,
    errors: HarvestFormErrors,
    isSaving: Boolean,
    saveText: String,
    onSave: (HarvestForm) -> Unit,
    onCancel: () -> Unit,
    farmLocked: Boolean = false,
) {
    var form by remember(initial) { mutableStateOf(initial) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val context = contexts.firstOrNull { it.farmId == form.farmId }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen)
            .testTag("harvest-editor"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        Text("Se guardará primero en este dispositivo.", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)

        if (farmLocked) {
            Text(context?.farmName.orEmpty(), style = MaterialTheme.typography.titleMedium)
        } else {
            MoSelectField(
                "Finca", context?.farmName ?: "Elige la finca", { picker = "farm" },
                Modifier.testTag("harvest-farm"),
            )
        }
        errors.farm?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        context?.let {
            Text(
                "Campaña ${it.campaignName} · ${if (it.campaignStatus == CampaignStatus.HARVEST) "en recolección" else "activa"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )
        }
        MoTextField(
            form.date, { form = form.copy(date = it) }, "Fecha (AAAA-MM-DD)",
            isError = errors.date != null, supportingText = errors.date,
            modifier = Modifier.fillMaxWidth().testTag("harvest-date"),
        )
        MoTextField(
            form.total, { form = form.copy(total = it) }, "Kilos recogidos",
            isError = errors.total != null,
            supportingText = errors.total ?: Weight.parseGrams(form.total)?.let { "= ${Weight.format(it)}" },
            modifier = Modifier.fillMaxWidth().testTag("harvest-total"),
        )

        MoSectionHeader("Parcelas de origen")
        if (context == null) {
            Text("Elige primero la finca.", color = MoTextSecondary)
        } else if (context.parcels.isEmpty()) {
            Text("Esta campaña no tiene parcelas.", color = MoTextSecondary)
        }
        context?.parcels?.forEach { parcel ->
            val checked = parcel.parcelId in form.parcelIds
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("harvest-parcel-option").clickable {
                    form = form.toggle(parcel.parcelId, !checked)
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, { value -> form = form.toggle(parcel.parcelId, value) })
                Text(parcel.name)
            }
        }
        errors.parcels?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("harvest-parcels-error")) }

        if (form.parcelIds.size > 1 && context != null) {
            MoSectionHeader("Reparto entre parcelas")
            SplitOption(
                "No conozco el reparto exacto",
                "Solo se guarda el total. Ninguna parcela recibe kilos inventados.",
                selected = !form.splitKnown,
                tag = "harvest-split-unknown",
            ) { form = form.copy(splitKnown = false) }
            SplitOption(
                "Conozco los kilos de cada parcela",
                "Deja en blanco las parcelas que no conozcas.",
                selected = form.splitKnown,
                tag = "harvest-split-known",
            ) { form = form.copy(splitKnown = true) }
            if (form.splitKnown) {
                form.parcelIds.forEach { parcelId ->
                    val name = context.parcels.firstOrNull { it.parcelId == parcelId }?.name.orEmpty()
                    MoTextField(
                        form.weights[parcelId].orEmpty(),
                        { value -> form = form.copy(weights = form.weights + (parcelId to value)) },
                        "Kilos de $name",
                        modifier = Modifier.fillMaxWidth().testTag("harvest-parcel-weight"),
                    )
                }
                val preview = form.preview()
                preview.totalGrams?.let { total ->
                    Text(
                        buildString {
                            append("Asignado ${Weight.format(preview.allocatedGrams)} de ${Weight.format(total)}")
                            if (total > preview.allocatedGrams) {
                                append(" · Sin repartir ${Weight.format(total - preview.allocatedGrams)}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (preview.allocatedGrams > total) MaterialTheme.colorScheme.error else MoTextSecondary,
                        modifier = Modifier.testTag("harvest-allocation-preview"),
                    )
                }
            }
        }

        MoSectionHeader("Recogida")
        MoSelectField(
            "Método de recogida", form.collectionMethod?.label() ?: "Sin indicar", { picker = "method" },
            Modifier.testTag("harvest-method"),
        )
        MoTextField(
            form.workers, { form = form.copy(workers = it) }, "Personas trabajando (opcional)",
            isError = errors.workers != null, supportingText = errors.workers,
            modifier = Modifier.fillMaxWidth().testTag("harvest-workers"),
        )
        MoTextField(
            form.machinery, { form = form.copy(machinery = it) }, "Maquinaria (opcional)",
            modifier = Modifier.fillMaxWidth(),
        )
        MoTextField(form.notes, { form = form.copy(notes = it) }, "Notas", singleLine = false, modifier = Modifier.fillMaxWidth())

        MoPrimaryButton(
            saveText,
            { onSave(form) },
            modifier = Modifier.fillMaxWidth().testTag("save-harvest"),
            enabled = !isSaving,
        )
        MoSecondaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }

    when (picker) {
        "farm" -> ChoiceSheet(
            "Finca",
            contexts.map { Choice(it.farmId.toString(), "${it.farmName} · ${it.campaignName}") },
            form.farmId?.toString(),
            { key ->
                val farmId = key?.let(UUID::fromString)
                if (farmId != form.farmId) form = form.copy(farmId = farmId, parcelIds = emptyList(), weights = emptyMap())
            },
            { picker = null },
            "harvest-farm-sheet",
        )
        "method" -> ChoiceSheet(
            "Método de recogida",
            listOf(Choice(null, "Sin indicar")) + CollectionMethod.entries.map { Choice(it.name, it.label()) },
            form.collectionMethod?.name,
            { key -> form = form.copy(collectionMethod = CollectionMethod.entries.firstOrNull { it.name == key }) },
            { picker = null },
            "harvest-method-sheet",
        )
    }
}

private fun HarvestForm.toggle(parcelId: UUID, selected: Boolean): HarvestForm =
    if (selected) {
        copy(parcelIds = (parcelIds + parcelId).distinct())
    } else {
        copy(parcelIds = parcelIds - parcelId, weights = weights - parcelId)
    }

@Composable
private fun SplitOption(title: String, body: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MoTextSecondary)
        }
    }
}

@Composable
fun HarvestDetailRoute(
    harvestId: UUID,
    persistence: LocalPersistence,
    clock: AppClock,
    onDeleted: () -> Unit,
) {
    val viewModel: HarvestDetailViewModel = viewModel(
        key = "harvest-$harvestId",
        factory = viewModelFactory {
            initializer { HarvestDetailViewModel(harvestId, persistence.harvestRepository, clock) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    HarvestDetailScreen(
        state = state,
        onUpdate = viewModel::update,
        onDelete = viewModel::delete,
        onEditorClosed = viewModel::clearFormErrors,
        attachmentContent = {
            AttachmentsRoute(
                owner = AttachmentOwner(AttachmentOwnerType.HARVEST, harvestId),
                persistence = persistence,
                title = "Fotos y documentos",
            )
        },
    )
}

/** S72 — Detalle cosecha: the total and how it is split, stated plainly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarvestDetailScreen(
    state: HarvestDetailUiState,
    onUpdate: (HarvestForm) -> Unit,
    onDelete: () -> Unit,
    onEditorClosed: () -> Unit = {},
    attachmentContent: @Composable () -> Unit = {},
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) { if (state.message != null) editorVisible = false }

    Scaffold(Modifier.fillMaxSize().testTag("harvest-detail-root"), containerColor = MoCream) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            val harvest = state.harvest
            when {
                state.isLoading -> CircularProgressIndicator()
                harvest == null -> MoErrorState("Cosecha no disponible", state.error ?: "No está guardada en este dispositivo.")
                else -> {
                    HarvestSummaryBlock(harvest)
                    if (harvest.editable) {
                        MoSecondaryButton(
                            "Editar cosecha", { editorVisible = true },
                            Modifier.fillMaxWidth().testTag("edit-harvest"),
                            enabled = state.context != null && !state.isSaving,
                        )
                        MoSecondaryButton(
                            "Eliminar cosecha", { confirmDelete = true },
                            Modifier.fillMaxWidth().testTag("delete-harvest"),
                            enabled = !state.isSaving,
                        )
                    } else {
                        Text(
                            "La campaña está cerrada: esta cosecha forma parte del histórico y no se modifica.",
                            color = MoTextSecondary,
                            modifier = Modifier.testTag("harvest-read-only"),
                        )
                    }
                    state.message?.let { Text(it, color = MoTextSecondary) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    attachmentContent()
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    val harvest = state.harvest
    val context = state.context
    if (editorVisible && harvest != null && context != null) {
        ModalBottomSheet(onDismissRequest = { editorVisible = false; onEditorClosed() }) {
            HarvestEditor(
                title = "Editar cosecha",
                initial = harvest.toForm(),
                contexts = listOf(context),
                errors = state.formErrors,
                isSaving = state.isSaving,
                saveText = "Guardar cambios",
                onSave = onUpdate,
                onCancel = { editorVisible = false; onEditorClosed() },
                farmLocked = true,
            )
        }
    }
    if (confirmDelete) {
        ModalBottomSheet(onDismissRequest = { confirmDelete = false }) {
            MoConfirmationSheet(
                title = "Eliminar cosecha",
                body = "Estos kilos dejarán de contar en la campaña. Esta acción no se puede deshacer.",
                confirmText = "Eliminar",
                onConfirm = { confirmDelete = false; onDelete() },
                onCancel = { confirmDelete = false },
                modifier = Modifier.padding(horizontal = MoSpacing.md).testTag("harvest-confirmation"),
            )
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

@Composable
private fun HarvestSummaryBlock(harvest: Harvest) {
    Text(
        "Cosecha del ${DATE_FORMAT.format(harvest.harvestDate)}",
        style = MaterialTheme.typography.headlineMedium,
        color = MoOliveDark,
    )
    Text(
        listOfNotNull(harvest.farmName, harvest.campaignName).joinToString(" · "),
        style = MaterialTheme.typography.bodyLarge,
        color = MoTextSecondary,
    )
    MoMetricCard(
        "Kilos recogidos",
        Weight.format(harvest.totalGrams),
        Modifier.fillMaxWidth().testTag("harvest-total-value"),
        supportingText = harvest.allocationMode.label(),
    )
    MoSectionHeader("Parcelas de origen")
    harvest.shares.forEach { share ->
        Row(Modifier.fillMaxWidth().testTag("harvest-share"), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(share.parcelName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (share.allocation == HarvestAllocation.EXACT && share.weightGrams != null) {
                    Weight.format(share.weightGrams)
                } else {
                    "Kilos no conocidos"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )
        }
    }
    if (harvest.allocationMode != HarvestAllocationMode.EXACT) {
        Text(
            "Sin repartir entre parcelas: ${Weight.format(harvest.unallocatedGrams)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MoTextSecondary,
            modifier = Modifier.testTag("harvest-unallocated"),
        )
    }
    DetailValue("Método de recogida", harvest.collectionMethod?.label())
    DetailValue("Personas trabajando", harvest.workerCount?.toString())
    DetailValue("Maquinaria", harvest.machineryText)
    harvest.notes?.let { DetailValue("Notas", it) }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
        Text(value ?: "Sin registrar", style = MaterialTheme.typography.bodyLarge)
    }
}
