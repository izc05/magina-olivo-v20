package com.isivoltpro.maginaolivo.feature.campaigns

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignParcelOption
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.time.LocalDate
import java.util.UUID

@Composable
fun FarmCampaignsRoute(farmId: UUID, persistence: LocalPersistence, onCampaignSelected: (UUID) -> Unit) {
    val vm: FarmCampaignsViewModel = viewModel(key = "farm-campaigns-$farmId", factory = viewModelFactory {
        initializer { FarmCampaignsViewModel(farmId, persistence.campaignRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    FarmCampaignsSection(state, onCampaignSelected, vm::create)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmCampaignsSection(state: FarmCampaignsUiState, onCampaignSelected: (UUID) -> Unit, onCreate: (CampaignDraft) -> Unit) {
    var editor by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) { if (state.message != null) editor = false }
    MoSectionHeader("Campañas", action = { TextButton(onClick = { editor = true }, modifier = Modifier.testTag("add-campaign")) { Text("Añadir") } })
    when {
        state.isLoading -> CircularProgressIndicator()
        state.error != null -> MoErrorState("No pudimos abrir las campañas", state.error)
        state.current.isEmpty() && state.history.isEmpty() -> MoEmptyState("Aún no hay campañas", "Crea una campaña y selecciona las parcelas que participan.")
        else -> {
            state.current.forEach { CampaignRow(it, onCampaignSelected) }
            if (state.history.isNotEmpty()) {
                MoSectionHeader("Histórico")
                state.history.forEach { CampaignRow(it, onCampaignSelected) }
            }
        }
    }
    if (editor) ModalBottomSheet(onDismissRequest = { editor = false }) {
        CampaignEditor(state.parcels, state.nameError, state.dateError, state.isSaving, onCreate) { editor = false }
    }
}

@Composable
private fun CampaignRow(campaign: Campaign, onSelected: (UUID) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelected(campaign.id) }.testTag("campaign-row"),
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, MoOutline),
    ) { Row(Modifier.fillMaxWidth().padding(MoSpacing.md), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text(campaign.name, style = MaterialTheme.typography.titleMedium); Text(campaign.startDate.toString(), color = MoTextSecondary) }
        MoStatusChip(campaign.status.label(), tone = if (campaign.status == CampaignStatus.CLOSED) MoStatusTone.Neutral else MoStatusTone.Success)
    } }
}

@Composable
private fun CampaignEditor(
    parcels: List<CampaignParcelOption>, nameError: String?, dateError: String?, isSaving: Boolean,
    onSave: (CampaignDraft) -> Unit, onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.md)) {
        Text("Nueva campaña", style = MaterialTheme.typography.headlineSmall)
        MoTextField(name, { name = it }, "Nombre", isError = nameError != null, supportingText = nameError, modifier = Modifier.testTag("campaign-name"))
        MoTextField(date, { date = it }, "Fecha de inicio (AAAA-MM-DD)", isError = dateError != null, supportingText = dateError, modifier = Modifier.testTag("campaign-start-date"))
        MoSectionHeader("Parcelas")
        if (parcels.isEmpty()) Text("Primero añade una parcela a esta finca.", color = MoTextSecondary)
        parcels.forEach { parcel ->
            val checked = parcel.id.toString() in selected
            Row(Modifier.fillMaxWidth().clickable { selected = if (checked) selected - parcel.id.toString() else selected + parcel.id.toString() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, { value -> selected = if (value) selected + parcel.id.toString() else selected - parcel.id.toString() })
                Text(parcel.name)
            }
        }
        MoTextField(notes, { notes = it }, "Notas")
        MoPrimaryButton("Guardar campaña", { onSave(CampaignDraft(name, runCatching { LocalDate.parse(date) }.getOrNull(), selected.map(UUID::fromString).toSet(), notes)) }, modifier = Modifier.fillMaxWidth().testTag("save-campaign"), enabled = !isSaving)
        MoSecondaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun CampaignDetailRoute(campaignId: UUID, persistence: LocalPersistence) {
    val vm: CampaignDetailViewModel = viewModel(key = "campaign-$campaignId", factory = viewModelFactory {
        initializer { CampaignDetailViewModel(campaignId, persistence.campaignRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    CampaignDetailScreen(state, vm::activate, vm::markHarvest, vm::close, vm::reopen, vm::archivePreparation)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignDetailScreen(
    state: CampaignDetailUiState, onActivate: () -> Unit, onHarvest: () -> Unit,
    onClose: (LocalDate) -> Unit, onReopen: () -> Unit, onArchive: () -> Unit,
) {
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(Modifier.fillMaxSize().testTag("campaign-detail-root"), containerColor = MoCream) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState()).padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.md)) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.campaign == null -> MoErrorState("Campaña no disponible", state.error ?: "No está guardada en este dispositivo.")
                else -> {
                    val campaign = state.campaign
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(campaign.name, style = MaterialTheme.typography.headlineLarge)
                        MoStatusChip(campaign.status.label(), tone = if (campaign.status == CampaignStatus.CLOSED) MoStatusTone.Neutral else MoStatusTone.Success)
                    }
                    Text("Inicio: ${campaign.startDate}", color = MoTextSecondary)
                    MoSectionHeader("Parcelas al activar")
                    campaign.snapshots.forEach { snapshot ->
                        Column(Modifier.fillMaxWidth().testTag("campaign-snapshot")) {
                            Text(snapshot.parcelName, style = MaterialTheme.typography.titleMedium)
                            Text(snapshot.farmName, color = MoTextSecondary)
                            snapshot.cadastralReference?.let { Text("Ref. catastral: $it", color = MoTextSecondary) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                        MoMetricCard("Kg recogidos", "Sin datos", Modifier.weight(1f))
                        MoMetricCard("Rendimiento", "Sin datos", Modifier.weight(1f))
                    }
                    MoMetricCard("Gastos", "Sin datos", Modifier.fillMaxWidth())
                    when (campaign.status) {
                        CampaignStatus.PREPARATION -> { MoPrimaryButton("Activar campaña", { confirmation = "activate" }, modifier = Modifier.fillMaxWidth().testTag("activate-campaign"), enabled = !state.isSaving); MoSecondaryButton("Archivar borrador", { confirmation = "archive" }, modifier = Modifier.fillMaxWidth()) }
                        CampaignStatus.ACTIVE -> MoPrimaryButton("Iniciar recolección", { confirmation = "harvest" }, modifier = Modifier.fillMaxWidth(), enabled = !state.isSaving)
                        CampaignStatus.HARVEST -> MoPrimaryButton("Cerrar campaña", { confirmation = "close" }, modifier = Modifier.fillMaxWidth().testTag("close-campaign"), enabled = !state.isSaving)
                        CampaignStatus.CLOSED -> { Text("Histórico protegido", style = MaterialTheme.typography.titleMedium); MoSecondaryButton("Reabrir campaña", { confirmation = "reopen" }, modifier = Modifier.fillMaxWidth().testTag("reopen-campaign")) }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirmation != null) ModalBottomSheet(onDismissRequest = { confirmation = null }) {
        Column(Modifier.fillMaxWidth().padding(MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.md)) {
            Text("Confirmar cambio", style = MaterialTheme.typography.headlineSmall)
            Text("Esta acción actualizará el estado de la campaña guardada en este dispositivo.", color = MoTextSecondary)
            MoPrimaryButton("Confirmar", {
                when (confirmation) { "activate" -> onActivate(); "harvest" -> onHarvest(); "close" -> onClose(LocalDate.now()); "reopen" -> onReopen(); "archive" -> onArchive() }
                confirmation = null
            }, modifier = Modifier.fillMaxWidth().testTag("confirm-campaign-action"))
            MoSecondaryButton("Cancelar", { confirmation = null }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun CampaignStatus.label() = when (this) {
    CampaignStatus.PREPARATION -> "Preparación"; CampaignStatus.ACTIVE -> "Activa"; CampaignStatus.HARVEST -> "Recolección"; CampaignStatus.CLOSED -> "Cerrada"
}
