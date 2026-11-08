package com.isivoltpro.maginaolivo.domain.assistant

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssistantIsolationAndSecurityTest {

    private lateinit var toolsService: DefaultAssistantToolsService
    private lateinit var inferenceProvider: SimulatedMistralInferenceProvider
    private lateinit var chatRepository: AssistantChatRepositoryImpl

    private val workspaceAccountA = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val workspaceAccountC = UUID.fromString("00000000-0000-0000-0000-00000000000c")

    private val farmA1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val farmA2 = UUID.fromString("11111111-1111-1111-1111-111111111112")
    private val farmC1 = UUID.fromString("22222222-2222-2222-2222-222222222221")

    @Before
    fun setUp() {
        toolsService = DefaultAssistantToolsService()
        inferenceProvider = SimulatedMistralInferenceProvider()
        chatRepository = AssistantChatRepositoryImpl(inferenceProvider, toolsService)

        // Registrar fincas en almacén simulado
        toolsService.registerMockFarm(
            DefaultAssistantToolsService.FarmRecordMock(
                id = farmA1,
                workspaceId = workspaceAccountA,
                name = "El Olivar Alto",
                municipality = "Bedmar",
                province = "Jaén",
                cadastre = "23018A00100010",
                trees = 450
            )
        )
        toolsService.registerMockFarm(
            DefaultAssistantToolsService.FarmRecordMock(
                id = farmA2,
                workspaceId = workspaceAccountA,
                name = "La Laguna",
                municipality = "Garcíez",
                province = "Jaén",
                cadastre = "23018A00200020",
                trees = 300
            )
        )
        toolsService.registerMockFarm(
            DefaultAssistantToolsService.FarmRecordMock(
                id = farmC1,
                workspaceId = workspaceAccountC,
                name = "Finca de C",
                municipality = "Jódar",
                province = "Jaén",
                cadastre = "23053A00300030",
                trees = 600
            )
        )
    }

    @Test
    fun scenario1_accountACanQueryItsOwnFarms() = runBlocking {
        val contextA = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceAccountA,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_FARMS)
        )

        val result = toolsService.getAuthorizedFarms(contextA)
        assertTrue(result is AppResult.Success)
        val farms = (result as AppResult.Success).value.first
        assertEquals(2, farms.size)
        assertTrue(farms.any { it.id == farmA1 })
        assertTrue(farms.any { it.id == farmA2 })
    }

    @Test
    fun scenario2_accountCCannotAccessFarmsOfAccountA_EvenWithExplicitIds() = runBlocking {
        val contextC = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceAccountC,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_FARMS)
        )

        // Intento de consultar finca farmA1 sabiendo su ID desde el contexto de C
        val directAccessResult = toolsService.getFarmDetails(contextC, farmA1)
        assertTrue("Debe denegar el acceso a finca ajena", directAccessResult is AppResult.Failure)

        // Comprobar que en su lista de fincas autorizadas no aparece ninguna de A
        val listResult = toolsService.getAuthorizedFarms(contextC)
        assertTrue(listResult is AppResult.Success)
        val farms = (listResult as AppResult.Success).value.first
        assertEquals(1, farms.size)
        assertEquals(farmC1, farms[0].id)
    }

    @Test
    fun scenario3_sharedUserAccessesOnlyExplicitlyAuthorizedFarms() = runBlocking {
        // Gestor / Arrendatario que solo tiene permiso sobre farmA1 pero no sobre farmA2
        val contextShared = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceAccountA,
            effectiveRole = AssistantUserRole.MANAGER,
            grantedPermissions = setOf(AssistantPermission.VIEW_FARMS),
            authorizedFarmIds = setOf(farmA1) // Solo farmA1 autorizada
        )

        val listResult = toolsService.getAuthorizedFarms(contextShared)
        assertTrue(listResult is AppResult.Success)
        val farms = (listResult as AppResult.Success).value.first
        assertEquals(1, farms.size)
        assertEquals(farmA1, farms[0].id)

        // Intento de acceder a farmA2 para la que no tiene permiso delegado
        val unauthorizedDetail = toolsService.getFarmDetails(contextShared, farmA2)
        assertTrue(unauthorizedDetail is AppResult.Failure)
    }

    @Test
    fun scenario4_accountSwitchClearsContextAndHistory() = runBlocking {
        val contextA = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceAccountA,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_FARMS)
        )

        chatRepository.sendMessage(workspaceAccountA, contextA, "¿Cuáles son mis fincas?")
        val historyA = chatRepository.observeMessages(workspaceAccountA).first()
        assertTrue(historyA.isNotEmpty())

        // Simulamos cambio de cuenta
        chatRepository.resetContextOnAccountSwitch()

        val historyAfterSwitch = chatRepository.observeMessages(workspaceAccountA).first()
        assertTrue("El historial debe vaciarse al cambiar de cuenta", historyAfterSwitch.isEmpty())
    }

    @Test
    fun scenario5_revokedPermissionBlocksQueries() = runBlocking {
        // Usuario recolector o colaborador al que se le revoca el acceso a entregas/gastos
        val contextNoDeliveries = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceAccountA,
            effectiveRole = AssistantUserRole.WORKER,
            grantedPermissions = setOf(AssistantPermission.VIEW_FARMS) // No tiene VIEW_DELIVERIES
        )

        val result = toolsService.getDeliverySummary(contextNoDeliveries, UUID.randomUUID())
        assertTrue("La consulta de entregas debe rechazarse sin el permiso", result is AppResult.Failure)
    }

    @Test
    fun scenario6_localUnsyncedRecordFlaggedProperly() = runBlocking {
        val context = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceAccountA,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_DELIVERIES)
        )

        val result = toolsService.getDeliverySummary(context, UUID.randomUUID())
        assertTrue(result is AppResult.Success)
        val meta = (result as AppResult.Success).value.second
        assertTrue("Los registros pendientes deben marcar isLocalPendingSync=true", meta.isLocalPendingSync)
    }

    @Test
    fun scenario11_mockModeWorksWithoutMistralApiKey() = runBlocking {
        val context = AssistantSecurityContext(
            userId = UUID.randomUUID(),
            workspaceId = workspaceAccountA,
            effectiveRole = AssistantUserRole.OWNER,
            grantedPermissions = setOf(AssistantPermission.VIEW_WEATHER)
        )

        val sendResult = chatRepository.sendMessage(workspaceAccountA, context, "¿Qué tiempo hace hoy?")
        assertTrue(sendResult is AppResult.Success)
        val msg = (sendResult as AppResult.Success).value
        assertTrue(msg.isMock)
        assertTrue(msg.text.contains("Previsión"))
        assertTrue(msg.usedTools.contains("get_weather_forecast"))
    }
}
