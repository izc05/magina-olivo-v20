package com.isivoltpro.maginaolivo.feature.farms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal data class FarmSetupState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val farm: Farm? = null,
    val parcels: List<Parcel> = emptyList(),
    val error: String? = null,
    val workspaceId: UUID? = null,
    val ownerId: UUID? = null,
)

internal class FarmSetupViewModel(
    private val persistence: LocalPersistence,
    private val checkpoint: FarmSetupCheckpointStore,
    private val requestedFarmId: UUID?,
) : ViewModel() {
    private val mutable = MutableStateFlow(FarmSetupState())
    val state: StateFlow<FarmSetupState> = mutable.asStateFlow()
    private var farmObservation: Job? = null

    init {
        viewModelScope.launch {
            persistence.workspaceRepository.observeActiveWorkspace().collectLatest { result ->
                farmObservation?.cancel()
                val workspace = (result as? AppResult.Success)?.value
                val owner = persistence.workspaceRepository.currentOwnerId()
                if (workspace == null) {
                    mutable.value = FarmSetupState(loading = false, error = "No hay un workspace activo.")
                    return@collectLatest
                }
                mutable.value = FarmSetupState(workspaceId = workspace, ownerId = owner)
                val id = requestedFarmId ?: checkpoint.load(workspace, owner)
                if (id == null) mutable.value = mutable.value.copy(loading = false)
                else observeFarm(id, workspace, owner)
            }
        }
    }

    private fun observeFarm(id: UUID, workspace: UUID, owner: UUID?) {
        farmObservation?.cancel()
        farmObservation = viewModelScope.launch {
            persistence.farmRepository.observeById(id).collectLatest { farm ->
                if (farm == null || farm.workspaceId != workspace ||
                    persistence.workspaceRepository.currentOwnerId() != owner
                ) {
                    mutable.value = mutable.value.copy(loading = false, farm = null, parcels = emptyList(),
                        error = if (requestedFarmId != null) "Esta finca no está disponible en la cuenta y workspace activos." else null)
                    return@collectLatest
                }
                mutable.value = mutable.value.copy(loading = false, farm = farm, error = null)
                persistence.parcelRepository.observeActive(id).collect { parcels ->
                    if (mutable.value.workspaceId == workspace && mutable.value.ownerId == owner)
                        mutable.value = mutable.value.copy(parcels = parcels)
                }
            }
        }
    }

    fun create(name: String) {
        val current = mutable.value
        val workspace = current.workspaceId ?: return
        if (current.saving || current.farm != null) return
        if (name.isBlank()) { mutable.value = current.copy(error = "Escribe el nombre de la finca."); return }
        viewModelScope.launch {
            mutable.value = mutable.value.copy(saving = true, error = null)
            val id = checkpoint.reserve(workspace, current.ownerId)
            if (id == null) {
                mutable.value = mutable.value.copy(saving = false, error = "No se pudo guardar el progreso en este dispositivo.")
                return@launch
            }
            val result = persistence.farmRepository.create(NewFarm(workspace, name.trim(), requestedId = id))
            if (result is AppResult.Success && result.value == id) {
                mutable.value = mutable.value.copy(saving = false)
                observeFarm(id, workspace, current.ownerId)
            } else mutable.value = mutable.value.copy(saving = false,
                error = "No se pudo crear la finca. Puedes reintentar sin generar otra.")
        }
    }

    fun finish(onDone: (UUID) -> Unit) {
        val current = mutable.value
        val farm = current.farm ?: return
        val workspace = current.workspaceId ?: return
        if (persistence.workspaceRepository.currentOwnerId() != current.ownerId || farm.workspaceId != workspace) return
        if (checkpoint.load(workspace, current.ownerId) == farm.id && !checkpoint.clear(workspace, current.ownerId)) {
            mutable.value = current.copy(error = "No se pudo cerrar el asistente. Tus datos siguen guardados.")
            return
        }
        onDone(farm.id)
    }
}

@Composable
fun FarmSetupRoute(
    persistence: LocalPersistence,
    farmId: UUID?,
    onMap: (UUID) -> Unit,
    onSearch: (UUID) -> Unit,
    onPolygonSearch: (UUID) -> Unit,
    onGps: (UUID) -> Unit,
    onEditParcel: (UUID) -> Unit,
    onLater: (UUID) -> Unit,
) {
    val context = LocalContext.current
    val model: FarmSetupViewModel = viewModel(key = "farm-setup-${farmId ?: "new"}", factory = viewModelFactory {
        initializer { FarmSetupViewModel(persistence, FarmSetupCheckpointStore(context), farmId) }
    })
    val state by model.state.collectAsStateWithLifecycle()
    FarmSetupScreen(state, model::create, onMap, onSearch, onPolygonSearch, onGps, onEditParcel,
        onLater = { state.farm?.id?.let(onLater) }, onFinish = { model.finish(onLater) })
}

@Composable
internal fun FarmSetupScreen(
    state: FarmSetupState,
    onCreate: (String) -> Unit,
    onMap: (UUID) -> Unit,
    onSearch: (UUID) -> Unit,
    onPolygonSearch: (UUID) -> Unit,
    onGps: (UUID) -> Unit,
    onEditParcel: (UUID) -> Unit,
    onLater: () -> Unit,
    onFinish: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var searchOptions by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MoSpacing.screen)
        .testTag("farm-setup-root"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        Text("Crear finca con ayuda", style = MaterialTheme.typography.headlineSmall)
        when {
            state.loading -> CircularProgressIndicator()
            state.farm == null -> {
                Text("Solo necesitas un nombre. Podrás añadir parcelas y completar los datos después.")
                MoTextField(name, { name = it }, "Nombre de la finca", Modifier.fillMaxWidth().testTag("farm-setup-name"))
                MoPrimaryButton(if (state.saving) "Guardando…" else "Crear finca", { onCreate(name) },
                    Modifier.fillMaxWidth().testTag("farm-setup-create"), enabled = !state.saving && name.isNotBlank() && state.error?.contains("no está disponible") != true)
            }
            else -> {
                val farm = state.farm
                Text(farm.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("farm-setup-summary"))
                Text("${state.parcels.size} parcelas guardadas en esta finca")
                MoPrimaryButton("Seleccionar en el mapa", { onMap(farm.id) }, Modifier.fillMaxWidth().testTag("farm-setup-map"))
                MoSecondaryButton("Buscar parcela", { searchOptions = !searchOptions }, Modifier.fillMaxWidth().testTag("farm-setup-search"))
                if (searchOptions) {
                    MoSecondaryButton("Referencia catastral", { onSearch(farm.id) }, Modifier.fillMaxWidth().testTag("farm-setup-reference"))
                    MoSecondaryButton("Municipio, polígono y parcela", { onPolygonSearch(farm.id) },
                        Modifier.fillMaxWidth().testTag("farm-setup-polygon"))
                }
                MoSecondaryButton("Estoy en el terreno · GPS", { onGps(farm.id) }, Modifier.fillMaxWidth().testTag("farm-setup-gps"))
                state.parcels.forEach { parcel ->
                    val pending = parcel.agronomy.oliveTreeCount == null || parcel.agronomy.variety == null ||
                        parcel.agronomy.irrigationSystem == null || parcel.managedAreaM2 == null
                    MoSecondaryButton("${parcel.displayName}${if (pending) " · Completar datos" else " · Ver datos"}",
                        { onEditParcel(parcel.id) }, Modifier.fillMaxWidth().testTag("farm-setup-parcel-${parcel.id}"))
                }
                MoTertiaryButton("Completar más tarde", onLater, Modifier.fillMaxWidth().testTag("farm-setup-later"))
                MoTertiaryButton("Finalizar asistente", onFinish, Modifier.fillMaxWidth().testTag("farm-setup-finish"))
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("farm-setup-error")) }
    }
}
