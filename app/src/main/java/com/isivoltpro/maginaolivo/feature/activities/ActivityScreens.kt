package com.isivoltpro.maginaolivo.feature.activities

import androidx.compose.foundation.BorderStroke
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
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
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
fun FarmActivitiesRoute(farmId: UUID, persistence: LocalPersistence, onActivitySelected: (UUID) -> Unit) {
    val vm: FarmActivitiesViewModel = viewModel(key = "farm-activities-$farmId", factory = viewModelFactory {
        initializer { FarmActivitiesViewModel(farmId, persistence.activityRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    FarmActivitiesSection(state, onActivitySelected, vm::create)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmActivitiesSection(
    state: FarmActivitiesUiState,
    onActivitySelected: (UUID) -> Unit,
    onCreate: (ActivityDraft, Boolean) -> Unit,
) {
    var editor by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) { if (state.message != null) editor = false }
    MoSectionHeader(
        "Actuaciones",
        action = {
            TextButton(onClick = { editor = true }, modifier = Modifier.testTag("add-activity")) { Text("Añadir") }
        },
    )
    when {
        state.isLoading -> CircularProgressIndicator()
        state.error != null -> MoErrorState("No pudimos abrir las actuaciones", state.error)
        state.drafts.isEmpty() && state.planned.isEmpty() && state.history.isEmpty() ->
            MoEmptyState("Aún no hay actuaciones", "Registra un trabajo y selecciona las parcelas donde se realiza.")
        else -> {
            if (state.drafts.isNotEmpty()) {
                MoSectionHeader("Borradores")
                state.drafts.forEach { ActivityRow(it, onActivitySelected) }
            }
            state.planned.forEach { ActivityRow(it, onActivitySelected) }
            if (state.history.isNotEmpty()) {
                MoSectionHeader("Histórico")
                state.history.forEach { ActivityRow(it, onActivitySelected) }
            }
        }
    }
    if (editor) {
        ModalBottomSheet(onDismissRequest = { editor = false }) {
            ActivityEditor(
                parcels = state.parcels,
                descriptionError = state.descriptionError,
                dateError = state.dateError,
                parcelsError = state.parcelsError,
                isSaving = state.isSaving,
                onSave = { draft -> onCreate(draft, false) },
                onSaveDraft = { draft -> onCreate(draft, true) },
                onCancel = { editor = false },
            )
        }
    }
}

@Composable
private fun ActivityRow(activity: Activity, onSelected: (UUID) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onSelected(activity.id) }.testTag("activity-row"),
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(MoSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(activity.description, style = MaterialTheme.typography.titleMedium)
                Text("${activity.type.label()} · ${activity.activityDate}", color = MoTextSecondary)
                Text(activity.targetsLabel(), color = MoTextSecondary)
            }
            MoStatusChip(activity.status.label(), tone = activity.status.tone())
        }
    }
}

@Composable
internal fun ActivityEditor(
    parcels: List<ActivityParcelOption>,
    descriptionError: String?,
    dateError: String?,
    parcelsError: String?,
    isSaving: Boolean,
    onSave: (ActivityDraft) -> Unit,
    onCancel: () -> Unit,
    onSaveDraft: ((ActivityDraft) -> Unit)? = null,
    initial: ActivityDraft = ActivityDraft(),
    title: String = "Nueva actuación",
) {
    var description by rememberSaveable(initial.description) { mutableStateOf(initial.description) }
    var date by rememberSaveable(initial.activityDate) { mutableStateOf(initial.activityDate?.toString().orEmpty()) }
    var notes by rememberSaveable(initial.notes) { mutableStateOf(initial.notes) }
    var type by rememberSaveable(initial.type) { mutableStateOf(initial.type.name) }
    var selected by rememberSaveable(initial.parcelIds) { mutableStateOf(initial.parcelIds.map(UUID::toString)) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        MoTextField(
            description, { description = it }, "Descripción",
            isError = descriptionError != null, supportingText = descriptionError,
            modifier = Modifier.testTag("activity-description"),
        )
        MoTextField(
            date, { date = it }, "Fecha (AAAA-MM-DD)",
            isError = dateError != null, supportingText = dateError,
            modifier = Modifier.testTag("activity-date"),
        )
        MoSectionHeader("Tipo de trabajo")
        ActivityType.entries.forEach { option ->
            val checked = option.name == type
            Row(
                Modifier.fillMaxWidth().testTag("activity-type-option").clickable { type = option.name }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, { type = option.name })
                Text(option.label())
            }
        }
        MoSectionHeader("Parcelas")
        // One canonical Activity may target many Parcels; selecting several never
        // creates several Activities.
        if (parcels.isEmpty()) Text("Primero añade una parcela a esta finca.", color = MoTextSecondary)
        parcelsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        parcels.forEach { parcel ->
            val checked = parcel.id.toString() in selected
            Row(
                Modifier.fillMaxWidth().testTag("activity-parcel-option").clickable {
                    selected = if (checked) selected - parcel.id.toString() else selected + parcel.id.toString()
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, { value ->
                    selected = if (value) selected + parcel.id.toString() else selected - parcel.id.toString()
                })
                Text(parcel.name)
            }
        }
        MoTextField(notes, { notes = it }, "Notas")
        MoPrimaryButton(
            "Guardar actuación",
            {
                onSave(
                    ActivityDraft(
                        runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
                        runCatching { LocalDate.parse(date) }.getOrNull(),
                        description,
                        selected.map(UUID::fromString).toSet(),
                        notes,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("save-activity"), enabled = !isSaving,
        )
        onSaveDraft?.let { saveDraft ->
            MoSecondaryButton(
                "Guardar borrador",
                {
                    saveDraft(
                        ActivityDraft(
                            runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
                            runCatching { LocalDate.parse(date) }.getOrNull(),
                            description,
                            selected.map(UUID::fromString).toSet(),
                            notes,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("save-activity-draft"),
            )
        }
        MoSecondaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ActivityDetailRoute(activityId: UUID, persistence: LocalPersistence) {
    val vm: ActivityDetailViewModel = viewModel(key = "activity-$activityId", factory = viewModelFactory {
        initializer { ActivityDetailViewModel(activityId, persistence.activityRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    ActivityDetailScreen(state, vm::update, vm::plan, vm::complete, vm::cancel, vm::reopen, vm::archive)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    state: ActivityDetailUiState,
    onUpdate: (ActivityDraft) -> Unit,
    onPlan: () -> Unit,
    onComplete: () -> Unit,
    onCancelActivity: () -> Unit,
    onReopen: () -> Unit,
    onArchive: () -> Unit,
) {
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf(false) }
    Scaffold(Modifier.fillMaxSize().testTag("activity-detail-root"), containerColor = MoCream) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.activity == null ->
                    MoErrorState("Actuación no disponible", state.error ?: "No está guardada en este dispositivo.")
                else -> {
                    val activity = state.activity
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(activity.description, style = MaterialTheme.typography.headlineLarge)
                        MoStatusChip(activity.status.label(), tone = activity.status.tone())
                    }
                    Text("${activity.type.label()} · ${activity.activityDate}", color = MoTextSecondary)
                    activity.notes?.let { Text(it, color = MoTextSecondary) }

                    MoSectionHeader("Parcelas afectadas")
                    if (activity.targets.isEmpty()) {
                        Text("Sin parcelas seleccionadas todavía.", color = MoTextSecondary)
                    }
                    activity.targets.forEach { target ->
                        Column(Modifier.fillMaxWidth().testTag("activity-target")) {
                            Text(target.parcelName, style = MaterialTheme.typography.titleMedium)
                            target.areaAffectedM2?.let { Text("Superficie: ${it.toLong()} m²", color = MoTextSecondary) }
                        }
                    }
                    MoMetricCard("Parcelas", activity.targets.size.toString(), Modifier.fillMaxWidth())

                    when (activity.status) {
                        ActivityStatus.DRAFT -> {
                            MoPrimaryButton("Editar borrador", { editor = true }, modifier = Modifier.fillMaxWidth().testTag("edit-activity"), enabled = !state.isSaving)
                            MoPrimaryButton("Planificar", { confirmation = "plan" }, modifier = Modifier.fillMaxWidth().testTag("plan-activity"), enabled = !state.isSaving)
                            MoSecondaryButton("Archivar borrador", { confirmation = "archive" }, modifier = Modifier.fillMaxWidth().testTag("archive-activity"))
                        }
                        ActivityStatus.PLANNED -> {
                            MoPrimaryButton("Editar actuación", { editor = true }, modifier = Modifier.fillMaxWidth().testTag("edit-activity"), enabled = !state.isSaving)
                            MoPrimaryButton("Marcar completada", { confirmation = "complete" }, modifier = Modifier.fillMaxWidth().testTag("complete-activity"), enabled = !state.isSaving)
                            MoSecondaryButton("Cancelar actuación", { confirmation = "cancel" }, modifier = Modifier.fillMaxWidth().testTag("cancel-activity"))
                        }
                        ActivityStatus.COMPLETED -> {
                            Text("Registro protegido", style = MaterialTheme.typography.titleMedium)
                            MoSecondaryButton("Reabrir actuación", { confirmation = "reopen" }, modifier = Modifier.fillMaxWidth().testTag("reopen-activity"))
                        }
                        ActivityStatus.CANCELLED -> {
                            Text("Actuación cancelada", style = MaterialTheme.typography.titleMedium)
                            MoSecondaryButton("Reabrir actuación", { confirmation = "reopen" }, modifier = Modifier.fillMaxWidth().testTag("reopen-activity"))
                            MoSecondaryButton("Archivar", { confirmation = "archive" }, modifier = Modifier.fillMaxWidth().testTag("archive-activity"))
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    val activity = state.activity
    if (editor && activity != null) {
        ModalBottomSheet(onDismissRequest = { editor = false }) {
            ActivityEditor(
                parcels = state.parcels,
                descriptionError = null,
                dateError = null,
                parcelsError = null,
                isSaving = state.isSaving,
                onSave = { draft -> onUpdate(draft); editor = false },
                onCancel = { editor = false },
                initial = ActivityDraft(
                    type = activity.type,
                    activityDate = activity.activityDate,
                    description = activity.description,
                    parcelIds = activity.targets.map { it.parcelId }.toSet(),
                    notes = activity.notes.orEmpty(),
                ),
                title = "Editar actuación",
            )
        }
    }
    if (confirmation != null) {
        ModalBottomSheet(onDismissRequest = { confirmation = null }) {
            Column(
                Modifier.fillMaxWidth().padding(MoSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
            ) {
                Text("Confirmar cambio", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Esta acción actualizará el estado de la actuación guardada en este dispositivo.",
                    color = MoTextSecondary,
                )
                MoPrimaryButton(
                    "Confirmar",
                    {
                        when (confirmation) {
                            "plan" -> onPlan()
                            "complete" -> onComplete()
                            "cancel" -> onCancelActivity()
                            "reopen" -> onReopen()
                            "archive" -> onArchive()
                        }
                        confirmation = null
                    },
                    modifier = Modifier.fillMaxWidth().testTag("confirm-activity-action"),
                )
                MoSecondaryButton("Cancelar", { confirmation = null }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun Activity.targetsLabel(): String = when (targets.size) {
    0 -> "Sin parcelas"
    1 -> targets.single().parcelName
    else -> "${targets.size} parcelas"
}

private fun ActivityStatus.label() = when (this) {
    ActivityStatus.DRAFT -> "Borrador"
    ActivityStatus.PLANNED -> "Planificada"
    ActivityStatus.COMPLETED -> "Completada"
    ActivityStatus.CANCELLED -> "Cancelada"
}

private fun ActivityStatus.tone() = when (this) {
    ActivityStatus.DRAFT -> MoStatusTone.Neutral
    ActivityStatus.PLANNED -> MoStatusTone.Info
    ActivityStatus.COMPLETED -> MoStatusTone.Success
    ActivityStatus.CANCELLED -> MoStatusTone.Warning
}

private fun ActivityType.label() = when (this) {
    ActivityType.OBSERVATION -> "Observación"
    ActivityType.PRUNING -> "Poda"
    ActivityType.SOIL_WORK -> "Labores de suelo"
    ActivityType.FERTILIZATION -> "Abonado"
    ActivityType.PHYTOSANITARY -> "Tratamiento"
    ActivityType.IRRIGATION -> "Riego"
    ActivityType.MAINTENANCE -> "Mantenimiento"
    ActivityType.INCIDENT -> "Incidencia"
    ActivityType.OTHER -> "Otro"
}
