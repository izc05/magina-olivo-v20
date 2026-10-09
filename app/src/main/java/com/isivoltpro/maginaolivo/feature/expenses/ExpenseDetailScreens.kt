package com.isivoltpro.maginaolivo.feature.expenses

import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import com.isivoltpro.maginaolivo.ui.components.OnEachSave
import androidx.compose.foundation.layout.WindowInsets
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
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
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
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
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
        onKeepIndependent = viewModel::keepAsIndependent,
        onFarmSelected = viewModel::selectFarm,
        onEditorClosed = viewModel::clearFormErrors,
        dayCostQuestion = viewModel::dayCostQuestion,
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
    onKeepIndependent: () -> Unit = {},
    /** #475: the question a day's jornales/maquinaria cost gets, from the day as it is. */
    dayCostQuestion: suspend (UUID, ExpenseCategory) -> com.isivoltpro.maginaolivo.domain.expense.DayCostQuestion? = { _, _ -> null },
    attachmentContent: @Composable () -> Unit = {},
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    OnEachSave(state.saveCount) { editorVisible = false }

    Scaffold(Modifier.fillMaxSize().testTag("expense-detail-root"), containerColor = MoSurfaceTokens.appBackground, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
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
                    val calculated = expense.origin == ExpenseOrigin.DAY_LABOUR || expense.origin == ExpenseOrigin.DAY_EQUIPMENT
                    if (calculated) {
                        // CR-010 A3: the day's attendance or equipment and its prices write this entry.
                        Text(
                            if (expense.status == ExpenseStatus.DRAFT) {
                                "Calculado, pero no suma: ese día tiene un gasto del mismo tipo anotado a mano. Elige cuál vale desde el día de recolección."
                            } else {
                                "Se calcula solo. Para cambiarlo, cambia los jornales, la maquinaria o los precios del día."
                            },
                            color = MoColors.current.secondaryText,
                            modifier = Modifier.testTag("expense-calculated-note"),
                        )
                    } else if (expense.status == ExpenseStatus.DRAFT) {
                        Text(
                            "Este gasto viene de un documento revisado y todavía no suma. Confírmalo cuando estés seguro del importe.",
                            color = MoColors.current.secondaryText,
                        )
                        MoPrimaryButton(
                            "Confirmar gasto",
                            { confirmation = "post" },
                            Modifier.fillMaxWidth().testTag("post-expense"),
                            enabled = !state.isSaving,
                        )
                    }
                    if (expense.origin == ExpenseOrigin.ACTIVITY_COST) {
                        // #429: the two explicit ways out for a cost typed on a work before 1.0.
                        Text(
                            "Este coste se anotó en un trabajo. Si ese trabajo ya no se da por hecho: consérvalo " +
                                "como gasto independiente si el dinero se gastó, o elimínalo para dejar de contabilizarlo.",
                            color = MoColors.current.secondaryText,
                            modifier = Modifier.testTag("expense-activity-cost-note"),
                        )
                        MoSecondaryButton(
                            "Conservar como gasto independiente",
                            { confirmation = "keep" },
                            Modifier.fillMaxWidth().testTag("keep-expense-independent"),
                            enabled = !state.isSaving,
                        )
                    }
                    if (!calculated) {
                        MoSecondaryButton("Editar gasto", { editorVisible = true }, Modifier.fillMaxWidth().testTag("edit-expense"))
                        MoSecondaryButton("Eliminar gasto", { confirmation = "delete" }, Modifier.fillMaxWidth().testTag("delete-expense"))
                    }
                    state.message?.let { Text(it, color = MoColors.current.secondaryText) }
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
                // Codex #520: a work's cost stays tied to that work in a plain edit; it is
                // detached only through «Conservar como gasto independiente».
                activityLocked = expense.origin == ExpenseOrigin.ACTIVITY_COST,
                farmLocked = expense.origin == ExpenseOrigin.ACTIVITY_COST,
                askCategory = false,
                dayCostQuestion = dayCostQuestion,
            )
        }
    }
    confirmation?.let { action ->
        ModalBottomSheet(onDismissRequest = { confirmation = null }) {
            MoConfirmationSheet(
                title = when (action) {
                    "post" -> "Confirmar gasto"
                    "keep" -> "Conservar como gasto independiente"
                    else -> "Eliminar gasto"
                },
                body = when (action) {
                    "post" -> "A partir de ahora este importe sumará en tus gastos."
                    "keep" -> "El importe sigue contando igual, en su finca, parcela y campaña, pero deja de estar ligado al trabajo."
                    else -> "El gasto dejará de contar en los totales. Esta acción no se puede deshacer."
                },
                confirmText = when (action) {
                    "post" -> "Confirmar"
                    "keep" -> "Conservar"
                    else -> "Eliminar"
                },
                onConfirm = {
                    confirmation = null
                    when (action) {
                        "post" -> onPost()
                        "keep" -> onKeepIndependent()
                        else -> onDelete()
                    }
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
    Text(expense.concept, style = MaterialTheme.typography.headlineMedium, color = MoColors.current.primaryText)
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
            ExpenseOrigin.DAY_REPLACEMENT -> "Anotado a mano · sustituye el cálculo del día"
            ExpenseOrigin.ACTIVITY_COST -> "Coste de un trabajo"
            ExpenseOrigin.DOCUMENT_OCR -> "Leído de un documento y revisado"
            ExpenseOrigin.DAY_LABOUR -> "Calculado de los jornales del día"
            ExpenseOrigin.DAY_EQUIPMENT -> "Calculado de la maquinaria del día"
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
                color = MoColors.current.secondaryText,
            )
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoColors.current.secondaryText)
        Text(value ?: "Sin registrar", style = MaterialTheme.typography.bodyLarge)
    }
}
