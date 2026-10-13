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
import com.isivoltpro.maginaolivo.feature.expenses.*
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.navigation.*
import java.io.File
import com.isivoltpro.maginaolivo.app.AppearanceMode
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuickLabourFormTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
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
        compose.onNodeWithTag("quick-labour-campaign").performClick()
        compose.runOnIdle { currency.value = "JPY"; loaded.value = true }
        compose.onNodeWithTag("quick-labour-review-currency").assertDoesNotExist()
        compose.onNodeWithTag("labour-rate").performScrollTo().assertTextContains("JPY", substring = true)
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
        compose.onNodeWithTag("quick-labour-general").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("expense-concept").performScrollTo().performTextClearance()
        compose.onNodeWithTag("expense-concept").performTextInput("Reparación de riego")
        compose.onNodeWithTag("expense-amount").performScrollTo().performTextInput("65")
        compose.onNodeWithTag("quick-labour-campaign").performClick()
        compose.onNodeWithTag("labour-worker").performScrollTo().performClick()
        compose.onNodeWithTag("labour-unit-HOURS").performScrollTo().performClick()
        compose.onNodeWithTag("labour-hours").performScrollTo().performTextInput("3")
        capture(dark, "campaign-hours")
        compose.onNodeWithTag("quick-labour-general").assertIsDisplayed()
        compose.onNodeWithTag("quick-labour-general").performClick()
        compose.onNodeWithTag("expense-concept").performScrollTo().assertTextContains("Reparación de riego")
        compose.onNodeWithTag("expense-amount").assertTextContains("65")
        compose.onNodeWithText("Guardar jornal general").performScrollTo().performClick()
        compose.runOnIdle {
            assertNotNull(savedGeneral); assertNull(savedGeneral!!.campaignId); assertNull(savedGeneral!!.harvestId)
            assertEquals(ExpenseCategory.LABOR, savedGeneral!!.category); assertNull(savedCrew)
            assertEquals(parcel, savedGeneral!!.parcelId); assertEquals(farm, savedGeneral!!.farmId)
        }
        compose.onNodeWithTag("quick-labour-campaign").performClick()
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
        compose.onNodeWithTag("quick-labour-general").performClick()
        compose.onNodeWithTag("quick-labour-campaign").performClick()
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
