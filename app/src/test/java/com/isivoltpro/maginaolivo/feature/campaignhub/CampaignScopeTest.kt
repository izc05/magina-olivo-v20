package com.isivoltpro.maginaolivo.feature.campaignhub

import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.farm.*
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CampaignScopeTest {
    @Test fun observingAndChangingAccountNeverCreatesAWorkspaceAndClearsPreviousFarms() = runTest {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val workspace = MutableStateFlow<AppResult<UUID>>(AppResult.Success(a))
        val repository = object : WorkspaceRepository {
            override suspend fun ensureLocalWorkspace(): AppResult<UUID> = error("Observation must never create")
            override fun observeActiveWorkspace() = workspace
        }
        val farmA = farm(a, "Cuenta A")
        val farmB = farm(b, "Cuenta B")
        val farms = object : FarmRepository {
            override fun observeActive(workspaceId: UUID) = flowOf(listOf(if (workspaceId == a) farmA else farmB))
            override fun observeArchived(workspaceId: UUID) = flowOf(emptyList<Farm>())
            override fun observeById(farmId: UUID): Flow<Farm?> = error("unused")
            override suspend fun create(command: NewFarm): AppResult<UUID> = error("read only")
            override suspend fun update(farmId: UUID, changes: FarmChanges): AppResult<Unit> = error("read only")
            override suspend fun archive(farmId: UUID): AppResult<Unit> = error("read only")
            override suspend fun restore(farmId: UUID): AppResult<Unit> = error("read only")
        }
        val seen = mutableListOf<CampaignFarmsState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { observeCampaignFarms(repository, farms).collect { seen += it } }
        testScheduler.runCurrent()
        assertEquals(listOf(farmA), seen.last().farms)
        workspace.value = AppResult.Success(b)
        testScheduler.runCurrent()
        assertEquals(listOf(farmB), seen.last().farms)
        workspace.value = AppResult.Failure(AppError.Validation("workspaceId", "select"))
        testScheduler.runCurrent()
        assertTrue(seen.last().farms.isEmpty())
        assertNotNull(seen.last().error)
        assertNull(seen.last().workspaceId)
    }

    private fun farm(workspace: UUID, name: String) = Farm(UUID.randomUUID(), workspace, name,
        null, null, null, null, null, 0, null, null, null, 1)
}
