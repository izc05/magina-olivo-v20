package com.isivoltpro.maginaolivo.feature.phytosanitary

import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicCredential
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicCredentialDraft
import com.isivoltpro.maginaolivo.domain.phytosanitary.AgronomicPerson
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoConfirmationSheet
import com.isivoltpro.maginaolivo.ui.components.MoDateInputField
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.components.OnEachSave
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.time.LocalDate
import java.util.UUID

@Composable
fun AgronomicPeopleRoute(
    persistence: LocalPersistence,
    onPersonSelected: (UUID) -> Unit,
) {
    val repository = persistence.phytosanitaryResourceRepository
    if (repository == null) {
        MoErrorState("Datos del cuaderno no disponibles", "Esta versión todavía no tiene los recursos CUE preparados.")
        return
    }
    val vm: AgronomicPeopleViewModel = viewModel(
        key = "agronomic-people",
        factory = viewModelFactory { initializer { AgronomicPeopleViewModel(repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    AgronomicPeopleScreen(state, vm::create, onPersonSelected, vm::clearMessage)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgronomicPeopleScreen(
    state: AgronomicPeopleUiState,
    onCreate: (AgronomicPersonForm) -> Unit,
    onPersonSelected: (UUID) -> Unit,
    onEditorClosed: () -> Unit = {},
) {
    var editor by rememberSaveable { mutableStateOf(false) }
    OnEachSave(state.saveCount) { editor = false }

    Scaffold(
        Modifier.fillMaxSize().testTag("agronomic-people-root"),
        containerColor = MoSurfaceTokens.appBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.md))
            Text("Aplicadores y asesores", style = MaterialTheme.typography.headlineLarge, color = MoColors.current.primaryText)
            Text(
                "Personas que intervienen en tratamientos o asesoramiento. Se guardan primero en este teléfono.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoColors.current.secondaryText,
            )
            MoPrimaryButton(
                "Añadir persona",
                { editor = true },
                Modifier.fillMaxWidth().testTag("add-agronomic-person"),
                enabled = !state.isSaving,
            )
            state.message?.let { Text(it, color = MoColors.current.secondaryText, modifier = Modifier.testTag("agronomic-people-message")) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("agronomic-people-error")) }
            when {
                state.isLoading -> CircularProgressIndicator()
                state.active.isEmpty() -> MoEmptyState(
                    "Aún no has añadido aplicadores",
                    "Puedes hacerlo cuando registres tus tratamientos.",
                    icon = MoIcons.People,
                )
                else -> state.active.forEach { person ->
                    PersonRow(
                        person = person,
                        credentialSummary = state.credentialSummaries[person.id],
                        onClick = { onPersonSelected(person.id) },
                    )
                }
            }
            if (state.archived.isNotEmpty()) {
                MoSectionHeader("Archivados")
                state.archived.forEach { person ->
                    PersonRow(person = person, credentialSummary = null, onClick = { onPersonSelected(person.id) })
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    if (editor) {
        ModalBottomSheet(onDismissRequest = { editor = false; onEditorClosed() }) {
            PersonEditor(
                title = "Añadir aplicador o asesor",
                initial = AgronomicPersonForm(),
                saveText = "Guardar",
                isSaving = state.isSaving,
                onSave = onCreate,
                onCancel = { editor = false; onEditorClosed() },
            )
        }
    }
}

@Composable
private fun PersonRow(
    person: AgronomicPerson,
    credentialSummary: String?,
    onClick: () -> Unit,
) {
    MoCompactListItem(
        title = person.displayName,
        subtitle = buildList {
            add(if (person.isAdvisor) "Asesor" else "Aplicador")
            person.taxId?.let { add(maskIdentifier(it)) }
            credentialSummary?.let { add(it) }
            if (person.archived) add("Archivado")
        }.joinToString(" · "),
        icon = MoIcons.Person,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("agronomic-person-row"),
        trailing = {
            when {
                person.archived -> MoStatusChip("Archivado", tone = MoStatusTone.Neutral)
                person.isAdvisor -> MoStatusChip("Asesor", tone = MoStatusTone.Info)
                else -> MoStatusChip("Aplicador", tone = MoStatusTone.Neutral)
            }
        },
    )
}

@Composable
private fun PersonEditor(
    title: String,
    initial: AgronomicPersonForm,
    saveText: String,
    isSaving: Boolean,
    onSave: (AgronomicPersonForm) -> Unit,
    onCancel: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        MoTextField(
            form.displayName,
            { form = form.copy(displayName = it) },
            "Nombre y apellidos",
            modifier = Modifier.fillMaxWidth().testTag("agronomic-person-name"),
        )
        MoTextField(
            form.taxId,
            { form = form.copy(taxId = it) },
            "NIF (opcional)",
            modifier = Modifier.fillMaxWidth().testTag("agronomic-person-tax-id"),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("También es asesor", style = MaterialTheme.typography.bodyLarge)
                Text("Actívalo solo si realiza asesoramiento agronómico.", style = MaterialTheme.typography.bodySmall, color = MoColors.current.secondaryText)
            }
            Switch(
                checked = form.isAdvisor,
                onCheckedChange = { form = form.copy(isAdvisor = it) },
                modifier = Modifier.testTag("agronomic-person-advisor"),
            )
        }
        MoPrimaryButton(saveText, { onSave(form) }, Modifier.fillMaxWidth().testTag("save-agronomic-person"), enabled = !isSaving)
        MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
fun AgronomicPersonDetailRoute(
    personId: UUID,
    persistence: LocalPersistence,
) {
    val repository = persistence.phytosanitaryResourceRepository
    if (repository == null) {
        MoErrorState("Datos del cuaderno no disponibles", "Esta versión todavía no tiene los recursos CUE preparados.")
        return
    }
    val vm: AgronomicPersonDetailViewModel = viewModel(
        key = "agronomic-person-$personId",
        factory = viewModelFactory { initializer { AgronomicPersonDetailViewModel(personId, repository) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    AgronomicPersonDetailScreen(
        state = state,
        onUpdate = vm::update,
        onAddCredential = vm::addCredential,
        onArchive = vm::archive,
        onRestore = vm::restore,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgronomicPersonDetailScreen(
    state: AgronomicPersonDetailUiState,
    onUpdate: (AgronomicPersonForm) -> Unit,
    onAddCredential: (AgronomicCredentialDraft) -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    OnEachSave(state.saveCount) { sheet = null }

    Scaffold(
        Modifier.fillMaxSize().testTag("agronomic-person-detail-root"),
        containerColor = MoSurfaceTokens.appBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            val person = state.person
            when {
                state.isLoading -> CircularProgressIndicator()
                person == null -> MoErrorState("Persona no disponible", state.error ?: "No está guardada en este dispositivo.")
                else -> {
                    Text(person.displayName, style = MaterialTheme.typography.headlineMedium, color = MoColors.current.primaryText)
                    Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                        if (person.isAdvisor) MoStatusChip("Asesor", tone = MoStatusTone.Info)
                        if (person.archived) MoStatusChip("Archivado", tone = MoStatusTone.Neutral)
                    }
                    DetailValue("NIF", person.taxId)
                    DetailValue("Origen", if (person.source.name == "REAFA") "REAFA" else "Manual")

                    MoSectionHeader("Credenciales")
                    if (state.credentials.isEmpty()) {
                        Text("Sin carné o ROPO registrado.", color = MoColors.current.secondaryText)
                    } else {
                        state.credentials.forEach { CredentialRow(it) }
                    }
                    if (!person.archived) {
                        MoSecondaryButton(
                            "Añadir credencial",
                            { sheet = "credential" },
                            Modifier.fillMaxWidth().testTag("add-agronomic-credential"),
                            enabled = !state.isSaving,
                        )
                    }

                    MoSectionHeader("Datos de la persona")
                    MoSecondaryButton(
                        "Editar",
                        { sheet = "edit" },
                        Modifier.fillMaxWidth().testTag("edit-agronomic-person"),
                        enabled = !state.isSaving,
                    )
                    if (person.archived) {
                        MoSecondaryButton(
                            "Volver a usarla",
                            onRestore,
                            Modifier.fillMaxWidth().testTag("restore-agronomic-person"),
                            enabled = !state.isSaving,
                        )
                    } else {
                        MoSecondaryButton(
                            "Archivar",
                            { sheet = "archive" },
                            Modifier.fillMaxWidth().testTag("archive-agronomic-person"),
                            enabled = !state.isSaving,
                        )
                    }
                    state.message?.let { Text(it, color = MoColors.current.secondaryText) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    val person = state.person ?: return
    when (sheet) {
        "edit" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            PersonEditor(
                title = "Editar persona",
                initial = AgronomicPersonForm(person.displayName, person.taxId.orEmpty(), person.isAdvisor),
                saveText = "Guardar cambios",
                isSaving = state.isSaving,
                onSave = onUpdate,
                onCancel = { sheet = null },
            )
        }
        "credential" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            CredentialEditor(
                isAdvisor = person.isAdvisor,
                isSaving = state.isSaving,
                onSave = onAddCredential,
                onCancel = { sheet = null },
            )
        }
        "archive" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            MoConfirmationSheet(
                title = "Archivar persona",
                body = "Dejará de aparecer en nuevas selecciones. Los tratamientos anteriores conservarán su referencia e histórico.",
                confirmText = "Archivar",
                onConfirm = { sheet = null; onArchive() },
                onCancel = { sheet = null },
                modifier = Modifier.padding(horizontal = MoSpacing.md),
            )
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

@Composable
private fun CredentialRow(credential: AgronomicCredential) {
    Column(
        Modifier.fillMaxWidth().testTag("agronomic-credential-row"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
    ) {
        Text(credentialKindLabel(credential.credentialType), style = MaterialTheme.typography.titleSmall)
        Text(
            buildList {
                add(credential.number)
                credential.categoryCode?.let { add(it) }
                val validity = listOfNotNull(
                    credential.validFrom?.let { "desde $it" },
                    credential.validUntil?.let { "hasta $it" },
                ).joinToString(" · ")
                if (validity.isNotBlank()) add(validity)
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MoColors.current.secondaryText,
        )
    }
}

@Composable
private fun CredentialEditor(
    isAdvisor: Boolean,
    isSaving: Boolean,
    onSave: (AgronomicCredentialDraft) -> Unit,
    onCancel: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(if (isAdvisor) "ADVISOR" else "APPLICATOR_CARD") }
    var number by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var until by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Añadir credencial", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
            MoSecondaryButton(
                "Aplicador",
                { kind = "APPLICATOR_CARD" },
                Modifier.weight(1f).testTag("credential-kind-applicator"),
            )
            MoSecondaryButton(
                "Asesor",
                { kind = "ADVISOR" },
                Modifier.weight(1f).testTag("credential-kind-advisor"),
            )
        }
        Text("Tipo: ${credentialKindLabel(kind)}", style = MaterialTheme.typography.bodySmall, color = MoColors.current.secondaryText)
        MoTextField(number, { number = it; error = null }, "Nº carné / ROPO", modifier = Modifier.fillMaxWidth().testTag("credential-number"))
        MoTextField(category, { category = it }, "Categoría (opcional)", modifier = Modifier.fillMaxWidth().testTag("credential-category"))
        MoDateInputField(from, { from = it }, "Válida desde (opcional)", modifier = Modifier.testTag("credential-valid-from"))
        MoDateInputField(until, { until = it }, "Válida hasta (opcional)", modifier = Modifier.testTag("credential-valid-until"))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        MoPrimaryButton(
            "Guardar credencial",
            {
                val validFrom = from.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val validUntil = until.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                when {
                    number.isBlank() -> error = "Escribe el número de carné o ROPO"
                    from.isNotBlank() && validFrom == null -> error = "Revisa la fecha inicial"
                    until.isNotBlank() && validUntil == null -> error = "Revisa la fecha final"
                    validFrom != null && validUntil != null && validUntil.isBefore(validFrom) ->
                        error = "La fecha final no puede ser anterior a la inicial"
                    else -> onSave(
                        AgronomicCredentialDraft(
                            credentialType = kind,
                            number = number.trim(),
                            categoryCode = category.trim().ifEmpty { null },
                            validFrom = validFrom,
                            validUntil = validUntil,
                        ),
                    )
                }
            },
            Modifier.fillMaxWidth().testTag("save-agronomic-credential"),
            enabled = !isSaving,
        )
        MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoColors.current.secondaryText)
        Text(value ?: "Sin registrar", style = MaterialTheme.typography.bodyLarge)
    }
}

private fun credentialKindLabel(value: String): String = when (value) {
    "APPLICATOR_CARD" -> "Carné / ROPO de aplicador"
    "ADVISOR" -> "Asesor"
    else -> value
}

