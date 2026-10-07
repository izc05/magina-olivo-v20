package com.isivoltpro.maginaolivo

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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

    /** #416: a new work carries no money field; «Más opciones» never offers a «Coste» for it. */
    @Test fun aNewWorkHasNoCostField() {
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27)))
        composeRule.onNodeWithTag("activity-more").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity-more").assertExists()
        assertEquals(0, composeRule.onAllNodesWithTag("activity-cost").fetchSemanticsNodes().size)
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

    @Test fun aTreatmentDoesNotAssumeTheWholeParcelUntilTheFarmerConfirmsIt() {
        var saved: ActivityDraft? = null
        val option = ActivityParcelOption(UUID.randomUUID(), "Olivar Norte", 20_000.0)
        show(
            ActivityDraft(activityDate = LocalDate.of(2026, 10, 5), type = ActivityType.PHYTOSANITARY),
            options = listOf(option), doneWork = true, onSave = { saved = it },
        )
        composeRule.onNodeWithTag("activity-parcel-area").performScrollTo().assertIsDisplayed()
        // The suggestion sits immediately below the field and may be outside a compact viewport.
        composeRule.onNodeWithTag("activity-parcel-use-full-area").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(null, saved?.parcelAreasM2?.get(option.id))
        }
    }

    @Test fun useAllExplicitlyConfirmsTheKnownParcelSurface() {
        var saved: ActivityDraft? = null
        val option = ActivityParcelOption(UUID.randomUUID(), "Olivar Norte", 20_000.0)
        show(
            ActivityDraft(activityDate = LocalDate.of(2026, 10, 5), type = ActivityType.PHYTOSANITARY),
            options = listOf(option), doneWork = true, onSave = { saved = it },
        )
        composeRule.onNodeWithTag("activity-parcel-use-full-area").performScrollTo().performClick()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(20_000.0, saved?.parcelAreasM2?.get(option.id)!!, 0.001)
        }
    }

    /**
     * #546/#440 (owner review on #560): a surface typed while a type that asks for it was chosen is
     * never saved under a type that does not; coming back never invents the whole Parcel.
     */
    @Test fun aSurfaceTypedForAnotherTypeIsNeverSavedHidden() {
        var saved: ActivityDraft? = null
        val option = ActivityParcelOption(UUID.randomUUID(), "Olivar Norte", 20_000.0)
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), description = "Trabajo"), options = listOf(option)) { saved = it }
        typeChip("Abonado").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity-parcel-option").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity-parcel-area").performScrollTo().performTextInput("0,5")
        typeChip("Poda").performScrollTo().performClick()
        composeRule.onNodeWithTag("activity-parcel-area").assertDoesNotExist()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(ActivityType.PRUNING, saved?.type)
            assertEquals(null, saved?.parcelAreasM2?.get(option.id))
        }
        // Back to Abonado: the typed surface, never the whole Parcel.
        typeChip("Abonado").performScrollTo().performClick()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(5_000.0, saved?.parcelAreasM2?.get(option.id)!!, 0.001) }
    }

    /** An edit whose new type does not ask for the surface keeps the one it had: no silent loss. */
    @Test fun anEditChangingToATypeWithoutSurfaceKeepsTheHistoricalOne() {
        var saved: ActivityDraft? = null
        val option = ActivityParcelOption(UUID.randomUUID(), "Olivar Norte", 20_000.0)
        show(
            ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.FERTILIZATION, description = "Abonado",
                parcelIds = setOf(option.id), parcelAreasM2 = mapOf(option.id to 4_000.0)),
            options = listOf(option), onSave = { saved = it },
        )
        typeChip("Poda").performScrollTo().performClick()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(4_000.0, saved?.parcelAreasM2?.get(option.id)!!, 0.001) }
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

    /** #435/#414: a quick entry dated tomorrow is not saved as a quiet plan; Avisos plans it. */
    @Test fun aQuickEntryDatedAheadUsesTheWorkspaceDayNotThePhoneDay() {
        var saved: ActivityDraft? = null
        val workspaceToday = LocalDate.of(2026, 10, 6)
        show(
            ActivityDraft(activityDate = LocalDate.of(2026, 10, 7), type = ActivityType.PHYTOSANITARY),
            options = parcels.take(1),
            doneWork = true,
            today = workspaceToday,
            onSave = { saved = it },
        )
        composeRule.onNodeWithText("La fecha es futura. Para trabajos pendientes usa Avisos → Planificar.").assertExists()
        composeRule.onNodeWithTag("save-activity").performScrollTo().assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertEquals(null, saved) }
    }

    /** #435: planning still accepts a date ahead of the Workspace's day. */
    @Test fun planningStillTakesADateAhead() {
        val workspaceToday = LocalDate.of(2026, 10, 6)
        show(
            ActivityDraft(activityDate = LocalDate.of(2026, 10, 7), type = ActivityType.PRUNING),
            today = workspaceToday,
        )
        composeRule.onNodeWithTag("save-activity").performScrollTo().assertIsEnabled()
    }

    /** #414: an Incidencia without category or detail keeps no made-up title (the save is refused upstream). */
    @Test fun anAnonymousIncidentGetsNoTitle() {
        var saved: ActivityDraft? = null
        show(
            ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.INCIDENT),
            options = parcels.take(1), doneWork = true, onSave = { saved = it },
        )
        composeRule.onNodeWithText("Detalle breve (o indica la categoría)").assertExists()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("", saved?.description) }
    }

    /** #414: with its category, an Incidencia takes the type as title. */
    @Test fun anIncidentWithACategorySaves() {
        var saved: ActivityDraft? = null
        show(
            ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.INCIDENT),
            options = parcels.take(1), doneWork = true, onSave = { saved = it },
        )
        composeRule.onNodeWithTag("detail-${ActivityDetailFields.CATEGORY}").performScrollTo().performTextInput("Granizo")
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("Incidencia", saved?.description) }
    }

    /** #414: a new Riego shows duration, volume and sector; no tariff; Sistema waits in «Más detalles». */
    @Test fun aNewIrrigationShowsOnlyTheEssentials() {
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.IRRIGATION), doneWork = true)
        composeRule.onNodeWithTag("detail-${ActivityDetailFields.SECTOR_TEXT}").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("detail-${ActivityDetailFields.UNIT_PRICE}").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("detail-${ActivityDetailFields.SYSTEM_TEXT}").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("activity-detail-advanced").performScrollTo().performClick()
        composeRule.onNodeWithTag("detail-${ActivityDetailFields.SYSTEM_TEXT}").performScrollTo().assertIsDisplayed()
    }

    /** #414: an older Poda that holds people and hours keeps them on screen and in what is saved. */
    @Test fun anOlderPodaKeepsItsPeopleAndHours() {
        var saved: ActivityDraft? = null
        show(
            ActivityDraft(
                activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.PRUNING, description = "Poda",
                parcelIds = setOf(parcels[0].id),
                detail = com.isivoltpro.maginaolivo.domain.activity.ActivityDetail.Pruning("Formación", 3, 6.0, null),
            ),
            onSave = { saved = it },
        )
        composeRule.onNodeWithTag("activity-detail-more").performScrollTo()
        composeRule.onNodeWithTag("detail-${ActivityDetailFields.WORKER_COUNT}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle {
            val pruning = saved?.detail as com.isivoltpro.maginaolivo.domain.activity.ActivityDetail.Pruning
            assertEquals(3, pruning.workerCount)
            assertEquals(6.0, pruning.hours!!, 0.0)
        }
    }

    /** #473: «abc» as hours is shown back with its error and nothing is saved, never a silent null. */
    @Test fun anUnreadableNumberBlocksTheSaveAndStaysOnScreen() {
        var saved: ActivityDraft? = null
        show(
            ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.IRRIGATION),
            options = parcels.take(1), doneWork = true, onSave = { saved = it },
        )
        composeRule.onNodeWithTag("detail-${ActivityDetailFields.VOLUME_M3}").performScrollTo().performTextInput("doce")
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.onNodeWithText("Escribe el volumen como 12,5").assertExists()
        composeRule.onNodeWithTag("detail-${ActivityDetailFields.VOLUME_M3}").assertTextContains("doce")
        composeRule.runOnIdle { assertEquals(null, saved) }
        // Once corrected, it saves the number written.
        composeRule.onNodeWithTag("detail-${ActivityDetailFields.VOLUME_M3}").performTextClearance()
        composeRule.onNodeWithTag("detail-${ActivityDetailFields.VOLUME_M3}").performTextInput("12,5")
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(12.5, (saved?.detail as com.isivoltpro.maginaolivo.domain.activity.ActivityDetail.Irrigation).volumeM3!!, 0.0)
        }
    }

    /** #414 (owner P1): editing a record saved without Parcels never ticks the Farm's only one. */
    @Test fun anEditWithoutParcelsStaysWithout() {
        var saved: ActivityDraft? = null
        show(
            ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.PRUNING, description = "Poda general"),
            options = parcels.take(1), onSave = { saved = it },
        )
        composeRule.onNodeWithTag("activity-parcel-option").performScrollTo().assertIsNotSelected()
        composeRule.onNodeWithTag("save-activity").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(emptySet<java.util.UUID>(), saved?.parcelIds) }
    }

    /** #414: on a new entry the single Parcel is ticked once; unticked, it stays unticked. */
    @Test fun anUntickedSingleParcelIsNotTickedAgain() {
        show(ActivityDraft(activityDate = LocalDate.of(2026, 9, 27), type = ActivityType.PRUNING), options = parcels.take(1), autoSelect = true)
        composeRule.onNodeWithTag("activity-parcel-option").performScrollTo().assertIsSelected().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("activity-parcel-option").assertIsNotSelected()
    }

    private fun typeChip(label: String) =
        composeRule.onAllNodesWithTag("activity-type-option").filterToOne(hasText(label))

    private fun show(
        initial: ActivityDraft,
        options: List<ActivityParcelOption> = parcels,
        doneWork: Boolean = false,
        autoSelect: Boolean = doneWork,
        today: LocalDate? = null,
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
                    autoSelectSingleParcel = autoSelect,
                    today = today,
                )
            }
        }
    }
}
