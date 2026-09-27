package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.feature.activities.ActivityDraft
import com.isivoltpro.maginaolivo.feature.activities.ActivityEditor
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Codex #253: when Registrar hoy already chose the type, the form does not ask for it again. */
class ActivityEditorPresetTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun aPresetTypeHidesTheTypeList() {
        show(lockInitialType = true)
        assertEquals(0, composeRule.onAllNodesWithTag("activity-type-option").fetchSemanticsNodes().size)
    }

    @Test fun withoutAPresetTheFarmerStillChoosesTheType() {
        show(lockInitialType = false)
        assertTrue(composeRule.onAllNodesWithTag("activity-type-option").fetchSemanticsNodes().isNotEmpty())
    }

    private fun show(lockInitialType: Boolean) {
        composeRule.setContent {
            MaginaOlivoTheme {
                ActivityEditor(
                    parcels = emptyList(),
                    descriptionError = null,
                    dateError = null,
                    parcelsError = null,
                    isSaving = false,
                    onSave = {},
                    onCancel = {},
                    initial = ActivityDraft(type = ActivityType.IRRIGATION, activityDate = LocalDate.of(2026, 9, 27)),
                    title = "Riego · Finca",
                    lockInitialType = lockInitialType,
                )
            }
        }
    }
}
