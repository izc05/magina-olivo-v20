package com.isivoltpro.maginaolivo.feature.machinery

import com.isivoltpro.maginaolivo.ui.components.OnEachSave
import androidx.compose.foundation.layout.WindowInsets
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
import com.isivoltpro.maginaolivo.domain.phytosanitary.RegulatoryResourceSource
import com.isivoltpro.maginaolivo.feature.activities.editableHours
import com.isivoltpro.maginaolivo.feature.expenses.Choice
import com.isivoltpro.maginaolivo.feature.expenses.ChoiceSheet
import com.isivoltpro.maginaolivo.feature.expenses.DATE_FORMAT
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoConfirmationSheet
import com.isivoltpro.maginaolivo.ui.components.MoDateInputField
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoMetricCard
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoSelectField
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
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
    OnEachSave(state.saveCount) { editorVisible = false }

    Scaffold(Modifier.fillMaxSize().testTag("machinery-root"), containerColor = MoCream, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.md))
            Text("Mis máquinas", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "Tus máquinas, para anotarlas en los trabajos si quieres. Solo hace falta el nombre.",
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
                    "No es obligatorio: los trabajos se registran igual sin maquinaria.",
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
        factory = viewModelFactory {
            initializer {
                MachineDetailViewModel(
                    machineId,
                    persistence.machineRepository,
                    persistence.phytosanitaryResourceRepository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    MachineDetailScreen(
        state = state,
        onUpdate = viewModel::update,
        onSavePhytosanitaryProfile = viewModel::savePhytosanitaryProfile,
        onAddPhytosanitaryInspection = viewModel::addPhytosanitaryInspection,
        onArchive = viewModel::archive,
        onRestore = viewModel::restore,
        onEditorClosed = viewModel::clearFormErrors,
    )
}

/** Detalle de máquina: its data and the Activities that named it, with the hours recorded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineDetailScreen(
    state: MachineDetailUiState,
    onUpdate: (MachineForm) -> Unit,
    onSavePhytosanitaryProfile: (PhytosanitaryEquipmentForm) -> Unit = {},
    onAddPhytosanitaryInspection: (PhytosanitaryInspectionForm) -> Unit = {},
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onEditorClosed: () -> Unit = {},
) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    OnEachSave(state.saveCount) { sheet = null }

    Scaffold(Modifier.fillMaxSize().testTag("machine-detail-root"), containerColor = MoCream, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
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

                    var showPhytosanitaryDetails by rememberSaveable(machine.id) { mutableStateOf(false) }
                    MoSectionHeader("Datos fitosanitarios")
                    if (showPhytosanitaryDetails) {
                    Text(
                        "Solo si esta máquina se utiliza para aplicar productos fitosanitarios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MoTextSecondary,
                    )
                    val phyto = state.phytosanitaryProfile
                    if (phyto == null) {
                        Text("Sin configurar", color = MoTextSecondary, modifier = Modifier.testTag("machine-phyto-empty"))
                        if (!machine.archived) {
                            MoSecondaryButton(
                                "Configurar datos fitosanitarios",
                                { sheet = "phyto-profile" },
                                Modifier.fillMaxWidth().testTag("machine-phyto-configure"),
                                enabled = !state.isSaving,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                            MoStatusChip(
                                if (phyto.source == RegulatoryResourceSource.REAFA) "REAFA" else "Manual",
                                tone = if (phyto.source == RegulatoryResourceSource.REAFA) MoStatusTone.Success else MoStatusTone.Neutral,
                                modifier = Modifier.testTag("machine-phyto-source"),
                            )
                        }
                        DetailValue("ROMA", phyto.romaRegistration)
                        DetailValue("Referencia de censo", phyto.censusReference)
                        DetailValue("Fecha de adquisición", phyto.acquisitionDate?.toString())
                        if (phyto.source != RegulatoryResourceSource.REAFA && !machine.archived) {
                            MoTertiaryButton(
                                "Editar datos fitosanitarios",
                                { sheet = "phyto-profile" },
                                Modifier.fillMaxWidth().testTag("machine-phyto-edit"),
                            )
                        } else if (phyto.source == RegulatoryResourceSource.REAFA) {
                            Text(
                                "Datos recibidos de REAFA. No se sobrescriben manualmente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MoTextSecondary,
                            )
                        }
                    }

                    MoSectionHeader("Inspecciones del equipo")
                    if (state.phytosanitaryInspections.isEmpty()) {
                        Text("Sin inspecciones registradas.", color = MoTextSecondary)
                    } else {
                        state.phytosanitaryInspections.forEach { inspection ->
                            Column(
                                Modifier.fillMaxWidth().testTag("machine-phyto-inspection"),
                                verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
                            ) {
                                Text(
                                    DATE_FORMAT.format(inspection.inspectionDate),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    listOfNotNull(
                                        inspection.resultCode,
                                        inspection.certificateReference?.let { "Cert. $it" },
                                    ).ifEmpty { listOf("Sin resultado adicional") }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MoTextSecondary,
                                )
                            }
                        }
                    }
                    if (!machine.archived) {
                        MoSecondaryButton(
                            "Añadir inspección",
                            { sheet = "phyto-inspection" },
                            Modifier.fillMaxWidth().testTag("machine-add-phyto-inspection"),
                            enabled = !state.isSaving,
                        )
                    }

                    MoTertiaryButton(
                        "Ocultar datos fitosanitarios",
                        { showPhytosanitaryDetails = false },
                        Modifier.fillMaxWidth().testTag("machine-phyto-hide"),
                    )
                    } else {
                        MoTertiaryButton(
                            if (state.phytosanitaryProfile != null || state.phytosanitaryInspections.isNotEmpty()) {
                                "Ver datos fitosanitarios · configurados"
                            } else {
                                "Ver datos fitosanitarios"
                            },
                            { showPhytosanitaryDetails = true },
                            Modifier.fillMaxWidth().testTag("machine-phyto-details"),
                        )
                    }

                    MoSectionHeader("Trabajos con esta máquina")
                    if (state.uses.isEmpty()) {
                        Text("Todavía no se ha anotado en ningún trabajo.", color = MoTextSecondary)
                    } else {
                        MoMetricCard(
                            "Horas anotadas",
                            "${editableHours(state.recordedHours)} h",
                            Modifier.fillMaxWidth().testTag("machine-recorded-hours"),
                            supportingText = if (state.usesWithoutHours > 0) {
                                "${state.usesWithoutHours} ${if (state.usesWithoutHours == 1) "trabajo" else "trabajos"} sin horas"
                            } else {
                                "${state.uses.size} ${if (state.uses.size == 1) "trabajo" else "trabajos"}"
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
        "phyto-profile" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            PhytosanitaryEquipmentEditor(
                initial = state.phytosanitaryProfile,
                isSaving = state.isSaving,
                onSave = onSavePhytosanitaryProfile,
                onCancel = { sheet = null },
            )
        }
        "phyto-inspection" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            PhytosanitaryInspectionEditor(
                isSaving = state.isSaving,
                onSave = onAddPhytosanitaryInspection,
                onCancel = { sheet = null },
            )
        }
        "archive" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            MoConfirmationSheet(
                title = "Retirar máquina",
                body = "Dejará de aparecer al registrar trabajos. Los trabajos pasados la seguirán mostrando.",
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
private fun PhytosanitaryEquipmentEditor(
    initial: com.isivoltpro.maginaolivo.domain.phytosanitary.PhytosanitaryEquipmentProfile?,
    isSaving: Boolean,
    onSave: (PhytosanitaryEquipmentForm) -> Unit,
    onCancel: () -> Unit,
) {
    var roma by rememberSaveable(initial?.romaRegistration) { mutableStateOf(initial?.romaRegistration.orEmpty()) }
    var census by rememberSaveable(initial?.censusReference) { mutableStateOf(initial?.censusReference.orEmpty()) }
    var acquisition by rememberSaveable(initial?.acquisitionDate) { mutableStateOf(initial?.acquisitionDate?.toString().orEmpty()) }
    var dateError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Datos fitosanitarios", style = MaterialTheme.typography.headlineSmall)
        Text(
            "No confundas el ROMA con la matrícula o nº de serie de la máquina.",
            style = MaterialTheme.typography.bodySmall,
            color = MoTextSecondary,
        )
        MoTextField(roma, { roma = it }, "Nº ROMA (si aplica)", modifier = Modifier.fillMaxWidth().testTag("machine-roma"))
        MoTextField(census, { census = it }, "Referencia de censo (si aplica)", modifier = Modifier.fillMaxWidth().testTag("machine-census"))
        MoDateInputField(
            acquisition,
            {
                acquisition = it
                dateError = null
            },
            "Fecha de adquisición (opcional)",
            isError = dateError != null,
            supportingText = dateError,
            modifier = Modifier.testTag("machine-acquisition-date"),
        )
        MoPrimaryButton(
            "Guardar",
            {
                if (acquisition.isNotBlank() && runCatching { java.time.LocalDate.parse(acquisition) }.isFailure) {
                    dateError = "Revisa la fecha de adquisición"
                } else {
                    onSave(PhytosanitaryEquipmentForm(roma, census, acquisition))
                }
            },
            Modifier.fillMaxWidth().testTag("save-machine-phyto-profile"),
            enabled = !isSaving,
        )
        MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun PhytosanitaryInspectionEditor(
    isSaving: Boolean,
    onSave: (PhytosanitaryInspectionForm) -> Unit,
    onCancel: () -> Unit,
) {
    var date by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    var certificate by rememberSaveable { mutableStateOf("") }
    var dateError by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text("Añadir inspección", style = MaterialTheme.typography.headlineSmall)
        MoDateInputField(
            date,
            {
                date = it
                dateError = null
            },
            "Fecha de inspección",
            isError = dateError != null,
            supportingText = dateError,
            modifier = Modifier.testTag("machine-inspection-date"),
        )
        MoTextField(
            result,
            { result = it },
            "Resultado (opcional)",
            modifier = Modifier.fillMaxWidth().testTag("machine-inspection-result"),
        )
        MoTextField(
            certificate,
            { certificate = it },
            "Referencia del certificado (opcional)",
            modifier = Modifier.fillMaxWidth().testTag("machine-inspection-certificate"),
        )
        MoPrimaryButton(
            "Guardar inspección",
            {
                if (runCatching { java.time.LocalDate.parse(date) }.isFailure) {
                    dateError = "Selecciona una fecha válida"
                } else {
                    onSave(PhytosanitaryInspectionForm(date, result, certificate))
                }
            },
            Modifier.fillMaxWidth().testTag("save-machine-phyto-inspection"),
            enabled = !isSaving,
        )
        MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

@Composable
private fun DetailValue(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MoTextSecondary)
        Text(value ?: "Sin registrar", style = MaterialTheme.typography.bodyLarge)
    }
}
