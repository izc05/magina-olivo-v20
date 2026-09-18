package com.isivoltpro.maginaolivo

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.ui.reference.ocr.DeliveryOcrReviewReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.parcel.ParcelDetailReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class Phase3ParcelOcrReferenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun parcelDetailReferenceRenders() {
        composeRule.setContent {
            MaginaOlivoTheme {
                ParcelDetailReferenceScreen()
            }
        }
        composeRule.onNodeWithTag("parcel-detail-reference-root").assertIsDisplayed()
        captureDeviceScreenshot("parcel-detail")
        composeRule.onNodeWithText("Parcela Norte").assertIsDisplayed()
        composeRule.onNodeWithText("Histórico").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun ocrReviewReferenceRendersUnconfirmedState() {
        composeRule.setContent {
            MaginaOlivoTheme {
                DeliveryOcrReviewReferenceScreen()
            }
        }
        composeRule.onNodeWithTag("ocr-review-reference-root").assertIsDisplayed()
        captureDeviceScreenshot("ocr-review")
        composeRule.onNodeWithText("Por revisar").assertIsDisplayed()
        composeRule.onNodeWithText("Confirmar entrega").performScrollTo().assertIsDisplayed()
    }

    private fun captureDeviceScreenshot(prefix: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val configuration = instrumentation.targetContext.resources.configuration
        val fontScalePercent = (configuration.fontScale * 100f).roundToInt()
        val fileName = "$prefix-${configuration.screenWidthDp}dp-font$fontScalePercent.png"

        runShellCommand("mkdir -p /sdcard/gate3")
        runShellCommand("screencap -p /sdcard/gate3/$fileName")
    }

    private fun runShellCommand(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
            stream.readBytes()
        }
    }
}
