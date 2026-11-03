package com.isivoltpro.maginaolivo.feature.profile.ui

import java.util.UUID

/**
 * Estado de sincronización de la cuenta (CR-G2.6 / CR-UX-PERFIL-01).
 * Diferencia con rigor técnico entre sesión iniciada con red, sesión sin conexión y modo local.
 */
enum class AccountSyncState(
    val label: String,
    val tone: com.isivoltpro.maginaolivo.ui.components.MoStatusTone,
) {
    LOCAL_ONLY("Modo local · Solo en este teléfono", com.isivoltpro.maginaolivo.ui.components.MoStatusTone.Neutral),
    SIGNED_IN_OFFLINE("Sesión iniciada · Sin conexión (Cambios locales)", com.isivoltpro.maginaolivo.ui.components.MoStatusTone.Warning),
    SIGNED_IN_SYNCED("Sesión iniciada · Sincronizado", com.isivoltpro.maginaolivo.ui.components.MoStatusTone.Success),
    SIGNED_IN_SYNCING("Sesión iniciada · Sincronizando...", com.isivoltpro.maginaolivo.ui.components.MoStatusTone.Info),
}

/**
 * Roles de colaboración en Mágina Olivo (CR-COLLAB-01 / CR-COLLAB-02).
 * NOTA: Este enum define exclusivamente la representación visual de prototipo.
 * No constituye concesión real de permisos ni autorización en backend.
 */
enum class CollaboratorRole(
    val title: String,
    val subtitle: String,
    val description: String,
    val allowedActions: List<String>,
    val restrictedActions: List<String>,
) {
    PROPIETARIO(
        title = "Titular / Propietario",
        subtitle = "Control total y legitimación de la explotación (Diseño)",
        description = "Administra parcelas, fincas, contratos de cooperativa, invita colaboradores y visualiza la contabilidad completa.",
        allowedActions = listOf(
            "Administración de parcelas y catastro",
            "Gestión y firma de contratos de campaña",
            "Invitar gestores y empresas recolectoras",
            "Acceso completo a gastos, ingresos y liquidaciones",
            "Registro de labores, riegos y recolección",
        ),
        restrictedActions = emptyList(),
    ),
    GESTOR(
        title = "Gestor / Arrendatario",
        subtitle = "Gestión agronómica y operativa del cuaderno (Diseño)",
        description = "Lleva el día a día de la campaña: planifica labores, tratamientos fitosanitarios, jornales y pesadas en las fincas autorizadas.",
        allowedActions = listOf(
            "Registro de labores agrícolas y tratamientos CUE",
            "Gestión de jornales y partes diarios de trabajo",
            "Anotación de pesadas y albaranes de aceituna",
            "Propuesta y control de gastos de explotación",
        ),
        restrictedActions = listOf(
            "No puede modificar la titularidad ni parcelas catastrales",
            "No accede a contratos ni liquidaciones bancarias del titular",
            "Acceso estrictamente acotado a las fincas asignadas",
        ),
    ),
    RECOLECTOR(
        title = "Empresa Recolectora",
        subtitle = "Encargo acotado de cosecha y entregas (Diseño)",
        description = "Encargada de recolectar parcelas específicas durante la campaña vigente. Sube partes de cuadrilla, tickets y fotos de pesadas.",
        allowedActions = listOf(
            "Registro de días de recolección en parcelas asignadas",
            "Subida de pesadas, albaranes de báscula y fotografías",
            "Propuesta de costes de recolección para aprobación",
        ),
        restrictedActions = listOf(
            "Sin acceso a otras fincas o campañas anteriores",
            "Sin acceso a la contabilidad general ni contratos",
            "Los costes propuestos requieren aprobación del titular",
        ),
    ),
}

/**
 * Estado de una invitación de colaboración (Prototipo UX).
 */
enum class InvitationStatus(val label: String) {
    PENDIENTE("Pendiente de confirmación"),
    ACEPTADA("Aceptada · Activa"),
    RECHAZADA("Rechazada"),
    REVOCADA("Revocada"),
    CADUCADA("Caducada"),
}

/**
 * Vía de envío de la invitación (Prototipo UX).
 */
enum class InvitationChannel(val title: String, val iconDescription: String) {
    WHATSAPP("WhatsApp", "Compartir por mensaje de WhatsApp"),
    QR_CODE("Código QR presencial", "Escanear cara a cara en el campo"),
    ACCESS_CODE("Código numérico (6 dígitos)", "Introducir código de un solo uso"),
    LINK("Enlace seguro HTTPS", "Copiar enlace privado al portapapeles"),
}

/**
 * Modelo de datos simulado para una explotación con rol asignado.
 */
data class MockFarmMembership(
    val farmId: UUID = UUID.randomUUID(),
    val farmName: String,
    val municipality: String,
    val parcelCount: Int,
    val areaHectares: Double,
    val role: CollaboratorRole,
    val isPrimary: Boolean = false,
)

/**
 * Modelo de datos simulado para una invitación enviada o recibida.
 */
data class MockInvitation(
    val id: UUID = UUID.randomUUID(),
    val farmName: String,
    val municipality: String,
    val inviterName: String,
    val inviterEmail: String,
    val recipientEmail: String? = null,
    val role: CollaboratorRole,
    val scope: String,
    val channel: InvitationChannel,
    val status: InvitationStatus,
    val dateLabel: String,
    val oneTimeCode: String? = null,
)

/**
 * Datos simulados 100% ficticios para Compose Previews y pruebas visuales.
 * No contienen datos personales reales de ningún usuario ni entidades operativas.
 */
object ProfilePreviewMocks {
    val signedInUser = MockUserProfile(
        userId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        displayName = "Agricultor Demo",
        email = "usuario.demo@example.test",
        initials = "AD",
        syncState = AccountSyncState.SIGNED_IN_SYNCED,
        preferredMunicipality = "Municipio Ficticio",
        preferredProvince = "Provincia Demo",
        preferredCooperative = "S.C.A. Olivarera Demo",
        activeWorkspace = "Explotación Principal (Demo)",
    )

    val signedInOfflineUser = MockUserProfile(
        userId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        displayName = "Agricultor Demo",
        email = "usuario.demo@example.test",
        initials = "AD",
        syncState = AccountSyncState.SIGNED_IN_OFFLINE,
        preferredMunicipality = "Municipio Ficticio",
        preferredProvince = "Provincia Demo",
        preferredCooperative = "S.C.A. Olivarera Demo",
        activeWorkspace = "Explotación Principal (Demo)",
    )

    val localGuestUser = MockUserProfile(
        userId = null,
        displayName = "Perfil Local (Demostración)",
        email = "sin-cuenta@example.test",
        initials = "PL",
        syncState = AccountSyncState.LOCAL_ONLY,
        preferredMunicipality = "Municipio Muestra",
        preferredProvince = "Provincia Demo",
        preferredCooperative = "S.C.A. San Isidro (Ficticia)",
        activeWorkspace = "Mi Olivar Local (Muestra)",
    )

    val farmMemberships = listOf(
        MockFarmMembership(
            farmName = "Finca La Esperanza (Demo)",
            municipality = "Valle Ficticio",
            parcelCount = 4,
            areaHectares = 14.8,
            role = CollaboratorRole.PROPIETARIO,
            isPrimary = true,
        ),
        MockFarmMembership(
            farmName = "Olivar Los Cerros (Muestra)",
            municipality = "Sierra Demo",
            parcelCount = 2,
            areaHectares = 6.4,
            role = CollaboratorRole.GESTOR,
            isPrimary = false,
        ),
        MockFarmMembership(
            farmName = "Pago El Romeral (Ficticio)",
            municipality = "Campiña Demo",
            parcelCount = 1,
            areaHectares = 3.2,
            role = CollaboratorRole.RECOLECTOR,
            isPrimary = false,
        ),
    )

    val receivedInvitations = listOf(
        MockInvitation(
            farmName = "Finca El Manantial (Demo)",
            municipality = "Valle Demo",
            inviterName = "Colaborador Demo",
            inviterEmail = "colaborador.demo@example.test",
            role = CollaboratorRole.GESTOR,
            scope = "Toda la finca · Campaña 2026/2027 (Prototipo)",
            channel = InvitationChannel.WHATSAPP,
            status = InvitationStatus.PENDIENTE,
            dateLabel = "Recibida hoy, 10:30 (Muestra)",
        ),
    )

    val sentInvitations = listOf(
        MockInvitation(
            farmName = "Finca La Esperanza (Demo)",
            municipality = "Valle Ficticio",
            inviterName = "Agricultor Demo",
            inviterEmail = "usuario.demo@example.test",
            recipientEmail = "cuadrilla.demo@example.test",
            role = CollaboratorRole.RECOLECTOR,
            scope = "Parcelas 1 y 2 · Campaña 2026/2027 (Prototipo)",
            channel = InvitationChannel.QR_CODE,
            status = InvitationStatus.ACEPTADA,
            dateLabel = "Aceptada hace 3 días (Muestra)",
            oneTimeCode = "000-000",
        ),
        MockInvitation(
            farmName = "Finca La Esperanza (Demo)",
            municipality = "Valle Ficticio",
            inviterName = "Agricultor Demo",
            inviterEmail = "usuario.demo@example.test",
            recipientEmail = "gestor.demo@example.test",
            role = CollaboratorRole.GESTOR,
            scope = "Toda la finca · Sin caducidad (Prototipo)",
            channel = InvitationChannel.WHATSAPP,
            status = InvitationStatus.PENDIENTE,
            dateLabel = "Enviada ayer (Muestra)",
            oneTimeCode = "111-222",
        ),
    )
}

data class MockUserProfile(
    val userId: UUID?,
    val displayName: String,
    val email: String,
    val initials: String,
    val syncState: AccountSyncState = if (userId != null) AccountSyncState.SIGNED_IN_SYNCED else AccountSyncState.LOCAL_ONLY,
    val preferredMunicipality: String,
    val preferredProvince: String,
    val preferredCooperative: String?,
    val activeWorkspace: String,
) {
    val isConnected: Boolean get() = userId != null
    val accountStatusLabel: String get() = syncState.label
}
