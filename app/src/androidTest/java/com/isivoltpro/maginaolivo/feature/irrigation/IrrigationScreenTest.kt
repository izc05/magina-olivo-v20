package com.isivoltpro.maginaolivo.feature.irrigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.isivoltpro.maginaolivo.domain.irrigation.CalculatedDose
import com.isivoltpro.maginaolivo.domain.irrigation.DaySchedule
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationRealStats
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationSummary
import com.isivoltpro.maginaolivo.domain.irrigation.IrrigationTypeBreakdown
import com.isivoltpro.maginaolivo.domain.irrigation.ScheduledTurnItem
import com.isivoltpro.maginaolivo.domain.irrigation.WeeklyPlan
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelAgronomy
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IrrigationScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private fun sampleParcel(
        id: UUID = UUID.randomUUID(),
        name: String = "Parcela Las Canteras",
        oliveCount: Int = 300,
    ) = Parcel(
        id = id,
        workspaceId = UUID.randomUUID(),
        farmId = UUID.randomUUID(),
        displayName = name,
        cadastralReference = null,
        cadastralPolygon = "1",
        cadastralParcel = "2",
        municipality = "Jódar",
        province = "Jaén",
        source = ParcelSource.MANUAL,
        geometryGeoJson = null,
        cadastralAreaM2 = 12000.0,
        managedAreaM2 = 12000.0,
        notes = null,
        archivedAt = null,
        version = 1L,
        agronomy = ParcelAgronomy(
            oliveTreeCount = oliveCount,
            irrigationSystem = IrrigationSystem.DRIP,
            irrigationNetwork = "Comunidad San Isidro",
            irrigationSector = "Sector 2",
            irrigationDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        ),
    )

    @Test
    fun screenWithData_displaysMetricsAndWeeklyTurns() {
        val parcel = sampleParcel()
        val turnItem = ScheduledTurnItem(
            parcelId = parcel.id,
            parcelName = parcel.displayName,
            sector = "Sector 2",
            communityOrNetwork = "Comunidad San Isidro",
            system = IrrigationSystem.DRIP,
            oliveTreeCount = 300,
        )

        val state = IrrigationUiState(
            farmName = "Finca La Loma",
            isLoading = false,
            selectedTab = IrrigationTab.SUMMARY,
            selectedDay = DayOfWeek.MONDAY,
            summary = IrrigationSummary(
                realStats = IrrigationRealStats(
                    totalVolumeM3 = 245.5,
                    totalDurationMinutes = 360L,
                    totalDurationHours = 6.0,
                    totalCostMinor = 7500L,
                    completedActivitiesCount = 3,
                    plannedActivitiesCount = 1,
                    distinctParcelsWatered = 1,
                ),
                breakdownByType = listOf(
                    IrrigationTypeBreakdown(IrrigationSystem.DRIP, 1, 300, 12000.0),
                ),
                totalParcelsCount = 1,
                irrigatedParcelsCount = 1,
                drylandParcelsCount = 0,
                unspecifiedSystemCount = 0,
            ),
            weeklyPlan = WeeklyPlan(
                days = DayOfWeek.entries.map { day ->
                    DaySchedule(day, if (day == DayOfWeek.MONDAY) listOf(turnItem) else emptyList())
                },
                totalScheduledTurns = 1,
                participatingParcelsCount = 1,
                communitiesInPlan = setOf("Comunidad San Isidro"),
            ),
            irrigatedParcels = listOf(parcel),
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                )
            }
        }

        // Header
        composeRule.onNodeWithTag("irrigation-title").assertIsDisplayed()
        composeRule.onNodeWithText("Finca La Loma").assertIsDisplayed()

        // Metrics
        composeRule.onNodeWithTag("irrigation-metric-volume").assertIsDisplayed()
        composeRule.onNodeWithText("245.5 m³").assertIsDisplayed()

        composeRule.onNodeWithTag("irrigation-metric-hours").assertIsDisplayed()
        composeRule.onNodeWithText("6.0 h").assertIsDisplayed()

        composeRule.onNodeWithTag("irrigation-metric-cost").assertIsDisplayed()
        composeRule.onNodeWithText("75.00 €").assertIsDisplayed()

        // Turn Card
        composeRule.onNodeWithTag("irrigation-summary-tab").performScrollToNode(hasTestTag("irrigation-turn-card-${parcel.id}"))
        composeRule.onNodeWithTag("irrigation-turn-card-${parcel.id}").assertIsDisplayed()
        composeRule.onNodeWithText("Parcela Las Canteras").assertIsDisplayed()
    }

    @Test
    fun emptyState_displaysCleanEmptyStateMessages() {
        val state = IrrigationUiState(
            isLoading = false,
            selectedTab = IrrigationTab.HISTORY,
            recentActivities = emptyList(),
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("irrigation-history-empty").assertIsDisplayed()
        composeRule.onNodeWithText("Aún no has registrado ningún riego").assertIsDisplayed()
    }

    @Test
    fun tabNavigation_triggersTabSelection() {
        val selectedTab = AtomicReference<IrrigationTab>()

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = IrrigationUiState(isLoading = false, selectedTab = IrrigationTab.SUMMARY),
                    onSelectTab = { selectedTab.set(it) },
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("irrigation-tab-installations").performClick()
        assertEquals(IrrigationTab.INSTALLATIONS, selectedTab.get())

        composeRule.onNodeWithTag("irrigation-tab-history").performClick()
        assertEquals(IrrigationTab.HISTORY, selectedTab.get())
    }

    @Test
    fun calculatorSection_displaysResultsBoxWhenDosePresent() {
        val state = IrrigationUiState(
            isLoading = false,
            selectedTab = IrrigationTab.INSTALLATIONS,
            calculator = DoseCalculatorState(
                parcelName = "Olivar El Santo",
                oliveTreeCountText = "200",
                emittersPerTreeText = "2",
                emitterFlowLphText = "4.0",
                durationMinutesText = "120",
                calculatedDose = CalculatedDose(
                    durationMinutes = 120,
                    totalFlowRateM3PerHour = 1.6,
                    theoreticalVolumeLiters = 3200.0,
                    theoreticalVolumeM3 = 3.2,
                    litersPerTree = 16.0,
                ),
            ),
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("dose-calculator-card").assertIsDisplayed()
        composeRule.onNodeWithTag("calc-results-box").assertIsDisplayed()
        composeRule.onNodeWithTag("calc-result-flow").assertTextContains("1.6 m³/h", substring = true)
        composeRule.onNodeWithTag("calc-result-volume").assertTextContains("3.2 m³", substring = true)
        composeRule.onNodeWithTag("calc-result-dose").assertTextContains("16.0 L / árbol", substring = true)
    }

    @Test
    fun headerButtons_triggerCallbacks() {
        val backClicked = AtomicBoolean(false)
        val registerClicked = AtomicBoolean(false)

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = IrrigationUiState(isLoading = false),
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = { registerClicked.set(true) },
                    onActivityClick = {},
                    onBack = { backClicked.set(true) },
                )
            }
        }

        composeRule.onNodeWithTag("irrigation-back-button").performClick()
        assertTrue(backClicked.get())

        composeRule.onNodeWithTag("irrigation-register-button").performClick()
        assertTrue(registerClicked.get())
    }

    @Test
    fun unauthorizedOrMissingFarm_displaysErrorAndNoHeaderOrTabs() {
        val backClicked = AtomicBoolean(false)
        val state = IrrigationUiState(
            isLoading = false,
            farmNotFound = true,
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = { backClicked.set(true) },
                )
            }
        }

        composeRule.onNodeWithTag("irrigation-unauthorized-root").assertIsDisplayed()
        composeRule.onNodeWithTag("irrigation-unauthorized-error").assertIsDisplayed()
        composeRule.onNodeWithText("Finca no disponible").assertIsDisplayed()
        composeRule.onNodeWithTag("irrigation-header").assertDoesNotExist()
        composeRule.onNodeWithTag("irrigation-register-button").assertDoesNotExist()

        composeRule.onNodeWithTag("irrigation-error-back").performClick()
        assertTrue(backClicked.get())
    }

    @Test
    fun simulationMode_displaysBannerAndAllowsClearing() {
        val clearClicked = AtomicBoolean(false)
        val state = IrrigationUiState(
            isLoading = false,
            selectedTab = IrrigationTab.INSTALLATIONS,
            calculator = DoseCalculatorState(
                isSimulationMode = true,
                oliveTreeCountText = "450",
                emittersPerTreeText = "2",
                emitterFlowLphText = "4.0",
                durationMinutesText = "120",
                calculatedDose = CalculatedDose(
                    durationMinutes = 120,
                    totalFlowRateM3PerHour = 3.6,
                    theoreticalVolumeLiters = 7200.0,
                    theoreticalVolumeM3 = 7.2,
                    litersPerTree = 16.0,
                ),
            ),
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onClearSimulation = { clearClicked.set(true) },
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("simulation-mode-banner").assertIsDisplayed()
        composeRule.onNodeWithText("Modo simulación (valores de ejemplo)").assertIsDisplayed()
        composeRule.onNodeWithText("Resultado de simulación (estimación teórica)").assertIsDisplayed()

        composeRule.onNodeWithTag("clear-simulation-button").performClick()
        assertTrue(clearClicked.get())
    }

    @Test
    fun pendingAuthorization_displaysLoadingIndicatorWithoutShellOrData() {
        val state = IrrigationUiState(
            isLoading = true,
            farmNotFound = false,
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("irrigation-loading").assertIsDisplayed()
        composeRule.onNodeWithTag("irrigation-screen").assertDoesNotExist()
        composeRule.onNodeWithTag("irrigation-title").assertDoesNotExist()
        composeRule.onNodeWithTag("irrigation-register-button").assertDoesNotExist()
        composeRule.onNodeWithTag("irrigation-unauthorized-root").assertDoesNotExist()
    }

    @Test
    fun archivedFarm_displaysArchivedBadgeAndHidesRegisterButton() {
        val state = IrrigationUiState(
            farmName = "Finca Huerta Vieja",
            isFarmArchived = true,
            isLoading = false,
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                )
            }
        }

        // Título con sufijo Archivada y badge presente
        composeRule.onNodeWithTag("irrigation-title").assertIsDisplayed()
        composeRule.onNodeWithTag("irrigation-archived-badge").assertIsDisplayed()
        composeRule.onNodeWithText("Finca Huerta Vieja · Archivada").assertIsDisplayed()

        // El botón para registrar riego NO debe ofrecerse (para no dar acciones engañosas)
        composeRule.onNodeWithTag("irrigation-register-button").assertDoesNotExist()
    }

    @Test
    fun farmerProfile_displaysOvernightBadgeAndEstimatedMetricsOnCalendar() {
        val parcelId = UUID.randomUUID()
        val turn = ScheduledTurnItem(
            parcelId = parcelId,
            parcelName = "La Loma Alta",
            communityOrNetwork = "Comunidad San Isidro",
            sector = "Sector 2",
            hydrant = "H-14",
            intakeOrValve = "Boca 3",
            timeSlot = com.isivoltpro.maginaolivo.domain.irrigation.ScheduledTimeSlot(
                startTime = java.time.LocalTime.of(22, 0),
                endTime = java.time.LocalTime.of(6, 0),
            ),
            estimatedVolumeM3 = 32.0,
            estimatedCostMinor = 640L,
            isExtraordinary = false,
        )

        val plan = WeeklyPlan(
            days = listOf(DaySchedule(dayOfWeek = DayOfWeek.MONDAY, turns = listOf(turn))),
            totalScheduledTurns = 1,
            participatingParcelsCount = 1,
            communitiesInPlan = setOf("Comunidad San Isidro"),
            totalEstimatedWeeklyVolumeM3 = 32.0,
            totalEstimatedWeeklyCostMinor = 640L,
        )

        val state = IrrigationUiState(
            farmName = "Finca San Isidro",
            isLoading = false,
            selectedTab = IrrigationTab.SUMMARY,
            selectedDay = DayOfWeek.MONDAY,
            weeklyPlan = plan,
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("irrigation-summary-tab").performScrollToNode(hasTestTag("add-punctual-turn-button"))
        composeRule.onNodeWithTag("add-punctual-turn-button").assertIsDisplayed()
        composeRule.onNodeWithTag("irrigation-summary-tab").performScrollToNode(hasTestTag("irrigation-turn-card-$parcelId"))
        composeRule.onNodeWithTag("irrigation-turn-card-$parcelId").assertIsDisplayed()
        composeRule.onNodeWithTag("overnight-badge").assertIsDisplayed()
        composeRule.onNodeWithText("La Loma Alta").assertIsDisplayed()
        composeRule.onNodeWithText("Comunidad San Isidro · Sector 2 · Hidrante H-14 · Boca 3").assertIsDisplayed()
    }

    @Test
    fun installationsTab_displaysConfigureButtonAndOpensProfileDialog() {
        val parcel = sampleParcel(name = "Parcela Don Diego")
        val openedParcel = AtomicReference<Parcel?>(null)
        val state = IrrigationUiState(
            farmName = "Finca Don Diego",
            isLoading = false,
            selectedTab = IrrigationTab.INSTALLATIONS,
            irrigatedParcels = listOf(parcel),
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                    onOpenProfileEditor = { openedParcel.set(it) },
                )
            }
        }

        composeRule.onNodeWithTag("irrigation-installations-tab").performScrollToNode(hasTestTag("configure-parcel-irrigation-${parcel.id}"))
        composeRule.onNodeWithTag("configure-parcel-irrigation-${parcel.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("configure-parcel-irrigation-${parcel.id}").performClick()
        org.junit.Assert.assertEquals(parcel.id, openedParcel.get()?.id)
    }

    @Test
    fun installationsTab_showsProfileDialogWhenDraftIsSet() {
        val parcel = sampleParcel(name = "Parcela Don Diego")
        val state = IrrigationUiState(
            farmName = "Finca Don Diego",
            isLoading = false,
            selectedTab = IrrigationTab.INSTALLATIONS,
            irrigatedParcels = listOf(parcel),
            editingProfileParcel = parcel,
            editingProfileDraft = com.isivoltpro.maginaolivo.domain.irrigation.ParcelIrrigationProfile(parcelId = parcel.id),
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("farmer-profile-dialog-content").assertIsDisplayed()
        composeRule.onNodeWithTag("save-irrigation-profile-button").assertIsDisplayed()
    }

    @Test
    fun punctualTurn_displaysExtraordinaryBadge() {
        val parcelId = UUID.randomUUID()
        val extraordinaryTurn = ScheduledTurnItem(
            parcelId = parcelId,
            parcelName = "Parcela El Valle",
            communityOrNetwork = "Comunidad San Isidro",
            timeSlot = com.isivoltpro.maginaolivo.domain.irrigation.ScheduledTimeSlot(
                startTime = java.time.LocalTime.of(18, 0),
                endTime = java.time.LocalTime.of(20, 0),
            ),
            isExtraordinary = true,
            reason = "Turno de socorro",
            punctualTurnId = UUID.randomUUID(),
        )

        val plan = WeeklyPlan(
            days = listOf(DaySchedule(dayOfWeek = DayOfWeek.MONDAY, turns = listOf(extraordinaryTurn))),
            totalScheduledTurns = 1,
            participatingParcelsCount = 1,
            communitiesInPlan = setOf("Comunidad San Isidro"),
        )

        val state = IrrigationUiState(
            farmName = "Finca El Valle",
            isLoading = false,
            selectedTab = IrrigationTab.SUMMARY,
            selectedDay = DayOfWeek.MONDAY,
            weeklyPlan = plan,
        )

        composeRule.setContent {
            MaginaOlivoTheme {
                IrrigationScreen(
                    state = state,
                    onSelectTab = {},
                    onSelectDay = {},
                    onSelectParcelForCalculator = {},
                    onUpdateTrees = {},
                    onUpdateEmitters = {},
                    onUpdateFlow = {},
                    onUpdateDuration = {},
                    onUpdateEmitterType = {},
                    onCalculateDose = {},
                    onRegisterIrrigation = {},
                    onActivityClick = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("irrigation-summary-tab").performScrollToNode(hasTestTag("extraordinary-badge"))
        composeRule.onNodeWithTag("extraordinary-badge").assertIsDisplayed()
    }
}

