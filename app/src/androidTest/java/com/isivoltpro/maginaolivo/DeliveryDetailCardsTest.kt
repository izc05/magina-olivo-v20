package com.isivoltpro.maginaolivo

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShare
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySource
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.domain.harvest.HarvestAllocation
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import com.isivoltpro.maginaolivo.domain.harvest.HarvestParcelOption
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveryDetailScreen
import com.isivoltpro.maginaolivo.feature.deliveries.DeliveryDetailUiState
import com.isivoltpro.maginaolivo.navigation.RootDestination
import com.isivoltpro.maginaolivo.navigation.bottomNavigationRoots
import com.isivoltpro.maginaolivo.ui.components.MoBottomBar
import com.isivoltpro.maginaolivo.ui.components.MoBottomBarItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.io.File
import java.time.LocalDate
import java.util.UUID
import org.junit.Test

/** Production detail with read-only synthetic records following the documented DEMO values. */
class DeliveryDetailCardsTest {
    @get:org.junit.Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val workspace = UUID.randomUUID()
    private val farm = UUID.randomUUID()
    private val campaign = UUID.randomUUID()
    private val parcel = UUID.randomUUID()
    private val date = LocalDate.of(2026, 10, 3)

    @Test fun lightCardsKeepEachValeAndUnknownValuesTruthful() = verify(AppearanceMode.LIGHT)
    @Test fun darkCardsKeepEachValeAndUnknownValuesTruthful() = verify(AppearanceMode.DARK)

    private fun verify(mode: AppearanceMode) {
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.setDecorFitsSystemWindows(it.window, false)
        }
        val current = mutableStateOf(delivery("DEMO-003", 1_730_000L, date, PesadaOrigin.TREE))
        val context = HarvestContext(farm, "Finca Demo Mágina", campaign, "Campaña de recogida 2026/27",
            CampaignStatus.HARVEST, LocalDate.of(2026, 9, 28), listOf(HarvestParcelOption(parcel, "El Barranco")))
        compose.setContent {
            MaginaOlivoTheme(mode) {
                Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
                    MoBottomBar(bottomNavigationRoots.map { destination ->
                        MoBottomBarItem(destination.label, destination.symbol, destination.isPrimaryAction, when (destination) {
                            RootDestination.Home -> MoIcons.Home
                            RootDestination.Olivar -> MoIcons.Tree
                            RootDestination.Notebook -> MoIcons.Notebook
                            RootDestination.Profile -> MoIcons.Person
                            RootDestination.Assistant -> MoIcons.Assistant
                        RootDestination.CampaignHub -> MoIcons.Campaign
                            RootDestination.Alerts -> MoIcons.Bell
                        })
                    }, selectedIndex = 2, onSelected = {}, onAddRecord = {})
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        DeliveryDetailScreen(
                            DeliveryDetailUiState(isLoading = false, delivery = current.value, context = context),
                            onUpdate = { error("No record writes in this UI fixture") },
                            onDelete = { error("No record deletion in this UI fixture") },
                            onRecordYield = { error("No analysis writes in this UI fixture") },
                            onRemoveYield = { error("No analysis removal in this UI fixture") },
                        )
                    }
                }
            }
        }
        capture(mode, "header")
        compose.onNodeWithTag("delivery-summary-card").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("DEMO-003", substring = true) and hasAnyAncestor(hasTestTag("delivery-header")))
            .performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(Weight.format(1_730_000L)) and hasAnyAncestor(hasTestTag("delivery-net-value")))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("edit-delivery").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("delivery-editor").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("delivery-editor").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("delivery-origin-chip").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("delivery-parcels-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("El Barranco").assertIsDisplayed()
        compose.onNode(hasText("Pendiente de distribuir") and hasAnyAncestor(hasTestTag("delivery-share"))).assertIsDisplayed()
        compose.onNodeWithTag("delivery-unallocated").assertTextEquals("Pendiente de distribuir: ${Weight.format(1_730_000L)}")
        capture(mode, "parcels")
        compose.onNodeWithTag("delivery-gross").performScrollTo()
        compose.onNode(hasText("No registrado") and hasAnyAncestor(hasTestTag("delivery-gross"))).assertIsDisplayed()
        assertMissingValueIsReadable("delivery-gross")
        compose.onNodeWithTag("delivery-tare").performScrollTo()
        compose.onNode(hasText("No registrado") and hasAnyAncestor(hasTestTag("delivery-tare"))).assertIsDisplayed()
        assertMissingValueIsReadable("delivery-tare")
        capture(mode, "weights")
        compose.onNodeWithTag("delivery-notes-card").performScrollTo().assertIsDisplayed()
        capture(mode, "notes")

        // The two records of 01-10 retain their own identity, origin and net weight.
        compose.runOnIdle { current.value = delivery("DEMO-002", 2_120_000L, LocalDate.of(2026, 10, 1), PesadaOrigin.TREE) }
        compose.onNodeWithTag("delivery-net-value").performScrollTo()
        compose.onNode(hasText(Weight.format(2_120_000L)) and hasAnyAncestor(hasTestTag("delivery-net-value"))).assertIsDisplayed()
        compose.onNodeWithText("DEMO-003", substring = true).assertDoesNotExist()
        compose.runOnIdle { current.value = delivery("DEMO-004", 980_000L, LocalDate.of(2026, 10, 1), PesadaOrigin.GROUND) }
        compose.onNodeWithTag("delivery-net-value").performScrollTo()
        compose.onNode(hasText(Weight.format(980_000L)) and hasAnyAncestor(hasTestTag("delivery-net-value"))).assertIsDisplayed()
        compose.onNodeWithTag("delivery-origin-chip").performScrollTo()
        compose.onNodeWithText("Suelo").assertIsDisplayed()
        compose.onNodeWithText("DEMO-002", substring = true).assertDoesNotExist()
        capture(mode, "same-day-own-vale")

        // A known exact allocation stays exact and produces no pending distribution.
        compose.runOnIdle { current.value = current.value.copy(
            shares = listOf(DeliveryShare(parcel, "El Barranco", HarvestAllocation.EXACT, 980_000L)),
            grossGrams = 1_100_000L, tareGrams = 120_000L,
        ) }
        compose.onNodeWithTag("delivery-parcels-card").performScrollTo()
        compose.onNodeWithTag("delivery-unallocated").assertDoesNotExist()
        compose.onNodeWithText("Pendiente de distribuir", substring = true).assertDoesNotExist()
        capture(mode, "exact-allocation")
        compose.onNodeWithTag("delivery-gross").performScrollTo()
        compose.onNode(hasText(Weight.format(1_100_000L)) and hasAnyAncestor(hasTestTag("delivery-gross"))).assertIsDisplayed()
        compose.onNodeWithTag("delivery-tare").performScrollTo()
        compose.onNode(hasText(Weight.format(120_000L)) and hasAnyAncestor(hasTestTag("delivery-tare"))).assertIsDisplayed()

        compose.runOnIdle { current.value = current.value.copy(editable = false, origin = null,
            farmName = null, campaignName = null, ticketNumber = null, notes = null) }
        compose.onNodeWithTag("edit-delivery").assertDoesNotExist()
        compose.onNodeWithText("Origen sin indicar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nombre de finca no disponible").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nombre de campaña no disponible").assertIsDisplayed()
        compose.onNodeWithTag("delivery-read-only").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("delivery-notes-card").performScrollTo()
        compose.onNode(hasText("No registrado") and hasAnyAncestor(hasTestTag("delivery-notes-card"))).assertIsDisplayed()
        capture(mode, "closed-missing-metadata")
    }

    private fun assertMissingValueIsReadable(field: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText("No registrado") and hasAnyAncestor(hasTestTag(field)))
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        org.junit.Assert.assertTrue("The actual placeholder text layout must exist", layouts.isNotEmpty())
        layouts.forEach { result ->
            org.junit.Assert.assertFalse("Missing values must not be clipped: field=$field size=${result.size} constraints=${result.layoutInput.constraints} lines=${result.lineCount} width=${result.didOverflowWidth} height=${result.didOverflowHeight}", result.hasVisualOverflow)
            val text = result.layoutInput.text.text
            for (line in 0 until result.lineCount - 1) {
                val end = result.getLineEnd(line, visibleEnd = true)
                org.junit.Assert.assertTrue("Missing values must wrap at spaces, not split a word: $text",
                    end == 0 || end >= text.length || text[end - 1].isWhitespace() || text[end].isWhitespace())
            }
        }
    }
    private fun delivery(ticket: String, grams: Long, day: LocalDate, origin: PesadaOrigin) = Delivery(
        id = UUID.randomUUID(), workspaceId = workspace, farmId = farm, campaignId = campaign,
        deliveryDate = day, destinationOrganizationId = null, destinationName = "Cooperativa Demo",
        netGrams = grams, grossGrams = null, tareGrams = null, deliveryNumber = null, ticketNumber = ticket,
        source = DeliverySource.MANUAL,
        shares = listOf(DeliveryShare(parcel, "El Barranco", HarvestAllocation.UNALLOCATED, null)),
        notes = "Registro de demostración", version = 1, farmName = "Finca Demo Mágina",
        campaignName = "Campaña de recogida 2026/27", harvestId = UUID.randomUUID(), origin = origin,
    )
    private fun capture(mode: AppearanceMode, stage: String) {
        compose.waitForIdle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = context.resources.configuration
        val file = File(context.getExternalFilesDir(null), "delivery-detail-evidence/${mode.name.lowercase()}-$stage-sw${config.smallestScreenWidthDp}dp-font${config.fontScale}.png")
        file.parentFile!!.mkdirs()
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
