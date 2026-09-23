package com.isivoltpro.maginaolivo.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.attachment.Attachment
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentRepository
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction
import com.isivoltpro.maginaolivo.domain.ocr.DocumentOcrRepository
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.domain.ocr.OcrStatus
import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.domain.organization.OrganizationDraft
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRepository
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** The category a document type most likely belongs to. Only a starting point. */
internal fun DocumentType.suggestedCategory(): ExpenseCategory = when (this) {
    DocumentType.PHYTOSANITARY_INVOICE, DocumentType.FERTILIZER_INVOICE -> ExpenseCategory.PRODUCTS
    DocumentType.IRRIGATION_INVOICE -> ExpenseCategory.IRRIGATION
    else -> ExpenseCategory.OTHER
}

/**
 * Pre-fills the review form from what the document seemed to say. Every value stays
 * editable and is shown as read from the document, never as confirmed.
 */
internal fun DocumentExtraction.toReviewForm(): ExpenseForm {
    val proposal = proposal
    return ExpenseForm(
        date = proposal?.invoiceDate?.toString().orEmpty(),
        amount = Money.editable(proposal?.totalMinor),
        concept = listOfNotNull(documentType.label(), proposal?.supplierName).joinToString(" · "),
        category = documentType.suggestedCategory(),
        supplierText = proposal?.supplierName.orEmpty(),
        invoiceNumber = proposal?.invoiceNumber.orEmpty(),
        lines = proposal?.lines.orEmpty().map {
            LineForm(product = it.productName, unit = it.unit.orEmpty(), total = Money.editable(it.lineTotalMinor))
        },
    )
}

data class DocumentReviewUiState(
    val isLoading: Boolean = true,
    val extraction: DocumentExtraction? = null,
    val attachment: Attachment? = null,
    val options: RelationOptions = RelationOptions(),
    val formErrors: ExpenseFormErrors = ExpenseFormErrors(),
    val isReading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val createdExpenseId: UUID? = null,
    val closed: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentReviewViewModel(
    private val extractionId: UUID,
    private val documents: DocumentOcrRepository,
    private val attachments: AttachmentRepository,
    private val relations: RelationSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DocumentReviewUiState())
    val state: StateFlow<DocumentReviewUiState> = mutableState.asStateFlow()
    private var readRequested = false

    init {
        val extraction = documents.observe(extractionId)
        viewModelScope.launch {
            extraction.catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos abrir el documento") }
                .collect { row ->
                    mutableState.value = mutableState.value.copy(isLoading = false, extraction = row)
                    // A document the process died reading is read again when it is reopened.
                    if (row?.status == OcrStatus.PENDING && !readRequested) read()
                }
        }
        viewModelScope.launch {
            extraction.map { it?.attachmentId }.distinctUntilChanged().filterNotNull()
                .flatMapLatest { attachmentId -> attachments.observe(attachmentId) }
                .catch { emit(null) }
                .collect { attachment -> mutableState.value = mutableState.value.copy(attachment = attachment) }
        }
        relations.collectInto(viewModelScope) { options ->
            mutableState.value = mutableState.value.copy(options = options)
        }
    }

    fun selectFarm(farmId: UUID?) = relations.selectFarm(farmId)

    /** Reads (or re-reads after a failure) the document with the on-device engine. */
    fun read() {
        readRequested = true
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isReading = true, error = null)
            val result = documents.runExtraction(extractionId)
            mutableState.value = mutableState.value.copy(
                isReading = false,
                error = if (result is AppResult.Failure) "No se pudo leer el documento. Puedes rellenar los datos a mano o intentarlo de nuevo." else null,
            )
        }
    }

    /** Creates a DRAFT expense from what the person reviewed. Money is not posted here. */
    fun createDraft(form: ExpenseForm) {
        val (draft, errors) = form.toDraft(requireAmount = false)
        mutableState.value = mutableState.value.copy(formErrors = errors)
        if (draft == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = documents.createExpenseDraft(extractionId, draft)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, createdExpenseId = result.value)
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = expenseErrorMessage(result.error))
            }
        }
    }

    fun keepWithoutExpense() = finish { documents.confirmWithoutExpense(extractionId) }

    fun discard() = finish { documents.discard(extractionId) }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = ExpenseFormErrors())
    }

    private fun finish(operation: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, closed = true)
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = expenseErrorMessage(result.error))
            }
        }
    }
}

data class OrganizationsUiState(
    val isLoading: Boolean = true,
    val organizations: List<Organization> = emptyList(),
    val isSaving: Boolean = false,
    val nameError: String? = null,
    val rolesError: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class OrganizationsViewModel(
    private val repository: OrganizationRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OrganizationsUiState())
    val state: StateFlow<OrganizationsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll()
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer las organizaciones") }
                .collect { mutableState.value = mutableState.value.copy(isLoading = false, organizations = it) }
        }
    }

    fun save(id: UUID?, draft: OrganizationDraft) {
        val nameError = if (draft.name.isBlank()) "Escribe el nombre" else null
        val rolesError = if (draft.roles.isEmpty()) "Elige al menos un papel" else null
        mutableState.value = mutableState.value.copy(nameError = nameError, rolesError = rolesError)
        if (nameError != null || rolesError != null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            val result = if (id == null) repository.create(draft).map { } else repository.update(id, draft)
            mutableState.value = when (result) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = "Organización guardada")
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = expenseErrorMessage(result.error))
            }
        }
    }

    fun archive(id: UUID) {
        viewModelScope.launch {
            mutableState.value = when (val result = repository.archive(id)) {
                is AppResult.Success -> mutableState.value.copy(message = "Organización archivada")
                is AppResult.Failure -> mutableState.value.copy(error = expenseErrorMessage(result.error))
            }
        }
    }

    fun clearErrors() {
        mutableState.value = mutableState.value.copy(nameError = null, rolesError = null)
    }
}

internal fun OrganizationRole.label(): String = when (this) {
    OrganizationRole.COOPERATIVE -> "Cooperativa"
    OrganizationRole.MILL -> "Almazara"
    OrganizationRole.SUPPLIER -> "Proveedor"
    OrganizationRole.IRRIGATION_PROVIDER -> "Comunidad de regantes"
    OrganizationRole.WORKSHOP -> "Taller"
    OrganizationRole.SERVICE_PROVIDER -> "Servicios"
    OrganizationRole.OTHER -> "Otro"
}

internal fun DocumentType.label(): String = when (this) {
    DocumentType.DELIVERY_TICKET -> "Ticket de entrega"
    DocumentType.PURCHASE_INVOICE -> "Factura de compra"
    DocumentType.PURCHASE_RECEIPT -> "Ticket de compra"
    DocumentType.PHYTOSANITARY_INVOICE -> "Factura de fitosanitarios"
    DocumentType.FERTILIZER_INVOICE -> "Factura de abono"
    DocumentType.IRRIGATION_INVOICE -> "Factura de riego"
    DocumentType.GENERIC_AGRICULTURAL_DOCUMENT -> "Documento agrícola"
}

/** Phase 12 offers purchase-side documents; delivery tickets arrive with Phase 14. */
internal val UPLOADABLE_DOCUMENT_TYPES = listOf(
    DocumentType.PURCHASE_INVOICE,
    DocumentType.PURCHASE_RECEIPT,
    DocumentType.PHYTOSANITARY_INVOICE,
    DocumentType.FERTILIZER_INVOICE,
    DocumentType.IRRIGATION_INVOICE,
    DocumentType.GENERIC_AGRICULTURAL_DOCUMENT,
)

internal fun ExpenseCategory.label(): String = when (this) {
    ExpenseCategory.LABOR -> "Mano de obra"
    ExpenseCategory.PRODUCTS -> "Productos (abono, tratamientos)"
    ExpenseCategory.MACHINERY -> "Maquinaria"
    ExpenseCategory.FUEL -> "Combustible"
    ExpenseCategory.IRRIGATION -> "Riego"
    ExpenseCategory.EXTERNAL_SERVICE -> "Servicios externos"
    ExpenseCategory.REPAIR -> "Reparaciones"
    ExpenseCategory.HARVEST -> "Cosecha"
    ExpenseCategory.TRANSPORT -> "Transporte"
    ExpenseCategory.OTHER -> "Otros"
}

internal fun OcrStatus.label(): String = when (this) {
    OcrStatus.PENDING -> "Leyendo documento"
    OcrStatus.EXTRACTED -> "Revisa los datos leídos"
    OcrStatus.NEEDS_REVIEW -> "Faltan datos: revísalos"
    OcrStatus.CONFIRMED -> "Revisado"
    OcrStatus.FAILED -> "No se pudo leer"
}

