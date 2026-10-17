package com.isivoltpro.maginaolivo.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.isivoltpro.maginaolivo.data.repository.AndroidFarmLinkLedger
import com.isivoltpro.maginaolivo.feature.accountlink.FarmChoice
import com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkConsent
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
}
