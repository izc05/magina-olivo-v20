package com.isivoltpro.maginaolivo.feature.expenses

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.ocr.OcrStatus
import com.isivoltpro.maginaolivo.domain.organization.Organization
import com.isivoltpro.maginaolivo.domain.organization.OrganizationDraft
import com.isivoltpro.maginaolivo.domain.organization.OrganizationRole
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.components.MoConfirmationSheet
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoTextField
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.util.UUID

@Composable
fun DocumentReviewRoute(
    extractionId: UUID,
    persistence: LocalPersistence,
    onExpenseCreated: (UUID) -> Unit,
    onClosed: () -> Unit,
) {
    val viewModel: DocumentReviewViewModel = viewModel(
        key = "document-$extractionId",
        factory = viewModelFactory {
            initializer {
                DocumentReviewViewModel(
                    extractionId,
                    persistence.documentOcrRepository,
                    persistence.attachmentRepository,
                    relationSource(persistence),
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdExpenseId) { state.createdExpenseId?.let(onExpenseCreated) }
    LaunchedEffect(state.closed) { if (state.closed) onClosed() }
    DocumentReviewScreen(
        state = state,
        onRead = viewModel::read,
        onCreateDraft = viewModel::createDraft,
        onKeep = viewModel::keepWithoutExpense,
        onDiscard = viewModel::discard,
        onFarmSelected = viewModel::selectFarm,
    )
}

/**
 * The review step of `RC1.2-PRODUCT-LOCK` §5: original file, what was read, what the person
 * corrects, and an explicit command. Nothing here posts money.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReviewScreen(
    state: DocumentReviewUiState,
    onRead: () -> Unit,
    onCreateDraft: (ExpenseForm) -> Unit,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onFarmSelected: (UUID?) -> Unit,
) {
    var rawTextVisible by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    Scaffold(Modifier.fillMaxSize().testTag("document-review-root"), containerColor = MoCream, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            val extraction = state.extraction
            Column(Modifier.padding(horizontal = MoSpacing.screen), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                Spacer(Modifier.height(MoSpacing.md))
                Text("Revisar documento", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
                when {
                    state.isLoading -> CircularProgressIndicator()
                    extraction == null -> MoErrorState("Documento no disponible", state.error ?: "No está en este dispositivo.")
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            Text(extraction.documentType.label(), style = MaterialTheme.typography.titleMedium, color = MoTextSecondary)
                            MoStatusChip(
                                if (state.isReading) OcrStatus.PENDING.label() else extraction.status.label(),
                                tone = if (state.isReading) OcrStatus.PENDING.tone() else extraction.status.tone(),
                                modifier = Modifier.testTag("document-status"),
                            )
                        }
                        DocumentPreview(state)
                        if (state.isReading) {
                            Row(horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.height(24.dp))
                                Text("Leyendo el documento en este dispositivo…", color = MoTextSecondary)
                            }
                        }
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("document-error")) }
                        if (extraction.status == OcrStatus.FAILED || extraction.status == OcrStatus.NEEDS_REVIEW) {
                            TextButton(onClick = onRead, enabled = !state.isReading, modifier = Modifier.testTag("document-read-again")) {
                                Text("Leer de nuevo")
                            }
                        }
                        extraction.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
                            TextButton(onClick = { rawTextVisible = !rawTextVisible }) {
                                Text(if (rawTextVisible) "Ocultar texto leído" else "Ver texto leído")
                            }
                            if (rawTextVisible) {
                                Text(raw, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("document-raw-text"))
                            }
                        }
                    }
                }
            }

            if (extraction != null && extraction.status != OcrStatus.CONFIRMED && !state.isReading &&
                extraction.status != OcrStatus.PENDING
            ) {
                val initial = remember(extraction.id, extraction.status, extraction.proposal) { extraction.toReviewForm() }
                ExpenseEditor(
                    title = "Datos para el gasto",
                    subtitle = "Lo que leímos aparece rellenado: revísalo y corrígelo. Se creará un borrador que no suma hasta que lo confirmes.",
                    initial = initial,
                    options = state.options,
                    errors = state.formErrors,
                    isSaving = state.isSaving,
                    saveText = "Crear borrador de gasto",
                    amountLabel = "Total (€)",
                    onFarmSelected = onFarmSelected,
                    onSave = onCreateDraft,
                    onCancel = { confirmDiscard = true },
                    scrollable = false,
                    extraActions = {
                        MoSecondaryButton(
                            "Guardar solo como documento",
                            onKeep,
                            Modifier.fillMaxWidth().testTag("document-keep"),
                            enabled = !state.isSaving,
                        )
                    },
                )
            } else if (extraction?.status == OcrStatus.CONFIRMED) {
                Column(Modifier.padding(horizontal = MoSpacing.screen)) {
                    Text("Este documento ya está revisado.", color = MoTextSecondary)
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    if (confirmDiscard) {
        ModalBottomSheet(onDismissRequest = { confirmDiscard = false }) {
            MoConfirmationSheet(
                title = "Descartar documento",
                body = "Se quitará el documento y lo que se leyó de él. No se creará ningún gasto.",
                confirmText = "Descartar",
                onConfirm = { confirmDiscard = false; onDiscard() },
                onCancel = { confirmDiscard = false },
                modifier = Modifier.padding(horizontal = MoSpacing.md).testTag("document-discard-sheet"),
            )
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

@Composable
private fun DocumentPreview(state: DocumentReviewUiState) {
    val attachment = state.attachment
    val model = attachment?.thumbnailUri ?: attachment?.localUri?.takeIf {
        attachment.kind == com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind.PHOTO
    }
    Box(
        Modifier.fillMaxWidth().height(220.dp).clip(MoShape.card).background(MoSurfaceSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(attachment?.displayName ?: "Documento", color = MoTextSecondary)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = "Documento original",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun OrganizationsRoute(persistence: LocalPersistence) {
    val viewModel: OrganizationsViewModel = viewModel(
        key = "organizations",
        factory = viewModelFactory { initializer { OrganizationsViewModel(persistence.organizationRepository) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    OrganizationsScreen(state, viewModel::save, viewModel::archive, viewModel::clearErrors)
}

/**
 * One list of cooperatives, mills, suppliers and irrigation communities. An organization
 * that plays two roles is one entry with two roles, chosen from every flow that needs it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationsScreen(
    state: OrganizationsUiState,
    onSave: (UUID?, OrganizationDraft) -> Unit,
    onArchive: (UUID) -> Unit,
    onEditorClosed: () -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.message) { if (state.message != null) editing = null }

    Scaffold(Modifier.fillMaxSize().testTag("organizations-root"), containerColor = MoCream, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Text("Proveedores y organizaciones", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text(
                "Cooperativas, almazaras, proveedores y comunidades de regantes. Cada una se guarda una sola vez.",
                color = MoTextSecondary,
            )
            MoPrimaryButton("Añadir organización", { editing = NEW }, Modifier.fillMaxWidth().testTag("add-organization"))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when {
                state.isLoading -> CircularProgressIndicator()
                state.organizations.isEmpty() -> MoEmptyState(
                    "Aún no hay organizaciones",
                    "Añade tu cooperativa o tus proveedores para elegirlos al anotar gastos.",
                    icon = MoIcons.People,
                )
                else -> state.organizations.forEach { organization ->
                    OrganizationRow(organization, onEdit = { editing = organization.id.toString() })
                }
            }
            Spacer(Modifier.height(MoSpacing.xl))
        }
    }

    editing?.let { key ->
        val current = state.organizations.firstOrNull { it.id.toString() == key }
        ModalBottomSheet(onDismissRequest = { editing = null; onEditorClosed() }) {
            OrganizationEditor(
                initial = current,
                state = state,
                onSave = { draft -> onSave(current?.id, draft) },
                onArchive = current?.let { { onArchive(it.id); editing = null } },
                onCancel = { editing = null; onEditorClosed() },
            )
        }
    }
}

@Composable
private fun OrganizationRow(organization: Organization, onEdit: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MoShape.card).background(MoSurfaceSoft)
            .padding(MoSpacing.md).testTag("organization-row"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs),
    ) {
        Text(organization.name, style = MaterialTheme.typography.titleMedium, color = MoOliveDark)
        Text(
            organization.roles.sortedBy { it.ordinal }.joinToString(" · ") { it.label() },
            style = MaterialTheme.typography.bodyMedium,
            color = MoTextSecondary,
        )
        organization.municipality?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MoTextSecondary) }
        TextButton(onClick = onEdit) { Text("Editar") }
    }
}

@Composable
private fun OrganizationEditor(
    initial: Organization?,
    state: OrganizationsUiState,
    onSave: (OrganizationDraft) -> Unit,
    onArchive: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var roles by rememberSaveable { mutableStateOf(initial?.roles.orEmpty().map { it.name }) }
    var municipality by rememberSaveable { mutableStateOf(initial?.municipality.orEmpty()) }
    var province by rememberSaveable { mutableStateOf(initial?.province.orEmpty()) }
    var taxId by rememberSaveable { mutableStateOf(initial?.taxId.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(initial?.phone.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
    ) {
        Text(if (initial == null) "Nueva organización" else "Editar organización", style = MaterialTheme.typography.headlineSmall)
        MoTextField(
            name, { name = it }, "Nombre",
            isError = state.nameError != null, supportingText = state.nameError,
            modifier = Modifier.fillMaxWidth().testTag("organization-name"),
        )
        MoSectionHeader("Qué es para ti")
        state.rolesError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OrganizationRole.entries.forEach { role ->
            val checked = role.name in roles
            Row(
                Modifier.fillMaxWidth().testTag("organization-role-${role.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked, { value -> roles = if (value) roles + role.name else roles - role.name })
                Text(role.label())
            }
        }
        MoTextField(municipality, { municipality = it }, "Municipio (opcional)", modifier = Modifier.fillMaxWidth())
        MoTextField(province, { province = it }, "Provincia (opcional)", modifier = Modifier.fillMaxWidth())
        MoTextField(taxId, { taxId = it }, "NIF/CIF (opcional)", modifier = Modifier.fillMaxWidth())
        MoTextField(phone, { phone = it }, "Teléfono (opcional)", modifier = Modifier.fillMaxWidth())
        MoTextField(notes, { notes = it }, "Notas", singleLine = false, modifier = Modifier.fillMaxWidth())
        MoPrimaryButton(
            "Guardar",
            {
                onSave(
                    OrganizationDraft(
                        name = name,
                        roles = roles.mapNotNull { key -> OrganizationRole.entries.firstOrNull { it.name == key } }.toSet(),
                        taxId = taxId,
                        municipality = municipality,
                        province = province,
                        phone = phone,
                        notes = notes,
                    ),
                )
            },
            Modifier.fillMaxWidth().testTag("save-organization"),
            enabled = !state.isSaving,
        )
        onArchive?.let { MoSecondaryButton("Archivar", it, Modifier.fillMaxWidth()) }
        MoTertiaryButton("Cancelar", onCancel, Modifier.fillMaxWidth())
        Spacer(Modifier.height(MoSpacing.lg))
    }
}

private const val NEW = "new"

