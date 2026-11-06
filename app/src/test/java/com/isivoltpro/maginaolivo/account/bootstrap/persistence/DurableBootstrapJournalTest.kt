package com.isivoltpro.maginaolivo.account.bootstrap.persistence

import com.isivoltpro.maginaolivo.account.bootstrap.*
import com.isivoltpro.maginaolivo.account.reservation.ReservationActor
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DurableBootstrapJournalTest {
    private fun fixture(test: (File) -> Unit) {
        val dir = Files.createTempDirectory("bootstrap-journal").toFile()
        try { test(File(dir, "plan")) } finally { dir.deleteRecursively() }
    }

    @Test fun restartRetainsExactPlanAndOnlyUnconfirmedOperationsAreReady() = fixture { file ->
        val f = BootstrapJournalFixture
        DurableBootstrapJournal(file).prepare(f.plan)
        val prepared = DurableBootstrapJournal(file).load()!!
        assertEquals(f.plan.steps, prepared.plan.steps)
        assertEquals(f.plan.actor, prepared.plan.actor)
        assertEquals(f.plan.remoteWorkspaceId, prepared.plan.remoteWorkspaceId)
        assertEquals(listOf(f.root), prepared.ready(f.actor, f.consent, setOf(f.farm)))
        DurableBootstrapJournal(file).record(f.rootReceipt)
        val restored = DurableBootstrapJournal(file).load()!!
        assertEquals(listOf(f.farmStep), restored.ready(f.actor, f.consent, setOf(f.farm)))
        assertEquals(listOf(f.rootReceipt), restored.receipts)
        assertTrue(restored.ready(f.actor.copy(userId = UUID.randomUUID()), f.consent, setOf(f.farm)).isEmpty())
        assertTrue(restored.ready(f.actor, null, setOf(f.farm)).isEmpty())
        assertTrue(restored.ready(f.actor, f.consent, emptySet()).isEmpty())
    }

    @Test fun sameReceiptAndPrepareAreIdempotentWithoutErasingReceipts() = fixture { file ->
        val f = BootstrapJournalFixture
        val journal = DurableBootstrapJournal(file)
        journal.prepare(f.plan); journal.record(f.rootReceipt)
        val before = file.readBytes()
        journal.record(f.rootReceipt); journal.prepare(f.plan)
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun preparedIdentityRoutePayloadAndConsentCannotBeReplaced() = fixture { file ->
        val f = BootstrapJournalFixture
        val journal = DurableBootstrapJournal(file); journal.prepare(f.plan)
        val before = file.readBytes()
        val otherWorkspace = UUID.randomUUID()
        listOf(
            BootstrapPlan(f.actor.copy(userId = UUID.randomUUID()), f.workspace, f.consent, setOf(f.farm), f.plan.steps),
            BootstrapPlan(f.actor, f.workspace, UUID.randomUUID(), setOf(f.farm), f.plan.steps),
            BootstrapPlan(f.actor, f.workspace, f.consent, setOf(f.farm), listOf(f.root.copy(payloadJson = "changed"), f.farmStep)),
            BootstrapPlan(f.actor, otherWorkspace, f.consent, setOf(f.farm),
                listOf(f.root.copy(entityId = otherWorkspace), f.farmStep)),
        ).forEach { changed -> assertThrows(IllegalArgumentException::class.java) { journal.prepare(changed) } }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun wrongOrOutOfOrderReceiptsDoNotModifyDisk() = fixture { file ->
        val f = BootstrapJournalFixture; val journal = DurableBootstrapJournal(file); journal.prepare(f.plan)
        val before = file.readBytes()
        listOf(f.rootReceipt.copy(payloadHash = "c".repeat(64)), f.rootReceipt.copy(remoteVersion = 0),
            f.rootReceipt.copy(actor = f.actor.copy(userId = UUID.randomUUID())), f.farmReceipt)
            .forEach { assertThrows(IllegalArgumentException::class.java) { journal.record(it) } }
        assertArrayEquals(before, file.readBytes())
        journal.record(f.rootReceipt)
        assertThrows(IllegalArgumentException::class.java) { journal.record(f.rootReceipt.copy(remoteVersion = 2)) }
    }

    @Test fun failedAtomicCommitKeepsPreviousSnapshotAndRetryUsesSameOperation() = fixture { file ->
        val f = BootstrapJournalFixture; DurableBootstrapJournal(file).prepare(f.plan)
        val before = file.readBytes()
        assertThrows(IOException::class.java) {
            DurableBootstrapJournal(file) { throw IOException("interrupted") }.record(f.rootReceipt)
        }
        assertArrayEquals(before, file.readBytes())
        assertEquals(listOf(f.root), DurableBootstrapJournal(file).load()!!.ready(f.actor, f.consent, setOf(f.farm)))
        DurableBootstrapJournal(file).record(f.rootReceipt)
        assertEquals(listOf(f.rootReceipt), DurableBootstrapJournal(file).load()!!.receipts)
    }

    @Test fun corruptionAndTruncationFailClosedWithoutResettingPlan() = fixture { file ->
        DurableBootstrapJournal(file).prepare(BootstrapJournalFixture.plan)
        val original = file.readBytes()
        listOf(original.copyOf(original.size - 1), original.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() },
            original + byteArrayOf(0)).forEach { damaged ->
            file.writeBytes(damaged)
            assertThrows(IOException::class.java) { DurableBootstrapJournal(file).load() }
            assertThrows(IOException::class.java) { DurableBootstrapJournal(file).prepare(BootstrapJournalFixture.plan) }
            assertArrayEquals(damaged, file.readBytes())
        }
    }

    @Test fun concurrentInstancesPreserveOneReceiptAndCompletion() = fixture { file ->
        val f = BootstrapJournalFixture; DurableBootstrapJournal(file).prepare(f.plan)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = (1..12).map { pool.submit { DurableBootstrapJournal(file).record(f.rootReceipt) } }
            jobs.forEach { it.get(10, TimeUnit.SECONDS) }
            DurableBootstrapJournal(file).record(f.farmReceipt)
            val restored = DurableBootstrapJournal(file).load()!!
            assertEquals(listOf(f.rootReceipt, f.farmReceipt), restored.receipts)
            assertTrue(restored.ready(f.actor, f.consent, setOf(f.farm)).isEmpty())
        } finally { pool.shutdownNow() }
    }

    @Test fun largeUtf8SnapshotsRoundTripWithoutWriteUtfLimit() = fixture { file ->
        val f = BootstrapJournalFixture
        val steps = listOf(f.root.copy(payloadJson = "{\"name\":\"" + "Olivo 🌳".repeat(12000) + "\"}"), f.farmStep)
        val plan = BootstrapPlan(f.actor, f.workspace, f.consent, setOf(f.farm), steps)
        DurableBootstrapJournal(file).prepare(plan)
        assertEquals(steps, DurableBootstrapJournal(file).load()!!.plan.steps)
    }

    @Test fun invalidUnicodeIsRejectedWithoutCommittingLossyPayload() = fixture { file ->
        val f = BootstrapJournalFixture
        val plan = BootstrapPlan(f.actor, f.workspace, f.consent, setOf(f.farm),
            listOf(f.root.copy(payloadJson = "bad\uD800"), f.farmStep))
        assertThrows(IOException::class.java) { DurableBootstrapJournal(file).prepare(plan) }
        assertFalse(file.exists())
        DurableBootstrapJournal(file).prepare(f.plan)
        val before = file.readBytes()
        assertThrows(IOException::class.java) { DurableBootstrapJournal(file).prepare(plan) }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun collectionAboveReadLimitCannotReplaceExistingJournal() = fixture { file ->
        val f = BootstrapJournalFixture
        val journal = DurableBootstrapJournal(file); journal.prepare(f.plan)
        val before = file.readBytes()
        val farms = (1..10000).map { UUID(1, it.toLong()) }.toSet() + f.farm
        val oversized = BootstrapPlan(f.actor, f.workspace, f.consent, farms, f.plan.steps)
        assertThrows(IllegalArgumentException::class.java) { journal.prepare(oversized) }
        assertArrayEquals(before, file.readBytes())
        assertEquals(f.plan.steps, journal.load()!!.plan.steps)
    }

    @Test fun aggregateOverSizeLimitDoesNotCreatePartialPlan() = fixture { file ->
        val f = BootstrapJournalFixture
        val payload = "a".repeat(3_500_000)
        val farms = (1..4).map { UUID.randomUUID() }
        val steps = listOf(f.root.copy(payloadJson = payload)) + farms.map { farm ->
            f.farmStep.copy(operationId = UUID.randomUUID(), entityId = farm,
                requiredFarmIds = setOf(farm), payloadJson = payload)
        }
        val plan = BootstrapPlan(f.actor, f.workspace, f.consent, farms.toSet(), steps)
        assertThrows(IOException::class.java) { DurableBootstrapJournal(file).prepare(plan) }
        assertFalse(file.exists())
    }
}

internal object BootstrapJournalFixture {
    val actor = ReservationActor("https://bootstrap.example.test/auth/v1", UUID(0, 1))
    val workspace = UUID(0, 2)
    val farm = UUID(0, 3)
    val consent = UUID(0, 4)
    val root = BootstrapStep(UUID(0, 5), workspace, BootstrapEntity.WORKSPACE, "{}", "a".repeat(64), 1, emptySet(), emptySet())
    val farmStep = BootstrapStep(UUID(0, 6), farm, BootstrapEntity.FARM, "{}", "b".repeat(64), 1, setOf(root.operationId), setOf(farm))
    val plan = BootstrapPlan(actor, workspace, consent, setOf(farm), listOf(root, farmStep))
    val rootReceipt = BootstrapReceipt(actor, root.operationId, workspace, root.entity, root.payloadHash, 1, 1)
    val farmReceipt = BootstrapReceipt(actor, farmStep.operationId, farm, farmStep.entity, farmStep.payloadHash, 1, 1)
}
