package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
        typeChip("Tratamiento").performScrollTo().performClick()
        typeChip("Abonado").performScrollTo().performClick()
        typeChip("Abonado").assertIsSelected()
        typeChip("Tratamiento").assertIsNotSelected()
        // Hour, machinery, notes and cost are out of the way until asked for.
        assertEquals(0, composeRule.onAllNodesWithTag("activity-cost").fetchSemanticsNodes().size)
        composeRule.onAllNodesWithTag("activity-parcel-option")[0].performScrollTo().performClick()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(ActivityType.FERTILIZATION, saved?.type)
            assertEquals(setOf(parcels[0].id), saved?.parcelIds)
        }
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

    private fun typeChip(label: String) =
        composeRule.onAllNodesWithTag("activity-type-option").filterToOne(hasText(label))

    private fun show(initial: ActivityDraft, onSave: (ActivityDraft) -> Unit = {}) {
        composeRule.setContent {
            MaginaOlivoTheme {
                ActivityEditor(
                    parcels = parcels,
                    descriptionError = null,
                    dateError = null,
                    parcelsError = null,
                    isSaving = false,
                    onSave = onSave,
                    onCancel = {},
                    initial = initial,
                )
            }
        }
    }
}
