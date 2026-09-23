package com.isivoltpro.maginaolivo.feature.expenses

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
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.feature.attachments.createCaptureUri
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
)

@Composable
fun ExpensesRoute(
    persistence: LocalPersistence,
    clock: AppClock,
    onExpenseSelected: (UUID) -> Unit,
    onDocumentSelected: (UUID) -> Unit,
    onOrganizations: () -> Unit,
) {
    val viewModel: ExpensesViewModel = viewModel(
        key = "expenses",
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.openedDocumentId) {
        state.openedDocumentId?.let { id ->
            viewModel.documentOpened()
            onDocumentSelected(id)
        }
    }
    ExpensesScreen(
        state = state,
        today = clock.today(ZoneId.systemDefault()),
        onCreate = viewModel::create,
        onFarmSelected = viewModel::selectFarm,
        onDocumentPicked = viewModel::importDocument,
        onProblem = viewModel::reportProblem,
        onExpenseSelected = onExpenseSelected,
        onDocumentSelected = onDocumentSelected,
        onOrganizations = onOrganizations,
        onEditorClosed = viewModel::clearFormErrors,
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
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var uploadVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) { if (state.message != null) editorVisible = false }

    Scaffold(Modifier.fillMaxSize().testTag("expenses-root"), containerColor = MoCream) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.md))
            Text("Gastos y documentos", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "Solo suman los gastos confirmados. Los borradores esperan tu revisión.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                MoMetricCard(
                    "Gastos confirmados",
                    Money.format(state.summary.totalMinor),
                    Modifier.weight(1f).testTag("expenses-total"),
                    supportingText = "${state.summary.postedCount} apuntes",
                )
                MoMetricCard(
                    "Este mes",
                    Money.format(state.monthTotalMinor),
                    Modifier.weight(1f),
                    supportingText = MONTH_FORMAT.format(today),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                MoPrimaryButton(
                    "Añadir gasto",
                    { editorVisible = true },
                    Modifier.weight(1f).testTag("add-expense"),
                    enabled = !state.isSaving,
                )
                MoPrimaryButton(
                    "Subir documento",
                    { uploadVisible = true },
                    Modifier.weight(1f).testTag("upload-document"),
                    enabled = !state.isSaving,
                )
            }
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

            if (state.summary.byCategory.isNotEmpty()) {
                MoSectionHeader("Categorías")
                state.summary.byCategory.entries.sortedByDescending { it.value }.forEach { (category, amount) ->
                    CategoryRow(category.label(), amount, state.summary.totalMinor)
                }
            }

            MoSectionHeader("Movimientos")
            val posted = state.expenses.filter { it.status == ExpenseStatus.POSTED }
            when {
                state.isLoading -> CircularProgressIndicator()
                posted.isEmpty() -> MoEmptyState(
                    "Aún no hay gastos",
                    "Añade un gasto a mano o sube una factura: la leeremos para que solo tengas que revisarla.",
                )
                else -> posted.forEach { expense -> ExpenseRow(expense) { onExpenseSelected(expense.id) } }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    if (editorVisible) {
        ModalBottomSheet(onDismissRequest = { editorVisible = false; onEditorClosed() }) {
            ExpenseEditor(
                title = "Nuevo gasto",
                initial = ExpenseForm(date = today.toString()),
                options = state.options,
                errors = state.formErrors,
                isSaving = state.isSaving,
                saveText = "Guardar gasto",
                onFarmSelected = onFarmSelected,
                onSave = onCreate,
                onCancel = { editorVisible = false; onEditorClosed() },
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
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
        Row(
            Modifier.fillMaxWidth().padding(MoSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
private fun CategoryRow(label: String, amountMinor: Long, totalMinor: Long) {
    val share = if (totalMinor > 0) (amountMinor * 100 / totalMinor) else 0
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
                Text("$share %", style = MaterialTheme.typography.labelMedium, color = MoTextSecondary)
            }
            Text(Money.format(amountMinor), style = MaterialTheme.typography.titleMedium, color = MoInk)
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
