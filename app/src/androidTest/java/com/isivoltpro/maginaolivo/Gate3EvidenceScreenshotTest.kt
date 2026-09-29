package com.isivoltpro.maginaolivo

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        assertTicketAmountFitsPreview()
        saveNode(tag = "ocr-review-reference-root", prefix = "delivery-ocr-review")
    }

    // ---------------------------------------------------------------- UI polish v2

    @Test
    fun captureUiPolishHome() = capture("home-reference-root", "ui-01-inicio") {
        com.isivoltpro.maginaolivo.feature.home.HomeScreen(
            UiPolishFixtures.home, java.time.LocalTime.of(10, 0), {}, {}, {}, {},
        )
    }

    @Test
    fun captureWeatherOnPhoto() = capture("home-reference-root", "ui-11-weather-home-fixture") {
        com.isivoltpro.maginaolivo.feature.home.HomeScreen(
            UiPolishFixtures.home.copy(
                today = WeatherVisualFixtures.today,
                weatherLocation = WeatherVisualFixtures.location,
                weather = WeatherVisualFixtures.fresh,
            ),
            java.time.LocalTime.of(12, 0), {}, {}, {}, {},
            feedNow = WeatherVisualFixtures.now,
            weatherMotion = false,
        )
    }

    @Test
    fun captureWeatherWeekAndItsLastDay() {
        capture("weather-week-root", "ui-12-weather-week-fixture") {
            com.isivoltpro.maginaolivo.feature.home.WeatherWeekScreen(
                com.isivoltpro.maginaolivo.feature.home.WeatherWeekUiState(
                    location = WeatherVisualFixtures.location,
                    weather = WeatherVisualFixtures.fresh,
                ),
                now = WeatherVisualFixtures.now,
                onBack = {},
                onRadar = {},
            )
        }
        composeRule.onNodeWithTag("weather-week-source").performScrollTo()
        composeRule.waitForIdle()
        saveNode("weather-week-root", "ui-13-weather-week-scrolled-fixture")
    }

    @Test
    fun captureUiPolishOlivar() = capture("farms-root", "ui-02-mi-olivar") {
        com.isivoltpro.maginaolivo.feature.farms.FarmListScreen(
            state = com.isivoltpro.maginaolivo.feature.farms.FarmListUiState(isLoading = false, farms = UiPolishFixtures.farms),
            onFarmSelected = {}, onCreate = {}, onRestore = {}, onRetry = {},
        )
    }

    @Test
    fun captureUiPolishFarm() = capture("farm-detail-root", "ui-03-finca") {
        com.isivoltpro.maginaolivo.feature.farms.FarmDetailScreen(
            state = com.isivoltpro.maginaolivo.feature.farms.FarmDetailUiState(isLoading = false, farm = UiPolishFixtures.farms.first()),
            onUpdate = {}, onCoverSelected = {}, onArchive = {}, onArchived = {},
        )
    }

    @Test
    fun captureUiPolishCampaign() = capture("campaign-detail-root", "ui-04-campana") {
        com.isivoltpro.maginaolivo.feature.campaigns.CampaignDetailScreen(
            com.isivoltpro.maginaolivo.feature.campaigns.CampaignDetailUiState(isLoading = false, campaign = UiPolishFixtures.campaign),
            {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun captureUiPolishCalendarAgenda() = capture("calendar-root", "ui-05-calendario-agenda") {
        com.isivoltpro.maginaolivo.feature.agenda.AgendaScreen(UiPolishFixtures.agenda, true, {}, {}, {}, {}, {})
    }

    @Test
    fun captureUiPolishCalendarMonth() {
        composeRule.setContent {
            MaginaOlivoTheme {
                com.isivoltpro.maginaolivo.feature.agenda.AgendaScreen(UiPolishFixtures.agenda, true, {}, {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithTag("agenda-view-month").performClick()
        composeRule.waitForIdle()
        saveNode(tag = "calendar-root", prefix = "ui-06-calendario-mes")
    }

    /** CR-011: the one Cuaderno with its six actions (was the old Quick Add sheet). */
    @Test
    fun captureUiPolishNotebook() = capture("notebook-root", "ui-07-cuaderno") {
        com.isivoltpro.maginaolivo.feature.notebook.NotebookHomeScreen(
            isLoading = false,
            error = null,
            farms = UiPolishFixtures.farms,
            activeFarm = UiPolishFixtures.farms.first(),
            notebook = com.isivoltpro.maginaolivo.feature.notebook.NotebookUiState(
                isLoading = false,
                campaigns = listOf(UiPolishFixtures.campaign),
                selectedCampaignId = UiPolishFixtures.campaign.id,
                notebook = com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook.project(
                    UiPolishFixtures.campaign, emptyList(), emptyList(), emptyList(), emptyList(),
                ),
            ),
            actions = com.isivoltpro.maginaolivo.feature.notebook.NotebookActions(),
            onSelectFarm = {},
            onSelectCampaign = {},
            onQuickAction = {},
            parcelContext = "Parcela Norte",
        )
    }

    @Test
    fun captureUiPolishActivity() = capture("activity-detail-root", "ui-08-actuacion") {
        com.isivoltpro.maginaolivo.feature.activities.ActivityDetailScreen(
            com.isivoltpro.maginaolivo.feature.activities.ActivityDetailUiState(isLoading = false, activity = UiPolishFixtures.activity),
            {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun captureUiPolishHarvest() = capture("harvests-root", "ui-09-cosecha") {
        com.isivoltpro.maginaolivo.feature.harvests.HarvestsScreen(
            state = UiPolishFixtures.harvestState,
            today = UiPolishFixtures.today,
            onCreate = {},
            onHarvestSelected = {},
        )
    }

    @Test
    fun captureUiPolishProfile() = capture("profile-root", "ui-10-perfil") {
        com.isivoltpro.maginaolivo.feature.profile.ProfileScreen(
            appVersion = "0.1.0",
            notificationsOn = true,
            onNotifications = {},
            onMachinery = {},
        )
    }

    private fun capture(tag: String, prefix: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { MaginaOlivoTheme { content() } }
        composeRule.waitForIdle()
        saveNode(tag = tag, prefix = prefix)
    }

    private fun assertTicketAmountFitsPreview() {
        val previewBounds = composeRule
            .onNodeWithTag("ocr-ticket-preview")
            .fetchSemanticsNode()
            .boundsInRoot
        val amountBounds = composeRule
            .onNodeWithTag("ocr-ticket-amount")
            .fetchSemanticsNode()
            .boundsInRoot

        check(amountBounds.top >= previewBounds.top && amountBounds.bottom <= previewBounds.bottom) {
            "OCR amount is clipped by the ticket preview: amount=$amountBounds preview=$previewBounds"
        }
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
            targetContext.filesDir,
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
