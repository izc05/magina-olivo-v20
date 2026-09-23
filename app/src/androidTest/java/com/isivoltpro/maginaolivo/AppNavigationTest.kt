package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick().assertIsSelected()
        composeRule.onNodeWithTag("farms-root").assertIsDisplayed()

        composeRule.onNodeWithTag("bottom-Calendario").performClick().assertIsSelected()
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
        composeRule.onNodeWithTag("bottom-Calendario").performClick()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("calendar-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Calendario").assertIsSelected()
    }

    @Test
    fun revisitingRootDoesNotAddDuplicateDestination() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        composeRule.onNodeWithTag("bottom-Inicio").performClick()
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()

        pressBack()

        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Inicio").assertIsSelected()
    }

    @Test
    fun registerRootOpensContextSheetBeforeFlow() {
        enterMainShell()

        composeRule.onNodeWithTag("bottom-Registrar").performClick()
        composeRule.onNodeWithTag("register-action-sheet").assertIsDisplayed()
        clickInSheetByText("Registrar actuación")

        waitForTag("register-activity-root")
        composeRule.onNodeWithTag("register-activity-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Registrar").assertIsSelected()
    }

    @Test
    fun registerSheetCanBeCancelledWithoutChangingRoot() {
        enterMainShell()

        composeRule.onNodeWithTag("bottom-Registrar").performClick()
        composeRule.onNodeWithText("Cancelar").performClick()

        composeRule.onNodeWithTag("register-action-sheet").assertDoesNotExist()
        composeRule.onNodeWithTag("home-reference-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Inicio").assertIsSelected()
    }

    @Test
    fun nestedFarmRouteReturnsToOlivar() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("add-farm").fetchSemanticsNodes().isNotEmpty()
        }
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("La Solana")
        clickInSheetByTag("save-farm")
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("La Solana").fetchSemanticsNodes().size == 1
        }
        clickByText("La Solana")
        waitForTag("farm-detail-root")
        composeRule.onNodeWithTag("farm-detail-root").assertIsDisplayed()

        pressBack()
        composeRule.waitForIdle()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("farms-root").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("farms-root").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom-Mi Olivar").assertIsSelected()
    }

    @Test
    fun parcelCanBeCreatedAndOpenedFromItsFarm() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("add-farm").fetchSemanticsNodes().isNotEmpty()
        }
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Los Llanos")
        clickInSheetByTag("save-farm")
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Los Llanos").fetchSemanticsNodes().isNotEmpty()
        }
        clickByText("Los Llanos")

        openSheet("add-parcel", "parcel-name")
        composeRule.onNodeWithTag("parcel-name").performTextInput("Parcela Alta")
        clickInSheetByTag("save-parcel")
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
            composeRule.onAllNodesWithText("Sin registrar", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("bottom-Mi Olivar").assertIsSelected()
    }

    @Test
    fun farmParcelCampaignLifecyclePersistsAcrossRecreation() {
        enterMainShell()

        // Mi Olivar
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        waitForTag("add-farm")

        // Farm: open the editor, fill it, save, and wait for the persisted row.
        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Campaña E2E")
        waitForTag("save-farm")
        clickInSheetByTag("save-farm")
        waitForText("Finca Campaña E2E")

        // Farm detail
        clickByText("Finca Campaña E2E")
        waitForTag("add-parcel")

        // Parcel: open the editor, fill it, save, and wait for the persisted row.
        openSheet("add-parcel", "parcel-name")
        composeRule.onNodeWithTag("parcel-name").performTextInput("Parcela Campaña E2E")
        waitForTag("save-parcel")
        clickInSheetByTag("save-parcel")
        waitForText("Parcela Campaña E2E")

        // Campaign: open the editor, fill it, select the Parcel, save.
        waitForTag("add-campaign")
        openSheet("add-campaign", "campaign-name")
        composeRule.onNodeWithTag("campaign-name").performTextInput("Campaña 2026/27 E2E")
        // Past start date: closing uses the device clock, so a future start would make
        // the legal close date depend on the day the suite runs.
        waitForTag("campaign-start-date")
        composeRule.onNodeWithTag("campaign-start-date").performTextInput("2026-01-01")
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

        // ACTIVE -> HARVEST
        clickByText("Iniciar recolección")
        confirmCampaignAction()

        // HARVEST -> CLOSED
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
        // Truthful summaries: the detail renders exactly three metric cards - kg, yield
        // and expenses - and all three read "Sin datos" because no harvest, delivery or
        // expense exists yet. The card labels are unique, so each one is addressed
        // unambiguously; "Sin datos" alone matches all three and cannot be.
        listOf("Kg recogidos", "Rendimiento", "Gastos").forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        composeRule.onAllNodesWithText("Sin datos").assertCountEquals(3)

        // A closed campaign stays protected after the restart, and reopening it is an
        // explicit, confirmed action that returns the aggregate to an editable state.
        waitForText("Histórico protegido")
        composeRule.onNodeWithText("Histórico protegido").assertIsDisplayed()

        // CLOSED -> HARVEST
        clickByTag("reopen-campaign")
        confirmCampaignAction()

        // HARVEST -> CLOSED again
        clickByTag("close-campaign")
        confirmCampaignAction()
        waitForText("Histórico protegido")
        composeRule.onNodeWithText("Parcela Campaña E2E").assertIsDisplayed()
    }

    @Test
    fun oneActivityTargetsTwoParcelsAsASingleCanonicalRecord() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        waitForTag("add-farm")

        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Actuación E2E")
        waitForTag("save-farm")
        clickInSheetByTag("save-farm")
        waitForText("Finca Actuación E2E")
        clickByText("Finca Actuación E2E")
        waitForTag("add-parcel")

        createParcel("Parcela Norte E2E")
        createParcel("Parcela Sur E2E")

        // One activity, two parcels selected.
        waitForTag("add-activity")
        openSheet("add-activity", "activity-description")
        composeRule.onNodeWithTag("activity-description").performTextInput("Poda multiparcela E2E")
        waitForTag("activity-date")
        composeRule.onNodeWithTag("activity-date").performTextInput("2026-01-15")
        waitForTag("activity-parcel-option")
        composeRule.onAllNodesWithTag("activity-parcel-option")[0].performScrollTo().performClick()
        composeRule.onAllNodesWithTag("activity-parcel-option")[1].performScrollTo().performClick()
        waitForTag("save-activity")
        clickInSheetByTag("save-activity")
        waitForText("Poda multiparcela E2E")

        // Exactly ONE canonical Activity row, not one per parcel.
        composeRule.onAllNodesWithTag("activity-row").assertCountEquals(1)
        assertTextVisible("2 parcelas")

        waitForTag("activity-row")
        clickByTag("activity-row")
        waitForTag("activity-detail-root")

        // The single Activity carries both Parcel targets.
        composeRule.onAllNodesWithTag("activity-target").assertCountEquals(2)
        assertTextVisible("Parcela Norte E2E")
        assertTextVisible("Parcela Sur E2E")

        // PLANNED -> COMPLETED, then protected until an explicit reopen.
        clickByTag("complete-activity")
        confirmActivityAction()
        waitForText("Registro protegido")

        pressBack()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        waitForText("Poda multiparcela E2E")

        // Still one canonical Activity after the restart.
        composeRule.onAllNodesWithTag("activity-row").assertCountEquals(1)
        clickByText("Poda multiparcela E2E")
        waitForTag("activity-detail-root")
        composeRule.onAllNodesWithTag("activity-target").assertCountEquals(2)
        assertTextVisible("Registro protegido")
    }

    /**
     * The Registrar (+) entry point writes through the same aggregate as the Farm
     * detail: it is the real editor, not a reference screen, and a single Farm needs
     * no extra question.
     */
    @Test
    fun registrarPlusCreatesARealActivityOnTheSelectedFarm() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        waitForTag("add-farm")

        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Registrar E2E")
        waitForTag("save-farm")
        clickInSheetByTag("save-farm")
        waitForText("Finca Registrar E2E")
        clickByText("Finca Registrar E2E")
        waitForTag("add-parcel")
        createParcel("Parcela Registrar E2E")

        composeRule.onNodeWithTag("bottom-Registrar").performClick()
        waitForTag("register-action-sheet")
        clickInSheetByText("Registrar actuación")
        waitForTag("register-activity-root")

        // A single Farm resolves itself and the editor opens straight away; with more
        // than one the flow asks first. Wait for whichever of the two actually arrives
        // instead of sampling the screen before it has settled.
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("activity-description").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("register-farm-option").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("activity-description").fetchSemanticsNodes().isEmpty()) {
            clickByText("Finca Registrar E2E")
        }
        waitForTag("activity-description")
        composeRule.onNodeWithTag("activity-description").performTextInput("Riego desde Registrar")
        waitForTag("activity-date")
        composeRule.onNodeWithTag("activity-date").performTextInput("2026-02-02")
        waitForTag("activity-parcel-option")
        composeRule.onAllNodesWithTag("activity-parcel-option")[0].performScrollTo().performClick()
        clickInSheetByTag("save-activity")

        waitForText("Riego desde Registrar")
        composeRule.onAllNodesWithTag("activity-row").assertCountEquals(1)

        // The same Activity is the one the Farm detail shows: one record, one home.
        //
        // Mi Olivar restores its own saved back stack, so returning to it lands back on
        // the Farm detail this test was already on rather than on the Farm list. Accept
        // either, and only look the Farm up again when the list is what came back.
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("farm-detail-root").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("add-farm").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("farm-detail-root").fetchSemanticsNodes().isEmpty()) {
            clickByText("Finca Registrar E2E")
        }
        waitForTag("farm-detail-root")
        waitForTag("add-activity")
        waitForText("Riego desde Registrar")
        composeRule.onAllNodesWithTag("activity-row").assertCountEquals(1)
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
        composeRule.onNodeWithTag("bottom-Mi Olivar").performClick()
        waitForTag("add-farm")

        openSheet("add-farm", "farm-name")
        composeRule.onNodeWithTag("farm-name").performTextInput("Finca Tipada E2E")
        waitForTag("save-farm")
        clickInSheetByTag("save-farm")
        waitForText("Finca Tipada E2E")
        clickByText("Finca Tipada E2E")
        waitForTag("add-parcel")
        createParcel("Parcela Tipada E2E")

        waitForTag("add-activity")
        openSheet("add-activity", "activity-description")
        composeRule.onNodeWithTag("activity-description").performTextInput("Trabajo tipado E2E")
        waitForTag("activity-date")
        composeRule.onNodeWithTag("activity-date").performTextInput("2026-04-08")

        // Observación is the default type and has no structured fields at all.
        composeRule.onAllNodesWithTag("activity-detail-block").assertCountEquals(0)

        // Poda shows pruning fields, and only those.
        clickInSheetByText("Poda")
        waitForTag("detail-workerCount")
        composeRule.onAllNodesWithTag("detail-volumeM3").assertCountEquals(0)

        // Switching to Riego swaps the whole block: no pruning field is left behind.
        clickInSheetByText("Riego")
        waitForTag("detail-volumeM3")
        composeRule.onAllNodesWithTag("detail-workerCount").assertCountEquals(0)
        composeRule.onNodeWithTag("detail-volumeM3").performScrollTo().performTextInput("240")
        composeRule.onNodeWithTag("detail-sectorText").performScrollTo().performTextInput("Sector 3")

        waitForTag("activity-parcel-option")
        composeRule.onAllNodesWithTag("activity-parcel-option")[0].performScrollTo().performClick()
        clickInSheetByTag("save-activity")
        waitForText("Trabajo tipado E2E")

        // The saved Activity carries the irrigation block it was given, and one record.
        composeRule.onAllNodesWithTag("activity-row").assertCountEquals(1)
        clickByTag("activity-row")
        waitForTag("activity-detail-root")
        waitForTag("activity-detail-summary")
        assertTextVisible("Volumen (m³): 240")
        assertTextVisible("Sector: Sector 3")

        // It survives a restart as part of the same aggregate, not as a second record.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        waitForTag("activity-detail-summary")
        assertTextVisible("Volumen (m³): 240")
    }

    @Test
    fun developerGalleryIsReachableFromDevProfile() {
        enterMainShell()
        composeRule.onNodeWithTag("bottom-Perfil").performClick()

        composeRule.onNodeWithText("Catálogo de diseño (DEV)").performClick()

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
    private fun waitForTag(tag: String, timeoutMillis: Long = UI_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
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
        waitForNodeOrDump("clickable node \"$text\"") { composeRule.onAllNodesWithText(text) }
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

    private fun createParcel(name: String) {
        waitForTag("add-parcel")
        openSheet("add-parcel", "parcel-name")
        composeRule.onNodeWithTag("parcel-name").performTextInput(name)
        waitForTag("save-parcel")
        clickInSheetByTag("save-parcel")
        waitForText(name)
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
