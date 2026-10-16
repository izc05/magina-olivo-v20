package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.*
import com.isivoltpro.maginaolivo.feature.account.*
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit synthetic two-process test; no production Auth or agricultural DB is opened. */
class FarmLinkProcessTest {
    @Test fun durableSelectionAndCachedIdentitySurviveProcessRestart()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        val phase=args.getString("qaG1bPhase")
        assumeTrue(phase in listOf("persist","recover"))
        val fixture=UUID.fromString(args.getString("qaG1bFixture"))
        val context=ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.isivoltpro.maginaolivo.dev",context.packageName)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand("getprop ro.kernel.qemu")).bufferedReader().use { assertEquals("1",it.readText().trim()) }
        val dbName="qa-g1b-process-$fixture.db"
        val database=MaginaOlivoDatabase.create(context,dbName)
        val ledgerFile=File(context.noBackupFilesDir,"qa-g1b-process-$fixture.json")
        val credentials=File(context.noBackupFilesDir,"qa-g1b-process-$fixture.enc")
        val alias="qa-g1b-process-$fixture"
        val photo=File(context.filesDir,"qa-g1b-process-$fixture.bin")
        val store=AndroidFarmLinkLedger(ledgerFile)
        val session=AndroidAccountSessionStore(credentials,alias,"synthetic-g1b-$fixture")
        val api=object:AccountAuthApi by SimulatedAccountAuthApi() { override val simulated=false }
        val account=AccountRepository(api,session)
        val repository=LocalFarmLinkRepository(context,database,account,"fixture-project",store)
        val ids=List(3){UUID.nameUUIDFromBytes("farm-$fixture-$it".toByteArray())}
        if(phase=="persist") {
            val workspace=UUID.nameUUIDFromBytes("workspace-$fixture".toByteArray()).toString()
            val db=database.openHelper.writableDatabase
            db.execSQL("INSERT INTO workspaces (id,name,owner_user_id,country_code,timezone,locale,currency,created_at,updated_at,version,sync_status) VALUES (?,'QA','historical-owner','ES','Europe/Madrid','es-ES','EUR',1,1,1,'LOCAL_ONLY')",arrayOf(workspace))
            ids.forEachIndexed { i,id -> db.execSQL("INSERT INTO farms (id,workspace_id,name,status,created_at,updated_at,version,sync_status) VALUES (?, ?,?,'ACTIVE',1,1,7,'LOCAL_ONLY')",arrayOf(id.toString(),workspace,"Finca $i")) }
            photo.writeBytes(byteArrayOf(9,0,4,-1))
            db.execSQL("INSERT INTO documents (id,workspace_id,owner_type,owner_id,type,mime_type,display_name,local_uri,upload_status,created_at,updated_at,version,sync_status) VALUES (?,?,'WORKSPACE',?,'PHOTO','application/octet-stream','QA',?,'LOCAL_ONLY',1,1,1,'LOCAL_ONLY')",arrayOf(UUID.randomUUID().toString(),workspace,workspace,photo.toURI().toString()))
            account.login("a@example.test","password42",true)
            repository.consent(account.state.value.userId!!,repository.choices().take(2),true)
            assertEquals(ids.take(2).toSet(),store.read()!!.farmIds)
            database.close()
        } else {
            try {
                account.resume(false)
                assertEquals(AccountStatus.OFFLINE,account.state.value.status)
                assertTrue(repository.accessAllowed())
                assertEquals(ids.take(2).toSet(),store.read()!!.farmIds)
                assertEquals(3,repository.choices().size)
                val originalLedger=ledgerFile.readBytes()
                val record=store.read()!!.consents.single()
                assertArrayEquals(photo.readBytes(),File(context.noBackupFilesDir,"account-link-backups/${record.backupId}/files/0").readBytes())
                account.logout(false);assertFalse(repository.accessAllowed())
                account.login("b@example.test","password42",true);assertFalse(repository.accessAllowed())
                try { repository.choices();fail("Account B must not read farm names") } catch (_:java.io.IOException) { }
                assertArrayEquals(originalLedger,ledgerFile.readBytes())
                account.logout(false);account.login("a@example.test","password42",true);account.resume(false)
                assertTrue(repository.accessAllowed())
                assertArrayEquals(byteArrayOf(9,0,4,-1),photo.readBytes())
                database.openHelper.readableDatabase.query("SELECT owner_user_id FROM workspaces").use { assertTrue(it.moveToFirst());assertEquals("historical-owner",it.getString(0)) }
            } finally {
                store.read()?.consents?.forEach { File(context.noBackupFilesDir,"account-link-backups/${it.backupId}").deleteRecursively() }
                database.close();context.deleteDatabase(dbName);photo.delete();session.clear()
                listOf(ledgerFile,File(ledgerFile.path+".bak"),File(ledgerFile.path+".new")).forEach { it.delete() }
                val keys=KeyStore.getInstance("AndroidKeyStore").apply { load(null) };if(keys.containsAlias(alias))keys.deleteEntry(alias)
            }
        }
    }
}
