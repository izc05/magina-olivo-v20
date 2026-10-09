package com.isivoltpro.maginaolivo.feature.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.ActiveFarmStore
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.feature.activities.RegisterActivityViewModel
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

private data class QuickContext(val farmId: UUID?, val parcel: Parcel?)
private data class QuickCampaigns(val farmId: UUID, val values: List<Campaign>? = null, val failed: Boolean = false)

/** CR-014: read-only context resolution; only choosing an action opens a writer. */
@Composable
fun GlobalQuickActions(
    persistence: LocalPersistence,
    activeFarmStore: ActiveFarmStore,
    contextualFarmId: UUID?,
    contextualParcelId: UUID?,
    contextualCampaignId: UUID?,
    onGoToFields: () -> Unit,
    onQuickAction: (NotebookQuickAction, UUID, Boolean, UUID?) -> Unit,
) {
    val farmsViewModel: RegisterActivityViewModel = viewModel(key = "global-quick-farms", factory = viewModelFactory {
        initializer { RegisterActivityViewModel(persistence.farmRepository, persistence.workspaceRepository) }
    })
    val farms by farmsViewModel.state.collectAsStateWithLifecycle()
    val hasContext = contextualFarmId != null || contextualParcelId != null || contextualCampaignId != null
    val context by produceState<QuickContext?>(if (hasContext) null else QuickContext(null, null),
        contextualFarmId, contextualParcelId, contextualCampaignId) {
        if (!hasContext) return@produceState
        try {
            val campaignFarm = contextualCampaignId?.let { persistence.campaignRepository.observe(it).first()?.farmId }
            if (contextualParcelId == null) {
                value = QuickContext(contextualFarmId ?: campaignFarm, null)
            } else {
                // Keep archive/membership changes visible while the sheet is open.
                persistence.parcelRepository.observeById(contextualParcelId).collect { parcel ->
                    value = QuickContext(contextualFarmId ?: parcel?.farmId ?: campaignFarm, parcel)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // An unavailable context must ask for a Farm, never borrow the stored Farm.
            value = QuickContext(null, null)
        }
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var parcelCleared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(farms.isLoading, farms.error, context, farms.farms) {
        if (initialized || farms.isLoading || farms.error != null || context == null) return@LaunchedEffect
        val requested = if (hasContext) context?.farmId else activeFarmStore.get()
        selectedId = requested?.takeIf { id -> farms.farms.any { it.id == id } }?.toString()
        initialized = true
    }
    // The list is scoped by the current Workspace; removal/archive immediately blocks actions.
    val farm = farms.farms.firstOrNull { it.id.toString() == selectedId && it.archivedAt == null }
    val parcel = context?.parcel?.takeIf {
        !parcelCleared && farm != null && it.farmId == farm.id && it.workspaceId == farm.workspaceId && it.archivedAt == null
    }
    var retry by rememberSaveable { mutableStateOf(0) }
    val observed by produceState<QuickCampaigns?>(null, farm?.id, retry) {
        val id = farm?.id ?: return@produceState
        value = QuickCampaigns(id)
        persistence.campaignRepository.observeForFarm(id).catch { value = QuickCampaigns(id, failed = true) }
            .collect { value = QuickCampaigns(id, values = it) }
    }
    // Never reuse the preceding Farm's state during a new flow's first frame.
    val campaigns = observed?.takeIf { it.farmId == farm?.id }
    val campaignKnown = campaigns?.values != null && campaigns.failed == false
    val running = campaigns?.values.orEmpty().filter {
        it.farmId == farm?.id && it.workspaceId == farm.workspaceId && it.status.isRunning
    }.maxByOrNull { it.startDate }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen)
        .testTag("quick-register-sheet"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        Text("Añadir registro", style = MaterialTheme.typography.headlineSmall)
        when {
            farms.error != null -> MoErrorState("No pudimos abrir tus fincas", farms.error!!, onRetry = farmsViewModel::retry)
            farms.isLoading || context == null || !initialized -> CircularProgressIndicator()
            farms.farms.isEmpty() -> {
                Text("Crea una finca para añadir tus registros agrícolas.")
                MoSecondaryButton("Crear finca en Mi Campo", onGoToFields)
            }
            farm == null || choosing -> {
                Text("Elige la finca de este registro", style = MaterialTheme.typography.titleMedium)
                farms.farms.forEach { option ->
                    MoSecondaryButton(option.name, {
                        selectedId = option.id.toString()
                        activeFarmStore.set(option.id)
                        parcelCleared = true
                        choosing = false
                    }, Modifier.fillMaxWidth().testTag("quick-register-farm-${option.id}"))
                }
            }
            else -> {
                Text(farm.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("quick-register-farm"))
                Text(when {
                    !campaignKnown -> "Consultando la campaña…"
                    running != null -> "Campaña ${running.name} · En marcha"
                    else -> "Fuera de campaña"
                }, color = MoSurfaceTokens.secondaryText)
                parcel?.let { Text("Parcela: ${it.displayName}", color = MoSurfaceTokens.secondaryText) }
                if (farms.farms.size > 1) MoTertiaryButton("Cambiar finca", { choosing = true })
                if (campaigns?.failed == true) MoErrorState("No pudimos consultar la campaña", "Reintenta para elegir Jornal o Pesada.", onRetry = { retry++ })
                QuickActionGrid(
                    onQuickAction = { action ->
                        if (action !in listOf(NotebookQuickAction.LABOUR, NotebookQuickAction.WEIGHING) || campaignKnown) {
                            onQuickAction(action, farm.id, running != null, parcel?.id)
                        }
                    },
                    isEnabled = { it !in listOf(NotebookQuickAction.LABOUR, NotebookQuickAction.WEIGHING) || campaignKnown },
                )
            }
        }
    }
}
