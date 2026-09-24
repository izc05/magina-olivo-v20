package com.isivoltpro.maginaolivo.feature.deliveries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryProblem
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction
import com.isivoltpro.maginaolivo.domain.ocr.DocumentOcrRepository
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRepository
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal val DESTINATION_ROLES = setOf(OrganizationRole.COOPERATIVE, OrganizationRole.MILL)

/** One Campaign's delivered kilos and weighted yield, for the S80 header. */
data class CampaignDeliveries(
    val campaignId: UUID,
    val farmName: String?,
    val campaignName: String?,
    val summary: DeliverySummary,
)

data class DeliveriesUiState(
    val isLoading: Boolean = true,
    val deliveries: List<Delivery> = emptyList(),
    val campaigns: List<CampaignDeliveries> = emptyList(),
    val contexts: List<HarvestContext> = emptyList(),
    val destinations: List<Organization> = emptyList(),
    val openTickets: List<DocumentExtraction> = emptyList(),
    val formErrors: DeliveryFormErrors = DeliveryFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val openedTicketId: UUID? = null,
    /** Phase 19B: Jornadas of the running Campaigns, for the Pesada's explicit choice. */
    val jornadas: List<Harvest> = emptyList(),
    /** Set after "Guardar y añadir otra": the editor stays open on this form. */
    val nextForm: DeliveryForm? = null,
    val nextFormGeneration: Int = 0,
)

class DeliveriesViewModel(
    private val deliveries: DeliveryRepository,
    private val documents: DocumentOcrRepository,
    organizations: OrganizationRepository,
    private val clock: AppClock,
    harvests: HarvestRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeliveriesUiState())
    val state: StateFlow<DeliveriesUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            deliveries.observeAll()
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer las entregas") }
                .collect { rows ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        deliveries = rows,
                        campaigns = rows.groupBy { it.campaignId }.map { (campaignId, group) ->
                            CampaignDeliveries(campaignId, group.first().farmName, group.first().campaignName, DeliverySummary.of(group))
                        },
                    )
                }
        }
        viewModelScope.launch {
            deliveries.observeContexts().catch { }.collect { mutableState.value = mutableState.value.copy(contexts = it) }
        }
        viewModelScope.launch {
            organizations.observeWithAnyRole(DESTINATION_ROLES).catch { }
                .collect { mutableState.value = mutableState.value.copy(destinations = it) }
        }
        harvests?.let { repository ->
            viewModelScope.launch {
                repository.observeAll().catch { }
                    .collect { rows -> mutableState.value = mutableState.value.copy(jornadas = rows.filter { it.editable }) }
            }
        }
        viewModelScope.launch {
            documents.observeOpen().catch { }.collect { open ->
                mutableState.value = mutableState.value.copy(
                    openTickets = open.filter { it.documentType == DocumentType.DELIVERY_TICKET },
                )
            }
        }
    }

    /** With [again], the editor stays open for the next Pesada of the same day (Phase 19B). */
    fun create(form: DeliveryForm, again: Boolean = false) {
        val (draft, errors) = form.toDraft(today())
        mutableState.value = mutableState.value.copy(formErrors = errors, message = null)
        if (draft == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = deliveries.create(draft)) {
                is AppResult.Success -> if (again) {
                    // A new Jornada opened with this Pesada is the one the next Pesada joins.
                    val jornada = deliveries.observe(result.value).first()?.harvestId
                    mutableState.value.copy(
                        isSaving = false,
                        formErrors = DeliveryFormErrors(),
                        nextForm = form.nextPesada(jornada),
                        nextFormGeneration = mutableState.value.nextFormGeneration + 1,
                    )
                } else {
                    mutableState.value.copy(
                        isSaving = false,
                        message = "Pesada guardada",
                        formErrors = DeliveryFormErrors(),
                        nextForm = null,
                    )
                }
                is AppResult.Failure -> mutableState.value.failed(result.error)
            }
        }
    }

    fun editorClosed() {
        mutableState.value = mutableState.value.copy(formErrors = DeliveryFormErrors(), nextForm = null)
    }

    /** Keeps the ticket, reads it on the device, then opens its review. Nothing is recorded yet. */
    fun importTicket(sourceUri: String) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            when (val imported = documents.importDocument(DocumentType.DELIVERY_TICKET, sourceUri)) {
                is AppResult.Failure -> mutableState.value = mutableState.value.copy(
                    isSaving = false,
                    error = "No se pudo guardar el vale. Prueba con otra foto o PDF.",
                )
                is AppResult.Success -> {
                    // A failed reading is recorded on the ticket and can be repeated from its review.
                    documents.runExtraction(imported.value)
                    mutableState.value = mutableState.value.copy(isSaving = false, openedTicketId = imported.value)
                }
            }
        }
    }

    fun ticketOpened() {
        mutableState.value = mutableState.value.copy(openedTicketId = null)
    }

    fun reportProblem(message: String) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = DeliveryFormErrors())
    }

    private fun today(): LocalDate = clock.today(ZoneId.systemDefault())

    private fun DeliveriesUiState.failed(error: AppError): DeliveriesUiState {
        val problem = error.asProblem()
        return if (problem != null) copy(isSaving = false, formErrors = problem.toFormErrors())
        else copy(isSaving = false, error = deliveryErrorMessage(error))
    }
}

data class DeliveryDetailUiState(
    val isLoading: Boolean = true,
    val delivery: Delivery? = null,
    val context: HarvestContext? = null,
    val destinations: List<Organization> = emptyList(),
    val formErrors: DeliveryFormErrors = DeliveryFormErrors(),
    val yieldErrors: YieldFormErrors = YieldFormErrors(),
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val deleted: Boolean = false,
    val jornadas: List<Harvest> = emptyList(),
)

class DeliveryDetailViewModel(
    private val deliveryId: UUID,
    private val deliveries: DeliveryRepository,
    organizations: OrganizationRepository,
    private val clock: AppClock,
    harvests: HarvestRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeliveryDetailUiState())
    val state: StateFlow<DeliveryDetailUiState> = mutableState.asStateFlow()
    private var contexts: List<HarvestContext> = emptyList()

    init {
        viewModelScope.launch {
            deliveries.observe(deliveryId)
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer la entrega") }
                .collect { delivery ->
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        delivery = delivery,
                        context = contexts.firstOrNull { it.campaignId == delivery?.campaignId },
                    )
                }
        }
        viewModelScope.launch {
            deliveries.observeContexts().catch { }.collect { rows ->
                contexts = rows
                mutableState.value = mutableState.value.copy(
                    context = rows.firstOrNull { it.campaignId == mutableState.value.delivery?.campaignId },
                )
            }
        }
        viewModelScope.launch {
            organizations.observeWithAnyRole(DESTINATION_ROLES).catch { }
                .collect { mutableState.value = mutableState.value.copy(destinations = it) }
        }
        harvests?.let { repository ->
            viewModelScope.launch {
                repository.observeAll().catch { }
                    .collect { rows -> mutableState.value = mutableState.value.copy(jornadas = rows) }
            }
        }
    }

    fun update(form: DeliveryForm) {
        val (draft, errors) = form.toDraft(today())
        mutableState.value = mutableState.value.copy(formErrors = errors, message = null)
        if (draft == null) return
        mutate("Cambios guardados") { deliveries.update(deliveryId, draft) }
    }

    fun recordYield(form: YieldForm) {
        val (draft, errors) = form.toDraft(today())
        mutableState.value = mutableState.value.copy(yieldErrors = errors, message = null)
        if (draft == null) return
        mutate("Rendimiento guardado") { deliveries.recordYield(deliveryId, draft).map { } }
    }

    fun removeYield() = mutate("Rendimiento quitado") { deliveries.removeYield(deliveryId) }

    fun delete() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = deliveries.delete(deliveryId)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, deleted = true)
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = deliveryErrorMessage(result.error))
            }
        }
    }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = DeliveryFormErrors(), yieldErrors = YieldFormErrors())
    }

    private fun today(): LocalDate = clock.today(ZoneId.systemDefault())

    private fun mutate(message: String, operation: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null, message = null)
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(
                    isSaving = false,
                    message = message,
                    formErrors = DeliveryFormErrors(),
                    yieldErrors = YieldFormErrors(),
                )
                is AppResult.Failure -> {
                    val problem = result.error.asProblem()
                    when {
                        problem != null && problem.field in setOf("yield", "analysisDate") -> mutableState.value.copy(
                            isSaving = false,
                            yieldErrors = YieldFormErrors(fat = deliveryProblemMessage(problem)),
                        )
                        problem != null -> mutableState.value.copy(isSaving = false, formErrors = problem.toFormErrors())
                        else -> mutableState.value.copy(isSaving = false, error = deliveryErrorMessage(result.error))
                    }
                }
            }
        }
    }
}

data class TicketReviewUiState(
    val isLoading: Boolean = true,
    val extraction: DocumentExtraction? = null,
    val contexts: List<HarvestContext> = emptyList(),
    val destinations: List<Organization> = emptyList(),
    val formErrors: DeliveryFormErrors = DeliveryFormErrors(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val createdDeliveryId: UUID? = null,
    val closed: Boolean = false,
)

/**
 * The weight-ticket review. What the engine read is only a starting form; the Delivery is
 * created from the values the person confirms, with the one explicit reviewed command.
 */
class TicketReviewViewModel(
    private val extractionId: UUID,
    private val documents: DocumentOcrRepository,
    deliveries: DeliveryRepository,
    organizations: OrganizationRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TicketReviewUiState())
    val state: StateFlow<TicketReviewUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            documents.observe(extractionId)
                .catch { mutableState.value = mutableState.value.copy(isLoading = false, error = "No pudimos leer el vale") }
                .collect { mutableState.value = mutableState.value.copy(isLoading = false, extraction = it) }
        }
        viewModelScope.launch {
            deliveries.observeContexts().catch { }.collect { mutableState.value = mutableState.value.copy(contexts = it) }
        }
        viewModelScope.launch {
            organizations.observeWithAnyRole(DESTINATION_ROLES).catch { }
                .collect { mutableState.value = mutableState.value.copy(destinations = it) }
        }
    }

    fun confirm(form: DeliveryForm, today: LocalDate) {
        val (draft, errors) = form.toDraft(today)
        mutableState.value = mutableState.value.copy(formErrors = errors)
        if (draft == null) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = documents.confirmDeliveryTicket(extractionId, draft)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, createdDeliveryId = result.value)
                is AppResult.Failure -> {
                    val problem = result.error.asProblem()
                    if (problem != null) mutableState.value.copy(isSaving = false, formErrors = problem.toFormErrors())
                    else mutableState.value.copy(isSaving = false, error = deliveryErrorMessage(result.error))
                }
            }
        }
    }

    fun readAgain() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            val result = documents.runExtraction(extractionId)
            mutableState.value = mutableState.value.copy(
                isSaving = false,
                error = if (result is AppResult.Failure) "No se pudo leer el vale. Puedes escribir los datos a mano." else null,
            )
        }
    }

    fun discard() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, error = null)
            mutableState.value = when (val result = documents.discard(extractionId)) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, closed = true)
                is AppResult.Failure -> mutableState.value.copy(isSaving = false, error = deliveryErrorMessage(result.error))
            }
        }
    }

    fun clearFormErrors() {
        mutableState.value = mutableState.value.copy(formErrors = DeliveryFormErrors())
    }
}

private fun AppError.asProblem(): DeliveryProblem? =
    (this as? AppError.Validation)?.let { DeliveryProblem(it.field ?: "parcels", it.code) }

internal fun deliveryErrorMessage(error: AppError): String = when (error) {
    is AppError.Validation -> deliveryProblemMessage(DeliveryProblem(error.field ?: "parcels", error.code))
    is AppError.NotFound -> "La entrega o el vale ya no está en este dispositivo"
    is AppError.Conflict -> when (error.resource) {
        "no_running_campaign" -> "Esta finca no tiene una campaña activa o en recolección"
        "closed_campaign" -> "La campaña está cerrada: esta entrega ya es histórico y no se modifica"
        "archived_farm" -> "La finca está archivada"
        "already_confirmed" -> "Este vale ya se confirmó como entrega"
        "linked_to_delivery" -> "Este vale pertenece a una entrega y se conserva con ella"
        else -> "No se pudo guardar por un conflicto con otros datos"
    }
    is AppError.Storage -> "No se pudo guardar en el dispositivo. Inténtalo de nuevo."
    else -> "Algo no ha ido bien. Inténtalo de nuevo."
}
