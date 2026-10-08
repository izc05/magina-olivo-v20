package com.isivoltpro.maginaolivo.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.domain.agenda.ReminderPreferences
import com.isivoltpro.maginaolivo.domain.agenda.ReminderReconciler
import com.isivoltpro.maginaolivo.domain.organization.OrganizationDraft
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRepository
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import com.isivoltpro.maginaolivo.domain.profile.PREFERRED_COOPERATIVE_ROLES
import com.isivoltpro.maginaolivo.domain.profile.ProfileDraft
import com.isivoltpro.maginaolivo.domain.profile.ProfileRepository
import com.isivoltpro.maginaolivo.domain.profile.ProfileSettings
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Phase 21A — «Mi perfil»: what the screen shows and whether a save is in flight. */
data class MyProfileUiState(
    val isLoading: Boolean = true,
    val settings: ProfileSettings = ProfileSettings(),
    val cooperatives: List<Organization> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    /** Bumped on each successful save, so an open sheet closes. */
    val saved: Int = 0,
)

class MyProfileViewModel(
    private val profile: ProfileRepository,
    private val organizations: OrganizationRepository,
    /** Phase 21B: rebuilds the alarms after Perfil → Avisos changes. Null where none exist. */
    private val reminders: ReminderReconciler? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MyProfileUiState())
    val state: StateFlow<MyProfileUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(profile.observe(), organizations.observeWithAnyRole(PREFERRED_COOPERATIVE_ROLES)) { settings, cooperatives ->
                settings to cooperatives
            }
                .catch { mutableState.update { it.copy(isLoading = false, error = "No se pudo leer tu perfil.") } }
                .collect { (settings, cooperatives) ->
                    mutableState.update { it.copy(isLoading = false, settings = settings, cooperatives = cooperatives) }
                }
        }
    }

    fun saveLocation(municipality: String, province: String) {
        val current = mutableState.value.settings
        save(ProfileDraft(municipality, province, current.preferredCooperative?.id))
    }

    fun chooseCooperative(id: UUID?) {
        val current = mutableState.value.settings
        save(ProfileDraft(current.municipality, current.province, id))
    }

    /** Creates the cooperative once (never a copy of an existing one) and makes it the farmer's. */
    fun createCooperative(name: String) {
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, error = null) }
            when (val created = organizations.create(OrganizationDraft(name, setOf(OrganizationRole.COOPERATIVE)))) {
                is AppResult.Success -> {
                    val current = mutableState.value.settings
                    finish(profile.save(ProfileDraft(current.municipality, current.province, created.value)))
                }
                is AppResult.Failure -> finish(created)
            }
        }
    }

    /** Phase 21B: stored first, then every alarm is rebuilt with the new preferences. */
    fun saveReminders(preferences: ReminderPreferences) {
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, error = null) }
            val result = profile.saveReminders(preferences)
            if (result is AppResult.Success) runCatching { reminders?.reconcile() }
            finish(result)
        }
    }

    fun clearError() = mutableState.update { it.copy(error = null) }

    private fun save(draft: ProfileDraft) {
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, error = null) }
            finish(profile.save(draft))
        }
    }

    private fun finish(result: AppResult<*>) {
        mutableState.update {
            when (result) {
                is AppResult.Success -> it.copy(isSaving = false, saved = it.saved + 1)
                is AppResult.Failure -> it.copy(isSaving = false, error = profileErrorMessage(result.error))
            }
        }
    }
}

internal fun profileErrorMessage(error: AppError): String = when {
    error == AppError.Conflict("duplicate_organization") -> "Ya tienes una organización con ese nombre: elígela en la lista."
    error is AppError.Validation && error.field == "name" -> "Escribe el nombre de la cooperativa."
    error is AppError.Validation && error.field == "municipality" -> "Escribe el municipio; la provincia sola no basta."
    error is AppError.NotFound || error == AppError.Validation("organization", "not_cooperative") ->
        "Esa cooperativa ya no está disponible. Elige otra."
    else -> "No se pudo guardar. Inténtalo de nuevo."
}

/** «Mi perfil» in Perfil: two rows, each opening a short sheet. Everything is saved on the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileSection(
    state: MyProfileUiState,
    onSaveLocation: (String, String) -> Unit,
    onChooseCooperative: (UUID?) -> Unit,
    onCreateCooperative: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    // A new save closes the sheet; a rotation with the sheet open does not.
    var seenSaves by rememberSaveable { mutableIntStateOf(state.saved) }
    LaunchedEffect(state.saved) {
        if (state.saved > seenSaves) sheet = null
        seenSaves = state.saved
    }
    val settings = state.settings
    MoSectionHeader("Mi perfil")
    MoCompactListItem(
        title = "Tu municipio",
        subtitle = settings.location?.let { listOfNotNull(it.municipality, it.province).joinToString(" · ") }
            ?: "Sin indicar · Inicio lo usa para el tiempo si tus fincas no tienen ubicación",
        icon = MoIcons.Location,
        onClick = { onClearError(); sheet = SHEET_LOCATION },
        modifier = Modifier.testTag("profile-municipality"),
    )
    MoCompactListItem(
        title = "Tu cooperativa",
        subtitle = settings.preferredCooperative?.name ?: "Sin elegir · la cooperativa o almazara donde entregas",
        icon = MoIcons.People,
        onClick = { onClearError(); sheet = SHEET_COOPERATIVE },
        modifier = Modifier.testTag("profile-cooperative"),
    )
    when (sheet) {
        SHEET_LOCATION -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            LocationSheet(settings, state.isSaving, state.error, onSave = onSaveLocation, onCancel = { sheet = null })
        }
        SHEET_COOPERATIVE -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            CooperativeSheet(
                current = settings.preferredCooperative?.id,
                cooperatives = state.cooperatives,
                isSaving = state.isSaving,
                error = state.error,
                onChoose = onChooseCooperative,
                onCreate = onCreateCooperative,
                onCancel = { sheet = null },
            )
        }
    }
}

@Composable
private fun LocationSheet(
    settings: ProfileSettings,
    isSaving: Boolean,
    error: String?,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var municipality by rememberSaveable { mutableStateOf(settings.municipality.orEmpty()) }
    var province by rememberSaveable { mutableStateOf(settings.province.orEmpty()) }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(MoSpacing.screen)
            .testTag("profile-location-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Tu municipio", style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
        Text(
            "Se usa para el tiempo de Inicio cuando tus fincas no tienen municipio o están en varios. Déjalo vacío para quitarlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
        )
        MoTextField(municipality, { municipality = it }, "Municipio", Modifier.fillMaxWidth().testTag("profile-municipality-field"))
        MoTextField(province, { province = it }, "Provincia (opcional)", Modifier.fillMaxWidth().testTag("profile-province-field"))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile-error")) }
        MoPrimaryButton(
            "Guardar",
            { onSave(municipality, province) },
            Modifier.fillMaxWidth().testTag("profile-save-location"),
            enabled = !isSaving,
        )
        MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CooperativeSheet(
    current: UUID?,
    cooperatives: List<Organization>,
    isSaving: Boolean,
    error: String?,
    onChoose: (UUID?) -> Unit,
    onCreate: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(MoSpacing.screen)
            .testTag("profile-cooperative-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Text("Tu cooperativa", style = MaterialTheme.typography.titleLarge, color = MoOliveDark)
        Text(
            "Elige una de tus cooperativas o almazaras. Es la misma lista que usas en pesadas y gastos: no se crea ninguna copia.",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
        )
        CooperativeOption("Ninguna", selected = current == null, enabled = !isSaving, tag = "profile-cooperative-none") { onChoose(null) }
        cooperatives.forEach { cooperative ->
            CooperativeOption(cooperative.name, selected = cooperative.id == current, enabled = !isSaving, tag = "profile-cooperative-option") {
                onChoose(cooperative.id)
            }
        }
        Text("¿No está en la lista?", style = MaterialTheme.typography.titleSmall, color = MoOliveDark, modifier = Modifier.padding(top = MoSpacing.sm))
        MoTextField(newName, { newName = it }, "Nombre de la cooperativa", Modifier.fillMaxWidth().testTag("profile-new-cooperative"))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("profile-error")) }
        MoPrimaryButton(
            "Añadir y elegir",
            { onCreate(newName) },
            Modifier.fillMaxWidth().testTag("profile-create-cooperative"),
            enabled = !isSaving && newName.isNotBlank(),
        )
        MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth())
    }
}

@Composable
private fun CooperativeOption(label: String, selected: Boolean, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private const val SHEET_LOCATION = "location"
private const val SHEET_COOPERATIVE = "cooperative"

/**
 * Phase 21B — Perfil → Avisos: one switch for every planned-work reminder on this phone and the
 * hour of the «day before» reminder. Android's own notification permission stays in the
 * «Notificaciones» row: this never overrides it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderSettings(
    preferences: ReminderPreferences,
    isSaving: Boolean,
    notificationsOn: Boolean,
    onNotifications: () -> Unit,
    onChange: (ReminderPreferences) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag("profile-reminders"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(
                    value = preferences.enabled,
                    enabled = !isSaving,
                    role = Role.Switch,
                    onValueChange = { onChange(preferences.copy(enabled = it)) },
                )
                .testTag("profile-reminders-switch"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Avisos de trabajos planificados", style = MaterialTheme.typography.bodyLarge, color = MoOliveDark)
                Text(
                    when {
                        !preferences.enabled -> "Desactivados: no suena ninguno; se guardan para cuando los actives."
                        !notificationsOn -> "Android bloquea los avisos en este teléfono."
                        else -> "Activados. Android controla el sonido y la vibración."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MoTextSecondary,
                )
            }
            Switch(checked = preferences.enabled, onCheckedChange = null, enabled = !isSaving)
        }
        if (preferences.enabled) {
            if (!notificationsOn) {
                MoTertiaryButton("Revisar notificaciones", onNotifications, Modifier.testTag("profile-reminders-notifications"))
            }
            Text("Aviso del día anterior, a las", style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                ReminderPreferences.PREVIOUS_DAY_CHOICES.forEach { hour ->
                    FilterChip(
                        selected = hour == preferences.previousDayTime,
                        onClick = { onChange(preferences.copy(previousDayTime = hour)) },
                        enabled = !isSaving,
                        label = { Text(hour.format(HOUR)) },
                        modifier = Modifier.testTag("profile-reminder-hour"),
                    )
                }
            }
        }
    }
}

private val HOUR = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
