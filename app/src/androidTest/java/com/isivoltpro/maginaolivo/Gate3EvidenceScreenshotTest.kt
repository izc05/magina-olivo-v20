package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.ui.reference.ocr.DeliveryOcrReviewReferenceScreen
import com.isivoltpro.maginaolivo.ui.reference.parcel.ParcelDetailReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gate3EvidenceScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureParcelDetailReference() {
        composeRule.setContent {
            MaginaOlivoTheme {
                ParcelDetailReferenceScreen()
            }
        }
        composeRule.waitForIdle()
        saveNode(tag = "parcel-detail-reference-root", prefix = "parcel-detail")
    }

    @Test
    fun captureDeliveryOcrReviewReference() {
        composeRule.setContent {
            MaginaOlivoTheme {
                DeliveryOcrReviewReferenceScreen()
            }
        }
        composeRule.waitForIdle()
        saveNode(tag = "ocr-review-reference-root", prefix = "delivery-ocr-review")
    }

    private fun saveNode(
        tag: String,
        prefix: String,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val configuration = targetContext.resources.configuration
        val widthDp = configuration.screenWidthDp
        val fontScalePercent = (configuration.fontScale * 100f).roundToInt()

        val outputDirectory = File(
            targetContext.getExternalFilesDir(null),
            "gate3",
        ).apply {
            mkdirs()
        }

        val outputFile = File(
            outputDirectory,
            "$prefix-${widthDp}dp-font${fontScalePercent}.png",
        )

        val bitmap = composeRule
            .onNodeWithTag(tag)
            .captureToImage()
            .asAndroidBitmap()

        FileOutputStream(outputFile).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Could not encode Gate 3 screenshot: ${outputFile.absolutePath}"
            }
        }

        check(outputFile.length() > 0L) {
            "Gate 3 screenshot was empty: ${outputFile.absolutePath}"
        }
    }
}
