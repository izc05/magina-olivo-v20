package com.isivoltpro.maginaolivo.feature.profile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.feed.FeedLocation
import com.isivoltpro.maginaolivo.domain.feed.IneMunicipality
import com.isivoltpro.maginaolivo.domain.weather.WeatherLocationPreferenceStore
import com.isivoltpro.maginaolivo.domain.weather.WeatherPreferenceIdentity
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.feature.home.WeatherLocationSelectorContent
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Selector 1.0 — Perfil → «Ubicación del tiempo» (personal, local to this phone). */
data class WeatherLocationSettingsState(
    val preference: FeedLocation? = null,
    val farmSuggestion: FeedLocation? = null,
    /** Signed out of a configured account: nothing is shown nor saved until signing in again. */
    val locked: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherLocationSettingsViewModel(
    private val workspaces: WorkspaceRepository,
    farms: FarmRepository,
    private val store: WeatherLocationPreferenceStore,
    identity: Flow<WeatherPreferenceIdentity>,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WeatherLocationSettingsState())
    val state: StateFlow<WeatherLocationSettingsState> = mutableState.asStateFlow()
    private val currentIdentity = MutableStateFlow<WeatherPreferenceIdentity>(WeatherPreferenceIdentity.Local)

    init {
        viewModelScope.launch {
            val workspace = flow { emit((workspaces.ensureLocalWorkspace() as? AppResult.Success)?.value) }
            combine(identity.onEach { currentIdentity.value = it }, workspace) { id, ws -> id to ws }
                .flatMapLatest { (id, ws) ->
                    if (ws == null) {
                        flowOf(WeatherLocationSettingsState(locked = id == WeatherPreferenceIdentity.SignedOut))
                    } else {
                        combine(
                            store.observe(id, ws),
                            farms.observeActive(ws).catch { emit(emptyList()) },
                        ) { pref, list ->
                            WeatherLocationSettingsState(
                                preference = pref,
                                farmSuggestion = FeedLocation.common(list.map { it.municipality to it.province })?.withValidatedIneCode(),
                                locked = id == WeatherPreferenceIdentity.SignedOut,
                            )
                        }
                    }
                }
                .collect { mutableState.value = it }
        }
    }

    fun select(municipality: IneMunicipality) = launchWithWorkspace { ws -> store.set(currentIdentity.value, ws, municipality) }

    fun clear() = launchWithWorkspace { ws -> store.clear(currentIdentity.value, ws) }

    private fun launchWithWorkspace(block: suspend (java.util.UUID) -> Unit) {
        viewModelScope.launch {
            val ws = (workspaces.ensureLocalWorkspace() as? AppResult.Success)?.value ?: return@launch
            block(ws)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherLocationSettingsRow(
    state: WeatherLocationSettingsState,
    onSelect: (IneMunicipality) -> Unit,
    onClear: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    MoCompactListItem(
        title = "Ubicación del tiempo",
        subtitle = when {
            state.locked -> "Inicia sesión para ver o elegir tu ubicación del tiempo"
            state.preference != null -> "${state.preference.municipality} · ${state.preference.province.orEmpty()} · Solo en este teléfono"
            state.farmSuggestion != null -> "Se usa el municipio de tus fincas: ${state.farmSuggestion.label}"
            else -> "Sin elegir · Inicio y El tiempo la usan para la previsión"
        },
        icon = MoIcons.Weather,
        onClick = if (state.locked) null else ({ open = true }),
        modifier = Modifier.testTag("profile-weather-location"),
    )
    if (open && !state.locked) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            WeatherLocationSelectorContent(
                currentLocation = state.preference,
                farmSuggestion = state.farmSuggestion,
                sheetTestTag = "profile-weather-location-sheet",
                onSelectMunicipality = { onSelect(it); open = false },
                onClearLocation = { onClear(); open = false },
                onCancel = { open = false },
            )
        }
    }
}

