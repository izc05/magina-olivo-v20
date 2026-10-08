package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain =
        RuleChain
            .outerRule(ClearOnboardingStateRule())
            .around(composeRule)

    @Test
    fun completedOnboardingStaysCompletedAfterActivityRecreation() {
        enterMainShell()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("onboarding-root").assertDoesNotExist()
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
    }

    @Test
    fun allFrozenRootsAreReachableAndSelected() {
        enterMainShell()

        composeRule.onNodeWithTag("bottom-Mi Campo").performClick().assertIsSelected()
        composeRule.onNodeWithTag("farms-root").assertIsDisplayed()

        // UX-B (Issue #246): Cuaderno is the centre; Avisos holds the agenda.
        composeRule.onNodeWithTag("bottom-Cuaderno").performClick().assertIsSelected()
        composeRule.onNodeWithTag("notebook-root").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-Avisos").performClick().assertIsSelected()
        composeRule.onNodeWithTag("calendar-root").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-Perfil").performClick().assertIsSelected()
        composeRule.onNodeWithTag("profile-root").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-Inicio").performClick().assertIsSelected()
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
    }

    @Test
    fun backFromAnotherRootReturnsToHome() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Perfil").performClick()

        pressBack()

        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Inicio").assertIsSelected()
    }

    @Test
    fun activeRootSurvivesActivityRecreation() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Avisos").performClick()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("calendar-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Avisos").assertIsSelected()
    }

    @Test
    fun revisitingRootDoesNotAddDuplicateDestination() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        composeRule.onNodeWithTag("bottom-Inicio").performClick()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()

        pressBack()

        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Inicio").assertIsSelected()
    }

    @Test
    fun aTabAlwaysOpensItsOwnRootScreenFromWhereverTheFarmerIs() {
        enterMainShell()
        // Deep inside Inicio (weekly weather), then Perfil deep inside (Maquinaria)...
        composeRule.onNodeWithTag("home-weather-hero").performClick()
        waitForTag("weather-week-root")
        composeRule.onNodeWithTag("bottom-Perfil").performClick()
        clickByTag("profile-machinery")
        waitForTag("machinery-root")

        // ...Inicio is Inicio, not the Jornadas screen left open under it.
        composeRule.onNodeWithTag("bottom-Inicio").performClick()
        waitForTag("home-reference-root")
        composeRule.onNodeWithTag("weather-week-root").assertDoesNotExist()

        // Perfil is the profile, not the Maquinaria screen left open under it.
        composeRule.onNodeWithTag("bottom-Perfil").performClick()
        waitForTag("profile-root")
        composeRule.onNodeWithTag("machinery-root").assertDoesNotExist()

        // Tapping the tab already showing stays on its root; Back returns to Inicio.
        composeRule.onNodeWithTag("bottom-Perfil").performClick()
        waitForTag("profile-root")
        pressBack()
        waitForTag("home-reference-root")
        composeRule.onNodeWithTag("bottom-Inicio").assertIsSelected()
    }

    @Test
    fun backWalksTheScreensActuallyVisited() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Perfil").performClick()
        clickByTag("profile-machinery")
        waitForTag("machinery-root")

        pressBack()
        waitForTag("profile-root")
        composeRule.onNodeWithTag("bottom-Perfil").assertIsSelected()
        pressBack()
        waitForTag("home-reference-root")
    }

    /** CR-011 §4: Cuaderno → Trabajo opens the form in one tap; no second menu repeats the choice. */
    @Test
    fun cuadernoWorkActionOpensTheFlowDirectly() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Acciones E2E")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Acciones E2E")

        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        chooseNotebookFarm("Finca Acciones E2E")
        composeRule.onNodeWithTag("notebook-register-today").assertDoesNotExist()
        clickByTag("notebook-quick-work")

        waitForTag("register-activity-root")
        composeRule.onNodeWithTag("register-action-sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("register-activity-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Cuaderno").assertIsSelected()
        waitForText("Registrar trabajo")
    }

    /** CR-011 §4/§14: Cuaderno → Pesada opens the form at once; Cuaderno → Gasto opens Gastos. */
    @Test
    fun cuadernoPesadaAndGastoOpenTheirScreensOnThatFarm() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Pesada E2E")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Pesada E2E")

        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        chooseNotebookFarm("Finca Pesada E2E")
        clickByTag("notebook-quick-weighing")
        waitForTag("delivery-editor")
        composeRule.onNodeWithTag("bottom-Cuaderno").assertIsSelected()

        pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        chooseNotebookFarm("Finca Pesada E2E")
        clickByTag("notebook-quick-expense")
        waitForTag("expenses-root")
        composeRule.onNodeWithTag("bottom-Cuaderno").assertIsSelected()
    }

    /** #357/#358: tapping the tab already on its root keeps that screen; it is not recreated. */
    @Test
    fun reselectingTheActiveRootKeepsItsScreenAsItIs() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        listOf("Finca Reselección A", "Finca Reselección B").forEach { name ->
            waitForTag("add-farm")
            openSheet("add-farm", "farm-name")
            composeRule.onNodeWithTag("farm-name").performTextInput(name)
            saveEditor("save-farm", "farm-name")
            // The second card can sit below the visible part of the list; the closed editor is
            // what says it was saved. The Cuaderno below then lists both farms.
            composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodesWithTag("farm-name").fetchSemanticsNodes().isEmpty() }
        }

        // «Cambiar finca» opens the Farm choice; that open state lives only in the screen.
        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        clickByTag("notebook-change-farm")
        // Other tests share the database, so the choice can list more Farms than these two.
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            listOf("Finca Reselección A", "Finca Reselección B").all { name ->
                composeRule.onAllNodes(hasTestTag("notebook-farm-option") and hasText(name)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        val options = composeRule.onAllNodesWithTag("notebook-farm-option").fetchSemanticsNodes().size

        // Recreating the root would close it again.
        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        composeRule.waitForIdle()
        assertEquals(options, composeRule.onAllNodesWithTag("notebook-farm-option").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("bottom-Cuaderno").assertIsSelected()

        // The same for another root, and Back still returns to Inicio once.
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("farms-root")
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("farms-root").assertIsDisplayed()
        pressBack()
        waitForTag("home-reference-root")
    }

    /** CR-011 §3: a Farm's «Cuaderno» is the one Cuaderno on that Farm, and Back returns to the Farm. */
    @Test
    fun aFarmsCuadernoIsTheOneCuadernoOnThatFarm() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Única E2E")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Única E2E")
        clickByText("Finca Única E2E")
        waitForTag("farm-detail-root")

        clickByTag("farm-section-notebook")
        waitForTag("notebook-root")
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag("notebook-context") and hasText("Finca Única E2E", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("farm-section-root").assertDoesNotExist()
        composeRule.onNodeWithTag("bottom-Cuaderno").assertIsSelected()
        // #369: the Farm was chosen in Mi Campo; this Cuaderno keeps it.
        composeRule.onNodeWithTag("notebook-change-farm").assertDoesNotExist()

        pressBack()
        waitForTag("farm-detail-root")
    }

    /** #369: a Farm's Cuaderno has no «Cambiar finca»; the Cuaderno tab is the general hub again. */
    @Test
    fun theCuadernoTabChangesFarmWhileAFarmsCuadernoKeepsIt() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        listOf("Finca Contexto A", "Finca Contexto B").forEach { name ->
            waitForTag("add-farm")
            openSheet("add-farm", "farm-name")
            composeRule.onNodeWithTag("farm-name").performTextInput(name)
            saveEditor("save-farm", "farm-name")
            composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodesWithTag("farm-name").fetchSemanticsNodes().isEmpty() }
        }
        clickByText("Finca Contexto A")
        waitForTag("farm-detail-root")
        clickByTag("farm-section-notebook")
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag("notebook-context") and hasText("Finca Contexto A", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("notebook-change-farm").assertDoesNotExist()

        // The Cuaderno tab opens the general Cuaderno, where the Farm can be changed.
        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        waitForTag("notebook-change-farm")
        composeRule.onNodeWithTag("bottom-Cuaderno").assertIsSelected()
        pressBack()
        waitForTag("home-reference-root")
    }

    /** #369: a Parcel's Cuaderno shows that Parcel and its Farm; Back returns to the Parcel and the tab does not keep it. */
    @Test
    fun aParcelsCuadernoKeepsItsParcelOnlyThere() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Parcela Contexto")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Parcela Contexto")
        clickByText("Finca Parcela Contexto")
        openFarmSection("parcels")
        createParcel("Haza Contexto")
        clickByTag("parcel-row")
        waitForTag("parcel-detail-root")

        clickByTag("parcel-register")
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag("notebook-parcel-context") and hasText("Haza Contexto", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("notebook-change-farm").assertDoesNotExist()
        // «Toda la finca» drops the Parcel at once (Codex #381).
        clickByTag("notebook-parcel-clear")
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodesWithTag("notebook-parcel-context").fetchSemanticsNodes().isEmpty() }
        pressBack()
        waitForTag("parcel-detail-root")

        // The Cuaderno tab is the general Cuaderno: the Parcel stays with the Parcel.
        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        waitForTag("notebook-context")
        composeRule.onNodeWithTag("notebook-parcel-context").assertDoesNotExist()
    }

    @Test
    fun registerFromAFarmOpensCuadernoWithThatFarm() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("El Cerro")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "El Cerro")
        clickByText("El Cerro")
        waitForTag("farm-detail-root")

        // UX-F (Issue #246 §5), CR-011 §14: Mi Campo does not write; it opens Cuaderno with the Farm chosen.
        clickByTag("farm-register")
        waitForTag("notebook-root")
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag("notebook-context") and hasText("El Cerro", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("register-action-sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("notebook-quick-work").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Cuaderno").assertIsSelected()

        // #375: Riego from this Farm's Cuaderno keeps the Farm; it is not asked again.
        clickByTag("notebook-quick-irrigation")
        waitForTag("save-activity")
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodesWithText("El Cerro").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("change-activity-farm").assertDoesNotExist()
    }

    @Test
    fun nestedFarmRouteReturnsToOlivar() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("La Solana")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "La Solana")
        clickByText("La Solana")
        waitForTag("farm-detail-root")
        composeRule.onNodeWithTag("farm-detail-root").assertIsDisplayed()

        pressBack()
        composeRule.waitForIdle()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("farms-root").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("farms-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Mi Campo").assertIsSelected()
    }

    @Test
    fun parcelCanBeCreatedAndOpenedFromItsFarm() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Los Llanos")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Los Llanos")
        clickByText("Los Llanos")
        openFarmSection("parcels")

        openParcelManual()
        composeRule.onNodeWithTag("parcel-name").performTextInput("Parcela Alta")
        saveEditor("save-parcel", "parcel-name")
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("save-parcel").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText("Parcela Alta").fetchSemanticsNodes().size == 1
        }
        clickByTag("parcel-row")

        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("parcel-detail-root").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("parcel-detail-root").assertIsDisplayed()
        composeRule.onNodeWithText("Entrada manual").assertIsDisplayed()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            // No area, trees or variety were given: the tiles say "—", never a number.
            composeRule.onAllNodesWithText("—", useUnmergedTree = true)
                .fetchSemanticsNodes().size >= 3
        }
        composeRule.onNodeWithTag("bottom-Mi Campo").assertIsSelected()
    }

    @Test
    fun farmParcelCampaignLifecyclePersistsAcrossRecreation() {
        enterMainShell()

        // Mi Olivar
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")

        // Farm: open the editor, fill it, save, and wait for the persisted row.
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Campaña E2E")
        waitForTag("save-farm")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Campaña E2E")

        // Farm detail
        clickByText("Finca Campaña E2E")
        openFarmSection("parcels")
        waitForTag("add-parcel")

        // Parcel: open the editor, fill it, save, and wait for the persisted row.
        openParcelManual()
        composeRule.onNodeWithTag("parcel-name").performTextInput("Parcela Campaña E2E")
        waitForTag("save-parcel")
        saveEditor("save-parcel", "parcel-name")
        waitForSaved("parcel-name", "Parcela Campaña E2E")

        // Campaign: back to the Farm hub, open Campañas, fill the editor, select the Parcel, save.
        backToFarmHub()
        openFarmSection("campaigns")
        waitForTag("add-campaign")
        openSheet("add-campaign", "campaign-name")
        composeRule.onNodeWithTag("campaign-name").performTextInput("Campaña 2026/27 E2E")
        // Past start date: closing uses the device clock, so a future start would make
        // the legal close date depend on the day the suite runs.
        waitForTag("campaign-start-date")
        pickDate("campaign-start-date", "2026-01-01")
        waitForTag("campaign-parcel-option")
        clickInSheetByTag("campaign-parcel-option")
        waitForTag("save-campaign")
        clickInSheetByTag("save-campaign")
        waitForText("Campaña 2026/27 E2E")

        // Campaign detail
        waitForTag("campaign-row")
        clickByTag("campaign-row")
        waitForTag("campaign-detail-root")

        // PREPARATION -> ACTIVE
        clickByTag("activate-campaign")
        confirmCampaignAction()

        // CR-010: no «Iniciar recolección» step; Activa closes directly.
        assertEquals(0, composeRule.onAllNodesWithText("Iniciar recolección").fetchSemanticsNodes().size)

        // ACTIVE -> CLOSED
        clickByTag("close-campaign")
        confirmCampaignAction()
        waitForText("Histórico protegido")

        // Restart the process and prove the aggregate survived.
        pressBack()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        waitForText("Campaña 2026/27 E2E")
        clickByText("Campaña 2026/27 E2E")
        waitForTag("campaign-detail-root")
        waitForText("Parcela Campaña E2E")
        composeRule.onNodeWithText("Parcela Campaña E2E").assertIsDisplayed()
        composeRule.onNodeWithText("Finca Campaña E2E").assertIsDisplayed()
        // No weighing, yield analysis or expense has been recorded. Unknown
        // amounts stay unknown; the record counter is the honest value zero.
        listOf(
            "campaign-metric-weighed" to "Kg pesados",
            "campaign-metric-yield" to "Rendimiento graso",
            "campaign-metric-expenses" to "Gastos",
        ).forEach { (tag, label) ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            // Accessible KPI cards merge their title and value; the same unknown values remain.
            composeRule.onNode(hasTestTag(tag) and hasText(label) and hasText("—"))
                .assertExists()
        }
        composeRule.onNode(
            hasTestTag("campaign-metric-weighings") and
                hasText("Pesadas") and
                hasText("0"),
        ).assertExists()

        // A closed campaign stays protected after the restart, and reopening it is an
        // explicit, confirmed action that returns the aggregate to an editable state.
        waitForText("Histórico protegido")
        composeRule.onNodeWithText("Histórico protegido").performScrollTo().assertIsDisplayed()

        // CLOSED -> HARVEST
        clickByTag("reopen-campaign")
        confirmCampaignAction()

        // HARVEST -> CLOSED again
        clickByTag("close-campaign")
        confirmCampaignAction()
        waitForText("Histórico protegido")
        // #365: the detail is longer now (Jornales under Pesadas); bring the snapshot into view.
        composeRule.onNodeWithText("Parcela Campaña E2E").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun oneActivityTargetsTwoParcelsAsASingleCanonicalRecord() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")

        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Actuación E2E")
        waitForTag("save-farm")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Actuación E2E")
        clickByText("Finca Actuación E2E")
        openFarmSection("parcels")
        waitForTag("add-parcel")

        createParcel("Parcela Norte E2E")
        createParcel("Parcela Sur E2E")

        // One activity, two parcels selected.
        backToFarmHub()
        openFarmSection("activities")
        waitForTag("add-activity")
        openSheet("add-activity", "activity-description")
        composeRule.onNodeWithTag("activity-description").performTextInput("Poda multiparcela E2E")
        waitForTag("activity-date")
        pickDate("activity-date", "2026-01-15")
        waitForTag("activity-parcel-option")
        composeRule.onAllNodesWithTag("activity-parcel-option")[0].performScrollTo().performClick()
        composeRule.onAllNodesWithTag("activity-parcel-option")[1].performScrollTo().performClick()
        waitForTag("save-activity")
        clickInSheetByTag("save-activity")
        waitForSaved("activity-description", "Poda multiparcela E2E")
        // The typed description is on screen before the save lands; wait for the saved row.
        waitForTag("activity-row")

        // Exactly ONE canonical Activity row, not one per parcel.
        composeRule.onAllNodesWithTag("activity-row").assertCountEquals(1)
        assertTextVisible("2 parcelas")

        waitForTag("activity-row")
        clickByTag("activity-row")
        waitForTag("activity-detail-root")

        // The single Activity carries both Parcel targets. The screen's root exists while the
        // Activity is still loading, so wait for its targets before counting them.
        waitForTag("activity-target")
        composeRule.onAllNodesWithTag("activity-target").assertCountEquals(2)
        assertTextVisible("Parcela Norte E2E")
        assertTextVisible("Parcela Sur E2E")

        // PLANNED -> COMPLETED, then protected until an explicit reopen.
        clickByTag("complete-activity")
        confirmActivityAction()
        waitForText("Trabajo realizado")

        pressBack()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        waitForText("Poda multiparcela E2E")
        // The typed description is on screen before the save lands; wait for the saved row.
        waitForTag("activity-row")

        // Still one canonical Activity after the restart.
        composeRule.onAllNodesWithTag("activity-row").assertCountEquals(1)
        clickByText("Poda multiparcela E2E")
        waitForTag("activity-detail-root")
        // Same race as above: the root is on screen before the Activity's targets load.
        waitForTag("activity-target")
        composeRule.onAllNodesWithTag("activity-target").assertCountEquals(2)
        assertTextVisible("Trabajo realizado")
    }

    /**
     * The Registrar (+) entry point writes through the same aggregate as the Farm
     * detail: it is the real editor, not a reference screen, and a single Farm needs
     * no extra question.
     */
    @Test
    fun registrarPlusCreatesARealActivityOnTheSelectedFarm() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")

        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Registrar E2E")
        waitForTag("save-farm")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Registrar E2E")
        clickByText("Finca Registrar E2E")
        openFarmSection("parcels")
        waitForTag("add-parcel")
        createParcel("Parcela Registrar E2E")

        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        chooseNotebookFarm("Finca Registrar E2E")
        clickByTag("notebook-quick-work")
        waitForTag("register-activity-root")

        // The farm is known from Cuaderno, then choose the specific task before its form.
        // #410: Riego and Tratamiento have their own quick actions, so Trabajo starts with Poda.
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("register-activity-type-pruning").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("register-farm-option").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("register-activity-type-pruning").fetchSemanticsNodes().isEmpty()) {
            clickByText("Finca Registrar E2E")
        }
        composeRule.onNodeWithTag("register-activity-type-pruning").performClick()
        waitForTag("activity-description")
        composeRule.onNodeWithTag("activity-description").performTextClearance()
        composeRule.onNodeWithTag("activity-description").performTextInput("Poda desde Registrar")
        waitForTag("activity-date")
        pickDate("activity-date", "2026-02-02")
        waitForTag("activity-parcel-option")
        ensureFirstParcelSelected()
        clickByTag("save-activity")

        waitForText("Poda desde Registrar")
        // The typed description is on screen before the save lands; wait for the saved row.
        waitForTag("activity-row")
        composeRule.onAllNodesWithTag("activity-row").assertCountEquals(1)

        // The same Activity is the one the Farm detail shows: one record, one home.
        //
        // Mi Campo restores its own saved back stack, so returning to it lands back where
        // this test left it (the Parcelas screen of the Farm) rather than on the Farm list.
        // Accept any of the three, and walk to the Farm's Trabajos from there.
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            listOf("farm-section-root", "farm-detail-root", "add-farm").any { tag ->
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        }
        if (composeRule.onAllNodesWithTag("farm-section-root").fetchSemanticsNodes().isNotEmpty()) {
            backToFarmHub()
        }
        if (composeRule.onAllNodesWithTag("farm-detail-root").fetchSemanticsNodes().isEmpty()) {
            clickByText("Finca Registrar E2E")
        }
        waitForTag("farm-detail-root")
        openFarmSection("activities")
        waitForTag("add-activity")
        waitForText("Poda desde Registrar")
        // The typed description is on screen before the save lands; wait for the saved row.
        waitForTag("activity-row")
        composeRule.onAllNodesWithTag("activity-row").assertCountEquals(1)
    }

    /**
     * UX-G — Issue #246 success criterion: open the app, tap Cuaderno, Registrar hoy, write
     * one thing down and see it at once in Diario; after the app is recreated the same Farm
     * is still the active one and the record is still there (local Room, no network).
     */
    @Test
    fun registerTodayShowsInTheDiaryAndSurvivesARestart() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Diario E2E")
        waitForTag("save-farm")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Diario E2E")
        clickByText("Finca Diario E2E")
        openFarmSection("parcels")
        createParcel("Parcela Diario E2E")
        backToFarmHub()
        openFarmSection("campaigns")
        waitForTag("add-campaign")
        openSheet("add-campaign", "campaign-name")
        composeRule.onNodeWithTag("campaign-name").performTextInput("Campaña Diario E2E")
        waitForTag("campaign-start-date")
        pickDate("campaign-start-date", "2026-01-01")
        waitForTag("campaign-parcel-option")
        clickInSheetByTag("campaign-parcel-option")
        waitForTag("save-campaign")
        clickInSheetByTag("save-campaign")
        waitForText("Campaña Diario E2E")

        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        chooseNotebookFarm("Finca Diario E2E")
        composeRule.onNodeWithTag("notebook-context").assertTextContains("Finca Diario E2E", substring = true)
        clickByTag("notebook-quick-work")
        waitForTag("register-activity-root")
        // Choose the work type first; the following page contains only pruning fields.
        waitForTag("register-activity-type-pruning")
        composeRule.onNodeWithTag("register-activity-type-pruning").performClick()
        waitForTag("activity-description")
        composeRule.onNodeWithTag("activity-description").performTextClearance()
        composeRule.onNodeWithTag("activity-description").performTextInput("Poda anotada hoy E2E")
        composeRule.onNodeWithTag("activity-type-chooser").assertDoesNotExist()
        composeRule.onNodeWithTag("activity-type-option").assertDoesNotExist()
        // #414: a Cuaderno quick entry opens its typed fields at once, without another tap.
        composeRule.onNodeWithText("Tipo de poda").performScrollTo().assertIsDisplayed()
        waitForTag("activity-date")
        pickDate("activity-date", "2026-02-02")
        waitForTag("activity-parcel-option")
        ensureFirstParcelSelected()
        clickByTag("save-activity")
        waitForTag("activity-row")
        assertTextVisible("Completada")

        // Back in Mi Cuaderno, the Diario already shows it: saved once, read everywhere.
        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        waitForTag("notebook-diary")
        waitForText("Poda anotada hoy E2E")

        // Cold reopen: the active Farm is remembered on the phone and the record is in Room.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithTag("notebook-root").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        }
        waitForTag("notebook-context")
        composeRule.onNodeWithTag("notebook-context").assertTextContains("Finca Diario E2E", substring = true)
        waitForTag("notebook-diary")
        waitForText("Poda anotada hoy E2E")
    }

    /**
     * Phase 10: the editor shows the typed block of the chosen type and only that one.
     *
     * This is the no-giant-form guarantee expressed as behaviour: choosing a type swaps
     * the block, the fields of the previous type are gone, and what the farmer typed in
     * the block that was showing is what the saved Activity carries.
     */
    @Test
    fun theActivityEditorShowsOnlyTheTypedBlockOfTheChosenType() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")

        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Tipada E2E")
        waitForTag("save-farm")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Tipada E2E")
        clickByText("Finca Tipada E2E")
        openFarmSection("parcels")
        waitForTag("add-parcel")
        createParcel("Parcela Tipada E2E")

        backToFarmHub()
        openFarmSection("activities")
        waitForTag("add-activity")
        openSheet("add-activity", "activity-description")
        composeRule.onNodeWithTag("activity-description").performTextInput("Trabajo tipado E2E")
        waitForTag("activity-date")
        pickDate("activity-date", "2026-04-08")

        // Observación is the default type and has no structured fields at all.
        composeRule.onAllNodesWithTag("activity-detail-block").assertCountEquals(0)

        // Poda shows pruning fields, and only those.
        clickInSheetByText("Poda")
        clickInSheetByTag("activity-detail-more")
        waitForTag("detail-pruningType")
        // #414: a new Poda no longer asks for people and hours (Jornal holds them).
        composeRule.onAllNodesWithTag("detail-workerCount").assertCountEquals(0)

        // Switching to Labores de suelo swaps the whole block: no pruning field is left behind.
        // (#410: Riego and Tratamiento have their own Cuaderno actions, not a Trabajo chip.)
        clickInSheetByText("Labores de suelo")
        waitForTag("detail-workType")
        composeRule.onAllNodesWithTag("detail-pruningType").assertCountEquals(0)
        composeRule.onNodeWithTag("detail-workType").performScrollTo().performTextInput("Grada")
        composeRule.onNodeWithTag("detail-method").performScrollTo().performTextInput("Tractor")

        waitForTag("activity-parcel-option")
        ensureFirstParcelSelected()
        clickInSheetByTag("save-activity")
        waitForSaved("activity-description", "Trabajo tipado E2E")
        waitForTag("activity-row")

        // The saved Activity carries the soil-work block it was given, and one record.
        composeRule.onAllNodesWithTag("activity-row").assertCountEquals(1)
        clickByTag("activity-row")
        waitForTag("activity-detail-root")
        waitForTag("activity-detail-summary")
        assertTextVisible("Tipo de labor: Grada")
        assertTextVisible("Método: Tractor")

        // It survives a restart as part of the same aggregate, not as a second record.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        waitForTag("activity-detail-summary")
        assertTextVisible("Tipo de labor: Grada")
    }

    /**
     * CR-011 flows 7–9 and 13: Cuaderno → Jornal opens today's día de recolección by itself,
     * the same day again after a cold restart, and never a second one.
     */
    @Test
    fun cuadernoJornalOpensTodaysDayOnceAndItSurvivesARestart() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Jornal E2E")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Jornal E2E")

        clickByText("Finca Jornal E2E")
        openFarmSection("parcels")
        waitForTag("add-parcel")
        openParcelManual()
        composeRule.onNodeWithTag("parcel-name").performTextInput("Parcela Jornal E2E")
        saveEditor("save-parcel", "parcel-name")
        waitForSaved("parcel-name", "Parcela Jornal E2E")

        backToFarmHub()
        openFarmSection("campaigns")
        waitForTag("add-campaign")
        openSheet("add-campaign", "campaign-name")
        composeRule.onNodeWithTag("campaign-name").performTextInput("Campaña Jornal E2E")
        waitForTag("campaign-start-date")
        pickDate("campaign-start-date", "2026-01-01")
        waitForTag("campaign-parcel-option")
        clickInSheetByTag("campaign-parcel-option")
        clickInSheetByTag("save-campaign")
        waitForTag("campaign-row")
        clickByTag("campaign-row")
        waitForTag("campaign-detail-root")
        clickByTag("activate-campaign")
        confirmCampaignAction()
        // Only an active campaign offers «Cerrar»: activation has been saved before leaving.
        waitForTag("close-campaign")

        // Cuaderno → Jornal: today's day opens by itself; nobody opens a «jornada».
        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        chooseNotebookFarm("Finca Jornal E2E")
        // The Cuaderno has loaded that campaign (already active) before Jornal is pressed.
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Campaña Jornal E2E", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        clickByTag("notebook-quick-labour")
        // Build 683: the container alone renders while still loading; wait for the day itself.
        waitForTagOrDumpScreen("day-resource-labour")
        // #365: Jornal lands on the day's Jornales, with «Registrar jornales» at hand.
        closeJornalFocus()

        // Back and Jornal again, before any restart: the same day opens again.
        pressBack()
        waitForTag("notebook-root")
        clickByTag("notebook-quick-labour")
        waitForTagOrDumpScreen("day-resource-labour")
        closeJornalFocus()

        // Cold restart: the same day is still there.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        waitForTagOrDumpScreen("day-resource-labour")

        // Jornal again the same day reuses it.
        pressBack()
        waitForTag("notebook-root")
        clickByTag("notebook-quick-labour")
        waitForTagOrDumpScreen("day-resource-labour")
        closeJornalFocus()

        // The campaign holds exactly one día de recolección.
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("farms-root")
        clickByText("Finca Jornal E2E")
        openFarmSection("campaigns")
        waitForTag("campaign-row")
        clickByTag("campaign-row")
        waitForTag("campaign-detail-root")
        waitForText("1 día")

        // #365 follow-up: Campaña → Jornales adds a jornal right there, on that same day of today.
        composeRule.onNodeWithTag("campaign-open-labour").performScrollTo().performClick()
        clickInSheetByTag("labour-add")
        waitForTagOrDumpScreen("day-resource-labour")
        closeJornalFocus()
        pressBack()
        waitForTag("campaign-detail-root")
        waitForText("1 día")
    }

    /** #378: without a running campaign, Cuaderno → Jornal opens the Farm's labour, labelled as such. */
    @Test
    fun cuadernoJornalWithoutCampaignOpensTheFarmsLabour() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Campo").performClick()
        waitForTag("add-farm")
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Sin Campaña E2E")
        saveEditor("save-farm", "farm-name")
        waitForSaved("farm-name", "Finca Sin Campaña E2E")

        composeRule.onNodeWithTag("bottom-Cuaderno").performClick()
        chooseNotebookFarm("Finca Sin Campaña E2E")
        // #417: without a Campaign the Cuaderno still opens on the Farm's own views.
        waitForTag("notebook-views")
        clickByTag("notebook-quick-labour")
        waitForTag("expenses-root")
        waitForNodeOrDump("Jornal fuera de campaña") { composeRule.onAllNodesWithText("Jornal fuera de campaña") }
        composeRule.onNodeWithTag("bottom-Cuaderno").assertIsSelected()
    }

    @Test
    fun avisosPlansWorkAndCuadernoRegistersIt() {
        // CR-011 §12: the Cuaderno records what happened; Avisos plans the future.
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Avisos").performClick()
        clickByTag("agenda-plan-work")
        waitForTag("register-activity-root")
        waitForText("Planificar trabajo")
        // Planning stays under the tab where it started.
        composeRule.onNodeWithTag("bottom-Avisos").assertIsSelected()
        assertEquals(0, composeRule.onAllNodesWithText("Registrar trabajo").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Registrar o planificar").fetchSemanticsNodes().size)
    }

    @Test
    fun profileShowsMisMaquinasAndNoProntoRows() {
        // CR-011 §7/§25: «Mis máquinas», and no provisional «Pronto» rows.
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Perfil").performClick()
        waitForTag("profile-root")
        composeRule.onNodeWithTag("profile-machinery").assertExists()
        waitForText("Mis máquinas")
        assertEquals(0, composeRule.onAllNodesWithText("Pronto").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("profile-account-later").assertDoesNotExist()
    }

    /** Phase 21C: the release notes of this APK and the privacy text are one tap from Perfil. */
    @Test
    fun helpAndPrivacyAreReachableFromPerfil() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Perfil").performClick()
        clickByTag("profile-help-news")
        waitForTag("help-news")
        // The CHANGELOG bundled at build time is read and shown as notes.
        waitForTag("help-news-item")
        pressBack()
        clickByTag("profile-help-privacy")
        waitForTag("help-privacy")
        assertTextVisible("Lo que queda en tu teléfono")
    }

    @Test
    fun developerGalleryIsReachableFromDevProfile() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Perfil").performClick()

        composeRule.onNodeWithText("Catálogo de diseño (DEV)").performScrollTo().performClick()

        composeRule.onNodeWithTag("component-catalogue-root").assertIsDisplayed()
    }

    private fun enterMainShell() {
        waitForText("Saltar")
        composeRule.onNodeWithText("Saltar").performClick()
        waitForTag("home-reference-root")
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
    }

    /**
     * Waits for observable UI state instead of relying on timing luck.
     *
     * CI emulators are much slower than a developer machine: cold Room initialisation,
     * the first composition of a ModalBottomSheet and activity recreation can each
     * exceed a 5s budget, which is why this E2E failed at a different point on every
     * run. Every asynchronous transition now waits for the state it depends on.
     */
    /** Like [waitForTag], but a timeout reports what is on screen, so CI shows where it stopped. */
    private fun waitForTagOrDumpScreen(tag: String) {
        try {
            waitForTag(tag)
        } catch (timeout: ComposeTimeoutException) {
            val screen = runCatching { composeRule.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE) }
                .getOrElse { "(screen not readable: $it)" }
            throw AssertionError(
                "«$tag» not shown after $UI_TIMEOUT_MS ms.\nDatabase: ${probeDatabase()}\nThreads:\n${busyThreads()}\nOn screen:\n$screen",
                timeout,
            )
        }
    }

    /** Whether a fresh one-shot read and fresh observations of the app's database answer now. */
    private fun probeDatabase(): String = runCatching {
        val database = MaginaOlivoDatabase.getInstance(ApplicationProvider.getApplicationContext<Context>())
        runBlocking {
            val flowAll = withTimeoutOrNull(5_000) { database.harvestDao().observeAll().first().size }
            val flowRunning = withTimeoutOrNull(5_000) { database.harvestDao().observeRunningCampaigns().first().size }
            val oneShot = withTimeoutOrNull(5_000) {
                database.harvestDao().findById(java.util.UUID.randomUUID())
                "ok"
            }
            "observeAll=${flowAll ?: "NO EMISSION in 5 s"}, observeRunningCampaigns=${flowRunning ?: "NO EMISSION in 5 s"}, " +
                "oneShot=${oneShot ?: "NO ANSWER in 5 s"}"
        }
    }.getOrElse { "probe failed: $it" }

    /** Threads that are blocked or waiting outside the usual idle loopers, with their top frames. */
    private fun busyThreads(): String = Thread.getAllStackTraces().entries
        .filter { (thread, _) -> thread.state == Thread.State.BLOCKED || thread.name.contains("arch_disk_io") || thread.name.startsWith("DefaultDispatcher") }
        .joinToString("\n") { (thread, frames) ->
            "${thread.name} ${thread.state}: " + frames.take(8).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        }

    private fun waitForTag(tag: String, timeoutMillis: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() || revealInFarmList(tag)
        }
    }

    /**
     * #359: Mi Campo's summary sits above «Añadir finca» and the Farms; on a short screen those
     * rows are below the fold of the lazy list, so scroll the list to them.
     */
    private fun revealInFarmList(tag: String): Boolean = runCatching {
        if (composeRule.onAllNodesWithTag("farm-list").fetchSemanticsNodes().isEmpty()) return false
        composeRule.onNodeWithTag("farm-list").performScrollToNode(hasTestTag(tag))
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    /**
     * Waits until an editor sheet has closed and its saved name is on screen. Waiting for the
     * text alone could match the sheet's own field while it animates away, and the next click
     * then landed on the field instead of the new row.
     */
    private fun waitForSaved(editorTag: String, text: String, timeoutMillis: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(editorTag).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Waits for a node and, on timeout, fails with a named diagnostic.
     *
     * A ModalBottomSheet renders in its own window, so a silent no-op click on the screen
     * behind it and a sheet that opened without the expected child look identical from a
     * bare timeout. The diagnostic tells the two apart on the next CI run.
     */
    private fun waitForNodeOrDump(description: String, nodes: () -> SemanticsNodeInteractionCollection) {
        try {
            composeRule.waitUntil(UI_TIMEOUT_MS) { nodes().fetchSemanticsNodes().isNotEmpty() }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for $description.\n" + diagnostics(), timeout)
        }
    }

    /**
     * Built only from node collections this suite already uses, so the diagnostic can
     * never be the reason the instrumented sources fail to compile, and every lookup is
     * guarded so a missing node reports itself instead of masking the real failure.
     */
    private fun diagnostics(): String = buildString {
        appendLine("Current screen roots:")
        appendLine(dumpOrAbsent { composeRule.onAllNodes(isRoot(), useUnmergedTree = true) })
        appendLine("Campaign detail screen:")
        appendLine(dumpOrAbsent { composeRule.onAllNodesWithTag("campaign-detail-root", useUnmergedTree = true) })
        appendLine("Confirmation sheet title:")
        appendLine(dumpOrAbsent { composeRule.onAllNodesWithText("Confirmar cambio", useUnmergedTree = true) })
        appendLine("Confirmation button:")
        appendLine(dumpOrAbsent { composeRule.onAllNodesWithTag("confirm-campaign-action", useUnmergedTree = true) })
        appendLine("Lifecycle nodes present:")
        listOf("edit-campaign", "activate-campaign", "close-campaign", "reopen-campaign", "campaign-row")
            .forEach { tag ->
                appendLine("  $tag -> ${countOrZero { composeRule.onAllNodesWithTag(tag, useUnmergedTree = true) }}")
            }
    }

    private fun dumpOrAbsent(nodes: () -> SemanticsNodeInteractionCollection): String =
        runCatching { nodes().printToString(Int.MAX_VALUE) }.getOrElse { "  <not available: ${it.message}>" }

    private fun countOrZero(nodes: () -> SemanticsNodeInteractionCollection): Int =
        runCatching { nodes().fetchSemanticsNodes().size }.getOrElse { -1 }

    /**
     * Opens a bottom-sheet editor and proves it actually opened.
     *
     * A ModalBottomSheet composes into its own window after an animation. On a cold CI
     * emulator the trigger click occasionally lands while the screen behind it is still
     * settling and is swallowed, and the suite then fails much later, waiting for a field
     * that was never composed. One bounded retry turns that into a pass; a second failure
     * still fails loudly, with the diagnostic, so a real regression is never hidden.
     */
    private fun openSheet(triggerTag: String, expectedTag: String) {
        clickByTag(triggerTag)
        if (awaitTag(expectedTag, SHEET_TIMEOUT_MS)) return
        clickByTag(triggerTag)
        waitForNodeOrDump("<$expectedTag> after reopening <$triggerTag>") {
            composeRule.onAllNodesWithTag(expectedTag)
        }
    }

    private fun awaitTag(tag: String, timeoutMillis: Long): Boolean =
        try {
            waitForTag(tag, timeoutMillis)
            true
        } catch (timeout: ComposeTimeoutException) {
            false
        }

    /**
     * Clicks a node only once the click can actually land.
     *
     * Screens here are verticalScroll Columns, so a composed node can sit outside the
     * viewport: the wait succeeds, performClick silently hits nothing, and the suite fails
     * much later waiting for whatever that click should have produced. Scrolling first and
     * asserting displayed/enabled/clickable turns the silent no-op into a named failure.
     * The scroll is an attempt, because performScrollTo itself throws for a node that has
     * no scrollable ancestor, and plenty of these nodes do not.
     */
    private fun clickByTag(tag: String) {
        waitForNodeOrDump("clickable node <$tag>") { composeRule.onAllNodesWithTag(tag) }
        // A control can be composed before the screen behind it finishes loading, and
        // stays disabled until it does. Wait for that, bounded; the assertion below still
        // fails loudly if it never becomes enabled.
        runCatching {
            composeRule.waitUntil(UI_TIMEOUT_MS) {
                runCatching { composeRule.onNodeWithTag(tag).assertIsEnabled() }.isSuccess
            }
        }
        scrollIntoViewIfPossible { composeRule.onNodeWithTag(tag) }
        composeRule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
    }

    /**
     * Clicks the node carrying this text that can actually receive a click.
     *
     * The same words legitimately appear more than once — a Farm name is a row in the
     * list and a section heading on the screen that resolved it — and only one of them
     * is clickable. Picking that one keeps the assertion strict without making the test
     * depend on which screen happens to be composed at that instant.
     */
    private fun clickByText(text: String) {
        runCatching {
            composeRule.waitUntil(UI_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() || runCatching {
                    composeRule.onNodeWithTag("farm-list").performScrollToNode(hasText(text))
                    true
                }.getOrDefault(false)
            }
        }
        waitForNodeOrDump("clickable node \"$text\"") { composeRule.onAllNodesWithText(text) }
        // Lazy farm rows can already be composed below the viewport. Loading the
        // seasonal summary also changes their position after the first scroll.
        // Re-resolve and scroll until the target is visible, before issuing one click.
        if (composeRule.onAllNodesWithTag("farm-list").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.waitUntil(UI_TIMEOUT_MS) {
                runCatching {
                    composeRule.onNodeWithTag("farm-list").performScrollToNode(hasText(text))
                    clickableNodeWithText(text).assertIsDisplayed()
                }.isSuccess
            }
        }
        val node = clickableNodeWithText(text)
        scrollIntoViewIfPossible { node }
        node.assertIsDisplayed().assertIsEnabled().assertHasClickAction().performClick()
    }

    private fun clickableNodeWithText(text: String): SemanticsNodeInteraction {
        val matches = composeRule.onAllNodesWithText(text)
        val count = matches.fetchSemanticsNodes().size
        val index = (0 until count).firstOrNull { position ->
            runCatching { matches[position].assertHasClickAction() }.isSuccess
        }
        return matches[index ?: 0]
    }

    /**
     * Clicks a control inside a bottom-sheet editor.
     *
     * Sheet content is not always fully on screen: the soft keyboard opened by the text
     * input just before it can cover the sheet's own footer, which is why requiring
     * assertIsDisplayed here turned clicks that had always landed into failures. The
     * scroll attempt still brings the control into view when the sheet scrolls, and the
     * guarantees that matter are kept — the node exists, is enabled and is clickable.
     */
    private fun clickInSheetByTag(tag: String) {
        waitForNodeOrDump("sheet control <$tag>") { composeRule.onAllNodesWithTag(tag) }
        scrollIntoViewIfPossible { composeRule.onNodeWithTag(tag) }
        composeRule.onNodeWithTag(tag)
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
    }

    /**
     * UI polish v2: dates are chosen in the month picker sheet, never typed. Opens it from
     * [fieldTag], walks from the current month to [iso]'s month and confirms that day.
     */
    /** #414: a Farm with a single Parcel arrives with it ticked; tick the first one only when it is not. */
    private fun ensureFirstParcelSelected() {
        composeRule.waitForIdle()
        val first = composeRule.onAllNodesWithTag("activity-parcel-option")[0]
        first.performScrollTo()
        if (first.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) != true) first.performClick()
    }

    private fun pickDate(fieldTag: String, iso: String) {
        val target = java.time.LocalDate.parse(iso)
        // The field usually follows a text input: close the keyboard first so the resize it
        // causes cannot swallow the tap, and tap once more if the picker still did not open.
        closeSoftKeyboard()
        composeRule.waitForIdle()
        scrollIntoViewIfPossible { composeRule.onNodeWithTag(fieldTag) }
        composeRule.onNodeWithTag(fieldTag).performClick()
        if (!awaitTag("date-picker-sheet", SHEET_TIMEOUT_MS)) {
            scrollIntoViewIfPossible { composeRule.onNodeWithTag(fieldTag) }
            composeRule.onNodeWithTag(fieldTag).performClick()
            waitForNodeOrDump("<date-picker-sheet> after tapping <$fieldTag> again") {
                composeRule.onAllNodesWithTag("date-picker-sheet")
            }
        }
        val months = java.time.temporal.ChronoUnit.MONTHS.between(
            java.time.YearMonth.now(),
            java.time.YearMonth.from(target),
        )
        val step = if (months < 0) "date-picker-previous" else "date-picker-next"
        repeat(kotlin.math.abs(months).toInt()) {
            composeRule.onNodeWithTag(step).performClick()
            composeRule.waitForIdle()
        }
        waitForTag("date-picker-day-$iso")
        scrollIntoViewIfPossible { composeRule.onNodeWithTag("date-picker-day-$iso") }
        composeRule.onNodeWithTag("date-picker-day-$iso").performClick()
        scrollIntoViewIfPossible { composeRule.onNodeWithTag("date-picker-confirm") }
        composeRule.onNodeWithTag("date-picker-confirm").performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("date-picker-sheet").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun clickInSheetByText(text: String) {
        waitForNodeOrDump("sheet control \"$text\"") { composeRule.onAllNodesWithText(text) }
        scrollIntoViewIfPossible { composeRule.onNodeWithText(text) }
        composeRule.onNodeWithText(text)
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()
    }

    private fun scrollIntoViewIfPossible(node: () -> SemanticsNodeInteraction) {
        runCatching { node().performScrollTo() }
    }

    /** Asserts a text is really on screen, scrolling to it when the screen scrolls. */
    private fun assertTextVisible(text: String) {
        waitForText(text)
        scrollIntoViewIfPossible { composeRule.onNodeWithText(text) }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    /**
     * Saves an editor sheet and makes sure the tap landed. The keyboard is closed first so the
     * resize it causes cannot swallow the tap; if the sheet is still open after the usual wait
     * (the tap was lost, never "saved twice": a stored save closes the sheet in milliseconds),
     * the save button is tapped once more before the caller's own wait reports the dump.
     */
    private fun saveEditor(saveTag: String, editorTag: String) {
        closeSoftKeyboard()
        composeRule.waitForIdle()
        clickInSheetByTag(saveTag)
        val closed = try {
            composeRule.waitUntil(SHEET_TIMEOUT_MS) {
                composeRule.onAllNodesWithTag(editorTag).fetchSemanticsNodes().isEmpty()
            }
            true
        } catch (timeout: ComposeTimeoutException) {
            false
        }
        if (!closed && composeRule.onAllNodesWithTag(saveTag).fetchSemanticsNodes().isNotEmpty()) {
            clickInSheetByTag(saveTag)
        }
    }

    /** Design v3: parcels, campaigns, work and documents open from the Farm hub. */
    private fun openFarmSection(section: String) {
        // CR-011: the Farm's work list is reached through the one Cuaderno, opened on this Farm.
        if (section == "activities") {
            waitForTag("farm-section-notebook")
            clickByTag("farm-section-notebook")
            waitForTag("notebook-farm-works")
            clickByTag("notebook-farm-works")
            waitForTag("add-activity")
            return
        }
        waitForTag("farm-section-$section")
        clickByTag("farm-section-$section")
        waitForTag("farm-section-root")
    }

    private fun backToFarmHub() {
        // From the work list Back first returns to the Cuaderno, then to the hub.
        repeat(4) {
            if (composeRule.onAllNodesWithTag("farm-detail-root").fetchSemanticsNodes().isNotEmpty()) return
            pressBack()
            composeRule.waitForIdle()
        }
        waitForTag("farm-detail-root")
    }

    /** Mi Cuaderno opens the remembered Farm; earlier tests may have left others behind. */
    private fun chooseNotebookFarm(name: String) {
        waitForTag("notebook-context")
        val alreadyActive = composeRule.onAllNodes(hasTestTag("notebook-context") and hasText(name, substring = true))
            .fetchSemanticsNodes().isNotEmpty()
        if (!alreadyActive) {
            clickByTag("notebook-change-farm")
            composeRule.waitUntil(UI_TIMEOUT_MS) {
                composeRule.onAllNodes(hasTestTag("notebook-farm-option") and hasText(name)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasTestTag("notebook-farm-option") and hasText(name)).performScrollTo().performClick()
        }
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag("notebook-context") and hasText(name, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** #365: the day opened from Jornal shows its Jornales first; close them to see the day. */
    private fun closeJornalFocus() {
        waitForTag("jornada-register-labour")
        clickInSheetByTag("resource-detail-close")
        composeRule.waitUntil(UI_TIMEOUT_MS) { composeRule.onAllNodesWithTag("jornada-register-labour").fetchSemanticsNodes().isEmpty() }
    }

    private fun createParcel(name: String) {
        waitForTag("add-parcel")
        openParcelManual()
        composeRule.onNodeWithTag("parcel-name").performTextInput(name)
        waitForTag("save-parcel")
        saveEditor("save-parcel", "parcel-name")
        waitForSaved("parcel-name", name)
    }

    private fun openParcelManual() {
        clickByTag("add-parcel")
        clickByTag("add-parcel-manual")
        waitForTag("parcel-name")
    }

    /** Confirms an Activity lifecycle action once its ModalBottomSheet is actually composed. */
    private fun confirmActivityAction() {
        waitForNodeOrDump("confirmation sheet title") { composeRule.onAllNodesWithText("Confirmar cambio") }
        waitForNodeOrDump("confirm-activity-action") {
            composeRule.onAllNodesWithTag("confirm-activity-action", useUnmergedTree = true)
        }
        composeRule.onNodeWithTag("confirm-activity-action", useUnmergedTree = true).performClick()
    }

    /** Confirms a Campaign lifecycle action once its ModalBottomSheet is actually composed. */
    private fun confirmCampaignAction() {
        // The sheet title proves the ModalBottomSheet window opened at all; only then is a
        // missing button a defect inside the sheet rather than a click that never landed.
        waitForNodeOrDump("confirmation sheet title") { composeRule.onAllNodesWithText("Confirmar cambio") }
        waitForNodeOrDump("confirm-campaign-action") {
            composeRule.onAllNodesWithTag("confirm-campaign-action", useUnmergedTree = true)
        }
        composeRule.onNodeWithTag("confirm-campaign-action", useUnmergedTree = true).performClick()
    }

    private companion object {
        /** Generous enough for a cold CI emulator, still bounded so a real hang fails. */
        const val UI_TIMEOUT_MS = 15_000L
        const val SHEET_TIMEOUT_MS = 8_000L
    }
}
