package com.isivoltpro.maginaolivo.feature.deliveries

import com.isivoltpro.maginaolivo.ui.components.OnEachSave
import com.isivoltpro.maginaolivo.domain.delivery.YieldStatus
import com.isivoltpro.maginaolivo.domain.delivery.PesadaSearch
import com.isivoltpro.maginaolivo.domain.delivery.PesadaQuery
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.ocr.OcrStatus
import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsRoute
import com.isivoltpro.maginaolivo.feature.attachments.createCaptureUri
import com.isivoltpro.maginaolivo.feature.expenses.Choice
import com.isivoltpro.maginaolivo.feature.expenses.ChoiceSheet
import com.isivoltpro.maginaolivo.feature.expenses.DATE_FORMAT
import com.isivoltpro.maginaolivo.feature.expenses.tone
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoDateInputField
import com.isivoltpro.maginaolivo.ui.components.MoBottomActionSheet
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
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

@Composable
fun DeliveriesRoute(
    persistence: LocalPersistence,
    clock: AppClock,
    onDeliverySelected: (UUID) -> Unit,
    onTicketSelected: (UUID) -> Unit,
    jornadaId: UUID? = null,
    initialStatus: YieldStatus? = null,
    onAddYield: (UUID) -> Unit = onDeliverySelected,
    /** CR-011 §4/§14: «Cuaderno → Pesada» — the form opens at once, on the Cuaderno's Farm. */
    presetFarmId: UUID? = null,
    /** CR-011 §14: the Cuaderno's Parcel, preselected when it is in that Farm's campaign. */
    presetParcelId: UUID? = null,
    /** #511: consult exactly one Campaign's Pesadas; closed Campaigns are read-only here. */
    campaignId: UUID? = null,
) {
    val viewModel: DeliveriesViewModel = viewModel(
        key = "deliveries-${campaignId ?: jornadaId ?: initialStatus ?: presetFarmId ?: "all"}",
        factory = viewModelFactory {
            initializer {
                DeliveriesViewModel(
                    persistence.deliveryRepository,
                    persistence.documentOcrRepository,
                    persistence.organizationRepository,
                    clock,
                    persistence.harvestRepository,
                    persistence.attachmentRepository,
                    campaignId = campaignId,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.openedTicketId) {
        state.openedTicketId?.let { id ->
            viewModel.ticketOpened()
            onTicketSelected(id)
        }
    }
    DeliveriesScreen(
        state = state,
        today = clock.today(ZoneId.systemDefault()),
        onCreate = { form -> viewModel.create(form) },
        onProblem = viewModel::reportProblem,
        onRetryContexts = viewModel::retryContexts,
        onDeliverySelected = onDeliverySelected,
        onTicketSelected = onTicketSelected,
        onEditorClosed = viewModel::editorClosed,
        onCreateAndAddAnother = { form -> viewModel.create(form, again = true) },
        jornadaId = jornadaId,
        initialStatus = initialStatus,
        onAddYield = onAddYield,
        presetFarmId = presetFarmId,
        presetParcelId = presetParcelId,
        allowCreate = campaignId == null || state.contexts.isNotEmpty(),
    )
}

/**
 * S80 — Pesadas. Weighed kilos per Campaign with the yield weighted by kilos and the
 * share of kilos it covers. A Delivery without analysis counts in kilos, never in yield.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveriesScreen(
    state: DeliveriesUiState,
    today: LocalDate,
    onCreate: (DeliveryForm) -> Unit,
    onProblem: (String) -> Unit,
    onRetryContexts: () -> Unit = {},
    onDeliverySelected: (UUID) -> Unit,
    onTicketSelected: (UUID) -> Unit,
    onEditorClosed: () -> Unit = {},
    onCreateAndAddAnother: ((DeliveryForm) -> Unit)? = null,
    jornadaId: UUID? = null,
    initialStatus: YieldStatus? = null,
    onAddYield: (UUID) -> Unit = onDeliverySelected,
    presetFarmId: UUID? = null,
    presetParcelId: UUID? = null,
    /** False for a scoped closed Campaign: history remains visible, creation disappears. */
    allowCreate: Boolean = true,
) {
    // Phase 19C: find a Pesada by its ticket days later, and the ones still without yield.
    var searchText by rememberSaveable { mutableStateOf("") }
    var statusName by rememberSaveable { mutableStateOf(initialStatus?.name) }
    var cooperative by rememberSaveable { mutableStateOf<String?>(null) }
    // Opened from a Jornada ("Añadir pesada") or from the Cuaderno's «Pesada», the editor starts open.
    var editorVisible by rememberSaveable { mutableStateOf(jornadaId != null || presetFarmId != null) }
    var receiptVisible by rememberSaveable { mutableStateOf(false) }
    // #342: what was typed in Nueva pesada before «Añadir foto del recibo». The editor reopens with
    // it, plus the picked receipt; cancelling the camera reopens it unchanged.
    var ticketSeed by remember { mutableStateOf<DeliveryForm?>(null) }
    OnEachSave(state.saveCount) { editorVisible = false; ticketSeed = null }
    // A saved Pesada («Guardar y añadir otra») starts the next one from itself, not from the seed.
    LaunchedEffect(state.nextFormGeneration) { ticketSeed = null }

    Scaffold(Modifier.fillMaxSize().testTag("deliveries-root"), containerColor = MoSurfaceTokens.appBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.md))
            Text("Pesadas", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "Lo que llevas a la cooperativa o almazara. El rendimiento se añade cuando llega el análisis.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoSurfaceTokens.secondaryText,
            )
            val canRecord = state.contexts.isNotEmpty() && !state.contextsReadFailed && !state.isSaving
            // #342: kilos are typed and confirmed by the farmer; there is no ticket-reading entry.
            // #511: a closed Campaign's scoped history never shows a write CTA.
            if (allowCreate) {
                MoPrimaryButton(
                    "+ Nueva pesada",
                    { editorVisible = true },
                    Modifier.fillMaxWidth().testTag("add-delivery"),
                    enabled = canRecord,
                )
            }
            when {
                state.contextsReadFailed -> {
                    Text(
                        "No hemos podido comprobar tus campañas. Reinténtalo antes de registrar la pesada.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("delivery-context-error"),
                    )
                    MoSecondaryButton(
                        "Reintentar",
                        onRetryContexts,
                        Modifier.fillMaxWidth().testTag("delivery-context-retry"),
                    )
                }
                state.contextsLoaded && state.contexts.isEmpty() -> {
                    Text(
                        "Para registrar una pesada, una finca necesita una campaña activa o en recolección.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoSurfaceTokens.secondaryText,
                        modifier = Modifier.testTag("delivery-no-campaign"),
                    )
                }
            }
            if (state.isSaving) Text("Guardando…", color = MoSurfaceTokens.secondaryText)
            state.message?.let { Text(it, color = MoSurfaceTokens.secondaryText, modifier = Modifier.testTag("deliveries-message")) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("deliveries-error")) }

            if (state.openTickets.isNotEmpty()) {
                MoSectionHeader("Vales por revisar")
                state.openTickets.forEach { ticket ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onTicketSelected(ticket.id) }.testTag("ticket-row"),
                        shape = MoShape.card,
                        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
                    ) {
                        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                            Text("Vale de entrega", style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                            Text(
                                DATE_FORMAT.format(ticket.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MoSurfaceTokens.secondaryText,
                            )
                            MoStatusChip("Sin confirmar · no cuenta", tone = ticket.status.tone())
                        }
                    }
                }
            }

            when {
                state.isLoading -> CircularProgressIndicator()
                state.deliveries.isEmpty() -> MoEmptyState(
                    "Aún no hay pesadas",
                    "Registra cada pesada con sus kilos netos. Si te dan vale, puedes adjuntar su foto.",
                    icon = MoIcons.Delivery,
                )
                else -> {
                    state.campaigns.forEach { campaign -> CampaignDeliveriesCard(campaign) }
                    MoSectionHeader("Pesadas")
                    val status = YieldStatus.entries.firstOrNull { it.name == statusName }
                    val query = PesadaQuery(text = searchText, status = status, cooperative = cooperative)
                    MoTextField(
                        searchText, { searchText = it }, "Buscar nº de pesada o vale",
                        modifier = Modifier.fillMaxWidth().testTag("pesada-search"),
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    ) {
                        val pending = state.deliveries.count { PesadaSearch.statusOf(it) == YieldStatus.PENDING }
                        FilterChip(
                            selected = status == YieldStatus.PENDING,
                            onClick = { statusName = if (status == YieldStatus.PENDING) null else YieldStatus.PENDING.name },
                            label = { Text("Pendiente de rendimiento ($pending)") },
                            modifier = Modifier.testTag("pesada-filter-pending"),
                        )
                        FilterChip(
                            selected = status == YieldStatus.WITH_YIELD,
                            onClick = { statusName = if (status == YieldStatus.WITH_YIELD) null else YieldStatus.WITH_YIELD.name },
                            label = { Text("Con rendimiento") },
                            modifier = Modifier.testTag("pesada-filter-with-yield"),
                        )
                    }
                    val cooperatives = state.deliveries.distinctBy { PesadaSearch.cooperativeKey(it) }
                    if (cooperatives.size > 1) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                        ) {
                            cooperatives.forEach { delivery ->
                                val key = PesadaSearch.cooperativeKey(delivery)
                                FilterChip(
                                    selected = cooperative == key,
                                    onClick = { cooperative = if (cooperative == key) null else key },
                                    label = { Text(delivery.destinationName) },
                                    modifier = Modifier.testTag("pesada-filter-cooperative"),
                                )
                            }
                        }
                    }
                    val found = PesadaSearch.filter(state.deliveries, query)
                    if (found.isEmpty()) {
                        Text(
                            when {
                                searchText.isNotBlank() -> "Ninguna pesada con ese número. Revisa el vale o quita filtros."
                                status == YieldStatus.PENDING -> "Todas las pesadas tienen su rendimiento."
                                else -> "Ninguna pesada con estos filtros."
                            },
                            color = MoSurfaceTokens.secondaryText,
                            modifier = Modifier.testTag("pesada-search-empty"),
                        )
                    }
                    found.forEach { delivery ->
                        DeliveryRow(delivery, onAddYield = { onAddYield(delivery.id) }) { onDeliverySelected(delivery.id) }
                    }
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    val jornada = jornadaId?.let { id -> state.jornadas.firstOrNull { it.id == id } }
    // Wait for the Jornada before opening its editor, so its Farm and day are preset.
    // Codex #377: a Jornada's editor waits until that Jornada is read (or known to be missing),
    // so it never opens on today or another Farm first.
    if (editorVisible && (jornadaId == null || jornada != null || state.jornadasLoaded)) {
        val start = remember(jornada?.id, state.contexts.size) {
            if (jornada != null) {
                DeliveryForm(
                    farmId = jornada.farmId,
                    date = jornada.harvestDate.toString(),
                    parcelIds = jornada.shares.map { it.parcelId },
                )
            } else {
                val farmId = presetFarmId ?: state.contexts.singleOrNull()?.farmId
                DeliveryForm(
                    farmId = farmId,
                    date = today.toString(),
                    parcelIds = presetOriginParcels(state.contexts, farmId, presetParcelId),
                )
            }
        }
        ModalBottomSheet(onDismissRequest = { editorVisible = false; ticketSeed = null; onEditorClosed() }) {
            val next = state.nextForm
            key(state.nextFormGeneration) {
                if (next != null) {
                    Text(
                        "Pesada guardada. Registra la siguiente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MoOliveDark,
                        modifier = Modifier.padding(horizontal = MoSpacing.screen).testTag("delivery-saved-next"),
                    )
                }
                // #342: a receipt that could not be kept is said inside the sheet the farmer is using.
                state.error?.takeIf { next != null }?.let { warning ->
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = MoSpacing.screen).testTag("delivery-sheet-warning"),
                    )
                }
                DeliveryEditor(
                    title = "Registrar pesada",
                    initial = ticketSeed ?: next ?: start,
                    contexts = state.contexts,
                    destinations = state.destinations,
                    errors = state.formErrors,
                    isSaving = state.isSaving,
                    saveText = "Guardar pesada",
                    onSave = onCreate,
                    onCancel = { editorVisible = false; ticketSeed = null; onEditorClosed() },
                    onAddReceipt = { typed -> ticketSeed = typed; editorVisible = false; receiptVisible = true },
                    onSaveAndAddAnother = onCreateAndAddAnother,
                    // #373/#375: a Farm chosen on the way here (Finca, Campaña, Parcela, Jornada) is
                    // context, not a question again. Only a global entry offers the Farm picker.
                    farmLocked = presetFarmId != null || jornada != null,
                    contextLoading = !state.contextsLoaded,
                )
            }
        }
    }
    if (receiptVisible) {
        ReceiptCaptureSheet(
            onPicked = { uri ->
                receiptVisible = false
                ticketSeed = ticketSeed?.copy(receiptUri = uri)
                editorVisible = true
            },
            onProblem = { message ->
                receiptVisible = false
                onProblem(message)
                editorVisible = true
            },
            onDismiss = {
                receiptVisible = false
                editorVisible = true
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptCaptureSheet(onPicked: (String) -> Unit, onProblem: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = pendingCapture
        pendingCapture = null
        if (saved && target != null) onPicked(target)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        MoBottomActionSheet(
            title = "Foto del recibo",
            body = "Se guarda como adjunto de esta pesada. Los kilos y demás datos son los que escribes tú.",
            modifier = Modifier.padding(horizontal = MoSpacing.md).testTag("receipt-capture-sheet"),
        ) {
            MoPrimaryButton(
                "Hacer foto",
                {
                    val target = runCatching { createCaptureUri(context) }.getOrNull()
                    if (target == null) {
                        onProblem("No se pudo preparar la cámara")
                    } else {
                        pendingCapture = target.toString()
                        try {
                            camera.launch(target)
                        } catch (error: ActivityNotFoundException) {
                            pendingCapture = null
                            onProblem("No hay ninguna cámara disponible")
                        }
                    }
                },
                Modifier.fillMaxWidth().testTag("receipt-camera"),
            )
            MoSecondaryButton(
                "Elegir PDF o imagen",
                {
                    try {
                        picker.launch(AttachmentKind.PICKER_MIME_TYPES)
                    } catch (error: ActivityNotFoundException) {
                        onProblem("No hay ningún selector de archivos disponible")
                    }
                },
                Modifier.fillMaxWidth().testTag("receipt-picker"),
            )
        }
        Spacer(Modifier.height(MoSpacing.md))
    }
}

@Composable
private fun CampaignDeliveriesCard(campaign: CampaignDeliveries) {
    val summary = campaign.summary
    Card(
        modifier = Modifier.fillMaxWidth().testTag("campaign-deliveries"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            Text(
                listOfNotNull(campaign.farmName, campaign.campaignName).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = MoOliveDark,
            )
            Text(
                summary.deliveredGrams?.let(Weight::format) ?: "No disponible",
                style = MaterialTheme.typography.headlineSmall,
                color = MoOlivePrimary,
                modifier = Modifier.testTag("campaign-delivered-total"),
            )
            Text(
                "${summary.deliveryCount} ${if (summary.deliveryCount == 1) "pesada" else "pesadas"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MoSurfaceTokens.secondaryText,
            )
            YieldLine("Rendimiento graso", summary.fatYield?.hundredths, summary.coveragePercent(summary.fatYield), "campaign-fat-yield")
            YieldLine(
                "Rendimiento industrial",
                summary.industrialYield?.hundredths,
                summary.coveragePercent(summary.industrialYield),
                "campaign-industrial-yield",
            )
        }
    }
}

@Composable
private fun YieldLine(label: String, hundredths: Int?, coverage: Int?, tag: String) {
    Text(
        if (hundredths == null) {
            "$label: sin análisis todavía"
        } else if (coverage == null) {
            "$label: ${Percent.format(hundredths)} ponderado · cobertura no disponible"
        } else {
            "$label: ${Percent.format(hundredths)} ponderado · con análisis el $coverage % de los kilos"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MoSurfaceTokens.secondaryText,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun DeliveryRow(delivery: Delivery, onAddYield: (() -> Unit)? = null, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("delivery-row"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
    ) {
        // CR-011 §23: icon + what the farmer recognises first (kg · destination), then when.
        Row(
            Modifier.fillMaxWidth().padding(MoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            MoIconBadge(MoIcons.Delivery)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
                Text(
                    listOfNotNull(Weight.format(delivery.netGrams), delivery.destinationName).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MoInk,
                )
                Text(
                    listOfNotNull(
                        DATE_FORMAT.format(delivery.deliveryDate),
                        delivery.deliveryTime?.toString(),
                        delivery.origin?.label,
                        delivery.ticketNumber?.let { "vale $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoSurfaceTokens.secondaryText,
                )
                val analysis = delivery.analysis
                if (analysis?.fatYieldHundredths != null) {
                    MoStatusChip("Rendimiento ${Percent.format(analysis.fatYieldHundredths)}", tone = MoStatusTone.Success)
                } else {
                    MoStatusChip("Rendimiento pendiente", tone = MoStatusTone.Warning)
                    if (onAddYield != null && delivery.analysis == null) {
                        MoTertiaryButton("Añadir rendimiento", onAddYield, Modifier.testTag("pesada-add-yield"))
                    }
                }
            }
        }
    }
}

/**
 * S81 — Nueva/Editar pesada. The destination is a saved cooperative or mill, or a name
 * typed by hand; origin Parcels come from the running Campaign, and a mixed load keeps
 * its kilos unallocated unless the person knows them.
 */
@Composable
internal fun DeliveryEditor(
    title: String,
    initial: DeliveryForm,
    contexts: List<HarvestContext>,
    destinations: List<Organization>,
    errors: DeliveryFormErrors,
    isSaving: Boolean,
    saveText: String,
    onSave: (DeliveryForm) -> Unit,
    onCancel: () -> Unit,
    farmLocked: Boolean = false,
    subtitle: String = "Se guardará primero en este dispositivo.",
    scrollable: Boolean = true,
    extraActions: @Composable () -> Unit = {},
    /** #342: «Añadir foto del recibo» from Nueva pesada — an optional attachment, never read. */
    onAddReceipt: ((DeliveryForm) -> Unit)? = null,
    onSaveAndAddAnother: ((DeliveryForm) -> Unit)? = null,
    /** Codex #377: the running campaigns are still being read; the context is not known yet. */
    contextLoading: Boolean = false,
) {
    var form by remember(initial) { mutableStateOf(initial) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val context = contexts.firstOrNull { it.farmId == form.farmId }
    val scrolling = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier

    Column(
        Modifier.fillMaxWidth().then(scrolling).padding(horizontal = MoSpacing.screen).testTag("delivery-editor"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MoSurfaceTokens.secondaryText)
        if (farmLocked) {
            // #373/#375: «Salinillas · Campaña 2026-2027» as fixed context, never a selector.
            Text(
                when {
                    context != null -> pesadaContextLine(context.farmName, context.campaignName)
                    contextLoading -> "Cargando la campaña…"
                    else -> PESADA_NO_RUNNING_CAMPAIGN
                },
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    context != null -> MoOliveDark
                    contextLoading -> MoSurfaceTokens.secondaryText
                    else -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.testTag("delivery-context"),
            )
        } else {
            MoSelectField("Finca", context?.farmName ?: "Elige la finca", { picker = "farm" }, Modifier.testTag("delivery-farm"))
        }
        errors.farm?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!farmLocked) {
            context?.let { Text("Campaña ${it.campaignName}", style = MaterialTheme.typography.bodyMedium, color = MoSurfaceTokens.secondaryText) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            Box(Modifier.weight(2f)) {
                MoDateInputField(
                    form.date, { form = form.copy(date = it) }, "Fecha",
                    isError = errors.date != null, supportingText = errors.date,
                    modifier = Modifier.fillMaxWidth().testTag("delivery-date"),
                )
            }
            MoTextField(
                form.time, { form = form.copy(time = it) }, "Hora",
                isError = errors.time != null, supportingText = errors.time,
                modifier = Modifier.weight(1f).testTag("delivery-time"),
            )
        }
        // CR-010 §4: the day of recolección is found or created on saving; nobody picks it.
        if (context != null) {
            Text(
                "Se guarda en el día de recolección de su fecha.",
                style = MaterialTheme.typography.bodyMedium,
                color = MoSurfaceTokens.secondaryText,
                modifier = Modifier.testTag("delivery-day-note"),
            )
        }
        val destination = form.destinationShown(destinations)
        MoSelectField(
            "Cooperativa o almazara", destination.name ?: "Escribir a mano", { picker = "destination" },
            Modifier.testTag("delivery-destination"),
        )
        destination.currentName?.let { now ->
            Text(
                "Ahora: $now",
                style = MaterialTheme.typography.bodySmall,
                color = MoSurfaceTokens.secondaryText,
                modifier = Modifier.testTag("delivery-destination-now"),
            )
        }
        if (destination.name == null) {
            MoTextField(
                form.destinationText, { form = form.copy(destinationText = it) }, "Nombre del destino",
                isError = errors.destination != null, supportingText = errors.destination,
                modifier = Modifier.fillMaxWidth().testTag("delivery-destination-text"),
            )
        }
        MoTextField(
            form.net, { form = form.copy(net = it) }, "Kilos pesados",
            isError = errors.net != null,
            supportingText = errors.net ?: Weight.parseGrams(form.net)?.let { "= ${Weight.format(it)}" },
            modifier = Modifier.fillMaxWidth().testTag("delivery-net"),
        )
        // Issue #254 (CODEX-3): every Pesada says where its olives were picked.
        Text("Origen de la aceituna", style = MaterialTheme.typography.labelLarge, color = MoSurfaceTokens.secondaryText)
        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            PesadaOrigin.entries.forEach { option ->
                FilterChip(
                    selected = form.origin == option,
                    onClick = { form = form.copy(origin = option) },
                    label = { Text(option.label) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("delivery-origin-${option.name.lowercase()}"),
                )
            }
        }
        errors.origin?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("delivery-origin-error")) }
        MoTextField(
            form.ticketNumber, { form = form.copy(ticketNumber = it) }, "Nº de vale",
            modifier = Modifier.fillMaxWidth().testTag("delivery-ticket-number"),
        )

        MoSectionHeader("Parcelas de origen")
        if (context == null) Text("Elige primero la finca.", color = MoSurfaceTokens.secondaryText)
        context?.parcels?.forEach { parcel ->
            val checked = parcel.parcelId in form.parcelIds
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delivery-parcel-option")
                    .clickable { form = form.toggle(parcel.parcelId, !checked) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, { value -> form = form.toggle(parcel.parcelId, value) })
                Text(parcel.name)
            }
        }
        errors.parcels?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("delivery-parcels-error")) }
        if (form.parcelIds.size > 1 && context != null) {
            MoSectionHeader("Reparto entre parcelas")
            SplitOption(
                "No conozco el reparto exacto",
                "Carga mezclada: solo cuenta el neto. Ninguna parcela recibe kilos inventados.",
                selected = !form.splitKnown,
                tag = "delivery-split-unknown",
            ) { form = form.copy(splitKnown = false) }
            SplitOption(
                "Conozco los kilos de cada parcela",
                "Deja en blanco las parcelas que no conozcas.",
                selected = form.splitKnown,
                tag = "delivery-split-known",
            ) { form = form.copy(splitKnown = true) }
            if (form.splitKnown) {
                form.parcelIds.forEach { parcelId ->
                    val name = context.parcels.firstOrNull { it.parcelId == parcelId }?.name.orEmpty()
                    MoTextField(
                        form.weights[parcelId].orEmpty(),
                        { value -> form = form.copy(weights = form.weights + (parcelId to value)) },
                        "Kilos de $name",
                        modifier = Modifier.fillMaxWidth().testTag("delivery-parcel-weight"),
                    )
                }
            }
        }
        // CR-011 §24: the rarely used fields wait under «Más detalles»; they open by themselves
        // when they already hold something (OCR, editing) or have an error, so nothing is hidden.
        val hasDetails = form.gross.isNotBlank() || form.tare.isNotBlank() || form.deliveryNumber.isNotBlank() ||
            form.notes.isNotBlank() || errors.gross != null
        var showDetails by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(hasDetails) { if (hasDetails) showDetails = true }
        if (showDetails || hasDetails) {
            Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                MoTextField(
                    form.gross, { form = form.copy(gross = it) }, "Bruto (opcional)",
                    isError = errors.gross != null, modifier = Modifier.weight(1f),
                )
                MoTextField(form.tare, { form = form.copy(tare = it) }, "Tara (opcional)", modifier = Modifier.weight(1f))
            }
            errors.gross?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            MoTextField(
                form.deliveryNumber, { form = form.copy(deliveryNumber = it) }, "Nº de albarán",
                modifier = Modifier.fillMaxWidth(),
            )
            MoTextField(form.notes, { form = form.copy(notes = it) }, "Notas", singleLine = false, modifier = Modifier.fillMaxWidth())
        } else {
            MoTertiaryButton("Más detalles", { showDetails = true }, Modifier.testTag("delivery-more-details"))
        }
        onAddReceipt?.let { add ->
            // #342: the receipt is evidence attached after saving; it never changes what is typed.
            if (form.receiptUri == null) {
                MoSecondaryButton("Añadir foto del recibo (opcional)", { add(form) }, Modifier.fillMaxWidth().testTag("delivery-add-receipt"))
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Foto del recibo añadida",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MoOliveDark,
                        modifier = Modifier.weight(1f).testTag("delivery-receipt-added"),
                    )
                    TextButton(onClick = { form = form.copy(receiptUri = null) }, modifier = Modifier.testTag("delivery-remove-receipt")) {
                        Text("Quitar")
                    }
                }
            }
        }
        MoPrimaryButton(saveText, { onSave(form) }, Modifier.fillMaxWidth().testTag("save-delivery"), enabled = !isSaving)
        onSaveAndAddAnother?.let { again ->
            MoSecondaryButton(
                "Guardar y añadir otra",
                { again(form) },
                Modifier.fillMaxWidth().testTag("save-delivery-again"),
                enabled = !isSaving,
            )
        }
        extraActions()
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
            "delivery-farm-sheet",
        )
        "destination" -> ChoiceSheet(
            "Cooperativa o almazara",
            listOf(Choice(null, "Escribir a mano")) + destinations.map { Choice(it.id.toString(), it.name) },
            form.destinationOrganizationId?.toString(),
            { key -> form = form.copy(destinationOrganizationId = key?.let(UUID::fromString)) },
            { picker = null },
            "delivery-destination-sheet",
        )
    }
}

private fun DeliveryForm.toggle(parcelId: UUID, selected: Boolean): DeliveryForm =
    if (selected) copy(parcelIds = (parcelIds + parcelId).distinct())
    else copy(parcelIds = parcelIds - parcelId, weights = weights - parcelId)

@Composable
private fun SplitOption(title: String, body: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
        }
    }
}

@Composable
fun DeliveryDetailRoute(
    deliveryId: UUID,
    persistence: LocalPersistence,
    clock: AppClock,
    onDeleted: () -> Unit,
    openYield: Boolean = false,
) {
    val viewModel: DeliveryDetailViewModel = viewModel(
        key = "delivery-$deliveryId",
        factory = viewModelFactory {
            initializer {
                DeliveryDetailViewModel(
                    deliveryId, persistence.deliveryRepository, persistence.organizationRepository, clock,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    DeliveryDetailScreen(
        state = state,
        onUpdate = viewModel::update,
        onDelete = viewModel::delete,
        onRecordYield = viewModel::recordYield,
        onRemoveYield = viewModel::removeYield,
        onEditorClosed = viewModel::clearFormErrors,
        openYield = openYield,
        attachmentContent = {
            AttachmentsRoute(
                owner = AttachmentOwner(AttachmentOwnerType.DELIVERY, deliveryId),
                persistence = persistence,
                title = "Vale y documentos",
            )
        },
    )
}

/**
 * S82 — Detalle de la pesada. The confirmed delivery on top, the later yield analysis below it
 * as a separate section: editing the analysis never edits the delivery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryDetailScreen(
    state: DeliveryDetailUiState,
    onUpdate: (DeliveryForm) -> Unit,
    onDelete: () -> Unit,
    onRecordYield: (YieldForm) -> Unit,
    onRemoveYield: () -> Unit,
    onEditorClosed: () -> Unit = {},
    attachmentContent: @Composable () -> Unit = {},
    openYield: Boolean = false,
) {
    // Phase 19C: "Añadir rendimiento" from the list opens the yield form in one tap.
    var sheet by rememberSaveable { mutableStateOf(if (openYield) "yield" else null) }
    OnEachSave(state.saveCount) { sheet = null }

    Scaffold(Modifier.fillMaxSize().testTag("delivery-detail-root"), containerColor = MoSurfaceTokens.appBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            val delivery = state.delivery
            when {
                state.isLoading -> CircularProgressIndicator()
                delivery == null -> MoErrorState("Pesada no disponible", state.error ?: "No está guardada en este dispositivo.")
                else -> {
                    DeliverySummaryBlock(delivery)
                    MoSectionHeader("Rendimiento")
                    val analysis = delivery.analysis
                    if (analysis == null) {
                        Text(
                            "Sin análisis todavía. La pesada cuenta en kilos; su rendimiento se añade cuando llegue.",
                            color = MoSurfaceTokens.secondaryText,
                            modifier = Modifier.testTag("delivery-yield-pending"),
                        )
                    } else {
                        DetailValue("Rendimiento graso", analysis.fatYieldHundredths?.let(Percent::format))
                        DetailValue("Rendimiento industrial", analysis.industrialYieldHundredths?.let(Percent::format))
                        DetailValue("Fecha del análisis", analysis.analysisDate?.let(DATE_FORMAT::format))
                    }
                    if (analysis == null || delivery.editable) {
                        MoSecondaryButton(
                            if (analysis == null) "Añadir rendimiento" else "Corregir rendimiento",
                            { sheet = "yield" },
                            Modifier.fillMaxWidth().testTag("edit-yield"),
                            enabled = !state.isSaving,
                        )
                    }
                    if (analysis != null && delivery.editable) {
                        MoSecondaryButton("Quitar rendimiento", { sheet = "remove-yield" }, Modifier.fillMaxWidth())
                    }
                    if (delivery.editable) {
                        MoSectionHeader("Pesada")
                        MoSecondaryButton(
                            "Editar pesada", { sheet = "edit" },
                            Modifier.fillMaxWidth().testTag("edit-delivery"),
                            enabled = state.context != null && !state.isSaving,
                        )
                        MoSecondaryButton(
                            "Eliminar pesada", { sheet = "delete" },
                            Modifier.fillMaxWidth().testTag("delete-delivery"),
                            enabled = !state.isSaving,
                        )
                    } else {
                        Text(
                            if (analysis == null) {
                                "La campaña está cerrada: la pesada forma parte del histórico. El rendimiento pendiente sí puede añadirse."
                            } else {
                                "La campaña está cerrada y el rendimiento ya está confirmado. Para corregir este histórico, reabre la campaña."
                            },
                            color = MoSurfaceTokens.secondaryText,
                            modifier = Modifier.testTag("delivery-read-only"),
                        )
                    }
                    state.message?.let { Text(it, color = MoSurfaceTokens.secondaryText) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    attachmentContent()
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    val delivery = state.delivery ?: return
    when (sheet) {
        "edit" -> state.context?.let { context ->
            ModalBottomSheet(onDismissRequest = { sheet = null; onEditorClosed() }) {
                DeliveryEditor(
                    title = "Editar pesada",
                    initial = delivery.toForm(),
                    contexts = listOf(context),
                    destinations = state.destinations,
                    errors = state.formErrors,
                    isSaving = state.isSaving,
                    saveText = "Guardar cambios",
                    onSave = onUpdate,
                    onCancel = { sheet = null; onEditorClosed() },
                    farmLocked = true,
                )
            }
        }
        "yield" -> ModalBottomSheet(onDismissRequest = { sheet = null; onEditorClosed() }) {
            YieldEditor(delivery.analysis.toForm(), state.yieldErrors, state.isSaving, onRecordYield) {
                sheet = null
                onEditorClosed()
            }
        }
        "delete", "remove-yield" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            val deleting = sheet == "delete"
            MoConfirmationSheet(
                title = if (deleting) "Eliminar pesada" else "Quitar rendimiento",
                body = if (deleting) {
                    "Estos kilos y su rendimiento dejarán de contar en la campaña. Esta acción no se puede deshacer."
                } else {
                    "La pesada se queda igual; solo deja de tener rendimiento."
                },
                confirmText = if (deleting) "Eliminar" else "Quitar",
                onConfirm = {
                    sheet = null
                    if (deleting) onDelete() else onRemoveYield()
                },
                onCancel = { sheet = null },
                modifier = Modifier.padding(horizontal = MoSpacing.md).testTag("delivery-confirmation"),
            )
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

@Composable
private fun YieldEditor(
    initial: YieldForm,
    errors: YieldFormErrors,
    isSaving: Boolean,
    onSave: (YieldForm) -> Unit,
    onCancel: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen)
            .testTag("yield-editor"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Rendimiento de la pesada", style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        Text(
            "Se guarda aparte: la pesada no cambia.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoSurfaceTokens.secondaryText,
        )
        MoTextField(
            form.fat, { form = form.copy(fat = it) }, "Rendimiento graso (%)",
            isError = errors.fat != null, supportingText = errors.fat,
            modifier = Modifier.fillMaxWidth().testTag("yield-fat"),
        )
        MoTextField(
            form.industrial, { form = form.copy(industrial = it) }, "Rendimiento industrial (%)",
            isError = errors.industrial != null, supportingText = errors.industrial,
            modifier = Modifier.fillMaxWidth().testTag("yield-industrial"),
        )
        MoDateInputField(
            form.date, { form = form.copy(date = it) }, "Fecha del análisis (opcional)",
            isError = errors.date != null, supportingText = errors.date,
            modifier = Modifier.fillMaxWidth(),
            optional = true,
        )
        MoTextField(form.notes, { form = form.copy(notes = it) }, "Notas", singleLine = false, modifier = Modifier.fillMaxWidth())
        MoPrimaryButton("Guardar rendimiento", { onSave(form) }, Modifier.fillMaxWidth().testTag("save-yield"), enabled = !isSaving)
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun DeliverySummaryBlock(delivery: Delivery) {
    Text("Pesada del ${DATE_FORMAT.format(delivery.deliveryDate)}", style = MaterialTheme.typography.headlineMedium, color = MoOliveDark)
    Text(
        listOfNotNull(delivery.farmName, delivery.campaignName).joinToString(" · "),
        style = MaterialTheme.typography.bodyLarge,
        color = MoSurfaceTokens.secondaryText,
    )
    MoMetricCard(
        "Kilos pesados",
        Weight.format(delivery.netGrams),
        Modifier.fillMaxWidth().testTag("delivery-net-value"),
        supportingText = delivery.destinationName,
    )
    if (delivery.source == DeliverySource.TICKET_OCR) {
        MoStatusChip("Leído del vale y confirmado por ti", tone = MoStatusTone.Info)
    }
    delivery.deliveryTime?.let { DetailValue("Hora", it.toString()) }
    DetailValue("Origen de la aceituna", delivery.origin?.label ?: "Sin indicar")
    if (delivery.harvestId != null) {
        Text(
            "Forma parte del día de recolección de su fecha: sus kilos cuentan en el total de ese día.",
            style = MaterialTheme.typography.bodyMedium,
            color = MoSurfaceTokens.secondaryText,
            modifier = Modifier.testTag("delivery-in-jornada"),
        )
    }
    DetailValue("Peso bruto", delivery.grossGrams?.let(Weight::format))
    DetailValue("Tara", delivery.tareGrams?.let(Weight::format))
    DetailValue("Nº de vale", delivery.ticketNumber)
    DetailValue("Nº de albarán", delivery.deliveryNumber)
    MoSectionHeader("Parcelas de origen")
    delivery.shares.forEach { share ->
        Row(Modifier.fillMaxWidth().testTag("delivery-share"), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(share.parcelName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (share.allocation == HarvestAllocation.EXACT && share.weightGrams != null) Weight.format(share.weightGrams)
                else "Kilos no conocidos",
                style = MaterialTheme.typography.bodyMedium,
                color = MoSurfaceTokens.secondaryText,
            )
        }
    }
    when (val unallocated = delivery.unallocatedGrams) {
        null -> Text(
            "Sin repartir entre parcelas: No disponible",
            style = MaterialTheme.typography.bodyMedium,
            color = MoSurfaceTokens.secondaryText,
            modifier = Modifier.testTag("delivery-unallocated"),
        )
        else -> if (unallocated > 0) {
            Text(
                "Sin repartir entre parcelas: ${Weight.format(unallocated)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MoSurfaceTokens.secondaryText,
                modifier = Modifier.testTag("delivery-unallocated"),
            )
        }
    }
    delivery.notes?.let { DetailValue("Notas", it) }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoSurfaceTokens.secondaryText)
        Text(value ?: "Sin registrar", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun TicketReviewRoute(
    extractionId: UUID,
    persistence: LocalPersistence,
    clock: AppClock,
    onDeliveryCreated: (UUID) -> Unit,
    onClosed: () -> Unit,
) {
    val viewModel: TicketReviewViewModel = viewModel(
        key = "ticket-$extractionId",
        factory = viewModelFactory {
            initializer {
                TicketReviewViewModel(
                    extractionId,
                    persistence.documentOcrRepository,
                    persistence.deliveryRepository,
                    persistence.organizationRepository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdDeliveryId) { state.createdDeliveryId?.let(onDeliveryCreated) }
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    val today = clock.today(ZoneId.systemDefault())
    TicketReviewScreen(
        state = state,
        today = today,
        onConfirm = { form -> viewModel.confirm(form, today) },
        onReadAgain = viewModel::readAgain,
        onDiscard = viewModel::discard,
        attachmentContent = {
            AttachmentsRoute(
                owner = AttachmentOwner(AttachmentOwnerType.DOCUMENT, extractionId),
                persistence = persistence,
                title = "Vale original",
            )
        },
    )
}

/**
 * Weight-ticket review: the read text and the ticket are shown next to a Delivery form that
 * starts from what the ticket seems to say. Nothing counts as delivered until the person
 * confirms the figures; the engine's reading itself is kept unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketReviewScreen(
    state: TicketReviewUiState,
    today: LocalDate,
    onConfirm: (DeliveryForm) -> Unit,
    onReadAgain: () -> Unit,
    onDiscard: () -> Unit,
    attachmentContent: @Composable () -> Unit = {},
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    Scaffold(Modifier.fillMaxSize().testTag("ticket-review-root"), containerColor = MoSurfaceTokens.appBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            val extraction = state.extraction
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(MoSpacing.screen))
                extraction == null -> MoErrorState("Vale no disponible", state.error ?: "No está guardado en este dispositivo.")
                extraction.status == OcrStatus.CONFIRMED -> Text(
                    "Este vale ya se confirmó como pesada.",
                    color = MoSurfaceTokens.secondaryText,
                    modifier = Modifier.padding(horizontal = MoSpacing.screen),
                )
                else -> {
                    Column(Modifier.padding(horizontal = MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                        Spacer(Modifier.height(MoSpacing.md))
                        MoStatusChip(
                            when (extraction.status) {
                                OcrStatus.PENDING -> "Aún no leído"
                                OcrStatus.FAILED -> "No se pudo leer: escribe los datos"
                                OcrStatus.NEEDS_REVIEW -> "Faltan datos: revísalos"
                                else -> "Leído: revisa cada dato"
                            },
                            tone = extraction.status.tone(),
                            modifier = Modifier.testTag("ticket-status"),
                        )
                        if (extraction.deliveryProposal?.weightsDisagree == true) {
                            Text(
                                "En el vale, bruto menos tara no da el neto. Comprueba los pesos antes de confirmar.",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("ticket-weights-disagree"),
                            )
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    DeliveryEditor(
                        title = "Confirmar pesada",
                        subtitle = "Los datos vienen del vale. Corrige lo que no coincida: solo cuenta lo que confirmes.",
                        initial = state.seed?.withTicket(extraction.deliveryProposal, state.destinations)
                            ?: extraction.deliveryProposal.toForm(state.contexts.singleOrNull()?.farmId, today, state.destinations),
                        contexts = state.contexts,
                        destinations = state.destinations,
                        errors = state.formErrors,
                        isSaving = state.isSaving,
                        saveText = "Confirmar pesada",
                        onSave = onConfirm,
                        onCancel = { confirmDiscard = true },
                        scrollable = false,
                        extraActions = {
                            MoSecondaryButton("Leer otra vez", onReadAgain, Modifier.fillMaxWidth(), enabled = !state.isSaving)
                        },
                    )
                    Column(Modifier.padding(horizontal = MoSpacing.screen)) { attachmentContent() }
                    extraction.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
                        Column(Modifier.padding(horizontal = MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                            MoSectionHeader("Texto leído")
                            Text(raw, style = MaterialTheme.typography.bodySmall, color = MoSurfaceTokens.secondaryText)
                        }
                    }
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }
    if (confirmDiscard) {
        ModalBottomSheet(onDismissRequest = { confirmDiscard = false }) {
            MoConfirmationSheet(
                title = "Descartar vale",
                body = "Se borra el vale y lo leído. No se registra ninguna pesada.",
                confirmText = "Descartar",
                onConfirm = { confirmDiscard = false; onDiscard() },
                onCancel = { confirmDiscard = false },
                modifier = Modifier.padding(horizontal = MoSpacing.md),
            )
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

/** #373/#375: the fixed context of a Pesada opened from a Farm, Campaign, Parcel or Jornada. */
internal fun pesadaContextLine(farmName: String, campaignName: String): String =
    // A campaign the farmer already named «Campaña 2026-2027» is not called «Campaña Campaña …».
    "$farmName · " + if (campaignName.trim().startsWith("campaña", ignoreCase = true)) campaignName.trim() else "Campaña $campaignName"

/** A Farm reached from Mi Campo whose campaign is no longer running: said, never swapped silently. */
internal const val PESADA_NO_RUNNING_CAMPAIGN =
    "Esta finca no tiene una campaña en marcha. Actívala en Campañas para registrar pesadas."
