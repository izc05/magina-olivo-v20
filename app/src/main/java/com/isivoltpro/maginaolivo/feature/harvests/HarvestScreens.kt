package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.ui.components.OnEachSave
import android.util.Log
import com.isivoltpro.maginaolivo.domain.expense.DayCostRole
import com.isivoltpro.maginaolivo.domain.expense.DayCostKind
import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import androidx.compose.foundation.layout.WindowInsets
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
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.harvest.CollectionMethod
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocationMode
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsRoute
import com.isivoltpro.maginaolivo.feature.expenses.Choice
import com.isivoltpro.maginaolivo.feature.expenses.ChoiceSheet
import com.isivoltpro.maginaolivo.feature.expenses.DATE_FORMAT
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoDateInputField
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
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoKpiKind
import com.isivoltpro.maginaolivo.ui.components.MoKpiMetric
import com.isivoltpro.maginaolivo.ui.components.MoMetricGrid
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.isivoltpro.maginaolivo.ui.theme.MoInk

@Composable
fun HarvestsRoute(
    persistence: LocalPersistence,
    clock: AppClock,
    onHarvestSelected: (UUID) -> Unit,
    onDeliveries: (UUID?) -> Unit = { _ -> },
    /** #408: when present, this surface belongs to one Campaign and must never leak another one. */
    campaignId: UUID? = null,
) {
    val viewModel: HarvestsViewModel = viewModel(
        key = "harvests-${campaignId ?: "all"}",
        factory = viewModelFactory { initializer { HarvestsViewModel(persistence.harvestRepository, clock) } },
    )
    val rawState by viewModel.state.collectAsStateWithLifecycle()
    val state = remember(rawState, campaignId) {
        if (campaignId == null) rawState
        else {
            val scoped = rawState.harvests.filter { it.campaignId == campaignId }
            rawState.copy(
                harvests = scoped,
                campaigns = rawState.campaigns.filter { it.campaignId == campaignId },
                contexts = rawState.contexts.filter { it.campaignId == campaignId },
            )
        }
    }
    val deliveries by remember(campaignId) {
        if (campaignId == null) persistence.deliveryRepository.observeAll()
        else persistence.deliveryRepository.observeForCampaign(campaignId)
    }.collectAsStateWithLifecycle(emptyList())
    // CR-011 §9/§23: jornales and machinery of the days listed, read from their own ledgers.
    val campaignIds = remember(state.harvests) { state.harvests.mapNotNull { it.campaignId }.distinct() }
    val labour by remember(campaignIds) {
        if (campaignIds.isEmpty()) flowOf(emptyList<LabourEntry>())
        else combine(campaignIds.map { persistence.labourRepository.observeForCampaign(it) }) { lists -> lists.flatMap { it } }
    }.collectAsStateWithLifecycle(emptyList())
    val equipment by remember(campaignIds) {
        if (campaignIds.isEmpty()) flowOf(emptyList<EquipmentLine>())
        else combine(campaignIds.map { persistence.equipmentRepository.observeForCampaign(it) }) { lists -> lists.flatMap { it } }
    }.collectAsStateWithLifecycle(emptyList())
    val dayLines = remember(state.harvests, deliveries, labour, equipment) {
        state.harvests.associate { harvest ->
            harvest.id to dayRowLine(
                harvest = harvest,
                pesadas = deliveries.count { it.harvestId == harvest.id },
                labour = labour.filter { it.harvestId == harvest.id },
                equipment = equipment.filter { it.harvestId == harvest.id },
            )
        }
    }
    HarvestsScreen(
        state = state,
        today = clock.today(ZoneId.systemDefault()),
        onCreate = viewModel::create,
        onHarvestSelected = onHarvestSelected,
        onEditorClosed = viewModel::clearFormErrors,
        onDeliveries = { onDeliveries(state.contexts.singleOrNull()?.farmId) },
        deliverySummary = remember(deliveries) { DeliverySummary.of(deliveries) },
        dayLines = dayLines,
    )
}

/** #246 §4B — Recolección: Jornadas derived from their canonical Pesadas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarvestsScreen(
    state: HarvestsUiState,
    today: LocalDate,
    onCreate: (HarvestForm) -> Unit,
    onHarvestSelected: (UUID) -> Unit,
    onEditorClosed: () -> Unit = {},
    onDeliveries: () -> Unit = {},
    /** Delivered kilos and yield, read from the Delivery ledger (never recomputed here). */
    deliverySummary: DeliverySummary? = null,
    /** CR-011 §9: each day's «kg · pesadas · jornales · maquinaria» line, by day id. */
    dayLines: Map<UUID, String> = emptyMap(),
) {
    Scaffold(Modifier.fillMaxSize().testTag("harvests-root"), containerColor = MoCream, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.sm))
            Text("Recolección", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "Cada día de recolección reúne sus pesadas, jornales y gastos. Los kilos se obtienen de las pesadas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )
            // UI polish v2: the summary first, then the actions.
            if (!state.isLoading) {
                MoMetricGrid(
                    content = listOf(
                        { m -> MoKpiMetric("Kg pesados", deliverySummary?.takeIf { it.deliveryCount > 0 }?.deliveredGrams?.let(Weight::format) ?: "—", m.testTag("harvest-metric-kg"), icon = MoIcons.Delivery, kind = MoKpiKind.PESADAS) },
                        { m -> MoKpiMetric("Pesadas", (deliverySummary?.deliveryCount ?: 0).toString(), m, icon = MoIcons.Checklist, kind = MoKpiKind.PESADAS) },
                        { m ->
                            // #366: calendar days, not records — two Farms on 3 oct are one day.
                            MoKpiMetric(
                                "Días de recolección",
                                harvestDayCount(state.harvests).toString(),
                                m.testTag("harvest-metric-days"),
                                icon = MoIcons.Harvest,
                                kind = MoKpiKind.CAMPAIGN,
                                supportingText = if (state.harvests.isEmpty()) "Se crea con su primera pesada o jornal"
                                else harvestDaysSpread(state.harvests) ?: "Con pesadas o jornales",
                            )
                        },
                        { m ->
                            MoKpiMetric(
                                "Rendimiento graso",
                                deliverySummary?.fatYield?.let { Percent.format(it.hundredths) } ?: "—",
                                m,
                                icon = MoIcons.Percent,
                                kind = MoKpiKind.PESADAS,
                                supportingText = if (deliverySummary?.fatYield == null) "Con los análisis de las pesadas" else "Ponderado por kilos",
                            )
                        },
                    ),
                )
            }
            MoPrimaryButton(
                "+ Nueva pesada",
                onDeliveries,
                Modifier.fillMaxWidth().testTag("add-pesada"),
                enabled = state.contexts.isNotEmpty() && !state.isSaving,
            )
            if (!state.isLoading && state.contexts.isEmpty()) {
                Text(
                    "Para registrar una pesada, una finca necesita una campaña activa o en recolección.",
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
                    "Aún no hay días de recolección",
                    "Registra una pesada o un jornal desde el Cuaderno y su día de recolección se crea solo. " +
                        "Sus kilos son siempre la suma de sus pesadas.",
                    icon = MoIcons.Harvest,
                )
                else -> {
                    state.campaigns.forEach { campaign ->
                        CampaignHarvestCard(campaign, harvestDayCount(state.harvests.filter { it.campaignId == campaign.campaignId }))
                    }
                    MoSectionHeader("Días de recolección")
                    state.harvests.forEach { harvest ->
                        HarvestRow(harvest, dayLines[harvest.id] ?: dayRowLine(harvest, 0, emptyList(), emptyList())) {
                            onHarvestSelected(harvest.id)
                        }
                    }
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

}

@Composable
private fun CampaignHarvestCard(campaign: CampaignHarvest, days: Int) {
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
                campaignHarvestHeadline(campaign.summary),
                style = MaterialTheme.typography.titleLarge,
                color = MoInk,
                modifier = Modifier.testTag("campaign-harvest-total"),
            )
            Text(
                "${harvestDays(days)} de recolección",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
                modifier = Modifier.testTag("campaign-harvest-days"),
            )
            campaign.summary.parcels.forEach { parcel ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(parcel.parcelName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        parcelHarvestText(parcel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoTextSecondary,
                    )
                }
            }
            unallocatedLine(campaign.summary)?.let { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                    modifier = Modifier.testTag("campaign-harvest-unallocated"),
                )
            }
        }
    }
}

@Composable
private fun HarvestRow(harvest: Harvest, line: String, onClick: () -> Unit) {
    // CR-011 §9/§23: icon + «12 dic 2026 · Día de recolección», then what it holds, then status.
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("harvest-row"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(MoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            MoIconBadge(MoIcons.Harvest)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
                Text(
                    harvestRowTitle(harvest),
                    style = MaterialTheme.typography.titleMedium,
                    color = MoOliveDark,
                    modifier = Modifier.testTag("harvest-row-title"),
                )
                Text(
                    line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (harvest.awaitingPesadas) MoTextSecondary else MoInk,
                    modifier = Modifier.testTag("harvest-row-line"),
                )
                MoStatusChip(harvest.allocationMode.label(), tone = harvest.allocationMode.tone())
            }
        }
    }
}

private fun HarvestAllocationMode.tone(): MoStatusTone = when (this) {
    HarvestAllocationMode.EXACT -> MoStatusTone.Success
    HarvestAllocationMode.PARTIAL -> MoStatusTone.Info
    HarvestAllocationMode.UNALLOCATED -> MoStatusTone.Neutral
}

/**
 * Legacy Jornada editor. The origin Parcels come from the Farm's running Campaign.
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
    pesadaCount: Int = 0,
) {
    var form by remember(initial) { mutableStateOf(initial) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val context = contexts.firstOrNull { it.farmId == form.farmId }
    // Phase 19B: with Pesadas, the kilos are theirs; the farmer never types them twice.
    // CR-010: an automatic day's kilos are always its Pesadas', even before the first one.
    val kilosFromPesadas = pesadaCount > 0 || form.automatic

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
                "Campaña ${it.campaignName} · activa",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )
        }
        if (form.automatic) {
            // CR-010: the day is its Pesadas' date; each Pesada changes its own date.
            Text(
                "Día ${runCatching { DATE_FORMAT.format(LocalDate.parse(form.date)) }.getOrDefault(form.date)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("harvest-day-date"),
            )
            errors.date?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } else {
            MoDateInputField(
                form.date, { form = form.copy(date = it) }, "Fecha",
                isError = errors.date != null, supportingText = errors.date,
                modifier = Modifier.fillMaxWidth().testTag("harvest-date"),
            )
        }
        MoTextField(
            form.total, { form = form.copy(total = it) }, if (form.automatic) "Kilos pesados" else "Kilos históricos",
            enabled = !kilosFromPesadas,
            isError = errors.total != null,
            supportingText = when {
                errors.total != null -> errors.total
                pesadaCount == 0 && form.automatic -> "$PENDING_KILOS: serán la suma de sus pesadas"
                kilosFromPesadas -> if (pesadaCount == 1) "Son los kilos de su pesada" else "Suma de sus $pesadaCount pesadas"
                else -> Weight.parseGrams(form.total)?.let { "= ${Weight.format(it)}" }
            },
            modifier = Modifier.fillMaxWidth().testTag("harvest-total"),
        )

        MoSectionHeader("Parcelas de origen")
        if (form.automatic) {
            // CR-010 (note 2): the union of its Pesadas' Parcels, or the whole Farm; never a split.
            Text(
                if (form.parcelIds.isEmpty()) {
                    UNKNOWN_DAY_ORIGIN
                } else {
                    context?.parcels?.filter { it.parcelId in form.parcelIds }?.joinToString { it.name }
                        ?.ifEmpty { null } ?: "Toda la finca"
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("harvest-day-parcels"),
            )
            Text(
                "Salen de sus pesadas. Los kilos de cada parcela están en cada pesada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )
        } else if (context == null) {
            Text("Elige primero la finca.", color = MoTextSecondary)
        } else if (context.parcels.isEmpty()) {
            Text("Esta campaña no tiene parcelas.", color = MoTextSecondary)
        }
        if (!form.automatic) context?.parcels?.forEach { parcel ->
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

        if (form.parcelIds.size > 1 && context != null && !kilosFromPesadas) {
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

        var moreDetails by rememberSaveable { mutableStateOf(false) }
        if (form.workers.isNotBlank()) Text("Personas trabajando (histórico): ${form.workers}", color = MoTextSecondary)
        if (form.machinery.isNotBlank()) Text("Maquinaria (histórico): ${form.machinery}", color = MoTextSecondary)
        if (moreDetails || form.collectionMethod != null || form.notes.isNotBlank()) {
            MoSelectField("Método de recogida", form.collectionMethod?.label() ?: "Sin indicar", { picker = "method" }, Modifier.testTag("harvest-method"))
            MoTextField(form.notes, { form = form.copy(notes = it) }, "Notas", singleLine = false, modifier = Modifier.fillMaxWidth())
        } else MoTertiaryButton("Más detalles", { moreDetails = true }, Modifier.testTag("harvest-more-details"))

        MoPrimaryButton(
            saveText,
            { onSave(form) },
            modifier = Modifier.fillMaxWidth().testTag("save-harvest"),
            enabled = !isSaving,
        )
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
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

private const val DAY_LOAD_TIMEOUT_MS = 10_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarvestDetailRoute(
    harvestId: UUID,
    persistence: LocalPersistence,
    clock: AppClock,
    onDeleted: () -> Unit,
    onAddPesada: (UUID) -> Unit = {},
    onPesadaSelected: (UUID) -> Unit = {},
    onExpenseSelected: (UUID) -> Unit = {},
    /** #365: the day's resource to open on («labour» from Cuaderno → Jornal). */
    initialResource: String? = null,
) {
    val viewModel: HarvestDetailViewModel = viewModel(
        key = "harvest-$harvestId",
        factory = viewModelFactory {
            initializer {
                HarvestDetailViewModel(
                    harvestId, persistence.harvestRepository, clock, persistence.deliveryRepository, persistence.labourRepository,
                    persistence.equipmentRepository, persistence.machineRepository, persistence.expenseRepository,
                    persistence.dayCostRepository,
                )
            }
        },
    )
    val loaded by viewModel.state.collectAsStateWithLifecycle()
    // Device check (build 683): a day that never finishes loading is explained, never an endless
    // spinner. If it arrives later it is shown as usual.
    var slow by remember(harvestId) { mutableStateOf(false) }
    LaunchedEffect(harvestId, loaded.isLoading) {
        if (loaded.isLoading) {
            delay(DAY_LOAD_TIMEOUT_MS)
            slow = true
            Log.w(LOG_TAG, "Harvest day $harvestId still loading after $DAY_LOAD_TIMEOUT_MS ms")
        }
    }
    val state = if (loaded.isLoading && slow) {
        loaded.copy(isLoading = false, error = "Está tardando más de lo normal. Vuelve atrás y ábrelo de nuevo.")
    } else {
        loaded
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    // Phase 19F: "Guardar y añadir foto" opens the new Expense, where its ticket is attached.
    LaunchedEffect(state.openExpenseId) {
        state.openExpenseId?.let { id ->
            viewModel.expenseOpened()
            onExpenseSelected(id)
        }
    }
    var labourPerson by rememberSaveable { mutableStateOf<String?>(null) }
    HarvestDetailScreen(
        state = state,
        onUpdate = viewModel::update,
        onDelete = viewModel::delete,
        onEditorClosed = viewModel::clearFormErrors,
        onAddPesada = { onAddPesada(harvestId) },
        onPesadaSelected = onPesadaSelected,
        labourActions = LabourActions(
            onSaveCrew = viewModel::recordCrew,
            onAddWorker = viewModel::addWorker,
            onRemove = viewModel::removeLabour,
            onUpdate = viewModel::updateLabour,
            onPerson = { labourPerson = it.toString() },
            onClear = viewModel::clearLabourMessages,
        ),
        onSaveEquipment = viewModel::saveEquipment,
        onAddCost = viewModel::addCost,
        onExpenseSelected = onExpenseSelected,
        onSaveRates = viewModel::saveRates,
        onPreferCalculated = viewModel::preferCalculated,
        onLinkCost = viewModel::linkCost,
        initialResource = initialResource,
        attachmentContent = {
            AttachmentsRoute(
                owner = AttachmentOwner(AttachmentOwnerType.HARVEST, harvestId),
                persistence = persistence,
                title = "Fotos y documentos",
            )
        },
    )
    val campaign = state.harvest?.campaignId
    if (labourPerson != null && campaign != null) {
        ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { labourPerson = null }) {
            LabourPaymentsRoute(campaign, persistence, UUID.fromString(labourPerson)) { labourPerson = null }
        }
    }
}
/** S72 — Detalle de Jornada; legacy kilos remain readable when no Pesadas exist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarvestDetailScreen(
    state: HarvestDetailUiState,
    onUpdate: (HarvestForm) -> Unit,
    onDelete: () -> Unit,
    onEditorClosed: () -> Unit = {},
    attachmentContent: @Composable () -> Unit = {},
    onAddPesada: () -> Unit = {},
    onPesadaSelected: (UUID) -> Unit = {},
    labourActions: LabourActions = LabourActions(),
    onSaveEquipment: (List<EquipmentDraftLine>) -> Unit = {},
    onAddCost: (JornadaExpenseKind, Long, String?, Boolean, DayCostRole) -> Unit = { _, _, _, _, _ -> },
    onExpenseSelected: (UUID) -> Unit = {},
    onSaveRates: (RecollectionRates) -> Unit = {},
    onPreferCalculated: (DayCostKind) -> Unit = {},
    onLinkCost: (UUID, DayCostRole) -> Unit = { _, _ -> },
    /**
     * #365: the resource detail open on arrival. Cuaderno → Jornal lands on the day's Jornales,
     * with «Registrar jornal» at hand, instead of on its Pesadas; the day stays one screen.
     */
    initialResource: String? = null,
) {
    var resourceDetail by rememberSaveable { mutableStateOf(initialResource) }
    var costVisible by rememberSaveable { mutableStateOf(false) }
    var ratesVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.ratesSaved) { if (state.ratesSaved > 0) ratesVisible = false }
    LaunchedEffect(state.costSaved) { if (state.costSaved > 0) costVisible = false }
    var equipmentVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.equipmentSaved) { if (state.equipmentSaved > 0) equipmentVisible = false }
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var labourVisible by rememberSaveable { mutableStateOf(false) }
    var editLabour by rememberSaveable { mutableStateOf<String?>(null) }
    // A saved set of jornales closes the sheet; its confirmation stays on the Jornada.
    LaunchedEffect(state.labourMessage) { if (state.labourMessage != null && state.labourMessage != "Persona añadida") { labourVisible = false; editLabour = null } }
    OnEachSave(state.saveCount) { editorVisible = false }

    Scaffold(Modifier.fillMaxSize().testTag("harvest-detail-root"), containerColor = MoCream, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            val harvest = state.harvest
            when {
                state.isLoading -> CircularProgressIndicator()
                harvest == null -> MoErrorState("Día de recolección no disponible", state.error ?: "No está guardado en este dispositivo.")
                else -> {
                    HarvestSummaryBlock(harvest, state.pesadas.size)
                    JornadaPesadas(state.pesadas, harvest.editable, onAddPesada, onPesadaSelected)
                    MoSectionHeader("Recursos del día")
                    val ledger = harvest.campaignId?.let { com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.of(it, state.costs, state.pesadas) }.orEmpty()
                    val labourSummary = com.isivoltpro.maginaolivo.domain.labour.LabourSummary.of(state.labour)
                    MoKpiMetric("Jornales", ledger.moneyLabel(com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket.LABOUR),
                        Modifier.fillMaxWidth().testTag("day-resource-labour"), icon = MoIcons.People, kind = MoKpiKind.JORNALES,
                        supportingText = when { state.labourReadFailed -> "No pudimos leer los jornales"; !state.labourLoaded -> "Cargando jornales…";
                            else -> listOfNotNull("${labourSummary.people} personas · ${labourSummary.label()} · Ver detalle",
                                labourCostNote(state.labour, state.costs)).joinToString("\n") },
                        onClick = { resourceDetail = "labour" })
                    MoKpiMetric("Maquinaria", ledger.moneyLabel(com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket.EQUIPMENT),
                        Modifier.fillMaxWidth().testTag("day-resource-equipment"), icon = MoIcons.Tractor, kind = MoKpiKind.MAQUINARIA,
                        supportingText = when { !state.equipmentLoaded -> "Cargando maquinaria…"; state.equipmentReadFailed -> "No pudimos leer la maquinaria";
                            else -> listOfNotNull("${state.equipment.sumOf { it.quantity }} equipos/usos · Ver detalle",
                                equipmentCostNote(state.equipment, state.costs)).joinToString("\n") }, onClick = { resourceDetail = "equipment" })
                    MoKpiMetric("Otros gastos", ledger.moneyLabel(com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket.OTHER),
                        Modifier.fillMaxWidth().testTag("day-resource-other"), icon = MoIcons.Euro, kind = MoKpiKind.COSTES,
                        supportingText = when { !state.costsLoaded -> "Cargando gastos…"; state.costsReadFailed -> "No pudimos leer los gastos";
                            else -> "Combustible, transporte, reparación · Ver gastos" }, onClick = { resourceDetail = "costs" })
                    MoSectionHeader("Resumen económico")
                    RecollectionTotalCards(ledger, true, state.pesadas.sumOf { it.netGrams }.takeIf { it > 0 }?.let(Weight::format),
                        // Codex #605: only with every source read; otherwise never presented as final.
                        com.isivoltpro.maginaolivo.domain.expense.RecollectionCostCompleteness.of(state.labour, state.equipment, state.costs)
                            .takeIf { state.labourLoaded && state.equipmentLoaded && state.costsLoaded &&
                                !state.labourReadFailed && !state.equipmentReadFailed && !state.costsReadFailed })
                    state.costError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    state.equipmentError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (harvest.editable) {
                        MoSecondaryButton(
                            "Editar día de recolección", { editorVisible = true },
                            Modifier.fillMaxWidth().testTag("edit-harvest"),
                            enabled = state.context != null && !state.isSaving,
                        )
                        // #457: deleting is offered only once the day is known to have no Pesadas.
                        val noPesadas = state.pesadasLoaded && !state.pesadasReadFailed && state.pesadas.isEmpty()
                        if (noPesadas) {
                            MoSecondaryButton(
                                "Eliminar día de recolección", { confirmDelete = true },
                                Modifier.fillMaxWidth().testTag("delete-harvest"),
                                enabled = !state.isSaving,
                            )
                        } else if (state.pesadas.isNotEmpty()) {
                            // #457: the day is there because it has Pesadas; it moves with them.
                            Text(
                                "Este día existe porque tiene pesadas. Para cambiarlo, corrige o mueve las pesadas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MoTextSecondary,
                                modifier = Modifier.testTag("harvest-delete-held"),
                            )
                        } else if (state.pesadasReadFailed) {
                            Text(
                                "No pudimos leer las pesadas de este día: vuelve a abrirlo para poder eliminarlo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("harvest-pesadas-unread"),
                            )
                        }
                    } else {
                        Text(
                            "La campaña está cerrada: este día de recolección forma parte del histórico y no se modifica.",
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
    if (resourceDetail != null && harvest != null) {
        ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { resourceDetail = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                when (resourceDetail) {
                    "labour" -> {
                    JornadaLabour(
                        labour = state.labour,
                        editable = harvest.editable,
                        message = state.labourMessage.takeUnless { labourVisible },
                        error = state.labourError.takeUnless { labourVisible },
                        onRegister = { labourActions.onClear(); resourceDetail = null; labourVisible = true },
                        onRemove = labourActions.onRemove,
                        onPerson = labourActions.onPerson,
                        onEdit = { labourActions.onClear(); resourceDetail = null; editLabour = it.toString() },
                        loaded = state.labourLoaded,
                        readFailed = state.labourReadFailed,
                    )
                    }
                    "equipment" -> {
                    JornadaEquipment(
                        lines = state.equipment,
                        editable = harvest.editable,
                        error = state.equipmentError.takeUnless { equipmentVisible },
                        onEdit = { resourceDetail = null; equipmentVisible = true },
                        loaded = state.equipmentLoaded,
                        readFailed = state.equipmentReadFailed,
                    )
                    }
                    "costs" -> {
                    JornadaCosts(
                        expenses = state.costs,
                        editable = harvest.editable,
                        error = state.costError.takeUnless { costVisible },
                        onAdd = { resourceDetail = null; costVisible = true },
                        onExpenseSelected = onExpenseSelected,
                        onPreferCalculated = onPreferCalculated,
                        onEditRates = state.rates?.let { { resourceDetail = null; ratesVisible = true } },
                        unlinked = state.unlinkedCosts,
                        onLink = onLinkCost,
                        labourPaid = state.labourPaid,
                        loaded = state.costsLoaded,
                        readFailed = state.costsReadFailed,
                    )
                    }
                }
                MoTertiaryButton("Cerrar", { resourceDetail = null }, Modifier.fillMaxWidth().testTag("resource-detail-close"))
            }
        }
    }
    if (editorVisible && harvest != null && context != null) {
        ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { editorVisible = false; onEditorClosed() }) {
            HarvestEditor(
                title = "Editar día de recolección",
                initial = harvest.toForm(),
                contexts = listOf(context),
                errors = state.formErrors,
                isSaving = state.isSaving,
                saveText = "Guardar cambios",
                onSave = onUpdate,
                onCancel = { editorVisible = false; onEditorClosed() },
                farmLocked = true,
                pesadaCount = state.pesadas.size,
            )
        }
    }
    val rates = state.rates
    if (ratesVisible && harvest != null && rates != null) {
        ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { ratesVisible = false }) {
            RatesSheet(
                rates = rates,
                isSaving = state.isSaving,
                error = state.ratesError,
                onSave = onSaveRates,
                onCancel = { ratesVisible = false },
            )
        }
    }
    if (costVisible && harvest != null) {
        val currencyContext = state.newCostCurrencyContext()
        ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { costVisible = false }) {
            CostSheet(
                currency = currencyContext.currency,
                currencyError = currencyContext.error,
                isSaving = state.isSaving,
                error = state.costError,
                onSave = { kind, amount, concept, openAfter -> onAddCost(kind, amount, concept, openAfter, DayCostRole.ADDITIVE) },
                onSaveWithRole = onAddCost,
                calculated = state.calculatedKinds(),
                labourPaid = state.labourPaid,
                onCancel = { costVisible = false },
            )
        }
    }
    if (equipmentVisible && harvest != null) {
        val equipmentCurrency = equipmentCurrencyContext(harvest.id, harvest.campaignId, state.equipment, state.costs, state.rates?.currency)
        ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { equipmentVisible = false }) {
            EquipmentSheet(
                current = state.equipment,
                machines = state.machines,
                rates = state.rates,
                currency = equipmentCurrency.currency ?: state.rates?.currency ?: "EUR",
                currencyError = equipmentCurrency.error,
                priceDate = harvest.harvestDate,
                isSaving = state.isSaving,
                onSave = onSaveEquipment,
                onCancel = { equipmentVisible = false },
            )
            state.equipmentError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = MoSpacing.screen)) }
        }
    }
    val editingLabour = state.labour.firstOrNull { it.id.toString() == editLabour }
    val labourCurrency = state.resolvedLabourCurrency()
    if (editingLabour != null && harvest != null && harvest.editable) {
        ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { if (!state.isSaving) { editLabour = null; labourActions.onClear() } }) {
            LabourPriceSheet(editingLabour, harvest.harvestDate, labourCurrency?.currency, state.isSaving, state.labourError, { labourActions.onUpdate(editingLabour.id, it) }, { editLabour = null; labourActions.onClear() }, labourCurrency?.error)
        }
    }
    if (labourVisible && harvest?.campaignId != null) {
        ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { if (!state.isSaving) { labourVisible = false; labourActions.onClear() } }) {
            if (labourCurrency?.currency == null) {
                Column(Modifier.padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                    Text(labourCurrency?.error ?: "La moneda de los jornales no está disponible.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("labour-currency-error"))
                    MoTertiaryButton("Cancelar", { labourVisible = false; labourActions.onClear() }, enabled = !state.isSaving)
                }
            } else LabourSheet(
                workers = state.workers,
                alreadyRecorded = state.labour.mapNotNull { it.workerId }.toSet(),
                harvestId = harvest.id,
                campaignId = harvest.campaignId,
                date = harvest.harvestDate,
                rates = state.rates,
                currency = labourCurrency.currency,
                isSaving = state.isSaving,
                error = state.labourError,
                onSaveCrew = labourActions.onSaveCrew,
                onAddWorker = labourActions.onAddWorker,
                onCancel = { labourVisible = false; labourActions.onClear() },
            )
        }
    }
    // A Pesada that arrives while the confirmation is open closes it (#457).
    if (confirmDelete && state.pesadas.isEmpty()) {
        ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { confirmDelete = false }) {
            MoConfirmationSheet(
                title = "Eliminar día de recolección",
                body = listOfNotNull(
                    // #457: only a day without Pesadas can be removed.
                    "Estos kilos dejarán de contar en la campaña.",
                    // Phase 19D: its jornales only describe this Jornada and go with it.
                    if (state.labour.isNotEmpty()) "Sus jornales se quitan con ella." else null,
                    if (state.equipment.isNotEmpty()) "Su maquinaria anotada también." else null,
                    if (state.costs.isNotEmpty()) "Sus gastos siguen en Gastos, sin día de recolección." else null,
                    "Esta acción no se puede deshacer.",
                ).joinToString(" "),
                confirmText = "Eliminar",
                onConfirm = { confirmDelete = false; onDelete() },
                onCancel = { confirmDelete = false },
                modifier = Modifier.padding(horizontal = MoSpacing.md).testTag("harvest-confirmation"),
            )
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

/** Phase 19D: what the Jornada screen can do with its jornales. */
data class LabourActions(
    val onSaveCrew: (com.isivoltpro.maginaolivo.domain.labour.CrewDraft) -> Unit = {},

    val onAddWorker: (String) -> Unit = {},
    val onRemove: (UUID) -> Unit = {},
    val onClear: () -> Unit = {},
    val onUpdate: (UUID, com.isivoltpro.maginaolivo.domain.labour.LabourChange) -> Unit = { _, _ -> },
    val onPerson: (UUID) -> Unit = {},
)

/**
 * Phase 19B — the Pesadas of this Jornada. Each shows its own cooperative, ticket and hour;
 * the Jornada only lists them. "Añadir pesada" opens the Pesada form on this Jornada.
 */
@Composable
private fun JornadaPesadas(
    pesadas: List<Delivery>,
    editable: Boolean,
    onAddPesada: () -> Unit,
    onPesadaSelected: (UUID) -> Unit,
) {
    MoSectionHeader("Pesadas del día")
    if (pesadas.isEmpty()) {
        Text(
            "Aún no hay pesadas enlazadas. Añádelas según lleguen: cada una con su cooperativa y su vale.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoTextSecondary,
            modifier = Modifier.testTag("jornada-no-pesadas"),
        )
    } else {
        val summary = DeliverySummary.of(pesadas)
        summary.fatYield?.let {
            val coverage = summary.coveragePercent(it)
            Text(
                if (coverage == null) {
                    "Rendimiento del día ${Percent.format(it.hundredths)} · cobertura no disponible"
                } else {
                    "Rendimiento del día ${Percent.format(it.hundredths)} · sobre el $coverage % de los kilos"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
                modifier = Modifier.testTag("jornada-pesadas-summary"),
            )
        }
        pesadas.forEach { pesada ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPesadaSelected(pesada.id) }
                    .testTag("jornada-pesada"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(pesada.destinationName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(
                            pesada.deliveryTime?.toString(),
                            pesada.origin?.label,
                            (pesada.ticketNumber ?: pesada.deliveryNumber)?.let { "Vale $it" },
                            pesada.analysis?.fatYieldHundredths?.let { "Rend. ${Percent.format(it)}" } ?: "Rend. pendiente",
                        ).ifEmpty { listOf(DATE_FORMAT.format(pesada.deliveryDate)) }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MoTextSecondary,
                    )
                }
                Text(Weight.format(pesada.netGrams), style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
            }
        }
    }
    if (editable) {
        MoPrimaryButton("Añadir pesada", onAddPesada, Modifier.fillMaxWidth().testTag("jornada-add-pesada"))
    }
}

@Composable
private fun HarvestSummaryBlock(harvest: Harvest, pesadaCount: Int) {
    Text(
        "Recolección del ${DATE_FORMAT.format(harvest.harvestDate)}",
        style = MaterialTheme.typography.headlineMedium,
        color = MoOliveDark,
    )
    Text(
        listOfNotNull(harvest.farmName, harvest.campaignName).joinToString(" · "),
        style = MaterialTheme.typography.bodyLarge,
        color = MoTextSecondary,
    )
    MoMetricCard(
        if (pesadaCount == 0 && !harvest.awaitingPesadas) "Kilos históricos" else "Kilos pesados",
        if (harvest.awaitingPesadas) PENDING_KILOS else Weight.format(harvest.totalGrams),
        Modifier.fillMaxWidth().testTag("harvest-total-value"),
        supportingText = when (pesadaCount) {
            0 -> if (harvest.awaitingPesadas) "Serán la suma de sus pesadas" else harvest.allocationMode.label()
            1 -> "Los de su pesada"
            else -> "Suma de sus $pesadaCount pesadas"
        },
    )
    MoSectionHeader("Parcelas de origen")
    if (harvest.shares.isEmpty()) {
        Text(
            // A legacy record (before Room v7) has no origin rows and expects no Pesada.
            if (harvest.automatic && harvest.awaitingPesadas) "$UNKNOWN_DAY_ORIGIN: llegará con sus pesadas." else UNKNOWN_DAY_ORIGIN,
            style = MaterialTheme.typography.bodyLarge,
            color = MoTextSecondary,
            modifier = Modifier.testTag("harvest-origin-unknown"),
        )
    }
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
    var detailsVisible by rememberSaveable { mutableStateOf(false) }
    if (detailsVisible) { DetailValue("Método de recogida", harvest.collectionMethod?.label()); harvest.notes?.let { DetailValue("Notas", it) } }
    else MoTertiaryButton("Más detalles", { detailsVisible = true }, Modifier.testTag("day-more-details"))
    harvest.workerCount?.let { DetailValue("Personas trabajando (histórico)", it.toString()) }
    harvest.machineryText?.let { DetailValue("Maquinaria (histórico)", it) }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
        Text(value ?: "Sin registrar", style = MaterialTheme.typography.bodyLarge)
    }
}

/** Spec §7: a Jornada without Pesadas has no kilos yet; never a fake zero. */
internal const val PENDING_KILOS = "Kg pendientes de pesada"
