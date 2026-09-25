package com.isivoltpro.maginaolivo.feature.activities

import androidx.compose.foundation.layout.WindowInsets
import com.isivoltpro.maginaolivo.domain.machinery.MachineOption
import com.isivoltpro.maginaolivo.domain.machinery.MachineUseInput
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsRoute
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.Activity
import com.isivoltpro.maginaolivo.domain.activity.ActivityDetail
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.activity.IncidentSeverity
import com.isivoltpro.maginaolivo.domain.activity.IncidentState
import com.isivoltpro.maginaolivo.domain.activity.IrrigationPricingBasis
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.ui.components.MoDateInputField
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import androidx.compose.foundation.layout.size
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIconBadge
import com.isivoltpro.maginaolivo.ui.components.MoDestructiveButton
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoSummaryMetric
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import java.time.LocalDate
import java.util.UUID

@Composable
fun FarmActivitiesRoute(
    farmId: UUID,
    persistence: LocalPersistence,
    onActivitySelected: (UUID) -> Unit,
    startWithEditor: Boolean = false,
    /** UX-D: what "Registrar hoy" already knows (type, today's date). */
    initialDraft: ActivityDraft = ActivityDraft(),
    editorTitle: String = "Nueva actuación",
) {
    val vm: FarmActivitiesViewModel = viewModel(key = "farm-activities-$farmId", factory = viewModelFactory {
        initializer { FarmActivitiesViewModel(farmId, persistence.activityRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    FarmActivitiesSection(state, onActivitySelected, vm::create, startWithEditor, initialDraft, editorTitle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmActivitiesSection(
    state: FarmActivitiesUiState,
    onActivitySelected: (UUID) -> Unit,
    onCreate: (ActivityDraft, Boolean) -> Unit,
    startWithEditor: Boolean = false,
    initialDraft: ActivityDraft = ActivityDraft(),
    editorTitle: String = "Nueva actuación",
) {
    var editor by rememberSaveable { mutableStateOf(startWithEditor) }
    LaunchedEffect(state.message) { if (state.message != null) editor = false }
    // UX-D: saving is confirmed where the farmer is looking, not only by the closed sheet.
    state.message?.let { message ->
        MoStatusChip(message, tone = MoStatusTone.Success, modifier = Modifier.testTag("activities-saved"))
    }
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
            MoEmptyState("Aún no hay actuaciones", "Registra un trabajo y selecciona las parcelas donde se realiza.", icon = MoIcons.Activity)
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
                machines = state.machines,
                descriptionError = state.descriptionError,
                dateError = state.dateError,
                parcelsError = state.parcelsError,
                isSaving = state.isSaving,
                onSave = { draft -> onCreate(draft, false) },
                onSaveDraft = { draft -> onCreate(draft, true) },
                onCancel = { editor = false },
                initial = initialDraft,
                title = editorTitle,
            )
        }
    }
}

/**
 * The real "Registrar actuación" flow behind the Registrar (+) sheet.
 *
 * It is the same aggregate and the same editor the Farm detail uses: the only extra
 * step is resolving which Farm the work belongs to, because a global entry point has
 * no Farm in context. One Farm resolves itself.
 */
@Composable
fun RegisterActivityRoute(
    persistence: LocalPersistence,
    onActivitySelected: (UUID) -> Unit,
    preselectedFarmId: UUID? = null,
    onFarmPreselected: () -> Unit = {},
    /** UX-D: the type chosen in "Registrar hoy" (Riego, Tratamiento…); null lets the farmer pick. */
    presetType: ActivityType? = null,
) {
    val vm: RegisterActivityViewModel = viewModel(factory = viewModelFactory {
        initializer {
            RegisterActivityViewModel(persistence.farmRepository, persistence.workspaceRepository)
        }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    // Quick Add opened from a Farm's screen: that Farm is already the answer.
    LaunchedEffect(preselectedFarmId) {
        if (preselectedFarmId != null) {
            vm.selectFarm(preselectedFarmId)
            onFarmPreselected()
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("register-activity-root"),
        containerColor = MoCream,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.md),
        ) {
            Text(
                "Registrar actuación",
                style = MaterialTheme.typography.headlineLarge,
                color = MoOliveDark,
            )
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> MoErrorState(
                    "No pudimos abrir tus fincas",
                    state.error.orEmpty(),
                    onRetry = vm::retry,
                )
                state.farms.isEmpty() -> MoEmptyState(
                    "Aún no tienes fincas",
                    "Crea una finca en Mi Campo y podrás registrar actuaciones sobre sus parcelas.",
                    icon = MoIcons.Tree,
                )
                else -> {
                    val selectedFarmId = state.selectedFarmId
                    if (selectedFarmId == null) {
                        MoSectionHeader("¿En qué finca?")
                        state.farms.forEach { farm ->
                            FarmChoiceRow(farm) { vm.selectFarm(farm.id) }
                        }
                    } else {
                        val selectedFarm = state.farms.firstOrNull { it.id == selectedFarmId }
                        val changeFarm: (@Composable () -> Unit)? =
                            if (state.farms.size > 1) {
                                {
                                    TextButton(
                                        onClick = vm::changeFarm,
                                        modifier = Modifier.testTag("change-activity-farm"),
                                    ) { Text("Cambiar finca") }
                                }
                            } else {
                                null
                            }
                        MoSectionHeader(selectedFarm?.name ?: "Finca seleccionada", action = changeFarm)
                        FarmActivitiesRoute(
                            farmId = selectedFarmId,
                            persistence = persistence,
                            onActivitySelected = onActivitySelected,
                            startWithEditor = true,
                            // Today by default (editable); the type already chosen; the Farm in the title.
                            initialDraft = ActivityDraft(type = presetType ?: ActivityType.OBSERVATION, activityDate = LocalDate.now()),
                            editorTitle = listOfNotNull(presetType?.label() ?: "Nueva actuación", selectedFarm?.name).joinToString(" · "),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FarmChoiceRow(farm: Farm, onSelected: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("register-farm-option")
            .clickable(role = Role.Button, onClick = onSelected),
        colors = CardDefaults.cardColors(containerColor = MoWarmWhite),
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(Modifier.padding(MoSpacing.md)) {
            Text(farm.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${farm.parcelCount} parcelas",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
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
            Modifier.fillMaxWidth().padding(horizontal = MoSpacing.sm, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoIconBadge(activity.type.icon())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(activity.description, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    "${activity.type.label()} · ${activity.activityDate.format(ROW_DATE)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MoTextSecondary,
                    maxLines = 1,
                )
                Text(activity.targetsLabel(), style = MaterialTheme.typography.bodySmall, color = MoTextSecondary, maxLines = 1)
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
    /** Machines that can be named; empty hides nothing but the choice (Phase 15). */
    machines: List<MachineOption> = emptyList(),
) {
    var description by rememberSaveable(initial.description) { mutableStateOf(initial.description) }
    var date by rememberSaveable(initial.activityDate) { mutableStateOf(initial.activityDate?.toString().orEmpty()) }
    var notes by rememberSaveable(initial.notes) { mutableStateOf(initial.notes) }
    var cost by rememberSaveable(initial.costMinor) { mutableStateOf(Money.editable(initial.costMinor)) }
    var costError by rememberSaveable { mutableStateOf<String?>(null) }
    var type by rememberSaveable(initial.type) { mutableStateOf(initial.type.name) }
    var selected by rememberSaveable(initial.parcelIds) { mutableStateOf(initial.parcelIds.map(UUID::toString)) }
    // Deliberately not rememberSaveable: the sheet itself does not survive process death,
    // so saving the typed block alone would restore it into an editor that is not there.
    val detailFields = remember(initial.detail) {
        mutableStateMapOf<String, String>().apply { putAll(initial.detail.toFields()) }
    }
    // Selected machine id → the hours typed for it ("" when not given).
    val machineHours = remember(initial.machines) {
        mutableStateMapOf<String, String>().apply {
            initial.machines.forEach { use -> put(use.machineId.toString(), use.usageHours?.let(::editableHours).orEmpty()) }
        }
    }
    var machinesError by rememberSaveable { mutableStateOf<String?>(null) }
    // Phase 16: optional planning and reminders. Not rememberSaveable, like the typed block.
    var planning by remember(initial.planning, initial.reminders) {
        mutableStateOf(PlanningInput.of(initial.planning, initial.reminders))
    }
    var planningError by remember { mutableStateOf<PlanningInput.Result.Invalid?>(null) }
    fun readPlanning(): PlanningInput.Result.Ok? = when (val result = planning.read()) {
        is PlanningInput.Result.Ok -> result.also { planningError = null }
        is PlanningInput.Result.Invalid -> { planningError = result; null }
    }
    fun readMachines(): List<MachineUseInput>? {
        val uses = machineHours.entries.map { (id, hours) ->
            val value = hours.replace(',', '.').trim()
            if (value.isNotEmpty() && value.toDoubleOrNull() == null) {
                machinesError = "Escribe las horas como 3 o 3,5"
                return null
            }
            MachineUseInput(UUID.fromString(id), usageHours = value.toDoubleOrNull())
        }
        machinesError = null
        return uses.sortedBy { it.machineId.toString() }
    }

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
        MoDateInputField(
            date, { date = it }, "Fecha",
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
        ActivityTypedDetailFields(
            type = runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
            fields = detailFields,
        )
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
        // Phase 15: optional. An Activity never needs a machine, and hours are optional too.
        if (machines.isNotEmpty()) {
            MoSectionHeader("Maquinaria (opcional)")
            machinesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            machines.forEach { machine ->
                val key = machine.id.toString()
                val checked = key in machineHours
                Row(
                    Modifier.fillMaxWidth().testTag("activity-machine-option").clickable {
                        if (checked) machineHours.remove(key) else machineHours[key] = ""
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked, { value -> if (value) machineHours[key] = "" else machineHours.remove(key) })
                    Text(machine.name)
                }
                if (checked) {
                    MoTextField(
                        machineHours[key].orEmpty(),
                        { machineHours[key] = it; machinesError = null },
                        "Horas de ${machine.name} (opcional)",
                        modifier = Modifier.fillMaxWidth().testTag("activity-machine-hours"),
                    )
                }
            }
        }
        PlanningFields(
            input = planning,
            error = planningError,
            onChange = { planning = it; planningError = null },
        )
        MoTextField(notes, { notes = it }, "Notas")
        // D2: a convenience for the linked Expense, never a second number on the Activity.
        MoTextField(
            cost,
            { cost = it; costError = null },
            "Coste (opcional, €)",
            isError = costError != null,
            supportingText = costError ?: "Se anota en Gastos, una sola vez.",
            modifier = Modifier.testTag("activity-cost"),
        )
        MoPrimaryButton(
            "Guardar actuación",
            {
                val costMinor = Money.parseMinor(cost)
                if (cost.isNotBlank() && costMinor == null) {
                    costError = "Escribe un importe como 65 o 65,50"
                    return@MoPrimaryButton
                }
                val machineUses = readMachines() ?: return@MoPrimaryButton
                val planned = readPlanning() ?: return@MoPrimaryButton
                onSave(
                    ActivityDraft(
                        runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
                        runCatching { LocalDate.parse(date) }.getOrNull(),
                        description,
                        selected.map(UUID::fromString).toSet(),
                        notes,
                        buildActivityDetail(
                            runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
                            detailFields,
                        ),
                        costMinor,
                        machines = machineUses,
                        planning = planned.planning,
                        reminders = planned.reminders,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("save-activity"), enabled = !isSaving,
        )
        onSaveDraft?.let { saveDraft ->
            MoSecondaryButton(
                "Guardar borrador",
                {
                    val costMinor = Money.parseMinor(cost)
                    if (cost.isNotBlank() && costMinor == null) {
                        costError = "Escribe un importe como 65 o 65,50"
                        return@MoSecondaryButton
                    }
                    val machineUses = readMachines() ?: return@MoSecondaryButton
                    val planned = readPlanning() ?: return@MoSecondaryButton
                    saveDraft(
                        ActivityDraft(
                            runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
                            runCatching { LocalDate.parse(date) }.getOrNull(),
                            description,
                            selected.map(UUID::fromString).toSet(),
                            notes,
                            buildActivityDetail(
                                runCatching { ActivityType.valueOf(type) }.getOrDefault(ActivityType.OTHER),
                                detailFields,
                            ),
                            costMinor,
                            machines = machineUses,
                            planning = planned.planning,
                            reminders = planned.reminders,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("save-activity-draft"),
            )
        }
        MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ActivityDetailRoute(activityId: UUID, persistence: LocalPersistence) {
    val vm: ActivityDetailViewModel = viewModel(key = "activity-$activityId", factory = viewModelFactory {
        initializer { ActivityDetailViewModel(activityId, persistence.activityRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    ActivityDetailScreen(
        state,
        vm::update,
        vm::plan,
        vm::complete,
        vm::cancel,
        vm::reopen,
        vm::archive,
        attachmentContent = {
            AttachmentsRoute(
                owner = AttachmentOwner(AttachmentOwnerType.ACTIVITY, activityId),
                persistence = persistence,
                title = "Fotos y documentos",
            )
        },
    )
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
    attachmentContent: @Composable () -> Unit = {},
) {
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf(false) }
    Scaffold(Modifier.fillMaxSize().testTag("activity-detail-root"), containerColor = MoCream, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
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
                    // UI polish v2: one first card with what the farmer needs at a glance.
                    ActivityHeaderCard(activity)
                    activity.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MoTextSecondary) }
                    if (activity.targets.size > 1) {
                        MoSectionHeader("Parcelas afectadas")
                    }
                    activity.targets.forEach { target ->
                        MoCompactListItem(
                            title = target.parcelName,
                            subtitle = target.areaAffectedM2?.let { "Superficie trabajada: ${hectaresLabel(it)}" },
                            icon = MoIcons.Parcels,
                            modifier = Modifier.testTag("activity-target"),
                        )
                    }
                    if (activity.targets.isEmpty()) {
                        MoEmptyState(
                            "Sin parcelas todavía",
                            "Edita la actuación y elige dónde se hace el trabajo.",
                            icon = MoIcons.Parcels,
                        )
                    }

                    activity.detail?.let { ActivityDetailSummary(it) }
                    if (activity.machines.isNotEmpty()) {
                        MoSectionHeader("Maquinaria")
                        activity.machines.forEach { machine ->
                            Text(
                                listOfNotNull(
                                    machine.name + if (machine.archived) " (retirada)" else "",
                                    machine.hoursUsed?.let { "${editableHours(it)} h" },
                                ).joinToString(" · "),
                                modifier = Modifier.testTag("activity-machine"),
                            )
                        }
                    }
                    PlanningSummary(activity.planning, activity.reminders)
                    activity.costMinor?.let { cost ->
                        MoSummaryMetric(
                            "Coste",
                            Money.format(cost),
                            Modifier.fillMaxWidth().testTag("activity-cost-summary"),
                            icon = MoIcons.Euro,
                            supportingText = "Anotado en Gastos",
                        )
                    }

                    // Principal / secundaria / destructiva — never three large green buttons.
                    when (activity.status) {
                        ActivityStatus.DRAFT -> {
                            MoPrimaryButton("Planificar", { confirmation = "plan" }, modifier = Modifier.fillMaxWidth().testTag("plan-activity"), enabled = !state.isSaving)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                MoSecondaryButton("Editar borrador", { editor = true }, modifier = Modifier.weight(1f).testTag("edit-activity"), enabled = !state.isSaving)
                                MoDestructiveButton("Archivar borrador", { confirmation = "archive" }, modifier = Modifier.weight(1f).testTag("archive-activity"))
                            }
                        }
                        ActivityStatus.PLANNED -> {
                            MoPrimaryButton("Marcar completada", { confirmation = "complete" }, modifier = Modifier.fillMaxWidth().testTag("complete-activity"), enabled = !state.isSaving)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                MoSecondaryButton("Editar", { editor = true }, modifier = Modifier.weight(1f).testTag("edit-activity"), enabled = !state.isSaving)
                                MoDestructiveButton("Cancelar actuación", { confirmation = "cancel" }, modifier = Modifier.weight(1f).testTag("cancel-activity"))
                            }
                        }
                        ActivityStatus.COMPLETED -> {
                            Text("Registro protegido", style = MaterialTheme.typography.titleSmall, color = MoTextSecondary)
                            MoSecondaryButton("Reabrir actuación", { confirmation = "reopen" }, modifier = Modifier.fillMaxWidth().testTag("reopen-activity"))
                        }
                        ActivityStatus.CANCELLED -> {
                            Text("Actuación cancelada", style = MaterialTheme.typography.titleSmall, color = MoTextSecondary)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
                                MoSecondaryButton("Reabrir", { confirmation = "reopen" }, modifier = Modifier.weight(1f).testTag("reopen-activity"))
                                MoDestructiveButton("Archivar", { confirmation = "archive" }, modifier = Modifier.weight(1f).testTag("archive-activity"))
                            }
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    attachmentContent()
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
                    detail = activity.detail,
                    costMinor = activity.costMinor,
                    machines = activity.machines.map { MachineUseInput(it.machineId, it.startHours, it.endHours, it.usageHours) },
                    planning = activity.planning,
                    reminders = activity.reminders.map { it.toRequest() },
                ),
                title = "Editar actuación",
                // A retired machine the Activity already named stays choosable here only.
                machines = state.machines + activity.machines.filter { it.archived }
                    .map { MachineOption(it.machineId, "${it.name} (retirada)", it.category) },
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
                val confirm = {
                    when (confirmation) {
                        "plan" -> onPlan()
                        "complete" -> onComplete()
                        "cancel" -> onCancelActivity()
                        "reopen" -> onReopen()
                        "archive" -> onArchive()
                    }
                    confirmation = null
                }
                if (confirmation == "cancel" || confirmation == "archive") {
                    MoDestructiveButton("Confirmar", confirm, modifier = Modifier.fillMaxWidth().testTag("confirm-activity-action"))
                } else {
                    MoPrimaryButton("Confirmar", confirm, modifier = Modifier.fillMaxWidth().testTag("confirm-activity-action"))
                }
                MoTertiaryButton("Volver", { confirmation = null }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun Activity.targetsLabel(): String = when (targets.size) {
    0 -> "Sin parcelas"
    1 -> targets.single().parcelName
    else -> "${targets.size} parcelas"
}

/**
 * The typed agronomic block, and only the one that belongs to the chosen type.
 *
 * This is what keeps the editor from becoming a giant form: the common header is always
 * there, and underneath it exactly one block appears — pruning, fertilisation, treatment,
 * soil work, irrigation, maintenance or incident. Observation and Other show none,
 * because the contract gives them no structured fields to show.
 */
@Composable
private fun ActivityTypedDetailFields(type: ActivityType, fields: SnapshotStateMap<String, String>) {
    if (!type.hasTypedDetail()) return
    MoSectionHeader(type.detailSectionTitle())
    Column(
        Modifier.fillMaxWidth().testTag("activity-detail-block"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        when (type) {
            ActivityType.PRUNING -> {
                DetailField(fields, ActivityDetailFields.PRUNING_TYPE, "Tipo de poda")
                DetailField(fields, ActivityDetailFields.WORKER_COUNT, "Nº de operarios")
                DetailField(fields, ActivityDetailFields.HOURS, "Horas")
                DetailField(fields, ActivityDetailFields.RESIDUE_MANAGEMENT, "Gestión de restos")
            }
            ActivityType.FERTILIZATION -> {
                DetailField(fields, ActivityDetailFields.PRODUCT_NAME, "Producto")
                DetailField(fields, ActivityDetailFields.TOTAL_QUANTITY, "Cantidad total")
                DetailField(fields, ActivityDetailFields.UNIT, "Unidad")
                DetailField(fields, ActivityDetailFields.DOSE_VALUE, "Dosis")
                DetailField(fields, ActivityDetailFields.DOSE_UNIT, "Unidad de dosis")
                DetailField(fields, ActivityDetailFields.APPLICATION_METHOD, "Método de aplicación")
            }
            ActivityType.PHYTOSANITARY -> {
                DetailField(fields, ActivityDetailFields.PRODUCT_NAME, "Producto")
                DetailField(fields, ActivityDetailFields.ACTIVE_SUBSTANCE, "Materia activa")
                DetailField(fields, ActivityDetailFields.TOTAL_QUANTITY, "Cantidad total")
                DetailField(fields, ActivityDetailFields.UNIT, "Unidad")
                DetailField(fields, ActivityDetailFields.DOSE_VALUE, "Dosis")
                DetailField(fields, ActivityDetailFields.DOSE_UNIT, "Unidad de dosis")
                DetailField(fields, ActivityDetailFields.REASON, "Motivo")
                DetailField(fields, ActivityDetailFields.EQUIPMENT_TEXT, "Equipo")
            }
            ActivityType.SOIL_WORK -> {
                DetailField(fields, ActivityDetailFields.WORK_TYPE, "Tipo de labor")
                DetailField(fields, ActivityDetailFields.METHOD, "Método")
            }
            ActivityType.IRRIGATION -> {
                DetailField(fields, ActivityDetailFields.DURATION_MINUTES, "Duración (minutos)")
                DetailField(fields, ActivityDetailFields.VOLUME_M3, "Volumen (m³)")
                DetailField(fields, ActivityDetailFields.SECTOR_TEXT, "Sector")
                DetailField(fields, ActivityDetailFields.SYSTEM_TEXT, "Sistema")
                // A tariff snapshot, kept with the irrigation that used it. It is an
                // estimate for the farmer's own reading: the expense ledger remains the
                // authoritative cost, and nothing here is summed into a financial total.
                Text(
                    "Tarifa (opcional, histórica)",
                    style = MaterialTheme.typography.titleSmall,
                )
                DetailChoice(
                    fields,
                    ActivityDetailFields.PRICE_BASIS,
                    IrrigationPricingBasis.entries.map { it.name to it.label() },
                )
                DetailField(fields, ActivityDetailFields.UNIT_PRICE, "Precio unitario (€)")
                DetailField(fields, ActivityDetailFields.PRICED_QUANTITY, "Cantidad facturada")
                DetailField(fields, ActivityDetailFields.PRICE_DATE, "Fecha de tarifa (AAAA-MM-DD)")
            }
            ActivityType.MAINTENANCE -> {
                DetailField(fields, ActivityDetailFields.MAINTENANCE_TYPE, "Tipo de mantenimiento")
                DetailField(fields, ActivityDetailFields.ASSET_TEXT, "Elemento o equipo")
            }
            ActivityType.INCIDENT -> {
                DetailField(fields, ActivityDetailFields.CATEGORY, "Categoría")
                Text("Gravedad", style = MaterialTheme.typography.titleSmall)
                DetailChoice(
                    fields,
                    ActivityDetailFields.SEVERITY,
                    IncidentSeverity.entries.map { it.name to it.label() },
                )
                Text("Estado", style = MaterialTheme.typography.titleSmall)
                DetailChoice(
                    fields,
                    ActivityDetailFields.INCIDENT_STATE,
                    IncidentState.entries.map { it.name to it.label() },
                )
                DetailField(fields, ActivityDetailFields.ACTION_TAKEN, "Actuación realizada")
            }
            ActivityType.OBSERVATION, ActivityType.OTHER, ActivityType.HARVEST_DAY -> Unit
        }
    }
}

@Composable
private fun DetailField(fields: SnapshotStateMap<String, String>, key: String, label: String) {
    MoTextField(
        fields[key].orEmpty(),
        { fields[key] = it },
        label,
        modifier = Modifier.fillMaxWidth().testTag("detail-$key"),
    )
}

@Composable
private fun DetailChoice(
    fields: SnapshotStateMap<String, String>,
    key: String,
    options: List<Pair<String, String>>,
) {
    options.forEach { (value, label) ->
        val checked = fields[key] == value
        Row(
            Modifier.fillMaxWidth().testTag("detail-$key-option")
                .clickable(role = Role.Checkbox) { fields[key] = value }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked, { fields[key] = value })
            Text(label)
        }
    }
}

/** Shows the typed block of an Activity that already has one, without a second header. */
@Composable
private fun ActivityDetailSummary(detail: ActivityDetail) {
    MoSectionHeader(detail.type.detailSectionTitle())
    Column(
        Modifier.fillMaxWidth().testTag("activity-detail-summary"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        detail.toFields().forEach { (key, value) ->
            Text(
                "${key.detailFieldLabel()}: ${value.detailValueLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MoTextSecondary,
                modifier = Modifier.testTag("activity-detail-value"),
            )
        }
    }
}

private fun ActivityType.detailSectionTitle() = when (this) {
    ActivityType.PRUNING -> "Datos de poda"
    ActivityType.FERTILIZATION -> "Datos de abonado"
    ActivityType.PHYTOSANITARY -> "Datos del tratamiento"
    ActivityType.SOIL_WORK -> "Datos de la labor"
    ActivityType.IRRIGATION -> "Datos de riego"
    ActivityType.MAINTENANCE -> "Datos de mantenimiento"
    ActivityType.INCIDENT -> "Datos de la incidencia"
    ActivityType.OBSERVATION, ActivityType.OTHER, ActivityType.HARVEST_DAY -> "Datos"
}

private fun String.detailFieldLabel() = when (this) {
    ActivityDetailFields.PRUNING_TYPE -> "Tipo de poda"
    ActivityDetailFields.WORKER_COUNT -> "Operarios"
    ActivityDetailFields.HOURS -> "Horas"
    ActivityDetailFields.RESIDUE_MANAGEMENT -> "Gestión de restos"
    ActivityDetailFields.PRODUCT_NAME -> "Producto"
    ActivityDetailFields.ACTIVE_SUBSTANCE -> "Materia activa"
    ActivityDetailFields.TOTAL_QUANTITY -> "Cantidad total"
    ActivityDetailFields.UNIT -> "Unidad"
    ActivityDetailFields.DOSE_VALUE -> "Dosis"
    ActivityDetailFields.DOSE_UNIT -> "Unidad de dosis"
    ActivityDetailFields.APPLICATION_METHOD -> "Método de aplicación"
    ActivityDetailFields.REASON -> "Motivo"
    ActivityDetailFields.EQUIPMENT_TEXT -> "Equipo"
    ActivityDetailFields.WORK_TYPE -> "Tipo de labor"
    ActivityDetailFields.METHOD -> "Método"
    ActivityDetailFields.DURATION_MINUTES -> "Duración (min)"
    ActivityDetailFields.VOLUME_M3 -> "Volumen (m³)"
    ActivityDetailFields.SECTOR_TEXT -> "Sector"
    ActivityDetailFields.SYSTEM_TEXT -> "Sistema"
    ActivityDetailFields.PRICE_BASIS -> "Base de tarifa"
    ActivityDetailFields.UNIT_PRICE -> "Precio unitario (€)"
    ActivityDetailFields.PRICED_QUANTITY -> "Cantidad facturada"
    ActivityDetailFields.PRICE_DATE -> "Fecha de tarifa"
    ActivityDetailFields.MAINTENANCE_TYPE -> "Tipo de mantenimiento"
    ActivityDetailFields.ASSET_TEXT -> "Elemento"
    ActivityDetailFields.CATEGORY -> "Categoría"
    ActivityDetailFields.SEVERITY -> "Gravedad"
    ActivityDetailFields.INCIDENT_STATE -> "Estado"
    ActivityDetailFields.ACTION_TAKEN -> "Actuación"
    else -> this
}

private fun String.detailValueLabel(): String =
    runCatching { IncidentSeverity.valueOf(this).label() }
        .recoverCatching { IncidentState.valueOf(this).label() }
        .recoverCatching { IrrigationPricingBasis.valueOf(this).label() }
        .getOrDefault(this)

private fun IncidentSeverity.label() = when (this) {
    IncidentSeverity.LOW -> "Baja"
    IncidentSeverity.MEDIUM -> "Media"
    IncidentSeverity.HIGH -> "Alta"
    IncidentSeverity.CRITICAL -> "Crítica"
}

private fun IncidentState.label() = when (this) {
    IncidentState.OPEN -> "Abierta"
    IncidentState.MONITORING -> "En observación"
    IncidentState.RESOLVED -> "Resuelta"
}

private fun IrrigationPricingBasis.label() = when (this) {
    IrrigationPricingBasis.PER_M3 -> "Por m³"
    IrrigationPricingBasis.PER_HOUR -> "Por hora"
    IrrigationPricingBasis.PER_EVENT -> "Por riego"
    IrrigationPricingBasis.PER_HECTARE -> "Por hectárea"
    IrrigationPricingBasis.INVOICE_TOTAL -> "Total factura"
    IrrigationPricingBasis.OTHER -> "Otra"
}

internal fun ActivityStatus.label() = when (this) {
    ActivityStatus.DRAFT -> "Borrador"
    ActivityStatus.PLANNED -> "Planificada"
    ActivityStatus.COMPLETED -> "Completada"
    ActivityStatus.CANCELLED -> "Cancelada"
}

internal fun ActivityStatus.tone() = when (this) {
    ActivityStatus.DRAFT -> MoStatusTone.Neutral
    ActivityStatus.PLANNED -> MoStatusTone.Info
    ActivityStatus.COMPLETED -> MoStatusTone.Success
    ActivityStatus.CANCELLED -> MoStatusTone.Warning
}

internal fun ActivityType.label() = when (this) {
    ActivityType.OBSERVATION -> "Observación"
    ActivityType.PRUNING -> "Poda"
    ActivityType.SOIL_WORK -> "Labores de suelo"
    ActivityType.FERTILIZATION -> "Abonado"
    ActivityType.PHYTOSANITARY -> "Tratamiento"
    ActivityType.IRRIGATION -> "Riego"
    ActivityType.MAINTENANCE -> "Mantenimiento"
    ActivityType.INCIDENT -> "Incidencia"
    ActivityType.OTHER -> "Otro"
    ActivityType.HARVEST_DAY -> "Jornada de cosecha"
}

/** "3", "3,5": hours as a farmer writes them. */
internal fun editableHours(hours: Double): String =
    java.math.BigDecimal.valueOf(hours).stripTrailingZeros().toPlainString().replace('.', ',')

/**
 * UI polish v2: the Activity's first card — type, state, date, farm parcels, planned
 * hour/duration and reminder — so the detail reads in one glance.
 */
@Composable
private fun ActivityHeaderCard(activity: Activity) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth().testTag("activity-header"),
        shape = com.isivoltpro.maginaolivo.ui.theme.MoShape.card,
        color = MoWarmWhite,
        border = BorderStroke(1.dp, MoOutline),
    ) {
        Column(Modifier.padding(MoSpacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(activity.description, style = MaterialTheme.typography.headlineMedium, color = MoOliveDark, modifier = Modifier.weight(1f))
                MoStatusChip(activity.status.label(), tone = activity.status.tone())
            }
            HeaderLine(activity.type.icon(), activity.type.label())
            HeaderLine(MoIcons.Calendar, activity.activityDate.format(HEADER_DATE).replaceFirstChar { it.titlecase(SPANISH_LOCALE) })
            activity.targets.takeIf { it.isNotEmpty() }?.let { targets ->
                HeaderLine(MoIcons.Parcels, if (targets.size == 1) targets.single().parcelName else "${targets.size} parcelas")
            }
            // Time, people and reminders are told once, in the Planificación block below.
        }
    }
}

@Composable
private fun HeaderLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.xs)) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = com.isivoltpro.maginaolivo.ui.components.MoIconTone.of(icon).tint, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = com.isivoltpro.maginaolivo.ui.theme.MoInk)
    }
}

private val SPANISH_LOCALE: java.util.Locale = java.util.Locale.forLanguageTag("es-ES")
private val ROW_DATE: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", SPANISH_LOCALE)
private val HEADER_DATE: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", SPANISH_LOCALE)

private fun hectaresLabel(areaM2: Double): String =
    "${java.text.NumberFormat.getNumberInstance(SPANISH_LOCALE).apply { maximumFractionDigits = 2 }.format(areaM2 / 10_000)} ha"
