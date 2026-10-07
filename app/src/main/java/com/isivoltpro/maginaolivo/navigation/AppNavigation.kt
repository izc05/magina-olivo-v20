package com.isivoltpro.maginaolivo.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.app.AppEnvironment
import com.isivoltpro.maginaolivo.feature.farms.FarmDetailRoute
import com.isivoltpro.maginaolivo.feature.agenda.AgendaRoute
import com.isivoltpro.maginaolivo.feature.profile.ProfileRoute
import com.isivoltpro.maginaolivo.feature.profile.appVersionLabel
import com.isivoltpro.maginaolivo.feature.farms.FarmListRoute
import com.isivoltpro.maginaolivo.feature.parcels.ParcelDetailRoute
import com.isivoltpro.maginaolivo.feature.activities.ActivityDetailRoute
import com.isivoltpro.maginaolivo.feature.activities.RegisterActivityRoute
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignDetailRoute
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailRoute
import com.isivoltpro.maginaolivo.feature.harvests.TodayHarvestRoute
import com.isivoltpro.maginaolivo.feature.profile.HelpRoute
import com.isivoltpro.maginaolivo.feature.profile.HelpTopic
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveriesRoute
import com.isivoltpro.maginaolivo.domain.delivery.YieldStatus
import com.isivoltpro.maginaolivo.feature.machinery.MachineDetailRoute
import com.isivoltpro.maginaolivo.feature.machinery.MachineryRoute
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveryDetailRoute
import com.isivoltpro.maginaolivo.feature.deliveries.TicketReviewRoute
import com.isivoltpro.maginaolivo.feature.harvests.HarvestsRoute
import com.isivoltpro.maginaolivo.feature.expenses.DocumentReviewRoute
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseDetailRoute
import com.isivoltpro.maginaolivo.feature.expenses.ExpensesRoute
import com.isivoltpro.maginaolivo.feature.farms.FarmSection
import com.isivoltpro.maginaolivo.feature.farms.FarmSectionRoute
import com.isivoltpro.maginaolivo.feature.home.HomeRoute
import com.isivoltpro.maginaolivo.feature.home.OilMarketRoute
import com.isivoltpro.maginaolivo.feature.home.RadarRoute
import com.isivoltpro.maginaolivo.feature.home.WeatherWeekRoute
import com.isivoltpro.maginaolivo.feature.expenses.OrganizationsRoute
import com.isivoltpro.maginaolivo.ui.components.MoBottomBar
import com.isivoltpro.maginaolivo.ui.components.MoBottomBarItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.reference.campaign.CampaignReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.components.ComponentCatalogueReferenceScreen
import com.isivoltpro.maginaolivo.feature.catastro.CadastreImportRoute
import com.isivoltpro.maginaolivo.feature.maps.FarmMapRoute
import com.isivoltpro.maginaolivo.feature.notebook.NotebookActions
import com.isivoltpro.maginaolivo.feature.notebook.NotebookHubTab
import com.isivoltpro.maginaolivo.feature.notebook.NotebookQuickAction
import com.isivoltpro.maginaolivo.feature.notebook.NotebookOrigin
import com.isivoltpro.maginaolivo.feature.notebook.NotebookRootRoute
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.isivoltpro.maginaolivo.ui.reference.ocr.DeliveryOcrReviewReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.onboarding.OnboardingReferenceScreen
import java.util.UUID

private val bottomBarItems = RootDestination.entries.map { destination ->
    MoBottomBarItem(
        label = destination.label,
        symbol = destination.symbol,
        isPrimaryAction = destination.isPrimaryAction,
        icon = when (destination) {
            RootDestination.Home -> MoIcons.Home
            RootDestination.Olivar -> MoIcons.Tree
            RootDestination.Notebook -> MoIcons.Notebook
            RootDestination.Alerts -> MoIcons.Bell
            RootDestination.Profile -> MoIcons.Person
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    compositionRoot: AppCompositionRoot,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    /** Planned work to open, from a tapped reminder (Phase 16). */
    openActivityId: UUID? = null,
    onActivityOpened: () -> Unit = {},
) {
    val onboardingStateStore = compositionRoot.onboardingStateStore
    val startDestination = remember {
        if (onboardingStateStore.isCompleted()) {
            RootDestination.Home.route
        } else {
            AppDestination.Onboarding
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoot = AppDestination.rootForRoute(backStackEntry?.destination?.route)
    // The Farm a quick action was tapped on, handed to the activity flow once.
    var registerFarmId by rememberSaveable { mutableStateOf<String?>(null) }
    // CR-011 §14: the Farm the Cuaderno opens on (from a Farm, a Parcel or Inicio).
    var notebookFarmRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var notebookTabRequest by rememberSaveable { mutableStateOf<String?>(null) }
    // The Parcel of the Cuaderno a work action was tapped on, handed to the work form.
    var registerParcelId by rememberSaveable { mutableStateOf<String?>(null) }
    // #369: from Inicio the Cuaderno is the general hub (ROOT); from a Farm or a Parcel it opens
    // over that screen with the Farm fixed, and Back returns there. A Parcel's Cuaderno keeps
    // that Parcel on its own back-stack entry, so no other Cuaderno picks it up.
    val openNotebookOn: (UUID, UUID?, String?, NotebookOrigin) -> Unit = { farmId, parcelId, parcelName, origin ->
        compositionRoot.activeFarmStore.set(farmId)
        notebookFarmRequest = farmId.toString()
        if (origin == NotebookOrigin.ROOT) {
            navController.navigateToRoot(RootDestination.Notebook)
        } else {
            navController.navigate(RootDestination.Notebook.route) { launchSingleTop = true }
            navController.currentBackStackEntry?.savedStateHandle?.let { handle ->
                handle[NOTEBOOK_ORIGIN_KEY] = origin.name
                handle[NOTEBOOK_PARCEL_ID_KEY] = parcelId?.toString()
                handle[NOTEBOOK_PARCEL_NAME_KEY] = parcelName
            }
        }
    }
    // A reminder opens its Activity over the Calendar, so Back returns to the agenda.
    LaunchedEffect(openActivityId, backStackEntry == null) {
        val id = openActivityId ?: return@LaunchedEffect
        if (backStackEntry == null || startDestination == AppDestination.Onboarding) return@LaunchedEffect
        // UX-B: the agenda lives under Avisos.
        navController.navigateToRoot(RootDestination.Alerts)
        navController.navigate(AppDestination.activity(id.toString()))
        onActivityOpened()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoot != null) {
                MoBottomBar(
                    items = bottomBarItems,
                    selectedIndex = currentRoot.ordinal,
                    // UX-B (Issue #246): every tab is a real root; "Registrar hoy" lives in Cuaderno.
                    onSelected = { index -> navController.navigateToRoot(RootDestination.entries[index]) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        ) {
            composable(AppDestination.Onboarding) {
                OnboardingReferenceScreen(
                    onFinished = {
                        onboardingStateStore.markCompleted()
                        navController.navigate(RootDestination.Home.route) {
                            popUpTo(AppDestination.Onboarding) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(RootDestination.Home.route) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    HomeRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onCalendar = { navController.navigate(AppDestination.Calendar) },
                        // CR-011 §17: a running campaign opens its Farm's Cuaderno on Campaña.
                        onCampaign = { farmId ->
                            if (farmId == null) {
                                navController.navigateToRoot(RootDestination.Notebook)
                            } else {
                                notebookTabRequest = NotebookHubTab.CAMPAIGN.name
                                openNotebookOn(farmId, null, null, NotebookOrigin.ROOT)
                            }
                        },
                        onWeatherWeek = { navController.navigate(AppDestination.Weather) },
                        onActivitySelected = { id -> navController.navigate(AppDestination.activity(id.toString())) },
                        onOilMarket = { navController.navigate(AppDestination.OilMarket) },
                    )
                }
            }
            // Phase 20D-3: the oil market — official weeks from the phone's cache, daily pulse online.
            composable(AppDestination.OilMarket) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    OilMarketRoute(persistence = persistence)
                }
            }
            // Phase 20B-radar: rain radar over the active Farm (live only, never cached).
            composable(AppDestination.Radar) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    RadarRoute(persistence = persistence, farmId = compositionRoot.activeFarmStore.get())
                }
            }
            composable(RootDestination.Olivar.route) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    FarmListRoute(
                        persistence = persistence,
                        onFarmSelected = { farmId ->
                            navController.navigate(AppDestination.farm(farmId.toString()))
                        },
                    )
                }
            }
            composable(RootDestination.Notebook.route) { entry ->
                val persistence = compositionRoot.localPersistence
                // #369: kept on this back-stack entry, so a Farm's Cuaderno and the Cuaderno tab
                // never share it, and it survives the process being recreated.
                val originName by entry.savedStateHandle
                    .getStateFlow(NOTEBOOK_ORIGIN_KEY, NotebookOrigin.ROOT.name)
                    .collectAsStateWithLifecycle()
                val origin = NotebookOrigin.entries.firstOrNull { it.name == originName } ?: NotebookOrigin.ROOT
                val parcelId by entry.savedStateHandle.getStateFlow<String?>(NOTEBOOK_PARCEL_ID_KEY, null).collectAsStateWithLifecycle()
                val parcelName by entry.savedStateHandle.getStateFlow<String?>(NOTEBOOK_PARCEL_NAME_KEY, null).collectAsStateWithLifecycle()
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    NotebookRootRoute(
                        persistence = persistence,
                        activeFarmStore = compositionRoot.activeFarmStore,
                        onQuickAction = { action, farmId, running ->
                            registerFarmId = farmId.toString()
                            // CR-011 §8/§14: Jornal opens today's recolección day of this Farm by
                            // itself (found or created); nobody opens a «jornada» by hand.
                            // The tap navigates at once; the day screen resolves the day itself.
                            if (action == NotebookQuickAction.LABOUR && running) {
                                navController.navigate(AppDestination.todayHarvest(farmId.toString())) { launchSingleTop = true }
                            } else {
                                // Only this Cuaderno's Parcel travels with the action; the work form
                                // reads it from here.
                                registerParcelId = parcelId
                                navController.openQuickAction(action, farmId, parcelId)
                            }
                        },
                        actionsFor = { farmId -> navController.notebookActions(farmId) },
                        onGoToFields = { navController.navigateToRoot(RootDestination.Olivar) },
                        farmRequest = notebookFarmRequest?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        onFarmRequestHandled = { notebookFarmRequest = null },
                        parcelContext = parcelName,
                        onClearParcel = {
                            // Set, not removed: removing detaches the observed flows, which would
                            // keep showing and using the Parcel the farmer just dropped.
                            entry.savedStateHandle[NOTEBOOK_PARCEL_ID_KEY] = null
                            entry.savedStateHandle[NOTEBOOK_PARCEL_NAME_KEY] = null
                        },
                        tabRequest = notebookTabRequest?.let { name -> NotebookHubTab.entries.firstOrNull { it.name == name } },
                        onTabRequestHandled = { notebookTabRequest = null },
                        origin = origin,
                    )
                }
            }
            composable(
                AppDestination.RegisterPattern,
                arguments = listOf(navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                val persistence = compositionRoot.localPersistence
                val presetType = entry.arguments?.getString("type")
                    ?.let { name -> ActivityType.entries.firstOrNull { it.name == name } }
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    RegisterActivityRoute(
                        presetType = presetType,
                        persistence = persistence,
                        preselectedFarmId = registerFarmId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        onFarmPreselected = { registerFarmId = null },
                        preselectedParcelId = registerParcelId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        onActivitySelected = { activityId ->
                            navController.navigate(AppDestination.activity(activityId.toString()))
                        },
                        clock = compositionRoot.clock,
                    )
                }
            }
            // CR-011 §12: Avisos → «Planificar trabajo», the register flow in plan mode under Avisos.
            composable(AppDestination.PlanWork) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    RegisterActivityRoute(
                        persistence = persistence,
                        planning = true,
                        onActivitySelected = { activityId ->
                            navController.navigate(AppDestination.activity(activityId.toString()))
                        },
                        clock = compositionRoot.clock,
                    )
                }
            }
            // UX-B: Avisos shows the agenda (overdue, today, next days, reminders); the old
            // `calendar` route opens the same screen, now under Avisos.
            listOf(RootDestination.Alerts.route to "Avisos", AppDestination.Calendar to "Calendario").forEach { (route, title) ->
                composable(route) {
                    val persistence = compositionRoot.localPersistence
                    if (persistence == null) {
                        PersistenceUnavailableScreen()
                    } else {
                        AgendaRoute(
                            persistence = persistence,
                            clock = compositionRoot.clock,
                            onActivitySelected = { id -> navController.navigate(AppDestination.activity(id.toString())) },
                            onPlanWork = { navController.navigate(AppDestination.planWork()) },
                            title = title,
                        )
                    }
                }
            }
            composable(RootDestination.Profile.route) {
                ProfileRoute(
                    appVersion = appVersionLabel(
                        com.isivoltpro.maginaolivo.BuildConfig.VERSION_NAME,
                        com.isivoltpro.maginaolivo.BuildConfig.BUILD_NUMBER,
                    ),
                    onMachinery = { navController.navigate(AppDestination.Machinery) },
                    developerGalleryEnabled = compositionRoot.environment == AppEnvironment.DEV,
                    onDeveloperGallery = { navController.navigate(AppDestination.DeveloperGallery) },
                    persistence = compositionRoot.localPersistence,
                    // #399: the demo farm exists only in the dev flavor and only in a DEV environment.
                    demoFarm = compositionRoot.localPersistence
                        ?.takeIf { compositionRoot.environment == AppEnvironment.DEV }
                        ?.let { com.isivoltpro.maginaolivo.app.DevTools.demoFarm(it) },
                    onHelp = { topic -> navController.navigate(AppDestination.help(topic.route)) },
                )
            }
            composable(AppDestination.FarmPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val farmId = backStackEntry.arguments
                    ?.getString("farmId")
                    ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
                if (persistence == null || farmId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    FarmDetailRoute(
                        farmId = farmId,
                        persistence = persistence,
                        onOpenSection = { section ->
                            if (section == FarmSection.NOTEBOOK) {
                                // CR-011 §3: the one Cuaderno, on this Farm; Back returns to the Farm.
                                openNotebookOn(farmId, null, null, NotebookOrigin.FARM_CONTEXT)
                            } else {
                                navController.navigate(AppDestination.farmSection(section.route, farmId.toString()))
                            }
                        },
                        onArchived = { navController.popBackStack() },
                        onRegister = { openNotebookOn(farmId, null, null, NotebookOrigin.FARM_CONTEXT) },
                    )
                }
            }
            // CR-011 §3: no `farm-notebook` screen; the hub entry opens the one Cuaderno.
            FarmSection.entries.filter { it != FarmSection.NOTEBOOK }.forEach { section ->
                composable(AppDestination.farmSectionPattern(section.route)) { backStackEntry ->
                    val persistence = compositionRoot.localPersistence
                    val farmId = backStackEntry.arguments?.getString("farmId")
                        ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
                    if (persistence == null || farmId == null) {
                        PersistenceUnavailableScreen()
                    } else {
                        FarmSectionRoute(
                            farmId = farmId,
                            section = section,
                            persistence = persistence,
                            onParcelSelected = { id -> navController.navigate(AppDestination.parcel(id.toString())) },
                            onCampaignSelected = { id -> navController.navigate(AppDestination.campaign(id.toString())) },
                            onActivitySelected = { id -> navController.navigate(AppDestination.activity(id.toString())) },
                            onImportFromCatastro = { navController.navigate(AppDestination.catastro(farmId.toString())) },
                            onMap = { navController.navigate(AppDestination.farmMap(farmId.toString())) },
                        )
                    }
                }
            }
            composable(AppDestination.FarmMapPattern) { entry ->
                val persistence = compositionRoot.localPersistence
                val farmId = entry.arguments?.getString("farmId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || farmId == null) PersistenceUnavailableScreen()
                else FarmMapRoute(
                    farmId = farmId,
                    persistence = persistence,
                    onOpenParcel = { navController.navigate(AppDestination.parcel(it.toString())) },
                    onSearchByReference = { navController.navigate(AppDestination.catastro(farmId.toString())) },
                    onImported = { navController.popBackStack() },
                )
            }
            composable(AppDestination.FarmMapLocatePattern) { entry ->
                val persistence = compositionRoot.localPersistence
                val farmId = entry.arguments?.getString("farmId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val parcelId = entry.arguments?.getString("parcelId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || farmId == null || parcelId == null) PersistenceUnavailableScreen()
                else FarmMapRoute(
                    farmId = farmId,
                    persistence = persistence,
                    onOpenParcel = { navController.navigate(AppDestination.parcel(it.toString())) },
                    onSearchByReference = { navController.navigate(AppDestination.catastro(farmId.toString())) },
                    locateParcelId = parcelId,
                    // Back on the parcel it came from, now with its boundary.
                    onLocated = { navController.popBackStack() },
                )
            }
            composable(AppDestination.ParcelPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val parcelId = backStackEntry.arguments?.getString("parcelId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || parcelId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    ParcelDetailRoute(
                        parcelId = parcelId,
                        persistence = persistence,
                        onArchived = { navController.popBackStack() },
                        onLocate = { farmId -> navController.navigate(AppDestination.farmMapLocate(farmId.toString(), parcelId.toString())) },
                        onRegister = { farmId, parcelName -> openNotebookOn(farmId, parcelId, parcelName, NotebookOrigin.PARCEL_CONTEXT) },
                    )
                }
            }
            composable(AppDestination.CampaignPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val campaignId = backStackEntry.arguments?.getString("campaignId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || campaignId == null) PersistenceUnavailableScreen()
                else CampaignDetailRoute(
                    campaignId,
                    persistence,
                    onHarvests = { navController.navigate(AppDestination.campaignHarvests(campaignId.toString())) },
                    onDeliveries = { navController.navigate(AppDestination.Deliveries) },
                    onNewPesada = { farmId -> navController.navigate(AppDestination.newPesada(farmId.toString())) },
                    // #365: the same day as Cuaderno → Jornal; find-or-create keeps it a single day.
                    onAddLabour = { farmId ->
                        navController.navigate(AppDestination.todayHarvest(farmId.toString())) { launchSingleTop = true }
                    },
                )
            }
            composable(AppDestination.CampaignHarvestsPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val campaignId = backStackEntry.arguments?.getString("campaignId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || campaignId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    HarvestsRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        campaignId = campaignId,
                        onHarvestSelected = { id -> navController.navigate(AppDestination.harvest(id.toString())) },
                        // #408: from a running Campaign, Nueva pesada keeps its Farm context.
                        onDeliveries = { farmId ->
                            if (farmId != null) navController.navigate(AppDestination.newPesada(farmId.toString()))
                        },
                    )
                }
            }
            composable(AppDestination.ActivityPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val activityId = backStackEntry.arguments?.getString("activityId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val expenseAdded by backStackEntry.savedStateHandle
                    .getStateFlow(RELATED_EXPENSE_ADDED_KEY, false).collectAsStateWithLifecycle()
                if (persistence == null || activityId == null) PersistenceUnavailableScreen()
                else ActivityDetailRoute(
                    activityId,
                    persistence,
                    relatedExpenseAdded = expenseAdded,
                    onRelatedExpenseNoticeShown = { backStackEntry.savedStateHandle[RELATED_EXPENSE_ADDED_KEY] = false },
                    // #416: money for a work is its own Gasto, tied to the work; never a second figure.
                    onAddRelatedExpense = { activity ->
                        activity.farmId?.let { farmId ->
                            navController.navigate(
                                AppDestination.farmExpenses(
                                    farmId.toString(),
                                    parcelId = activity.targets.singleOrNull()?.parcelId?.toString(),
                                    activityId = activity.id.toString(),
                                ),
                            )
                        }
                    },
                    onOpenExpense = { expenseId -> navController.navigate(AppDestination.expense(expenseId.toString())) },
                )
            }
            composable(AppDestination.MapCatastro) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) PersistenceUnavailableScreen()
                else CadastreImportRoute(
                    persistence = persistence,
                    preselectedFarmId = null,
                    onParcelImported = { id ->
                        navController.navigate(AppDestination.parcel(id.toString())) {
                            popUpTo(AppDestination.MapCatastro) { inclusive = true }
                        }
                    },
                )
            }
            composable(AppDestination.CatastroPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val farmId = backStackEntry.arguments?.getString("farmId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || farmId == null) PersistenceUnavailableScreen()
                else CadastreImportRoute(
                    persistence = persistence,
                    preselectedFarmId = farmId,
                    onParcelImported = { id ->
                        // Back from the new parcel returns to its farm, not to the search.
                        navController.navigate(AppDestination.parcel(id.toString())) {
                            popUpTo(AppDestination.CatastroPattern) { inclusive = true }
                        }
                    },
                )
            }
            composable(AppDestination.Weather) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    WeatherWeekRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onRadar = { navController.navigate(AppDestination.Radar) }.takeIf { persistence.radarSource != null },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(AppDestination.Analytics) { CampaignReferenceScreen() }
            composable(AppDestination.OcrReview) { DeliveryOcrReviewReferenceScreen() }
            composable(AppDestination.Harvest) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    HarvestsRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onHarvestSelected = { id -> navController.navigate(AppDestination.harvest(id.toString())) },
                        onDeliveries = { _ -> navController.navigate(AppDestination.Deliveries) },
                    )
                }
            }
            composable(AppDestination.HarvestPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val harvestId = backStackEntry.arguments?.getString("harvestId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || harvestId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    HarvestDetailRoute(
                        harvestId = harvestId,
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onDeleted = { navController.popBackStack() },
                        onAddPesada = { id -> navController.navigate(AppDestination.jornadaPesada(id.toString())) },
                        onPesadaSelected = { id -> navController.navigate(AppDestination.delivery(id.toString())) },
                        onExpenseSelected = { id -> navController.navigate(AppDestination.expense(id.toString())) },
                    )
                }
            }
            composable(AppDestination.TodayHarvestPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val farmId = backStackEntry.arguments?.getString("farmId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || farmId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    TodayHarvestRoute(
                        farmId = farmId,
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onDeleted = { navController.popBackStack() },
                        onAddPesada = { id -> navController.navigate(AppDestination.jornadaPesada(id.toString())) },
                        onPesadaSelected = { id -> navController.navigate(AppDestination.delivery(id.toString())) },
                        onExpenseSelected = { id -> navController.navigate(AppDestination.expense(id.toString())) },
                    )
                }
            }
            composable(AppDestination.Machinery) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    MachineryRoute(
                        persistence = persistence,
                        onMachineSelected = { id -> navController.navigate(AppDestination.machine(id.toString())) },
                    )
                }
            }
            composable(AppDestination.MachinePattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val machineId = backStackEntry.arguments?.getString("machineId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || machineId == null) PersistenceUnavailableScreen()
                else MachineDetailRoute(machineId, persistence)
            }
            composable(AppDestination.Deliveries) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    DeliveriesRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onDeliverySelected = { id -> navController.navigate(AppDestination.delivery(id.toString())) },
                        onTicketSelected = { id -> navController.navigate(AppDestination.ticket(id.toString())) },
                        onAddYield = { id -> navController.navigate(AppDestination.deliveryYield(id.toString())) },
                    )
                }
            }
            composable(
                AppDestination.NewPesadaPattern,
                arguments = listOf(
                    navArgument("farmId") { type = NavType.StringType },
                    navArgument("parcelId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val farmId = backStackEntry.arguments?.getString("farmId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val parcelId = backStackEntry.arguments?.getString("parcelId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || farmId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    DeliveriesRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onDeliverySelected = { id -> navController.navigate(AppDestination.delivery(id.toString())) },
                        onTicketSelected = { id -> navController.navigate(AppDestination.ticket(id.toString())) },
                        onAddYield = { id -> navController.navigate(AppDestination.deliveryYield(id.toString())) },
                        presetFarmId = farmId,
                        presetParcelId = parcelId,
                    )
                }
            }
            // #378: Cuaderno → Jornal outside a running campaign opens the Farm's labour form at once.
            composable(
                AppDestination.FarmLabourPattern,
                arguments = listOf(
                    navArgument("farmId") { type = NavType.StringType },
                    navArgument("parcelId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val farmId = backStackEntry.arguments?.getString("farmId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val parcelId = backStackEntry.arguments?.getString("parcelId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || farmId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    ExpensesRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onExpenseSelected = { id -> navController.navigate(AppDestination.expense(id.toString())) },
                        onDocumentSelected = { id -> navController.navigate(AppDestination.document(id.toString())) },
                        onOrganizations = { navController.navigate(AppDestination.Organizations) },
                        presetFarmId = farmId,
                        presetParcelId = parcelId,
                        presetLabour = true,
                    )
                }
            }
            composable(
                AppDestination.FarmExpensesPattern,
                arguments = listOf(
                    navArgument("farmId") { type = NavType.StringType },
                    navArgument("parcelId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("campaignId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("activityId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("quick") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val farmId = backStackEntry.arguments?.getString("farmId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val parcelId = backStackEntry.arguments?.getString("parcelId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val campaignId = backStackEntry.arguments?.getString("campaignId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val activityId = backStackEntry.arguments?.getString("activityId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val quick = backStackEntry.arguments?.getBoolean("quick") ?: false
                if (persistence == null || farmId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    ExpensesRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onExpenseSelected = { id -> navController.navigate(AppDestination.expense(id.toString())) },
                        onDocumentSelected = { id -> navController.navigate(AppDestination.document(id.toString())) },
                        onOrganizations = { navController.navigate(AppDestination.Organizations) },
                        presetFarmId = farmId,
                        presetParcelId = parcelId,
                        presetCampaignId = campaignId,
                        presetActivityId = activityId,
                        presetQuick = quick,
                        // #415: «Guardar y añadir foto» opens the saved Gasto; a contextual entry is
                        // replaced by it, so Back returns where the flow started.
                        onOpenSavedExpense = { id ->
                            navController.navigate(AppDestination.expense(id.toString())) {
                                if (quick || activityId != null) {
                                    popUpTo(AppDestination.FarmExpensesPattern) { inclusive = true }
                                }
                            }
                        },
                        // #416: back to the work the expense was added from, which says so when it was saved.
                        onContextDone = { saved ->
                            if (saved && activityId != null) navController.previousBackStackEntry?.savedStateHandle?.set(RELATED_EXPENSE_ADDED_KEY, true)
                            navController.popBackStack()
                        },
                        onDocumentImported = { id ->
                            navController.navigate(
                                AppDestination.documentInContext(
                                    id.toString(), farmId.toString(), campaignId?.toString(),
                                    preselectRecollection = campaignId == null,
                                ),
                            )
                        },
                    )
                }
            }
            composable(AppDestination.JornadaPesadaPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val harvestId = backStackEntry.arguments?.getString("harvestId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || harvestId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    DeliveriesRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onDeliverySelected = { id -> navController.navigate(AppDestination.delivery(id.toString())) },
                        onTicketSelected = { id -> navController.navigate(AppDestination.ticket(id.toString())) },
                        jornadaId = harvestId,
                    )
                }
            }
            composable(AppDestination.DeliveryPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val deliveryId = backStackEntry.arguments?.getString("deliveryId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || deliveryId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    DeliveryDetailRoute(
                        deliveryId = deliveryId,
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onDeleted = { navController.popBackStack() },
                    )
                }
            }
            composable(AppDestination.DeliveryYieldPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val deliveryId = backStackEntry.arguments?.getString("deliveryId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || deliveryId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    DeliveryDetailRoute(
                        deliveryId = deliveryId,
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onDeleted = { navController.popBackStack() },
                        openYield = true,
                    )
                }
            }
            composable(AppDestination.PendingYieldsRoute) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    DeliveriesRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onDeliverySelected = { id -> navController.navigate(AppDestination.delivery(id.toString())) },
                        onTicketSelected = { id -> navController.navigate(AppDestination.ticket(id.toString())) },
                        initialStatus = YieldStatus.PENDING,
                        onAddYield = { id -> navController.navigate(AppDestination.deliveryYield(id.toString())) },
                    )
                }
            }
            composable(AppDestination.TicketPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val extractionId = backStackEntry.arguments?.getString("extractionId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || extractionId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    TicketReviewRoute(
                        extractionId = extractionId,
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onDeliveryCreated = { id ->
                            navController.navigate(AppDestination.delivery(id.toString())) {
                                popUpTo(AppDestination.TicketPattern) { inclusive = true }
                            }
                        },
                        onClosed = { navController.popBackStack() },
                    )
                }
            }
            composable(AppDestination.Expenses) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    ExpensesRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onExpenseSelected = { id -> navController.navigate(AppDestination.expense(id.toString())) },
                        onDocumentSelected = { id -> navController.navigate(AppDestination.document(id.toString())) },
                        onOrganizations = { navController.navigate(AppDestination.Organizations) },
                    )
                }
            }
            composable(AppDestination.ExpensePattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val expenseId = backStackEntry.arguments?.getString("expenseId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || expenseId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    ExpenseDetailRoute(expenseId, persistence, onDeleted = { navController.popBackStack() })
                }
            }
            composable(
                AppDestination.DocumentPattern,
                arguments = listOf(
                    navArgument("extractionId") { type = NavType.StringType },
                    navArgument("farmId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("campaignId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("recollection") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val extractionId = backStackEntry.arguments?.getString("extractionId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val contextFarmId = backStackEntry.arguments?.getString("farmId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                val contextCampaignId = backStackEntry.arguments?.getString("campaignId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || extractionId == null) {
                    PersistenceUnavailableScreen()
                } else {
                    DocumentReviewRoute(
                        extractionId = extractionId,
                        persistence = persistence,
                        onExpenseCreated = { expenseId ->
                            navController.navigate(AppDestination.expense(expenseId.toString())) {
                                popUpTo(AppDestination.DocumentPattern) { inclusive = true }
                            }
                        },
                        onClosed = { navController.popBackStack() },
                        contextFarmId = contextFarmId,
                        contextCampaignId = contextCampaignId,
                        requireCampaignChoice = backStackEntry.arguments?.getBoolean("recollection") == true,
                    )
                }
            }
            composable(AppDestination.Organizations) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) PersistenceUnavailableScreen() else OrganizationsRoute(persistence)
            }
            if (compositionRoot.environment == AppEnvironment.DEV) {
                composable(AppDestination.DeveloperGallery) { ComponentCatalogueReferenceScreen() }
            }
            composable(AppDestination.HelpPattern) { backStackEntry ->
                HelpTopic.of(backStackEntry.arguments?.getString("topic"))?.let { HelpRoute(it) }
            }
        }
    }
}

@Composable
private fun PersistenceUnavailableScreen() {
    NavigationPlaceholderScreen(
        title = "Datos locales no disponibles",
        description = "No se ha podido abrir el almacenamiento de este dispositivo. Cierra y vuelve a abrir la aplicación.",
        testTag = "local-persistence-error",
    )
}


/**
 * A bottom-bar tab always opens its own root screen, from wherever the farmer is: Inicio is
 * always Inicio, Mi Campo is always the farm list. Nothing is kept or restored per tab (that
 * made a tab reopen a screen left deep inside it), so Back simply walks the screens visited,
 * and from any root it returns to Inicio. Tapping the tab of the root already shown does nothing.
 */
private fun NavHostController.navigateToRoot(destination: RootDestination) {
    // #357/#358: the root already on screen is kept as it is. Popping it to Inicio and opening
    // it again recreated the screen (title flicker, view and scroll reset). #369: a Farm's
    // Cuaderno is not the Cuaderno tab, so its tab still opens the general Cuaderno.
    // The general Cuaderno also records ROOT once read, so the stored value decides, not its presence.
    val contextual = currentBackStackEntry?.savedStateHandle?.get<String>(NOTEBOOK_ORIGIN_KEY)
        ?.let { it != NotebookOrigin.ROOT.name } == true
    if (currentDestination?.route == destination.route && !contextual) return
    navigate(destination.route) {
        popUpTo(RootDestination.Home.route)
        launchSingleTop = true
    }
}

/** #369: where a Cuaderno opened from a Farm or a Parcel records that origin (and the Parcel). */
private const val NOTEBOOK_ORIGIN_KEY = "notebook-origin"
private const val NOTEBOOK_PARCEL_ID_KEY = "notebook-parcel-id"
private const val NOTEBOOK_PARCEL_NAME_KEY = "notebook-parcel-name"
private const val RELATED_EXPENSE_ADDED_KEY = "related-expense-added"

/** The Cuaderno's links, the same from Mi Cuaderno and from a Farm in Mi Campo. */
private fun NavHostController.notebookActions(farmId: UUID) = NotebookActions(
    onActivity = { id -> navigate(AppDestination.activity(id.toString())) },
    onHarvest = { id -> navigate(AppDestination.harvest(id.toString())) },
    onDelivery = { id -> navigate(AppDestination.delivery(id.toString())) },
    onExpense = { id -> navigate(AppDestination.expense(id.toString())) },
    onWorks = { navigate(AppDestination.farmSection(FarmSection.ACTIVITIES.route, farmId.toString())) },
    onHarvests = { navigate(AppDestination.Harvest) },
    onDeliveries = { navigate(AppDestination.Deliveries) },
    onPendingYields = { navigate(AppDestination.PendingYieldsRoute) },
    onExpenses = { navigate(AppDestination.Expenses) },
    onCampaignExpenses = { campaignId -> navigate(AppDestination.farmExpenses(farmId.toString(), campaignId = campaignId.toString())) },
    onCampaigns = { navigate(AppDestination.farmSection(FarmSection.CAMPAIGNS.route, farmId.toString())) },
)

/**
 * UX-D, CR-011: where each Cuaderno action goes — the existing forms, nothing new, always on
 * the Cuaderno's Farm. Activity kinds open the register flow with their type already chosen
 * and today's date; a running campaign's Jornal is handled by the caller (today's day).
 */
private fun NavHostController.openQuickAction(action: NotebookQuickAction, farmId: UUID, parcelId: String? = null) {
    when (action) {
        NotebookQuickAction.WORK ->
            navigate(AppDestination.register(null)) { launchSingleTop = true }
        NotebookQuickAction.IRRIGATION ->
            navigate(AppDestination.register(ActivityType.IRRIGATION.name)) { launchSingleTop = true }
        NotebookQuickAction.TREATMENT ->
            navigate(AppDestination.register(ActivityType.PHYTOSANITARY.name)) { launchSingleTop = true }
        NotebookQuickAction.WEIGHING -> navigate(AppDestination.newPesada(farmId.toString(), parcelId))
        // #415: one tap opens the form; saving or cancelling returns to the Cuaderno.
        NotebookQuickAction.EXPENSE -> navigate(AppDestination.farmExpenses(farmId.toString(), parcelId, quick = true))
        // #378: outside a running campaign, Jornal is the Farm's own labour — a labour expense,
        // opened and labelled as such, never a plain «Nuevo gasto».
        NotebookQuickAction.LABOUR -> navigate(AppDestination.farmLabour(farmId.toString(), parcelId))
    }
}
