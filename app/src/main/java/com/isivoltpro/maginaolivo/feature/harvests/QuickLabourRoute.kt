package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.withTransaction
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.*
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.labour.*
import com.isivoltpro.maginaolivo.feature.expenses.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class QuickLabourState(
    val options: RelationOptions = RelationOptions(), val contexts: List<HarvestContext> = emptyList(),
    val workers: List<Worker> = emptyList(), val rates: RecollectionRates? = null,
    val contextsLoaded: Boolean = false, val workersLoaded: Boolean = false, val ratesLoaded: Boolean = false,
    val isSaving: Boolean = false, val errors: ExpenseFormErrors = ExpenseFormErrors(),
    val error: String? = null, val saved: Boolean = false,
    val dayDate: String? = null, val dayCurrency: LabourCurrencyContext? = null, val recorded: Set<UUID> = emptySet(),
)

/** Failures must roll back the day too, rather than committing a failed crew's empty day. */
private class QuickSaveRejected(val failure: AppResult.Failure) : RuntimeException()
internal suspend fun <T> quickLabourTransaction(persistence: LocalPersistence, operation: suspend () -> AppResult<T>): AppResult<T> = try {
    withTimeout(10_000) {
        persistence.database.withTransaction {
            when (val result = operation()) {
                is AppResult.Success -> result
                is AppResult.Failure -> throw QuickSaveRejected(result)
            }
        }
    }
} catch (rejected: QuickSaveRejected) { rejected.failure }
catch (timedOut: TimeoutCancellationException) { AppResult.Failure(AppError.Storage("quick_labour_timeout", timedOut)) }
catch (cancelled: CancellationException) { throw cancelled }
catch (failed: Exception) { AppResult.Failure(AppError.Storage("quick_labour", failed)) }

@OptIn(ExperimentalCoroutinesApi::class)
internal class QuickLabourViewModel(
    private val persistence: LocalPersistence, private val farmId: UUID, parcelId: UUID?, today: LocalDate,
) : ViewModel() {
    val generalDraft = mutableStateOf(ExpenseForm(date = today.toString(), farmId = farmId, parcelId = parcelId,
        concept = "Jornal", category = ExpenseCategory.LABOR))
    private val mutableState = MutableStateFlow(QuickLabourState(ratesLoaded = persistence.dayCostRepository == null))
    val state = mutableState.asStateFlow()
    init {
        val relations = relationSource(persistence)
        relations.selectFarm(farmId)
        relations.collectInto(viewModelScope) { value -> mutableState.update { it.copy(options = value) } }
        viewModelScope.launch {
            persistence.harvestRepository.observeContexts().catch {
                mutableState.update { it.copy(error = "No pudimos comprobar las campañas. Vuelve a abrir el formulario.") }
            }.collect { contexts -> mutableState.update { it.copy(contexts = contexts.filter { c -> c.farmId == farmId }, contextsLoaded = true) } }
        }
        viewModelScope.launch {
            persistence.labourRepository.observeWorkers().catch {
                mutableState.update { it.copy(error = "No pudimos leer las personas. Vuelve a abrir el formulario.") }
            }.collect { workers -> mutableState.update { it.copy(workers = workers, workersLoaded = true) } }
        }
        persistence.dayCostRepository?.let { repository -> viewModelScope.launch {
            repository.observeRates(farmId).catch {
                mutableState.update { it.copy(error = "No pudimos leer las tarifas. Vuelve a abrir el formulario.") }
            }.collect { rates -> mutableState.update { it.copy(rates = rates, ratesLoaded = true) } }
        } }
        viewModelScope.launch {
            combine(snapshotFlow { generalDraft.value.date }, persistence.harvestRepository.observeAll(),
                state.map { it.contexts.singleOrNull()?.campaignId }.distinctUntilChanged(),
                state.map { it.rates }.distinctUntilChanged()) { dateText, days, campaignId, rates ->
                val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
                val matching = days.filter { it.farmId == farmId && it.campaignId == campaignId && it.harvestDate == date }
                Triple(dateText, matching, rates?.currency)
            }.distinctUntilChanged().flatMapLatest { (dateText, days, usualCurrency) ->
                val day = days.singleOrNull()
                if (days.size > 1) flowOf(Triple(dateText, LabourCurrencyContext(null, "Hay varias jornadas de esa fecha. Revisa el día antes de guardar."), emptySet<UUID>()))
                else if (day == null) flowOf(Triple(dateText, LabourCurrencyContext(usualCurrency ?: "EUR"), emptySet<UUID>()))
                else combine(persistence.labourRepository.observeForHarvest(day.id), persistence.expenseRepository.observeAll()) { entries, costs ->
                    Triple(dateText, labourCurrencyContext(day.id, day.campaignId, entries, costs, usualCurrency), entries.mapNotNull { it.workerId }.toSet())
                }
            }.catch { mutableState.update { it.copy(dayCurrency = LabourCurrencyContext(null, "No pudimos comprobar los jornales del día. Vuelve a abrir el formulario.")) } }
                .collect { (dateText, currency, recorded) -> mutableState.update { it.copy(dayDate = dateText, dayCurrency = currency, recorded = recorded) } }
        }
    }
    fun addWorker(name: String) = write { persistence.labourRepository.addWorker(name).map { } }
    fun saveGeneral(form: ExpenseForm) {
        if (mutableState.value.isSaving) return
        val (draft, errors) = form.toDraft()
        mutableState.update { it.copy(errors = errors) }
        if (draft == null) return
        write(saved = true) {
            quickLabourTransaction(persistence) {
                val activity = draft.activityId?.let { persistence.activityRepository.observe(it).first() }
                if (draft.farmId != farmId || draft.campaignId != null || draft.harvestId != null ||
                    draft.category != ExpenseCategory.LABOR || (draft.activityId != null && (activity == null || activity.campaignId != null))) {
                    AppResult.Failure(AppError.Validation("campaignId", "general_context_changed"))
                } else persistence.expenseRepository.create(draft).map { }
            }
        }
    }
    fun saveCrew(campaignId: UUID, date: LocalDate, draft: CrewDraft) = write(saved = true) {
        quickLabourTransaction(persistence) {
            saveCampaignQuickLabour(farmId, campaignId, date, draft,
                persistence.harvestRepository.observeContexts().first(),
                persistence.harvestRepository::openJornada,
                { persistence.harvestRepository.observe(it).first() }, persistence.labourRepository::recordCrew).map { }
        }
    }
    private fun write(saved: Boolean = false, operation: suspend () -> AppResult<Unit>) {
        if (mutableState.value.isSaving) return
        mutableState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                when (val result = operation()) {
                    is AppResult.Success -> mutableState.update { it.copy(isSaving = false, saved = saved) }
                    is AppResult.Failure -> mutableState.update { it.copy(isSaving = false, error =
                        if ((result.error as? AppError.Validation)?.code in setOf("changed_or_ambiguous", "general_context_changed"))
                            "El contexto ha cambiado. Revisa la campaña o el trabajo antes de guardar."
                        else labourErrorMessage(result.error)) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failed: Exception) { mutableState.update { it.copy(isSaving = false, error = "No se pudo guardar. El borrador se conserva.") } }
        }
    }
}

@Composable
fun QuickLabourRoute(farmId: UUID, parcelId: UUID?, persistence: LocalPersistence, clock: AppClock, onDone: () -> Unit) {
    val model: QuickLabourViewModel = viewModel(key = "quick-labour-$farmId-$parcelId", factory = viewModelFactory {
        initializer { QuickLabourViewModel(persistence, farmId, parcelId, clock.today(ZoneId.systemDefault())) }
    })
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    QuickLabourScreen(farmId, model.generalDraft, state.options, state.contexts, state.workers, state.rates,
        ready = state.contextsLoaded && state.options.campaignsKnownFor(farmId),
        isSaving = state.isSaving, errors = state.errors, error = state.error ?: state.dayCurrency?.error,
        onSaveGeneral = model::saveGeneral, onSaveCrew = model::saveCrew, onAddWorker = model::addWorker, onCancel = onDone,
        campaignReady = state.workersLoaded && state.ratesLoaded && state.dayDate == model.generalDraft.value.date,
        currency = state.dayCurrency?.currency, alreadyRecorded = state.recorded)
}
