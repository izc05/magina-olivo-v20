package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.agenda.AgendaScreen
import com.isivoltpro.maginaolivo.feature.campaigns.*
import com.isivoltpro.maginaolivo.feature.farms.*
import com.isivoltpro.maginaolivo.feature.notebook.*
import com.isivoltpro.maginaolivo.feature.parcels.ParcelDraft
import com.isivoltpro.maginaolivo.feature.parcels.ParcelEditor
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import androidx.test.espresso.Espresso.closeSoftKeyboard
import org.junit.Rule
import org.junit.Test

/** Real agricultural UI; synthetic fixtures and callbacks only, no Room writes. */
class DarkAgriculturalSurfacesTest {
    @get:Rule val compose = createComposeRule()
    private val mode = AppearanceMode.valueOf(
        InstrumentationRegistry.getArguments().getString("appearance", "DARK"),
    )

    @Test fun farmHeadingHasReadableContrast() {
        show {
            FarmListScreen(FarmListUiState(isLoading = false, farms = UiPolishFixtures.farms), {}, {}, {}, {})
        }
        expectReadableText(compose.onNodeWithText("Mis fincas", useUnmergedTree = true))
        capture("farms")
    }

    @Test fun farmEditorHasReadableContrastAndReachableSave() {
        var saved: FarmDraft? = null
        show {
            FarmDetailScreen(FarmDetailUiState(isLoading = false, farm = UiPolishFixtures.farms.first()), { saved = it }, {}, {}, {})
        }
        capture("farm-photo")
        compose.onNodeWithText("Editar finca").performScrollTo().performClick()
        // The standard modal opens partially; expand it using its real accessibility action.
        val expandable = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        if (expandable.fetchSemanticsNodes().isNotEmpty()) {
            expandable.onFirst().performSemanticsAction(SemanticsActions.Expand) { it() }
            compose.waitForIdle()
        }
        expectReadableText(compose.onAllNodesWithText("Editar finca", useUnmergedTree = true).onLast())
        capture("farm-editor-top")
        compose.onNodeWithTag("farm-name").performClick().performTextInput(" QA")
        capture("farm-editor-keyboard")
        closeSoftKeyboard()
        compose.waitForIdle()
        compose.onNodeWithTag("save-farm").performScrollTo().assertIsDisplayed()
        capture("farm-editor-save")
        compose.onNodeWithTag("save-farm").performClick()
        compose.runOnIdle { assertTrue("Edited draft reaches the existing save callback", saved?.name?.contains(" QA") == true) }
    }

    @Test fun campaignHeadingHasReadableContrast() {
        show { CampaignDetailScreen(CampaignDetailUiState(isLoading = false, campaign = UiPolishFixtures.campaign), {}, {}, {}, {}, {}, {}) }
        expectReadableText(compose.onNodeWithText("Campaña de ejemplo", useUnmergedTree = true))
        capture("campaign")
    }

    @Test fun notebookActionLabelsHaveReadableContrast() {
        show {
            NotebookHomeScreen(
                isLoading = false, error = null, farms = UiPolishFixtures.farms,
                activeFarm = UiPolishFixtures.farms.first(),
                notebook = NotebookUiState(
                    isLoading = false, campaigns = listOf(UiPolishFixtures.campaign),
                    selectedCampaignId = UiPolishFixtures.campaign.id,
                    notebook = CampaignNotebook.project(UiPolishFixtures.campaign, emptyList(), emptyList(), emptyList(), emptyList()),
                ),
                actions = NotebookActions(), onSelectFarm = {}, onSelectCampaign = {}, onQuickAction = {},
            )
        }
        expectReadableText(compose.onNodeWithText("Trabajo", useUnmergedTree = true))
        capture("notebook")
    }

    @Test fun agendaHeadingHasReadableContrast() {
        show { AgendaScreen(UiPolishFixtures.agenda, true, {}, {}, {}, {}, {}, title = "Avisos") }
        expectReadableText(compose.onNodeWithText("Avisos", useUnmergedTree = true))
        capture("agenda")
    }

    @Test fun parcelEditorDisclosureHasReadableContrast() {
        show { ParcelEditor("Nueva parcela", ParcelDraft(), false, null, null, {}, {}) }
        expectReadableText(compose.onNodeWithText("Más datos del olivar", useUnmergedTree = true))
        capture("parcel-editor")
    }

    @Test fun thePhotographKeepsItsPixelsWhenAppearanceChanges() {
        var selectedMode by mutableStateOf(AppearanceMode.LIGHT)
        compose.setContent {
            MaginaOlivoTheme(selectedMode) {
                FarmDetailScreen(FarmDetailUiState(isLoading = false, farm = UiPolishFixtures.farms.first()), {}, {}, {}, {})
            }
        }
        compose.waitForIdle()
        val light = compose.onNodeWithTag("farm-detail-root").captureToImage().asAndroidBitmap()
        compose.runOnIdle { selectedMode = AppearanceMode.DARK }
        compose.waitForIdle()
        val dark = compose.onNodeWithTag("farm-detail-root").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        val directory = File(context.filesDir, "dark3-evidence").apply { mkdirs() }
        for ((label, bitmap) in listOf("light" to light, "dark" to dark)) {
            File(directory, "photo-identity-$label-${config.screenWidthDp}dp-font${(config.fontScale * 100).roundToInt()}.png")
                .outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        // Inside the photograph's upper half, away from bars, title/chips and rounded corners.
        val x = light.width / 4
        val y = light.height / 12
        val size = 32
        val lightPixels = IntArray(size * size)
        val darkPixels = IntArray(size * size)
        light.getPixels(lightPixels, 0, size, x, y, size, size)
        dark.getPixels(darkPixels, 0, size, x, y, size, size)
        assertTrue("The source photograph must have varied pixels", lightPixels.toSet().size > 8)
        assertEquals("Photo dimensions stay stable", light.width, dark.width)
        assertEquals("Photo height stays stable", light.height, dark.height)
        // GPU readback can round an unchanged 8-bit channel by one level (19/1024 pixels
        // in the diagnostic capture). Larger changes still reveal a filter or inversion.
        assertTrue("Appearance must not filter the photograph", lightPixels.indices.all { index ->
            listOf(0, 8, 16, 24).all { shift ->
                abs(((lightPixels[index] ushr shift) and 255) -
                    ((darkPixels[index] ushr shift) and 255)) <= 1
            }
        })
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            MaginaOlivoTheme(mode) {
                Surface(Modifier.fillMaxSize().testTag("dark3-root"), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun expectReadableText(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
        val bitmap = node.captureToImage().asAndroidBitmap()
        // Unmerged Text only: surrounding icons/cards cannot satisfy the contrast check.
        // Top-right lies in the text's ascent space and samples the actual painted backdrop.
        val background = luminance(bitmap.getPixel(bitmap.width - 1, 0))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val strongest = pixels.maxOf { pixel ->
            val foreground = luminance(pixel)
            (maxOf(foreground, background) + 0.05) / (minOf(foreground, background) + 0.05)
        }
        assertTrue("Actual $mode agricultural text contrast $strongest must be >=4.5:1", strongest >= 4.5)
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((argb ushr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        val output = File(context.filesDir, "dark3-evidence").apply { mkdirs() }
        val file = File(output, "$name-${mode.name.lowercase()}-${config.screenWidthDp}dp-font${(config.fontScale * 100).roundToInt()}.png")
        // Full native window includes the real modal and IME; a root node may be its underlay.
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        check(file.length() > 0)
    }
}
