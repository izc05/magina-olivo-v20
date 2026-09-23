package com.isivoltpro.maginaolivo.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
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
import com.isivoltpro.maginaolivo.feature.expenses.OrganizationsRoute
import com.isivoltpro.maginaolivo.ui.components.MoBottomActionSheet
import com.isivoltpro.maginaolivo.ui.components.MoBottomBar
import com.isivoltpro.maginaolivo.ui.components.MoBottomBarItem
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.reference.campaign.CampaignReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.components.ComponentCatalogueReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.home.HomeReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.map.MapCatastroReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.ocr.DeliveryOcrReviewReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.onboarding.OnboardingReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.weather.WeatherMarketReferenceScreen
import java.util.UUID

private val bottomBarItems = RootDestination.entries.map { destination ->
    MoBottomBarItem(
        label = destination.label,
        symbol = destination.symbol,
        isPrimaryAction = destination.isPrimaryAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    compositionRoot: AppCompositionRoot,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
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
            modifier = Modifier.padding(innerPadding),
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
                HomeReferenceScreen(
                    showBottomBar = false,
                    onOlivarSelected = { navController.navigateToRoot(RootDestination.Olivar) },
                    onMapSelected = { navController.navigate(AppDestination.MapCatastro) },
                    onCampaignSelected = { navController.navigateToRoot(RootDestination.Olivar) },
                    onHarvestSelected = { navController.navigate(AppDestination.Harvest) },
                    onExpensesSelected = { navController.navigate(AppDestination.Expenses) },
                    onWeatherSelected = { navController.navigate(AppDestination.Weather) },
                )
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
                        onActivitySelected = { activityId ->
                            navController.navigate(AppDestination.activity(activityId.toString()))
                        },
                    )
                }
            }
            composable(RootDestination.Calendar.route) {
                NavigationPlaceholderScreen(
                    title = "Calendario",
                    description = "Aquí aparecerán las actuaciones planificadas y los recordatorios cuando se active su fase de datos.",
                    testTag = "calendar-root",
                )
            }
            composable(RootDestination.Profile.route) {
                NavigationPlaceholderScreen(
                    title = "Perfil y ajustes",
                    description = "Cuenta, preferencias, ayuda y estado de sincronización se incorporarán en sus fases correspondientes.",
                    testTag = "profile-root",
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
                        onParcelSelected = { parcelId ->
                            navController.navigate(AppDestination.parcel(parcelId.toString()))
                        },
                        onCampaignSelected = { campaignId ->
                            navController.navigate(AppDestination.campaign(campaignId.toString()))
                        },
                        onActivitySelected = { activityId ->
                            navController.navigate(AppDestination.activity(activityId.toString()))
                        },
                        onArchived = { navController.popBackStack() },
                    )
                }
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
                    )
                }
            }
            composable(AppDestination.CampaignPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val campaignId = backStackEntry.arguments?.getString("campaignId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || campaignId == null) PersistenceUnavailableScreen()
                else CampaignDetailRoute(campaignId, persistence)
            }
            composable(AppDestination.ActivityPattern) { backStackEntry ->
                val persistence = compositionRoot.localPersistence
                val activityId = backStackEntry.arguments?.getString("activityId")
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (persistence == null || activityId == null) PersistenceUnavailableScreen()
                else ActivityDetailRoute(activityId, persistence)
            }
            composable(AppDestination.MapCatastro) { MapCatastroReferenceScreen() }
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
        ModalBottomSheet(onDismissRequest = { registerSheetVisible = false }) {
            MoBottomActionSheet(
                title = "¿Qué quieres registrar?",
                body = "Elige el tipo de anotación. Podrás completar el contexto dentro del flujo.",
                modifier = Modifier.testTag("register-action-sheet"),
            ) {
                RegisterAction(
                    text = "Registrar actuación",
                    onClick = {
                        registerSheetVisible = false
                        navController.navigateToRoot(RootDestination.Register)
                    },
                )
                RegisterAction(
                    text = "Registrar cosecha",
                    onClick = {
                        registerSheetVisible = false
                        navController.navigate(AppDestination.Harvest)
                    },
                )
                RegisterAction(
                    text = "Registrar entrega",
                    onClick = {
                        registerSheetVisible = false
                        navController.navigate(AppDestination.Deliveries)
                    },
                )
                RegisterAction(
                    text = "Registrar gasto o documento",
                    onClick = {
                        registerSheetVisible = false
                        navController.navigate(AppDestination.Expenses)
                    },
                )
                MoSecondaryButton(
                    text = "Cancelar",
                    onClick = { registerSheetVisible = false },
                )
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

@Composable
private fun RegisterAction(
    text: String,
    onClick: () -> Unit,
) {
    MoPrimaryButton(
        text = text,
        onClick = onClick,
    )
}

private fun NavHostController.navigateToRoot(destination: RootDestination) {
    navigate(destination.route) {
        popUpTo(RootDestination.Home.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
