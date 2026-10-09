package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.app.InMemoryActiveFarmStore
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.campaign.CampaignRepository
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository
import com.isivoltpro.maginaolivo.domain.parcel.Parcel
import com.isivoltpro.maginaolivo.domain.parcel.ParcelRepository
import com.isivoltpro.maginaolivo.domain.parcel.ParcelSource
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.feature.notebook.GlobalQuickActions
import com.isivoltpro.maginaolivo.feature.notebook.NotebookQuickAction
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Observable context changes, not database mocks of an agricultural write. All writers reject calls. */
class GlobalQuickActionsTest {
    @get:Rule val rule = createComposeRule()
    private val workspace = UUID.randomUUID()
    private val a = farm("A")
    private val b = farm("B")
    private val p = Parcel(UUID.randomUUID(), workspace, a.id, "P1", null, null, null, null, null,
        ParcelSource.MANUAL, null, null, null, null, null, 1)
    private val running = campaign("Operativa", CampaignStatus.ACTIVE)
    private val historical = campaign("Histórica", CampaignStatus.CLOSED)
    private val farms = MutableStateFlow(listOf(a, b))
    private val parcels = MutableStateFlow<Parcel?>(p)
    private val campaigns = MutableStateFlow(listOf(historical, running))
    private val store = InMemoryActiveFarmStore(a.id)
    private val opened = mutableListOf<Opened>()
    private lateinit var database: MaginaOlivoDatabase
    private lateinit var persistence: LocalPersistence
    private var campaignMode = "normal"

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), MaginaOlivoDatabase::class.java).build()
        val farmRepository = object : FarmRepository by rejectCalls<FarmRepository>() {
            override fun observeActive(workspaceId: UUID): Flow<List<Farm>> {
                assertEquals(workspace, workspaceId)
                return farms
            }
        }
        val parcelRepository = object : ParcelRepository by rejectCalls<ParcelRepository>() {
            override fun observeById(parcelId: UUID): Flow<Parcel?> = parcels
        }
        val campaignRepository = object : CampaignRepository by rejectCalls<CampaignRepository>() {
            override fun observe(id: UUID): Flow<Campaign?> = flowOf(historical)
            override fun observeForFarm(farmId: UUID): Flow<List<Campaign>> = when (campaignMode) {
                "loading" -> flow { awaitCancellation() }
                "error" -> flow { throw IllegalStateException("read unavailable") }
                else -> campaigns
            }
        }
        persistence = LocalPersistence(database, farmRepository, rejectCalls(), parcelRepository, campaignRepository,
            rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(), rejectCalls(),
            rejectCalls(), rejectCalls(), rejectCalls(), object : WorkspaceRepository {
                override suspend fun ensureLocalWorkspace(): AppResult<UUID> = AppResult.Success(workspace)
            })
    }

    @After fun cleanup() { database.close() }

    @Test fun archivingTheParcelWhileTheSheetIsOpenDropsItsContext() {
        show(parcel = p.id)
        rule.onNodeWithText("Parcela: P1").assertExists()
        rule.runOnIdle { parcels.value = p.copy(archivedAt = Instant.now()) }
        awaitNoParcel()
        work()
        assertEquals(null, opened.single().parcel)
    }

    @Test fun movingTheParcelToAnotherFarmWhileOpenDropsItsContext() {
        show(parcel = p.id)
        rule.onNodeWithText("Parcela: P1").assertExists()
        rule.runOnIdle { parcels.value = p.copy(farmId = b.id) }
        awaitNoParcel()
        work()
        assertEquals(null, opened.single().parcel)
    }

    @Test fun changingFarmDropsThePreviousParcel() {
        show(parcel = p.id)
        rule.onNodeWithText("Cambiar finca").performClick()
        rule.onNodeWithText("B").performClick()
        awaitNoParcel()
        work()
        assertEquals(b.id, opened.single().farm)
        assertEquals(null, opened.single().parcel)
    }

    @Test fun aForeignContextDoesNotBorrowTheStoredFarm() {
        show(farm = UUID.randomUUID())
        rule.onNodeWithText("Elige la finca de este registro").assertExists()
        rule.onNodeWithTag("notebook-quick-work").assertDoesNotExist()
        assertTrue(opened.isEmpty())
    }

    @Test fun anInvalidStoredFarmRequiresExplicitSelection() {
        store.set(UUID.randomUUID())
        show(farm = null)
        rule.onNodeWithText("Elige la finca de este registro").assertExists()
        rule.onNodeWithText("B").performClick()
        work()
        assertEquals(b.id, opened.single().farm)
    }

    @Test fun archivingTheChosenFarmImmediatelyStopsItsActions() {
        show()
        rule.onNodeWithTag("notebook-quick-work").assertExists()
        rule.runOnIdle { farms.value = listOf(b) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Elige la finca de este registro").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("notebook-quick-work").assertDoesNotExist()
        assertTrue(opened.isEmpty())
    }

    @Test fun aHistoricalCampaignStillUsesTheOperationalCampaignForLabour() {
        show(farm = null, campaign = historical.id)
        rule.onNodeWithTag("notebook-quick-labour").performScrollTo().performClick()
        assertEquals(a.id, opened.single().farm)
        assertTrue(opened.single().running)
    }

    @Test fun closedCampaignDoesNotBecomeAnOperationalLabourDay() {
        campaigns.value = listOf(historical)
        show()
        rule.onNodeWithTag("notebook-quick-labour").performScrollTo().performClick()
        assertEquals(false, opened.single().running)
    }

    @Test fun loadingCampaignIsUnknownAndBlocksLabourAndWeighing() {
        campaignMode = "loading"
        show()
        rule.onNodeWithTag("notebook-quick-labour").assertIsNotEnabled()
        rule.onNodeWithTag("notebook-quick-weighing").assertIsNotEnabled()
        rule.onNodeWithTag("notebook-quick-work").assertIsEnabled()
        assertTrue(opened.isEmpty())
    }

    @Test fun failedCampaignIsUnknownAndBlocksLabourAndWeighing() {
        campaignMode = "error"
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("No pudimos consultar la campaña").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("notebook-quick-labour").assertIsNotEnabled()
        rule.onNodeWithTag("notebook-quick-weighing").assertIsNotEnabled()
        assertTrue(opened.isEmpty())
    }

    private fun show(farm: UUID? = a.id, parcel: UUID? = null, campaign: UUID? = null) {
        rule.setContent { MaginaOlivoTheme {
            GlobalQuickActions(persistence, store, farm, parcel, campaign, {}, { action, id, active, parcelId ->
                opened += Opened(action, id, active, parcelId)
            })
        } }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Elige la finca de este registro").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Cambiar finca").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    private fun awaitNoParcel() {
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Parcela: P1").fetchSemanticsNodes().isEmpty() }
    }
    private fun work() { rule.onNodeWithTag("notebook-quick-work").performScrollTo().performClick() }
    private fun farm(name: String) = Farm(UUID.randomUUID(), workspace, name, null, null, null, null, null, 1, null, null, null, 1)
    private fun campaign(name: String, status: CampaignStatus) = Campaign(UUID.randomUUID(), workspace, a.id, name,
        LocalDate.of(2026, 10, 1), null, status, null, emptyList(), 1)
    private data class Opened(val action: NotebookQuickAction, val farm: UUID, val running: Boolean, val parcel: UUID?)
    private inline fun <reified T> rejectCalls(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> throw AssertionError("Unexpected persistence call: ${method.name}") } as T
}
