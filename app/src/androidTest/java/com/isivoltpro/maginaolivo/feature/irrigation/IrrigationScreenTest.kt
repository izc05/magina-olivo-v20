package com.isivoltpro.maginaolivo.feature.irrigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}

