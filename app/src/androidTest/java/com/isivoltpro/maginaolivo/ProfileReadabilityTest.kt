package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.feature.profile.ProfileScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual ProfileScreen text layout/action; no operational DB/preferences writes. */
class ProfileReadabilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lightStatusDoesNotSplitOrHideTheNotificationExplanation() = verify(AppearanceMode.LIGHT)
    @Test fun darkStatusDoesNotSplitOrHideTheNotificationExplanation() = verify(AppearanceMode.DARK)

    private fun verify(mode: AppearanceMode) {
        val clicks = AtomicInteger()
        compose.setContent {
            MaginaOlivoTheme(mode) {
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    ProfileScreen("DEV QA", false, { clicks.incrementAndGet() }, {}, onHelp = {})
                }
            }
        }
        compose.onNodeWithTag("profile-notifications").performScrollTo()
        val title = layout("Notificaciones", "profile-notifications")
        assertEquals("The whole notification word fits without splitting off its last letter", 1, title.lineCount)
        capture(mode, "notifications")
        assertComplete(layout("Permisos de avisos", "profile-notifications"))
        compose.onNode(hasText("Desactivadas") and hasAnyAncestor(hasTestTag("profile-notifications")), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("profile-notifications").assertHasClickAction().performClick()
        assertEquals("The existing notification action remains reachable", 1, clicks.get())

        compose.onNodeWithTag("profile-help-offline").performScrollTo().assertHasClickAction()
        assertComplete(layout("Sin cobertura", "profile-help-offline"))
        capture(mode, "offline")
    }

    private fun layout(text: String, tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun assertComplete(result: TextLayoutResult) {
        assertFalse("No horizontal text clipping: text=${result.layoutInput.text}, size=${result.size.width}, paragraph=${result.multiParagraph.width}, lineRights=${(0 until result.lineCount).map(result::getLineRight)}", result.didOverflowWidth)
        assertFalse("No vertical text clipping", result.didOverflowHeight)
        for (line in 0 until result.lineCount) assertFalse("No elided explanation", result.isLineEllipsized(line))
    }

    private fun capture(mode: AppearanceMode, stage: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val config = context.resources.configuration
        val file = File(context.getExternalFilesDir(null), "profile-readability-evidence/${mode.name.lowercase()}-$stage-sw${config.smallestScreenWidthDp}dp-font${config.fontScale}.png")
        file.parentFile!!.mkdirs()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
