package com.isivoltpro.maginaolivo.domain.assistant

import java.time.Instant
import java.util.UUID

/**
 * Roles reconocidos en la gobernanza de explotación de Mágina Olivo.
 */
enum class AssistantUserRole {
    OWNER,          // Titular o propietario
    MANAGER,        // Gestor o arrendatario
    HARVEST_CREW,   // Empresa recolectora
    WORKER,         // Jornalero o colaborador
    GUEST           // Invitado / sin privilegios específicos
}

/**
 * Permisos atómicos evaluados por los servicios del asistente.
 */
enum class AssistantPermission {
    VIEW_FARMS,
    VIEW_PARCELS,
    VIEW_CAMPAIGNS,
    VIEW_DELIVERIES,
    VIEW_EXPENSES,
    VIEW_ECONOMY_SUMMARY,
    VIEW_IRRIGATION,
    VIEW_WEATHER,
    VIEW_NOTEBOOK,
    // Acciones de escritura asistida (Fase F)
    PREPARE_EXPENSE,
    PREPARE_DELIVERY,
    PREPARE_IRRIGATION,
    PREPARE_NOTEBOOK_ENTRY
}

/**
 * Contexto de seguridad e identidad autorizado para cada consulta conversacional.
 */
data class AssistantSecurityContext(
    val userId: UUID,
    val workspaceId: UUID,
    val effectiveRole: AssistantUserRole,
    val grantedPermissions: Set<AssistantPermission>,
    val authorizedFarmIds: Set<UUID> = emptySet(),
    val selectedCampaignId: UUID? = null,
    val timestamp: Instant = Instant.now()
) {
    fun hasPermission(permission: AssistantPermission): Boolean =
        grantedPermissions.contains(permission)

    fun isFarmAuthorized(farmId: UUID): Boolean =
        authorizedFarmIds.contains(farmId)
}
