package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        assertTrue(dao.record(receipt()))
        val stored = dao.find("iss", "a", "DELIVERY", pesada)
        assertEquals(receipt(), stored)
        // The same evidence later: zero writes, the first receivedAt is kept.
        assertFalse(dao.record(receipt(at = 9_000)))
        assertEquals(stored, dao.find("iss", "a", "DELIVERY", pesada))
        // New evidence (a newer remote version) replaces it.
        assertTrue(dao.record(receipt(version = 2, at = 9_000)))
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
        assertTrue(receipts.record(receipt(account = "a"))); assertTrue(receipts.record(receipt(account = "b")))
        assertTrue(receipts.record(receipt(issuer = "otro")))
        assertTrue(holds.observe(hold(account = "a"))); assertTrue(holds.observe(hold(account = "b")))
        assertEquals(1, receipts.listForHarvest("iss", "a", harvest).size)
        assertNull(receipts.find("iss", "c", "DELIVERY", pesada))
        assertEquals(listOf("a"), holds.listOpen("iss", "a").map { it.accountId })
        assertEquals(listOf("b"), holds.listOpen("iss", "b").map { it.accountId })
        assertTrue(holds.listOpen("otro", "a").isEmpty())
        assertEquals(3, receipts.count()); assertEquals(2, holds.count())
    }

    @Test fun aFreshInstallStartsWithBothTablesEmptyAndNoOutbox() = runBlocking<Unit> {
        assertEquals(0, db.syncReceiveReceiptDao().count())
        assertEquals(0, db.syncReceiveHoldDao().count())
        assertEquals(0, db.syncOutboxDao().countActive())
    }
}
