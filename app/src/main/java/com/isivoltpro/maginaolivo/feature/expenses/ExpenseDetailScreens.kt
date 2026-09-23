package com.isivoltpro.maginaolivo.feature.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsRoute
import com.isivoltpro.maginaolivo.ui.components.MoConfirmationSheet
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.util.UUID

@Composable
fun ExpenseDetailRoute(
    expenseId: UUID,
    persistence: LocalPersistence,
    onDeleted: () -> Unit,
) {
    val viewModel: ExpenseDetailViewModel = viewModel(
        key = "expense-$expenseId",
        factory = viewModelFactory {
            initializer { ExpenseDetailViewModel(expenseId, persistence.expenseRepository, relationSource(persistence)) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    ExpenseDetailScreen(
        state = state,
        onUpdate = viewModel::update,
        onPost = viewModel::post,
        onDelete = viewModel::delete,
        onFarmSelected = viewModel::selectFarm,
        onEditorClosed = viewModel::clearFormErrors,
        attachmentContent = {
            AttachmentsRoute(
                owner = AttachmentOwner(AttachmentOwnerType.EXPENSE, expenseId),
                persistence = persistence,
                title = "Factura o ticket",
            )
        },
    )
}

/** S62 — Detalle Gasto: read, confirm a draft, edit and delete with confirmation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    state: ExpenseDetailUiState,
    onUpdate: (ExpenseForm) -> Unit,
    onPost: () -> Unit,
    onDelete: () -> Unit,
    onFarmSelected: (UUID?) -> Unit,
    onEditorClosed: () -> Unit = {},
    attachmentContent: @Composable () -> Unit = {},
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.message) { if (state.message != null) editorVisible = false }

    Scaffold(Modifier.fillMaxSize().testTag("expense-detail-root"), containerColor = MoCream) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            val expense = state.expense
            when {
                state.isLoading -> CircularProgressIndicator()
                expense == null -> MoErrorState("Gasto no disponible", state.error ?: "No está guardado en este dispositivo.")
                else -> {
                    ExpenseSummaryBlock(expense)
                    if (expense.status == ExpenseStatus.DRAFT) {
                        Text(
                            "Este gasto viene de un documento revisado y todavía no suma. Confírmalo cuando estés seguro del importe.",
                            color = MoTextSecondary,
                        )
                        MoPrimaryButton(
                            "Confirmar gasto",
                            { confirmation = "post" },
                            Modifier.fillMaxWidth().testTag("post-expense"),
                            enabled = !state.isSaving,
                        )
                    }
                    MoSecondaryButton("Editar gasto", { editorVisible = true }, Modifier.fillMaxWidth().testTag("edit-expense"))
                    MoSecondaryButton("Eliminar gasto", { confirmation = "delete" }, Modifier.fillMaxWidth().testTag("delete-expense"))
                    state.message?.let { Text(it, color = MoTextSecondary) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    attachmentContent()
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    val expense = state.expense
    if (editorVisible && expense != null) {
        ModalBottomSheet(onDismissRequest = { editorVisible = false; onEditorClosed() }) {
            ExpenseEditor(
                title = "Editar gasto",
                initial = expense.toForm(),
                options = state.options,
                errors = state.formErrors,
                isSaving = state.isSaving,
                saveText = "Guardar cambios",
                onFarmSelected = onFarmSelected,
                onSave = onUpdate,
                onCancel = { editorVisible = false; onEditorClosed() },
            )
        }
    }
    confirmation?.let { action ->
        ModalBottomSheet(onDismissRequest = { confirmation = null }) {
            MoConfirmationSheet(
                title = if (action == "post") "Confirmar gasto" else "Eliminar gasto",
                body = if (action == "post") {
                    "A partir de ahora este importe sumará en tus gastos."
                } else {
                    "El gasto dejará de contar en los totales. Esta acción no se puede deshacer."
                },
                confirmText = if (action == "post") "Confirmar" else "Eliminar",
                onConfirm = {
                    confirmation = null
                    if (action == "post") onPost() else onDelete()
                },
                onCancel = { confirmation = null },
                modifier = Modifier.padding(horizontal = MoSpacing.md).testTag("expense-confirmation"),
            )
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

@Composable
private fun ExpenseSummaryBlock(expense: Expense) {
    Text(expense.concept, style = MaterialTheme.typography.headlineMedium, color = MoOliveDark)
    if (expense.status == ExpenseStatus.DRAFT) {
        MoStatusChip("Borrador · no suma", tone = MoStatusTone.Warning, modifier = Modifier.testTag("expense-draft-chip"))
    } else {
        MoStatusChip("Confirmado", tone = MoStatusTone.Success)
    }
    MoMetricCard(
        "Importe",
        Money.format(expense.amountMinor, expense.currency),
        Modifier.fillMaxWidth().testTag("expense-amount-value"),
        supportingText = "${DATE_FORMAT.format(expense.expenseDate)} · ${expense.category.label()}",
    )
    DetailValue("Proveedor", expense.supplierName)
    DetailValue("Nº de factura o ticket", expense.invoiceNumber)
    DetailValue(
        "Origen",
        when (expense.origin) {
            ExpenseOrigin.MANUAL -> "Anotado a mano"
            ExpenseOrigin.ACTIVITY_COST -> "Coste de una actuación"
            ExpenseOrigin.DOCUMENT_OCR -> "Leído de un documento y revisado"
        },
    )
    expense.notes?.let { DetailValue("Notas", it) }
    if (expense.lines.isNotEmpty()) {
        MoSectionHeader("Qué se compró")
        expense.lines.forEach { line ->
            Text(
                listOfNotNull(
                    line.productName,
                    line.quantity?.let { quantity -> listOfNotNull(quantity.toString().removeSuffix(".0"), line.unit).joinToString(" ") },
                    line.lineTotalMinor?.let { Money.format(it, expense.currency) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        val linesTotal = expense.lines.mapNotNull { it.lineTotalMinor }.takeIf { it.isNotEmpty() }?.sum()
        if (linesTotal != null && linesTotal != expense.amountMinor) {
            Text(
                "Las líneas suman ${Money.format(linesTotal, expense.currency)}; cuenta el total del gasto.",
                style = MaterialTheme.typography.bodySmall,
                color = MoTextSecondary,
            )
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
        Text(value ?: "Sin registrar", style = MaterialTheme.typography.bodyLarge)
    }
}
