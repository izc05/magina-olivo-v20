package com.isivoltpro.maginaolivo.feature.irrigation

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.IsolatedAppStateRule
import com.isivoltpro.maginaolivo.MainActivity
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.ActivityEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmParcelMembershipEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.ParcelEntity
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.data.local.model.FarmStatus
import com.isivoltpro.maginaolivo.data.local.model.RecordStatus
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IrrigationNavigationTest {

    val composeRule = createAndroidComposeRule<MainActivity>()

    private var testFarmIrrigatedId: UUID = UUID.randomUUID()
    private var testFarmSecanoId: UUID = UUID.randomUUID()
    private var testActivityId: UUID = UUID.randomUUID()

    private val seed = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                val database = MaginaOlivoDatabase.getInstance(context)
                runBlocking {
                    val now = Instant.now()
                    val today = LocalDate.now()
                    val metadata = LocalMetadata(now, now)

                    val workspace = UUID.randomUUID()
                    database.workspaceDao().upsert(
                        WorkspaceEntity(
                            id = workspace,
                            name = "Olivar",
                            ownerUserId = UUID.randomUUID(),
                            countryCode = "ES",
                            timezone = "Europe/Madrid",
                            locale = "es-ES",
                            currency = "EUR",
                            metadata = metadata,
                        )
                    )

                    // Finca 1: Regadío
                    val farmIrrigatedId = UUID.randomUUID()
                    database.farmDao().upsert(
                        FarmEntity(
                            id = farmIrrigatedId,
                            workspaceId = workspace,
                            name = "Finca Huerta del Pozo",
                            description = "Finca con sistema de riego localizado",
                            municipality = "Bedmar",
                            province = "Jaén",
                            status = FarmStatus.ACTIVE,
                            metadata = metadata,
                        )
                    )

                    // Parcela de regadío con turnos todos los días para garantizar cálculo en Inicio
                    val parcelId = UUID.randomUUID()
                    val allDays = DayOfWeek.entries.joinToString(",") { it.name }
                    database.parcelDao().upsert(
                        ParcelEntity(
                            id = parcelId,
                            workspaceId = workspace,
                            displayName = "Haza de Arriba",
                            source = "MANUAL",
                            status = RecordStatus.ACTIVE,
                            metadata = metadata,
                            oliveTreeCount = 450,
                            irrigationSystem = "DRIP",
                            irrigationNetwork = "Comunidad San Isidro",
                            irrigationSector = "Sector 2",
                            irrigationDays = allDays,
                        )
                    )

                    // Membresía
                    database.parcelDao().upsertMembership(
                        FarmParcelMembershipEntity(
                            id = UUID.randomUUID(),
                            workspaceId = workspace,
                            farmId = farmIrrigatedId,
                            parcelId = parcelId,
                            validFrom = now,
                            metadata = metadata,
                        )
                    )

                    // Actividad histórica de riego en Finca 1
                    val activityId = UUID.randomUUID()
                    database.activityDao().upsert(
                        ActivityEntity(
                            id = activityId,
                            workspaceId = workspace,
                            farmId = farmIrrigatedId,
                            activityDate = today.minusDays(3),
                            type = ActivityType.IRRIGATION.name,
                            status = ActivityStatus.COMPLETED,
                            description = "Riego semanal programado",
                            quantity = 35.0, // m³
                            unit = "m³",
                            costMinor = 2800L, // 28.00 €
                            currency = "EUR",
                            notes = "Presión normal, 4 horas",
                            metadata = metadata,
                        )
                    )

                    // Finca 2: Secano (sin parcelas de regadío)
                    val farmSecanoId = UUID.randomUUID()
                    database.farmDao().upsert(
                        FarmEntity(
                            id = farmSecanoId,
                            workspaceId = workspace,
                            name = "Finca Cerro del Viento",
                            description = "Olivar tradicional de secano",
                            municipality = "Jódar",
                            province = "Jaén",
                            status = FarmStatus.ACTIVE,
                            metadata = metadata,
                        )
                    )

                    testFarmIrrigatedId = farmIrrigatedId
                    testFarmSecanoId = farmSecanoId
                    testActivityId = activityId
                }
                base.evaluate()
            }
        }
    }

    @get:Rule
    val rules: TestRule =
        RuleChain
            .outerRule(IsolatedAppStateRule())
            .around(seed)
            .around(composeRule)

    private fun enterMainShell() {
        waitForText("Saltar")
        composeRule.onNodeWithText("Saltar").performClick()
        waitForTag("home-reference-root")
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000L) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000L) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun countOutboxRecords(): Long {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = MaginaOlivoDatabase.getInstance(context)
        return database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_outbox").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun openFarm(farmId: UUID) {
        waitForTag("farms-root")
        composeRule.onNodeWithTag("farm-list").performScrollToNode(hasTestTag("farm-$farmId"))
        composeRule.onNodeWithTag("farm-$farmId").performClick()
        waitForTag("farm-detail-root")
    }

    private fun openIrrigationFromFarmDetail() {
        waitForTag("farm-section-irrigation")
        composeRule.onNodeWithTag("farm-section-irrigation").performScrollTo().performClick()
        waitForTag("irrigation-screen")
    }

    @Test
    fun homeNextIrrigationTurnOpensIrrigationScreenAndBackReturnsHome() {
        enterMainShell()

        // 1. Verificar sección de próximo riego en Inicio
        waitForTag("home-irrigation-header")
        composeRule.onNodeWithTag("home-irrigation-header").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("home-irrigation-turn").performScrollTo().assertIsDisplayed()

        // 2. Pulsar sobre el turno de riego
        composeRule.onNodeWithTag("home-irrigation-turn").performClick()

        // 3. Verificar que se abrió la pantalla de Riego
        waitForTag("irrigation-screen")
        composeRule.onNodeWithTag("irrigation-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("irrigation-title").assertIsDisplayed()
        composeRule.onNodeWithText("Finca Huerta del Pozo").assertIsDisplayed()

        // 4. Pulsar el botón Atrás y comprobar retorno a Inicio
        composeRule.onNodeWithTag("irrigation-back-button").performClick()
        waitForTag("home-reference-root")
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
    }

    @Test
    fun farmDetailScreenHasIrrigationEntryAndNavigatesToIrrigationAndBack() {
        enterMainShell()

        // 1. Navegar a Mi Campo
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()

        // 2. Entrar en la finca de regadío
        openFarm(testFarmIrrigatedId)

        // 3. Comprobar que existe la entrada de Riego en la ficha y abrirla
        openIrrigationFromFarmDetail()

        // 4. Verificar pantalla de Riego abierta con los datos de esta finca
        composeRule.onNodeWithTag("irrigation-title").assertIsDisplayed()
        composeRule.onNodeWithText("Finca Huerta del Pozo").assertIsDisplayed()

        // 5. Volver atrás a la ficha de la finca
        composeRule.onNodeWithTag("irrigation-back-button").performClick()
        waitForTag("farm-detail-root")
        composeRule.onNodeWithTag("farm-detail-root").assertIsDisplayed()
    }

    @Test
    fun irrigationRegisterButtonOpensCuadernoWithIrrigationType() {
        enterMainShell()

        // Ir a Mi Campo -> Finca -> Riego
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        openFarm(testFarmIrrigatedId)
        openIrrigationFromFarmDetail()

        // Pulsar "+ Registrar riego"
        composeRule.onNodeWithTag("irrigation-register-button").performClick()

        // Debe abrir la pantalla real del Cuaderno / Registro
        waitForTag("register-activity-root")
        composeRule.onNodeWithTag("register-activity-root").assertIsDisplayed()

        // Volver atrás a Riego
        pressBack()
        waitForTag("irrigation-screen")
        composeRule.onNodeWithTag("irrigation-screen").assertIsDisplayed()
    }

    @Test
    fun irrigationHistoryTabDisplaysActivitiesAndClickOpensDetail() {
        enterMainShell()

        // Ir a Riego
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        openFarm(testFarmIrrigatedId)
        openIrrigationFromFarmDetail()

        // Ir a la pestaña Historial
        composeRule.onNodeWithTag("irrigation-tab-history").performClick()
        waitForTag("irrigation-history-list")

        // Verificar la tarjeta de la actividad registrada
        val activityCardTag = "irrigation-activity-card-$testActivityId"
        composeRule.onNodeWithTag(activityCardTag).performScrollTo().assertIsDisplayed()

        // Pulsar sobre la tarjeta para abrir su detalle en el Cuaderno
        composeRule.onNodeWithTag(activityCardTag).performClick()

        // Debe abrir el detalle de la actividad
        waitForTag("activity-detail-root")
        composeRule.onNodeWithTag("activity-detail-root").assertIsDisplayed()

        // Volver atrás
        pressBack()
        waitForTag("irrigation-screen")
        composeRule.onNodeWithTag("irrigation-screen").assertIsDisplayed()
    }

    @Test
    fun farmWithoutIrrigationParcelsShowsEmptyStateWithoutOutboxCalls() {
        val initialOutbox = countOutboxRecords()
        enterMainShell()

        // Navegar a la finca de secano
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        openFarm(testFarmSecanoId)

        // Entrar a Riego en finca de secano
        openIrrigationFromFarmDetail()
        composeRule.onNodeWithText("Finca Cerro del Viento").assertIsDisplayed()

        // Pestaña Instalaciones debe mostrar que no hay parcelas de regadío
        composeRule.onNodeWithTag("irrigation-tab-installations").performClick()
        composeRule.onNodeWithTag("irrigation-installations-tab").performScrollToNode(hasTestTag("installations-empty"))
        composeRule.onNodeWithTag("installations-empty").assertIsDisplayed()

        // Comprobación de seguridad offline: navegar no debe haber creado ninguna entrada en outbox
        val finalOutbox = countOutboxRecords()
        assertEquals("Navegar y consultar datos locales no debe generar entradas de outbox", initialOutbox, finalOutbox)
    }

    @Test
    fun simulationModeFlowAllowsCalculatingAndClearingWithoutOutboxWrites() {
        val initialOutbox = countOutboxRecords()
        enterMainShell()

        // Navegar a Riego en finca de regadío
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        openFarm(testFarmIrrigatedId)
        openIrrigationFromFarmDetail()

        // Pestaña Instalaciones
        composeRule.onNodeWithTag("irrigation-tab-installations").performClick()
        waitForTag("dose-calculator-card")
        composeRule.onNodeWithTag("dose-calculator-card").performScrollTo().assertIsDisplayed()

        // Por defecto, sin valores por defecto inventados ni banner de simulación
        composeRule.onAllNodes(hasTestTag("simulation-mode-banner")).assertCountEquals(0)
        composeRule.onAllNodes(hasTestTag("calc-results-box")).assertCountEquals(0)

        // Pulsar "Cargar ejemplo simulación"
        composeRule.onNodeWithTag("load-simulation-button").performScrollTo().performClick()

        // Debe activarse el banner de simulación y el resultado teórico
        waitForTag("simulation-mode-banner")
        composeRule.onNodeWithTag("simulation-mode-banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("calc-results-box").performScrollTo().assertIsDisplayed()

        // Limpiar la simulación
        composeRule.onNodeWithTag("clear-simulation-button").performScrollTo().performClick()

        // Debe desaparecer el banner y el resultado
        composeRule.onAllNodes(hasTestTag("simulation-mode-banner")).assertCountEquals(0)
        composeRule.onAllNodes(hasTestTag("calc-results-box")).assertCountEquals(0)

        // Verificar que interactuar con la calculadora y simulación es 100% offline y no toca el outbox
        val finalOutbox = countOutboxRecords()
        assertEquals("Cálculos y simulaciones no deben generar escrituras de sincronización", initialOutbox, finalOutbox)
    }

    @Test
    fun activityRecreationPreservesIrrigationData() {
        enterMainShell()

        // Navegar a Riego
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        openFarm(testFarmIrrigatedId)
        openIrrigationFromFarmDetail()

        waitForTag("irrigation-screen")
        composeRule.onNodeWithTag("irrigation-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Finca Huerta del Pozo").assertIsDisplayed()

        // Recrear Activity (cambio de configuración / rotación / reinicio de ciclo de vida)
        composeRule.activityRule.scenario.recreate()

        // Verificar que la pantalla se restablece intacta
        waitForTag("irrigation-screen")
        composeRule.onNodeWithTag("irrigation-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Finca Huerta del Pozo").assertIsDisplayed()
    }
}
