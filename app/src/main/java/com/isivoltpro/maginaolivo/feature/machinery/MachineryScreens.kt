package com.isivoltpro.maginaolivo.feature.machinery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.machinery.Machine
import com.isivoltpro.maginaolivo.domain.machinery.MachineCategory
import com.isivoltpro.maginaolivo.feature.activities.editableHours
import com.isivoltpro.maginaolivo.feature.expenses.Choice
import com.isivoltpro.maginaolivo.feature.expenses.ChoiceSheet
import com.isivoltpro.maginaolivo.feature.expenses.DATE_FORMAT
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoConfirmationSheet
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoSelectField
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.util.UUID
import com.isivoltpro.maginaolivo.ui.theme.MoInk

@Composable
fun MachineryRoute(persistence: LocalPersistence, onMachineSelected: (UUID) -> Unit) {
    val viewModel: MachineryViewModel = viewModel(
        key = "machinery",
        factory = viewModelFactory { initializer { MachineryViewModel(persistence.machineRepository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    MachineryScreen(state, viewModel::create, onMachineSelected, viewModel::clearFormErrors)
}

/**
 * Maquinaria — a lightweight list of the farmer's machines. Only a name is required; the
 * machines then appear as an optional choice when registering an Activity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineryScreen(
    state: MachineryUiState,
    onCreate: (MachineForm) -> Unit,
    onMachineSelected: (UUID) -> Unit,
    onEditorClosed: () -> Unit = {},
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) { if (state.message != null) editorVisible = false }

    Scaffold(Modifier.fillMaxSize().testTag("machinery-root"), containerColor = MoCream) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.md))
            Text("Maquinaria", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "Tus máquinas, para anotarlas en las actuaciones si quieres. Solo hace falta el nombre.",
                style = MaterialTheme.typography.bodyLarge,
                color = MoTextSecondary,
            )
            MoPrimaryButton(
                "Añadir máquina",
                { editorVisible = true },
                Modifier.fillMaxWidth().testTag("add-machine"),
                enabled = !state.isSaving,
            )
            state.message?.let { Text(it, color = MoTextSecondary, modifier = Modifier.testTag("machinery-message")) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("machinery-error")) }
            when {
                state.isLoading -> CircularProgressIndicator()
                state.active.isEmpty() -> MoEmptyState(
                    "Aún no has añadido máquinas",
                    "No es obligatorio: las actuaciones se registran igual sin maquinaria.",
                    icon = MoIcons.Tractor,
                )
                else -> state.active.forEach { machine -> MachineRow(machine) { onMachineSelected(machine.id) } }
            }
            if (state.archived.isNotEmpty()) {
                MoSectionHeader("Retiradas")
                state.archived.forEach { machine -> MachineRow(machine) { onMachineSelected(machine.id) } }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    if (editorVisible) {
        ModalBottomSheet(onDismissRequest = { editorVisible = false; onEditorClosed() }) {
            MachineEditor(
                title = "Nueva máquina",
                initial = MachineForm(),
                errors = state.formErrors,
                isSaving = state.isSaving,
                saveText = "Guardar máquina",
                onSave = onCreate,
                onCancel = { editorVisible = false; onEditorClosed() },
            )
        }
    }
}

@Composable
private fun MachineRow(machine: Machine, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("machine-row"),
        shape = MoShape.card,
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MoSpacing.sm, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoIconBadge(MoIcons.Tractor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
                Text(machine.name, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
                Text(
                    listOfNotNull(machine.category.label(), listOfNotNull(machine.make, machine.model).joinToString(" ").ifEmpty { null })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MoTextSecondary,
                )
            }
            machine.currentHours?.let {
                Text("${editableHours(it)} h", style = MaterialTheme.typography.titleMedium, color = MoInk)
            }
        }
    }
}

@Composable
internal fun MachineEditor(
    title: String,
    initial: MachineForm,
    errors: MachineFormErrors,
    isSaving: Boolean,
    saveText: String,
    onSave: (MachineForm) -> Unit,
    onCancel: () -> Unit,
) {
    var form by remember(initial) { mutableStateOf(initial) }
    var picker by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen)
            .testTag("machine-editor"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MoOliveDark)
        MoTextField(
            form.name, { form = form.copy(name = it) }, "Nombre",
            isError = errors.name != null, supportingText = errors.name,
            modifier = Modifier.fillMaxWidth().testTag("machine-name"),
        )
        MoSelectField("Tipo", form.category.label(), { picker = true }, Modifier.testTag("machine-category"))
        MoTextField(form.make, { form = form.copy(make = it) }, "Marca (opcional)", modifier = Modifier.fillMaxWidth())
        MoTextField(form.model, { form = form.copy(model = it) }, "Modelo (opcional)", modifier = Modifier.fillMaxWidth())
        MoTextField(
            form.registration, { form = form.copy(registration = it) }, "Matrícula o nº de serie (opcional)",
            modifier = Modifier.fillMaxWidth(),
        )
        MoTextField(
            form.hours, { form = form.copy(hours = it) }, "Horas del contador (opcional)",
            isError = errors.hours != null, supportingText = errors.hours,
            modifier = Modifier.fillMaxWidth().testTag("machine-hours"),
        )
        MoTextField(form.notes, { form = form.copy(notes = it) }, "Notas", singleLine = false, modifier = Modifier.fillMaxWidth())
        MoPrimaryButton(saveText, { onSave(form) }, Modifier.fillMaxWidth().testTag("save-machine"), enabled = !isSaving)
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
    if (picker) {
        ChoiceSheet(
            "Tipo de máquina",
            MachineCategory.entries.map { Choice(it.name, it.label()) },
            form.category.name,
            { key -> form = form.copy(category = MachineCategory.entries.firstOrNull { it.name == key } ?: form.category) },
            { picker = false },
            "machine-category-sheet",
        )
    }
}

@Composable
fun MachineDetailRoute(machineId: UUID, persistence: LocalPersistence) {
    val viewModel: MachineDetailViewModel = viewModel(
        key = "machine-$machineId",
        factory = viewModelFactory { initializer { MachineDetailViewModel(machineId, persistence.machineRepository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    MachineDetailScreen(state, viewModel::update, viewModel::archive, viewModel::restore, viewModel::clearFormErrors)
}

/** Detalle de máquina: its data and the Activities that named it, with the hours recorded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineDetailScreen(
    state: MachineDetailUiState,
    onUpdate: (MachineForm) -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onEditorClosed: () -> Unit = {},
) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.message) { if (state.message != null) sheet = null }

    Scaffold(Modifier.fillMaxSize().testTag("machine-detail-root"), containerColor = MoCream) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            val machine = state.machine
            when {
                state.isLoading -> CircularProgressIndicator()
                machine == null -> MoErrorState("Máquina no disponible", state.error ?: "No está guardada en este dispositivo.")
                else -> {
                    Text(machine.name, style = MaterialTheme.typography.headlineMedium, color = MoOliveDark)
                    if (machine.archived) MoStatusChip("Retirada", modifier = Modifier.testTag("machine-archived"))
                    DetailValue("Tipo", machine.category.label())
                    DetailValue("Marca y modelo", listOfNotNull(machine.make, machine.model).joinToString(" ").ifEmpty { null })
                    DetailValue("Matrícula o nº de serie", machine.registrationOrSerial)
                    DetailValue("Horas del contador", machine.currentHours?.let { "${editableHours(it)} h" })
                    machine.notes?.let { DetailValue("Notas", it) }

                    MoSectionHeader("Actuaciones con esta máquina")
                    if (state.uses.isEmpty()) {
                        Text("Todavía no se ha anotado en ninguna actuación.", color = MoTextSecondary)
                    } else {
                        MoMetricCard(
                            "Horas anotadas",
                            "${editableHours(state.recordedHours)} h",
                            Modifier.fillMaxWidth().testTag("machine-recorded-hours"),
                            supportingText = if (state.usesWithoutHours > 0) {
                                "${state.usesWithoutHours} ${if (state.usesWithoutHours == 1) "actuación" else "actuaciones"} sin horas"
                            } else {
                                "${state.uses.size} ${if (state.uses.size == 1) "actuación" else "actuaciones"}"
                            },
                        )
                        state.uses.forEach { use ->
                            Row(Modifier.fillMaxWidth().testTag("machine-use"), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(use.description, style = MaterialTheme.typography.bodyLarge)
                                    Text(DATE_FORMAT.format(use.activityDate), style = MaterialTheme.typography.bodySmall, color = MoTextSecondary)
                                }
                                Text(use.hoursUsed?.let { "${editableHours(it)} h" } ?: "Sin horas", color = MoTextSecondary)
                            }
                        }
                    }

                    MoSecondaryButton("Editar máquina", { sheet = "edit" }, Modifier.fillMaxWidth().testTag("edit-machine"), enabled = !state.isSaving)
                    if (machine.archived) {
                        MoSecondaryButton("Volver a usarla", onRestore, Modifier.fillMaxWidth().testTag("restore-machine"), enabled = !state.isSaving)
                    } else {
                        MoSecondaryButton("Retirar máquina", { sheet = "archive" }, Modifier.fillMaxWidth().testTag("archive-machine"), enabled = !state.isSaving)
                    }
                    state.message?.let { Text(it, color = MoTextSecondary) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    val machine = state.machine ?: return
    when (sheet) {
        "edit" -> ModalBottomSheet(onDismissRequest = { sheet = null; onEditorClosed() }) {
            MachineEditor(
                title = "Editar máquina",
                initial = machine.toForm(),
                errors = state.formErrors,
                isSaving = state.isSaving,
                saveText = "Guardar cambios",
                onSave = onUpdate,
                onCancel = { sheet = null; onEditorClosed() },
            )
        }
        "archive" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            MoConfirmationSheet(
                title = "Retirar máquina",
                body = "Dejará de aparecer al registrar actuaciones. Las actuaciones pasadas la seguirán mostrando.",
                confirmText = "Retirar",
                onConfirm = { sheet = null; onArchive() },
                onCancel = { sheet = null },
                modifier = Modifier.padding(horizontal = MoSpacing.md).testTag("machine-confirmation"),
            )
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
        Text(value ?: "Sin registrar", style = MaterialTheme.typography.bodyLarge)
    }
}
