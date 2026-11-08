package com.isivoltpro.maginaolivo.domain.assistant

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio de conversación y memoria conversacional de Mágina IA.
 * Aislado estrictamente por Workspace e Identity.
 */
interface AssistantChatRepository {
    fun observeMessages(workspaceId: UUID): Flow<List<AssistantChatMessage>>
    suspend fun sendMessage(
        workspaceId: UUID,
        securityContext: AssistantSecurityContext,
        text: String
    ): AppResult<AssistantChatMessage>
    suspend fun clearHistory(workspaceId: UUID)
    fun resetContextOnAccountSwitch()
}

class AssistantChatRepositoryImpl(
    private val inferenceProvider: AssistantInferenceProvider,
    private val toolsService: AssistantToolsService
) : AssistantChatRepository {

    private val conversationStores = mutableMapOf<UUID, MutableStateFlow<List<AssistantChatMessage>>>()

    private fun getFlowForWorkspace(workspaceId: UUID): MutableStateFlow<List<AssistantChatMessage>> {
        return conversationStores.getOrPut(workspaceId) {
            MutableStateFlow(emptyList())
        }
    }

    override fun observeMessages(workspaceId: UUID): Flow<List<AssistantChatMessage>> {
        return getFlowForWorkspace(workspaceId).asStateFlow()
    }

    override suspend fun sendMessage(
        workspaceId: UUID,
        securityContext: AssistantSecurityContext,
        text: String
    ): AppResult<AssistantChatMessage> {
        val store = getFlowForWorkspace(workspaceId)
        val conversationId = workspaceId // Conversación anclada al ámbito del workspace

        val userMsg = AssistantChatMessage(
            id = UUID.randomUUID(),
            conversationId = conversationId,
            role = ChatMessageRole.USER,
            text = text,
            timestamp = Instant.now(),
            status = MessageStatus.SENT,
            isMock = false
        )

        // Registrar mensaje de usuario
        store.value = store.value + userMsg

        val request = AssistantInferenceRequest(
            conversationId = conversationId,
            userPrompt = text,
            securityContext = securityContext,
            history = store.value
        )

        val responseResult = inferenceProvider.generateResponse(request, toolsService)

        return when (responseResult) {
            is AppResult.Success -> {
                val assistantMsg = AssistantChatMessage(
                    id = UUID.randomUUID(),
                    conversationId = conversationId,
                    role = ChatMessageRole.ASSISTANT,
                    text = responseResult.value.text,
                    timestamp = Instant.now(),
                    status = MessageStatus.DELIVERED,
                    usedTools = responseResult.value.toolsCalled,
                    isMock = responseResult.value.isMock
                )
                store.value = store.value + assistantMsg
                AppResult.Success(assistantMsg)
            }
            is AppResult.Failure -> {
                val errorMsg = AssistantChatMessage(
                    id = UUID.randomUUID(),
                    conversationId = conversationId,
                    role = ChatMessageRole.ASSISTANT,
                    text = "No se pudo procesar la consulta: ${responseResult.error}",
                    timestamp = Instant.now(),
                    status = MessageStatus.ERROR,
                    isMock = true
                )
                store.value = store.value + errorMsg
                AppResult.Failure(responseResult.error)
            }
        }
    }

    override suspend fun clearHistory(workspaceId: UUID) {
        val store = conversationStores[workspaceId]
        store?.value = emptyList()
    }

    override fun resetContextOnAccountSwitch() {
        // Al cambiar de cuenta se descartan todos los flujos de memoria en memoria
        conversationStores.clear()
    }
}
