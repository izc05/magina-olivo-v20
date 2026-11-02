package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.feature.accountlink.FarmChoice
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkConsent
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkReconsent
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkRevocation
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FarmLinkLedgerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.noBackupFilesDir, "qa-farm-link-${UUID.randomUUID()}.json")
    private val farm = FarmChoice(UUID.randomUUID(), UUID.randomUUID(), "Finca QA", 4)
    private fun consent(subject: String = "isolated-project|user-a", farms: List<FarmChoice> = listOf(farm)) =
        FarmLinkConsent(UUID.randomUUID(), subject, farms, UUID.randomUUID(), "a".repeat(64), Instant.parse("2026-10-16T00:00:00Z"))
    @After fun cleanup() { listOf(file, File(file.path+".bak"), File(file.path+".new")).forEach { it.delete() } }

    @Test fun durableConsentRetainsIdsAndExactSelectionAfterRestart() {
        val entry = consent()
        AndroidFarmLinkLedger(file).append(entry)
        val reopened = AndroidFarmLinkLedger(file).read()!!
        assertEquals(entry, reopened.consents.single())
        assertEquals("PENDING_REMOTE_RESERVATION", reopened.consents.single().state)
        assertEquals(farm.id, reopened.consents.single().farms.single().id)
    }
    @Test fun anotherAccountCannotClaimOrOverwriteExistingConsent() {
        val store = AndroidFarmLinkLedger(file)
        store.append(consent()); val before = file.readBytes()
        try { store.append(consent("isolated-project|user-b")); fail("Account reassignment must fail") } catch (_: IOException) { }
        assertArrayEquals(before, file.readBytes())
    }
    @Test fun exactRetryDoesNotDuplicateAndChangedRetryIsRejected() {
        val store = AndroidFarmLinkLedger(file); val entry = consent()
        store.append(entry); val before=file.readBytes()
        assertEquals(1, store.append(entry).consents.size)
        assertArrayEquals(before,file.readBytes())
        try { store.append(entry.copy(backupId=UUID.randomUUID()));fail("Immutable consent") } catch (_: IOException) { }
    }
    @Test fun emptySelectionAndCorruptMetadataNeverBecomeValidConsent() {
        val store = AndroidFarmLinkLedger(file)
        try { store.append(consent(farms=emptyList()));fail("Empty selection") } catch (_: IOException) { }
        assertFalse(file.exists())
        store.append(consent()); file.appendText("broken")
        try { store.read();fail("Corruption must fail closed") } catch (_: IOException) { }
    }
    @Test fun interruptedAtomicReplacementRestoresPreviousConsent() {
        val store=AndroidFarmLinkLedger(file);val entry=consent();store.append(entry)
        file.copyTo(File(file.path+".bak"));file.writeText("partial-write")
        assertEquals(entry,AndroidFarmLinkLedger(file).read()!!.consents.single())
    }
    @Test fun newWordingNeverRewritesPreviouslyAcceptedTextVersion() {
        val store=AndroidFarmLinkLedger(file)
        val old=consent().copy(textVersion=1)
        store.append(old)
        val fresh=consent(farms=listOf(farm.copy(id=UUID.randomUUID()))).copy(textVersion=2)
        store.append(fresh)
        assertEquals(listOf(old,fresh),AndroidFarmLinkLedger(file).read()!!.consents)
        val before=file.readBytes()
        try { store.append(consent(farms=listOf(farm.copy(id=UUID.randomUUID()))).copy(textVersion=999));fail("Unknown consent text") } catch (_:IOException) { }
        assertArrayEquals(before,file.readBytes())
    }

    private fun revocation(subject: String = "isolated-project|user-a") =
        FarmLinkRevocation(UUID.randomUUID(), subject, farm.id, Instant.parse("2026-10-20T00:00:00Z"))
    private fun renewal(after: UUID, at: Instant = Instant.parse("2026-10-21T00:00:00Z")) =
        FarmLinkReconsent(UUID.randomUUID(), "isolated-project|user-a", farm.id, after, at)

    @Test fun renewalIsOrderedByTheLedgerAndSurvivesRestart() {
        val store = AndroidFarmLinkLedger(file); store.append(consent())
        val revoked = store.revoke(revocation()).revocations.single()
        assertEquals(1L, revoked.sequence)
        // A device clock that went backwards does not matter: the sequence orders events.
        val renewed = store.reconsent(renewal(revoked.operationId, Instant.parse("2026-01-01T00:00:00Z")))
        assertEquals(setOf(farm.id), renewed.activeFarmIds)
        val reopened = AndroidFarmLinkLedger(file).read()!!
        assertEquals(2L, reopened.reconsents.single().sequence)
        assertEquals(reopened.reconsents.single(), reopened.reconsentAfter(revoked.operationId))
        val again = store.revoke(revocation())
        assertEquals(3L, again.revocations.last().sequence)
        assertEquals(setOf(farm.id), AndroidFarmLinkLedger(file).read()!!.revokedFarmIds)
        assertNull(AndroidFarmLinkLedger(file).read()!!.reconsentAfter(revoked.operationId))
    }

    @Test fun renewalNeedsTheRevocationCurrentlyInForce() {
        val store = AndroidFarmLinkLedger(file); store.append(consent())
        try { store.reconsent(renewal(UUID.randomUUID())); fail("Nothing to renew") } catch (_: IOException) { }
        val revoked = store.revoke(revocation()).revocations.single()
        try { store.reconsent(renewal(UUID.randomUUID())); fail("Must name the revocation in force") } catch (_: IOException) { }
        try { store.reconsent(renewal(revoked.operationId).copy(subject = "isolated-project|user-b")); fail("Other account") } catch (_: IOException) { }
        val entry = renewal(revoked.operationId)
        store.reconsent(entry); val before = file.readBytes()
        assertEquals("Exact retry is idempotent", 1, store.reconsent(entry).reconsents.size)
        assertArrayEquals(before, file.readBytes())
        try { store.reconsent(renewal(revoked.operationId)); fail("Already renewed") } catch (_: IOException) { }
    }

    @Test fun formatTwoLedgerIsReadAndUpgradedWithoutLosingTheRevocation() {
        val store = AndroidFarmLinkLedger(file); store.append(consent())
        val revoked = store.revoke(revocation()).revocations.single()
        assertTrue(file.readText().contains("\\\"format\\\":2"))
        store.reconsent(renewal(revoked.operationId))
        val upgraded = AndroidFarmLinkLedger(file).read()!!
        assertTrue(file.readText().contains("\\\"format\\\":3"))
        assertEquals(revoked, upgraded.revocations.single())
        assertEquals(setOf(farm.id), upgraded.activeFarmIds)
    }
}
