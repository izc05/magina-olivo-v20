package com.isivoltpro.maginaolivo.feature.irrigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.irrigation.CalculatedDose
import com.isivoltpro.maginaolivo.domain.irrigation.EmitterType
import com.isivoltpro.maginaolivo.domain.irrigation.ScheduledTurnItem
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.getValue
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository

@Composable
fun IrrigationRoute(
    farmId: UUID,
    parcelRepository: ParcelRepository,
    activityRepository: ActivityRepository,
    farmRepository: FarmRepository? = null,
    onRegisterIrrigation: () -> Unit,
    onActivityClick: (UUID) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: IrrigationViewModel = viewModel(
        key = "IrrigationViewModel-$farmId",
        factory = viewModelFactory {
            initializer {
                IrrigationViewModel(
                    farmId = farmId,
                    parcelRepository = parcelRepository,
                    activityRepository = activityRepository,
                    farmRepository = farmRepository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    IrrigationScreen(
        state = state,
        onSelectTab = viewModel::selectTab,
        onSelectDay = viewModel::selectDay,
        onSelectParcelForCalculator = viewModel::loadParcelForCalculator,
        onUpdateTrees = viewModel::updateTrees,
        onUpdateEmitters = viewModel::updateEmittersPerTree,
        onUpdateFlow = viewModel::updateFlowLph,
        onUpdateDuration = viewModel::updateDuration,
        onUpdateEmitterType = viewModel::updateEmitterType,
        onCalculateDose = viewModel::recalculateDose,
        onRegisterIrrigation = onRegisterIrrigation,
        onActivityClick = onActivityClick,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun IrrigationScreen(
    state: IrrigationUiState,
    onSelectTab: (IrrigationTab) -> Unit,
    onSelectDay: (DayOfWeek) -> Unit,
    onSelectParcelForCalculator: (Parcel) -> Unit,
    onUpdateTrees: (String) -> Unit,
    onUpdateEmitters: (String) -> Unit,
    onUpdateFlow: (String) -> Unit,
    onUpdateDuration: (String) -> Unit,
    onUpdateEmitterType: (EmitterType) -> Unit,
    onCalculateDose: () -> Unit,
    onRegisterIrrigation: () -> Unit,
    onActivityClick: (UUID) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("irrigation-screen"),
    ) {
        IrrigationHeader(
            farmName = state.farmName,
            onBack = onBack,
            onRegister = onRegisterIrrigation,
        )

        IrrigationTabBar(
            selectedTab = state.selectedTab,
            onSelectTab = onSelectTab,
            modifier = Modifier.padding(horizontal = MoSpacing.md, vertical = MoSpacing.xs),
        )

        when (state.selectedTab) {
            IrrigationTab.SUMMARY -> IrrigationSummaryTab(
                state = state,
                onSelectDay = onSelectDay,
                onRegisterIrrigation = onRegisterIrrigation,
            )
            IrrigationTab.INSTALLATIONS -> IrrigationInstallationsTab(
                state = state,
                onSelectParcel = onSelectParcelForCalculator,
                onUpdateTrees = onUpdateTrees,
                onUpdateEmitters = onUpdateEmitters,
                onUpdateFlow = onUpdateFlow,
                onUpdateDuration = onUpdateDuration,
                onUpdateEmitterType = onUpdateEmitterType,
                onCalculate = onCalculateDose,
            )
            IrrigationTab.HISTORY -> IrrigationHistoryTab(
                activities = state.recentActivities,
                onActivityClick = onActivityClick,
                onRegisterIrrigation = onRegisterIrrigation,
            )
        }
    }
}

@Composable
private fun IrrigationHeader(
    farmName: String?,
    onBack: () -> Unit,
    onRegister: () -> Unit,
) {
    Surface(
        color = MoSurfaceTokens.cardSurface,
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MoSpacing.sm, vertical = MoSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("irrigation-back-button"),
                ) {
                    Icon(
                        imageVector = MoIcons.ChevronLeft,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column {
                    Text(
                        text = "Riego",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("irrigation-title"),
                    )
                    if (!farmName.isNullOrBlank()) {
                        Text(
                            text = farmName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MoSurfaceTokens.secondaryText,
                        )
                    }
                }
            }

            MoPrimaryButton(
                text = "+ Registrar riego",
                onClick = onRegister,
                modifier = Modifier.testTag("irrigation-register-button"),
            )
        }
    }
}

@Composable
private fun IrrigationTabBar(
    selectedTab: IrrigationTab,
    onSelectTab: (IrrigationTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        IrrigationTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Surface(
                shape = MoShape.pill,
                color = if (isSelected) MoColors.current.primaryButton else MoSurfaceTokens.cardSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MoColors.current.primaryButton else MoSurfaceTokens.cardStroke,
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectTab(tab) }
                    .testTag("irrigation-tab-${tab.name.lowercase()}"),
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isSelected) MoColors.current.onPrimaryButton else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(vertical = MoSpacing.xs)
                        .fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun IrrigationSummaryTab(
    state: IrrigationUiState,
    onSelectDay: (DayOfWeek) -> Unit,
    onRegisterIrrigation: () -> Unit,
) {
    val realStats = state.summary.realStats

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("irrigation-summary-tab"),
        contentPadding = PaddingValues(MoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
    ) {
        // 1. Metric Cards Row
        item {
            MoSectionHeader("Balance de riego de la campaña")
            Spacer(Modifier.height(MoSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoMetricCard(
                    label = "Volumen registrado",
                    value = "${realStats.totalVolumeM3} m³",
                    supportingText = "${realStats.completedActivitiesCount} riegos",
                    leading = {
                        Icon(MoIcons.Drop, contentDescription = null, tint = MoColors.current.primaryButton)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("irrigation-metric-volume"),
                )
                MoMetricCard(
                    label = "Horas acumuladas",
                    value = "${realStats.totalDurationHours} h",
                    supportingText = "${realStats.totalDurationMinutes} min",
                    leading = {
                        Icon(MoIcons.Clock, contentDescription = null, tint = MoColors.current.primaryButton)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("irrigation-metric-hours"),
                )
            }
            Spacer(Modifier.height(MoSpacing.xs))
            MoMetricCard(
                label = "Coste variable estimado de agua",
                value = formatEuros(realStats.totalCostMinor),
                supportingText = "Solo riegos completados (excluye cuotas fijas de comunidad)",
                leading = {
                    Icon(MoIcons.Euro, contentDescription = null, tint = MoColors.current.primaryButton)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("irrigation-metric-cost"),
            )
        }

        // 2. Irrigation Breakdown Badges
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                state.summary.breakdownByType.forEach { breakdown ->
                    Card(
                        shape = MoShape.pill,
                        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
                        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(MoSpacing.xs),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = breakdown.systemLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MoSurfaceTokens.secondaryText,
                            )
                            Text(
                                text = "${breakdown.parcelCount} parcelas",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // 3. Weekly Calendar Section
        item {
            MoSectionHeader("Plan semanal de turnos de riego")
            Spacer(Modifier.height(MoSpacing.xs))
            DayOfWeekSelector(
                selectedDay = state.selectedDay,
                plan = state.weeklyPlan,
                onSelectDay = onSelectDay,
            )
        }

        // 4. Turns for selected day
        val daySchedule = state.weeklyPlan.days.firstOrNull { it.dayOfWeek == state.selectedDay }
        val turnsForDay = daySchedule?.turns.orEmpty()

        if (turnsForDay.isEmpty()) {
            item {
                MoEmptyState(
                    title = "Sin turnos habituales este día",
                    body = "Ninguna parcela tiene asignado el ${dayLabel(state.selectedDay)} como día habitual de riego.",
                    icon = MoIcons.Calendar,
                )
            }
        } else {
            items(turnsForDay, key = { it.parcelId }) { turn ->
                TurnItemCard(turn)
            }
        }

        // 5. Explanatory Note
        item {
            Card(
                shape = MoShape.card,
                colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
                border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(MoSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = MoIcons.Warning,
                        contentDescription = null,
                        tint = MoSurfaceTokens.secondaryText,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Los días habituales son configurados por el agricultor en cada parcela. No constituyen turnos confirmados oficialmente por la comunidad de regantes ni suponen agua efectivamente entregada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayOfWeekSelector(
    selectedDay: DayOfWeek,
    plan: com.isivoltpro.maginaolivo.domain.irrigation.WeeklyPlan,
    onSelectDay: (DayOfWeek) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("irrigation-day-selector"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day == selectedDay
            val hasTurns = plan.days.firstOrNull { it.dayOfWeek == day }?.hasTurns == true

            Surface(
                shape = MoShape.pill,
                color = if (isSelected) MoColors.current.primaryButton else MoSurfaceTokens.cardSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MoColors.current.primaryButton else MoSurfaceTokens.cardStroke,
                ),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectDay(day) }
                    .testTag("day-chip-${day.name.lowercase()}"),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = MoSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = dayShortLabel(day),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (isSelected) MoColors.current.onPrimaryButton else MaterialTheme.colorScheme.onSurface,
                    )
                    if (hasTurns) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MoColors.current.onPrimaryButton else MoColors.current.primaryButton),
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnItemCard(turn: ScheduledTurnItem) {
    Card(
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("irrigation-turn-card-${turn.parcelId}"),
    ) {
        Row(
            modifier = Modifier.padding(MoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = turn.parcelName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = turn.locationDescription ?: "Comunidad y sector sin especificar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoSurfaceTokens.secondaryText,
                )
                if (turn.oliveTreeCount != null && turn.oliveTreeCount > 0) {
                    Text(
                        text = "${turn.oliveTreeCount} olivos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
                }
            }

            Surface(
                shape = MoShape.pill,
                color = MoSurfaceTokens.cardSurface,
                border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
            ) {
                Text(
                    text = turn.system?.name ?: "Riego",
                    style = MaterialTheme.typography.labelSmall,
                    color = MoColors.current.primaryButton,
                    modifier = Modifier.padding(horizontal = MoSpacing.xs, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun IrrigationInstallationsTab(
    state: IrrigationUiState,
    onSelectParcel: (Parcel) -> Unit,
    onUpdateTrees: (String) -> Unit,
    onUpdateEmitters: (String) -> Unit,
    onUpdateFlow: (String) -> Unit,
    onUpdateDuration: (String) -> Unit,
    onUpdateEmitterType: (EmitterType) -> Unit,
    onCalculate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("irrigation-installations-tab"),
        contentPadding = PaddingValues(MoSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
    ) {
        // 1. Interactive Dose Calculator Card
        item {
            DoseCalculatorSection(
                calc = state.calculator,
                onUpdateTrees = onUpdateTrees,
                onUpdateEmitters = onUpdateEmitters,
                onUpdateFlow = onUpdateFlow,
                onUpdateDuration = onUpdateDuration,
                onUpdateEmitterType = onUpdateEmitterType,
                onCalculate = onCalculate,
            )
        }

        // 2. Installations List
        item {
            MoSectionHeader("Parcelas de regadío en la explotación")
        }

        if (state.irrigatedParcels.isEmpty()) {
            item {
                MoEmptyState(
                    title = "Sin parcelas de regadío",
                    body = "Ninguna parcela de esta finca tiene asignado sistema de riego (goteo o aspersión).",
                    icon = MoIcons.Parcels,
                )
            }
        } else {
            items(state.irrigatedParcels, key = { it.id }) { parcel ->
                ParcelInstallationCard(parcel = parcel, onSimulate = { onSelectParcel(parcel) })
            }
        }
    }
}

@Composable
private fun ParcelInstallationCard(
    parcel: Parcel,
    onSimulate: () -> Unit,
) {
    Card(
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MoSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = parcel.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val agro = parcel.agronomy
                val systemLabel = when (agro.irrigationSystem) {
                    IrrigationSystem.DRIP -> "Goteo"
                    IrrigationSystem.SPRINKLER -> "Aspersión"
                    IrrigationSystem.OTHER -> "Otro"
                    else -> "Secano"
                }
                val network = agro.irrigationNetwork ?: "Comunidad no asignada"
                val sector = agro.irrigationSector?.let { " · Sector $it" }.orEmpty()
                Text(
                    text = "$systemLabel · $network$sector",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoSurfaceTokens.secondaryText,
                )
                if (agro.oliveTreeCount != null) {
                    Text(
                        text = "${agro.oliveTreeCount} olivos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MoColors.current.primaryButton,
                    )
                }
            }

            OutlinedButton(
                onClick = onSimulate,
                shape = MoShape.pill,
                modifier = Modifier.testTag("simulate-dose-${parcel.id}"),
            ) {
                Text("Simular dosis", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DoseCalculatorSection(
    calc: DoseCalculatorState,
    onUpdateTrees: (String) -> Unit,
    onUpdateEmitters: (String) -> Unit,
    onUpdateFlow: (String) -> Unit,
    onUpdateDuration: (String) -> Unit,
    onUpdateEmitterType: (EmitterType) -> Unit,
    onCalculate: () -> Unit,
) {
    Card(
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dose-calculator-card"),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MoIcons.Activity,
                    contentDescription = null,
                    tint = MoColors.current.primaryButton,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(MoSpacing.xs))
                Text(
                    text = "Calculadora agronómica de dotación",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (!calc.parcelName.isNullOrBlank()) {
                Text(
                    text = "Parcela seleccionada: ${calc.parcelName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MoColors.current.primaryButton,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoTextField(
                    value = calc.oliveTreeCountText,
                    onValueChange = onUpdateTrees,
                    label = "Nº de olivos",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc-input-trees"),
                )
                MoTextField(
                    value = calc.emittersPerTreeText,
                    onValueChange = onUpdateEmitters,
                    label = "Goteros / olivo",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc-input-emitters"),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                MoTextField(
                    value = calc.emitterFlowLphText,
                    onValueChange = onUpdateFlow,
                    label = "Caudal gotero (L/h)",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc-input-flow"),
                )
                MoTextField(
                    value = calc.durationMinutesText,
                    onValueChange = onUpdateDuration,
                    label = "Duración (min)",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calc-input-duration"),
                )
            }

            // Results Box
            val dose = calc.calculatedDose
            if (dose != null) {
                Surface(
                    shape = MoShape.card,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calc-results-box"),
                ) {
                    Column(
                        modifier = Modifier.padding(MoSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
                    ) {
                        Text(
                            text = "Resultado teórico estimado",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Caudal horario total:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${dose.totalFlowRateM3PerHour} m³/h",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.testTag("calc-result-flow"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Volumen teórico total:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${dose.theoreticalVolumeM3} m³ (${dose.theoreticalVolumeLiters} L)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.testTag("calc-result-volume"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Dosis neta por olivo:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${dose.litersPerTree} L / árbol",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MoColors.current.primaryButton,
                                ),
                                modifier = Modifier.testTag("calc-result-dose"),
                            )
                        }
                    }
                }
            } else if (!calc.error.isNullOrBlank()) {
                Text(
                    text = calc.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                text = "Cálculo agronómico teórico de emisores. La medición real del consumo se realiza mediante lectura de contador o aforo en el Cuaderno de campo.",
                style = MaterialTheme.typography.labelSmall,
                color = MoSurfaceTokens.secondaryText,
            )
        }
    }
}

@Composable
private fun IrrigationHistoryTab(
    activities: List<Activity>,
    onActivityClick: (UUID) -> Unit,
    onRegisterIrrigation: () -> Unit,
) {
    if (activities.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(MoSpacing.md)
                .testTag("irrigation-history-empty"),
        ) {
            MoEmptyState(
                title = "Aún no has registrado ningún riego",
                body = "Registra un riego desde el botón superior o desde el Cuaderno para ver aquí su volumen, duración y coste acumulado.",
                actionText = "Registrar riego",
                onAction = onRegisterIrrigation,
                icon = MoIcons.History,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("irrigation-history-list"),
            contentPadding = PaddingValues(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            item {
                MoSectionHeader("Riegos completados en esta explotación (${activities.size})")
            }
            items(activities, key = { it.id }) { activity ->
                HistoryActivityCard(activity = activity, onClick = { onActivityClick(activity.id) })
            }
        }
    }
}

@Composable
private fun HistoryActivityCard(
    activity: Activity,
    onClick: () -> Unit,
) {
    val detail = activity.detail as? ActivityDetail.Irrigation

    Card(
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("irrigation-activity-card-${activity.id}"),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = activity.activityDate.format(DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", Locale("es"))),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val cost = detail?.price?.estimatedAmountMinor ?: activity.costMinor
                if (cost != null && cost > 0L) {
                    Text(
                        text = formatEuros(cost),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MoColors.current.primaryButton,
                    )
                }
            }

            val targetNames = activity.targets.joinToString(", ") { it.parcelName }
            if (targetNames.isNotBlank()) {
                Text(
                    text = targetNames,
                    style = MaterialTheme.typography.bodySmall,
                    color = MoSurfaceTokens.secondaryText,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.md),
            ) {
                detail?.volumeM3?.let {
                    Text(
                        text = "$it m³",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                detail?.durationMinutes?.let {
                    Text(
                        text = "$it min (${String.format(Locale.ROOT, "%.1f", it / 60.0)} h)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
                }
                detail?.sectorText?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = "Sector: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
                }
            }
        }
    }
}

private fun formatEuros(cents: Long): String =
    String.format(Locale.ROOT, "%.2f €", cents / 100.0)

private fun dayShortLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "L"
    DayOfWeek.TUESDAY -> "M"
    DayOfWeek.WEDNESDAY -> "X"
    DayOfWeek.THURSDAY -> "J"
    DayOfWeek.FRIDAY -> "V"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "D"
}

private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "lunes"
    DayOfWeek.TUESDAY -> "martes"
    DayOfWeek.WEDNESDAY -> "miércoles"
    DayOfWeek.THURSDAY -> "jueves"
    DayOfWeek.FRIDAY -> "viernes"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}
