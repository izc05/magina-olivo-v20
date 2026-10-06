package com.isivoltpro.maginaolivo.feature.harvests

import com.isivoltpro.maginaolivo.domain.expense.DayCostKind
import com.isivoltpro.maginaolivo.domain.expense.DayCostRepository
import com.isivoltpro.maginaolivo.domain.expense.RecollectionRates
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestProblem
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentDraftLine
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseRepository
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.expense.UnlinkedDayCosts
import com.isivoltpro.maginaolivo.domain.expense.DayCostRole
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentLine
import com.isivoltpro.maginaolivo.domain.equipment.EquipmentRepository
import com.isivoltpro.maginaolivo.domain.harvest.Jornada
import com.isivoltpro.maginaolivo.domain.machinery.Machine
import com.isivoltpro.maginaolivo.domain.machinery.MachineRepository
import com.isivoltpro.maginaolivo.domain.labour.CrewDraft
import com.isivoltpro.maginaolivo.domain.labour.LabourChange
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourRepository
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.labour.Worker
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The truthful totals of one Campaign, for the S70 header. */
data class CampaignHarvest(
    val campaignId: UUID?,
    val farmName: String?,
    val campaignName: String?,
    val summary: HarvestSummary,
)

data class HarvestsUiState(
    val isLoading: Boolean = true,
    val harvests: List<Harvest> = emptyList(),
    val campaigns: List<CampaignHarvest> = emptyList(),
    val contexts: List<HarvestContext> = emptyList(),
    val formErrors: HarvestFormErrors = HarvestFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class HarvestsViewModel(
    private val harvests: HarvestRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HarvestsUiState())
    val state: StateFlow<HarvestsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            harvests.observeAll()
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer los días de recolección") }
                .collect { rows ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        harvests = rows,
                        campaigns = rows.groupBy { it.campaignId }.map { (campaignId, group) ->
                            CampaignHarvest(campaignId, group.first().farmName, group.first().campaignName, HarvestSummary.of(group))
                        },
                    )
                }
        }
        viewModelScope.launch {
            harvests.observeContexts().catch { }.collect { mutableState.value = mutableState.value.copy(contexts = it) }
        }
    }

    fun create(form: HarvestForm) {
        val (draft, errors) = form.toDraft(clock.today(ZoneId.systemDefault()))
        mutableState.value = mutableState.value.copy(formErrors = errors, message = null)
        if (draft == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = harvests.create(draft)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = "Día de recolección guardado", formErrors = HarvestFormErrors())
                is AppResult.Failure -> mutableState.value.failed(result.error)
            }
        }
    }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = HarvestFormErrors())
    }

    private fun HarvestsUiState.failed(error: AppError): HarvestsUiState {
        val problem = error.asProblem()
        return if (problem != null) copy(isSaving = false, formErrors = problem.toFormErrors())
        else copy(isSaving = false, error = harvestErrorMessage(error))
    }
}

data class HarvestDetailUiState(
    val isLoading: Boolean = true,
    val harvest: Harvest? = null,
    val context: HarvestContext? = null,
    val formErrors: HarvestFormErrors = HarvestFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val deleted: Boolean = false,
    /** Phase 19B: the Pesadas linked to this Jornada, oldest first. */
    val pesadas: List<Delivery> = emptyList(),
    /** False until this day's Pesadas have been read: deleting the day is only offered once it is true. */
    val pesadasLoaded: Boolean = true,
    /** True when the Pesadas could not be read: the day is never treated as having none. */
    val pesadasReadFailed: Boolean = false,
    /** Phase 19D: the jornales of this Jornada and the people to choose from. */
    val labour: List<LabourEntry> = emptyList(),
    /** False until this day's jornales have been read: «none» is only said once it is true. */
    val labourLoaded: Boolean = true,
    /** True when this day's jornales could not be read: never shown as «no jornales». */
    val labourReadFailed: Boolean = false,
    val workers: List<Worker> = emptyList(),
    val previousCrew: List<UUID> = emptyList(),
    val labourMessage: String? = null,
    val labourError: String? = null,
    /** Phase 19E: equipment used on this Jornada, and the registered machines to pick from. */
    val equipment: List<EquipmentLine> = emptyList(),
    val equipmentLoaded: Boolean = true,
    val equipmentReadFailed: Boolean = false,
    val machines: List<Machine> = emptyList(),
    val equipmentSaved: Int = 0,
    val equipmentError: String? = null,
    /** Phase 19F: the ledger Expenses linked to this Jornada. */
    val costs: List<Expense> = emptyList(),
    val costsLoaded: Boolean = true,
    val costsReadFailed: Boolean = false,
    val costSaved: Int = 0,
    val costError: String? = null,
    /** Set when a cost was saved with "añadir foto": the Expense to open for its ticket. */
    val openExpenseId: UUID? = null,
    /** CR-010 A3: the Farm's recollection prices, null until read or where not wired. */
    val rates: RecollectionRates? = null,
    val ratesLoaded: Boolean = true,
    val ratesReadFailed: Boolean = false,
    val ratesSaved: Int = 0,
    val ratesError: String? = null,
    /** CR-010 A3: hand-typed costs of this Farm and date linked to no day (ambiguous). */
    val unlinkedCosts: List<Expense> = emptyList(),
    /** #475: people of this Campaign with a payment recorded; their day's jornales can only add. */
    val paidWorkers: Set<UUID> = emptySet(),
    /** #380: finished saves; the editor closes when this rises. */
    val saveCount: Int = 0,
)

class HarvestDetailViewModel(
    private val harvestId: UUID,
    private val harvests: HarvestRepository,
    private val clock: AppClock,
    deliveries: DeliveryRepository? = null,
    private val labour: LabourRepository? = null,
    private val equipment: EquipmentRepository? = null,
    machines: MachineRepository? = null,
    private val expenses: ExpenseRepository? = null,
    private val dayCosts: DayCostRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HarvestDetailUiState(labourLoaded = labour == null, pesadasLoaded = deliveries == null,
        equipmentLoaded = equipment == null, costsLoaded = expenses == null, ratesLoaded = dayCosts == null))
    val state: StateFlow<HarvestDetailUiState> = mutableState.asStateFlow()
    private var contexts: List<HarvestContext> = emptyList()
    private var allExpenses: List<Expense> = emptyList()

    init {
        viewModelScope.launch {
            harvests.observe(harvestId)
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer el día de recolección") }
                .collect { harvest ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        // #502: an automatic day removed once nothing backs it closes like a deleted one.
                        deleted = mutableState.value.deleted || (mutableState.value.harvest != null && harvest == null),
                        harvest = harvest,
                        context = contexts.firstOrNull { it.campaignId == harvest?.campaignId },
                    )
                    refreshUnlinked()
                    harvest?.farmId?.let { observeRates(it) }
                    harvest?.campaignId?.let { observePayments(it) }
                }
        }
        viewModelScope.launch {
            harvests.observeContexts().catch { }.collect { rows ->
                contexts = rows
                mutableState.value = mutableState.value.copy(
                    context = rows.firstOrNull { it.campaignId == mutableState.value.harvest?.campaignId },
                )
            }
        }
        deliveries?.let { repository ->
            viewModelScope.launch {
                repository.observeAll()
                    .catch { mutableState.value = mutableState.value.copy(pesadasLoaded = true, pesadasReadFailed = true) }
                    .collect { rows ->
                        mutableState.value = mutableState.value.copy(
                            pesadas = Jornada.linkedTo(harvestId, rows), pesadasLoaded = true, pesadasReadFailed = false,
                        )
                    }
            }
        }
        labour?.let { repository ->
            viewModelScope.launch {
                repository.observeForHarvest(harvestId)
                    .catch { mutableState.value = mutableState.value.copy(labourLoaded = true, labourReadFailed = true) }
                    .collect { mutableState.value = mutableState.value.copy(labour = it, labourLoaded = true, labourReadFailed = false) }
            }
            viewModelScope.launch {
                repository.observeWorkers().catch { }.collect { mutableState.value = mutableState.value.copy(workers = it) }
            }
            viewModelScope.launch {
                // Read first, then update the state as it is now. Copying before the read and
                // writing that copy back after it reverted a day that loaded meanwhile to
                // «loading» for good (device check, build 683: Jornal stuck on a spinner).
                val crew = repository.previousCrew(harvestId)
                mutableState.update { it.copy(previousCrew = crew) }
            }
        }
        equipment?.let { repository ->
            viewModelScope.launch {
                repository.observeForHarvest(harvestId).catch { mutableState.value = mutableState.value.copy(equipmentLoaded = true, equipmentReadFailed = true, equipmentError = "No pudimos leer la maquinaria") }
                    .collect { mutableState.value = mutableState.value.copy(equipment = it, equipmentLoaded = true, equipmentReadFailed = false) }
            }
        }
        machines?.let { repository ->
            viewModelScope.launch {
                repository.observeActive().catch { }.collect { mutableState.value = mutableState.value.copy(machines = it) }
            }
        }
        expenses?.let { repository ->
            viewModelScope.launch {
                repository.observeForHarvest(harvestId).catch { mutableState.value = mutableState.value.copy(costsLoaded = true, costsReadFailed = true, costError = "No pudimos leer los gastos") }.collect { mutableState.value = mutableState.value.copy(costs = it, costsLoaded = true, costsReadFailed = false) }
            }
            if (dayCosts != null) {
                viewModelScope.launch {
                    repository.observeAll().catch { }.collect { rows ->
                        allExpenses = rows
                        refreshUnlinked()
                    }
                }
            }
        }
    }

    private fun refreshUnlinked() {
        val harvest = mutableState.value.harvest
        mutableState.value = mutableState.value.copy(
            unlinkedCosts = harvest?.let { UnlinkedDayCosts.of(harvestId, it.farmId, it.harvestDate, allExpenses, it.campaignId) }.orEmpty(),
        )
    }

    private var paymentsCampaign: UUID? = null

    private fun observePayments(campaignId: UUID) {
        val repository = labour ?: return
        if (paymentsCampaign == campaignId) return
        paymentsCampaign = campaignId
        viewModelScope.launch {
            repository.observePayments(campaignId).catch { }.collect { rows ->
                mutableState.value = mutableState.value.copy(paidWorkers = rows.map { it.workerId }.toSet())
            }
        }
    }

    /** CR-010 A3: the farmer says an unlinked cost of this date belongs to this day, and how it counts (#475). */
    fun linkCost(expenseId: UUID, role: DayCostRole = DayCostRole.ADDITIVE) {
        val repository = dayCosts ?: return
        viewModelScope.launch {
            val result = repository.linkToDay(expenseId, harvestId, role)
            if (result is AppResult.Failure) {
                mutableState.value = mutableState.value.copy(
                    costError = if (result.error == AppError.Conflict("campaign_closed")) {
                        "La campaña está cerrada: sus gastos ya no cambian."
                    } else if (result.error == AppError.Conflict("outside_campaign")) {
                        "Ese gasto está «Fuera de campaña»: no se enlaza a la recogida."
                    } else if (machineryPriceErrorMessage(result.error) != null) {
                        machineryPriceErrorMessage(result.error)!!
                    } else {
                        dayCostRoleErrorMessage(result.error) ?: "No se pudo enlazar el gasto al día de recolección."
                    },
                )
            }
        }
    }

    private var ratesFarm: UUID? = null

    private fun observeRates(farmId: UUID) {
        val repository = dayCosts ?: return
        if (ratesFarm == farmId) return
        ratesFarm = farmId
        viewModelScope.launch {
            repository.observeRates(farmId).catch { mutableState.value = mutableState.value.copy(ratesLoaded = true, ratesReadFailed = true) }
                .collect { mutableState.value = mutableState.value.copy(rates = it, ratesLoaded = true, ratesReadFailed = false) }
        }
    }

    /** CR-010 A3: the Farm's prices; the running Campaign's day costs follow them. */
    fun saveRates(rates: RecollectionRates) {
        val repository = dayCosts ?: return
        val farmId = mutableState.value.harvest?.farmId ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, ratesError = null)
            mutableState.value = when (repository.saveRates(farmId, rates)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, ratesSaved = mutableState.value.ratesSaved + 1)
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, ratesError = "No se pudieron guardar los precios.")
            }
        }
    }

    /** CR-010 A3 collision: the calculation counts; the hand-typed cost stays as a draft. */
    fun preferCalculated(kind: DayCostKind) {
        val repository = dayCosts ?: return
        viewModelScope.launch {
            val result = repository.preferCalculated(harvestId, kind)
            if (result is AppResult.Failure) {
                mutableState.value = mutableState.value.copy(
                    costError = if (result.error == AppError.Conflict("campaign_closed")) {
                        "La campaña está cerrada: sus gastos ya no cambian."
                    } else if (machineryPriceErrorMessage(result.error) != null) {
                        machineryPriceErrorMessage(result.error)!!
                    } else {
                        "No se pudo cambiar el gasto que cuenta."
                    },
                )
            }
        }
    }

    /** Phase 19F: a posted Expense of this Jornada, in the one ledger. */
    fun addCost(
        kind: JornadaExpenseKind,
        amountMinor: Long,
        concept: String?,
        openAfter: Boolean,
        role: DayCostRole = DayCostRole.ADDITIVE,
    ) {
        val repository = expenses ?: return
        val harvest = mutableState.value.harvest ?: return
        val state = mutableState.value
        val currencyContext = state.newCostCurrencyContext()
        val currency = currencyContext.currency ?: run {
            mutableState.value = state.copy(costError = currencyContext.error)
            return
        }
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, costError = null)
            val draft = ExpenseDraft(
                expenseDate = harvest.harvestDate,
                concept = kind.concept(concept),
                category = kind.category,
                amountMinor = amountMinor,
                currency = currency,
                farmId = harvest.farmId,
                campaignId = harvest.campaignId,
                harvestId = harvest.id,
                dayCostRole = role,
            )
            mutableState.value = when (val result = repository.create(draft)) {
                is AppResult.Success -> mutableState.value.copy(
                    isSaving = false,
                    costSaved = mutableState.value.costSaved + 1,
                    openExpenseId = if (openAfter) result.value else null,
                )
                is AppResult.Failure -> mutableState.value.copy(isSaving = false,
                    costError = machineryPriceErrorMessage(result.error) ?: dayCostRoleErrorMessage(result.error)
                        ?: "No se pudo guardar el gasto. Revisa el importe.")
            }
        }
    }

    fun expenseOpened() {
        mutableState.value = mutableState.value.copy(openExpenseId = null)
    }

    /** Phase 19E: the whole equipment sheet in one save. */
    fun saveEquipment(lines: List<EquipmentDraftLine>) {
        val repository = equipment ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, equipmentError = null)
            mutableState.value = when (val result = repository.replaceForHarvest(harvestId, lines)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, equipmentSaved = mutableState.value.equipmentSaved + 1)
                is AppResult.Failure -> mutableState.value.copy(
                    isSaving = false,
                    equipmentError = when (result.error) {
                        is AppError.Conflict -> "La campaña está cerrada: este día de recolección ya es histórico"
                        is AppError.Validation -> when (result.error.code) {
                            "confirm_missing_prices" -> "Confirma primero los precios que faltan en la maquinaria histórica de este día."
                            "confirm_before_recompose" -> "Hay maquinaria con coste sin confirmar. Confirma los precios antes de cambiar una composición que ya tiene un coste contabilizado."
                            "overflow" -> "El total es demasiado grande. Reduce el precio o la cantidad."
                            "archived_machine" -> "Esa máquina está archivada: solo se conserva en los días en que ya trabajó."
                            "currency_mismatch", "ambiguous_historical_currency" -> "La moneda no coincide con el coste histórico de este día. Revisa los precios."
                            else -> "Revisa la maquinaria: cantidades de 1 a 50 y un nombre para «Otra»"
                        }
                        else -> "No se pudo guardar en el dispositivo. Inténtalo de nuevo."
                    },
                )
            }
        }
    }

    /** CR-012: identified attendance, historical price and optional initial payment atomically. */
    fun recordCrew(draft: CrewDraft) =
        labourCall({ if (it == 1) "1 jornal guardado" else "$it jornales guardados" }) {
            labour!!.recordCrew(draft)
        }

    fun updateLabour(entryId: UUID, change: LabourChange) = labourCall({ "Jornal corregido" }) { labour!!.update(entryId, change) }

    fun removeLabour(entryId: UUID) = labourCall({ "Jornal quitado" }) { labour!!.remove(entryId) }

    fun addWorker(name: String) = labourCall({ "Persona añadida" }) { labour!!.addWorker(name) }

    fun clearLabourMessages() {
        mutableState.value = mutableState.value.copy(labourMessage = null, labourError = null)
    }

    private fun <T> labourCall(message: (T) -> String, operation: suspend () -> AppResult<T>) {
        if (labour == null || mutableState.value.isSaving) return
        mutableState.value = mutableState.value.copy(isSaving = true, labourError = null, labourMessage = null)
        viewModelScope.launch {
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, labourMessage = message(result.value))
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, labourError = labourErrorMessage(result.error))
            }
        }
    }

    fun update(form: HarvestForm) {
        val (draft, errors) = form.toDraft(clock.today(ZoneId.systemDefault()))
        mutableState.value = mutableState.value.copy(formErrors = errors, message = null)
        if (draft == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = harvests.update(harvestId, draft)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = "Cambios guardados", saveCount = mutableState.value.saveCount + 1, formErrors = HarvestFormErrors())
                is AppResult.Failure -> {
                    val problem = result.error.asProblem()
                    if (problem != null) mutableState.value.copy(isSaving = false, formErrors = problem.toFormErrors())
                    else mutableState.value.copy(isSaving = false, error = harvestErrorMessage(result.error))
                }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = harvests.delete(harvestId)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, deleted = true)
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = harvestDeleteErrorMessage(result.error))
            }
        }
    }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = HarvestFormErrors())
    }
}

/** A validation the repository refused, shown next to the field it concerns. */
private fun AppError.asProblem(): HarvestProblem? =
    (this as? AppError.Validation)?.let { HarvestProblem(it.field ?: "parcels", it.code) }

internal fun machineryPriceErrorMessage(error: AppError): String? =
    if (error is AppError.Validation && error.field == "appliedPrice" && error.code == "confirm_missing_prices")
        "Confirma primero los precios que faltan en la maquinaria histórica de este día."
    else null

internal fun labourErrorMessage(error: AppError): String = when (error) {
    is AppError.Validation -> when (error.code) {
        "empty" -> "Elige una persona"
        "campaign_closed" -> "La campaña está cerrada: el coste histórico no se modifica. Puedes registrar pagos pendientes."
        "below_paid" -> "Debes corregir los pagos antes de reducir el coste por debajo de lo pagado."
        "price_required" -> "Confirma el precio del jornal antes de guardar."
        "confirm_missing_prices" -> "Confirma primero los precios de los jornales históricos de este día."
        "overflow" -> "El importe es demasiado grande. Revisa el precio y la duración."
        "exceeds_pending" -> "El importe supera el pendiente actual. Revisa el saldo de esta persona."
        "future" -> if (error.field == "paymentDate") "La fecha del pago no puede ser futura." else "La fecha no puede ser futura."
        "anonymous_not_allowed" -> "Elige una persona para registrar el jornal."
        "currency_mismatch" -> "La moneda debe coincidir con el coste confirmado."
        "already_recorded" -> "Alguna de esas personas ya tiene su jornal en este día de recolección"
        "required" -> when (error.field) { "name" -> "Escribe el nombre y apellidos"; "worker", "workers" -> "Elige una persona"; "appliedRate" -> "Confirma el precio del jornal"; else -> "Escribe las horas por persona" }
        "too_long" -> if (error.field == "name") "El nombre es demasiado largo" else "No puede pasar de 24 horas por persona"
        "not_positive" -> if (error.field == "amount" || error.field == "amountMinor") "El importe debe ser mayor que cero" else "El número de personas debe ser mayor que cero"
        "too_many" -> "Son demasiadas personas para un día"
        "one_person" -> "Una persona con nombre cuenta un solo jornal"
        else -> "Revisa los jornales"
    }
    is AppError.Conflict -> when (error.resource) {
        "campaign_closed", "closed_campaign" -> "La campaña está cerrada: el coste histórico no se modifica."
        else -> "No se pudo guardar por un conflicto con otros datos. Revisa el movimiento."
    }
    is AppError.NotFound -> "Ese jornal ya no está en este dispositivo"
    else -> "No se pudo guardar en el dispositivo. Inténtalo de nuevo."
}

internal fun harvestDeleteErrorMessage(error: AppError): String =
    if (error is AppError.Validation && error.field == "amount" && error.code == "below_paid") {
        "No puedes eliminar este día porque dejaría pagos de jornales por encima del coste registrado. Revisa primero los pagos de la campaña."
    } else {
        harvestErrorMessage(error)
    }

internal fun harvestErrorMessage(error: AppError): String = when (error) {
    is AppError.Validation -> harvestProblemMessage(HarvestProblem(error.field ?: "parcels", error.code))
    is AppError.NotFound -> "El día de recolección o la finca ya no está en este dispositivo"
    is AppError.Conflict -> when (error.resource) {
        "no_running_campaign" -> "Esta finca no tiene una campaña activa o en recolección"
        "closed_campaign" -> "La campaña está cerrada: este día de recolección ya es histórico y no se modifica"
        "archived_farm" -> "La finca está archivada"
        com.isivoltpro.maginaolivo.domain.harvest.HARVEST_HAS_DELIVERIES ->
            "Este día tiene pesadas. Muévelas, corrígelas o elimínalas antes de eliminar la jornada."
        else -> "No se pudo guardar por un conflicto con otros datos"
    }
    is AppError.Storage -> "No se pudo guardar en el dispositivo. Inténtalo de nuevo."
    else -> "Algo no ha ido bien. Inténtalo de nuevo."
}

/** #475: why a cost could not be saved as the farmer chose to count it. */
internal fun dayCostRoleErrorMessage(error: AppError): String? =
    if (error !is AppError.Validation || error.field != "dayCostRole") null else when (error.code) {
        "labour_paid" -> "Este día tiene pagos por persona: el importe solo puede añadirse al cálculo de jornales."
        "nothing_to_replace" -> "Este día no tiene coste calculado que sustituir: el importe se añade."
        else -> "Este gasto no puede sustituir el cálculo del día."
    }

/** #475: the calculated costs a hand-typed cost of this day could replace. */
internal fun HarvestDetailUiState.calculatedKinds(): Set<com.isivoltpro.maginaolivo.domain.expense.DayCostKind> =
    costs.mapNotNull { expense -> com.isivoltpro.maginaolivo.domain.expense.DayCostKind.entries.firstOrNull { it.origin == expense.origin } }.toSet()

/** #475: someone who worked this day has a payment recorded in the Campaign. */
internal val HarvestDetailUiState.labourPaid: Boolean
    get() = labour.any { it.workerId != null && it.workerId in paidWorkers }
