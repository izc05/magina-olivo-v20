package com.isivoltpro.maginaolivo.feature.attachments

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.attachment.Attachment
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentKind
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentUploadState
import com.isivoltpro.maginaolivo.ui.components.MoBottomActionSheet
import com.isivoltpro.maginaolivo.ui.components.MoConfirmationSheet
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSecondaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.components.MoStatusChip
import com.isivoltpro.maginaolivo.ui.components.MoStatusTone
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceSoft
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Composable
fun AttachmentsRoute(
    owner: AttachmentOwner,
    persistence: LocalPersistence,
    title: String = "Documentos y fotos",
) {
    val viewModel: AttachmentsViewModel = viewModel(
        key = "attachments-${owner.type}-${owner.id}",
        factory = viewModelFactory {
            initializer { AttachmentsViewModel(owner, persistence.attachmentRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    AttachmentsSection(
        state = state,
        title = title,
        onPicked = viewModel::attach,
        onRemove = viewModel::remove,
        onProblem = viewModel::reportProblem,
    )
}

/**
 * S26 / S91: a contextual attachment list with its add, open and delete actions. It is a
 * section, not a screen, so it lives inside the owner's detail without a new root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentsSection(
    state: AttachmentsUiState,
    onPicked: (String) -> Unit,
    onRemove: (UUID) -> Unit,
    onProblem: (String) -> Unit,
    title: String = "Documentos y fotos",
) {
    val context = LocalContext.current
    var chooserVisible by rememberSaveable { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = pendingCapture
        pendingCapture = null
        if (saved && target != null) onPicked(target)
    }

    MoSectionHeader(
        title,
        action = {
            TextButton(
                onClick = { chooserVisible = true },
                enabled = !state.isSaving,
                modifier = Modifier.testTag("add-attachment"),
            ) { Text("Añadir") }
        },
    )
    when {
        state.isLoading -> CircularProgressIndicator()
        state.attachments.isEmpty() -> MoEmptyState(
            "Sin documentos ni fotos",
            "Haz una foto o añade un PDF o una imagen. Se guarda primero en este dispositivo.",
        )
        else -> state.attachments.forEach { attachment ->
            AttachmentRow(
                attachment = attachment,
                onOpen = {
                    if (!openAttachment(context, attachment)) {
                        onProblem("No hay ninguna aplicación para abrir este archivo")
                    }
                },
                onRemove = { pendingRemoval = attachment.id.toString() },
            )
        }
    }
    if (state.isSaving) {
        Text("Guardando adjunto…", color = MoTextSecondary, modifier = Modifier.testTag("attachment-saving"))
    }
    state.message?.let { Text(it, color = MoTextSecondary, modifier = Modifier.testTag("attachment-message")) }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("attachment-error")) }

    if (chooserVisible) {
        ModalBottomSheet(onDismissRequest = { chooserVisible = false }) {
            MoBottomActionSheet(
                title = "Añadir adjunto",
                body = "La copia se guarda en este dispositivo y se subirá cuando haya sincronización.",
                modifier = Modifier.padding(horizontal = MoSpacing.md),
            ) {
                MoPrimaryButton(
                    text = "Hacer foto",
                    onClick = {
                        chooserVisible = false
                        val target = runCatching { createCaptureUri(context) }.getOrNull()
                        if (target == null) {
                            onProblem("No se pudo preparar la cámara")
                        } else {
                            pendingCapture = target.toString()
                            try {
                                camera.launch(target)
                            } catch (error: ActivityNotFoundException) {
                                pendingCapture = null
                                onProblem("No hay ninguna cámara disponible")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("attachment-camera"),
                )
                MoSecondaryButton(
                    text = "Elegir PDF o imagen",
                    onClick = {
                        chooserVisible = false
                        try {
                            documentPicker.launch(AttachmentKind.PICKER_MIME_TYPES)
                        } catch (error: ActivityNotFoundException) {
                            onProblem("No hay ningún selector de archivos disponible")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("attachment-picker"),
                )
            }
            Spacer(Modifier.height(MoSpacing.md))
        }
    }

    val removal = pendingRemoval?.let { id -> state.attachments.firstOrNull { it.id.toString() == id } }
    if (removal != null) {
        ModalBottomSheet(onDismissRequest = { pendingRemoval = null }) {
            MoConfirmationSheet(
                title = "Eliminar adjunto",
                body = "Se quitará «${removal.displayName}» de este dispositivo. No se puede deshacer.",
                confirmText = "Eliminar",
                onConfirm = {
                    pendingRemoval = null
                    onRemove(removal.id)
                },
                onCancel = { pendingRemoval = null },
                modifier = Modifier.padding(horizontal = MoSpacing.md).testTag("attachment-remove-sheet"),
            )
            Spacer(Modifier.height(MoSpacing.md))
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: Attachment,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(MoShape.field)
            .clickable(enabled = attachment.isAvailableLocally, onClick = onOpen)
            .testTag("attachment-row"),
        horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AttachmentPreview(attachment)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MoSpacing.xxs)) {
            Text(
                attachment.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(attachment.detailLabel(), style = MaterialTheme.typography.bodySmall, color = MoTextSecondary)
            val (label, tone) = attachment.statusLabel()
            MoStatusChip(label, tone = tone)
        }
        TextButton(onClick = onRemove, modifier = Modifier.testTag("remove-attachment")) { Text("Eliminar") }
    }
}

@Composable
private fun AttachmentPreview(attachment: Attachment) {
    val model = attachment.thumbnailUri
        ?: attachment.localUri.takeIf { attachment.kind == AttachmentKind.PHOTO && attachment.isAvailableLocally }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(MoShape.field)
            .background(MoSurfaceSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (attachment.kind == AttachmentKind.PDF) "PDF" else "FOTO",
            style = MaterialTheme.typography.labelMedium,
            color = MoOliveDark,
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = "Vista previa de ${attachment.displayName}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

internal fun Attachment.statusLabel(): Pair<String, MoStatusTone> = when {
    !isAvailableLocally -> "Archivo no disponible" to MoStatusTone.Error
    uploadState == AttachmentUploadState.SYNCED -> "Sincronizado" to MoStatusTone.Success
    uploadState == AttachmentUploadState.FAILED -> "Subida fallida · guardado aquí" to MoStatusTone.Warning
    uploadState == AttachmentUploadState.UPLOADING -> "Subiendo" to MoStatusTone.Info
    else -> "Guardado en el dispositivo" to MoStatusTone.Neutral
}

internal fun Attachment.detailLabel(): String {
    val kindLabel = if (kind == AttachmentKind.PDF) "PDF" else "Foto"
    val date = DATE_FORMAT.format(createdAt.atZone(ZoneId.systemDefault()))
    return listOfNotNull(kindLabel, sizeBytes?.let(::formatSize), date).joinToString(" · ")
}

internal fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.forLanguageTag("es-ES"), "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.forLanguageTag("es-ES"), "%.1f MB", bytes / (1024.0 * 1024.0))
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-ES"))

private fun authority(context: Context) = "${context.packageName}.attachments"

/** A fresh file the camera app may write to. Earlier captures were copied already. */
private fun createCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, "camera")
    directory.listFiles()?.forEach(File::delete)
    if (!directory.isDirectory && !directory.mkdirs()) error("camera directory unavailable")
    val file = File(directory, "captura-${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, authority(context), file)
}

/** S91: the device's own viewer opens the app-owned copy through a read-only grant. */
private fun openAttachment(
    context: Context,
    attachment: Attachment,
): Boolean {
    val local = Uri.parse(attachment.localUri)
    val shareable = if (local.scheme == ContentResolver.SCHEME_FILE) {
        FileProvider.getUriForFile(context, authority(context), File(local.path ?: return false))
    } else {
        local
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(shareable, attachment.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        false
    } catch (error: IllegalArgumentException) {
        false
    }
}
