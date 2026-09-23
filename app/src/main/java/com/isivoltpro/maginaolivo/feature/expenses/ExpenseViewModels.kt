package com.isivoltpro.maginaolivo.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.ExpenseSummary
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.expense.PurchaseLine
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction
import com.isivoltpro.maginaolivo.domain.ocr.DocumentOcrRepository
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRepository
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** What the expense form holds while a person is typing: text, not yet money. */
data class ExpenseForm(
    val date: String = "",
    val amount: String = "",
    val concept: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val supplierOrganizationId: UUID? = null,
    val supplierText: String = "",
    val farmId: UUID? = null,
    val parcelId: UUID? = null,
    val activityId: UUID? = null,
    val invoiceNumber: String = "",
    val lines: List<LineForm> = emptyList(),
    val notes: String = "",
)

data class LineForm(
    val product: String = "",
    val quantity: String = "",
    val unit: String = "",
    val total: String = "",
)

data class ExpenseFormErrors(
    val date: String? = null,
    val amount: String? = null,
    val concept: String? = null,
    val lines: String? = null,
) {
    val isEmpty: Boolean get() = date == null && amount == null && concept == null && lines == null
}

/**
 * Turns the form into an [ExpenseDraft], or explains what is missing. Pure, so the rules
 * are provable on the JVM: an unreadable amount is an error, never a zero.
 */
internal fun ExpenseForm.toDraft(requireAmount: Boolean = true): Pair<ExpenseDraft?, ExpenseFormErrors> {
    val parsedDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    val amountMinor = Money.parseMinor(amount)
    val parsedLines = lines.filter { it.product.isNotBlank() }.map { line ->
        PurchaseLine(
            productName = line.product.trim(),
            quantity = line.quantity.replace(',', '.').trim().toDoubleOrNull(),
            unit = line.unit.trim().ifEmpty { null },
            lineTotalMinor = Money.parseMinor(line.total),
        )
    }
    val badLine = lines.any { line ->
        line.product.isNotBlank() &&
            ((line.quantity.isNotBlank() && line.quantity.replace(',', '.').trim().toDoubleOrNull() == null) ||
                (line.total.isNotBlank() && Money.parseMinor(line.total) == null))
    }
    val errors = ExpenseFormErrors(
        date = if (parsedDate == null) "Escribe la fecha como AAAA-MM-DD" else null,
        amount = when {
            amount.isBlank() && requireAmount -> "Escribe el importe"
            amount.isNotBlank() && amountMinor == null -> "Escribe un importe como 65 o 65,50"
            requireAmount && amountMinor == 0L -> "El importe debe ser mayor que cero"
            else -> null
        },
        concept = if (concept.isBlank()) "Describe el gasto" else null,
        lines = if (badLine) "Revisa la cantidad o el importe de las líneas" else null,
    )
    if (!errors.isEmpty) return null to errors
    return ExpenseDraft(
        expenseDate = parsedDate!!,
        concept = concept.trim(),
        category = category,
        amountMinor = amountMinor ?: 0L,
        supplierOrganizationId = supplierOrganizationId,
        supplierText = supplierText.trim().ifEmpty { null },
        farmId = farmId,
        parcelId = parcelId,
        activityId = activityId,
        invoiceNumber = invoiceNumber.trim().ifEmpty { null },
        lines = parsedLines,
        notes = notes.trim().ifEmpty { null },
    ) to errors
}

internal fun Expense.toForm() = ExpenseForm(
    date = expenseDate.toString(),
    amount = Money.editable(amountMinor),
    concept = concept,
    category = category,
    supplierOrganizationId = supplierOrganizationId,
    supplierText = if (supplierOrganizationId == null) supplierName.orEmpty() else "",
    farmId = farmId,
    parcelId = parcelId,
    activityId = activityId,
    invoiceNumber = invoiceNumber.orEmpty(),
    lines = lines.map {
        LineForm(
            product = it.productName,
            quantity = it.quantity?.let { quantity -> quantity.toString().removeSuffix(".0").replace('.', ',') }.orEmpty(),
            unit = it.unit.orEmpty(),
            total = Money.editable(it.lineTotalMinor),
        )
    },
    notes = notes.orEmpty(),
)

internal fun expenseErrorMessage(error: AppError): String = when (error) {
    is AppError.Validation -> when (error.field) {
        "parcelId" -> "La parcela elegida no pertenece a esa finca"
        "activityId" -> "La actuación elegida no pertenece a esa finca"
        "amountMinor" -> "El importe debe ser mayor que cero"
        "concept" -> "Describe el gasto"
        "farmId" -> "La finca ya no está disponible"
        else -> "Revisa los datos del gasto"
    }
    is AppError.Conflict -> when (error.resource) {
        "duplicate_organization" -> "Ya existe una organización con ese nombre"
        "already_confirmed" -> "Este documento ya se revisó"
        "linked_to_expense" -> "Este documento ya está unido a un gasto"
        else -> "La operación no se puede hacer en este estado"
    }
    is AppError.NotFound -> "Ya no está disponible en este dispositivo"
    else -> "No se pudo guardar en este dispositivo"
}

/** Everything the expense form can relate an expense to, read from local data only. */
data class RelationOptions(
    val farms: List<Farm> = emptyList(),
    val parcels: List<Parcel> = emptyList(),
    val activities: List<Activity> = emptyList(),
    val suppliers: List<Organization> = emptyList(),
)

data class ExpensesUiState(
    val isLoading: Boolean = true,
    val expenses: List<Expense> = emptyList(),
    val summary: ExpenseSummary = ExpenseSummary.of(emptyList()),
    val monthTotalMinor: Long = 0,
    val openDocuments: List<DocumentExtraction> = emptyList(),
    val options: RelationOptions = RelationOptions(),
    val formErrors: ExpenseFormErrors = ExpenseFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** Set once after a document is kept, so the screen can open its review. */
    val openedDocumentId: UUID? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpensesViewModel(
    private val expenses: ExpenseRepository,
    private val documents: DocumentOcrRepository,
    private val relations: RelationSource,
    private val clock: AppClock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExpensesUiState())
    val state: StateFlow<ExpensesUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            expenses.observeAll()
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer los gastos") }
                .collect { rows ->
                    val month = clock.today(ZoneId.systemDefault()).withDayOfMonth(1)
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        expenses = rows,
                        summary = ExpenseSummary.of(rows),
                        monthTotalMinor = ExpenseSummary.of(rows.filter { !it.expenseDate.isBefore(month) }).totalMinor,
                    )
                }
        }
        viewModelScope.launch {
            documents.observeOpen().catch { }.collect { open ->
                mutableState.value = mutableState.value.copy(openDocuments = open)
            }
        }
        relations.collectInto(viewModelScope) { options ->
            mutableState.value = mutableState.value.copy(options = options)
        }
    }

    fun selectFarm(farmId: UUID?) = relations.selectFarm(farmId)

    fun create(form: ExpenseForm) {
        val (draft, errors) = form.toDraft()
        mutableState.value = mutableState.value.copy(formErrors = errors)
        if (draft == null) return
        mutate("Gasto guardado en este dispositivo") { expenses.create(draft).map { } }
    }

    fun importDocument(type: DocumentType, sourceUri: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            when (val imported = documents.importDocument(type, sourceUri)) {
                is AppResult.Success -> mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    openedDocumentId = imported.value,
                )
                is AppResult.Failure -> mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    error = com.isivoltpro.maginaolivo.feature.attachments.attachmentErrorMessage(imported.error),
                )
            }
        }
    }

    fun documentOpened() {
        mutableState.value = mutableState.value.copy(openedDocumentId = null)
    }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = ExpenseFormErrors())
    }

    fun reportProblem(message: String) {
        mutableState.value = mutableState.value.copy(message = null, error = message)
    }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message, formErrors = ExpenseFormErrors())
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = expenseErrorMessage(result.error))
            }
        }
    }
}

/**
 * Reads the Farms, the chosen Farm's Parcels and Activities, and the suppliers the
 * expense form offers. Shared by the list and the detail screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RelationSource(
    private val workspaces: WorkspaceRepository,
    private val farms: FarmRepository,
    private val parcels: ParcelRepository,
    private val activities: ActivityRepository,
    private val organizations: OrganizationRepository,
) {
    private val selectedFarm = MutableStateFlow<UUID?>(null)
    private var options = RelationOptions()

    fun selectFarm(farmId: UUID?) {
        selectedFarm.value = farmId
    }

    fun collectInto(scope: kotlinx.coroutines.CoroutineScope, onChange: (RelationOptions) -> Unit): Job =
        scope.launch {
            val workspaceId = (workspaces.ensureLocalWorkspace() as? AppResult.Success)?.value
            launch {
                (workspaceId?.let(farms::observeActive) ?: flowOf(emptyList())).catch { }.collect {
                    options = options.copy(farms = it)
                    onChange(options)
                }
            }
            launch {
                organizations.observeAll().catch { }.collect {
                    options = options.copy(suppliers = it)
                    onChange(options)
                }
            }
            launch {
                selectedFarm.flatMapLatest { farmId ->
                    farmId?.let(parcels::observeActive) ?: flowOf(emptyList())
                }.catch { }.collect {
                    options = options.copy(parcels = it)
                    onChange(options)
                }
            }
            launch {
                selectedFarm.flatMapLatest { farmId ->
                    farmId?.let(activities::observeForFarm) ?: flowOf(emptyList())
                }.catch { }.collect {
                    options = options.copy(activities = it)
                    onChange(options)
                }
            }
        }
}

data class ExpenseDetailUiState(
    val isLoading: Boolean = true,
    val expense: Expense? = null,
    val options: RelationOptions = RelationOptions(),
    val formErrors: ExpenseFormErrors = ExpenseFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val deleted: Boolean = false,
)

class ExpenseDetailViewModel(
    private val expenseId: UUID,
    private val expenses: ExpenseRepository,
    private val relations: RelationSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ExpenseDetailUiState())
    val state: StateFlow<ExpenseDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            expenses.observe(expenseId)
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer el gasto") }
                .collect { expense ->
                    mutableState.value = mutableState.value.copy(isLoading = false, expense = expense)
                    if (expense?.farmId != null) relations.selectFarm(expense.farmId)
                }
        }
        relations.collectInto(viewModelScope) { options ->
            mutableState.value = mutableState.value.copy(options = options)
        }
    }

    fun selectFarm(farmId: UUID?) = relations.selectFarm(farmId)

    fun update(form: ExpenseForm) {
        val current = mutableState.value.expense ?: return
        val (draft, errors) = form.toDraft(requireAmount = current.status == ExpenseStatus.POSTED)
        mutableState.value = mutableState.value.copy(formErrors = errors)
        if (draft == null) return
        mutate("Cambios guardados") { expenses.update(expenseId, draft) }
    }

    /** The one human step that turns a reviewed draft into counted money. */
    fun post() = mutate("Gasto confirmado") { expenses.post(expenseId) }

    fun delete() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = expenses.delete(expenseId)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, deleted = true)
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = expenseErrorMessage(result.error))
            }
        }
    }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = ExpenseFormErrors())
    }

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message, formErrors = ExpenseFormErrors())
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = expenseErrorMessage(result.error))
            }
        }
    }
}
