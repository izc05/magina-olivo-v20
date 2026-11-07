package com.isivoltpro.maginaolivo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.cycle.*
import com.isivoltpro.maginaolivo.feature.campaignhub.AgriculturalCyclePanel
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AgriculturalCyclePanelTest {
    @get:Rule val compose=createComposeRule()
    private val farm=UUID.randomUUID()
    private val scope=CycleScope(UUID.randomUUID(),farm,null,0)
    private val today=LocalDate.parse("2027-05-04")
    private val state=MutableStateFlow<AppResult<CycleSnapshot>>(AppResult.Success(CycleSnapshot(scope,emptyList(),emptyList())))
    private var creates=0
    private var closes=0
    private val repository=object: AgriculturalCycleRepository {
        override fun observe(farmId: UUID)=state
        override suspend fun create(scope: CycleScope,label: String,start: LocalDate,operationId: UUID): AppResult<UUID> {
            creates++
            val id=UUID.randomUUID()
            state.value=AppResult.Success(CycleSnapshot(scope.copy(revision=scope.revision+1),listOf(
                AgriculturalCycle(id,scope.workspaceId,farm,label,start,null,null,false,1)),emptyList()))
            return AppResult.Success(id)
        }
        override suspend fun previewClose(cycleId: UUID,end: LocalDate,noRecollection: Boolean): AppResult<CycleClosePreview> {
            val snapshot=(state.value as AppResult.Success).value
            return AppResult.Success(CycleClosePreview(snapshot.cycles.single(),snapshot.scope,end,noRecollection,
                null,emptyList(),if(noRecollection) emptyList() else listOf("confirm_no_recollection"),0))
        }
        override suspend fun close(preview: CycleClosePreview,operationId: UUID): AppResult<Unit> {
            closes++
            state.value=AppResult.Success(CycleSnapshot(preview.scope.copy(revision=preview.scope.revision+1),
                listOf(preview.cycle.copy(effectiveEndDate=preview.effectiveEndDate,closedAt=Instant.parse("2027-05-04T12:00:00Z"))),emptyList()))
            return AppResult.Success(Unit)
        }
        override suspend fun assign(scope: CycleScope,cycleId: UUID,kind: CycleRecordKind,recordId: UUID,reason: String,operationId: UUID)=AppResult.Success(Unit)
    }
    private fun open() { compose.setContent { MaginaOlivoTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
        AgriculturalCyclePanel(repository,farm,today)
    } } } }

    private fun chooseDate(tag: String,initial: LocalDate,target: String) {
        val chosen=LocalDate.parse(target)
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        val months=java.time.temporal.ChronoUnit.MONTHS.between(java.time.YearMonth.from(initial),java.time.YearMonth.from(chosen)).toInt()
        repeat(kotlin.math.abs(months)) {
            compose.onNodeWithTag(if(months<0) "date-picker-previous" else "date-picker-next").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("date-picker-day-$target").performClick()
        compose.onNodeWithTag("date-picker-confirm").performScrollTo().performClick()
    }

    @Test fun cancellingCreationNeverWritesAndFieldsRemainReadable() {
        open()
        compose.onNodeWithTag("cycle-new").performScrollTo().performClick()
        compose.onNodeWithTag("cycle-label").performTextReplacement("2026/27")
        compose.onNodeWithTag("cycle-form-cancel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0,creates); assertEquals(0,closes) }
    }

    @Test fun creatingAndClosingRequiresSeparateExplicitConfirmationsAndUsesChosenDate() {
        open()
        compose.onNodeWithTag("cycle-new").performScrollTo().performClick()
        compose.onNodeWithTag("cycle-label").performTextReplacement("2026/27")
        chooseDate("cycle-start-date",today,"2026-02-01")
        compose.onNodeWithTag("cycle-create-confirm").performScrollTo().performClick()
        compose.waitUntil { creates==1 }
        compose.onNodeWithTag("cycle-close-open").performScrollTo().performClick()
        chooseDate("cycle-effective-end",today,"2027-01-20")
        compose.onNodeWithTag("cycle-no-recollection").performScrollTo().performClick()
        compose.onNodeWithTag("cycle-review-close").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0,closes) }
        compose.onNodeWithTag("cycle-close-confirm").performScrollTo().performClick()
        compose.waitUntil { closes==1 }
        compose.runOnIdle {
            val cycle=(state.value as AppResult.Success).value.cycles.single()
            assertEquals(LocalDate.parse("2027-01-20"),cycle.effectiveEndDate)
            assertEquals(1,creates)
        }
        compose.onNodeWithTag("cycle-history-does-not-write").performScrollTo().assertExists()
    }
}
