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
import com.isivoltpro.maginaolivo.feature.farms.FarmListRoute
import com.isivoltpro.maginaolivo.feature.parcels.ParcelDetailRoute
import com.isivoltpro.maginaolivo.feature.activities.ActivityDetailRoute
import com.isivoltpro.maginaolivo.feature.activities.RegisterActivityRoute
import com.isivoltpro.maginaolivo.feature.campaigns.CampaignDetailRoute
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailRoute
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveriesRoute
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
import com.isivoltpro.maginaolivo.feature.expenses.OrganizationsRoute
import com.isivoltpro.maginaolivo.ui.components.MoBottomBar
import com.isivoltpro.maginaolivo.ui.components.MoBottomBarItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.reference.campaign.CampaignReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.components.ComponentCatalogueReferenceScreen
import com.isivoltpro.maginaolivo.feature.catastro.CadastreImportRoute
import com.isivoltpro.maginaolivo.feature.maps.FarmMapRoute
import com.isivoltpro.maginaolivo.ui.reference.ocr.DeliveryOcrReviewReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.onboarding.OnboardingReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.weather.WeatherMarketReferenceScreen
import java.util.UUID

private val bottomBarItems = RootDestination.entries.map { destination ->
    MoBottomBarItem(
        label = destination.label,
        symbol = destination.symbol,
        isPrimaryAction = destination.isPrimaryAction,
        icon = when (destination) {
            RootDestination.Home -> MoIcons.Home
            RootDestination.Olivar -> MoIcons.Tree
            RootDestination.Register -> MoIcons.Plus
            RootDestination.Calendar -> MoIcons.Calendar
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
    var registerSheetVisible by rememberSaveable { mutableStateOf(false) }
    // The Farm the Quick Add context resolved, handed to the activity flow once.
    var registerFarmId by rememberSaveable { mutableStateOf<String?>(null) }
    val quickAddContext = rememberQuickAddContext(
        persistence = compositionRoot.localPersistence,
        route = backStackEntry?.destination?.route,
        argumentId = backStackEntry?.arguments?.let { arguments ->
            listOf("farmId", "parcelId", "campaignId", "activityId").firstNotNullOfOrNull { arguments.getString(it) }
        },
    )
    // A reminder opens its Activity over the Calendar, so Back returns to the agenda.
    LaunchedEffect(openActivityId, backStackEntry == null) {
        val id = openActivityId ?: return@LaunchedEffect
        if (backStackEntry == null || startDestination == AppDestination.Onboarding) return@LaunchedEffect
        navController.navigateToRoot(RootDestination.Calendar)
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
                    onSelected = { index ->
                        val destination = RootDestination.entries[index]
                        if (destination == RootDestination.Register) {
                            registerSheetVisible = true
                        } else {
                            navController.navigateToRoot(destination)
                        }
                    },
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
                        onOlivar = { navController.navigateToRoot(RootDestination.Olivar) },
                        onCalendar = { navController.navigateToRoot(RootDestination.Calendar) },
                        onHarvest = { navController.navigate(AppDestination.Harvest) },
                        onDeliveries = { navController.navigate(AppDestination.Deliveries) },
                        onExpenses = { navController.navigate(AppDestination.Expenses) },
                        onActivitySelected = { id -> navController.navigate(AppDestination.activity(id.toString())) },
                    )
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
                        onMachinery = { navController.navigate(AppDestination.Machinery) },
                    )
                }
            }
            composable(RootDestination.Register.route) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    RegisterActivityRoute(
                        persistence = persistence,
                        preselectedFarmId = registerFarmId?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                        onFarmPreselected = { registerFarmId = null },
                        onActivitySelected = { activityId ->
                            navController.navigate(AppDestination.activity(activityId.toString()))
                        },
                    )
                }
            }
            composable(RootDestination.Calendar.route) {
                val persistence = compositionRoot.localPersistence
                if (persistence == null) {
                    PersistenceUnavailableScreen()
                } else {
                    AgendaRoute(
                        persistence = persistence,
                        clock = compositionRoot.clock,
                        onActivitySelected = { id -> navController.navigate(AppDestination.activity(id.toString())) },
                        onPlanWork = { navController.navigateToRoot(RootDestination.Register) },
                    )
                }
            }
            composable(RootDestination.Profile.route) {
                ProfileRoute(
                    appVersion = com.isivoltpro.maginaolivo.BuildConfig.VERSION_NAME,
                    onMachinery = { navController.navigate(AppDestination.Machinery) },
                    developerGalleryEnabled = compositionRoot.environment == AppEnvironment.DEV,
                    onDeveloperGallery = { navController.navigate(AppDestination.DeveloperGallery) },
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
                            navController.navigate(AppDestination.farmSection(section.route, farmId.toString()))
                        },
                        onArchived = { navController.popBackStack() },
                    )
                }
            }
            FarmSection.entries.forEach { section ->
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
                            notebookActions = com.isivoltpro.maginaolivo.feature.notebook.NotebookActions(
                                onActivity = { id -> navController.navigate(AppDestination.activity(id.toString())) },
                                onHarvest = { id -> navController.navigate(AppDestination.harvest(id.toString())) },
                                onDelivery = { id -> navController.navigate(AppDestination.delivery(id.toString())) },
                                onExpense = { id -> navController.navigate(AppDestination.expense(id.toString())) },
                                onWorks = { navController.navigate(AppDestination.farmSection(FarmSection.ACTIVITIES.route, farmId.toString())) },
                                onHarvests = { navController.navigate(AppDestination.Harvest) },
                                onDeliveries = { navController.navigate(AppDestination.Deliveries) },
                                onExpenses = { navController.navigate(AppDestination.Expenses) },
                                onCampaigns = { navController.navigate(AppDestination.farmSection(FarmSection.CAMPAIGNS.route, farmId.toString())) },
                            ),
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
                    onHarvests = { navController.navigate(AppDestination.Harvest) },
                    onDeliveries = { navController.navigate(AppDestination.Deliveries) },
                )
            }
            composable(AppDestination.ActivityPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val activityId = backStackEntry.arguments?.getString("activityId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || activityId == null) PersistenceUnavailableScreen()
                else ActivityDetailRoute(activityId, persistence)
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
            composable(AppDestination.Weather) { WeatherMarketReferenceScreen() }
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
                        onDeliveries = { navController.navigate(AppDestination.Deliveries) },
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
            composable(AppDestination.DocumentPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val extractionId = backStackEntry.arguments?.getString("extractionId")
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
        }
    }

    if (registerSheetVisible) {
        // UI polish v2: open fully (never half-cut) and let the sheet scroll on short screens.
        ModalBottomSheet(
            onDismissRequest = { registerSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            QuickAddSheet(
                context = quickAddContext,
                onAction = { action ->
                    registerSheetVisible = false
                    when (action) {
                        QuickAddAction.ACTIVITY, QuickAddAction.PLAN -> {
                            registerFarmId = quickAddContext?.farmId?.toString()
                            navController.navigateToRoot(RootDestination.Register)
                        }
                        QuickAddAction.HARVEST -> navController.navigate(AppDestination.Harvest)
                        QuickAddAction.DELIVERY -> navController.navigate(AppDestination.Deliveries)
                        QuickAddAction.EXPENSE -> navController.navigate(AppDestination.Expenses)
                    }
                },
                onCancel = { registerSheetVisible = false },
            )
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
 * always Inicio, Mi Olivar is always the farm list. Nothing is kept or restored per tab (that
 * made a tab reopen a screen left deep inside it), so Back simply walks the screens visited,
 * and from any root it returns to Inicio.
 */
private fun NavHostController.navigateToRoot(destination: RootDestination) {
    navigate(destination.route) {
        popUpTo(RootDestination.Home.route)
        launchSingleTop = true
    }
}
