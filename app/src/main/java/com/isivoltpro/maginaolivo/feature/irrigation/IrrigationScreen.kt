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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AccountState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityRepository
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.irrigation.AforoConfig
import com.isivoltpro.maginaolivo.domain.irrigation.AndroidFarmerIrrigationStore
import com.isivoltpro.maginaolivo.domain.irrigation.CalculatedDose
import com.isivoltpro.maginaolivo.domain.irrigation.EmitterType
import com.isivoltpro.maginaolivo.domain.irrigation.FarmerIrrigationStore
import com.isivoltpro.maginaolivo.domain.irrigation.FlowMeasurementMode
import com.isivoltpro.maginaolivo.domain.irrigation.FlowUnit
import com.isivoltpro.maginaolivo.domain.irrigation.ParcelIrrigationProfile
import com.isivoltpro.maginaolivo.domain.irrigation.PunctualTurn
import com.isivoltpro.maginaolivo.domain.irrigation.ScheduledTimeSlot
import com.isivoltpro.maginaolivo.domain.irrigation.ScheduledTurnItem
import com.isivoltpro.maginaolivo.domain.irrigation.WaterSourceType
import com.isivoltpro.maginaolivo.domain.irrigation.WaterSupplyDetails
import com.isivoltpro.maginaolivo.domain.irrigation.WaterTariff
import com.isivoltpro.maginaolivo.domain.irrigation.WaterTariffType
import com.isivoltpro.maginaolivo.domain.parcel.IrrigationSystem
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun IrrigationRoute(
    farmId: UUID,
    parcelRepository: ParcelRepository,
    activityRepository: ActivityRepository,
    farmRepository: FarmRepository,
    onRegisterIrrigation: () -> Unit,
    onActivityClick: (UUID) -> Unit,
    onBack: () -> Unit,
    accountRepository: AccountRepository? = null,
    farmerIrrigationStore: FarmerIrrigationStore? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = farmerIrrigationStore ?: remember(context) { AndroidFarmerIrrigationStore(context) }
    val identity by accountRepository?.state?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(AccountState()) }

    val viewModel: IrrigationViewModel = viewModel(
        key = "IrrigationViewModel-$farmId-${identity.userId}",
        factory = viewModelFactory {
            initializer {
                IrrigationViewModel(
                    farmId = farmId,
                    parcelRepository = parcelRepository,
                    activityRepository = activityRepository,
                    farmRepository = farmRepository,
                    farmerIrrigationStore = store,
                    accountRepository = accountRepository,
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
        onLoadSimulation = viewModel::loadSimulationExample,
        onClearSimulation = viewModel::clearSimulation,
        onOpenProfileEditor = viewModel::openProfileEditor,
        onUpdateProfileDraft = viewModel::updateProfileDraft,
        onCloseProfileEditor = viewModel::closeProfileEditor,
        onSaveProfile = viewModel::saveProfile,
        onOpenPunctualTurnDialog = viewModel::openPunctualTurnDialog,
        onClosePunctualTurnDialog = viewModel::closePunctualTurnDialog,
        onAddPunctualTurn = viewModel::addPunctualTurn,
        onTogglePunctualTurnCancellation = viewModel::togglePunctualTurnCancellation,
        onDeletePunctualTurn = viewModel::deletePunctualTurn,
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
    onLoadSimulation: () -> Unit = {},
    onClearSimulation: () -> Unit = {},
    onOpenProfileEditor: (Parcel) -> Unit = {},
    onUpdateProfileDraft: (ParcelIrrigationProfile) -> Unit = {},
    onCloseProfileEditor: () -> Unit = {},
    onSaveProfile: (ParcelIrrigationProfile) -> Unit = {},
    onOpenPunctualTurnDialog: (UUID?) -> Unit = {},
    onClosePunctualTurnDialog: () -> Unit = {},
    onAddPunctualTurn: (PunctualTurn) -> Unit = {},
    onTogglePunctualTurnCancellation: (UUID, UUID) -> Unit = { _, _ -> },
    onDeletePunctualTurn: (UUID, UUID) -> Unit = { _, _ -> },
    onRegisterIrrigation: () -> Unit,
    onActivityClick: (UUID) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("irrigation-loading"),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MoColors.current.primaryButton)
        }
        return
    }

    if (state.farmNotFound) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(MoSpacing.screen)
                .testTag("irrigation-unauthorized-root"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MoErrorState(
                title = "Finca no disponible",
                body = "No tienes acceso a esta finca o no existe en el espacio de trabajo actual.",
                modifier = Modifier.testTag("irrigation-unauthorized-error"),
            )
            Spacer(Modifier.height(MoSpacing.md))
            MoSecondaryButton(
                text = "Volver",
                onClick = onBack,
                modifier = Modifier.testTag("irrigation-error-back"),
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("irrigation-screen"),
    ) {
        IrrigationHeader(
            farmName = state.farmName,
            isArchived = state.isFarmArchived,
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
                onOpenPunctualDialog = { onOpenPunctualTurnDialog(null) },
                onToggleCancellation = onTogglePunctualTurnCancellation,
            )
            IrrigationTab.INSTALLATIONS -> IrrigationInstallationsTab(
                state = state,
                onSelectParcel = onSelectParcelForCalculator,
                onConfigureParcel = onOpenProfileEditor,
                onUpdateTrees = onUpdateTrees,
                onUpdateEmitters = onUpdateEmitters,
                onUpdateFlow = onUpdateFlow,
                onUpdateDuration = onUpdateDuration,
                onUpdateEmitterType = onUpdateEmitterType,
                onCalculate = onCalculateDose,
                onLoadSimulation = onLoadSimulation,
                onClearSimulation = onClearSimulation,
            )
            IrrigationTab.HISTORY -> IrrigationHistoryTab(
                activities = state.recentActivities,
                isArchived = state.isFarmArchived,
                onActivityClick = onActivityClick,
                onRegisterIrrigation = onRegisterIrrigation,
            )
        }
    }

    // Modal: Profile Configuration Dialog (B.5)
    if (state.editingProfileParcel != null && state.editingProfileDraft != null) {
        FarmerIrrigationProfileDialog(
            parcel = state.editingProfileParcel,
            draft = state.editingProfileDraft,
            onUpdateDraft = onUpdateProfileDraft,
            onSave = { onSaveProfile(state.editingProfileDraft) },
            onDismiss = onCloseProfileEditor,
        )
    }

    // Modal: Punctual Turn Dialog (B.5)
    if (state.showPunctualTurnDialog) {
        PunctualTurnDialog(
            parcels = state.irrigatedParcels.ifEmpty { state.allParcels },
            initialParcelId = state.punctualTurnParcelId,
            onConfirm = onAddPunctualTurn,
            onDismiss = onClosePunctualTurnDialog,
        )
    }
}

@Composable
private fun IrrigationHeader(
    farmName: String?,
    isArchived: Boolean,
    onBack: () -> Unit,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth(),
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
                    ) {
                        Text(
                            text = "Riego",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("irrigation-title"),
                        )
                        if (isArchived) {
                            Surface(
                                shape = MoShape.pill,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                                modifier = Modifier.testTag("irrigation-archived-badge"),
                            ) {
                                Text(
                                    text = "Archivada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MoSurfaceTokens.secondaryText,
                                    modifier = Modifier.padding(horizontal = MoSpacing.xs, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (!farmName.isNullOrBlank()) {
                        Text(
                            text = if (isArchived) "$farmName · Archivada" else farmName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MoSurfaceTokens.secondaryText,
                        )
                    }
                }
            }

            if (!isArchived) {
                MoPrimaryButton(
                    text = "+ Registrar riego",
                    onClick = onRegister,
                    modifier = Modifier.testTag("irrigation-register-button"),
                )
            }
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
    onOpenPunctualDialog: () -> Unit,
    onToggleCancellation: (UUID, UUID) -> Unit,
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

        // 2. Annual Community Fees (Fixed quotas)
        if (state.summary.annualCommunityFees.isNotEmpty()) {
            item {
                Card(
                    shape = MoShape.card,
                    colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
                    border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("irrigation-community-fees-section"),
                ) {
                    Column(
                        modifier = Modifier.padding(MoSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    ) {
                        Text(
                            text = "Cuotas fijas anuales de comunidad",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        state.summary.annualCommunityFees.forEach { fee ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (!fee.sector.isNullOrBlank()) "${fee.communityName} · Sector ${fee.sector}" else fee.communityName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MoSurfaceTokens.secondaryText,
                                )
                                Text(
                                    text = formatEuros(fee.annualFixedFeeMinor),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Weekly Calendar Section & Actions
        item {
            MoSectionHeader(
                title = "Plan semanal de turnos de riego",
                action = {
                    if (!state.isFarmArchived) {
                        OutlinedButton(
                            onClick = onOpenPunctualDialog,
                            shape = MoShape.pill,
                            modifier = Modifier.testTag("add-punctual-turn-button"),
                        ) {
                            Text("+ Turno extraordinario", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
            )
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
            items(turnsForDay, key = { it.punctualTurnId ?: it.parcelId }) { turn ->
                TurnItemCard(turn = turn, onToggleCancellation = onToggleCancellation)
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
                        text = "Los días y turnos habituales son configurados por el agricultor en cada parcela. No constituyen turnos confirmados oficialmente por la comunidad de regantes ni suponen agua efectivamente entregada.",
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
private fun TurnItemCard(
    turn: ScheduledTurnItem,
    onToggleCancellation: (UUID, UUID) -> Unit = { _, _ -> },
) {
    Card(
        shape = MoShape.card,
        colors = CardDefaults.cardColors(
            containerColor = if (turn.isCancelled) MaterialTheme.colorScheme.surfaceVariant else MoSurfaceTokens.cardSurface,
        ),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("irrigation-turn-card-${turn.parcelId}"),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
                    ) {
                        Text(
                            text = turn.parcelName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (turn.isExtraordinary) {
                            Surface(
                                shape = MoShape.pill,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.testTag("extraordinary-badge"),
                            ) {
                                Text(
                                    text = "Extraordinario",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = MoSpacing.xxs, vertical = 1.dp),
                                )
                            }
                        }
                        if (turn.isCancelled) {
                            Surface(
                                shape = MoShape.pill,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.testTag("cancelled-turn-badge"),
                            ) {
                                Text(
                                    text = "Cancelado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = MoSpacing.xxs, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = turn.locationDescription ?: "Comunidad y sector sin especificar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
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

            // Horario y detalles de volumen / coste
            if (turn.timeSlot != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    ) {
                        Icon(MoIcons.Clock, contentDescription = null, tint = MoColors.current.primaryButton, modifier = Modifier.size(14.dp))
                        Text(
                            text = turn.timeSlot.formattedSlot,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("turn-time-slot"),
                        )
                        if (turn.timeSlot.isOvernight) {
                            Surface(
                                shape = MoShape.pill,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.testTag("overnight-badge"),
                            ) {
                                Text(
                                    text = "Nocturno",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MoSurfaceTokens.secondaryText,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    if (turn.estimatedVolumeM3 != null) {
                        Text(
                            text = "Est. ${turn.estimatedVolumeM3} m³ · ${formatEuros(turn.estimatedCostMinor ?: 0L)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MoColors.current.primaryButton,
                        )
                    }
                }
            }

            if (turn.punctualTurnId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { onToggleCancellation(turn.parcelId, turn.punctualTurnId) },
                        modifier = Modifier.testTag("toggle-punctual-turn-${turn.punctualTurnId}"),
                    ) {
                        Text(
                            text = if (turn.isCancelled) "Reactivar turno" else "Suspender turno",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IrrigationInstallationsTab(
    state: IrrigationUiState,
    onSelectParcel: (Parcel) -> Unit,
    onConfigureParcel: (Parcel) -> Unit,
    onUpdateTrees: (String) -> Unit,
    onUpdateEmitters: (String) -> Unit,
    onUpdateFlow: (String) -> Unit,
    onUpdateDuration: (String) -> Unit,
    onUpdateEmitterType: (EmitterType) -> Unit,
    onCalculate: () -> Unit,
    onLoadSimulation: () -> Unit = {},
    onClearSimulation: () -> Unit = {},
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
                onLoadSimulation = onLoadSimulation,
                onClearSimulation = onClearSimulation,
            )
        }

        // 2. Installations List
        item {
            MoSectionHeader("Parcelas de la explotación")
        }

        val parcelsToShow = state.irrigatedParcels.ifEmpty { state.allParcels }
        if (parcelsToShow.isEmpty()) {
            item {
                MoEmptyState(
                    title = "Sin parcelas registradas",
                    body = "Esta finca no tiene parcelas activas asociadas.",
                    icon = MoIcons.Parcels,
                    modifier = Modifier.testTag("installations-empty"),
                )
            }
        } else {
            items(parcelsToShow, key = { it.id }) { parcel ->
                val profile = state.profiles[parcel.id]
                ParcelInstallationCard(
                    parcel = parcel,
                    profile = profile,
                    isArchived = state.isFarmArchived,
                    onSimulate = { onSelectParcel(parcel) },
                    onConfigure = { onConfigureParcel(parcel) },
                )
            }
        }
    }
}

@Composable
private fun ParcelInstallationCard(
    parcel: Parcel,
    profile: ParcelIrrigationProfile?,
    isArchived: Boolean,
    onSimulate: () -> Unit,
    onConfigure: () -> Unit,
) {
    Card(
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoSurfaceTokens.cardSurface),
        border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("parcel-card-${parcel.id}"),
    ) {
        Column(
            modifier = Modifier.padding(MoSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parcel.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val supplyText = profile?.supply?.fullSupplyDescription
                        ?: listOfNotNull(
                            parcel.agronomy.irrigationNetwork,
                            parcel.agronomy.irrigationSector?.let { "Sector $it" },
                        ).joinToString(" · ").ifBlank { "Suministro no configurado" }

                    Text(
                        text = supplyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
                }

                Surface(
                    shape = MoShape.pill,
                    color = MoSurfaceTokens.cardSurface,
                    border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                ) {
                    val systemLabel = when (parcel.agronomy.irrigationSystem) {
                        IrrigationSystem.DRIP -> "Goteo"
                        IrrigationSystem.SPRINKLER -> "Aspersión"
                        IrrigationSystem.OTHER -> "Otro"
                        else -> "Secano"
                    }
                    Text(
                        text = systemLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MoColors.current.primaryButton,
                        modifier = Modifier.padding(horizontal = MoSpacing.xs, vertical = 2.dp),
                    )
                }
            }

            // Datos de aforo y tarifa si están configurados
            if (profile != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MoSpacing.md),
                ) {
                    Text(
                        text = "Aforo: ${profile.aforo.effectiveFlowRateM3PerHour} m³/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MoColors.current.primaryButton,
                    )
                    Text(
                        text = "Tarifa: ${profile.tariff.tariffType.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MoSurfaceTokens.secondaryText,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
            ) {
                if (!isArchived) {
                    OutlinedButton(
                        onClick = onConfigure,
                        shape = MoShape.pill,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("configure-parcel-irrigation-${parcel.id}"),
                    ) {
                        Text("Configurar riego", style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedButton(
                    onClick = onSimulate,
                    shape = MoShape.pill,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("simulate-dose-${parcel.id}"),
                ) {
                    Text("Simular dosis", style = MaterialTheme.typography.labelSmall)
                }
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
    onLoadSimulation: () -> Unit = {},
    onClearSimulation: () -> Unit = {},
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

            if (calc.isSimulationMode) {
                Surface(
                    shape = MoShape.card,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulation-mode-banner"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MoSpacing.sm, vertical = MoSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Modo simulación (valores de ejemplo)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        TextButton(
                            onClick = onClearSimulation,
                            modifier = Modifier.testTag("clear-simulation-button"),
                        ) {
                            Text("Limpiar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
            ) {
                if (!calc.isSimulationMode) {
                    MoSecondaryButton(
                        text = "Cargar ejemplo simulación",
                        onClick = onLoadSimulation,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("load-simulation-button"),
                    )
                }
                MoPrimaryButton(
                    text = "Calcular dotación",
                    onClick = onCalculate,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calculate-dose-button"),
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
                            text = if (calc.isSimulationMode) "Resultado de simulación (estimación teórica)" else "Resultado teórico calculado (no registrado)",
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
    isArchived: Boolean = false,
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
                actionText = if (isArchived) null else "Registrar riego",
                onAction = if (isArchived) null else onRegisterIrrigation,
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

// ==========================================
// B.5 FARMER IRRIGATION CONFIGURATION DIALOG
// ==========================================

@Composable
fun FarmerIrrigationProfileDialog(
    parcel: Parcel,
    draft: ParcelIrrigationProfile,
    onUpdateDraft: (ParcelIrrigationProfile) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var sourceIndex by remember { mutableStateOf(draft.supply.sourceType.ordinal) }
    var communityName by remember { mutableStateOf(draft.supply.communityName.orEmpty()) }
    var sector by remember { mutableStateOf(draft.supply.sector.orEmpty()) }
    var hydrant by remember { mutableStateOf(draft.supply.hydrant.orEmpty()) }
    var intake by remember { mutableStateOf(draft.supply.intakeOrValve.orEmpty()) }

    var treesText by remember { mutableStateOf(draft.aforo.oliveTreeCount.toString()) }
    var emittersText by remember { mutableStateOf(draft.aforo.emittersPerTree.toString()) }
    var flowText by remember { mutableStateOf(draft.aforo.emitterFlowLph.toString()) }
    var directFlowText by remember { mutableStateOf(draft.aforo.directFlowRate?.toString().orEmpty()) }
    var isDirectMode by remember { mutableStateOf(draft.aforo.measurementMode == FlowMeasurementMode.DIRECT_HYDRANT_FLOW) }

    var startHourText by remember { mutableStateOf("22") }
    var startMinuteText by remember { mutableStateOf("00") }
    var endHourText by remember { mutableStateOf("06") }
    var endMinuteText by remember { mutableStateOf("00") }

    var selectedDays by remember {
        mutableStateOf(
            if (draft.weeklySlots.isNotEmpty()) draft.weeklySlots.keys.toSet()
            else parcel.agronomy.irrigationDays
        )
    }

    var tariffMode by remember { mutableStateOf(draft.tariff.tariffType) }
    var pricePerUnitText by remember { mutableStateOf((draft.tariff.variablePricePerUnitMinor / 100.0).toString()) }
    var annualFeeText by remember { mutableStateOf((draft.tariff.annualCommunityQuotaMinor / 100.0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Configurar riego: ${parcel.displayName}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("farmer-profile-dialog-content"),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                // Section 1: Suministro
                Text("1. Suministrador y red de agua", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                    WaterSourceType.entries.forEachIndexed { index, type ->
                        FilterChip(
                            selected = sourceIndex == index,
                            onClick = { sourceIndex = index },
                            label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (sourceIndex == WaterSourceType.IRRIGATION_COMMUNITY.ordinal) {
                    MoTextField(
                        value = communityName,
                        onValueChange = { communityName = it },
                        label = "Nombre comunidad de regantes",
                        modifier = Modifier.fillMaxWidth().testTag("input-community-name"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                    MoTextField(value = sector, onValueChange = { sector = it }, label = "Sector", modifier = Modifier.weight(1f).testTag("input-sector"))
                    MoTextField(value = hydrant, onValueChange = { hydrant = it }, label = "Hidrante", modifier = Modifier.weight(1f).testTag("input-hydrant"))
                    MoTextField(value = intake, onValueChange = { intake = it }, label = "Toma / Boca", modifier = Modifier.weight(1f).testTag("input-intake"))
                }

                Spacer(Modifier.height(MoSpacing.xs))

                // Section 2: Instalación y Aforo
                Text("2. Instalación y aforo", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                    FilterChip(
                        selected = !isDirectMode,
                        onClick = { isDirectMode = false },
                        label = { Text("Por goteros", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = isDirectMode,
                        onClick = { isDirectMode = true },
                        label = { Text("Aforo directo hidrante", style = MaterialTheme.typography.labelSmall) },
                    )
                }
                if (!isDirectMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                        MoTextField(value = treesText, onValueChange = { treesText = it }, label = "Olivos", modifier = Modifier.weight(1f))
                        MoTextField(value = emittersText, onValueChange = { emittersText = it }, label = "Goteros/árbol", modifier = Modifier.weight(1f))
                        MoTextField(value = flowText, onValueChange = { flowText = it }, label = "Caudal (L/h)", modifier = Modifier.weight(1f))
                    }
                } else {
                    MoTextField(
                        value = directFlowText,
                        onValueChange = { directFlowText = it },
                        label = "Caudal instantáneo aforado (m³/h)",
                        modifier = Modifier.fillMaxWidth().testTag("input-direct-flow"),
                    )
                }

                Spacer(Modifier.height(MoSpacing.xs))

                // Section 3: Horarios y días fijos
                Text("3. Días y horario habitual de riego", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val isSelected = day in selectedDays
                        Surface(
                            shape = MoShape.pill,
                            color = if (isSelected) MoColors.current.primaryButton else MoSurfaceTokens.cardSurface,
                            border = BorderStroke(1.dp, MoSurfaceTokens.cardStroke),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedDays = if (isSelected) selectedDays - day else selectedDays + day
                                },
                        ) {
                            Text(
                                text = dayShortLabel(day),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MoColors.current.onPrimaryButton else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoTextField(value = startHourText, onValueChange = { startHourText = it }, label = "Hora inicio", modifier = Modifier.weight(1f))
                    Text(":")
                    MoTextField(value = startMinuteText, onValueChange = { startMinuteText = it }, label = "Min", modifier = Modifier.weight(1f))
                    Text("hasta")
                    MoTextField(value = endHourText, onValueChange = { endHourText = it }, label = "Hora fin", modifier = Modifier.weight(1f))
                    Text(":")
                    MoTextField(value = endMinuteText, onValueChange = { endMinuteText = it }, label = "Min", modifier = Modifier.weight(1f))
                }
                val sHour = startHourText.toIntOrNull() ?: 22
                val eHour = endHourText.toIntOrNull() ?: 6
                if (eHour <= sHour) {
                    Text(
                        text = "Turno nocturno: cruza la medianoche (+1 día)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MoColors.current.primaryButton,
                    )
                }

                Spacer(Modifier.height(MoSpacing.xs))

                // Section 4: Tarifas y cuotas
                Text("4. Tarifas de agua y cuotas fijas", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                    WaterTariffType.entries.forEach { type ->
                        FilterChip(
                            selected = tariffMode == type,
                            onClick = { tariffMode = type },
                            label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                if (tariffMode != WaterTariffType.FREE) {
                    MoTextField(
                        value = pricePerUnitText,
                        onValueChange = { pricePerUnitText = it },
                        label = if (tariffMode == WaterTariffType.PER_M3) "Precio por m³ (€)" else "Precio por hora (€)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MoTextField(
                    value = annualFeeText,
                    onValueChange = { annualFeeText = it },
                    label = "Cuota fija anual comunidad (€/año)",
                    modifier = Modifier.fillMaxWidth().testTag("input-annual-fee"),
                )
            }
        },
        confirmButton = {
            MoPrimaryButton(
                text = "Guardar configuración",
                onClick = {
                    val sH = (startHourText.toIntOrNull() ?: 22).coerceIn(0, 23)
                    val sM = (startMinuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                    val eH = (endHourText.toIntOrNull() ?: 6).coerceIn(0, 23)
                    val eM = (endMinuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                    val timeSlot = ScheduledTimeSlot(LocalTime.of(sH, sM), LocalTime.of(eH, eM))

                    val weeklyMap = selectedDays.associateWith { timeSlot }

                    val updatedSupply = WaterSupplyDetails(
                        sourceType = WaterSourceType.entries[sourceIndex],
                        communityName = communityName.takeIf(String::isNotBlank),
                        sector = sector.takeIf(String::isNotBlank),
                        hydrant = hydrant.takeIf(String::isNotBlank),
                        intakeOrValve = intake.takeIf(String::isNotBlank),
                    )

                    val updatedAforo = AforoConfig(
                        measurementMode = if (isDirectMode) FlowMeasurementMode.DIRECT_HYDRANT_FLOW else FlowMeasurementMode.CALCULATED_FROM_EMITTERS,
                        oliveTreeCount = treesText.toIntOrNull() ?: 0,
                        emittersPerTree = emittersText.toIntOrNull() ?: 0,
                        emitterFlowLph = flowText.toDoubleOrNull() ?: 0.0,
                        directFlowRate = directFlowText.toDoubleOrNull(),
                    )

                    val variablePriceMinor = ((pricePerUnitText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    val annualFeeMinor = ((annualFeeText.toDoubleOrNull() ?: 0.0) * 100).toLong()

                    val updatedTariff = WaterTariff(
                        tariffType = tariffMode,
                        variablePricePerUnitMinor = variablePriceMinor,
                        annualCommunityQuotaMinor = annualFeeMinor,
                    )

                    val profile = draft.copy(
                        supply = updatedSupply,
                        aforo = updatedAforo,
                        weeklySlots = weeklyMap,
                        tariff = updatedTariff,
                    )
                    onUpdateDraft(profile)
                    onSave()
                },
                modifier = Modifier.testTag("save-irrigation-profile-button"),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel-irrigation-profile-button")) {
                Text("Cancelar")
            }
        },
    )
}

// ==========================================
// B.5 PUNCTUAL TURN CREATION DIALOG
// ==========================================

@Composable
fun PunctualTurnDialog(
    parcels: List<Parcel>,
    initialParcelId: UUID?,
    onConfirm: (PunctualTurn) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedParcelId by remember { mutableStateOf(initialParcelId ?: parcels.firstOrNull()?.id) }
    var reasonText by remember { mutableStateOf("Turno extraordinario") }
    var startHourText by remember { mutableStateOf("20") }
    var startMinuteText by remember { mutableStateOf("00") }
    var endHourText by remember { mutableStateOf("02") }
    var endMinuteText by remember { mutableStateOf("00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Añadir turno extraordinario / puntual", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("punctual-turn-dialog-content"),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Text("Selecciona la parcela destinataria:", style = MaterialTheme.typography.labelMedium)
                parcels.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedParcelId = p.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (p.id == selectedParcelId) MoColors.current.primaryButton else MoSurfaceTokens.cardStroke),
                        )
                        Text(text = p.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                }

                MoTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = "Motivo del turno",
                    modifier = Modifier.fillMaxWidth().testTag("input-punctual-reason"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoTextField(value = startHourText, onValueChange = { startHourText = it }, label = "Inicio (H)", modifier = Modifier.weight(1f))
                    Text(":")
                    MoTextField(value = startMinuteText, onValueChange = { startMinuteText = it }, label = "Min", modifier = Modifier.weight(1f))
                    Text("hasta")
                    MoTextField(value = endHourText, onValueChange = { endHourText = it }, label = "Fin (H)", modifier = Modifier.weight(1f))
                    Text(":")
                    MoTextField(value = endMinuteText, onValueChange = { endMinuteText = it }, label = "Min", modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            MoPrimaryButton(
                text = "Añadir turno",
                onClick = {
                    val pId = selectedParcelId ?: return@MoPrimaryButton
                    val sH = (startHourText.toIntOrNull() ?: 20).coerceIn(0, 23)
                    val sM = (startMinuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                    val eH = (endHourText.toIntOrNull() ?: 2).coerceIn(0, 23)
                    val eM = (endMinuteText.toIntOrNull() ?: 0).coerceIn(0, 59)

                    val turn = PunctualTurn(
                        parcelId = pId,
                        date = LocalDate.now(),
                        timeSlot = ScheduledTimeSlot(LocalTime.of(sH, sM), LocalTime.of(eH, eM)),
                        reason = reasonText.ifBlank { "Turno extraordinario" },
                    )
                    onConfirm(turn)
                },
                modifier = Modifier.testTag("confirm-punctual-turn-button"),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel-punctual-turn-button")) {
                Text("Cancelar")
            }
        },
    )
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
