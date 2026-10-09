package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.ocr.DocumentExtraction
import com.isivoltpro.maginaolivo.domain.ocr.DocumentType
import com.isivoltpro.maginaolivo.domain.ocr.OcrStatus
import com.isivoltpro.maginaolivo.domain.ocr.PurchaseProposal
import com.isivoltpro.maginaolivo.feature.expenses.DocumentReviewScreen
import com.isivoltpro.maginaolivo.feature.expenses.DocumentReviewUiState
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseEditor
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseForm
import com.isivoltpro.maginaolivo.feature.expenses.ExpenseFormErrors
import com.isivoltpro.maginaolivo.feature.expenses.ExpensesScreen
import com.isivoltpro.maginaolivo.feature.expenses.ExpensesUiState
import com.isivoltpro.maginaolivo.feature.expenses.RelationOptions
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** #411: a running Campaign never silently captures a Farm-level expense. */
class RecollectionChoiceUiTest {
    @get:Rule val rule = createComposeRule()
    private val date = LocalDate.of(2026, 11, 20)
    private val workspace = UUID.randomUUID()
    private val farm = farm("La Solana")
    private val otherFarm = farm("El Chaparral")
    private val running = campaign(farm.id, "2026/27", CampaignStatus.HARVEST)
    private val options = RelationOptions(farms = listOf(farm, otherFarm), campaigns = listOf(running), campaignsFor = farm.id)

    @Test fun creatingFromACampaignKeepsItsContextWithoutAnAmbiguousChoice() {
        var saved: ExpenseForm? = null
        campaignExpense { saved = it }
        rule.onNodeWithTag("expense-kind").assertDoesNotExist()
        rule.onNodeWithTag("expense-campaign-context").assertTextContains("2026/27", substring = true)
        rule.onNodeWithTag("expense-farm").assertDoesNotExist()
        rule.onNodeWithTag("expense-concept").performScrollTo().performTextInput("Gasoil de recogida")
        rule.onNodeWithTag("expense-amount").performScrollTo().performTextInput("40")
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle {
            assertNotNull(saved)
            assertEquals(farm.id, saved?.farmId)
            assertEquals(running.id, saved?.campaignId)
        }
    }

    @Test fun campaignContextOffersOnlyCompatibleWorksAndSurvivesRemovingTheLink() {
        fun work(name: String, campaignId: UUID?) = com.isivoltpro.maginaolivo.domain.activity.Activity(
            id = UUID.randomUUID(), workspaceId = workspace, farmId = farm.id, campaignId = campaignId,
            type = com.isivoltpro.maginaolivo.domain.activity.ActivityType.OTHER,
            status = com.isivoltpro.maginaolivo.data.local.model.ActivityStatus.COMPLETED,
            activityDate = date, description = name, notes = null, targets = emptyList(), version = 1,
        )
        val compatible = work("Transporte de recogida", running.id)
        val general = work("Poda general", null)
        val historical = work("Transporte de otra campaña", UUID.randomUUID())
        var saved: ExpenseForm? = null
        campaignExpense(options.copy(activities = listOf(compatible, general, historical))) { saved = it }
        rule.onNodeWithTag("expense-more-details").performScrollTo().performClick()
        rule.onNodeWithTag("expense-activity").performScrollTo().performClick()
        rule.onNodeWithTag("choice-${general.id}").assertDoesNotExist()
        rule.onNodeWithTag("choice-${historical.id}").assertDoesNotExist()
        rule.onNodeWithTag("choice-${compatible.id}").performClick()
        rule.onNodeWithTag("expense-work-campaign").performScrollTo().assertTextContains("2026/27", substring = true)
        rule.onNodeWithTag("expense-activity").performScrollTo().performClick()
        rule.onNodeWithTag("choice-none").performClick()
        rule.onNodeWithTag("expense-kind").assertDoesNotExist()
        rule.onNodeWithTag("expense-campaign-context").performScrollTo().assertTextContains("2026/27", substring = true)
        rule.onNodeWithTag("expense-concept").performScrollTo().performTextInput("Sacos de recogida")
        rule.onNodeWithTag("expense-amount").performScrollTo().performTextInput("20")
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle {
            assertNotNull(saved)
            assertEquals(farm.id, saved?.farmId)
            assertEquals(running.id, saved?.campaignId)
            assertNull(saved?.activityId)
        }
    }

    @Test fun campaignContextRemainsExplicitWhileCampaignOptionsLoad() {
        campaignExpense(options.copy(campaigns = emptyList(), campaignsFor = null)) {}
        rule.onNodeWithTag("expense-kind").assertDoesNotExist()
        rule.onNodeWithTag("expense-campaign-context").assertTextContains("Recogida", substring = true)
        rule.onNodeWithTag("expense-farm").assertDoesNotExist()
    }

    private fun campaignExpense(loaded: RelationOptions = options, onSave: (ExpenseForm) -> Unit) {
        rule.setContent { MaginaOlivoTheme {
            ExpensesScreen(
                ExpensesUiState(isLoading = false, options = loaded), date, onSave, {}, { _, _ -> }, {}, {}, {}, {},
                presetFarmId = farm.id, presetCampaignId = running.id,
            )
        } }
        rule.onNodeWithTag("add-expense").performClick()
    }

    @Test fun cuadernoGastoRequiresAnExplicitChoice() {
        rule.setContent {
            MaginaOlivoTheme {
                ExpensesScreen(
                    ExpensesUiState(isLoading = false, options = options), date, {}, {}, { _, _ -> }, {}, {}, {}, {},
                    presetFarmId = farm.id,
                )
            }
        }
        rule.onNodeWithTag("add-expense").performClick()
        rule.onNodeWithTag("expense-kind-recollection").assertIsNotSelected()
        rule.onNodeWithTag("expense-kind-general").assertIsNotSelected()
        rule.onNodeWithTag("expense-kind-required").assertExists()
        rule.onNodeWithTag("save-expense").performScrollTo().assertIsNotEnabled()
    }

    /** #415 QA 10/11: Cuaderno → Gasto opens the form in one tap and asks only Recogida/General. */
    @Test fun quickCuadernoGastoOpensTheFormAtOnce() {
        var done: Boolean? = null
        rule.setContent {
            MaginaOlivoTheme {
                ExpensesScreen(
                    ExpensesUiState(isLoading = false, options = options), date, {}, {}, { _, _ -> }, {}, {}, {}, {},
                    presetFarmId = farm.id, presetQuick = true, onContextDone = { done = it },
                )
            }
        }
        rule.onNodeWithTag("expense-kind-required").performScrollTo().assertExists()
        rule.onNodeWithTag("save-expense").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Cancelar").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(false, done) }
    }

    /** Owner #524: Cuaderno of Parcel A → Gasto says A and never asks it again; works offered are A's. */
    @Test fun aCuadernoParcelIsContextAndItsWorksAreTheOnlyOnes() {
        fun parcel(name: String) = com.isivoltpro.maginaolivo.domain.parcel.Parcel(
            id = UUID.randomUUID(), workspaceId = workspace, farmId = farm.id, displayName = name,
            cadastralReference = null, cadastralPolygon = null, cadastralParcel = null, municipality = null,
            province = null, source = com.isivoltpro.maginaolivo.domain.parcel.ParcelSource.MANUAL,
            geometryGeoJson = null, cadastralAreaM2 = null, managedAreaM2 = null, notes = null, archivedAt = null, version = 1,
        )
        fun work(name: String, on: com.isivoltpro.maginaolivo.domain.parcel.Parcel) = com.isivoltpro.maginaolivo.domain.activity.Activity(
            id = UUID.randomUUID(), workspaceId = workspace, farmId = farm.id, campaignId = null,
            type = com.isivoltpro.maginaolivo.domain.activity.ActivityType.PRUNING,
            status = com.isivoltpro.maginaolivo.data.local.model.ActivityStatus.COMPLETED,
            activityDate = date, description = name, notes = null,
            targets = listOf(com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget(on.id, on.displayName)),
            version = 1,
        )
        val llanos = parcel("Los Llanos")
        val cerro = parcel("El Cerro")
        val onLlanos = work("Poda Llanos", llanos)
        val onCerro = work("Poda Cerro", cerro)
        rule.setContent {
            MaginaOlivoTheme {
                ExpensesScreen(
                    ExpensesUiState(isLoading = false, options = options.copy(parcels = listOf(llanos, cerro), activities = listOf(onLlanos, onCerro))),
                    date, {}, {}, { _, _ -> }, {}, {}, {}, {},
                    presetFarmId = farm.id, presetParcelId = llanos.id, presetQuick = true,
                )
            }
        }
        rule.onNodeWithTag("expense-parcel-context").performScrollTo().assertTextContains("Los Llanos", substring = true)
        rule.onNodeWithTag("expense-more-details").performScrollTo().performClick()
        assertEquals(0, rule.onAllNodesWithTag("expense-parcel").fetchSemanticsNodes().size)
        rule.onNodeWithTag("expense-activity").performScrollTo().assertTextContains("Relacionado con", substring = true).performClick()
        rule.onNodeWithTag("choice-${onLlanos.id}").assertExists()
        assertEquals(0, rule.onAllNodesWithTag("choice-${onCerro.id}").fetchSemanticsNodes().size)
    }

    /** #415 QA 12: Gastos opened as a list does not open the form by itself. */
    @Test fun gastosAsAListStaysOnTheList() {
        rule.setContent {
            MaginaOlivoTheme {
                ExpensesScreen(
                    ExpensesUiState(isLoading = false, options = options), date, {}, {}, { _, _ -> }, {}, {}, {}, {},
                    presetFarmId = farm.id,
                )
            }
        }
        assertEquals(0, rule.onAllNodesWithTag("save-expense").fetchSemanticsNodes().size)
        rule.onNodeWithTag("add-expense").assertExists()
    }

    /** Codex #423: while the Farm's campaigns load, «no running campaign» is unknown, so no save yet. */
    @Test fun cuadernoGastoWaitsForTheFarmsCampaigns() {
        rule.setContent {
            MaginaOlivoTheme {
                ExpensesScreen(
                    ExpensesUiState(isLoading = false, options = options.copy(campaigns = emptyList(), campaignsFor = null)),
                    date, {}, {}, { _, _ -> }, {}, {}, {}, {},
                    presetFarmId = farm.id,
                )
            }
        }
        rule.onNodeWithTag("add-expense").performClick()
        rule.onNodeWithTag("expense-kind-loading").performScrollTo().assertExists()
        rule.onNodeWithTag("save-expense").performScrollTo().assertIsNotEnabled()
    }

    @Test fun farmerCanSwitchToAGeneralFarmExpense() {
        val saved = cuadernoExpense(options) {
            rule.onNodeWithTag("expense-kind-general").performClick().assertIsSelected()
            rule.onNodeWithTag("expense-kind-recollection").assertIsNotSelected()
        }
        rule.runOnIdle { assertNotNull(saved()); assertNull(saved()?.campaignId); assertEquals(farm.id, saved()?.farmId) }
    }

    @Test fun withoutARunningRecolectionNoChoiceIsShownAndTheExpenseIsGeneral() {
        val closed = campaign(farm.id, "2025/26", CampaignStatus.CLOSED)
        val saved = cuadernoExpense(options.copy(campaigns = listOf(closed)), expectChoice = false) {}
        rule.runOnIdle { assertNotNull(saved()); assertNull(saved()?.campaignId) }
    }

    @Test fun editingAGeneralExpenseNeverReassignsIt() {
        var saved: ExpenseForm? = null
        editor(ExpenseForm(date.toString(), "25", "Poda", farmId = farm.id)) { saved = it }
        rule.onNodeWithTag("expense-kind-general").assertIsSelected()
        rule.onNodeWithTag("expense-kind-recollection").assertIsNotSelected()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle { assertNotNull(saved); assertNull(saved?.campaignId) }
    }

    @Test fun changingTheFarmClearsTheCampaign() {
        var saved: ExpenseForm? = null
        editor(ExpenseForm(date.toString(), "40", "Gasoil", farmId = farm.id, campaignId = running.id), preselect = true) { saved = it }
        rule.onNodeWithTag("expense-kind-recollection").assertIsSelected()
        rule.onNodeWithTag("expense-farm").performScrollTo().performClick()
        rule.onNodeWithTag("choice-${otherFarm.id}").performClick()
        rule.onNodeWithTag("expense-kind").assertDoesNotExist()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(otherFarm.id, saved?.farmId); assertNull(saved?.campaignId) }
    }

    @Test fun ticketFromRecollectionExpensesKeepsFarmAndCampaignThroughReview() {
        val reviewed = review(contextCampaignId = running.id, preselect = false)
        rule.runOnIdle { assertEquals(farm.id, reviewed()?.farmId); assertEquals(running.id, reviewed()?.campaignId) }
    }

    @Test fun ticketFromCuadernoGastoStartsOnTheRunningRecolection() {
        val reviewed = review(contextCampaignId = null, preselect = true)
        rule.runOnIdle { assertEquals(farm.id, reviewed()?.farmId); assertEquals(running.id, reviewed()?.campaignId) }
    }

    private fun cuadernoExpense(options: RelationOptions, expectChoice: Boolean = true, choose: () -> Unit): () -> ExpenseForm? {
        var saved: ExpenseForm? = null
        rule.setContent {
            MaginaOlivoTheme {
                ExpensesScreen(
                    ExpensesUiState(isLoading = false, options = options), date, { saved = it }, {}, { _, _ -> }, {}, {}, {}, {},
                    presetFarmId = farm.id,
                )
            }
        }
        rule.onNodeWithTag("add-expense").performClick()
        if (expectChoice) {
            rule.onNodeWithText("Recogida · Campaña 2026/27").assertExists()
            rule.onNodeWithTag("expense-kind-recollection").assertIsNotSelected()
            rule.onNodeWithTag("expense-kind-general").assertIsNotSelected()
        } else {
            rule.onNodeWithTag("expense-kind").assertDoesNotExist()
        }
        choose()
        rule.onNodeWithTag("expense-concept").performTextInput("Gasoil")
        rule.onNodeWithTag("expense-amount").performTextInput("40")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        return { saved }
    }

    /** #375: opened on a Farm already chosen (Cuaderno, campaign), the Farm is context only. */
    @Test fun aFarmFromContextIsShownNotAsked() {
        var saved: ExpenseForm? = null
        editor(ExpenseForm(date.toString(), "12", "Gasoil", farmId = farm.id), farmLocked = true) { saved = it }
        rule.onNodeWithTag("expense-farm").assertDoesNotExist()
        rule.onNodeWithTag("expense-farm-context").performScrollTo().assertTextContains(farm.name)
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(farm.id, saved?.farmId) }
    }

    /** Codex #405: before the Farms load, a locked Farm is still not a selector. */
    @Test fun aLockedFarmIsNotSelectableWhileLoading() {
        editor(ExpenseForm(date.toString(), "12", "Gasoil", farmId = farm.id), farmLocked = true, loaded = options.copy(farms = emptyList())) {}
        rule.onNodeWithTag("expense-farm").assertDoesNotExist()
        rule.onNodeWithTag("expense-farm-context").performScrollTo().assertTextContains("Cargando la finca…")
    }

    /** Owner #480: from a work the expense keeps that work, offers only its Parcels and asks the category. */
    @Test fun aRelatedExpenseKeepsItsWorkAndAsksTheCategory() {
        val work = com.isivoltpro.maginaolivo.domain.activity.Activity(
            id = UUID.randomUUID(), workspaceId = workspace, farmId = farm.id, campaignId = null,
            type = com.isivoltpro.maginaolivo.domain.activity.ActivityType.PRUNING,
            status = com.isivoltpro.maginaolivo.data.local.model.ActivityStatus.COMPLETED,
            activityDate = date, description = "Poda de olivar", notes = null,
            targets = listOf(
                com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget(UUID.randomUUID(), "Norte"),
                com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget(UUID.randomUUID(), "Sur"),
            ),
            version = 1,
        )
        var saved: ExpenseForm? = null
        rule.setContent {
            MaginaOlivoTheme {
                ExpenseEditor(
                    title = "Nuevo gasto", initial = ExpenseForm(date.toString(), "65", "Afilado", farmId = farm.id, activityId = work.id),
                    options = options.copy(activities = listOf(work)), errors = ExpenseFormErrors(),
                    isSaving = false, saveText = "Guardar gasto", onFarmSelected = {}, onSave = { saved = it }, onCancel = {},
                    farmLocked = true, activityLocked = true,
                )
            }
        }
        rule.onNodeWithTag("expense-activity-context").performScrollTo().assertTextContains("Poda de olivar", substring = true)
        // Codex #480: general work on a Farm with a running campaign — no Recogida choice, said as context.
        assertEquals(0, rule.onAllNodesWithTag("expense-kind").fetchSemanticsNodes().size)
        rule.onNodeWithTag("expense-work-campaign").assertTextContains("Fuera de campaña", substring = true)
        // Category on purpose: no save until chosen.
        rule.onNodeWithTag("expense-category-required").assertExists()
        rule.onNodeWithTag("save-expense").performScrollTo().assertIsNotEnabled()
        // Only the work's two Parcels (and the whole work) are offered.
        rule.onNodeWithTag("expense-parcel").performScrollTo().performClick()
        rule.onNodeWithText("Norte").assertExists()
        rule.onNodeWithText("Sur").assertExists()
        rule.onNodeWithTag("choice-none").performClick() // «Todo el trabajo» in the sheet; the field says it too
        rule.onNodeWithTag("expense-category").performScrollTo().performClick()
        rule.onNodeWithText("Reparaciones").performClick()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(work.id, saved?.activityId); assertNull(saved?.parcelId); assertNull(saved?.campaignId) }
    }

    /** Codex #480: work of the running campaign shows that campaign as context, never a choice. */
    @Test fun aRelatedExpenseOfCampaignWorkShowsItsCampaign() {
        val work = com.isivoltpro.maginaolivo.domain.activity.Activity(
            id = UUID.randomUUID(), workspaceId = workspace, farmId = farm.id, campaignId = running.id,
            type = com.isivoltpro.maginaolivo.domain.activity.ActivityType.OTHER,
            status = com.isivoltpro.maginaolivo.data.local.model.ActivityStatus.COMPLETED,
            activityDate = date, description = "Limpieza de fardos", notes = null,
            targets = listOf(com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget(UUID.randomUUID(), "Norte")),
            version = 1,
        )
        rule.setContent {
            MaginaOlivoTheme {
                ExpenseEditor(
                    title = "Nuevo gasto", initial = ExpenseForm(date.toString(), "20", "Sacos", farmId = farm.id, activityId = work.id),
                    options = options.copy(activities = listOf(work)), errors = ExpenseFormErrors(),
                    isSaving = false, saveText = "Guardar gasto", onFarmSelected = {}, onSave = {}, onCancel = {},
                    preselectRecollection = true, farmLocked = true, activityLocked = true,
                )
            }
        }
        assertEquals(0, rule.onAllNodesWithTag("expense-kind").fetchSemanticsNodes().size)
        rule.onNodeWithTag("expense-work-campaign").performScrollTo().assertTextContains("2026/27", substring = true)
    }

    /** #433: choosing a general work drops the Recogida choice and offers only the work's Parcels. */
    @Test fun choosingAWorkMakesItsRelationsContext() {
        val norte = UUID.randomUUID()
        val work = com.isivoltpro.maginaolivo.domain.activity.Activity(
            id = UUID.randomUUID(), workspaceId = workspace, farmId = farm.id, campaignId = null,
            type = com.isivoltpro.maginaolivo.domain.activity.ActivityType.PRUNING,
            status = com.isivoltpro.maginaolivo.data.local.model.ActivityStatus.COMPLETED,
            activityDate = date, description = "Poda general", notes = null,
            targets = listOf(com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget(norte, "Norte")),
            version = 1,
        )
        var saved: ExpenseForm? = null
        rule.setContent {
            MaginaOlivoTheme {
                ExpenseEditor(
                    title = "Nuevo gasto", initial = ExpenseForm(date.toString(), "20", "Gasoil", farmId = farm.id),
                    options = options.copy(activities = listOf(work)), errors = ExpenseFormErrors(),
                    isSaving = false, saveText = "Guardar gasto", onFarmSelected = {}, onSave = { saved = it }, onCancel = {},
                )
            }
        }
        rule.onNodeWithTag("expense-kind").assertExists()
        // #415: the work waits under «Relacionar y más detalles».
        rule.onNodeWithTag("expense-more-details").performScrollTo().performClick()
        rule.onNodeWithTag("expense-activity").performScrollTo().performClick()
        rule.onNodeWithTag("choice-${work.id}").performClick()
        assertEquals(0, rule.onAllNodesWithTag("expense-kind").fetchSemanticsNodes().size)
        rule.onNodeWithTag("expense-work-campaign").assertTextContains("Fuera de campaña", substring = true)
        rule.onNodeWithTag("expense-parcel").performScrollTo().performClick()
        rule.onNodeWithTag("choice-$norte").assertExists()
        rule.onNodeWithTag("choice-$norte").performClick()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle {
            assertEquals(work.id, saved?.activityId)
            assertEquals(norte, saved?.parcelId)
            assertNull(saved?.campaignId)
        }
    }

    /** Codex #522: dropping the work of an expense in a campaign asks the choice again before saving. */
    @Test fun droppingTheWorkAsksTheCampaignAgain() {
        val work = com.isivoltpro.maginaolivo.domain.activity.Activity(
            id = UUID.randomUUID(), workspaceId = workspace, farmId = farm.id, campaignId = running.id,
            type = com.isivoltpro.maginaolivo.domain.activity.ActivityType.OTHER,
            status = com.isivoltpro.maginaolivo.data.local.model.ActivityStatus.COMPLETED,
            activityDate = date, description = "Limpieza", notes = null,
            targets = listOf(com.isivoltpro.maginaolivo.domain.activity.ActivityParcelTarget(UUID.randomUUID(), "Norte")),
            version = 1,
        )
        rule.setContent {
            MaginaOlivoTheme {
                ExpenseEditor(
                    title = "Editar gasto",
                    initial = ExpenseForm(date.toString(), "20", "Sacos", farmId = farm.id, activityId = work.id, campaignId = running.id),
                    options = options.copy(activities = listOf(work)), errors = ExpenseFormErrors(),
                    isSaving = false, saveText = "Guardar cambios", onFarmSelected = {}, onSave = {}, onCancel = {},
                )
            }
        }
        assertEquals(0, rule.onAllNodesWithTag("expense-kind").fetchSemanticsNodes().size)
        rule.onNodeWithTag("expense-activity").performScrollTo().performClick()
        rule.onNodeWithTag("choice-none").performClick()
        rule.onNodeWithTag("expense-kind").performScrollTo().assertExists()
        rule.onNodeWithTag("expense-kind-required").assertExists()
        rule.onNodeWithTag("save-expense").performScrollTo().assertIsNotEnabled()
    }

    private fun editor(initial: ExpenseForm, preselect: Boolean = false, farmLocked: Boolean = false, loaded: RelationOptions = options, onSave: (ExpenseForm) -> Unit) {
        rule.setContent {
            MaginaOlivoTheme {
                ExpenseEditor(
                    title = "Editar gasto", initial = initial, options = loaded, errors = ExpenseFormErrors(),
                    isSaving = false, saveText = "Guardar cambios", onFarmSelected = {}, onSave = onSave, onCancel = {},
                    preselectRecollection = preselect, farmLocked = farmLocked,
                )
            }
        }
    }

    private fun review(contextCampaignId: UUID?, preselect: Boolean): () -> ExpenseForm? {
        var reviewed: ExpenseForm? = null
        val extraction = DocumentExtraction(
            id = UUID.randomUUID(), attachmentId = UUID.randomUUID(), documentType = DocumentType.PURCHASE_INVOICE,
            status = OcrStatus.EXTRACTED, engine = "test", rawText = "…",
            proposal = PurchaseProposal(supplierName = "Gasóleos Mágina", invoiceDate = date, totalMinor = 7_260),
            expenseId = null, createdAt = Instant.EPOCH, reviewedAt = null,
        )
        rule.setContent {
            MaginaOlivoTheme {
                DocumentReviewScreen(
                    state = DocumentReviewUiState(isLoading = false, extraction = extraction, options = options),
                    onRead = {}, onCreateDraft = { reviewed = it }, onKeep = {}, onDiscard = {}, onFarmSelected = {},
                    contextFarmId = farm.id, contextCampaignId = contextCampaignId, requireCampaignChoice = preselect,
                )
            }
        }
        if (contextCampaignId == null && preselect) {
            rule.onNodeWithTag("expense-kind-recollection").performScrollTo().assertIsNotSelected().performClick().assertIsSelected()
        } else {
            rule.onNodeWithTag("expense-kind-recollection").performScrollTo().assertIsSelected()
        }
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        return { reviewed }
    }

    private fun farm(name: String) = Farm(
        UUID.randomUUID(), workspace, name, null, null, null, null, null, 0L, null, null, null, 1L,
    )

    private fun campaign(farmId: UUID, name: String, status: CampaignStatus) =
        Campaign(UUID.randomUUID(), workspace, farmId, name, date.minusMonths(1), null, status, null, emptyList(), 1)
}
