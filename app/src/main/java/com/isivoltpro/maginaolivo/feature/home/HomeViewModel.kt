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
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.weather.WeatherFeed
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
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
 * Inicio: a summary built from this phone's data first. External feeds (Phase 20: weather,
 * oil market, cooperative notices) come after it, each with an honest state — never sample
 * numbers, and never a reason for the farm part to wait.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val today: LocalDate? = null,
    val farms: List<Farm> = emptyList(),
    val campaigns: List<HomeCampaign> = emptyList(),
    val upcoming: List<AgendaEntry> = emptyList(),
    val overdueCount: Int = 0,
    /** Phase 20A: the place asked about for weather; null when the farms give none (or several). */
    val weatherLocation: FeedLocation? = null,
    val weather: FeedState<WeatherNow> = FeedState.NotConfigured,
) {
    val parcelCount: Long get() = farms.sumOf { it.parcelCount }
    val knownAreaM2: Double? get() = farms.mapNotNull { it.totalAreaM2 }.takeIf { it.isNotEmpty() }?.sum()
    /** Olive trees the farmer counted (CR-004); null when no parcel has a count. */
    val oliveTrees: Long? get() = farms.mapNotNull { it.oliveTreeCount }.takeIf { it.isNotEmpty() }?.sum()
    /** False when some farm or parcel has no count yet, so the total is only a lower bound. */
    val oliveTreesComplete: Boolean get() = farms.isNotEmpty() && farms.all { it.oliveTreeCountComplete }
    /** "Bedmar · Jaén" when every farm is in the same place; otherwise nothing is claimed. */
    val location: String? get() = farms
        .map { listOfNotNull(it.municipality, it.province).filter(String::isNotBlank).joinToString(" · ") }
        .distinct()
        .singleOrNull()
        ?.ifEmpty { null }
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
    /** Phase 20A: null where no weather feed exists (tests, previews). */
    private val weatherFeed: WeatherFeed? = null,
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
        val sharedFarms = activeFarms.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
        val location = sharedFarms
            .map { list -> FeedLocation.common(list.map { it.municipality to it.province }) }
            .distinctUntilChanged()
            // A stale or missing value is refreshed in the background; Inicio never waits for it.
            .onEach { place -> if (place != null && weatherFeed != null) viewModelScope.launch { weatherFeed.refreshIfStale(place) } }
        val weather = location.flatMapLatest { place ->
            (weatherFeed?.observe(place) ?: flowOf(FeedState.NotConfigured)).map { place to it }
        }
        viewModelScope.launch {
            combine(
                sharedFarms,
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
            }.collect { base ->
                mutableState.value = base.copy(weatherLocation = mutableState.value.weatherLocation, weather = mutableState.value.weather)
            }
        }
        viewModelScope.launch {
            weather.collect { (place, value) ->
                mutableState.value = mutableState.value.copy(weatherLocation = place, weather = value)
            }
        }
    }
}
