package com.isivoltpro.maginaolivo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText

/**
 * Test-only classification of a save that never reached Room. It changes no timeout, no assertion
 * and no production code: the caller rethrows the original failure with this report suppressed, so
 * a red test stays red with its own stack while naming which of the possible causes happened.
 *
 * The four distinguishable states after the original 15-second Room assertion gives up:
 *  - FORM_GONE: the editor subtree no longer exists, so the tap was swallowed by a rebuilt tree.
 *  - SAVE_DISABLED: the button is there but disabled, so the tap could not act.
 *  - EDIT_LOST: the field no longer holds the replaced text, so the edit never reached state.
 *  - SAVE_NOT_PERSISTED: form, text and button are intact, so either the tap never reached the
 *    button or the save ran and its write did not land; the reported Room row tells them apart.
 */
internal fun SemanticsNodeInteractionsProvider.lostSaveReport(
    expectedText: String,
    storedDescription: String?,
    storedUpdatedAt: String?,
    saveEnabledBeforeClick: Boolean?,
): String {
    fun exists(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    fun textExists(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    val descriptionNodes = onAllNodesWithTag("activity-description").fetchSemanticsNodes()
    val fieldText = descriptionNodes.firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.EditableText)?.text
    val saveNodes = onAllNodesWithTag("save-activity").fetchSemanticsNodes()
    val saveEnabled = saveNodes.firstOrNull()?.let { !it.config.contains(SemanticsProperties.Disabled) }
    val state = when {
        descriptionNodes.isEmpty() || saveNodes.isEmpty() -> "FORM_GONE"
        saveEnabled == false -> "SAVE_DISABLED"
        fieldText != expectedText -> "EDIT_LOST"
        else -> "SAVE_NOT_PERSISTED"
    }
    return buildString {
        append("lost-save=").append(state)
        append(" room=").append(storedDescription?.let { "\"" + it + "\"" } ?: "null")
        append(" roomUpdatedAt=").append(storedUpdatedAt ?: "null")
        append(" field=").append(fieldText?.let { "\"" + it + "\"" } ?: "absent")
        append(" save=").append(if (saveNodes.isEmpty()) "absent" else "present")
        append(" saveEnabled=").append(saveEnabled)
        append(" saveEnabledBeforeClick=").append(saveEnabledBeforeClick)
        append(" permissionDialog=").append(if (exists("notification-permission-dialog")) "present" else "absent")
        // The G1b access gate replaces the whole protected tree, navigation included.
        append(" farmLinkLocked=").append(if (exists("farm-link-locked")) "present" else "absent")
        append(" detailRoot=").append(if (exists("activity-detail-root")) "present" else "absent")
        append(" plannedBanner=").append(if (textExists("Trabajo planificado")) "present" else "absent")
        // A silent refusal by one of the form guards leaves the editor open and Room untouched
        // too, so name the fields the form considers wrong: empty means nothing refused the save.
        append(" formErrors=").append(
            onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.Error), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .mapNotNull { it.config.getOrNull(SemanticsProperties.Error) }
                .joinToString("|")
                .ifEmpty { "none" }
        )
    }
}

/**
 * Test-only precondition for the save tap. Compose injects the tap at the centre of the node, and
 * `performScrollTo` leaves the primary action with its bottom edge on the keyboard boundary, so the
 * whole safety budget is half the button's height. If the centre ever falls inside the IME window
 * the keyboard eats the event and nothing reaches Room, which looks exactly like a silent timeout.
 * Asserting it here keeps that case loud and named; it relaxes nothing.
 */
internal fun assertSaveTapClearOfKeyboard(
    compose: androidx.compose.ui.test.junit4.AndroidComposeTestRule<
        androidx.test.ext.junit.rules.ActivityScenarioRule<MainActivity>, MainActivity>,
) {
    val bounds = compose.onNodeWithTag("save-activity").fetchSemanticsNode().boundsInWindow
    var imeTop = Float.MAX_VALUE
    var detail = "ime=unavailable"
    compose.activityRule.scenario.onActivity { activity ->
        val root = activity.window.decorView
        val insets = root.rootWindowInsets ?: return@onActivity
        val type = android.view.WindowInsets.Type.ime()
        if (!insets.isVisible(type)) { detail = "ime=hidden"; return@onActivity }
        imeTop = (root.height - insets.getInsets(type).bottom).toFloat()
        detail = "imeTop=" + imeTop + " rootHeight=" + root.height
    }
    val centre = (bounds.top + bounds.bottom) / 2f
    org.junit.Assert.assertTrue(
        "The Guardar tap point must stay clear of the keyboard: centre=" + centre +
            " bounds=" + bounds + " " + detail + " margin=" + (imeTop - centre),
        centre < imeTop,
    )
}
