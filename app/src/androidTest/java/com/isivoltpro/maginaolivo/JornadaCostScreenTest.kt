package com.isivoltpro.maginaolivo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.AppDispatchers
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import com.isivoltpro.maginaolivo.data.local.entity.FarmEntity
import com.isivoltpro.maginaolivo.data.local.entity.HarvestEntity
import com.isivoltpro.maginaolivo.data.local.entity.LocalMetadata
import com.isivoltpro.maginaolivo.data.local.entity.WorkspaceEntity
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstExpenseRepository
import com.isivoltpro.maginaolivo.domain.expense.DayCostRole
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.ExpenseCategory
import com.isivoltpro.maginaolivo.domain.expense.ExpenseDraft
import com.isivoltpro.maginaolivo.domain.expense.ExpenseOrigin
import com.isivoltpro.maginaolivo.domain.expense.ExpenseStatus
import com.isivoltpro.maginaolivo.domain.expense.JornadaExpenseKind
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.labour.LabourEntry
import com.isivoltpro.maginaolivo.domain.labour.LabourUnit
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailScreen
import com.isivoltpro.maginaolivo.feature.harvests.HarvestDetailUiState
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Phase 19F — a recollection cost in three taps, read back from the ledger. */
class JornadaCostScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val harvest = Harvest(
        id = UUID.randomUUID(), workspaceId = UUID.randomUUID(), farmId = UUID.randomUUID(), campaignId = UUID.randomUUID(),
        harvestDate = LocalDate.of(2026, 11, 27), totalGrams = 1_000_000, shares = emptyList(), collectionMethod = null,
        workerCount = null, machineryText = null, notes = null, version = 1,
    )

    @Test fun kindAmountSave() {
        val saved = AtomicReference<Triple<JornadaExpenseKind, Long, Boolean>?>(null)
        val callCount = AtomicInteger(0)
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest),
                    onUpdate = {},
                    onDelete = {},
                    onAddCost = { kind, minor, _, photo, _ ->
                        callCount.incrementAndGet()
                        saved.set(Triple(kind, minor, photo))
                    },
                )
            }
        }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-add-cost").performScrollTo().performClick()
        composeRule.onNodeWithTag("cost-save").assertIsNotEnabled()
        // The sheet can still be settling: bring each control into view and let the choice land
        // before the next step, so a tap never falls outside the sheet.
        composeRule.onNodeWithTag("cost-kind-RENTAL").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("cost-kind-RENTAL") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cost-amount").performScrollTo().performTextInput("120")
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cost-save").performScrollTo()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("cost-save") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cost-save").performClick()
        // The button is immediately disabled by the busy latch, preventing double clicks
        composeRule.onNodeWithTag("cost-save").assertIsNotEnabled()
        composeRule.waitUntil(10_000) { saved.get() != null }
        composeRule.runOnIdle {
            assertEquals(Triple(JornadaExpenseKind.RENTAL, 12_000L, false), saved.get())
            assertEquals(1, callCount.get())
        }
    }

    @Test fun kindAmountSaveWithPhoto() {
        val saved = AtomicReference<Triple<JornadaExpenseKind, Long, Boolean>?>(null)
        val callCount = AtomicInteger(0)
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest),
                    onUpdate = {},
                    onDelete = {},
                    onAddCost = { kind, minor, _, photo, _ ->
                        callCount.incrementAndGet()
                        saved.set(Triple(kind, minor, photo))
                    },
                )
            }
        }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-add-cost").performScrollTo().performClick()
        composeRule.onNodeWithTag("cost-save-photo").assertIsNotEnabled()
        composeRule.onNodeWithTag("cost-kind-DIESEL").performScrollTo().performClick()
        composeRule.onNodeWithTag("cost-amount").performScrollTo().performTextInput("50")
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cost-save-photo").performScrollTo()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("cost-save-photo") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cost-save-photo").performClick()
        // Button immediately becomes disabled to prevent rapid second invocation
        composeRule.onNodeWithTag("cost-save-photo").assertIsNotEnabled()
        composeRule.waitUntil(10_000) { saved.get() != null }
        composeRule.runOnIdle {
            assertEquals(Triple(JornadaExpenseKind.DIESEL, 5_000L, true), saved.get())
            assertEquals(1, callCount.get())
        }
    }

    @Test fun costSaveErrorAllowsCorrectionAndRetry() {
        var isSavingState by mutableStateOf(false)
        var errorState by mutableStateOf<String?>(null)
        val callCount = AtomicInteger(0)
        val lastSaved = AtomicReference<Long?>(null)

        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(
                        isLoading = false,
                        harvest = harvest,
                        isSaving = isSavingState,
                        costError = errorState,
                    ),
                    onUpdate = {},
                    onDelete = {},
                    onAddCost = { _, minor, _, _, _ ->
                        val count = callCount.incrementAndGet()
                        if (count == 1) {
                            isSavingState = false
                            errorState = "Error al guardar el gasto"
                        } else {
                            isSavingState = false
                            errorState = null
                            lastSaved.set(minor)
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-add-cost").performScrollTo().performClick()
        composeRule.onNodeWithTag("cost-amount").performScrollTo().performTextInput("40")
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        composeRule.waitForIdle()

        // First click -> fails with errorState
        composeRule.onNodeWithTag("cost-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { errorState != null }
        assertEquals(1, callCount.get())

        // Modal remains open and displays the error in the sheet
        composeRule.onNodeWithTag("cost-sheet-error").assertExists().assertTextContains("Error al guardar el gasto")

        // User corrects the amount and the save button is enabled
        composeRule.onNodeWithTag("cost-amount").performScrollTo().performTextReplacement("60")
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cost-save").performScrollTo()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("cost-save") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }

        // Retry succeeds
        composeRule.onNodeWithTag("cost-save").performClick()
        composeRule.waitUntil(5_000) { lastSaved.get() != null }
        assertEquals(2, callCount.get())
        assertEquals(6_000L, lastSaved.get())
    }

    @Test fun singleExpensePersistedInRoomPerSaveOperation() = runBlocking {
        val dbName = "cost-persist-${UUID.randomUUID()}.db"
        val db = MaginaOlivoDatabase.create(ApplicationProvider.getApplicationContext(), dbName)
        try {
            val workspaceId = UUID.randomUUID()
            val farmId = UUID.randomUUID()
            val campaignId = UUID.randomUUID()
            val harvestId = UUID.randomUUID()
            val now = Instant.now()
            val meta = LocalMetadata(now, now)
            db.workspaceDao().upsert(WorkspaceEntity(workspaceId, "Finca", UUID.randomUUID(), "ES", "Europe/Madrid", "es-ES", "EUR", meta))
            db.farmDao().upsert(FarmEntity(farmId, workspaceId, "Finca", metadata = meta))
            db.campaignDao().upsert(CampaignEntity(campaignId, workspaceId, farmId, "2026/27", LocalDate.now(), null, CampaignStatus.HARVEST, metadata = meta))
            db.harvestDao().upsert(HarvestEntity(harvestId, workspaceId, campaignId, farmId, LocalDate.now(), 1000L, metadata = meta))

            val workspaces = object : WorkspaceRepository {
                override suspend fun ensureLocalWorkspace() = AppResult.Success(workspaceId)
            }
            val clock = object : AppClock {
                override fun nowInstant() = now
                override fun today(zoneId: ZoneId) = LocalDate.now()
            }
            val ids = object : IdGenerator { override fun newId() = UUID.randomUUID() }
            val dispatchers = object : AppDispatchers {
                override val io = Dispatchers.IO
                override val default = Dispatchers.Default
                override val main = Dispatchers.Main
            }
            val repo = OfflineFirstExpenseRepository(db, workspaces, clock, ids, dispatchers)
            val draft = ExpenseDraft(
                expenseDate = LocalDate.now(),
                concept = "Gasóleo",
                category = ExpenseCategory.FUEL,
                amountMinor = 12_000L,
                currency = "EUR",
                farmId = farmId,
                campaignId = campaignId,
                harvestId = harvestId,
                dayCostRole = DayCostRole.ADDITIVE,
            )

            val res = repo.create(draft)
            assertTrue(res is AppResult.Success)

            val count = db.openHelper.writableDatabase.query("SELECT count(*) FROM expenses WHERE harvest_id='$harvestId'").use {
                it.moveToFirst()
                it.getInt(0)
            }
            assertEquals(1, count)
        } finally {
            db.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(dbName)
        }
    }

    /** #475: with the day's jornales already calculated, the farmer answers how the cost counts. */
    @Test fun aCostThatCouldReplaceTheCalculationAsksHowItCounts() {
        val saved = AtomicReference<Pair<JornadaExpenseKind, DayCostRole>?>(null)
        val calculated = expense(35_000, ExpenseStatus.POSTED, "Jornales (calculado)")
            .copy(origin = ExpenseOrigin.DAY_LABOUR, category = ExpenseCategory.LABOR)
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest, costs = listOf(calculated)),
                    onUpdate = {},
                    onDelete = {},
                    onAddCost = { kind, _, _, _, role -> saved.set(kind to role) },
                )
            }
        }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-add-cost").performScrollTo().performClick()
        composeRule.onNodeWithTag("cost-kind-LABOUR").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("cost-kind-LABOUR") and isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cost-amount").performScrollTo().performTextInput("300")
        runCatching { androidx.test.espresso.Espresso.closeSoftKeyboard() }
        composeRule.waitForIdle()
        // Nothing is assumed: until the farmer answers, it cannot be saved.
        composeRule.onNodeWithTag("cost-save").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("cost-role-REPLACEMENT").performScrollTo().performClick()
        if (composeRule.onAllNodes(hasTestTag("cost-role-REPLACEMENT") and isSelected()).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("cost-role-REPLACEMENT").performScrollTo().performClick()
        }
        // The explanatory row is conditional on the selected role; allow Compose to settle.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("cost-role-replaces-labour").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cost-role-replaces-labour").performScrollTo().assertExists()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("cost-save") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cost-save").performScrollTo().performClick()
        if (saved.get() == null) {
            runCatching { composeRule.waitUntil(1_500) { saved.get() != null } }
        }
        if (saved.get() == null && composeRule.onAllNodes(hasTestTag("cost-save") and isEnabled()).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("cost-save").performScrollTo().performClick()
        }
        composeRule.waitUntil(5_000) { saved.get() != null }
        composeRule.runOnIdle { assertEquals(JornadaExpenseKind.LABOUR to DayCostRole.REPLACEMENT, saved.get()) }
    }

    /** #475: with payments per person, jornales only add; diesel never asks. */
    @Test fun paidJornalesOnlyAddAndDieselNeverAsks() {
        val worker = UUID.randomUUID()
        val calculated = expense(35_000, ExpenseStatus.POSTED, "Jornales (calculado)")
            .copy(origin = ExpenseOrigin.DAY_LABOUR, category = ExpenseCategory.LABOR)
        val entry = LabourEntry(UUID.randomUUID(), harvest.id, worker, "Juan", 1, LabourUnit.FULL_DAY, null, 1)
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(
                    state = HarvestDetailUiState(isLoading = false, harvest = harvest, costs = listOf(calculated),
                        labour = listOf(entry), paidWorkers = setOf(worker)),
                    onUpdate = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-add-cost").performScrollTo().performClick()
        composeRule.onNodeWithTag("cost-role-ADDITIVE").assertDoesNotExist()
        composeRule.onNodeWithTag("cost-kind-LABOUR").performScrollTo().performClick()
        composeRule.onNodeWithTag("cost-role-labour-paid").performScrollTo().assertExists()
        composeRule.onNodeWithTag("cost-role-REPLACEMENT").assertDoesNotExist()
    }

    @Test fun theTotalIsThePostedLedgerRowsOnly() {
        val costs = listOf(
            expense(4_550, ExpenseStatus.POSTED, "Gasoil"),
            expense(12_000, ExpenseStatus.POSTED, "Alquiler vibradora"),
            expense(9_999, ExpenseStatus.DRAFT, "Tique por revisar"),
        )
        composeRule.setContent {
            MaginaOlivoTheme {
                HarvestDetailScreen(state = HarvestDetailUiState(isLoading = false, harvest = harvest, costs = costs), onUpdate = {}, onDelete = {})
            }
        }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-cost-total").assertTextContains("165,50", substring = true)
        composeRule.onNodeWithTag("jornada-cost-total").assertTextContains("1 borrador sin contar", substring = true)
    }

    @Test fun historicalDetailKeepsUnsupportedCodeAndOverflowUnavailable() {
        val costs = listOf(expense(Long.MAX_VALUE, ExpenseStatus.POSTED, "Gasoil"),
            expense(1, ExpenseStatus.POSTED, "Transporte"),
            expense(123, ExpenseStatus.POSTED, "Histórico").copy(currency = "INVALID"))
        composeRule.setContent { MaginaOlivoTheme {
            HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = harvest, costs = costs), {}, {})
        } }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-cost-total").assertTextContains("Importe no disponible (EUR)", substring = true)
            .assertTextContains("Importe no disponible (INVALID)", substring = true)
        composeRule.onAllNodesWithTag("jornada-cost").assertCountEquals(3)
    }

    @Test fun failedCostReadHidesStaleRowsAndCollisionMutations() {
        val manual = expense(1000, ExpenseStatus.POSTED, "Jornales")
        val calculated = expense(2000, ExpenseStatus.DRAFT, "Calculado").copy(origin = ExpenseOrigin.DAY_LABOUR)
        composeRule.setContent { MaginaOlivoTheme {
            HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = harvest,
                costs = listOf(manual, calculated), unlinkedCosts = listOf(manual.copy(harvestId = null)),
                costsLoaded = true, costsReadFailed = true), {}, {})
        } }
        composeRule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        composeRule.onNodeWithTag("jornada-costs-read-error").assertExists()
        composeRule.onNodeWithTag("jornada-cost-total").assertDoesNotExist()
        composeRule.onAllNodesWithTag("jornada-cost").assertCountEquals(0)
        composeRule.onNodeWithTag("jornada-prefer-calculated").assertDoesNotExist()
        composeRule.onNodeWithTag("jornada-link-cost").assertDoesNotExist()
    }

    private fun expense(minor: Long, status: ExpenseStatus, concept: String) = Expense(
        id = UUID.randomUUID(), workspaceId = harvest.workspaceId, expenseDate = harvest.harvestDate, concept = concept,
        category = ExpenseCategory.FUEL, amountMinor = minor, currency = "EUR", status = status, origin = ExpenseOrigin.MANUAL,
        harvestId = harvest.id,
    )
}
