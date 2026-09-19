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
import com.isivoltpro.maginaolivo.ui.components.MoBottomActionSheet
import com.isivoltpro.maginaolivo.ui.components.MoBottomBar
import com.isivoltpro.maginaolivo.ui.components.MoBottomBarItem
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.reference.campaign.CampaignReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.components.ComponentCatalogueReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.expenses.ExpensesDocumentsReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.farm.FarmDetailReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.harvest.HarvestReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.home.HomeReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.map.MapCatastroReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.ocr.DeliveryOcrReviewReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.olivar.OlivarReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.onboarding.OnboardingReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.parcel.ParcelDetailReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.register.RegisterActivityReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.weather.WeatherMarketReferenceScreen

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
                    onCampaignSelected = { navController.navigate(AppDestination.campaign("active")) },
                    onHarvestSelected = { navController.navigate(AppDestination.Harvest) },
                    onExpensesSelected = { navController.navigate(AppDestination.Expenses) },
                    onWeatherSelected = { navController.navigate(AppDestination.Weather) },
                )
            }
            composable(RootDestination.Olivar.route) {
                OlivarReferenceScreen(
                    onFarmSelected = { navController.navigate(AppDestination.farm("la-solana")) },
                )
            }
            composable(RootDestination.Register.route) { RegisterActivityReferenceScreen() }
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
            composable(AppDestination.FarmPattern) {
                FarmDetailReferenceScreen(
                    onParcelSelected = { navController.navigate(AppDestination.parcel(it)) },
                    onCampaignSelected = { navController.navigate(AppDestination.campaign("active")) },
                )
            }
            composable(AppDestination.ParcelPattern) {
                ParcelDetailReferenceScreen(
                    onMapSelected = { navController.navigate(AppDestination.MapCatastro) },
                    onCampaignSelected = { navController.navigate(AppDestination.campaign("active")) },
                )
            }
            composable(AppDestination.CampaignPattern) {
                CampaignReferenceScreen(
                    onAnalyticsSelected = { navController.navigate(AppDestination.Analytics) },
                )
            }
            composable(AppDestination.MapCatastro) { MapCatastroReferenceScreen() }
            composable(AppDestination.Weather) { WeatherMarketReferenceScreen() }
            composable(AppDestination.Analytics) { CampaignReferenceScreen() }
            composable(AppDestination.OcrReview) { DeliveryOcrReviewReferenceScreen() }
            composable(AppDestination.Harvest) {
                HarvestReferenceScreen(
                    onDeliverySelected = { navController.navigate(AppDestination.OcrReview) },
                )
            }
            composable(AppDestination.Expenses) { ExpensesDocumentsReferenceScreen() }
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
