package com.isivoltpro.maginaolivo.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.activity.AgendaEntry
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryRepository
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.FeedState
import com.isivoltpro.maginaolivo.domain.feed.IneMunicipality
import com.isivoltpro.maginaolivo.domain.harvest.HarvestRepository
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationTurn
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationTurns
import com.isivoltpro.maginaolivo.domain.harvest.HarvestSummary
import com.isivoltpro.maginaolivo.domain.market.OilMarketFeed
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.market.OilMarketSeries
import com.isivoltpro.maginaolivo.domain.profile.ProfileRepository
import com.isivoltpro.maginaolivo.domain.profile.ProfileSettings
import com.isivoltpro.maginaolivo.domain.weather.WeatherFeed
import com.isivoltpro.maginaolivo.domain.weather.WeatherLocationPreferenceStore
import com.isivoltpro.maginaolivo.domain.weather.WeatherLocationResolver
import com.isivoltpro.maginaolivo.domain.weather.WeatherNow
import com.isivoltpro.maginaolivo.domain.weather.WeatherPreferenceIdentity
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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
    /** CR-011 §17: the Farm whose Cuaderno the card opens. */
    val farmId: java.util.UUID? = null,
    /** A null total with existing Pesadas is unavailable, not an empty campaign. */
    val deliveryCount: Int = 0,
) {
    val deliverySubtitle: String
        get() = deliveredGrams?.let { "Pesado ${com.isivoltpro.maginaolivo.domain.harvest.Weight.format(it)}" }
            ?: if (deliveryCount == 0) "Aún no hay pesadas" else "Kilos no disponibles"
}

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
    /** Phase 20D: the official weekly oil series (Junta de Andalucía), from the phone's cache. */
    val oilMarket: FeedState<OilMarketSeries> = FeedState.NotConfigured,
    /** Phase 21A: the preferred cooperative chosen in Perfil; null when none. */
    val cooperativeName: String? = null,
    /**
     * #720 R2: the farmer's own next watering days, from each Parcel's irrigation days. A plan,
     * never a community turn and never a watering already done.
     */
    val irrigationTurns: List<IrrigationTurn> = emptyList(),
    /** #620: local agricultural data could not be read reliably; never present this as empty. */
    val localReadError: String? = null,
    /** Explicit weather preference from WeatherLocationPreferenceStore, when set. */
    val hasExplicitWeatherLocation: Boolean = false,
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
    /** Unambiguous farm location suggestion for the weather selector when every farm shares one place. */
    val farmWeatherSuggestion: FeedLocation?
        get() = FeedLocation.common(farms.map { it.municipality to it.province })?.withValidatedIneCode()
    /** True when active farms exist in different municipalities and no weather location has resolved it. */
    val weatherLocationAmbiguous: Boolean get() = weatherLocation == null && farms
        .mapNotNull { farm ->
            farm.municipality?.trim()?.takeIf(String::isNotEmpty)?.let { FeedLocation(it, farm.province).key }
        }
        .distinct()
        .size > 1
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val workspaces: WorkspaceRepository,
    farms: FarmRepository,
    activities: ActivityRepository,
    harvests: HarvestRepository,
    deliveries: DeliveryRepository,
    private val clock: AppClock,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** Phase 20A: null where no weather feed exists (tests, previews). */
    private val weatherFeed: WeatherFeed? = null,
    /** Phase 20D: null where no oil-market feed exists (tests, previews). */
    private val oilMarketFeed: OilMarketFeed? = null,
    /** Phase 21A: «Mi perfil»; null where no profile exists (tests, previews). */
    profile: ProfileRepository? = null,
    /** #720 R2: the Parcels whose irrigation days make the watering plan; null in tests/previews. */
    private val parcels: ParcelRepository? = null,
    /** National Weather Selector 1.0: personal weather municipality preference store. */
    private val weatherLocationStore: WeatherLocationPreferenceStore? = null,
    /** Verified identity scope for personal weather preferences. */
    private val weatherIdentity: Flow<WeatherPreferenceIdentity> = flowOf(WeatherPreferenceIdentity.Local),
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()
    private var weatherRefresh: Job? = null
    private var weatherRefreshPlace: FeedLocation? = null
    private val localRetry = MutableStateFlow(0)
    private val currentIdentity = MutableStateFlow<WeatherPreferenceIdentity>(WeatherPreferenceIdentity.Local)

    /**
     * #315: entering or coming back to Inicio (also after the app was in the background) asks for
     * the weather again only when the cached value is past its one-hour window. No polling.
     */
    fun onResumed() {
        mutableState.value.weatherLocation?.let(::refreshWeatherIfStale)
    }

    /** #620: retries the complete local projection after a storage/workspace read failure. */
    fun retryLocalData() {
        if (mutableState.value.localReadError == null) return
        localRetry.value = localRetry.value + 1
    }

    /** Saves the farmer's chosen weather municipality in [WeatherLocationPreferenceStore]. */
    fun selectWeatherMunicipality(municipality: IneMunicipality) {
        val store = weatherLocationStore ?: return
        viewModelScope.launch {
            val wsId = (workspaces.ensureLocalWorkspace() as? AppResult.Success)?.value ?: return@launch
            store.set(currentIdentity.value, wsId, municipality)
        }
    }

    /** Clears the explicit weather municipality preference for the current identity and workspace. */
    fun clearWeatherMunicipality() {
        val store = weatherLocationStore ?: return
        viewModelScope.launch {
            val wsId = (workspaces.ensureLocalWorkspace() as? AppResult.Success)?.value ?: return@launch
            store.clear(currentIdentity.value, wsId)
        }
    }

    /** One background attempt per place at a time; Inicio never waits for it. */
    private fun refreshWeatherIfStale(place: FeedLocation) {
        val feed = weatherFeed ?: return
        if (weatherRefresh?.isActive == true && weatherRefreshPlace == place) return
        weatherRefreshPlace = place
        weatherRefresh = viewModelScope.launch { feed.refreshIfStale(place) }
    }

    init {
        val profileSettings = (profile?.observe() ?: flowOf(ProfileSettings()))
            .catch { emit(ProfileSettings()) }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
        val explicitWeatherPreference = combine(
            weatherIdentity.onEach { currentIdentity.value = it },
            localRetry.flatMapLatest { flow { emit(workspaces.ensureLocalWorkspace()) } },
        ) { identity, wsResult ->
            identity to (wsResult as? AppResult.Success)?.value
        }.flatMapLatest { (identity, wsId) ->
            if (wsId == null || weatherLocationStore == null) {
                flowOf(identity to null)
            } else {
                weatherLocationStore.observe(identity, wsId).map { pref -> identity to pref }
            }
        }
        // Priority: explicit weather preference -> unambiguous farm municipality -> local profile fallback.
        val resolvedWeatherPlace = combine(
            state.map { it.farms }.distinctUntilChanged(),
            explicitWeatherPreference,
            profileSettings,
        ) { farmList, (identity, explicitPref), settings ->
            WeatherLocationResolver.resolve(
                farms = farmList,
                explicitPreference = explicitPref,
                legacyProfileSettings = settings,
                identity = identity,
            )
        }
            .distinctUntilChanged()
            // A stale or missing value is refreshed in the background; Inicio never waits for it.
            .onEach { resolved -> resolved.location?.let(::refreshWeatherIfStale) }
        val weather = resolvedWeatherPlace.flatMapLatest { resolved ->
            (weatherFeed?.observe(resolved.location) ?: flowOf(FeedState.NotConfigured)).map { resolved to it }
        }
        viewModelScope.launch {
            localRetry.flatMapLatest {
                val previous = mutableState.value
                mutableState.value = previous.copy(
                    isLoading = previous.today == null && previous.farms.isEmpty(),
                    localReadError = null,
                )
                workspaces.observeActiveWorkspace().flatMapLatest { result ->
                    when (result) {
                        is AppResult.Success -> combine(
                            farms.observeActive(result.value),
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
                                    val harvested = harvestList.filter { it.campaignId == context.campaignId && !it.awaitingPesadas }
                                    val delivered = deliveryList.filter { it.campaignId == context.campaignId }
                                    HomeCampaign(
                                        name = context.campaignName,
                                        farmName = context.farmName,
                                        harvestedGrams = harvested.takeIf { it.isNotEmpty() }?.let(HarvestSummary::of)?.totalGrams,
                                        deliveredGrams = delivered.takeIf { it.isNotEmpty() }?.let(DeliverySummary::of)?.deliveredGrams,
                                        farmId = context.farmId,
                                        deliveryCount = delivered.size,
                                    )
                                },
                                upcoming = agenda.filter { !it.activityDate.isBefore(today) }
                                    .sortedWith(compareBy<AgendaEntry> { it.activityDate }.thenBy(nullsFirst()) { it.planning?.startTime })
                                    .take(3),
                                overdueCount = agenda.count { it.activityDate.isBefore(today) },
                            ) as HomeUiState?
                        }.catch {
                            mutableState.value = mutableState.value.copy(
                                isLoading = false,
                                localReadError = LOCAL_READ_ERROR,
                            )
                            emit(null)
                        }
                        is AppResult.Failure -> flow {
                            mutableState.value = HomeUiState(
                                isLoading = false,
                                localReadError = LOCAL_READ_ERROR,
                            )
                            emit(null)
                        }
                    }
                }
            }.collect { base ->
                if (base != null) {
                    mutableState.value = base.copy(
                        weatherLocation = mutableState.value.weatherLocation,
                        weather = mutableState.value.weather,
                        hasExplicitWeatherLocation = mutableState.value.hasExplicitWeatherLocation,
                        oilMarket = mutableState.value.oilMarket,
                        cooperativeName = mutableState.value.cooperativeName,
                        irrigationTurns = mutableState.value.irrigationTurns,
                        localReadError = null,
                    )
                }
            }
        }
        oilMarketFeed?.let { feed ->
            // Cache first; a refresh runs in the background and never holds Inicio up.
            viewModelScope.launch { feed.refreshIfStale() }
            viewModelScope.launch { feed.observe().collect { value -> mutableState.value = mutableState.value.copy(oilMarket = value) } }
        }
        // #720 R2: the watering plan follows the active Farms' Parcels. It is local data, so it
        // never holds Inicio up and a read failure simply leaves the card out.
        parcels?.let { repository ->
            viewModelScope.launch {
                state.map { ui -> ui.farms.map { it.id } }.distinctUntilChanged()
                    .flatMapLatest { ids ->
                        if (ids.isEmpty()) flowOf(emptyList())
                        else combine(ids.map { repository.observeActive(it) }) { lists -> lists.toList().flatten() }
                    }
                    .catch { emit(emptyList()) }
                    .collect { list ->
                        val today = mutableState.value.today ?: clock.today(zone())
                        mutableState.value = mutableState.value.copy(irrigationTurns = IrrigationTurns.of(list, today))
                    }
            }
        }
        viewModelScope.launch {
            profileSettings.collect { settings ->
                mutableState.value = mutableState.value.copy(cooperativeName = settings.preferredCooperative?.name)
            }
        }
        viewModelScope.launch {
            weather.collect { (resolved, value) ->
                mutableState.value = mutableState.value.copy(
                    weatherLocation = resolved.location,
                    weather = value,
                    hasExplicitWeatherLocation = resolved.hasExplicitPreference,
                )
            }
        }
    }

    private companion object {
        const val LOCAL_READ_ERROR = "No hemos podido leer los datos de tu olivar."
    }
}
