package com.isivoltpro.maginaolivo.feature.expenses
import com.isivoltpro.maginaolivo.ui.components.OnEachSave

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.feature.attachments.createCaptureUri
import com.isivoltpro.maginaolivo.feature.harvests.moneyLabel
import com.isivoltpro.maginaolivo.ui.components.MoKpiMetric
import com.isivoltpro.maginaolivo.ui.components.MoKpiKind
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.ui.components.MoIconTone
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoBottomActionSheet
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOlivePrimary
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import com.isivoltpro.maginaolivo.ui.theme.MoInk

internal fun relationSource(persistence: LocalPersistence) = RelationSource(
    workspaces = persistence.workspaceRepository,
    farms = persistence.farmRepository,
    parcels = persistence.parcelRepository,
    activities = persistence.activityRepository,
    organizations = persistence.organizationRepository,
    campaigns = persistence.campaignRepository,
    dayCosts = persistence.dayCostRepository,
)

@Composable
fun ExpensesRoute(
    persistence: LocalPersistence,
    clock: AppClock,
    onExpenseSelected: (UUID) -> Unit,
    onDocumentSelected: (UUID) -> Unit,
    onOrganizations: () -> Unit,
    /** CR-011 §14: the Farm the Cuaderno already knows; a new expense starts on it. */
    presetFarmId: UUID? = null,
    /** CR-011 §14: the Cuaderno's Parcel (always of [presetFarmId]); a new expense starts on it. */
    presetParcelId: UUID? = null,
    presetCampaignId: UUID? = null,
    /** #378: «Jornal fuera de campaña» — the labour form of [presetFarmId], opened at once. */
    presetLabour: Boolean = false,
    /** #416: «Añadir gasto relacionado» — a new expense tied to this work, opened at once. */
    presetActivityId: UUID? = null,
    /** #416: a contextual entry returns where it started, after saving or cancelling. */
    onContextDone: ((saved: Boolean) -> Unit)? = null,
    /** #415: Cuaderno → Gasto — the form opens at once and the entry is contextual. */
    presetQuick: Boolean = false,
    /** #415: after «Guardar y añadir foto», the saved Gasto is opened here. */
    onOpenSavedExpense: (UUID) -> Unit = onExpenseSelected,
    /**
     * A document just taken on this screen; it is reviewed with this screen's Farm/Campaign
     * context. Documents listed «por revisar» open with [onDocumentSelected], without context.
     */
    onDocumentImported: (UUID) -> Unit = onDocumentSelected,
) {
    val viewModel: ExpensesViewModel = viewModel(
        key = "expenses-${presetFarmId ?: "all"}-${presetCampaignId ?: "outside"}",
        factory = viewModelFactory {
            initializer {
                ExpensesViewModel(
                    persistence.expenseRepository,
                    persistence.documentOcrRepository,
                    relationSource(persistence),
                    clock,
                )
            }
        },
    )
    val allState by viewModel.state.collectAsStateWithLifecycle()
    val state = if (presetCampaignId == null) allState else {
        val rows = allState.expenses.filter { it.campaignId == presetCampaignId }
        allState.copy(expenses = rows)
    }
    LaunchedEffect(state.savedExpenseToOpen) {
        state.savedExpenseToOpen?.let { id ->
            viewModel.savedExpenseOpened()
            onOpenSavedExpense(id)
        }
    }
    LaunchedEffect(state.openedDocumentId) {
        state.openedDocumentId?.let { id ->
            viewModel.documentOpened()
            onDocumentImported(id)
        }
    }
    ExpensesScreen(
        state = state,
        today = clock.today(ZoneId.systemDefault()),
        onCreate = viewModel::create,
        onCreateWithPhoto = viewModel::createAndOpen,
        onFarmSelected = viewModel::selectFarm,
        onDocumentPicked = viewModel::importDocument,
        onProblem = viewModel::reportProblem,
        onExpenseSelected = onExpenseSelected,
        onDocumentSelected = onDocumentSelected,
        onOrganizations = onOrganizations,
        onEditorClosed = viewModel::clearFormErrors,
        presetFarmId = presetFarmId,
        presetParcelId = presetParcelId,
        presetCampaignId = presetCampaignId,
        presetLabour = presetLabour,
        presetActivityId = presetActivityId,
        presetQuick = presetQuick,
        onContextDone = onContextDone,
        dayCostQuestion = viewModel::dayCostQuestion,
    )
}

/** S60 — Gastos y documentos. Totals are posted money only; drafts are shown apart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    state: ExpensesUiState,
    today: LocalDate,
    onCreate: (ExpenseForm) -> Unit,
    onFarmSelected: (UUID?) -> Unit,
    onDocumentPicked: (DocumentType, String) -> Unit,
    onProblem: (String) -> Unit,
    onExpenseSelected: (UUID) -> Unit,
    onDocumentSelected: (UUID) -> Unit,
    onOrganizations: () -> Unit,
    onEditorClosed: () -> Unit = {},
    presetFarmId: UUID? = null,
    presetParcelId: UUID? = null,
    presetCampaignId: UUID? = null,
    presetLabour: Boolean = false,
    presetActivityId: UUID? = null,
    presetQuick: Boolean = false,
    onContextDone: ((saved: Boolean) -> Unit)? = null,
    /** #415: «Guardar y añadir foto»; null hides the button. */
    onCreateWithPhoto: ((ExpenseForm) -> Unit)? = null,
    /** #475: the question a day's jornales/maquinaria cost gets, from the day as it is. */
    dayCostQuestion: suspend (UUID, ExpenseCategory) -> com.isivoltpro.maginaolivo.domain.expense.DayCostQuestion? = { _, _ -> null },
) {
    // #416/#415: a work's «Añadir gasto relacionado» or Cuaderno → Gasto opens the form at once
    // and returns to where it started; the plain Gastos screen opens on its list.
    val contextual = presetActivityId != null || presetQuick
    var editorVisible by rememberSaveable { mutableStateOf(presetLabour || contextual) }
    var uploadVisible by rememberSaveable { mutableStateOf(false) }
    OnEachSave(state.saveCount) {
        editorVisible = false
        if (contextual) onContextDone?.invoke(true)
    }
    LaunchedEffect(state.savedExpenseToOpen) { if (state.savedExpenseToOpen != null) editorVisible = false }
    // The Farm's parcels and works are offered in the form from the start.
    LaunchedEffect(presetFarmId) { presetFarmId?.let(onFarmSelected) }

    Scaffold(Modifier.fillMaxSize().testTag("expenses-root"), containerColor = MoCream, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.md))
            Text(if (presetCampaignId == null) "Gastos y documentos" else "Gastos de recogida", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "Solo suman los gastos confirmados. Los borradores esperan tu revisión.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )
            if (presetCampaignId != null) {
                val ledger = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.of(presetCampaignId, state.expenses, emptyList())
                MoKpiMetric("Gastos confirmados", ledger.moneyLabel(), Modifier.fillMaxWidth().testTag("expenses-total"),
                    icon = MoIcons.Euro, kind = MoKpiKind.TOTAL, supportingText = "${ledger.sumOf { it.posted.size }} apuntes · Monedas originales")
                val month = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.of(presetCampaignId,
                    state.expenses.filter { java.time.YearMonth.from(it.expenseDate) == java.time.YearMonth.from(today) }, emptyList())
                MoKpiMetric("Este mes", month.moneyLabel(), Modifier.fillMaxWidth(), icon = MoIcons.Euro,
                    kind = MoKpiKind.TOTAL, supportingText = MONTH_FORMAT.format(today))
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                // #450: one total per currency, never EUR standing for all; nothing converted.
                val ledger = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.posted(state.expenses)
                val month = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.posted(
                    state.expenses.filter { java.time.YearMonth.from(it.expenseDate) == java.time.YearMonth.from(today) },
                )
                MoMetricCard(
                    "Gastos confirmados",
                    ledger.moneyLabel(),
                    Modifier.weight(1f).testTag("expenses-total"),
                    supportingText = "${ledger.sumOf { it.posted.size }} apuntes",
                )
                MoMetricCard(
                    "Este mes",
                    month.moneyLabel(),
                    Modifier.weight(1f).testTag("expenses-month"),
                    supportingText = MONTH_FORMAT.format(today),
                )
            }
            // #342: expenses are typed by the farmer; an invoice photo is attached from the expense.
            MoPrimaryButton(
                "Añadir gasto",
                { editorVisible = true },
                Modifier.fillMaxWidth().testTag("add-expense"),
                enabled = !state.isSaving,
            )
            TextButton(onClick = onOrganizations, modifier = Modifier.testTag("open-organizations")) {
                Text("Proveedores y organizaciones")
            }
            if (state.isSaving) Text("Guardando…", color = MoTextSecondary)
            state.message?.let { Text(it, color = MoTextSecondary, modifier = Modifier.testTag("expenses-message")) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("expenses-error")) }

            if (state.openDocuments.isNotEmpty()) {
                MoSectionHeader("Documentos por revisar")
                state.openDocuments.forEach { document -> DocumentRow(document) { onDocumentSelected(document.id) } }
            }
            val drafts = state.expenses.filter { it.status == ExpenseStatus.DRAFT }
            if (drafts.isNotEmpty()) {
                MoSectionHeader("Borradores sin confirmar")
                drafts.forEach { expense -> ExpenseRow(expense) { onExpenseSelected(expense.id) } }
            }

            val byCurrency = com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger.posted(state.expenses)
            if (presetCampaignId == null && byCurrency.isNotEmpty()) {
                MoSectionHeader("Categorías")
                // #450: shares only within one currency; with several, each currency has its own list.
                byCurrency.forEach { ledger ->
                    if (byCurrency.size > 1) {
                        Text("En ${ledger.currency}", style = MaterialTheme.typography.labelLarge, color = MoTextSecondary,
                            modifier = Modifier.testTag("expenses-categories-${ledger.currency}"))
                    }
                    // #500: checked sums; a category or total that overflows reads «No disponible», never negative.
                    val total = ledger.amount()
                    com.isivoltpro.maginaolivo.domain.expense.ExpenseSummary.of(ledger.posted, ledger.currency).byCategory
                        .entries.sortedByDescending { it.value ?: Long.MAX_VALUE }.forEach { (category, amount) ->
                            CategoryRow(category.label(), amount, total, ledger.currency)
                        }
                }
            }

            MoSectionHeader("Movimientos")
            val posted = state.expenses.filter { it.status == ExpenseStatus.POSTED }
            when {
                state.isLoading -> CircularProgressIndicator()
                posted.isEmpty() -> MoEmptyState(
                    "Aún no hay gastos",
                    "Añade cada gasto a mano. Puedes adjuntar la foto de la factura desde el propio gasto.",
                    icon = MoIcons.Document,
                )
                else -> posted.forEach { expense -> ExpenseRow(expense) { onExpenseSelected(expense.id) } }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    if (editorVisible) {
        val closeEditor = {
            editorVisible = false
            onEditorClosed()
            if (contextual) onContextDone?.invoke(false)
        }
        ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, onDismissRequest = { closeEditor() }) {
            ExpenseEditor(
                // #378: outside a campaign, Jornal is the Farm's labour — said so, with Mano de obra chosen.
                title = if (presetLabour) LABOUR_OUTSIDE_CAMPAIGN_TITLE else "Nuevo gasto",
                subtitle = if (presetLabour) LABOUR_OUTSIDE_CAMPAIGN_NOTE else "Se guardará primero en este dispositivo.",
                initial = ExpenseForm(
                    date = today.toString(), farmId = presetFarmId, parcelId = presetParcelId, campaignId = presetCampaignId,
                    activityId = presetActivityId,
                    category = if (presetLabour) ExpenseCategory.LABOR else ExpenseCategory.OTHER,
                    concept = if (presetLabour) "Jornal" else "",
                ),
                options = state.options,
                errors = state.formErrors,
                isSaving = state.isSaving,
                saveText = "Guardar gasto",
                onFarmSelected = onFarmSelected,
                onSave = onCreate,
                onSaveWithPhoto = onCreateWithPhoto,
                dayCostQuestion = dayCostQuestion,
                onCancel = { closeEditor() },
                // #411: a running Campaign never silently captures a general Farm expense.
                preselectRecollection = false,
                // #416/#433: a work's expense follows that work's Campaign; there is nothing to choose.
                requireCampaignChoice = presetFarmId != null && presetCampaignId == null && !presetLabour && presetActivityId == null,
                // #375: from a Farm's Cuaderno or a campaign, the Farm is context, not a question.
                farmLocked = presetFarmId != null,
                campaignLocked = presetCampaignId != null,
                activityLocked = presetActivityId != null,
            )
        }
    }
    if (uploadVisible) {
        DocumentUploadSheet(
            onPicked = { type, uri -> uploadVisible = false; onDocumentPicked(type, uri) },
            onProblem = { message -> uploadVisible = false; onProblem(message) },
            onDismiss = { uploadVisible = false },
        )
    }
}

/**
 * Choose what the document is, then take a photo or pick a file. The same capture path as
 * every other attachment: the file is copied into the app before anything reads it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentUploadSheet(
    onPicked: (DocumentType, String) -> Unit,
    onProblem: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf(DocumentType.PURCHASE_INVOICE.name) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedType = DocumentType.valueOf(type)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPicked(selectedType, it.toString()) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = pendingCapture
        pendingCapture = null
        if (saved && target != null) onPicked(selectedType, target)
    }
    ModalBottomSheet(containerColor = com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite, onDismissRequest = onDismiss) {
        MoBottomActionSheet(
            title = "Subir documento",
            body = "Leeremos el documento en este dispositivo. Nada se apunta como gasto hasta que lo revises.",
            modifier = Modifier.padding(horizontal = MoSpacing.md).testTag("document-upload-sheet"),
        ) {
            UPLOADABLE_DOCUMENT_TYPES.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { type = option.name }.testTag("document-type-${option.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(selected = option.name == type, onClick = { type = option.name })
                    Text(option.label())
                }
            }
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
                Modifier.fillMaxWidth().testTag("document-camera"),
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
                Modifier.fillMaxWidth().testTag("document-picker"),
            )
        }
        Spacer(Modifier.height(MoSpacing.md))
    }
}

@Composable
internal fun ExpenseRow(expense: Expense, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("expense-row"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        // CR-011 §23: icon in the Gasto colour, the concept, then when / what / who, then status.
        Row(
            Modifier.fillMaxWidth().padding(MoSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoIconBadge(MoIcons.Euro, tint = MoIconTone.MONEY.tint, container = MoIconTone.MONEY.container)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
                Text(expense.concept, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text(
                    listOfNotNull(DATE_FORMAT.format(expense.expenseDate), expense.category.label(), expense.supplierName)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                )
                if (expense.status == ExpenseStatus.DRAFT) {
                    MoStatusChip("Borrador · no suma", tone = MoStatusTone.Warning)
                }
            }
            Text(
                Money.format(expense.amountMinor, expense.currency),
                style = MaterialTheme.typography.titleMedium,
                color = if (expense.status == ExpenseStatus.DRAFT) MoTextSecondary else MoOlivePrimary,
            )
        }
    }
}

@Composable
private fun CategoryRow(label: String, amountMinor: Long?, totalMinor: Long?, currency: String) {
    val share = categoryShare(amountMinor, totalMinor)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(MoSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text(share?.let { "$it %" } ?: "—", style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
            }
            Text(amountMinor?.let { Money.format(it, currency) } ?: "No disponible", style = MaterialTheme.typography.titleMedium, color = MoInk)
        }
    }
}

@Composable
internal fun DocumentRow(document: DocumentExtraction, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("document-row"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            Text(document.documentType.label(), style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
            Text(
                DATE_FORMAT.format(document.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()),
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
            )
            MoStatusChip(document.status.label(), tone = document.status.tone())
        }
    }
}

internal fun com.isivoltpro.maginaolivo.domain.ocr.OcrStatus.tone(): MoStatusTone = when (this) {
    com.isivoltpro.maginaolivo.domain.ocr.OcrStatus.PENDING -> MoStatusTone.Info
    com.isivoltpro.maginaolivo.domain.ocr.OcrStatus.EXTRACTED -> MoStatusTone.Neutral
    com.isivoltpro.maginaolivo.domain.ocr.OcrStatus.NEEDS_REVIEW -> MoStatusTone.Warning
    com.isivoltpro.maginaolivo.domain.ocr.OcrStatus.CONFIRMED -> MoStatusTone.Success
    com.isivoltpro.maginaolivo.domain.ocr.OcrStatus.FAILED -> MoStatusTone.Error
}

private val SPANISH = Locale.forLanguageTag("es-ES")
internal val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH)
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", SPANISH)

/** #378: the labour form Cuaderno → Jornal opens when the Farm has no running campaign. */
internal const val LABOUR_OUTSIDE_CAMPAIGN_TITLE = "Jornal fuera de campaña"
internal const val LABOUR_OUTSIDE_CAMPAIGN_NOTE =
    "Mano de obra de la finca: poda, desbroce, tratamientos… Se guarda como gasto de mano de obra. " +
        "Los jornales de recogida, por persona, se registran dentro de una campaña."

/** #500: a category's share of the total, or null when either sum is unknown; never an overflowed figure. */
internal fun categoryShare(amountMinor: Long?, totalMinor: Long?): Long? {
    if (amountMinor == null || totalMinor == null) return null
    if (totalMinor <= 0) return 0
    return java.math.BigInteger.valueOf(amountMinor).multiply(java.math.BigInteger.valueOf(100))
        .divide(java.math.BigInteger.valueOf(totalMinor)).toLong()
}
