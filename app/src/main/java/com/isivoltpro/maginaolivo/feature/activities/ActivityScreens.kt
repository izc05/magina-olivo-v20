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
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoOutline
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import com.isivoltpro.maginaolivo.ui.theme.MoWarmWhite
import java.time.LocalDate
import java.util.UUID

@Composable
fun FarmActivitiesRoute(
    farmId: UUID,
    persistence: LocalPersistence,
    onActivitySelected: (UUID) -> Unit,
    startWithEditor: Boolean = false,
) {
    val vm: FarmActivitiesViewModel = viewModel(key = "farm-activities-$farmId", factory = viewModelFactory {
        initializer { FarmActivitiesViewModel(farmId, persistence.activityRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    FarmActivitiesSection(state, onActivitySelected, vm::create, startWithEditor)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmActivitiesSection(
    state: FarmActivitiesUiState,
    onActivitySelected: (UUID) -> Unit,
    onCreate: (ActivityDraft, Boolean) -> Unit,
    startWithEditor: Boolean = false,
) {
    var editor by rememberSaveable { mutableStateOf(startWithEditor) }
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

/**
 * The real "Registrar actuación" flow behind the Registrar (+) sheet.
 *
 * It is the same aggregate and the same editor the Farm detail uses: the only extra
 * step is resolving which Farm the work belongs to, because a global entry point has
 * no Farm in context. One Farm resolves itself.
 */
@Composable
fun RegisterActivityRoute(persistence: LocalPersistence, onActivitySelected: (UUID) -> Unit) {
    val vm: RegisterActivityViewModel = viewModel(factory = viewModelFactory {
        initializer {
            RegisterActivityViewModel(persistence.farmRepository, persistence.workspaceRepository)
        }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("register-activity-root"),
        containerColor = MoCream,
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
                    "Crea una finca en Mi Olivar y podrás registrar actuaciones sobre sus parcelas.",
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
    var cost by rememberSaveable(initial.costMinor) { mutableStateOf(Money.editable(initial.costMinor)) }
    var costError by rememberSaveable { mutableStateOf<String?>(null) }
    var type by rememberSaveable(initial.type) { mutableStateOf(initial.type.name) }
    var selected by rememberSaveable(initial.parcelIds) { mutableStateOf(initial.parcelIds.map(UUID::toString)) }
    // Deliberately not rememberSaveable: the sheet itself does not survive process death,
    // so saving the typed block alone would restore it into an editor that is not there.
    val detailFields = remember(initial.detail) {
        mutableStateMapOf<String, String>().apply { putAll(initial.detail.toFields()) }
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

                    activity.detail?.let { ActivityDetailSummary(it) }
                    activity.costMinor?.let { cost ->
                        MoMetricCard(
                            "Coste",
                            Money.format(cost),
                            Modifier.fillMaxWidth().testTag("activity-cost-summary"),
                            supportingText = "Anotado en Gastos",
                        )
                    }

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
            ActivityType.OBSERVATION, ActivityType.OTHER -> Unit
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
    ActivityType.OBSERVATION, ActivityType.OTHER -> "Datos"
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
