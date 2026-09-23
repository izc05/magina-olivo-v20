package com.isivoltpro.maginaolivo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.AgendaEntry
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** A running campaign as Inicio shows it: only figures the ledgers hold. */
data class HomeCampaign(
    val name: String,
    val farmName: String,
    val harvestedGrams: Long?,
    val deliveredGrams: Long?,
)

/**
 * Inicio (UI polish v2): a summary built only from this phone's data. External services
 * (weather, oil market, cooperative notices) belong to their own phase and are announced,
 * never shown with sample numbers.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val today: LocalDate? = null,
    val farms: List<Farm> = emptyList(),
    val campaigns: List<HomeCampaign> = emptyList(),
    val upcoming: List<AgendaEntry> = emptyList(),
    val overdueCount: Int = 0,
) {
    val parcelCount: Long get() = farms.sumOf { it.parcelCount }
    val knownAreaM2: Double? get() = farms.mapNotNull { it.totalAreaM2 }.takeIf { it.isNotEmpty() }?.sum()
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    workspaces: WorkspaceRepository,
    farms: FarmRepository,
    activities: ActivityRepository,
    harvests: HarvestRepository,
    deliveries: DeliveryRepository,
    private val clock: AppClock,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    init {
        val activeFarms = flow { emit(workspaces.ensureLocalWorkspace()) }.flatMapLatest { result ->
            when (result) {
                is AppResult.Success -> farms.observeActive(result.value)
                is AppResult.Failure -> flowOf(emptyList())
            }
        }
        viewModelScope.launch {
            combine(
                activeFarms,
                activities.observeAgenda(),
                harvests.observeContexts(),
                harvests.observeAll(),
                deliveries.observeAll(),
            ) { farmList, agenda, running, harvestList, deliveryList ->
                val today = clock.today(zone())
                HomeUiState(
                    isLoading = false,
                    today = today,
                    farms = farmList,
                    campaigns = running.map { context ->
                        val harvested = harvestList.filter { it.campaignId == context.campaignId }
                        val delivered = deliveryList.filter { it.campaignId == context.campaignId }
                        HomeCampaign(
                            name = context.campaignName,
                            farmName = context.farmName,
                            harvestedGrams = harvested.takeIf { it.isNotEmpty() }?.sumOf { it.totalGrams },
                            deliveredGrams = delivered.takeIf { it.isNotEmpty() }?.sumOf { it.netGrams },
                        )
                    },
                    upcoming = agenda.filter { !it.activityDate.isBefore(today) }
                        .sortedWith(compareBy<AgendaEntry> { it.activityDate }.thenBy(nullsFirst()) { it.planning?.startTime })
                        .take(3),
                    overdueCount = agenda.count { it.activityDate.isBefore(today) },
                )
            }.collect { mutableState.value = it }
        }
    }
}
