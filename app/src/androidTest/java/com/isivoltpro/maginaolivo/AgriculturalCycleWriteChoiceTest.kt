package com.isivoltpro.maginaolivo

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.cycle.*
import com.isivoltpro.maginaolivo.feature.campaignhub.AgriculturalCycleWriteChoice
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AgriculturalCycleWriteChoiceTest {
    @get:Rule val compose=createComposeRule()
    private val farm=UUID.randomUUID()
    private val workspace=UUID.randomUUID()
    private val old=UUID.randomUUID()
    private val current=UUID.randomUUID()
    private val cycles=listOf(
        AgriculturalCycle(old,workspace,farm,"Histórica",LocalDate.parse("2026-02-01"),LocalDate.parse("2027-01-25"),
            java.time.Instant.parse("2027-05-01T12:00:00Z"),true,2),
        AgriculturalCycle(current,workspace,farm,"Actual",LocalDate.parse("2027-01-26"),null,null,false,1))
    private val snapshot=CycleSnapshot(CycleScope(workspace,farm,null,2),cycles,emptyList())
    private val flow=MutableStateFlow<AppResult<CycleSnapshot>>(AppResult.Success(snapshot))
    private val repository=object: AgriculturalCycleRepository {
        override fun observe(farmId: UUID)=flow
        override suspend fun create(scope: CycleScope,label: String,start: LocalDate,operationId: UUID): AppResult<UUID> = error("Unexpected write")
        override suspend fun previewClose(cycleId: UUID,end: LocalDate,noRecollection: Boolean): AppResult<CycleClosePreview> = error("Unexpected write")
        override suspend fun close(preview: CycleClosePreview,operationId: UUID): AppResult<Unit> = error("Unexpected write")
        override suspend fun assign(scope: CycleScope,cycleId: UUID,kind: CycleRecordKind,recordId: UUID,reason: String,operationId: UUID): AppResult<Unit> = error("Unexpected write")
    }

    @Test fun closedDraftDestinationIsNotSilentlyReplacedByTheNextOpenCycle() {
        val choice=mutableStateOf<UUID?>(old)
        compose.setContent { MaginaOlivoTheme { AgriculturalCycleWriteChoice(repository,farm,choice.value,{choice.value=it}) } }
        compose.waitForIdle()
        compose.runOnIdle {assertNull(choice.value)}
        compose.waitForIdle()
        compose.runOnIdle {assertNull(choice.value)}
        compose.onNodeWithTag("cycle-write-selector").performClick()
        compose.onNodeWithText("Actual").performClick()
        compose.runOnIdle {assertEquals(current,choice.value)}
    }

    @Test fun newDraftUsesTheOpenCycleUuidEvenWhenHistoryIsBeingConsultedElsewhere() {
        val choice=mutableStateOf<UUID?>(null)
        compose.setContent { MaginaOlivoTheme { AgriculturalCycleWriteChoice(repository,farm,choice.value,{choice.value=it}) } }
        compose.waitForIdle()
        compose.runOnIdle {assertEquals(current,choice.value)}
        compose.onNodeWithTag("cycle-write-selector").assertTextContains("Actual",substring=true)
    }
}
