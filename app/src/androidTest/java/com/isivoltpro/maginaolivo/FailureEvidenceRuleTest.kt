package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Passing, opt-in sentinel proves real capture while Compose is alive, without a flaky failure. */
class FailureEvidenceRuleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun syntheticSentinelRetainsOriginalFailureAndCapturesBeforeTeardown() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("qaFailureDiagnostics") == "true")
        compose.setContent { Text("Synthetic failure evidence fixture") }
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (unavailableSemantics in listOf(false, true)) {
            val label = if (unavailableSemantics) "synthetic-sentinel-unavailable" else "synthetic-sentinel"
            val sentinel = AssertionError("qa_expected_evidence_sentinel")
            val rule = FailureEvidenceRule {
                compose.onNodeWithText("Synthetic failure evidence fixture").assertIsDisplayed()
                if (unavailableSemantics) error("qa_expected_semantics_unavailable")
                compose.onRoot(useUnmergedTree = true).printToString()
            }
            val description = Description.createTestDescription(FailureEvidenceRuleTest::class.java, label)
            try {
                rule.apply(object : Statement() {
                    override fun evaluate() { throw sentinel }
                }, description).evaluate()
                fail("Expected original sentinel")
            } catch (actual: Throwable) {
                assertSame("Capture must preserve the original failure object", sentinel, actual)
            }
            val folder = File(context.getExternalFilesDir(null), "failure-evidence/${description.className}-$label")
            for (name in listOf("failure.txt", "semantics.txt", "screen.png", "active-window.txt", "window.txt", "ime.txt", "activity.txt", "logcat.txt")) {
                assertTrue("Missing sentinel evidence: $name", File(folder, name).length() > 0)
            }
            val semantics = File(folder, "semantics.txt").readText()
            assertTrue(if (unavailableSemantics) semantics.contains("qa_expected_semantics_unavailable")
                else semantics.contains("Synthetic failure evidence fixture"))
            val window = File(folder, "window.txt").readText()
            assertTrue("Expected window state, not command usage", window.contains("WINDOW MANAGER") || window.contains("Window #"))
            val ime = File(folder, "ime.txt").readText()
            assertTrue("Expected IME state, not command usage", ime.contains("Input Method Manager") || ime.contains("InputMethod"))
            val activity = File(folder, "activity.txt").readText()
            assertTrue("Expected activity state, not command usage", activity.contains("ACTIVITY MANAGER ACTIVITIES") || activity.contains("ActivityRecord"))
            assertTrue("Expected structured threadtime logcat", Regex("\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+")
                .containsMatchIn(File(folder, "logcat.txt").readText()))
            // Distinguish deliberate passing self-test evidence from actual suite failures.
            File(folder, "synthetic-self-test.txt").writeText("Expected sentinel caught by passing FailureEvidenceRuleTest; not a suite failure.\n")
        }
    }
}
