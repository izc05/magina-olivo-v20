package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.isivoltpro.maginaolivo.data.local.dao.ReceiptRecordOutcome
import com.isivoltpro.maginaolivo.data.local.dao.SyncReceiveHoldDao
import com.isivoltpro.maginaolivo.data.local.entity.SyncReceiveHoldEntity
import com.isivoltpro.maginaolivo.data.local.entity.SyncReceiveReceiptEntity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #808 R3-a: the two receive tables on a real Room database (API 35). Evidence and holds are idempotent (the same
 * observation twice writes nothing), holds are never resolved by these DAOs, and accounts and issuers never see each
 * other's rows. No receiver exists yet: nothing in shipped code writes these tables.
 */
@RunWith(AndroidJUnit4::class)
class SyncReceiveStoreRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "g808-r3a-${UUID.randomUUID()}"
    private lateinit var db: MaginaOlivoDatabase
    private val harvest = UUID.fromString("10000000-0000-0000-0000-0000000008a3")
    private val pesada = UUID.fromString("20000000-0000-0000-0000-0000000008a3")

    @Before fun open() { db = MaginaOlivoDatabase.create(context, name) }
    @After fun close() { runCatching { db.close() }; context.deleteDatabase(name) }

    private fun receipt(version: Long = 1, at: Long = 1_000, account: String = "a", issuer: String = "iss") = SyncReceiveReceiptEntity(
        issuer = issuer, accountId = account, entityType = "DELIVERY", entityId = pesada, harvestId = harvest, remoteVersion = version,
        deleted = false, headOperationId = UUID.fromString("30000000-0000-0000-0000-0000000008a3"), headPayloadVersion = 3,
        headPayloadHash = "a".repeat(64), ownFingerprint = null, derivedFingerprint = null, contractVersion = 1, receivedAt = at)

    private fun hold(remote: Long = 2, at: Long = 1_000, detail: String? = "local=1", cause: String = "REMOTE_AHEAD", account: String = "a") =
        SyncReceiveHoldEntity(issuer = "iss", accountId = account, entityType = "DELIVERY", entityId = pesada, cause = cause, harvestId = harvest,
            localVersion = 1, remoteVersion = remote, remoteHash = "b".repeat(64), detail = detail, firstSeenAt = at, lastSeenAt = at)

    @Test fun aReceiptIsRecordedOnceAndTheSameEvidenceAgainWritesNothing() = runBlocking<Unit> {
        val dao = db.syncReceiveReceiptDao()
        assertEquals(ReceiptRecordOutcome.INSERTED, dao.record(receipt()))
        val stored = dao.find("iss", "a", "DELIVERY", pesada)
        assertEquals(receipt(), stored)
        // The same evidence later: zero writes, the first receivedAt is kept.
        assertEquals(ReceiptRecordOutcome.UNCHANGED, dao.record(receipt(at = 9_000)))
        assertEquals(stored, dao.find("iss", "a", "DELIVERY", pesada))
        // New evidence (a newer remote version) replaces it.
        assertEquals(ReceiptRecordOutcome.UPDATED, dao.record(receipt(version = 2, at = 9_000)))
        assertEquals(2L, dao.find("iss", "a", "DELIVERY", pesada)!!.remoteVersion)
        assertEquals(listOf(receipt(version = 2, at = 9_000)), dao.listForHarvest("iss", "a", harvest))
        assertEquals(1, dao.count())
    }

    @Test fun aHoldIsObservedIdempotentlyKeepsItsFirstSightingAndIsNeverResolvedHere() = runBlocking<Unit> {
        val dao = db.syncReceiveHoldDao()
        assertTrue(dao.observe(hold()))
        val first = dao.find("iss", "a", "DELIVERY", pesada, "REMOTE_AHEAD")!!
        assertEquals(1_000L, first.firstSeenAt); assertNull(first.resolvedAt)
        // Seen again, unchanged: not even last_seen_at is written.
        assertFalse(dao.observe(hold(at = 5_000)))
        assertEquals(first, dao.find("iss", "a", "DELIVERY", pesada, "REMOTE_AHEAD"))
        // A newer remote version: updated, first sighting kept, still unresolved.
        assertTrue(dao.observe(hold(remote = 3, at = 6_000)))
        val changed = dao.find("iss", "a", "DELIVERY", pesada, "REMOTE_AHEAD")!!
        assertEquals(listOf(1_000L, 6_000L, 3L), listOf(changed.firstSeenAt, changed.lastSeenAt, changed.remoteVersion))
        assertNull(changed.resolvedAt)
        // A caller can never resolve a hold through observe().
        assertTrue(dao.observe(hold(remote = 4, at = 7_000).copy(resolvedAt = 7_000)))
        assertNull(dao.find("iss", "a", "DELIVERY", pesada, "REMOTE_AHEAD")!!.resolvedAt)
        // Another cause for the same Pesada is another hold; the detail is bounded.
        assertTrue(dao.observe(hold(cause = "UNVERIFIED", detail = "x".repeat(2_000))))
        assertEquals(SyncReceiveHoldDao.DETAIL_MAX, dao.find("iss", "a", "DELIVERY", pesada, "UNVERIFIED")!!.detail!!.length)
        assertEquals(listOf("REMOTE_AHEAD", "UNVERIFIED"), dao.listOpen("iss", "a").map { it.cause })
        assertEquals(2, dao.count())
    }

    @Test fun accountsAndIssuersNeverSeeEachOthersEvidenceOrHolds() = runBlocking<Unit> {
        val receipts = db.syncReceiveReceiptDao(); val holds = db.syncReceiveHoldDao()
        assertEquals(ReceiptRecordOutcome.INSERTED, receipts.record(receipt(account = "a")))
        assertEquals(ReceiptRecordOutcome.INSERTED, receipts.record(receipt(account = "b")))
        assertEquals(ReceiptRecordOutcome.INSERTED, receipts.record(receipt(issuer = "otro")))
        assertTrue(holds.observe(hold(account = "a"))); assertTrue(holds.observe(hold(account = "b")))
        assertEquals(1, receipts.listForHarvest("iss", "a", harvest).size)
        assertNull(receipts.find("iss", "c", "DELIVERY", pesada))
        assertEquals(listOf("a"), holds.listOpen("iss", "a").map { it.accountId })
        assertEquals(listOf("b"), holds.listOpen("iss", "b").map { it.accountId })
        assertTrue(holds.listOpen("otro", "a").isEmpty())
        assertEquals(3, receipts.count()); assertEquals(2, holds.count())
    }

    private fun table(name: String) = db.openHelper.writableDatabase.query("SELECT * FROM $name ORDER BY rowid").use { c ->
        buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { if (c.isNull(it)) "<NULL>" else c.getString(it) }) } }

    /** Regression 1: an older remote version (an old bundle, a retried read) never goes back; nothing is written. */
    @Test fun anOlderRemoteVersionIsStaleAndWritesNothing() = runBlocking<Unit> {
        val dao = db.syncReceiveReceiptDao()
        assertEquals(ReceiptRecordOutcome.INSERTED, dao.record(receipt(version = 3)))
        val before = table("sync_receive_receipts")
        for (older in listOf(2L, 1L)) assertEquals(ReceiptRecordOutcome.STALE, db.recordReceiveEvidence(receipt(version = older, at = 9_000)))
        // Even an older version with other evidence is stale, never a conflict and never a replacement.
        assertEquals(ReceiptRecordOutcome.STALE, db.recordReceiveEvidence(receipt(version = 2).copy(headPayloadHash = "c".repeat(64))))
        assertEquals(before, table("sync_receive_receipts"))
        assertEquals(0, db.syncReceiveHoldDao().count())
    }

    /** Regression 2: the same version with identical evidence (a repeated reception) is accepted with zero writes. */
    @Test fun theSameVersionWithIdenticalEvidenceIsUnchangedAndWritesNothing() = runBlocking<Unit> {
        assertEquals(ReceiptRecordOutcome.INSERTED, db.recordReceiveEvidence(receipt(version = 2)))
        val before = table("sync_receive_receipts")
        repeat(3) { assertEquals(ReceiptRecordOutcome.UNCHANGED, db.recordReceiveEvidence(receipt(version = 2, at = 5_000L + it))) }
        assertEquals(before, table("sync_receive_receipts"))
        assertEquals(0, db.syncReceiveHoldDao().count())
    }

    /** Regression 3: the same version with contradictory evidence is a verifiable conflict; the receipt is never replaced. */
    @Test fun theSameVersionWithContradictoryEvidenceIsAVerifiableConflict() = runBlocking<Unit> {
        assertEquals(ReceiptRecordOutcome.INSERTED, db.recordReceiveEvidence(receipt(version = 2)))
        val stored = table("sync_receive_receipts")
        val rewritten = receipt(version = 2, at = 7_000).copy(headOperationId = UUID.randomUUID(), headPayloadHash = "d".repeat(64))
        assertEquals(ReceiptRecordOutcome.CONFLICT, db.recordReceiveEvidence(rewritten))
        assertEquals(stored, table("sync_receive_receipts"))
        val hold = db.syncReceiveHoldDao().find("iss", "a", "DELIVERY", pesada, RECEIVE_HOLD_REMOTE_REWRITTEN)!!
        assertEquals(listOf(2L, 2L, "d".repeat(64)), listOf(hold.localVersion, hold.remoteVersion, hold.remoteHash))
        assertTrue(hold.detail!!.contains("stored_hash=" + "a".repeat(64)))
        assertNull(hold.resolvedAt)
        // Each kind of contradiction is a conflict: deleted, fingerprints, Jornada, contract.
        for (other in listOf(receipt(version = 2).copy(deleted = true), receipt(version = 2).copy(ownFingerprint = "e".repeat(64)),
            receipt(version = 2).copy(harvestId = UUID.randomUUID()), receipt(version = 2).copy(contractVersion = 2))) {
            assertEquals(ReceiptRecordOutcome.CONFLICT, db.syncReceiveReceiptDao().record(other))
        }
        assertEquals(stored, table("sync_receive_receipts"))
        // The same conflict observed again writes nothing more.
        val holds = table("sync_receive_holds")
        assertEquals(ReceiptRecordOutcome.CONFLICT, db.recordReceiveEvidence(rewritten.copy(receivedAt = 8_000)))
        assertEquals(holds, table("sync_receive_holds"))
    }

    /** Regression 4: a newer version updates; it survives a restart, and accounts never block each other. */
    @Test fun aNewerVersionUpdatesSurvivesARestartAndAccountsStayIsolated() = runBlocking<Unit> {
        assertEquals(ReceiptRecordOutcome.INSERTED, db.recordReceiveEvidence(receipt(version = 1)))
        assertEquals(ReceiptRecordOutcome.UPDATED, db.recordReceiveEvidence(receipt(version = 3, at = 2_000).copy(headPayloadHash = "f".repeat(64))))
        assertTrue(db.syncReceiveHoldDao().observe(hold()))
        val receipts = table("sync_receive_receipts"); val holds = table("sync_receive_holds")
        // Restart: the database is closed and reopened; the rules apply to what was persisted.
        db.close(); db = MaginaOlivoDatabase.create(context, name)
        assertEquals(receipts, table("sync_receive_receipts")); assertEquals(holds, table("sync_receive_holds"))
        assertEquals(ReceiptRecordOutcome.STALE, db.recordReceiveEvidence(receipt(version = 2)))
        assertEquals(ReceiptRecordOutcome.UNCHANGED, db.recordReceiveEvidence(receipt(version = 3, at = 9_000).copy(headPayloadHash = "f".repeat(64))))
        // Another account (or issuer) keeps its own evidence: a lower version there is a first insert, not stale.
        assertEquals(ReceiptRecordOutcome.INSERTED, db.recordReceiveEvidence(receipt(version = 1, account = "b")))
        assertEquals(ReceiptRecordOutcome.INSERTED, db.recordReceiveEvidence(receipt(version = 1, issuer = "otro")))
        assertEquals(3L, db.syncReceiveReceiptDao().find("iss", "a", "DELIVERY", pesada)!!.remoteVersion)
        // Outbox untouched throughout.
        assertEquals(0, db.syncOutboxDao().countActive())
    }

    @Test fun aFreshInstallStartsWithBothTablesEmptyAndNoOutbox() = runBlocking<Unit> {
        assertEquals(0, db.syncReceiveReceiptDao().count())
        assertEquals(0, db.syncReceiveHoldDao().count())
        assertEquals(0, db.syncOutboxDao().countActive())
    }
}
