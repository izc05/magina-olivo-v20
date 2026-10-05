package com.isivoltpro.maginaolivo.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the expense form holds while a person is typing: text, not yet money. */
data class ExpenseForm(
    val date: String = "",
    val amount: String = "",
    val concept: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val supplierOrganizationId: UUID? = null,
    val supplierText: String = "",
    /** #451: the supplier this Gasto was saved with and the name it was saved under. */
    val recordedSupplierId: UUID? = null,
    val recordedSupplierName: String = "",
    val farmId: UUID? = null,
    val parcelId: UUID? = null,
    val activityId: UUID? = null,
    val invoiceNumber: String = "",
    val lines: List<LineForm> = emptyList(),
    val notes: String = "",
    /** Phase 19F: kept from the Expense so editing it never unlinks it from its Jornada. */
    val harvestId: UUID? = null,
    val campaignId: UUID? = null,
    val currency: String = "EUR",
)

data class LineForm(
    val product: String = "",
    val quantity: String = "",
    val unit: String = "",
    val total: String = "",
    /** The current form edits the line total, so retain the recorded unit price unchanged. */
    val unitPriceMinor: Long? = null,
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
    val supportedCurrency = runCatching { java.util.Currency.getInstance(currency).defaultFractionDigits >= 0 }.getOrDefault(false)
    val amountMinor = Money.parseMinor(amount, currency)
    val parsedLines = lines.filter { it.product.isNotBlank() }.map { line ->
        PurchaseLine(
            productName = line.product.trim(),
            quantity = line.quantity.replace(',', '.').trim().toDoubleOrNull(),
            unit = line.unit.trim().ifEmpty { null },
            unitPriceMinor = line.unitPriceMinor,
            lineTotalMinor = Money.parseMinor(line.total, currency),
        )
    }
    val badLine = lines.any { line ->
        line.product.isNotBlank() &&
            ((line.quantity.isNotBlank() && line.quantity.replace(',', '.').trim().toDoubleOrNull() == null) ||
                (line.total.isNotBlank() && Money.parseMinor(line.total, currency) == null))
    }
    val errors = ExpenseFormErrors(
        date = if (parsedDate == null) "Elige una fecha" else null,
        amount = when {
            !supportedCurrency -> "La moneda histórica $currency no admite edición. Se conserva el importe original."
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
        currency = currency,
        campaignId = campaignId,
        supplierOrganizationId = supplierOrganizationId,
        supplierText = supplierText.trim().ifEmpty { null },
        farmId = farmId,
        parcelId = parcelId,
        activityId = activityId,
        invoiceNumber = invoiceNumber.trim().ifEmpty { null },
        lines = parsedLines,
        notes = notes.trim().ifEmpty { null },
        harvestId = harvestId,
    ) to errors
}

internal fun Expense.toForm() = ExpenseForm(
    date = expenseDate.toString(),
    amount = Money.editable(amountMinor, currency),
    currency = currency,
    campaignId = campaignId,
    concept = concept,
    category = category,
    supplierOrganizationId = supplierOrganizationId,
    supplierText = if (supplierOrganizationId == null) supplierName.orEmpty() else "",
    recordedSupplierId = supplierOrganizationId,
    recordedSupplierName = supplierName.orEmpty(),
    farmId = farmId,
    parcelId = parcelId,
    activityId = activityId,
    harvestId = harvestId,
    invoiceNumber = invoiceNumber.orEmpty(),
    lines = lines.map {
        LineForm(
            product = it.productName,
            quantity = it.quantity?.let { quantity -> quantity.toString().removeSuffix(".0").replace('.', ',') }.orEmpty(),
            unit = it.unit.orEmpty(),
            total = Money.editable(it.lineTotalMinor, currency),
            unitPriceMinor = it.unitPriceMinor,
        )
    },
    notes = notes.orEmpty(),
)

/**
 * #456: why a DRAFT did not become counted money. A relation that no longer holds sends it back
 * to review, said plainly; it stays a DRAFT and counts nothing until then.
 */
internal fun postErrorMessage(error: AppError): String = when {
    error is AppError.Validation && error.field == "expenseDate" && error.code == "future" ->
        "La fecha de este gasto es posterior a hoy. Corrígela antes de confirmarlo."
    error is AppError.Validation && error.code != "campaign_closed" && error.code != "archived" &&
        error.field in setOf("farmId", "parcelId", "activityId", "campaignId", "harvestId", "supplierOrganizationId") ->
        "Este gasto necesita revisar su finca/parcela/trabajo antes de confirmarlo."
    else -> expenseErrorMessage(error)
}

internal fun expenseErrorMessage(error: AppError): String = when (error) {
    is AppError.Validation -> when {
        error.code == "campaign_closed" -> "La campaña está cerrada: el coste histórico no se modifica."
        error.code == "below_paid" -> "Debes corregir los pagos antes de reducir el coste por debajo de lo pagado."
        error.field == "appliedPrice" && error.code == "confirm_missing_prices" ->
            "Confirma primero los precios de la maquinaria histórica de este día antes de guardar el alquiler."
        // #433: the cost of a work follows that work.
        error.code == "activity_general" ->
            "Ese trabajo es general de la finca: su gasto va «Fuera de campaña», no en la recogida."
        error.field == "campaignId" && error.code == "not_in_activity" -> "Ese trabajo pertenece a otra campaña."
        error.field == "parcelId" && error.code == "not_in_activity" -> "Ese trabajo no se hizo en la parcela elegida."
        error.field == "activityId" && error.code == "not_in_day" -> "Ese trabajo no es de esta jornada de recogida."
        error.field == "supplierOrganizationId" && error.code == "archived" ->
            "Ese proveedor está archivado. Elige otro proveedor o escríbelo a mano."
        error.code == "activity_cost_locked" ->
            "Este coste es de su trabajo. Para separarlo usa «Conservar como gasto independiente»."
        else -> when (error.field) {
        "parcelId" -> "La parcela elegida no pertenece a esa finca"
        "activityId" -> "El trabajo elegido no pertenece a esa finca"
        "amountMinor" -> "El importe debe ser mayor que cero"
        "concept" -> "Describe el gasto"
        "farmId" -> "La finca ya no está disponible"
        else -> "Revisa los datos del gasto"
        }
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
    /** Campaigns of the selected Farm, to name the explicit «Gasto de recogida» choice. */
    val campaigns: List<Campaign> = emptyList(),
    /**
     * #411: false only for a source that never reads campaigns. Until the first read lands it stays
     * true with no [campaignsFor], so «no running campaign» is never assumed while loading.
     */
    val campaignsTracked: Boolean = true,
    /** #411: the Farm [campaigns] were last read for; until it matches, «no running campaign» is unknown. */
    val campaignsFor: UUID? = null,
) {
    /** #411: whether [campaigns] already answer for [farmId] (an empty list then really means none). */
    fun campaignsKnownFor(farmId: UUID?): Boolean = !campaignsTracked || campaignsFor == farmId
}

/**
 * Owner decision 2026-10-03 (CR-012 Slice 4 amendment): the campaign an expense may be put on
 * by the farmer's explicit choice in the form — the campaign it already carries (editing), or
 * else the running recolección of its Farm. Null when there is nothing to choose: no Farm, no
 * running campaign, or a Jornada-linked expense whose campaign comes from its day.
 */
internal fun RelationOptions.recollectionCampaignFor(form: ExpenseForm, linkedCampaignId: UUID? = null): Campaign? {
    if (form.harvestId != null || form.farmId == null) return null
    val onFarm = campaigns.filter { it.farmId == form.farmId }
    return (form.campaignId ?: linkedCampaignId)?.let { id -> onFarm.firstOrNull { it.id == id } }
        ?: onFarm.firstOrNull { it.status.isRunning }
}

/**
 * Cuaderno → Gasto with a running recolección: the form starts on «Gasto de recogida», shown
 * and changeable. Never applied to an expense that already has a choice or a Jornada.
 */
internal fun ExpenseForm.withRecollectionPreselected(options: RelationOptions): ExpenseForm {
    if (campaignId != null || harvestId != null || farmId == null) return this
    val running = options.campaigns.firstOrNull { it.farmId == farmId && it.status.isRunning } ?: return this
    return copy(campaignId = running.id)
}

/**
 * #433: the expense follows the work it is tied to. Its Parcel stays only if the work was done
 * there, its Campaign is the work's (none for general work), and a recolección day stays only
 * when the work is of that day's Campaign (the form carries the day's Campaign as stored).
 * Dropping the work leaves the expense
 * outside any Campaign unless a day still gives it one, so the person chooses again.
 */
internal fun ExpenseForm.withActivity(activity: Activity?): ExpenseForm {
    if (activity == null) return copy(activityId = null, campaignId = if (harvestId != null) campaignId else null)
    val targets = activity.targets.map { it.parcelId }.toSet()
    return copy(
        activityId = activity.id,
        parcelId = parcelId?.takeIf { it in targets },
        campaignId = activity.campaignId,
        // Codex #522: a day of another Campaign would make the expense unsavable; it goes.
        harvestId = harvestId?.takeIf { activity.campaignId != null && activity.campaignId == campaignId },
    )
}

/** «Campaña 2026/27» whether the farmer typed the word or only the years. */
internal fun Campaign.choiceLabel(): String =
    if (name.startsWith("Campaña", ignoreCase = true)) name else "Campaña $name"

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
    /** #380: finished saves; the editor closes when this rises. */
    val saveCount: Int = 0,
    /** #415: set once after «Guardar y añadir foto», so the screen opens that Gasto. */
    val savedExpenseToOpen: UUID? = null,
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
                // Weight tickets are reviewed with Deliveries, where they become kilos, not money.
                mutableState.value = mutableState.value.copy(
                    openDocuments = open.filter { it.documentType != DocumentType.DELIVERY_TICKET },
                )
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

    /** #415: «Guardar y añadir foto» — the same save, then the Gasto opens where its photo goes. */
    fun createAndOpen(form: ExpenseForm) {
        val (draft, errors) = form.toDraft()
        mutableState.value = mutableState.value.copy(formErrors = errors)
        if (draft == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            mutableState.value = when (val result = expenses.create(draft)) {
                is AppResult.Success -> mutableState.value.copy(
                    isSaving = false, savedExpenseToOpen = result.value, formErrors = ExpenseFormErrors(),
                )
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = expenseErrorMessage(result.error))
            }
        }
    }

    fun savedExpenseOpened() {
        mutableState.value = mutableState.value.copy(savedExpenseToOpen = null)
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
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message, saveCount = mutableState.value.saveCount + 1, formErrors = ExpenseFormErrors())
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
    private val campaigns: CampaignRepository? = null,
) {
    private val selectedFarm = MutableStateFlow<UUID?>(null)
    private var options = RelationOptions(campaignsTracked = campaigns != null)

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
            campaigns?.let { source ->
                launch {
                    selectedFarm.flatMapLatest { farmId ->
                        (farmId?.let(source::observeForFarm) ?: flowOf(emptyList())).map { farmId to it }
                    }.catch { }.collect { (farmId, list) ->
                        options = options.copy(campaigns = list, campaignsFor = farmId)
                        onChange(options)
                    }
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
    /** #380: finished saves; the editor closes when this rises. */
    val saveCount: Int = 0,
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
    fun post() = mutate("Gasto confirmado", ::postErrorMessage) { expenses.post(expenseId) }

    /** #429: the cost of a work no longer done really was spent: it stays, as a Gasto of its own. */
    fun keepAsIndependent() = mutate("Conservado como gasto independiente") { expenses.keepAsIndependent(expenseId) }

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

    private fun mutate(
        message: String,
        errorText: (AppError) -> String = ::expenseErrorMessage,
        operation: suspend () -> AppResult<Unit>,
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = message, saveCount = mutableState.value.saveCount + 1, formErrors = ExpenseFormErrors())
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = errorText(result.error))
            }
        }
    }
}

/** #451: the supplier as the form shows it; see [com.isivoltpro.maginaolivo.feature.deliveries.destinationShown]. */
internal data class SupplierShown(val name: String?, val currentName: String?)

internal fun ExpenseForm.supplierShown(suppliers: List<Organization>): SupplierShown {
    val chosen = supplierOrganizationId ?: return SupplierShown(null, null)
    val current = suppliers.firstOrNull { it.id == chosen }?.name
    if (chosen != recordedSupplierId || recordedSupplierName.isEmpty()) return SupplierShown(current, null)
    return SupplierShown(recordedSupplierName, current?.takeIf { it != recordedSupplierName })
}
