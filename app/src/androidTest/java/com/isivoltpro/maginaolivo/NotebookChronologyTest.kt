package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.isivoltpro.maginaolivo.data.local.model.ActivityStatus
import com.isivoltpro.maginaolivo.domain.activity.ActivityType
import com.isivoltpro.maginaolivo.domain.notebook.CampaignNotebook
import com.isivoltpro.maginaolivo.feature.notebook.DiaryView
import com.isivoltpro.maginaolivo.feature.notebook.NotebookActions
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.util.UUID
import org.junit.Rule
import org.junit.Test

/** The Cuaderno reads day by day, today's line apart from yesterday's; planned work stays in Avisos (#424). */
class NotebookChronologyTest {
    @get:Rule val composeRule = createComposeRule()

    private val campaign = UiPolishFixtures.campaign
    private val today = UiPolishFixtures.today
    private val cure = UiPolishFixtures.activity.copy(
        id = UUID.randomUUID(), campaignId = campaign.id, type = ActivityType.PHYTOSANITARY,
        status = ActivityStatus.COMPLETED, activityDate = today, description = "Cura",
    )
    private val irrigation = UiPolishFixtures.activity.copy(
        id = UUID.randomUUID(), campaignId = campaign.id, status = ActivityStatus.COMPLETED,
        activityDate = today.minusDays(1), description = "Riego",
    )
    private val cleaning = UiPolishFixtures.activity.copy(
        id = UUID.randomUUID(), campaignId = campaign.id, type = ActivityType.PRUNING,
        status = ActivityStatus.PLANNED, activityDate = today.minusDays(1), description = "Trabajo de limpieza",
    )
    private val notebook = CampaignNotebook.project(campaign, listOf(cure, irrigation, cleaning), emptyList(), emptyList(), emptyList())

    @Test fun theDiaryDrawsOneLinePerDayAndNamesToday() {
        composeRule.setContent { MaginaOlivoTheme { Column { DiaryView(notebook, NotebookActions(), today = today) } } }
        composeRule.onNodeWithTag("notebook-day-today").assertTextContains("Hoy · ", substring = true)
        composeRule.onAllNodesWithTag("notebook-day").assertCountEquals(1)
        composeRule.onNodeWithTag("notebook-day").assertTextContains("Ayer · ", substring = true)
        // Only what was done is a diary fact; the planned cleaning is not listed.
        composeRule.onAllNodesWithTag("notebook-work").assertCountEquals(2)
        composeRule.onNodeWithText("Trabajo de limpieza").assertDoesNotExist()
        composeRule.onNodeWithText("Planificada").assertDoesNotExist()
        composeRule.onAllNodesWithTag("notebook-work")[0].assertTextContains("Completada", substring = true)
    }
}
