package com.isivoltpro.maginaolivo.feature.attachments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.attachment.Attachment
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class AttachmentsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val message: String? = null,
    val error: String? = null,
)

class AttachmentsViewModel(
    private val owner: AttachmentOwner,
    private val repository: AttachmentRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AttachmentsUiState())
    val state: StateFlow<AttachmentsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeForOwner(owner)
                .catch {
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        error = "No pudimos leer los adjuntos de este dispositivo",
                    )
                }.collect { attachments ->
                    mutableState.value = mutableState.value.copy(isLoading = false, attachments = attachments)
                }
        }
    }

    fun attach(sourceUri: String) {
        mutate(ATTACHED_MESSAGE) { repository.attach(owner, sourceUri) }
    }

    fun remove(id: UUID) {
        mutate(REMOVED_MESSAGE) { repository.remove(id) }
    }

    fun reportProblem(message: String) {
        mutableState.value = mutableState.value.copy(message = null, error = message)
    }

    private fun mutate(
        successMessage: String,
        operation: suspend () -> AppResult<*>,
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isSaving = true, message = null, error = null)
            mutableState.value = when (val result = operation()) {
                is AppResult.Success -> mutableState.value.copy(isSaving = false, message = successMessage)
                is AppResult.Failure -> mutableState.value.copy(
                    isSaving = false,
                    error = attachmentErrorMessage(result.error),
                )
            }
        }
    }

    companion object {
        const val ATTACHED_MESSAGE = "Adjunto guardado en este dispositivo"
        const val REMOVED_MESSAGE = "Adjunto eliminado"
    }
}

/** What the farmer reads when an attachment could not be saved. Never a stack trace. */
internal fun attachmentErrorMessage(error: AppError): String = when (error) {
    is AppError.Validation -> when (error.code) {
        "unsupported_type", "unsupported_source" -> "Solo se pueden adjuntar fotos, imágenes y PDF"
        "file_too_large" -> "El archivo supera el máximo de 50 MB"
        "empty_file" -> "El archivo está vacío"
        "archived_owner" -> "Este registro está archivado y no admite adjuntos"
        else -> "El archivo no es válido"
    }
    is AppError.NotFound -> "El registro ya no está disponible en este dispositivo"
    is AppError.Permission -> "No hay permiso para leer ese archivo. Vuelve a elegirlo"
    else -> "No se pudo guardar el adjunto en este dispositivo"
}
