package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.isivoltpro.maginaolivo.domain.activity.ActivityParcelOption
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.feature.activities.ActivityDraft
import com.isivoltpro.maginaolivo.feature.activities.ActivityDetailFields
import com.isivoltpro.maginaolivo.feature.activities.ActivityEditor
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The work form is short: one type chip, the essentials, and the rest folded under "Más opciones". */
class ActivityEditorCompactTest {
    @get:Rule val composeRule = createComposeRule()

    private val parcels = listOf(
        ActivityParcelOption(UUID.randomUUID(), "Pol. 14 · Parc. 104", null),
        ActivityParcelOption(UUID.randomUUID(), "Campanil", null),
    )

    @Test fun oneTypeAtATimeAndTheOptionalFieldsStartFolded() {
        var saved: ActivityDraft? = null
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), description = "Cura")) { saved = it }

        // Picking a type replaces the previous one: never two ticked.
        typeChip("Poda").performScrollTo().performClick()
        typeChip("Abonado").performScrollTo().performClick()
        typeChip("Abonado").assertIsSelected()
        typeChip("Poda").assertIsNotSelected()
        // #410: Riego and Tratamiento have their own Cuaderno actions; a new Trabajo does not repeat them.
        assertEquals(0, composeRule.onAllNodesWithTag("activity-type-option").filter(hasText("Riego")).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("activity-type-option").filter(hasText("Tratamiento")).fetchSemanticsNodes().size)
        // Hour, machinery, notes and cost are out of the way until asked for.
        assertEquals(0, composeRule.onAllNodesWithTag("activity-cost").fetchSemanticsNodes().size)
        composeRule.onAllNodesWithTag("activity-parcel-option")[0].performScrollTo().performClick()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(ActivityType.FERTILIZATION, saved?.type)
            assertEquals(setOf(parcels[0].id), saved?.parcelIds)
        }
    }

    /** #410: editing an existing Riego keeps its own type selectable even though a new Trabajo hides it. */
    @Test fun anExistingIrrigationKeepsItsType() {
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.IRRIGATION, description = "Riego"))
        typeChip("Riego").performScrollTo().assertIsSelected()
    }

    @Test fun moreOptionsUnfoldsAndAnEditWithACostStartsOpen() {
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27)))
        composeRule.onNodeWithTag("activity-more").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity-cost").performScrollTo()
    }

    @Test fun anEditThatAlreadyHasACostShowsIt() {
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), costMinor = 5_000))
        composeRule.onNodeWithTag("activity-cost").performScrollTo()
    }

    @Test fun taskSpecificFieldsStartFoldedAndRemainAvailable() {
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.PRUNING))
        composeRule.onNodeWithTag("activity-detail-more").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("activity-detail-block").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("activity-detail-more").performClick()
        composeRule.onNodeWithTag("detail-${ActivityDetailFields.PRUNING_TYPE}")
            .performScrollTo().assertIsDisplayed()
    }

    /** #414 (QA 1, 3, 8): Riego from the Cuaderno opens its fields, plans nothing and needs no words. */
    @Test fun aQuickIrrigationIsShortAndSavesAsDoneWork() {
        var saved: ActivityDraft? = null
        show(
            ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.IRRIGATION),
            onSave = { saved = it }, options = parcels.take(1), doneWork = true,
        )
        // The irrigation fields are open at once, without «Detalles».
        composeRule.onNodeWithTag("detail-volumeM3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Detalle breve (opcional)").assertExists()
        // Planning and reminders belong to Avisos, not to work already done.
        composeRule.onNodeWithTag("activity-more").performScrollTo().performClick()
        assertEquals(0, composeRule.onAllNodesWithTag("planning-time").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("planning-people").fetchSemanticsNodes().size)
        // The only Parcel is already ticked: saving needs no other tap.
        composeRule.onNodeWithTag("save-activity").performScrollTo().assertTextContains("Guardar riego").performClick()
        composeRule.runOnIdle {
            assertEquals(ActivityType.IRRIGATION, saved?.type)
            assertEquals("Riego", saved?.description)
            assertEquals(setOf(parcels[0].id), saved?.parcelIds)
            assertEquals(null, saved?.planning)
        }
    }

    /** #414: Observación says nothing by itself, so it still asks for a description. */
    @Test fun anObservationStillAsksForWords() {
        var saved: ActivityDraft? = null
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.OBSERVATION), onSave = { saved = it }, doneWork = true)
        composeRule.onNodeWithText("Descripción").assertExists()
        composeRule.onAllNodesWithTag("activity-parcel-option")[0].performScrollTo().performClick()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("", saved?.description) }
    }

    /** #414: outside the Cuaderno quick entry (Avisos, the Farm's sheet) planning stays available. */
    @Test fun planningStaysOutsideTheQuickEntry() {
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.PRUNING))
        composeRule.onNodeWithTag("activity-more").performScrollTo().performClick()
        composeRule.onNodeWithTag("planning-people").performScrollTo().assertIsDisplayed()
    }

    private fun typeChip(label: String) =
        composeRule.onAllNodesWithTag("activity-type-option").filterToOne(hasText(label))

    private fun show(
        initial: ActivityDraft,
        options: List<ActivityParcelOption> = parcels,
        doneWork: Boolean = false,
        onSave: (ActivityDraft) -> Unit = {},
    ) {
        composeRule.setContent {
            MaginaOlivoTheme {
                ActivityEditor(
                    parcels = options,
                    descriptionError = null,
                    dateError = null,
                    parcelsError = null,
                    isSaving = false,
                    onSave = onSave,
                    onCancel = {},
                    initial = initial,
                    lockInitialType = doneWork,
                    doneWork = doneWork,
                )
            }
        }
    }
}
