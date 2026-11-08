package com.isivoltpro.maginaolivo.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.assistant.AssistantChatMessage
import com.isivoltpro.maginaolivo.domain.assistant.AssistantChatRepository
import com.isivoltpro.maginaolivo.domain.assistant.AssistantPermission
import com.isivoltpro.maginaolivo.domain.assistant.AssistantSecurityContext
import com.isivoltpro.maginaolivo.domain.assistant.AssistantUserRole
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class AssistantChatUiState(
    val messages: List<AssistantChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val securityContext: AssistantSecurityContext? = null,
    val currentInput: String = ""
)

class AssistantChatViewModel(
    private val chatRepository: AssistantChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantChatUiState())
    val uiState: StateFlow<AssistantChatUiState> = _uiState.asStateFlow()

    private var activeWorkspaceId: UUID? = null

    fun initializeSession(context: AssistantSecurityContext) {
        _uiState.value = _uiState.value.copy(
            securityContext = context,
            errorMessage = null
        )
        activeWorkspaceId = context.workspaceId

        viewModelScope.launch {
            chatRepository.observeMessages(context.workspaceId).collectLatest { msgs ->
                _uiState.value = _uiState.value.copy(messages = msgs)
            }
        }
    }

    fun onInputChanged(newText: String) {
        _uiState.value = _uiState.value.copy(currentInput = newText)
    }

    fun sendMessage() {
        val text = _uiState.value.currentInput.trim()
        val context = _uiState.value.securityContext
        val workspaceId = activeWorkspaceId

        if (text.isEmpty() || context == null || workspaceId == null) return

        _uiState.value = _uiState.value.copy(
            isSending = true,
            currentInput = "",
            errorMessage = null
        )

        viewModelScope.launch {
            val result = chatRepository.sendMessage(workspaceId, context, text)
            _uiState.value = _uiState.value.copy(
                isSending = false,
                errorMessage = (result as? AppResult.Failure)?.let { "Error al consultar: ${it.error}" }
            )
        }
    }

    fun clearHistory() {
        val workspaceId = activeWorkspaceId ?: return
        viewModelScope.launch {
            chatRepository.clearHistory(workspaceId)
        }
    }

    fun onAccountSwitched() {
        chatRepository.resetContextOnAccountSwitch()
        _uiState.value = AssistantChatUiState()
        activeWorkspaceId = null
    }
}
