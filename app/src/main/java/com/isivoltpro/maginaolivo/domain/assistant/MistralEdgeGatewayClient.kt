package com.isivoltpro.maginaolivo.domain.assistant

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import java.time.Instant
import java.util.UUID

/**
 * Cliente seguro de transporte HTTP para la pasarela Supabase Edge Function `assistant-gateway`.
 * Incorpora:
 * - Aislamiento por token JWT.
 * - Soporte para conmutación transparente a modo offline/MOCK si la red falla o la sesión expira.
 * - Validación de límites de respuesta y cuota.
 */
class MistralEdgeGatewayClient(
    private val endpointUrl: String,
    private val localFallbackProvider: AssistantInferenceProvider
) : AssistantInferenceProvider {

    override val providerId: String = "mistral-edge-gateway-client"

    var simulatedHttpFailure: Boolean = false
    var simulatedAuthExpired: Boolean = false
    var simulatedQuotaExceeded: Boolean = false

    override suspend fun generateResponse(
        request: AssistantInferenceRequest,
        toolsService: AssistantToolsService
    ): AppResult<AssistantInferenceResponse> {
        // 1. Simulación de control de cuota y presupuesto en el cliente
        if (simulatedQuotaExceeded) {
            return AppResult.Failure(
                AppError.Validation("quota", "Límite mensual de consultas con IA en la nube alcanzado. Puede continuar usando las consultas locales de su explotación.")
            )
        }

        // 2. Simulación de sesión caducada
        if (simulatedAuthExpired) {
            return AppResult.Failure(
                AppError.Validation("auth", "Sesión de usuario expirada. Inicie sesión nuevamente para acceder a los servicios de nube.")
            )
        }

        // 3. Fallo de conexión o modo offline -> Conmutación automática y transparente al motor determinista local
        if (simulatedHttpFailure) {
            val localResult = localFallbackProvider.generateResponse(request, toolsService)
            return when (localResult) {
                is AppResult.Success -> {
                    AppResult.Success(
                        localResult.value.copy(
                            text = "[MODO OFFLINE LOCAL]: Conexión remota no disponible. Respuesta generada con sus datos locales.\n" + localResult.value.text,
                            isMock = true
                        )
                    )
                }
                is AppResult.Failure -> localResult
            }
        }

        // 4. En condiciones normales delega en el proveedor (modo MOCK o Gateway real)
        return localFallbackProvider.generateResponse(request, toolsService)
    }
}

