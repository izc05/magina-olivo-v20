package com.isivoltpro.maginaolivo.domain.assistant

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.Instant
import java.util.UUID

/**
 * Rol conversacional del mensaje.
 */
enum class ChatMessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

/**
 * Estado del mensaje en el chat.
 */
enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    ERROR
}

/**
 * Representación inmutable de un mensaje conversacional.
 */
data class AssistantChatMessage(
    val id: UUID = UUID.randomUUID(),
    val conversationId: UUID,
    val role: ChatMessageRole,
    val text: String,
    val timestamp: Instant = Instant.now(),
    val status: MessageStatus = MessageStatus.DELIVERED,
    val usedTools: List<String> = emptyList(),
    val isMock: Boolean = true
)

/**
 * Solicitud de interacción enviada a la pasarela o motor de IA.
 */
data class AssistantInferenceRequest(
    val conversationId: UUID,
    val userPrompt: String,
    val securityContext: AssistantSecurityContext,
    val history: List<AssistantChatMessage> = emptyList(),
    val maxTokens: Int = 1000
)

/**
 * Respuesta estructurada de inferencia.
 */
data class AssistantInferenceResponse(
    val text: String,
    val toolsCalled: List<String> = emptyList(),
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val isMock: Boolean = true
)

/**
 * Interfaz desacoplada para el proveedor de inferencia (Mistral, Mock, etc.).
 * No almacena credenciales ni acopla la arquitectura a una API específica.
 */
interface AssistantInferenceProvider {
    val providerId: String

    suspend fun generateResponse(
        request: AssistantInferenceRequest,
        toolsService: AssistantToolsService
    ): AppResult<AssistantInferenceResponse>
}
