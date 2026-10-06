package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Xml
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.*
import com.isivoltpro.maginaolivo.domain.equipment.*
import com.isivoltpro.maginaolivo.domain.expense.*
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.labour.*
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.harvests.*
import com.isivoltpro.maginaolivo.feature.notebook.*
import com.isivoltpro.maginaolivo.feature.expenses.*
import com.isivoltpro.maginaolivo.ui.theme.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual production Compose screens, synthetic canonical records; no reference screenshots. */
class Cr012SurfacesTest {
    @get:Rule val rule = createComposeRule()
    private val date = LocalDate.of(2026, 10, 2)
    private val campaign = Campaign(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Recogida 2026/27", date, null, CampaignStatus.ACTIVE, null, emptyList(), 1)
    private val day = Harvest(UUID.randomUUID(), campaign.workspaceId, campaign.farmId, campaign.id, date, 3200000, emptyList(), null, null, null, null, 1)
    private val workers = listOf("Juan García López", "Ana López", "María Pérez").map { Worker(UUID.randomUUID(), it) }
    private val entries = workers.map { LabourEntry(UUID.randomUUID(), day.id, it.id, it.name, 1, LabourUnit.FULL_DAY, null, 1, LabourRateSnapshot(10000, "EUR", date, LabourRateBasis.DAY)) }
    private val equipment = listOf(EquipmentType.SHAKER to 7000L, EquipmentType.COMB to 2000L, EquipmentType.TRAILER to 9000L).map { (type, amount) -> EquipmentLine(UUID.randomUUID(), day.id, type, null, 1, null, 1, EquipmentPriceSnapshot(amount, "EUR", date)) }
    private val delivery = Delivery(UUID.randomUUID(), campaign.workspaceId, campaign.farmId, campaign.id, date, null, "Cooperativa de prueba", 3200000, null, null, null, "CR012-3200", DeliverySource.MANUAL, emptyList(), null, 1, harvestId = day.id)
    private fun expense(category: ExpenseCategory, amount: Long, origin: ExpenseOrigin = ExpenseOrigin.MANUAL) = Expense(UUID.randomUUID(), campaign.workspaceId, date, category.name, category, amount, "EUR", ExpenseStatus.POSTED, origin, farmId = campaign.farmId, campaignId = campaign.id, harvestId = day.id)
    private val costs = listOf(expense(ExpenseCategory.LABOR, 30000, ExpenseOrigin.DAY_LABOUR), expense(ExpenseCategory.MACHINERY, 18000, ExpenseOrigin.DAY_EQUIPMENT), expense(ExpenseCategory.FUEL, 5000))
    private val payments = listOf(LabourPayment(UUID.randomUUID(), workers[0].id, campaign.id, date, 6000, "EUR"))
    private val notebook get() = CampaignNotebook.project(campaign, emptyList(), listOf(day), listOf(delivery), costs, entries, equipment)

    @Test fun dayOtherDetailKeepsJpyOnlyPostedTotal() {
        showDayCosts(listOf(costs[2].copy(currency = "JPY", amountMinor = 1000)))
        rule.onNodeWithTag("jornada-cost-total").assertTextContains("1.000", substring = true).assertTextContains("JPY", substring = true)
    }

    @Test fun dayOtherDetailKeepsMixedCurrenciesAndIgnoresDraftMoney() {
        showDayCosts(listOf(costs[2], costs[2].copy(id = UUID.randomUUID(), currency = "JPY", amountMinor = 1000),
            costs[2].copy(id = UUID.randomUUID(), status = ExpenseStatus.DRAFT, amountMinor = 99999)))
        rule.onNodeWithTag("jornada-cost-total").assertTextContains("50,00", substring = true)
            .assertTextContains("1.000", substring = true).assertTextContains("JPY", substring = true)
            .assertTextContains("1 borrador sin contar", substring = true)
    }

    private fun showDayCosts(rows: List<Expense>) {
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day, costs = rows), {}, {}) } }
        rule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
    }

    @Test fun dayUnpricedResourcesAndDraftCostsStayUnknownBesidePostedFuel() {
        val rows = listOf(costs[2], costs[0].copy(status = ExpenseStatus.DRAFT), costs[1].copy(status = ExpenseStatus.DRAFT))
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day,
            labour = entries.map { it.copy(appliedRate = null) }, equipment = equipment.map { it.copy(appliedPrice = null) }, costs = rows), {}, {}) } }
        rule.onNodeWithTag("day-resource-labour").performScrollTo().assertTextContains("—").assertTextContains("sin confirmar", substring = true)
        rule.onNodeWithTag("day-resource-equipment").performScrollTo().assertTextContains("—").assertTextContains("sin confirmar", substring = true)
        rule.onNodeWithTag("day-cost").performScrollTo().assertTextContains("50,00", substring = true)
    }

    @Test fun campaignConfirmedPortionDisclosesUnpricedResources() {
        val partial = notebook.copy(labour = entries + entries[0].copy(id = UUID.randomUUID(), appliedRate = null),
            equipment = equipment + equipment[0].copy(id = UUID.randomUUID(), appliedPrice = null))
        rule.setContent { MaginaOlivoTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CampaignView(partial, NotebookUiState(isLoading = false, notebook = partial), NotebookActions())
        } } }
        rule.onNodeWithTag("notebook-summary-labour").performScrollTo().assertTextContains("300,00", substring = true).assertTextContains("sin confirmar", substring = true)
        rule.onNodeWithTag("notebook-summary-equipment").performScrollTo().assertTextContains("180,00", substring = true).assertTextContains("sin confirmar", substring = true)
    }

    @Test fun dayPostedZeroIsShownWithoutFabricatedJpyResourceZero() {
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day,
            costs = listOf(costs[0].copy(amountMinor = 0), costs[1].copy(amountMinor = 0), costs[2].copy(currency = "JPY", amountMinor = 1000))), {}, {}) } }
        rule.onNodeWithTag("day-resource-labour").performScrollTo().assertTextContains("0,00", substring = true).assert(!hasText("JPY", substring = true))
        rule.onNodeWithTag("day-resource-equipment").performScrollTo().assertTextContains("0,00", substring = true).assert(!hasText("JPY", substring = true))
    }

    @Test fun pendingResourceDetailsNeverClaimEmptyOrAllowEdits() = unavailableResourceDetails(false)
    @Test fun failedResourceDetailsNeverClaimEmptyOrAllowEdits() = unavailableResourceDetails(true)

    private fun unavailableResourceDetails(failed: Boolean) {
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day,
            equipmentLoaded = failed, equipmentReadFailed = failed, costsLoaded = failed, costsReadFailed = failed), {}, {}) } }
        rule.onNodeWithTag("day-resource-equipment").performScrollTo().performClick()
        rule.onNodeWithTag(if (failed) "jornada-equipment-read-error" else "jornada-equipment-loading").assertExists()
        rule.onNodeWithText("Sin maquinaria anotada.").assertDoesNotExist()
        rule.onNodeWithTag("jornada-edit-equipment").assertDoesNotExist()
        rule.onNodeWithTag("resource-detail-close").performScrollTo().performClick()
        rule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        rule.onNodeWithTag(if (failed) "jornada-costs-read-error" else "jornada-costs-loading").assertExists()
        rule.onNodeWithTag("jornada-no-costs").assertDoesNotExist()
        rule.onNodeWithTag("jornada-add-cost").assertDoesNotExist()
        rule.onNodeWithTag("jornada-edit-rates").assertDoesNotExist()
    }

    @Test fun dayExpenseUsesHistoricalJpyBeforeTodaysEurAndEmitsJpyMinorUnits() {
        var saved: Long? = null
        val jpy = costs[2].copy(currency = "JPY", amountMinor = 1000)
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day, costs = listOf(jpy), rates = RecollectionRates(currency = "EUR")), {}, {}, onAddCost = { _, amount, _, _, _ -> saved = amount }) } }
        rule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        rule.onNodeWithTag("jornada-add-cost").performScrollTo().performClick()
        rule.onNodeWithText("Importe (JPY)").assertExists()
        rule.onNodeWithTag("cost-amount").performTextInput("1000")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("cost-save").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1000L, saved) }
    }

    @Test fun resourceReadErrorsAreVisibleWithoutOpeningDetailsAndNoZeroIsInvented() {
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day,
            labourLoaded = false, costError = "No pudimos leer los gastos", equipmentError = "No pudimos leer la maquinaria"), {}, {}) } }
        rule.onNodeWithTag("day-resource-labour").performScrollTo().assertTextContains("Cargando jornales", substring = true)
        rule.onNodeWithText("No pudimos leer los gastos").performScrollTo().assertExists()
        rule.onNodeWithText("No pudimos leer la maquinaria").assertExists()
    }

    @Test fun mixedDayCurrencyBlocksContextualCostWithoutInventingCurrency() {
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day,
            costs = listOf(costs[0], costs[2].copy(currency = "JPY"))), {}, {}) } }
        rule.onNodeWithTag("day-resource-other").performScrollTo().performClick()
        rule.onNodeWithTag("jornada-add-cost").performScrollTo().performClick()
        rule.onNodeWithTag("day-expense-currency-error").assertTextContains("EUR", substring = true).assertTextContains("JPY", substring = true)
        rule.onNodeWithTag("cost-save").performScrollTo().assertIsNotEnabled()
    }

    @Test fun loadingFinancialContextCannotSaveAGuessedExpenseCurrency() {
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day,
            labourLoaded = false, equipmentLoaded = false, costsLoaded = false), {}, {}) } }
        rule.onNodeWithTag("day-resource-equipment").performScrollTo().assertTextContains("Cargando maquinaria", substring = true)
        rule.onNodeWithTag("day-resource-other").performScrollTo().assertTextContains("Cargando gastos", substring = true).performClick()
        rule.onNodeWithTag("jornada-costs-loading").assertExists()
        rule.onNodeWithTag("jornada-add-cost").assertDoesNotExist()
        rule.onNodeWithTag("cost-save").assertDoesNotExist()
    }

    @Test fun comparisonKeepsMixedCurrenciesVisibleWithoutConversion() {
        val mixed = notebook.copy(expenses = listOf(costs[0], costs[2].copy(currency = "KWD", amountMinor = 1000)))
        val older = mixed.copy(campaign = campaign.copy(id = UUID.randomUUID(), startDate = date.minusYears(1)), expenses = emptyList(), deliveries = emptyList())
        val rows = com.isivoltpro.maginaolivo.domain.analytics.CampaignComparison.of(listOf(older, mixed))
        rule.setContent { MaginaOlivoTheme { Column { CampaignComparisonList(rows) } } }
        rule.onAllNodesWithTag("comparison-line", useUnmergedTree = true)[0].assertTextContains("KWD", substring = true).assertTextContains("€", substring = true)
    }

    @Test fun actualDayCardsShowCanonical530Over3200AndOpenEachResource() {
        var person: UUID? = null
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day, pesadas = listOf(delivery), labour = entries, equipment = equipment, costs = costs), {}, {}, labourActions = LabourActions(onPerson = { person = it })) } }
        rule.onNodeWithTag("day-resource-labour").performScrollTo().assertTextContains("300,00", substring = true)
        rule.onNodeWithTag("day-resource-equipment").assertTextContains("180,00", substring = true)
        rule.onNodeWithTag("day-resource-other").performScrollTo().assertTextContains("50,00", substring = true)
        capture("day-resources")
        rule.onNodeWithTag("day-cost-per-kg").performScrollTo().assertTextContains("0,166", substring = true) // #486: 530 € / 3.200 kg = 0,1656… → 0,166
        capture("day-cost-kg")
        rule.onNodeWithTag("day-resource-labour").performScrollTo().performClick()
        rule.onAllNodesWithTag("jornada-labour")[0].performClick()
        rule.runOnIdle { assertEquals(workers[0].id, person) }
        capture("day-labour-detail")
        rule.onNodeWithTag("resource-detail-close").performScrollTo().performClick()
        rule.onNodeWithTag("day-resource-equipment").performScrollTo().performClick()
        rule.onAllNodesWithTag("jornada-equipment-line").assertCountEquals(3)
        capture("day-machinery-detail")
    }

    @Test fun actualCampaignHasSeparateProductionAndCompactCostsWithoutWorkerLists() {
        var opened: UUID? = null
        rule.setContent { MaginaOlivoTheme { Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(MoSpacing.screen)) { CampaignView(notebook, NotebookUiState(isLoading = false, notebook = notebook, labourPayments = payments), NotebookActions(onLabour = { opened = it })) } } }
        rule.onNodeWithTag("notebook-summary-labour").performScrollTo().assertTextContains("60,00", substring = true).assertTextContains("240,00", substring = true)
        rule.onNodeWithTag("notebook-summary-other").performScrollTo()
        capture("campaign-costs")
        rule.onNodeWithTag("dashboard-cost-per-kg").performScrollTo().assertTextContains("0,166", substring = true) // #486: thousandths, never 0,17
        capture("campaign-total-cost-kg")
        rule.onNodeWithText(workers[0].name).assertDoesNotExist()
        rule.onNodeWithText("Trabajos").assertDoesNotExist()
        rule.onNodeWithTag("notebook-summary-labour").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(campaign.id, opened) }
    }

    @Test fun actualPersonAndPaymentShowIndependentBalancesAfterClose() {
        val state = LabourPaymentsUiState(isLoading = false, campaign = campaign.copy(status = CampaignStatus.CLOSED), days = listOf(day), entries = entries, payments = payments,
            accounts = labourAccounts(campaign.id, entries, costs, payments))
        var saved: LabourPayment? = null
        rule.setContent { MaginaOlivoTheme { Surface(Modifier.fillMaxSize().statusBarsPadding(), color = MoCream) { LabourPaymentsScreen(state, workers[0].id, date, onPay = { saved = it }) } } }
        rule.onAllNodesWithText(workers[0].name)[0].assertIsDisplayed()
        rule.onNodeWithTag("person-generated").assertIsDisplayed().assertTextContains("100,00", substring = true)
        rule.onNodeWithTag("person-paid-pending").assertTextContains("60,00", substring = true).assertTextContains("40,00", substring = true)
        rule.onNodeWithTag("payment-state-PARTIAL").assertExists()
        capture("person-partial-closed")
        rule.onNodeWithTag("person-register-payment").performScrollTo().performClick()
        rule.onNodeWithTag("payment-all").performClick()
        rule.onNodeWithTag("payment-save").performScrollTo().assertIsEnabled()
        capture("payment-after-close")
        nativeClick("Guardar pago")
        rule.runOnIdle { assertEquals(4000L, saved?.amountMinor); assertEquals(campaign.id, saved?.campaignId) }
    }

    @Test fun equipmentSaveIsPhysicallyReachableAfterPriceEditAndImeDismissal() {
        var saved: List<EquipmentDraftLine>? = null
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day, equipment = equipment, costs = costs), {}, {}, onSaveEquipment = { saved = it }) } }
        rule.onNodeWithTag("day-resource-equipment").performScrollTo().performClick()
        rule.onNodeWithTag("jornada-edit-equipment").performScrollTo().performClick()
        rule.onNodeWithTag("equipment-SHAKER-price").performScrollTo().performTextReplacement("80")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("equipment-save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("equipment-post-edit-save")
        nativeClick("Guardar maquinaria")
        rule.runOnIdle { assertNotNull("Native pointer Save must emit draft", saved); assertEquals(8000L, saved!!.first { it.type == EquipmentType.SHAKER }.appliedPrice?.unitPriceMinor) }
    }

    @Test fun legacyJpyLabourSaveIsPhysicallyReachableAfterPriceEditAndImeDismissal() {
        var saved: LabourChange? = null
        val legacy = entries[0].copy(appliedRate = null)
        val jpy = costs[0].copy(currency = "JPY", amountMinor = 1000)
        rule.setContent { MaginaOlivoTheme { HarvestDetailScreen(HarvestDetailUiState(isLoading = false, harvest = day, labour = listOf(legacy), costs = listOf(jpy)), {}, {}, labourActions = LabourActions(onUpdate = { _, value -> saved = value })) } }
        rule.onNodeWithTag("day-resource-labour").performScrollTo().performClick()
        rule.onNodeWithTag("jornada-labour-edit").performScrollTo().performClick()
        rule.onNodeWithTag("labour-edit-rate").performTextInput("1000")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("labour-edit-save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("labour-jpy-post-edit-save")
        nativeClick("Guardar cambios")
        rule.runOnIdle { assertNotNull("Native pointer Save must emit JPY draft", saved); assertEquals("JPY", saved?.appliedRate?.currency); assertEquals(1000L, saved?.appliedRate?.unitPriceMinor) }
    }

    @Test fun contextualExpenseFormEmitsExplicitCampaignWhileFarmOnlyIsOutside() {
        var saved: ExpenseForm? = null
        rule.setContent { MaginaOlivoTheme { ExpensesScreen(ExpensesUiState(isLoading = false), date, { saved = it }, {}, { _, _ -> }, {}, {}, {}, {}, presetFarmId = campaign.farmId, presetCampaignId = campaign.id) } }
        rule.onNodeWithTag("add-expense").performClick()
        rule.onNodeWithTag("expense-concept").performTextInput("Transporte")
        rule.onNodeWithTag("expense-amount").performTextInput("50")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("save-expense").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(campaign.id, saved?.toDraft()?.first?.campaignId); assertEquals(campaign.farmId, saved?.farmId) }
        assertNull(ExpenseForm(date.toString(), "50", "Poda", farmId = campaign.farmId).toDraft().first!!.campaignId)
    }

    @Test fun campaignExpenseTotalsKeepOriginalCurrenciesSeparate() {
        val mixed = listOf(costs[0], costs[2].copy(currency = "KWD", amountMinor = 1000))
        rule.setContent { MaginaOlivoTheme { ExpensesScreen(ExpensesUiState(isLoading = false, expenses = mixed), date,
            {}, {}, { _, _ -> }, {}, {}, {}, {}, presetCampaignId = campaign.id) } }
        rule.onNodeWithTag("expenses-total").assertTextContains("KWD", substring = true).assertTextContains("300,00", substring = true)
    }

    @Test fun historicalJpyCostsTabDoesNotClaimNoPostedExpenses() {
        rule.setContent { MaginaOlivoTheme { Column { CostsView(notebook.copy(expenses = listOf(costs[2].copy(currency = "JPY", amountMinor = 1000))), NotebookActions()) } } }
        rule.onNodeWithTag("notebook-expenses-total").assertTextContains("JPY", substring = true)
    }

    private fun nativeClick(text: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = instrumentation.uiAutomation.rootInActiveWindow
        val node = find(root, text) ?: error("Native accessibility tree has no $text")
        val bounds = Rect().also(node::getBoundsInScreen)
        val metrics = instrumentation.targetContext.resources.displayMetrics
        assertTrue("Save must be visible inside the physical window: $bounds", node.isVisibleToUser && bounds.width() > 0 && bounds.height() > 0 && bounds.top >= 0 && bounds.bottom <= metrics.heightPixels)
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 60, MotionEvent.ACTION_UP, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0))
        rule.waitForIdle()
    }
    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (i in 0 until node.childCount) find(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "cr012-slice4").apply { mkdirs() }
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val scale = instrumentation.targetContext.resources.configuration.fontScale
        val suffix = "${(instrumentation.targetContext.resources.displayMetrics.widthPixels / density).toInt()}dp-font$scale"
        var drawn: Bitmap? = null
        // Await observable native compositor content, not a timed sleep or a retry click.
        rule.waitUntil(timeoutMillis = 5000) {
            val candidate = instrumentation.uiAutomation.takeScreenshot()
            val colors = (10..90).flatMap { y -> (1..48).map { x -> candidate.getPixel(candidate.width * x / 49, candidate.height * y / 100) } }.toSet()
            if (colors.size > 8) { drawn = candidate; true } else { candidate.recycle(); false }
        }
        val bitmap = checkNotNull(drawn)
        File(dir, "$name-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        File(dir, "$name-$suffix.xml").writer().use { writer ->
            val serializer = Xml.newSerializer().apply { setOutput(writer); startDocument("UTF-8", true); startTag(null, "hierarchy") }
            fun write(node: AccessibilityNodeInfo?) {
                if (node == null) return
                serializer.startTag(null, "node")
                serializer.attribute(null, "text", node.text?.toString().orEmpty())
                serializer.attribute(null, "description", node.contentDescription?.toString().orEmpty())
                serializer.attribute(null, "bounds", Rect().also(node::getBoundsInScreen).toString())
                serializer.attribute(null, "visible", node.isVisibleToUser.toString())
                for (i in 0 until node.childCount) write(node.getChild(i))
                serializer.endTag(null, "node")
            }
            write(instrumentation.uiAutomation.rootInActiveWindow)
            serializer.endTag(null, "hierarchy"); serializer.endDocument()
        }
    }
}
