package com.isivoltpro.maginaolivo.feature.farms

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FarmSetupCheckpointStoreTest {
    @Test fun reservationSurvivesStoreRecreationAndIsScopedByOwnerAndWorkspace() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspaceA = UUID.randomUUID()
        val workspaceB = UUID.randomUUID()
        val ownerA = UUID.randomUUID()
        val ownerB = UUID.randomUUID()
        val first = FarmSetupCheckpointStore(context)
        val farmId = requireNotNull(first.reserve(workspaceA, ownerA))
        val reopened = FarmSetupCheckpointStore(context)
        assertEquals(farmId, reopened.load(workspaceA, ownerA))
        assertEquals(farmId, reopened.reserve(workspaceA, ownerA))
        val concurrent = (1..8).map { java.util.concurrent.CompletableFuture.supplyAsync {
            FarmSetupCheckpointStore(context).reserve(workspaceA, ownerA)
        } }
        assertTrue(concurrent.all { it.get() == farmId })
        assertNull(reopened.load(workspaceB, ownerA))
        assertNull(reopened.load(workspaceA, ownerB))
        assertTrue(reopened.clear(workspaceA, ownerA))
        assertNull(FarmSetupCheckpointStore(context).load(workspaceA, ownerA))
    }
}
