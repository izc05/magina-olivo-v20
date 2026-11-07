package com.isivoltpro.maginaolivo.feature.harvests

import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.harvest.*
import com.isivoltpro.maginaolivo.domain.labour.*
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.parcel.*
import com.isivoltpro.maginaolivo.domain.cycle.*
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.feature.expenses.*
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.navigation.*
import java.io.File
import com.isivoltpro.maginaolivo.app.AppearanceMode
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuickLabourFormTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun campaignJornalCanAddWorkerWithAgriculturalCycleContext() {
        val farm = UUID.randomUUID()
        val campaign = UUID.randomUUID()
        val scope = CycleScope(UUID.randomUUID(), farm, null, 1)
        val cycle = AgriculturalCycle(UUID.randomUUID(), scope.workspaceId, farm, "2026/27",
            LocalDate.of(2026, 1, 1), null, null, false, 1)
        val repository = object : AgriculturalCycleRepository {
            override fun observe(farmId: UUID) = flowOf(AppResult.Success(CycleSnapshot(scope, listOf(cycle), emptyList())))
            override suspend fun create(scope: CycleScope, label: String, start: LocalDate, operationId: UUID): AppResult<UUID> = error("Unused")
            override suspend fun previewClose(cycleId: UUID, end: LocalDate, noRecollection: Boolean): AppResult<CycleClosePreview> = error("Unused")
            override suspend fun close(preview: CycleClosePreview, operationId: UUID): AppResult<Unit> = error("Unused")
            override suspend fun assign(scope: CycleScope, cycleId: UUID, kind: CycleRecordKind, recordId: UUID,
                reason: String, operationId: UUID): AppResult<Unit> = error("Unused")
        }
        val workers = mutableStateOf(emptyList<Worker>())
        val draft = mutableStateOf(ExpenseForm(date = "2026-10-13", farmId = farm))
        val contexts = listOf(HarvestContext(farm, "Finca", campaign, "2026/27", CampaignStatus.ACTIVE,
            LocalDate.of(2026, 1, 1), emptyList()))
        compose.setContent { MaginaOlivoTheme(AppearanceMode.LIGHT) {
            QuickLabourScreen(farm, draft, RelationOptions(campaignsFor = farm), contexts, workers.value,
                null, true, false, ExpenseFormErrors(), null, {}, { _, _, _ -> },
                { name -> workers.value = workers.value + Worker(UUID.randomUUID(), name) }, {},
                agriculturalCycles = repository)
        } }
        scopeControl("quick-labour-campaign").performClick()
        compose.onNodeWithTag("labour-new-person").performScrollTo().performClick()
        compose.onNodeWithTag("labour-new-name").performScrollTo().performTextInput("Persona QA")
        closeKeyboardAndWait()
        compose.onNodeWithTag("labour-add-worker").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("labour-worker").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("labour-worker").performScrollTo().assertIsDisplayed()
    }
    /**
     * On a cold device the first IME can appear after the text input; the form then switches to
     * keyboard scrolling while a click is dispatched. Touch only once the real insets say it is hidden.
     */
    private fun closeKeyboardAndWait() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(15_000) {
            var hidden = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view = compose.activity.window.decorView
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
                hidden = view.hasWindowFocus() && insets != null &&
                    !insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            }
            hidden
        }
        compose.waitForIdle()
    }
    private fun cycleRepository(snapshot: CycleSnapshot) = object : AgriculturalCycleRepository {
        override fun observe(farmId: UUID) = flowOf(AppResult.Success(snapshot))
        override suspend fun create(scope: CycleScope, label: String, start: LocalDate, operationId: UUID): AppResult<UUID> = error("Unused")
        override suspend fun previewClose(cycleId: UUID, end: LocalDate, noRecollection: Boolean): AppResult<CycleClosePreview> = error("Unused")
        override suspend fun close(preview: CycleClosePreview, operationId: UUID): AppResult<Unit> = error("Unused")
        override suspend fun assign(scope: CycleScope, cycleId: UUID, kind: CycleRecordKind, recordId: UUID,
            reason: String, operationId: UUID): AppResult<Unit> = error("Unused")
    }
    private fun campaignJornal(closed: Boolean, linked: Boolean) {
        val farm = UUID.randomUUID()
        val campaign = UUID.randomUUID()
        val scope = CycleScope(UUID.randomUUID(), farm, null, 1)
        val cycle = AgriculturalCycle(UUID.randomUUID(), scope.workspaceId, farm, "2026/27", LocalDate.of(2026, 1, 1),
            if (closed) LocalDate.of(2026, 9, 30) else null, null, false, 1)
        val record = CycleRecord(campaign, "RECOLLECTION", "Recogida", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 2),
            cycle.id.takeIf { linked }, "ACTIVE", 1)
        val workers = mutableStateOf(emptyList<Worker>())
        val draft = mutableStateOf(ExpenseForm(date = "2026-10-13", farmId = farm))
        val contexts = listOf(HarvestContext(farm, "Finca", campaign, "2026/27", CampaignStatus.ACTIVE,
            LocalDate.of(2026, 1, 1), emptyList()))
        compose.setContent { MaginaOlivoTheme(AppearanceMode.LIGHT) {
            QuickLabourScreen(farm, draft, RelationOptions(campaignsFor = farm), contexts, workers.value,
                null, true, false, ExpenseFormErrors(), null, {}, { _, _, _ -> },
                { name -> workers.value = workers.value + Worker(UUID.randomUUID(), name) }, {},
                agriculturalCycles = cycleRepository(CycleSnapshot(scope, listOf(cycle), listOf(record))))
        } }
        scopeControl("quick-labour-campaign").performClick()
    }
    @Test fun campaignJornalShowsACompactNoticeWhenItsCycleIsClosedAndStillReachesTheWorkerForm() {
        campaignJornal(closed = true, linked = true)
        compose.onNodeWithTag("cycle-parent-notice").assertIsDisplayed().assertTextContains("está cerrada", substring = true)
        compose.onNodeWithTag("cycle-write-choice").assertDoesNotExist()
        capture(false, "campaign-closed-cycle-notice")
        compose.onNodeWithTag("labour-new-person").performScrollTo().performClick()
        compose.onNodeWithTag("labour-new-name").performScrollTo().performTextInput("Persona QA")
        closeKeyboardAndWait()
        compose.onNodeWithTag("labour-add-worker").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("labour-worker").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun campaignJornalAsksToLinkTheRecollectionFirst() {
        campaignJornal(closed = false, linked = false)
        compose.onNodeWithTag("cycle-parent-notice").assertIsDisplayed()
            .assertTextContains("Vincula primero la recolección", substring = true)
        capture(false, "campaign-unlinked-recollection-notice")
    }
    @Test fun campaignJornalLinkedToTheOpenCycleShowsNoNotice() {
        campaignJornal(closed = false, linked = true)
        compose.onNodeWithTag("quick-labour-date").assertExists()
        compose.onNodeWithTag("cycle-parent-notice").assertDoesNotExist()
    }
    @Test fun lightScopeSwitchRetainsDraftsAndSavesCorrectContract() = verify(false)
    @Test fun darkScopeSwitchRetainsDraftsAndSavesCorrectContract() = verify(true)
    @Test fun firstConfirmedCurrencyDoesNotAskToConvertAnEmptyDraft() {
        val farm = UUID.randomUUID()
        val campaign = UUID.randomUUID()
        val date = LocalDate.of(2026, 10, 13)
        val loaded = mutableStateOf(false)
        val currency = mutableStateOf("EUR")
        val draft = mutableStateOf(ExpenseForm(date = date.toString(), farmId = farm))
        val contexts = listOf(HarvestContext(farm, "Fixture", campaign, "2026/27", CampaignStatus.ACTIVE,
            date, listOf(HarvestParcelOption(UUID.randomUUID(), "Fixture"))))
        compose.setContent { MaginaOlivoTheme(AppearanceMode.LIGHT) {
            QuickLabourScreen(farm, draft, RelationOptions(campaignsFor = farm), contexts, emptyList(), null,
                true, false, ExpenseFormErrors(), null, {}, { _, _, _ -> }, {}, {},
                campaignReady = loaded.value, currency = currency.value)
        } }
        scopeControl("quick-labour-campaign").performClick()
        compose.runOnIdle { currency.value = "JPY"; loaded.value = true }
        compose.onNodeWithTag("quick-labour-review-currency").assertDoesNotExist()
        compose.onNodeWithTag("labour-rate").performScrollTo().assertTextContains("JPY", substring = true)
    }
    /** The header can scroll with an open IME on a compact screen; touch its real visible control. */
    private fun scopeControl(tag: String): SemanticsNodeInteraction {
        compose.waitForIdle()
        val control = compose.onNodeWithTag(tag)
        if (runCatching { control.assertIsDisplayed() }.isFailure) control.performScrollTo()
        return control.assertIsDisplayed().assertIsEnabled()
    }
    private fun verify(dark: Boolean) {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.setDecorFitsSystemWindows(it.window, false)
            it.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val farm = UUID.randomUUID()
        val workspace = UUID.randomUUID()
        val parcel = UUID.randomUUID()
        val campaign = UUID.randomUUID()
        val worker = Worker(UUID.randomUUID(), "Persona sintética")
        val date = LocalDate.of(2026, 10, 13)
        val contexts = mutableStateOf(listOf(HarvestContext(farm, "Finca", campaign, "2026/27", CampaignStatus.ACTIVE,
            date.minusDays(2), listOf(HarvestParcelOption(UUID.randomUUID(), "Parcela")))))
        val draft = mutableStateOf(ExpenseForm(date = date.toString(), farmId = farm, parcelId = parcel, category = ExpenseCategory.LABOR, concept = "Jornal"))
        val options = RelationOptions(campaignsFor = farm,
            farms = listOf(Farm(farm, workspace, "Finca sintética", null, null, null, null, null, 1, null, null, null, 1)),
            parcels = listOf(Parcel(parcel, workspace, farm, "Parcela sintética", null, null, null, null, null,
                ParcelSource.MANUAL, null, null, null, null, null, 1)))
        var savedGeneral: ExpenseForm? = null
        var savedCrew: CrewDraft? = null
        val campaignReady = mutableStateOf(true)
        val currency = mutableStateOf("EUR")
        compose.setContent { MaginaOlivoTheme(if (dark) AppearanceMode.DARK else AppearanceMode.LIGHT) {
            Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
                MoBottomBar(bottomNavigationRoots.map { destination ->
                    MoBottomBarItem(destination.label, destination.symbol, destination.isPrimaryAction, when (destination) {
                        RootDestination.Home -> MoIcons.Home
                        RootDestination.Olivar -> MoIcons.Tree
                        RootDestination.Notebook -> MoIcons.Notebook
                        RootDestination.Profile -> MoIcons.Person
                        RootDestination.Assistant -> MoIcons.Assistant
                        RootDestination.CampaignHub -> MoIcons.Campaign
                        RootDestination.Alerts -> MoIcons.Bell
                    })
                }, selectedIndex = 2, onSelected = {}, onAddRecord = {})
            }) { padding -> Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            QuickLabourScreen(farm, draft, options, contexts.value,
                listOf(worker), RecollectionRates(hourlyMinor = 1200), ready = true, isSaving = false,
                errors = ExpenseFormErrors(), error = null,
                onSaveGeneral = { savedGeneral = it }, onSaveCrew = { _, _, it -> savedCrew = it },
                onAddWorker = { error("No person writes in UI fixture") }, onCancel = {},
                campaignReady = campaignReady.value, currency = currency.value)
            } }
        } }
        compose.onNodeWithText("Añadir jornal").assertIsDisplayed()
        compose.onNodeWithTag("quick-labour-farm-context").assertTextContains("Finca sintética · Parcela sintética")
        capture(dark, "general")
        scopeControl("quick-labour-general").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("expense-concept").performScrollTo().performTextClearance()
        compose.onNodeWithTag("expense-concept").performTextInput("Reparación de riego")
        compose.onNodeWithTag("expense-amount").performScrollTo().performTextInput("65")
        scopeControl("quick-labour-campaign").performClick()
        compose.onNodeWithTag("labour-worker").performScrollTo().performClick()
        compose.onNodeWithTag("labour-unit-HOURS").performScrollTo().performClick()
        compose.onNodeWithTag("labour-hours").performScrollTo().performTextInput("3")
        capture(dark, "campaign-hours")
        scopeControl("quick-labour-general").assertIsDisplayed()
        scopeControl("quick-labour-general").performClick()
        compose.onNodeWithTag("expense-concept").performScrollTo().assertTextContains("Reparación de riego")
        compose.onNodeWithTag("expense-amount").assertTextContains("65")
        compose.onNodeWithText("Guardar jornal general").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull(savedGeneral); assertNull(savedGeneral!!.campaignId); assertNull(savedGeneral!!.harvestId)
            assertEquals(ExpenseCategory.LABOR, savedGeneral!!.category); assertNull(savedCrew)
            assertEquals(parcel, savedGeneral!!.parcelId); assertEquals(farm, savedGeneral!!.farmId)
        }
        scopeControl("quick-labour-campaign").performClick()
        compose.onNodeWithTag("labour-hours").performScrollTo().assertTextContains("3")
        compose.runOnIdle { campaignReady.value = false }
        compose.onNodeWithTag("labour-save").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { campaignReady.value = true }
        compose.onNodeWithTag("labour-hours").performScrollTo().assertTextContains("3")
        compose.runOnIdle { draft.value = draft.value.copy(date = "") }
        compose.onNodeWithTag("labour-save").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { draft.value = draft.value.copy(date = date.toString()) }
        compose.onNodeWithTag("labour-hours").performScrollTo().assertTextContains("3")
        compose.runOnIdle { currency.value = "JPY" }
        compose.onNodeWithTag("labour-save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("quick-labour-review-currency").performClick()
        compose.onNodeWithTag("quick-labour-confirm-currency").performClick()
        compose.onNodeWithTag("labour-hours").performScrollTo().assertTextContains("3")
        compose.onNodeWithTag("labour-rate").performScrollTo().assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("labour-rate").performScrollTo().performTextInput("1200")
        scopeControl("quick-labour-general").performClick()
        scopeControl("quick-labour-campaign").performClick()
        compose.onNodeWithTag("labour-rate").performScrollTo().assertTextContains("1200")
        compose.onNodeWithTag("labour-hours").performScrollTo().assertTextContains("3")
        capture(dark, "revised-currency")
        compose.onNodeWithTag("labour-save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(worker.id), savedCrew!!.workerIds); assertEquals(180, savedCrew!!.minutes) }
        compose.runOnIdle { contexts.value = contexts.value.map { it.copy(campaignId = UUID.randomUUID(), campaignName = "2027/28") } }
        compose.onNodeWithTag("labour-save").assertDoesNotExist()
        compose.onNodeWithText("La campaña seleccionada ya no está disponible. El borrador se conserva.").assertIsDisplayed()
        compose.runOnIdle { contexts.value = emptyList() }
        compose.onNodeWithTag("quick-labour-campaign").assertDoesNotExist()
        compose.onNodeWithTag("labour-save").assertDoesNotExist()
        compose.onNodeWithTag("quick-labour-return-general").performClick()
        compose.onNodeWithText("Asignar a").assertDoesNotExist()
        compose.onNodeWithText("Guardar jornal general").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("expense-concept").performScrollTo().assertTextContains("Reparación de riego")
        capture(dark, "no-campaign")
        compose.runOnIdle { contexts.value = listOf(
            HarvestContext(farm, "Finca", UUID.randomUUID(), "2027/28", CampaignStatus.ACTIVE, date,
                listOf(HarvestParcelOption(UUID.randomUUID(), "Parcela"))),
            HarvestContext(farm, "Finca", UUID.randomUUID(), "2028/29", CampaignStatus.ACTIVE, date,
                listOf(HarvestParcelOption(UUID.randomUUID(), "Parcela")))) }
        compose.onNodeWithTag("quick-labour-campaign").assertDoesNotExist()
        compose.onNodeWithText("Hay varias campañas operativas. Abre la campaña concreta para registrar su jornal.").assertIsDisplayed()
    }
    private fun capture(dark: Boolean, stage: String) {
        compose.waitForIdle()
        val context = compose.activity
        val directory = File(context.getExternalFilesDir(null), "quick-labour-evidence").apply { mkdirs() }
        val configuration = context.resources.configuration
        val name = "${if (dark) "dark" else "light"}-$stage-sw${configuration.smallestScreenWidthDp}dp-font${configuration.fontScale}.png"
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
